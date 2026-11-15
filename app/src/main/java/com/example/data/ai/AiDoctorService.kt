package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.compatibility.DeviceCompatibilityEngine
import com.example.data.diagnostics.RealDeviceDiagnostics
import com.example.data.knowledge.IssueRagMatch
import com.example.data.knowledge.KnowledgeRepository
import com.example.data.knowledge.PlaybookRagRetriever
import com.example.data.knowledge.RagRetrievalOutcome
import com.example.data.model.AiServiceStatus
import com.example.data.model.AutomationCapability
import com.example.data.model.AutonomousRepairBundle
import com.example.data.model.DeviceVitals
import com.example.data.model.DoctorAction
import com.example.data.model.OemCompatibilityProfile
import com.example.data.model.RepairPlan
import com.example.data.repair.AutonomousRepairOrchestrator
import com.example.data.repair.SafeRepairRegistry
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

private const val TAG = "AiDoctorService"

data class DoctorResponse(
    val replyText: String,
    val actions: List<DoctorAction>,
    val repairPlan: RepairPlan? = null,
    val autonomousBundle: AutonomousRepairBundle? = null,
    val aiStatus: AiServiceStatus = AiServiceStatus.ONLINE_GEMINI,
    val aiStatusDetail: String? = null,
    val responseLanguage: String = LanguageDetector.LANG_ENGLISH
)

