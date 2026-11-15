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
import com.example.util.AppCheckConfigStatus
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
    val responseLanguage: String = LanguageDetector.LANG_ENGLISH,
    val matchedProblemId: String? = null,
    val matchedProblemTitle: String? = null,
    val matchedCategory: String? = null,
    val intentHandled: ConversationIntent = ConversationIntent.PROBLEM_REPORT
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
        language: String = LanguageDetector.LANG_ENGLISH,
        sessionContext: TroubleshootingSessionContext = TroubleshootingSessionContext()
    ): DoctorResponse = withContext(Dispatchers.IO) {
        if (context != null) {
            runCatching { KnowledgeRepository.ensureLoadedSynchronously(context) }
        }

        val effectiveLanguage = LanguageDetector.detectResponseLanguage(userProblem, language)
        val intent = if (screenshot != null) {
            ConversationIntent.PROBLEM_REPORT
        } else {
            ConversationContextManager.classifyIntent(userProblem, sessionContext)
        }
        val deviceLabel = "${vitals.brand} ${vitals.model}".trim().ifBlank { "Android Smartphone" }
        val proxyBase = aiProxyUrl.trim().trimEnd('/')

        // 1. Handle normal human-like small talk gracefully without forcing robotic diagnostic forms
        if (intent == ConversationIntent.SMALL_TALK) {
            return@withContext handleSmallTalkTurn(
                userMessage = userProblem,
                vitals = vitals,
                deviceLabel = deviceLabel,
                conversationHistory = conversationHistory,
                effectiveLanguage = effectiveLanguage,
                proxyBase = proxyBase
            )
        }

        val vitalsSummary = runCatching {
            RealDeviceDiagnostics.formatVitalsSummary(vitals, effectiveLanguage)
        }.getOrDefault("Device: $deviceLabel, Android ${vitals.androidVersion}")

        val oemProfile = runCatching {
            DeviceCompatibilityEngine.resolveProfile(
                manufacturer = vitals.manufacturer,
                brand = vitals.brand,
                model = vitals.model,
                apiLevel = vitals.apiLevel,
                androidVersion = vitals.androidVersion
            )
        }.getOrElse { DeviceCompatibilityEngine.getProfile() }

        // For follow-up / clarify turns, anchor RAG lookup to active troubleshooting symptom when available
        val effectiveRagQuery = when {
            (intent == ConversationIntent.FOLLOW_UP_NOT_FIXED || intent == ConversationIntent.CLARIFY_EXPLANATION) &&
                !sessionContext.lastSymptomQuery.isNullOrBlank() -> sessionContext.lastSymptomQuery
            (intent == ConversationIntent.FOLLOW_UP_NOT_FIXED || intent == ConversationIntent.CLARIFY_EXPLANATION) &&
                !sessionContext.activeProblemId.isNullOrBlank() -> sessionContext.activeProblemId
            else -> userProblem
        }

        val retrievalOutcome = runCatching {
            PlaybookRagRetriever.retrieveIssueOutcome(
                query = effectiveRagQuery,
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
            val pbMatches = PlaybookRagRetriever.retrieveRelevantPlaybooks(effectiveRagQuery, vitals, limit = 3)
            PlaybookRagRetriever.formatRagContextForPrompt(pbMatches, oemProfile)
        }

        val excludedIds = sessionContext.excludedActionIds
        val validActionCatalog = SafeRepairRegistry.approvedActions
            .filter { it.id !in excludedIds }
            .ifEmpty { SafeRepairRegistry.approvedActions }
            .joinToString("\n") { action ->
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
            hasScreenshot = screenshot != null,
            intent = intent,
            sessionContext = sessionContext
        )

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
                userProblem = effectiveRagQuery,
                sessionContext = sessionContext,
                intent = intent
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
                language = effectiveLanguage,
                sessionContext = sessionContext,
                intentOverride = intent
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            val appCheckDiag = AppCheckConfigStatus.summary()
            Log.e(TAG, "Cloud AI request failed safely [$appCheckDiag] (surfacing real error & fallback KB): ${t.localizedMessage}", t)
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
                language = effectiveLanguage,
                sessionContext = sessionContext,
                intentOverride = intent
            )
        }
    }

    private suspend fun handleSmallTalkTurn(
        userMessage: String,
        vitals: DeviceVitals,
        deviceLabel: String,
        conversationHistory: List<Pair<String, String>>,
        effectiveLanguage: String,
        proxyBase: String
    ): DoctorResponse {
        val langRule = when (effectiveLanguage) {
            LanguageDetector.LANG_HINDI -> "Reply warmly in 100% pure Devanagari Hindi (शुद्ध देवनागरी हिंदी). No English sentences or Hinglish."
            LanguageDetector.LANG_HINGLISH -> "Reply warmly in natural conversational Hinglish (Roman Hindi-English). Do NOT switch to formal English or Devanagari."
            else -> "Reply warmly in natural, polite English with zero Hindi mixing."
        }
        val historyBlock = if (conversationHistory.isNotEmpty()) {
            "RECENT CHAT:\n" + conversationHistory.takeLast(4).joinToString("\n") { (r, m) -> "$r: $m" } + "\n\n"
        } else ""
        val chatPrompt = """
You are AI Smartphone Assistant (a helpful, polite, human-like Android assistant on $deviceLabel, Health Score ${vitals.healthScore}/100).
$langRule
The user sent casual conversation / greeting / thanks (NOT a repair form request).
Respond naturally in 1-3 friendly sentences like a helpful human technician chatting politely. Do not dump diagnostic checklists or fake repair cards.
$historyBlock
USER: $userMessage
        """.trimIndent()

        if (proxyBase.isBlank()) {
            val (cloudText, modelName) = runCatching {
                withTimeout(15_000L) { callFirebaseConversational(chatPrompt) }
            }.getOrDefault(null to SUPPORTED_GEMINI_MODELS.first())

            if (!cloudText.isNullOrBlank()) {
                return DoctorResponse(
                    replyText = cloudText,
                    actions = emptyList(),
                    repairPlan = null,
                    autonomousBundle = null,
                    aiStatus = AiServiceStatus.ONLINE_GEMINI,
                    aiStatusDetail = "Cloud AI ($modelName) • Conversational Mode",
                    responseLanguage = effectiveLanguage,
                    intentHandled = ConversationIntent.SMALL_TALK
                )
            }
        }

        return DoctorResponse(
            replyText = ConversationContextManager.buildSmallTalkFallback(userMessage, effectiveLanguage, deviceLabel),
            actions = emptyList(),
            repairPlan = null,
            autonomousBundle = null,
            aiStatus = AiServiceStatus.ONLINE_GEMINI,
            aiStatusDetail = "Assistant Ready • Conversational Mode ($deviceLabel)",
            responseLanguage = effectiveLanguage,
            intentHandled = ConversationIntent.SMALL_TALK
        )
    }

    private suspend fun callFirebaseConversational(prompt: String): Pair<String?, String> {
        val config = generationConfig {
            temperature = 0.6f
        }
        for (modelName in SUPPORTED_GEMINI_MODELS) {
            coroutineContext.ensureActive()
            try {
                val model = Firebase.ai.generativeModel(
                    modelName = modelName,
                    generationConfig = config
                )
                val response = withTimeout(10_000L) { model.generateContent(prompt) }
                val txt = response.text?.trim()
                if (!txt.isNullOrBlank()) return txt to modelName
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                val msg = t.localizedMessage ?: t::class.java.simpleName
                if (isFatalAuthOrAppCheckError(msg)) return null to modelName
            }
        }
        return null to SUPPORTED_GEMINI_MODELS.first()
    }

    private fun buildPrompt(
        userProblem: String,
        vitalsSummary: String,
        ragContext: String,
        validActionCatalog: String,
        oemContext: String,
        conversationHistory: List<Pair<String, String>>,
        effectiveLanguage: String,
        hasScreenshot: Boolean,
        intent: ConversationIntent = ConversationIntent.PROBLEM_REPORT,
        sessionContext: TroubleshootingSessionContext = TroubleshootingSessionContext()
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

        val sessionMemoryBlock = buildString {
            if (sessionContext.hasActiveProblem || sessionContext.excludedActionIds.isNotEmpty()) {
                appendLine("ACTIVE TROUBLESHOOTING CONTEXT:")
                sessionContext.activeProblemTitle?.let { appendLine("- Active Problem: $it (${sessionContext.activeProblemId ?: "custom"})") }
                sessionContext.lastSymptomQuery?.let { appendLine("- Original User Symptom: $it") }
                if (sessionContext.failedActionIds.isNotEmpty()) {
                    appendLine("- FAILED / UNRESOLVED ACTIONS (DO NOT REPEAT): ${sessionContext.failedActionIds.joinToString(", ")}")
                }
                if (sessionContext.attemptedActionIds.isNotEmpty()) {
                    appendLine("- ALREADY ATTEMPTED ACTIONS (PICK NEXT ALTERNATIVE): ${sessionContext.attemptedActionIds.joinToString(", ")}")
                }
                when (intent) {
                    ConversationIntent.FOLLOW_UP_NOT_FIXED -> {
                        appendLine("- TURN INTENT: FOLLOW-UP (User tried earlier advice and it STILL DID NOT FIX the issue). Acknowledge kindly and empathetically (e.g. reassure them we will try an easier next method step by step), NEVER repeat failed/attempted steps, and pick the next simplest effective alternative or honest Android restriction escalation.")
                    }
                    ConversationIntent.CLARIFY_EXPLANATION -> {
                        appendLine("- TURN INTENT: CLARIFY EXPLANATION (User did not understand the previous reply). Explain calmly and warmly in simple everyday words, 1 small step at a time, without technical jargon.")
                    }
                    else -> {}
                }
                appendLine()
            }
        }

        val historyPrompt = if (conversationHistory.isNotEmpty()) {
            "RECENT CONVERSATION:\n" + conversationHistory.takeLast(6).joinToString("\n") { (role, msg) ->
                "${if (role == "user") "User" else "Doctor"}: $msg"
            } + "\n\n"
        } else ""

        return """
You are AI Smartphone Assistant, a warm, empathetic, evidence-based Android hardware & software troubleshooting expert.
CORE PHILOSOPHY: Speak naturally like a caring human technician. Diagnose first with verified telemetry. Separate automatic in-app checks from actions needing user permission/settings. Verify outcome last, and remember tried/failed steps.

STRICT RULES:
1. TELEMETRY HONESTY: NEVER invent sensor numbers, RAM values, battery levels, or overheating claims. Use ONLY readings marked [VERIFIED] in telemetry below. If a reading says [NOT COLLECTED] or Unavailable, explicitly list it under `unverifiedAssumptions`, NOT `confirmedEvidence`, and never pretend the fault is confirmed without evidence.
2. EVIDENCE VS CAUSES: Clearly distinguish confirmed telemetry evidence (`confirmedEvidence`) from suspected root causes (`possibleCauses`).
3. CLARIFY WHEN VAGUE: If the user's complaint is broad or marked AMBIGUOUS COMPLAINT NOTICE (and no active problem context exists), set `confidenceLevel` to "Low", `matchedProblemId` to "CLARIFY-REQUIRED", and ask focused `diagnosticQuestions` first.
4. AUTONOMOUS EXECUTION OVER MANUAL LECTURES: Choose `recommendedActionIds` ONLY from APPROVED ACTION CATALOG below (and never repeat failed actions). Whenever an `AUTO IN-APP` executor handles a step, tell the user cleanly and include its ID so the Autonomous Repair Plan card runs it. For OS-sandboxed steps (3rd-party cache, force-stop, Wi-Fi/Data radio toggles), explain the Android restriction honestly and offer the easiest alternative.
5. LANGUAGE CONSISTENCY:
$languageDirective
6. SAFETY & HUMAN TONE: Never instruct silent third-party cache wipes or claim a problem is repaired when only instructions or Settings links were provided. Never cite internal KB codes (like AND-05-001) or severity tags (P2/R1) inside `replyText`.

${sessionMemoryBlock}DEVICE & COMPATIBILITY PROFILE:
$oemContext

$ragContext

APPROVED ACTION CATALOG (ONLY USE THESE IDs):
$validActionCatalog

$historyPrompt
VERIFIED LIVE DEVICE TELEMETRY:
$vitalsSummary

USER MESSAGE:
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
                val diag = AppCheckConfigStatus.summary()
                lastError = "$modelName: $msg ($diag)"
                Log.w(TAG, "Firebase AI call failed for $modelName [$diag]: $msg")
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
        userProblem: String = "",
        sessionContext: TroubleshootingSessionContext = TroubleshootingSessionContext(),
        intent: ConversationIntent = ConversationIntent.PROBLEM_REPORT
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
        val rawValidated = actionIds
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
        val excludedIds = sessionContext.excludedActionIds
        val validatedActions = if (excludedIds.isNotEmpty() && rawValidated.isNotEmpty()) {
            val nonExcluded = rawValidated.filter { it.id !in excludedIds }
            if (nonExcluded.isNotEmpty()) {
                nonExcluded
            } else {
                listOfNotNull(
                    com.example.data.repair.RepairVerificationEngine.selectNextSafeAlternative(
                        failedOrCurrentActionId = rawValidated.first().id,
                        playbook = legacyPlaybook,
                        alreadyTriedActionIds = excludedIds,
                        issueId = issueRecord?.problemId
                    )
                )
            }
        } else {
            rawValidated
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
            responseLanguage = effectiveLanguage,
            matchedProblemId = issueRecord?.problemId ?: matchedId,
            matchedProblemTitle = plan.problemTitle,
            matchedCategory = issueRecord?.categoryName,
            intentHandled = intent
        )
    }

    /**
     * Structured 466-Issue KB + Compatibility RAG Fallback Engine.
     * Respects Hindi / Hinglish / English language consistency, remembers tried/failed actions,
     * and responds naturally on follow-up or clarification turns.
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
        language: String = LanguageDetector.LANG_ENGLISH,
        sessionContext: TroubleshootingSessionContext = TroubleshootingSessionContext(),
        intentOverride: ConversationIntent? = null
    ): DoctorResponse {
        if (context != null) {
            runCatching { KnowledgeRepository.ensureLoadedSynchronously(context) }
        }
        val effectiveLanguage = LanguageDetector.detectResponseLanguage(userProblem, language)
        val intent = intentOverride ?: ConversationContextManager.classifyIntent(userProblem, sessionContext)
        val deviceLabel = "${vitals.brand} ${vitals.model}".trim().ifBlank { "Android Smartphone" }

        if (intent == ConversationIntent.SMALL_TALK) {
            return DoctorResponse(
                replyText = ConversationContextManager.buildSmallTalkFallback(userProblem, effectiveLanguage, deviceLabel),
                actions = emptyList(),
                repairPlan = null,
                autonomousBundle = null,
                aiStatus = AiServiceStatus.ONLINE_GEMINI,
                aiStatusDetail = "Assistant Ready • Conversational Mode ($deviceLabel)",
                responseLanguage = effectiveLanguage,
                intentHandled = ConversationIntent.SMALL_TALK
            )
        }

        val effectiveQuery = when {
            (intent == ConversationIntent.FOLLOW_UP_NOT_FIXED || intent == ConversationIntent.CLARIFY_EXPLANATION) &&
                !sessionContext.lastSymptomQuery.isNullOrBlank() -> sessionContext.lastSymptomQuery
            (intent == ConversationIntent.FOLLOW_UP_NOT_FIXED || intent == ConversationIntent.CLARIFY_EXPLANATION) &&
                !sessionContext.activeProblemId.isNullOrBlank() -> sessionContext.activeProblemId
            else -> userProblem
        }

        val outcome = if (precomputedMatches == null) {
            PlaybookRagRetriever.retrieveIssueOutcome(
                query = effectiveQuery,
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
                responseLanguage = effectiveLanguage,
                matchedProblemId = "CLARIFY-REQUIRED",
                matchedProblemTitle = "Clarification Needed",
                intentHandled = intent
            )
        }

        val topIssueMatch = outcome.matches.firstOrNull()
        if (topIssueMatch != null) {
            val issue = topIssueMatch.issue
            val baseActions = topIssueMatch.allowedActions.ifEmpty {
                issue.automaticActionIds.mapNotNull { SafeRepairRegistry.findAction(it) }.distinctBy { it.id }
            }
            val excludedIds = sessionContext.excludedActionIds
            val filteredActions = if (excludedIds.isNotEmpty()) {
                val remaining = baseActions.filter { it.id !in excludedIds }
                if (remaining.isNotEmpty()) {
                    remaining
                } else {
                    listOfNotNull(
                        com.example.data.repair.RepairVerificationEngine.selectNextSafeAlternative(
                            failedOrCurrentActionId = baseActions.firstOrNull()?.id ?: "",
                            playbook = null,
                            alreadyTriedActionIds = excludedIds,
                            issueId = issue.problemId
                        )
                    )
                }
            } else {
                baseActions
            }

            val telemetryLine = buildConciseVerifiedLine(vitals, effectiveLanguage)
            val allSolutionsHi = LocalizedDiagnosticFormatter.buildHindiFallbackSteps(issue, topIssueMatch.resolvedSettingsPath)
            val allSolutionsEn = issue.solutions.ifEmpty {
                listOf("Open ${topIssueMatch.resolvedSettingsPath} and verify settings safely.")
            }

            // Pick rotated/next untried step chunk on follow-up turns so user doesn't see identical instructions repeated
            val stepOffset = if (intent == ConversationIntent.FOLLOW_UP_NOT_FIXED) {
                (sessionContext.followUpStepOffset + 1).coerceAtLeast(1)
            } else {
                0
            }

            fun selectProgressedSteps(steps: List<String>): Pair<List<String>, Boolean> {
                if (steps.size <= 1 || stepOffset == 0) return steps to false
                val startIdx = (stepOffset * 2) % steps.size
                val remaining = steps.drop(stepOffset * 2)
                return if (remaining.isNotEmpty()) {
                    remaining to false
                } else {
                    // All software steps exhausted — include honest escalation/Safe Mode guidance
                    listOf(steps[startIdx]) to true
                }
            }

            val reply = when {
                intent == ConversationIntent.CLARIFY_EXPLANATION -> {
                    when (effectiveLanguage) {
                        LanguageDetector.LANG_HINDI -> {
                            val hiTitle = LocalizedDiagnosticFormatter.buildHindiIssueSummaryTitle(issue)
                            val simpleSteps = allSolutionsHi.take(2)
                            """
बिल्कुल, कोई चिंता नहीं! मैं **"$hiTitle"** को बिल्कुल सरल शब्दों में समझाता हूँ:

1. आसान शब्द में बात यह है: पहले सुरक्षित जांच से देखते हैं कि समस्या सेटिंग्स से है या बैकग्राउंड ऐप से।
2. पहला आसान काम: ${simpleSteps.firstOrNull() ?: topIssueMatch.resolvedSettingsPath}
${if (simpleSteps.size > 1) "3. दूसरा कदम: ${simpleSteps[1]}" else ""}

*(स्थानीय सहायक मोड — ऊपर क्लाउड एआई के लिए पुनः प्रयास भी कर सकते हैं।)* अगर चाहें तो बताएं कि इनमें से किस चरण पर मदद चाहिए।
                            """.trimIndent()
                        }
                        LanguageDetector.LANG_HINGLISH -> {
                            val simpleSteps = allSolutionsEn.take(2)
                            """
Koi baat nahi! Main **"${issue.canonicalProblemName}"** ko bilkul simple language mein step-by-step samjhata hoon:

1. Simple matlab yeh hai ki pehle hum bina kisi risk ke check karenge ki setting ya background app ki wajah se toh issue nahi aa raha.
2. Pehla aasan step: ${simpleSteps.firstOrNull() ?: topIssueMatch.resolvedSettingsPath}
${if (simpleSteps.size > 1) "3. Doosra step: ${simpleSteps[1]}" else ""}

*(Cloud AI abhi offline mode mein hai — aap upar Retry kar sakte hain.)* Batayein, kya ab yeh step clear hua?
                            """.trimIndent()
                        }
                        else -> {
                            val simpleSteps = allSolutionsEn.take(2)
                            """
Of course! Let me explain **"${issue.canonicalProblemName}"** in plain, simple steps:

1. Simply put: we check the safest setting or background cause first without changing any personal data.
2. Step 1 (Easiest check): ${simpleSteps.firstOrNull() ?: topIssueMatch.resolvedSettingsPath}
${if (simpleSteps.size > 1) "3. Step 2: ${simpleSteps[1]}" else ""}

*(Cloud AI offline fallback — tap Retry anytime.)* Tell me which part felt unclear and I will walk you through it slowly.
                            """.trimIndent()
                        }
                    }
                }
                intent == ConversationIntent.FOLLOW_UP_NOT_FIXED -> {
                    when (effectiveLanguage) {
                        LanguageDetector.LANG_HINDI -> {
                            val hiTitle = LocalizedDiagnosticFormatter.buildHindiIssueSummaryTitle(issue)
                            val (nextSteps, exhausted) = selectProgressedSteps(allSolutionsHi)
                            val nextAction = filteredActions.firstOrNull()?.let {
                                LocalizedDiagnosticFormatter.localizeAction(it, effectiveLanguage).title
                            }
                            buildString {
                                appendLine("कोई बात नहीं! इस बार हम **\"$hiTitle\"** के लिए एक नया और आसान तरीका आज़माते हैं। मैं आपको एक-एक कदम समझाऊंगा।")
                                appendLine()
                                appendLine(telemetryLine)
                                appendLine()
                                appendLine("**अगला आसान समाधान (पिछले चरण दोहराए बिना):**")
                                nextSteps.forEachIndexed { idx, step ->
                                    appendLine("${idx + 1}. $step")
                                }
                                if (nextAction != null) {
                                    appendLine()
                                    appendLine("• आप नीचे दिया नया विकल्प **$nextAction** भी सीधे चला सकते हैं।")
                                }
                                if (exhausted) {
                                    appendLine()
                                    appendLine("• *नोट:* पिछले सॉफ्टवेयर कदमों से समाधान नहीं मिला, इसलिए अब फोन को सेफ मोड में जाँचें या अधिकृत सर्विस सेंटर में हार्डवेयर दिखवाना सबसे सुरक्षित रहेगा। *(स्थानीय ज्ञानकोश मोड)*")
                                } else {
                                    appendLine()
                                    append("*(स्थानीय ज्ञानकोश मोड — क्लाउड एआई उपलब्ध नहीं)*")
                                }
                            }.trim()
                        }
                        LanguageDetector.LANG_HINGLISH -> {
                            val (nextSteps, exhausted) = selectProgressedSteps(allSolutionsEn)
                            val nextAction = filteredActions.firstOrNull()?.let {
                                LocalizedDiagnosticFormatter.localizeAction(it, effectiveLanguage).title
                            }
                            buildString {
                                appendLine("Koi baat nahi! Is baar hum **\"${issue.canonicalProblemName}\"** ke liye ek aur aasan tareeka try karte hain. Main aapko ek-ek step samjhaunga.")
                                appendLine()
                                appendLine(telemetryLine)
                                appendLine()
                                appendLine("**Agla Easy Solution (Bina failed steps repeat kiye):**")
                                nextSteps.forEachIndexed { idx, step ->
                                    appendLine("${idx + 1}. $step")
                                }
                                if (nextAction != null) {
                                    appendLine()
                                    appendLine("• Neeche diya naya option **$nextAction** try karein.")
                                }
                                if (exhausted) {
                                    appendLine()
                                    appendLine("• *Honest Update:* Pehle ke software steps se fix nahi hua, toh Safe Mode test ya service center inspection best rahega. *(Cloud AI abhi unavailable hai)*")
                                } else {
                                    appendLine()
                                    append("*(Cloud AI abhi unavailable hai — Next Verified KB Step)*")
                                }
                            }.trim()
                        }
                        else -> {
                            val (nextSteps, exhausted) = selectProgressedSteps(allSolutionsEn)
                            val nextAction = filteredActions.firstOrNull()?.title
                            buildString {
                                appendLine("No problem at all — since the earlier step didn't fix **\"${issue.canonicalProblemName}\"**, let's try the next easiest method step by step.")
                                appendLine()
                                appendLine(telemetryLine)
                                appendLine()
                                appendLine("**Practical Safe Solutions (Next Step, No Repeated Failed Steps):**")
                                nextSteps.forEachIndexed { idx, step ->
                                    appendLine("${idx + 1}. $step")
                                }
                                if (nextAction != null) {
                                    appendLine()
                                    appendLine("• Try the untried action card below: **$nextAction**.")
                                }
                                if (exhausted) {
                                    appendLine()
                                    appendLine("• *Honest Note:* Earlier software steps didn't resolve it, so testing in Safe Mode or an authorized service check is recommended. *(Cloud AI unavailable)*")
                                } else {
                                    appendLine()
                                    append("*(Offline KB Mode — Cloud AI unavailable)*")
                                }
                            }.trim()
                        }
                    }
                }
                else -> when (effectiveLanguage) {
                    LanguageDetector.LANG_HINDI -> {
                        val hiTitle = LocalizedDiagnosticFormatter.buildHindiIssueSummaryTitle(issue)
                        val hiSteps = allSolutionsHi
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
                        val steps = allSolutionsEn
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
${allSolutionsEn.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")}

${if (firstQuestion != null) "**Confirming Question:** $firstQuestion\n\n" else ""}Below, automatic in-app diagnostics are clearly separated from actions that require your permission or opening System Settings.
                        """.trimIndent()
                    }
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
                userProblem = effectiveQuery,
                vitals = vitals,
                recommendedActions = filteredActions,
                repairPlan = plan,
                oemProfile = oemProfile,
                language = effectiveLanguage
            )

            return DoctorResponse(
                replyText = reply,
                actions = filteredActions,
                repairPlan = plan,
                autonomousBundle = bundle,
                aiStatus = status,
                aiStatusDetail = statusDetail,
                responseLanguage = effectiveLanguage,
                matchedProblemId = issue.problemId,
                matchedProblemTitle = issue.canonicalProblemName,
                matchedCategory = issue.categoryName,
                intentHandled = intent
            )
        }

        val matches = PlaybookRagRetriever.retrieveRelevantPlaybooks(effectiveQuery, vitals, limit = 1)
        val topPlaybook = matches.first().playbook
        val rawPbActions = topPlaybook.automaticActionIds.mapNotNull { SafeRepairRegistry.findAction(it) }.distinctBy { it.id }
        val excludedIds = sessionContext.excludedActionIds
        val actions = if (excludedIds.isNotEmpty()) {
            rawPbActions.filter { it.id !in excludedIds }.ifEmpty {
                listOfNotNull(
                    com.example.data.repair.RepairVerificationEngine.selectNextSafeAlternative(
                        failedOrCurrentActionId = rawPbActions.firstOrNull()?.id ?: "",
                        playbook = topPlaybook,
                        alreadyTriedActionIds = excludedIds
                    )
                )
            }
        } else {
            rawPbActions
        }
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
            userProblem = effectiveQuery,
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
            responseLanguage = effectiveLanguage,
            matchedProblemId = topPlaybook.id,
            matchedProblemTitle = topPlaybook.problemTitle,
            intentHandled = intent
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