class AiDoctorService(
    private val aiProxyUrl: String = BuildConfig.AI_PROXY_URL
) {

    companion object {
        /**
         * Currently supported Gemini 3.x models supporting generateContent.
         * Retired models (1.5-flash, 2.0-flash, 2.5-flash) are excluded.
         */
        val SUPPORTED_GEMINI_MODELS = listOf(
            "gemini-3.5-flash-lite",
            "gemini-3-flash-preview",
            "gemini-3.8-flash"
        )
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val diagnosisSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "replyText" to Schema.string(description = "Clear diagnostic explanation and practical step-by-step fix in the exact requested language."),
                "problemTitle" to Schema.string(description = "Concise localized title for the diagnosed problem."),
                "matchedProblemId" to Schema.string(description = "Exact KB problem ID (e.g. AND-05-001) or CLARIFY-REQUIRED."),
                "confidenceLevel" to Schema.string(description = "High, Medium, or Low."),
                "confirmedEvidence" to Schema.array(
                    items = Schema.string(),
                    description = "Only telemetry readings explicitly marked [VERIFIED]. Never invent readings."
                ),
                "possibleCauses" to Schema.array(
                    items = Schema.string(),
                    description = "Probable root causes clearly framed as possibilities, not unverified facts."
                ),
                "diagnosticQuestions" to Schema.array(
                    items = Schema.string(),
                    description = "Focused clarifying or confirming questions."
                ),
                "unverifiedAssumptions" to Schema.array(
                    items = Schema.string(),
                    description = "Metrics marked [NOT COLLECTED] or sandboxed OS internals."
                ),
                "recommendedActionIds" to Schema.array(
                    items = Schema.string(),
                    description = "Valid action IDs chosen strictly from APPROVED ACTION CATALOG."
                ),
                "hardwareFaultIndicators" to Schema.array(
                    items = Schema.string(),
                    description = "Signs pointing to physical hardware fault requiring service."
                ),
                "escalationInstructions" to Schema.string(description = "Safe escalation guidance if steps do not resolve the problem."),
                "verificationMethod" to Schema.string(description = "How to verify telemetry/behavior improvement after repair.")
            ),
            optionalProperties = listOf("unverifiedAssumptions", "hardwareFaultIndicators", "verificationMethod")
        )
    }

    suspend fun diagnoseAndGuide(
        userProblem: String,
        vitals: DeviceVitals,
        screenshot: Bitmap? = null,
        conversationHistory: List<Pair<String, String>> = emptyList(),
        context: Context? = null,
        language: String = LanguageDetector.LANG_ENGLISH
    ): DoctorResponse = withContext(Dispatchers.IO) {
        if (context != null) {
            runCatching { KnowledgeRepository.ensureLoadedSynchronously(context) }
        }

        val effectiveLanguage = LanguageDetector.detectResponseLanguage(userProblem, language)
        val vitalsSummary = runCatching {
            RealDeviceDiagnostics.formatVitalsSummary(vitals, effectiveLanguage)
        }.getOrDefault("Device: ${vitals.brand} ${vitals.model}, Android ${vitals.androidVersion}")

        val oemProfile = runCatching {
            DeviceCompatibilityEngine.resolveProfile(
                manufacturer = vitals.manufacturer,
                brand = vitals.brand,
                model = vitals.model,
                apiLevel = vitals.apiLevel,
                androidVersion = vitals.androidVersion
            )
        }.getOrElse { DeviceCompatibilityEngine.getProfile() }

        val retrievalOutcome = runCatching {
            PlaybookRagRetriever.retrieveIssueOutcome(
                query = userProblem,
                vitals = vitals,
                context = context,
                limit = 4
            )
        }.getOrElse {
            RagRetrievalOutcome(matches = emptyList(), isAmbiguous = true)
        }

        val issueMatches = retrievalOutcome.matches
        val ragContext = if (issueMatches.isNotEmpty()) {
            PlaybookRagRetriever.formatIssueRagContextForPrompt(issueMatches, oemProfile, vitals)
        } else if (retrievalOutcome.isAmbiguous) {
            "AMBIGUOUS COMPLAINT NOTICE: The user's complaint is too broad to select a specific AND-XX-YYY problem safely. Ask 2-3 focused clarifying questions FIRST before diagnosing or recommending invasive fixes.\nClarifying options: ${retrievalOutcome.clarifyingQuestions.joinToString(" | ")}"
        } else {
            val pbMatches = PlaybookRagRetriever.retrieveRelevantPlaybooks(userProblem, vitals, limit = 3)
            PlaybookRagRetriever.formatRagContextForPrompt(pbMatches, oemProfile)
        }

        val validActionCatalog = SafeRepairRegistry.approvedActions.joinToString("\n") { action ->
            val modeLabel = if (action.capability == AutomationCapability.NORMAL_API) {
                "AUTO IN-APP"
            } else {
                "REQUIRES USER PERMISSION / SYSTEM SETTINGS (${action.capability.badgeLabel})"
            }
            "- ${action.id}: ${action.title} [$modeLabel, ${action.riskClass.label}]"
        }

        val oemContext = buildString {
            appendLine("Brand/OEM: ${oemProfile.oemName} (${oemProfile.osFlavor}) | ID: ${oemProfile.profileId}")
            appendLine("Android Version: ${oemProfile.androidCompatibility.versionLabel}")
            appendLine("Coverage Tier: ${oemProfile.coverageTier.code} (${oemProfile.coverageTier.label}) | Path Verified: ${oemProfile.isPathVerifiedForBuild}")
            appendLine("GMS Available: ${oemProfile.gmsAvailable}")
            append("API Restrictions: ${oemProfile.androidCompatibility.apiRestrictions.joinToString("; ")}")
        }

        val fullPrompt = buildPrompt(
            userProblem = userProblem,
            vitalsSummary = vitalsSummary,
            ragContext = ragContext,
            validActionCatalog = validActionCatalog,
            oemContext = oemContext,
            conversationHistory = conversationHistory,
            effectiveLanguage = effectiveLanguage,
            hasScreenshot = screenshot != null
        )

        val proxyBase = aiProxyUrl.trim().trimEnd('/')
        try {
            val (jsonString, modelUsed) = withTimeout(55_000L) {
                if (proxyBase.isNotBlank()) {
                    callSecureBackendProxy(
                        proxyBaseUrl = proxyBase,
                        userProblem = userProblem,
                        vitalsSummary = vitalsSummary,
                        ragContext = ragContext,
                        validActionCatalog = validActionCatalog,
                        oemContext = oemContext,
                        history = conversationHistory,
                        language = effectiveLanguage,
                        screenshot = screenshot,
                        rawPrompt = fullPrompt
                    )
                } else {
                    callFirebaseAiLogic(
                        prompt = fullPrompt,
                        screenshot = screenshot
                    )
                }
            }

            parseStructuredGeminiJson(
                jsonString = jsonString,
                vitals = vitals,
                oemProfile = oemProfile,
                topIssueMatch = issueMatches.firstOrNull(),
                language = effectiveLanguage,
                modelUsed = modelUsed,
                context = context,
                userProblem = userProblem
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Cloud AI request timed out after 55s", e)
            buildStructuredFallbackResponse(
                userProblem = userProblem,
                vitals = vitals,
                oemProfile = oemProfile,
                status = AiServiceStatus.TIMEOUT_FALLBACK,
                statusDetail = when (effectiveLanguage) {
                    LanguageDetector.LANG_HINDI -> "क्लाउड एआई का समय समाप्त हो गया (55 सेकंड)। कृपया पुनः प्रयास करें।"
                    LanguageDetector.LANG_HINGLISH -> "Cloud AI request time out ho gaya (55s). Neeche Verified KB steps hain, aap Retry kar sakte hain."
                    else -> "Cloud AI request timed out after 55s. Tap Retry to re-query Gemini."
                },
                context = context,
                precomputedMatches = issueMatches,
                isAmbiguousOverride = retrievalOutcome.isAmbiguous,
                clarifyingQuestionsOverride = retrievalOutcome.clarifyingQuestions,
                language = effectiveLanguage
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "Cloud AI request failed safely (surfacing real error & fallback KB): ${t.localizedMessage}", t)
            val rawDetail = (t.localizedMessage ?: t::class.java.simpleName).take(220)
            buildStructuredFallbackResponse(
                userProblem = userProblem,
                vitals = vitals,
                oemProfile = oemProfile,
                status = AiServiceStatus.ERROR_FALLBACK,
                statusDetail = when (effectiveLanguage) {
                    LanguageDetector.LANG_HINDI -> "क्लाउड एआई अनुपलब्ध: $rawDetail"
                    LanguageDetector.LANG_HINGLISH -> "Cloud AI unavailable: $rawDetail"
                    else -> "Cloud AI unavailable: $rawDetail"
                },
                context = context,
                precomputedMatches = issueMatches,
                isAmbiguousOverride = retrievalOutcome.isAmbiguous,
                clarifyingQuestionsOverride = retrievalOutcome.clarifyingQuestions,
                language = effectiveLanguage
            )
        }
    }

    private fun buildPrompt(
        userProblem: String,
        vitalsSummary: String,
        ragContext: String,
        validActionCatalog: String,
        oemContext: String,
        conversationHistory: List<Pair<String, String>>,
        effectiveLanguage: String,
        hasScreenshot: Boolean
    ): String {
        val languageDirective = when (effectiveLanguage) {
            LanguageDetector.LANG_HINDI -> """
                Target Language: Pure Devanagari Hindi (शुद्ध देवनागरी हिंदी).
                - Write 100% of EVERY JSON string value (replyText, problemTitle, confirmedEvidence, possibleCauses, diagnosticQuestions, unverifiedAssumptions, hardwareFaultIndicators, escalationInstructions, verificationMethod) in pure Devanagari Hindi script.
                - NEVER write English sentences, headings, bullet labels, or Hinglish. Only keep essential short technical tokens (Wi-Fi, RAM, Bluetooth, GPS, USB, 5G) where genuinely needed.
            """.trimIndent()
            LanguageDetector.LANG_HINGLISH -> """
                Target Language: Natural Hinglish (conversational Hindi-English in Latin script).
                - Write 100% of EVERY JSON string value consistently in natural Hinglish.
                - NEVER switch mid-response into pure formal English or Devanagari Hindi.
            """.trimIndent()
            else -> """
                Target Language: Pure Practical English.
                - Write 100% of EVERY JSON string value strictly in clear English with zero Hindi/Hinglish mixing.
            """.trimIndent()
        }

        val historyPrompt = if (conversationHistory.isNotEmpty()) {
            "RECENT CONVERSATION:\n" + conversationHistory.takeLast(4).joinToString("\n") { (role, msg) ->
                "${if (role == "user") "User" else "Doctor"}: $msg"
            } + "\n\n"
        } else ""

        return """
You are AI Smartphone Doctor, an evidence-based Android hardware & software diagnostic expert.
CORE PHILOSOPHY: Diagnose first with verified telemetry. Explain clearly. Separate automatic in-app checks from actions needing user permission/settings. Verify outcome last.

STRICT RULES:
1. TELEMETRY HONESTY: NEVER invent sensor numbers, RAM values, battery levels, or overheating claims. Use ONLY readings marked [VERIFIED] in telemetry below. If a reading says [NOT COLLECTED] or Unavailable, explicitly list it under `unverifiedAssumptions`, NOT `confirmedEvidence`, and never pretend the fault is confirmed without evidence.
2. EVIDENCE VS CAUSES: Clearly distinguish confirmed telemetry evidence (`confirmedEvidence`) from suspected root causes (`possibleCauses`).
3. CLARIFY WHEN VAGUE: If the user's complaint is broad or marked AMBIGUOUS COMPLAINT NOTICE, set `confidenceLevel` to "Low", `matchedProblemId` to "CLARIFY-REQUIRED", and ask focused `diagnosticQuestions` first.
4. AUTONOMOUS EXECUTION OVER MANUAL LECTURES: Choose `recommendedActionIds` ONLY from APPROVED ACTION CATALOG below. Whenever an `AUTO IN-APP` executor handles a step (e.g., own cache & stale APK purge, DNS/captive-portal audit, audio routing/volume fix, RAM trim, thermal audit, or 30s timeout/brightness with WRITE_SETTINGS), NEVER tell the user to manually do that step — include its ID and tell the user to authorize the Autonomous Repair Plan card below. For OS-sandboxed steps (3rd-party cache, force-stop, Wi-Fi/Data radio toggles), explain the Android restriction honestly and attach the guided Settings action.
5. LANGUAGE CONSISTENCY:
$languageDirective
6. SAFETY: Never instruct silent third-party cache wipes or hidden background changes, and never claim a problem is repaired when only instructions or Settings links were provided. Never cite internal KB codes (like AND-05-001) or severity tags (P2/R1) inside `replyText`.

DEVICE & COMPATIBILITY PROFILE:
$oemContext

$ragContext

APPROVED ACTION CATALOG (ONLY USE THESE IDs):
$validActionCatalog

$historyPrompt
VERIFIED LIVE DEVICE TELEMETRY:
$vitalsSummary

USER PROBLEM:
$userProblem
${if (hasScreenshot) "(User attached a device screenshot — analyze visible error messages, settings screens, or battery/storage charts without exposing sensitive personal data.)" else ""}
        """.trimIndent()
    }

    /**
     * Primary Android-native Cloud AI path using Firebase AI Logic SDK + App Check.
     * Contains ZERO API keys in APK bytecode.
     */
    private suspend fun callFirebaseAiLogic(
        prompt: String,
        screenshot: Bitmap?
    ): Pair<String, String> {
        var lastError = "Unable to reach Firebase AI Logic"
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = diagnosisSchema
            temperature = 0.3f
        }

        for (modelName in SUPPORTED_GEMINI_MODELS) {
            coroutineContext.ensureActive()
            try {
                val model = Firebase.ai.generativeModel(
                    modelName = modelName,
                    generationConfig = config
                )
                val response = withTimeout(20_000L) {
                    if (screenshot != null) {
                        model.generateContent(
                            content {
                                text(prompt)
                                image(screenshot)
                            }
                        )
                    } else {
                        model.generateContent(prompt)
                    }
                }
                val text = response.text?.trim().orEmpty()
                if (text.isNotBlank()) {
                    return stripMarkdownJsonFences(text) to modelName
                }
                lastError = "$modelName returned empty response"
            } catch (timeout: TimeoutCancellationException) {
                coroutineContext.ensureActive()
                lastError = "$modelName timed out after 20s"
                Log.w(TAG, "Model $modelName timed out; trying fallback if available")
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Throwable) {
                val msg = t.localizedMessage ?: t::class.java.simpleName
                lastError = "$modelName: $msg"
                Log.w(TAG, "Firebase AI call failed for $modelName: $msg")
                if (isFatalAuthOrAppCheckError(msg)) {
                    throw IllegalStateException(lastError, t)
                }
            }
        }
        throw IllegalStateException(lastError)
    }

    /**
     * Optional self-hosted secure backend proxy path (`/v1/diagnose`).
     * The Gemini API key lives strictly on the backend server as an environment secret.
     */
    private fun callSecureBackendProxy(
        proxyBaseUrl: String,
        userProblem: String,
        vitalsSummary: String,
        ragContext: String,
        validActionCatalog: String,
        oemContext: String,
        history: List<Pair<String, String>>,
        language: String,
        screenshot: Bitmap?,
        rawPrompt: String
    ): Pair<String, String> {
        val historyArr = JSONArray().apply {
            history.takeLast(4).forEach { (role, msg) ->
                put(JSONObject().put("role", role).put("text", msg))
            }
        }

        val screenshotBase64 = screenshot?.let { bmp ->
            val stream = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        }

        val payload = JSONObject().apply {
            put("userProblem", userProblem)
            put("vitalsSummary", vitalsSummary)
            put("ragContext", ragContext)
            put("validActionCatalog", validActionCatalog)
            put("oemContext", oemContext)
            put("history", historyArr)
            put("language", language)
            put("rawPrompt", rawPrompt)
            if (screenshotBase64 != null) {
                put("screenshotBase64", screenshotBase64)
            }
        }.toString()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val request = Request.Builder()
            .url("$proxyBaseUrl/v1/diagnose")
            .post(payload.toRequestBody(mediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (response.isSuccessful && rawBody.isNotBlank()) {
                val root = JSONObject(rawBody)
                val modelUsed = root.optString("modelUsed").ifBlank { SUPPORTED_GEMINI_MODELS.first() }
                val rawJson = root.optString("rawJson").ifBlank {
                    root.optJSONObject("data")?.toString().orEmpty()
                }
                if (rawJson.isNotBlank()) {
                    return stripMarkdownJsonFences(rawJson) to modelUsed
                }
                throw IllegalStateException("Proxy HTTP ${response.code}: Empty diagnostic payload")
            } else {
                val apiErr = runCatching {
                    JSONObject(rawBody).optJSONObject("error")?.optString("message")
                }.getOrNull()?.takeIf { it.isNotBlank() }
                val errText = apiErr ?: "HTTP ${response.code}"
                throw IllegalStateException(errText)
            }
        }
    }

    private fun isFatalAuthOrAppCheckError(msg: String): Boolean {
        val lower = msg.lowercase()
        return lower.contains("401") ||
            lower.contains("403") ||
            lower.contains("app check") ||
            lower.contains("appcheck") ||
            lower.contains("api key") ||
            lower.contains("permission_denied")
    }

    private fun stripMarkdownJsonFences(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val firstNewline = trimmed.indexOf('\n')
        val body = if (firstNewline != -1) trimmed.substring(firstNewline + 1) else trimmed.removePrefix("```")
        return body.removeSuffix("```").trim()
    }

    internal fun parseStructuredGeminiJson(
        jsonString: String,
        vitals: DeviceVitals,
        oemProfile: OemCompatibilityProfile,
        topIssueMatch: IssueRagMatch? = null,
        fallbackPlaybookId: String? = null,
        language: String = LanguageDetector.LANG_ENGLISH,
        modelUsed: String = SUPPORTED_GEMINI_MODELS.first(),
        context: Context? = null,
        userProblem: String = ""
    ): DoctorResponse {
        val cleanJson = stripMarkdownJsonFences(jsonString)
        val json = JSONObject(cleanJson)
        val effectiveLanguage = LanguageDetector.normalizeUiLanguage(language)

        val replyText = json.optString("replyText").ifBlank {
            when (effectiveLanguage) {
                LanguageDetector.LANG_HINDI -> "${vitals.brand} ${vitals.model} के सत्यापित टेलीमेट्री के आधार पर निदान पूरा हो गया है।"
                LanguageDetector.LANG_HINGLISH -> "${vitals.brand} ${vitals.model} ke verified telemetry ke hisab se diagnosis complete ho gaya hai."
                else -> "Diagnostic analysis completed based on verified telemetry for ${vitals.brand} ${vitals.model}."
            }
        }

        val matchedId = json.optString("matchedProblemId")
            .ifBlank { json.optString("matchedPlaybookId") }
            .ifBlank { topIssueMatch?.issue?.problemId ?: fallbackPlaybookId ?: "AND-02-001" }

        val issueRecord = KnowledgeRepository.findIssueById(matchedId) ?: topIssueMatch?.issue
        val legacyPlaybook = if (issueRecord == null) PlaybookRagRetriever.findById(matchedId) else null

        val actionIds = jsonArrayToList(json.optJSONArray("recommendedActionIds"))
        val validatedActions = actionIds
            .mapNotNull { SafeRepairRegistry.findAction(it) }
            .distinctBy { it.id }
            .ifEmpty {
                if (matchedId == "CLARIFY-REQUIRED") {
                    emptyList()
                } else {
                    issueRecord?.automaticActionIds?.mapNotNull { SafeRepairRegistry.findAction(it) }
                        ?: legacyPlaybook?.automaticActionIds?.mapNotNull { SafeRepairRegistry.findAction(it) }.orEmpty()
                }
            }

        val resolvedPath = issueRecord?.let {
            DeviceCompatibilityEngine.resolveSettingsPath(it.oemSettingsKey, oemProfile).resolvedPath
        }

        val problemTitleFromJson = json.optString("problemTitle").trim()
        val resolvedTitle = problemTitleFromJson.ifBlank {
            when (effectiveLanguage) {
                LanguageDetector.LANG_HINDI -> when {
                    matchedId == "CLARIFY-REQUIRED" -> "निदान से पहले स्पष्टीकरण आवश्यक"
                    issueRecord != null -> LocalizedDiagnosticFormatter.buildHindiIssueSummaryTitle(issueRecord)
                    else -> "एआई निदान योजना"
                }
                LanguageDetector.LANG_HINGLISH -> when {
                    matchedId == "CLARIFY-REQUIRED" -> "Sahi Diagnosis Ke Liye Clarify Zaroori Hai"
                    issueRecord != null -> issueRecord.canonicalProblemName
                    else -> "AI Diagnostic Plan"
                }
                else -> issueRecord?.canonicalProblemName
                    ?: legacyPlaybook?.problemTitle
                    ?: if (matchedId == "CLARIFY-REQUIRED") "Clarification Needed Before Repair" else "AI Diagnostic Plan"
            }
        }

        val verifiedEvidence = jsonArrayToList(json.optJSONArray("confirmedEvidence")).ifEmpty {
            LocalizedDiagnosticFormatter.buildVerifiedTelemetryEvidence(vitals, oemProfile, effectiveLanguage)
        }

        val unverifiedAssumptions = jsonArrayToList(json.optJSONArray("unverifiedAssumptions")).ifEmpty {
            LocalizedDiagnosticFormatter.buildUnverifiedAssumptions(vitals, oemProfile, effectiveLanguage)
        }

        val possibleCauses = jsonArrayToList(json.optJSONArray("possibleCauses")).ifEmpty {
            when {
                effectiveLanguage == LanguageDetector.LANG_HINDI && issueRecord != null ->
                    LocalizedDiagnosticFormatter.localizeHindiPossibleCauses(issueRecord)
                else -> issueRecord?.possibleCauses ?: legacyPlaybook?.possibleCauses.orEmpty()
            }
        }

        val diagnosticQuestions = jsonArrayToList(json.optJSONArray("diagnosticQuestions")).ifEmpty {
            when {
                effectiveLanguage == LanguageDetector.LANG_HINDI && issueRecord != null ->
                    LocalizedDiagnosticFormatter.localizeHindiQuestions(issueRecord)
                else -> issueRecord?.diagnosticQuestions.orEmpty()
            }
        }

        val hardwareIndicators = jsonArrayToList(json.optJSONArray("hardwareFaultIndicators")).ifEmpty {
            when {
                effectiveLanguage == LanguageDetector.LANG_HINDI && issueRecord != null ->
                    LocalizedDiagnosticFormatter.localizeHindiHardwareIndicators(issueRecord)
                else -> issueRecord?.hardwareIndicators ?: legacyPlaybook?.hardwareFaultIndicators.orEmpty()
            }
        }

        val escalationText = json.optString("escalationInstructions").ifBlank {
            when (effectiveLanguage) {
                LanguageDetector.LANG_HINDI -> "यदि सुरक्षित उपायों से समस्या हल न हो, तो सेफ मोड में जांचें या अधिकृत सेवा केंद्र से संपर्क करें।"
                LanguageDetector.LANG_HINGLISH -> "Agar safe steps se issue solve na ho, toh Safe Mode mein check karein ya authorized service center par dikhayein."
                else -> issueRecord?.escalation?.joinToString(" ")
                    ?: legacyPlaybook?.escalationInstructions
                    ?: "If issue persists after safe repairs, test in Safe Mode or consult authorized OEM support."
            }
        }

        val verificationText = json.optString("verificationMethod").ifBlank {
            when (effectiveLanguage) {
                LanguageDetector.LANG_HINDI -> "सुधार के बाद लाइव टेलीमेट्री की तुलना करें और पुष्टि करें कि समस्या ठीक हुई या नहीं।"
                LanguageDetector.LANG_HINGLISH -> "Action chalane ke baad before/after telemetry compare karein aur confirm karein ki problem solve hui."
                else -> issueRecord?.verification?.joinToString(" ")
                    ?: legacyPlaybook?.verificationMethod
                    ?: "Compare before/after verified telemetry and confirm user outcome."
            }
        }

        val plan = RepairPlan(
            playbookId = matchedId,
            problemTitle = resolvedTitle,
            confidenceLevel = json.optString("confidenceLevel", "Medium"),
            confirmedEvidence = verifiedEvidence,
            possibleCauses = possibleCauses,
            diagnosticQuestions = diagnosticQuestions,
            unverifiedAssumptions = unverifiedAssumptions,
            hardwareFaultIndicators = hardwareIndicators,
            escalationInstructions = escalationText,
            verificationMethod = verificationText,
            matchedIssueId = issueRecord?.problemId,
            sourceCitation = issueRecord?.let { "${it.sourcePdf} (p.${it.sourcePage}) • ${it.verificationStatus} v${it.version}" },
            severityLabel = issueRecord?.severity,
            riskTierLabel = issueRecord?.riskTier,
            coverageTierLabel = "${oemProfile.coverageTier.code} (${oemProfile.oemName})",
            oemResolvedPath = resolvedPath,
            relatedOverlapIds = issueRecord?.relatedOverlaps.orEmpty()
        )

        val bundle = AutonomousRepairOrchestrator.buildBundle(
            context = context,
            userProblem = userProblem.ifBlank { resolvedTitle },
            vitals = vitals,
            recommendedActions = validatedActions,
            repairPlan = plan,
            oemProfile = oemProfile,
            language = effectiveLanguage
        )

        return DoctorResponse(
            replyText = replyText,
            actions = validatedActions,
            repairPlan = plan,
            autonomousBundle = bundle,
            aiStatus = AiServiceStatus.ONLINE_GEMINI,
            aiStatusDetail = "Cloud AI ($modelUsed) • Verified Telemetry + KB RAG (${oemProfile.oemName})",
            responseLanguage = effectiveLanguage
        )
    }

    /**
     * Structured 466-Issue KB + Compatibility RAG Fallback Engine.
     * Respects Hindi / Hinglish / English language consistency and NEVER emits unverified telemetry numbers.
     */
    fun buildStructuredFallbackResponse(
        userProblem: String,
        vitals: DeviceVitals,
        oemProfile: OemCompatibilityProfile = DeviceCompatibilityEngine.getProfile(),
        status: AiServiceStatus = AiServiceStatus.UNAVAILABLE_OFFLINE_FALLBACK,
        statusDetail: String = "Cloud AI offline — using verified KB v2.0 + Compatibility v3.0 RAG.",
        context: Context? = null,
        precomputedMatches: List<IssueRagMatch>? = null,
        isAmbiguousOverride: Boolean? = null,
        clarifyingQuestionsOverride: List<String>? = null,
        language: String = LanguageDetector.LANG_ENGLISH
    ): DoctorResponse {
        if (context != null) {
            runCatching { KnowledgeRepository.ensureLoadedSynchronously(context) }
        }
        val effectiveLanguage = LanguageDetector.detectResponseLanguage(userProblem, language)

        val outcome = if (precomputedMatches == null) {
            PlaybookRagRetriever.retrieveIssueOutcome(
                query = userProblem,
                vitals = vitals,
                context = context,
                limit = 3
            )
        } else {
            RagRetrievalOutcome(
                matches = precomputedMatches,
                isAmbiguous = isAmbiguousOverride ?: precomputedMatches.isEmpty(),
                clarifyingQuestions = clarifyingQuestionsOverride.orEmpty()
            )
        }

        val evidence = LocalizedDiagnosticFormatter.buildVerifiedTelemetryEvidence(vitals, oemProfile, effectiveLanguage)
        val unverified = LocalizedDiagnosticFormatter.buildUnverifiedAssumptions(vitals, oemProfile, effectiveLanguage)

        if (outcome.isAmbiguous && outcome.matches.isEmpty()) {
            val questions = when (effectiveLanguage) {
                LanguageDetector.LANG_HINDI -> listOf(
                    "आपके फोन के किस हिस्से में समस्या आ रही है — धीमा चलना/हैंंग होना, बैटरी/चार्जिंग, गर्म होना, वाई-फाई/इंटरनेट, ब्लूटूथ, स्क्रीन/टच, या कोई खास ऐप?",
                    "क्या कोई सटीक त्रुटि संदेश दिख रहा है या यह समस्या किसी सिस्टम अपडेट के बाद शुरू हुई?",
                    "क्या यह दिक्कत लगातार हो रही है या केवल चार्जिंग, कॉलिंग या गेमिंग के समय होती है?"
                )
                LanguageDetector.LANG_HINGLISH -> listOf(
                    "Phone ke kis hisse mein problem aa rahi hai — hang/slow, battery/charging, heating, Wi-Fi/internet, Bluetooth, touch/display ya koi specific app?",
                    "Kya koi exact error message dikh raha hai ya system update ke baad shuru hua?",
                    "Kya yeh dikkat hamesha hoti hai ya sirf charging, calling ya gaming ke time?"
                )
                else -> outcome.clarifyingQuestions.ifEmpty {
                    listOf("Which specific area or symptom is failing (battery/charging, lag/hang, heating, Wi-Fi/SIM, Bluetooth, touch/display, audio, or a specific app)?")
                }
            }

            val clarifyReply = when (effectiveLanguage) {
                LanguageDetector.LANG_HINDI -> """
⚠️ **सूचना (क्लाउड एआई उपलब्ध नहीं — स्थानीय सत्यापित ज्ञानकोश मोड):**

गलत निदान से बचने के लिए कृपया अपनी समस्या के बारे में थोड़ा और स्पष्ट विवरण दें, क्योंकि आपका संदेश अभी सामान्य है।

**कृपया पहले इन त्वरित प्रश्नों के उत्तर दें:**
${questions.mapIndexed { i, q -> "${i + 1}. $q" }.joinToString("\n")}

तब तक आप नीचे दिए गए **स्वचालित सुरक्षित परीक्षण** चला सकते हैं (और ऊपर क्लाउड एआई से पुनः प्रयास कर सकते हैं)।
                """.trimIndent()
                LanguageDetector.LANG_HINGLISH -> """
⚠️ **Notice (Cloud AI abhi unavailable hai — Local Verified KB Mode):**

Galat diagnosis se bachne ke liye thoda aur detail batayein, kyunki abhi aapka message kaafi general hai ("$userProblem").

**Pehle in quick sawalon ka jawab dein:**
${questions.mapIndexed { i, q -> "${i + 1}. $q" }.joinToString("\n")}

Tab tak aap neeche diye **Automatic In-App Checks** chala sakte hain ya Cloud AI ko **Retry** kar sakte hain.
                """.trimIndent()
                else -> """
⚠️ **Notice (Cloud AI unavailable — Local Verified KB Mode):**

To avoid an incorrect diagnosis, I need a bit more detail because your complaint ("$userProblem") is currently too broad.

**Please answer these quick diagnostic questions first:**
${questions.mapIndexed { i, q -> "${i + 1}. $q" }.joinToString("\n")}

Meanwhile, you can run the read-only automatic checks below or tap Retry to reconnect to Cloud AI.
                """.trimIndent()
            }

            val safeTriageActions = listOfNotNull(
                SafeRepairRegistry.findAction("auto_audit_thermal_power"),
                SafeRepairRegistry.findAction("auto_optimize_memory"),
                SafeRepairRegistry.findAction("repair_settings_search")
            )
            return DoctorResponse(
                replyText = clarifyReply,
                actions = safeTriageActions,
                repairPlan = RepairPlan(
                    playbookId = "CLARIFY-REQUIRED",
                    problemTitle = when (effectiveLanguage) {
                        LanguageDetector.LANG_HINDI -> "निदान से पहले स्पष्टीकरण आवश्यक"
                        LanguageDetector.LANG_HINGLISH -> "Diagnosis Se Pehle Clarify Zaroori Hai"
                        else -> "Clarifying Triage Required Before Diagnosis"
                    },
                    confidenceLevel = "Low",
                    confirmedEvidence = evidence,
                    possibleCauses = when (effectiveLanguage) {
                        LanguageDetector.LANG_HINDI -> listOf("लक्षण अभी अस्पष्ट हैं — सही मरम्मत चुनने के लिए स्पष्ट विवरण की प्रतीक्षा है।")
                        LanguageDetector.LANG_HINGLISH -> listOf("Symptom abhi unclear hai — sahi repair workflow chunnay ke liye user detail zaroori hai.")
                        else -> listOf("Symptom underspecified — waiting for user clarification before selecting repair workflow.")
                    },
                    diagnosticQuestions = questions,
                    unverifiedAssumptions = unverified,
                    hardwareFaultIndicators = when (effectiveLanguage) {
                        LanguageDetector.LANG_HINDI -> listOf("यदि फोन में भौतिक क्षति, बैटरी फूलना या स्क्रीन टूटना दिखता है, तो तुरंत सर्विस केंद्र जाएं।")
                        LanguageDetector.LANG_HINGLISH -> listOf("Agar physical damage, battery swelling ya toota screen ho toh turant service center dikhayein.")
                        else -> listOf("If physical damage, swelling, burnt port, or dead display exists, stop and seek service.")
                    },
                    escalationInstructions = when (effectiveLanguage) {
                        LanguageDetector.LANG_HINDI -> "प्रभावित सुविधा या सटीक त्रुटि संदेश बताएं ताकि सटीक सत्यापित समाधान मिल सके।"
                        LanguageDetector.LANG_HINGLISH -> "Exact feature ya error message likhein taaki sahi verified KB workflow mil sake."
                        else -> "Reply with the affected feature or exact error message to match the verified KB v2.0 record."
                    },
                    verificationMethod = when (effectiveLanguage) {
                        LanguageDetector.LANG_HINDI -> "उपयोगकर्ता के स्पष्ट उत्तर की प्रतीक्षा है।"
                        LanguageDetector.LANG_HINGLISH -> "User ke exact symptom detail ka wait kiya ja raha hai."
                        else -> "Awaiting user symptom clarification."
                    },
                    coverageTierLabel = "${oemProfile.coverageTier.code} (${oemProfile.oemName})"
                ),
                aiStatus = status,
                aiStatusDetail = statusDetail,
                responseLanguage = effectiveLanguage
            )
        }

        val topIssueMatch = outcome.matches.firstOrNull()
        if (topIssueMatch != null) {
            val issue = topIssueMatch.issue
            val actions = topIssueMatch.allowedActions.ifEmpty {
                issue.automaticActionIds.mapNotNull { SafeRepairRegistry.findAction(it) }.distinctBy { it.id }
            }

            val telemetryLine = buildConciseVerifiedLine(vitals, effectiveLanguage)

            val reply = when (effectiveLanguage) {
                LanguageDetector.LANG_HINDI -> {
                    val hiTitle = LocalizedDiagnosticFormatter.buildHindiIssueSummaryTitle(issue)
                    val hiSteps = LocalizedDiagnosticFormatter.buildHindiFallbackSteps(issue, topIssueMatch.resolvedSettingsPath)
                    val firstHiQuestion = LocalizedDiagnosticFormatter.localizeHindiQuestions(issue).firstOrNull()
                    """
नमस्ते! आपके **${vitals.brand} ${vitals.model}** की सत्यापित टेलीमेट्री जाँच पूरी हो गई है। *(स्थानीय ज्ञानकोश मोड — क्लाउड एआई उपलब्ध नहीं)*

**संभावित समस्या क्षेत्र:** $hiTitle
$telemetryLine

**व्यावहारिक समाधान चरण:**
${hiSteps.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")}

${if (firstHiQuestion != null) "**पुष्टि प्रश्न:** $firstHiQuestion\n\n" else ""}नीचे दिए कार्यों में **स्वचालित इन-ऐप जाँच** और **उपयोगकर्ता अनुमति / सिस्टम सेटिंग्स** स्पष्ट रूप से अलग दिखाए गए हैं। ऊपर क्लाउड एआई के लिए **पुनः प्रयास** भी उपलब्ध है।
                    """.trimIndent()
                }
                LanguageDetector.LANG_HINGLISH -> {
                    val steps = issue.solutions
                    val firstQ = issue.diagnosticQuestions.firstOrNull()
                    """
Namaste! Aapke **${vitals.brand} ${vitals.model}** ki verified telemetry check ho gayi hai. *(Offline KB Mode — Cloud AI abhi unavailable hai)*

**Possible Issue:** ${issue.canonicalProblemName} (${issue.categoryName})
$telemetryLine

**Step-by-Step Safe Solution:**
${steps.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")}

${if (firstQ != null) "**Ek Chhota Sawal:** $firstQ\n\n" else ""}Neeche diye gaye cards mein **Automatic In-App Actions** aur **Permission / Settings Required** alag-alag dikhaye gaye hain. Aap upar **Retry** bhi kar sakte hain.
                    """.trimIndent()
                }
                else -> {
                    val firstQuestion = issue.diagnosticQuestions.firstOrNull()
                    """
Hello! Telemetry triage for your **${vitals.brand} ${vitals.model}** is complete. *(Offline KB Mode — Cloud AI unavailable)*

**Possible Issue Match:** ${issue.canonicalProblemName} (${issue.categoryName})
$telemetryLine

**Practical Safe Solutions:**
${issue.solutions.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")}

${if (firstQuestion != null) "**Confirming Question:** $firstQuestion\n\n" else ""}Below, automatic in-app diagnostics are clearly separated from actions that require your permission or opening System Settings.
                    """.trimIndent()
                }
            }

            val plan = when (effectiveLanguage) {
                LanguageDetector.LANG_HINDI -> RepairPlan(
                    playbookId = issue.problemId,
                    problemTitle = LocalizedDiagnosticFormatter.buildHindiIssueSummaryTitle(issue),
                    confidenceLevel = if (topIssueMatch.score >= 20) "उच्च (सत्यापित ज्ञानकोश)" else "मध्यम (टेलीमेट्री + ज्ञानकोश)",
                    confirmedEvidence = evidence,
                    possibleCauses = LocalizedDiagnosticFormatter.localizeHindiPossibleCauses(issue),
                    diagnosticQuestions = LocalizedDiagnosticFormatter.localizeHindiQuestions(issue),
                    unverifiedAssumptions = unverified,
                    hardwareFaultIndicators = LocalizedDiagnosticFormatter.localizeHindiHardwareIndicators(issue),
                    escalationInstructions = "यदि सुरक्षित चरणों से समस्या ठीक न हो, तो सेफ मोड में जांचें या अधिकृत सेवा केंद्र से संपर्क करें।",
                    verificationMethod = "हर कार्य से पहले और बाद की टेलीमेट्री तुलना देखें तथा परिणाम की पुष्टि करें।",
                    matchedIssueId = issue.problemId,
                    sourceCitation = "${issue.sourcePdf} (पृ.${issue.sourcePage}) • v${issue.version}",
                    severityLabel = issue.severity,
                    riskTierLabel = issue.riskTier,
                    coverageTierLabel = "${oemProfile.coverageTier.code} (${oemProfile.oemName})",
                    oemResolvedPath = topIssueMatch.resolvedSettingsPath,
                    relatedOverlapIds = issue.relatedOverlaps
                )
                else -> RepairPlan(
                    playbookId = issue.problemId,
                    problemTitle = "${issue.problemId}: ${issue.canonicalProblemName}",
                    confidenceLevel = if (topIssueMatch.score >= 20) "High (Exact KB Match)" else "Medium (KB + Telemetry)",
                    confirmedEvidence = evidence,
                    possibleCauses = issue.possibleCauses,
                    diagnosticQuestions = issue.diagnosticQuestions,
                    unverifiedAssumptions = unverified,
                    hardwareFaultIndicators = issue.hardwareIndicators,
                    escalationInstructions = issue.escalation.joinToString(" "),
                    verificationMethod = issue.verification.joinToString(" "),
                    matchedIssueId = issue.problemId,
                    sourceCitation = "${issue.sourcePdf} (p.${issue.sourcePage}) • ${issue.verificationStatus} v${issue.version}",
                    severityLabel = issue.severity,
                    riskTierLabel = issue.riskTier,
                    coverageTierLabel = "${oemProfile.coverageTier.code} (${oemProfile.oemName})",
                    oemResolvedPath = topIssueMatch.resolvedSettingsPath,
                    relatedOverlapIds = issue.relatedOverlaps
                )
            }

            val bundle = AutonomousRepairOrchestrator.buildBundle(
                context = context,
                userProblem = userProblem,
                vitals = vitals,
                recommendedActions = actions,
                repairPlan = plan,
                oemProfile = oemProfile,
                language = effectiveLanguage
            )

            return DoctorResponse(
                replyText = reply,
                actions = actions,
                repairPlan = plan,
                autonomousBundle = bundle,
                aiStatus = status,
                aiStatusDetail = statusDetail,
                responseLanguage = effectiveLanguage
            )
        }

        val matches = PlaybookRagRetriever.retrieveRelevantPlaybooks(userProblem, vitals, limit = 1)
        val topPlaybook = matches.first().playbook
        val actions = topPlaybook.automaticActionIds.mapNotNull { SafeRepairRegistry.findAction(it) }.distinctBy { it.id }
        val oemPath = topPlaybook.oemSettingsPathResolver(oemProfile)
        val reply = when (effectiveLanguage) {
            LanguageDetector.LANG_HINDI -> "⚠️ **सूचना (क्लाउड एआई उपलब्ध नहीं):**\n\n**${topPlaybook.problemTitle}**\n• **सेटिंग्स पथ:** `$oemPath`\n\n" +
                topPlaybook.stepByStepSolution.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
            LanguageDetector.LANG_HINGLISH -> "⚠️ **Notice (Cloud AI abhi unavailable hai):**\n\n**${topPlaybook.problemTitle}**\n• **OEM Settings Path:** `$oemPath`\n\n" +
                topPlaybook.stepByStepSolution.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
            else -> "⚠️ **Notice (Cloud AI unavailable):**\n\n**${topPlaybook.problemTitle}**\n• **OEM Path:** `$oemPath`\n\n" +
                topPlaybook.stepByStepSolution.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
        }
        val plan = RepairPlan(
            playbookId = topPlaybook.id,
            problemTitle = topPlaybook.problemTitle,
            confidenceLevel = "Medium (Offline Playbook)",
            confirmedEvidence = evidence,
            possibleCauses = topPlaybook.possibleCauses,
            diagnosticQuestions = topPlaybook.diagnosticQuestions,
            unverifiedAssumptions = unverified,
            hardwareFaultIndicators = topPlaybook.hardwareFaultIndicators,
            escalationInstructions = topPlaybook.escalationInstructions,
            verificationMethod = topPlaybook.verificationMethod
        )
        val fallbackBundle = AutonomousRepairOrchestrator.buildBundle(
            context = context,
            userProblem = userProblem,
            vitals = vitals,
            recommendedActions = actions,
            repairPlan = plan,
            oemProfile = oemProfile,
            language = effectiveLanguage
        )
        return DoctorResponse(
            replyText = reply,
            actions = actions,
            repairPlan = plan,
            autonomousBundle = fallbackBundle,
            aiStatus = status,
            aiStatusDetail = statusDetail,
            responseLanguage = effectiveLanguage
        )
    }

    private fun buildConciseVerifiedLine(vitals: DeviceVitals, language: String): String {
        val ramStr = if (vitals.ramVerified) RealDeviceDiagnostics.formatBytes(vitals.ramFreeBytes) else when (language) {
            LanguageDetector.LANG_HINDI -> "एकत्र नहीं"
            LanguageDetector.LANG_HINGLISH -> "Collect nahi hua"
            else -> "Not collected"
        }
        val batStr = if (vitals.batteryVerified) "${vitals.batteryLevel}%" else when (language) {
            LanguageDetector.LANG_HINDI -> "एकत्र नहीं"
            LanguageDetector.LANG_HINGLISH -> "Collect nahi hua"
            else -> "Not collected"
        }
        val tempStr = if (vitals.temperatureVerified) "${vitals.batteryTempC}°C" else when (language) {
            LanguageDetector.LANG_HINDI -> "एकत्र नहीं"
            LanguageDetector.LANG_HINGLISH -> "Collect nahi hua"
            else -> "Not collected"
        }
        return when (language) {
            LanguageDetector.LANG_HINDI ->
                "• **सत्यापित टेलीमेट्री:** स्वास्थ्य स्कोर ${vitals.healthScore}/100 • खाली रैम: $ramStr • बैटरी: $batStr • तापमान: $tempStr"
            LanguageDetector.LANG_HINGLISH ->
                "• **Verified Telemetry:** Health ${vitals.healthScore}/100 • RAM Free: $ramStr • Battery: $batStr • Temp: $tempStr"
            else ->
                "• **Verified Telemetry:** Health ${vitals.healthScore}/100 • RAM Free: $ramStr • Battery: $batStr • Temp: $tempStr"
        }
    }

    private fun jsonArrayToList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { idx ->
            array.optString(idx).takeIf { it.isNotBlank() }
        }
    }
}
