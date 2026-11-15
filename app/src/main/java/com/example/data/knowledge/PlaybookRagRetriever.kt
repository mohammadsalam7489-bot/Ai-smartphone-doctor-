package com.example.data.knowledge

import android.content.Context
import com.example.data.compatibility.DeviceCompatibilityEngine
import com.example.data.model.DeviceVitals
import com.example.data.model.OemCompatibilityProfile
import com.example.data.repair.CapabilityEvaluator
import com.example.data.repair.SafeRepairRegistry

data class RagRetrievalOutcome(
    val matches: List<IssueRagMatch>,
    val isAmbiguous: Boolean,
    val clarifyingQuestions: List<String> = emptyList(),
    val ambiguityReason: String? = null
)

/**
 * Connected Multi-Database RAG Engine:
 * Connects:
 *   1) Complete 466-issue / 29-category Knowledge Database (PDF 1),
 *   2) Global Device, OEM Skin & Android OS Compatibility Database (PDF 2),
 *   3) Live Hardware/OS Telemetry (DeviceVitals),
 *   4) Zero-Trust SafeRepairRegistry & CapabilityEvaluator.
 */
object PlaybookRagRetriever {

    val allPlaybooks: List<DiagnosticPlaybook> =
        DiagnosticPlaybooksPart1.playbooks + DiagnosticPlaybooksPart2.playbooks

    fun findById(id: String?): DiagnosticPlaybook? {
        if (id.isNullOrBlank()) return null
        return allPlaybooks.find { it.id == id }
    }

    /**
     * Full 466-Issue Multilingual RAG Retrieval with Ambiguity Triage.
     * Prevents guessing a wrong diagnosis when user complaints are underspecified
     * (e.g. "phone kharab hai", "kuch kaam nahi kar raha", "help").
     */
    fun retrieveIssueOutcome(
        query: String,
        vitals: DeviceVitals,
        context: Context? = null,
        limit: Int = 4
    ): RagRetrievalOutcome {
        if (context != null) {
            KnowledgeRepository.ensureLoadedSynchronously(context)
        }
        val issues = KnowledgeRepository.getAllIssues()
        if (issues.isEmpty()) {
            return RagRetrievalOutcome(
                matches = emptyList(),
                isAmbiguous = true,
                clarifyingQuestions = defaultClarifyingQuestions(query),
                ambiguityReason = "Knowledge database not loaded yet."
            )
        }

        val oemProfile = DeviceCompatibilityEngine.resolveProfile(
            manufacturer = vitals.manufacturer,
            brand = vitals.brand,
            model = vitals.model,
            apiLevel = vitals.apiLevel,
            androidVersion = vitals.androidVersion
        )

        val normalizedQuery = normalizeMultilingualQuery(query)
        val rawTokens = normalizedQuery
            .split(Regex("[\\s,?.!;:/\"'()]+"))
            .filter { it.length >= 2 }
            .distinct()
        val meaningfulTokens = rawTokens.filter { it !in STOP_WORDS }

        val explicitIdMatch = Regex("""AND-\d{2}-\d{3}""", RegexOption.IGNORE_CASE).find(query)?.value?.uppercase()
        val intentBoostCategories = inferIntentCategories(normalizedQuery)
        val hasDirectDomainSignal = explicitIdMatch != null || intentBoostCategories.isNotEmpty() || meaningfulTokens.size >= 2

        val scored = issues.map { issue ->
            var score = 0
            val reasons = mutableListOf<String>()

            if (explicitIdMatch != null && issue.problemId == explicitIdMatch) {
                score += 120
                reasons.add("Exact problem ID match: $explicitIdMatch")
            }

            val titleLower = issue.canonicalProblemName.lowercase()
            if (normalizedQuery.contains(titleLower) && titleLower.length >= 4) {
                score += 35
                reasons.add("Canonical title matched")
            }
            for (phrase in issue.userPhrases) {
                val pLower = phrase.lowercase()
                if (pLower.length >= 4 && normalizedQuery.contains(pLower)) {
                    score += if (pLower.contains(" ")) 28 else 10
                    reasons.add("Matched phrase '$phrase'")
                }
            }

            val catLower = issue.categoryName.lowercase()
            val phrasesJoined = issue.userPhrases.joinToString(" ").lowercase()
            val causesJoined = issue.possibleCauses.joinToString(" ").lowercase()

            var titleTokenHits = 0
            for (tok in meaningfulTokens) {
                if (titleLower.contains(tok)) {
                    score += 7
                    titleTokenHits++
                }
                if (phrasesJoined.contains(tok)) score += 4
                if (catLower.contains(tok)) score += 2
                if (causesJoined.contains(tok)) score += 2
            }
            if (titleTokenHits >= 2) {
                score += titleTokenHits * 4
                reasons.add("$titleTokenHits title keyword hits")
            }

            val intentCatWeight = intentBoostCategories[issue.categoryNumber] ?: 0
            if (intentCatWeight > 0) {
                score += intentCatWeight
                reasons.add("Matched category intent (Cat ${issue.categoryNumber})")
            }

            score += computeSignatureIssueBoost(normalizedQuery, issue)

            val teleBoost = computeTelemetryBoost(vitals, issue)
            if (teleBoost > 0 && score > 0) {
                score += teleBoost
                reasons.add("Live telemetry signal (+$teleBoost)")
            }

            val pathRes = DeviceCompatibilityEngine.resolveSettingsPath(issue.oemSettingsKey, oemProfile)
            val actions = issue.automaticActionIds
                .mapNotNull { SafeRepairRegistry.findAction(it) }
                .distinctBy { it.id }

            val blockedNotes = if (context != null) {
                actions.mapNotNull { act ->
                    val eval = CapabilityEvaluator.evaluate(context, act)
                    if (!eval.isSupportedOnDevice) "${act.title}: ${eval.restrictionSummary}" else null
                }
            } else emptyList()

            val supportedActions = if (context != null) {
                actions.filter { CapabilityEvaluator.evaluate(context, it).isSupportedOnDevice }
            } else actions

            IssueRagMatch(
                issue = issue,
                category = KnowledgeRepository.findCategoryByNumber(issue.categoryNumber),
                score = score,
                matchReasons = reasons.distinct(),
                resolvedSettingsPath = pathRes.resolvedPath,
                isSettingsPathVerified = pathRes.isVerifiedPath,
                coverageTier = pathRes.coverageTier,
                allowedActions = supportedActions,
                blockedActionNotes = blockedNotes
            )
        }

        val positive = scored.filter { it.score > 0 }.sortedByDescending { it.score }
        val topScore = positive.firstOrNull()?.score ?: 0
        val isAmbiguous = positive.isEmpty() || (!hasDirectDomainSignal && topScore < 14)

        return if (isAmbiguous) {
            RagRetrievalOutcome(
                matches = emptyList(),
                isAmbiguous = true,
                clarifyingQuestions = defaultClarifyingQuestions(query),
                ambiguityReason = "Complaint is underspecified; clarifying symptoms before diagnosing."
            )
        } else {
            RagRetrievalOutcome(
                matches = positive.take(limit),
                isAmbiguous = false
            )
        }
    }

    /**
     * Full 466-Issue Multilingual RAG Retrieval (English, Hindi, Hinglish, Punjabi).
     */
    fun retrieveMatchingIssues(
        query: String,
        vitals: DeviceVitals,
        context: Context? = null,
        limit: Int = 4
    ): List<IssueRagMatch> {
        return retrieveIssueOutcome(query, vitals, context, limit).matches
    }

    private fun defaultClarifyingQuestions(query: String): List<String> {
        val isHindi = query.any { it in '\u0900'..'\u097F' || it in '\u0A00'..'\u0A7F' } ||
            listOf("mera", "phone", "kharab", "nahi", "kuch", "samasya", "problem", "bhai", "kaam")
                .any { query.lowercase().contains(it) }
        return if (isHindi) {
            listOf(
                "Aapko kis area mein dikkat aa rahi hai — performance/hang, battery/charging, heating, internet/Wi-Fi, Bluetooth, display/touch, ya koi specific app?",
                "Kya koi exact error message dikhta hai ya yeh problem kisi update/new app install ke baad shuru hui?",
                "Kya yeh dikkat hamesha rehti hai ya sirf gaming/charging/calling ke waqt aati hai?"
            )
        } else {
            listOf(
                "Which area is affected — performance/lag, battery/charging, overheating, Wi-Fi/mobile data, Bluetooth, display/touch, audio, or a specific app?",
                "Did this issue begin after an OS/app update, new app install, or physical drop/moisture?",
                "Does it happen constantly or only during a specific action (charging, calling, gaming)?"
            )
        }
    }

    fun formatIssueRagContextForPrompt(
        matches: List<IssueRagMatch>,
        oemProfile: OemCompatibilityProfile,
        vitals: DeviceVitals
    ): String {
        if (matches.isEmpty()) return ""
        return buildString {
            append("=== CONNECTED KNOWLEDGE & COMPATIBILITY RAG CONTEXT (KB v2.0 + Manual v3.0) ===\n")
            append("Device Profile: ${oemProfile.oemName} (${oemProfile.osFlavor}) | ${oemProfile.androidCompatibility.versionLabel}\n")
            append("Coverage Tier: ${oemProfile.coverageTier.code} (${oemProfile.coverageTier.label}) | Settings Verified: ${oemProfile.isPathVerifiedForBuild} | GMS Available: ${oemProfile.gmsAvailable}\n")
            append("OS Restrictions: ${oemProfile.androidCompatibility.apiRestrictions.joinToString("; ")}\n\n")

            matches.forEachIndexed { idx, m ->
                val iss = m.issue
                append("[${idx + 1}] PROBLEM ID: ${iss.problemId} | ${iss.canonicalProblemName} (${iss.categoryName})\n")
                append("• Source Citation: ${iss.sourcePdf} (Page ${iss.sourcePage}) | Status: ${iss.verificationStatus} | Severity: ${iss.severity} | Risk: ${iss.riskTier}\n")
                if (iss.relatedOverlaps.isNotEmpty()) {
                    append("• Overlapping Issues to Check: ${iss.relatedOverlaps.joinToString(", ")}\n")
                }
                append("• Clarifying Questions: ${iss.diagnosticQuestions.joinToString(" | ")}\n")
                append("• Possible Causes: ${iss.possibleCauses.joinToString("; ")}\n")
                append("• Resolved OEM Settings Path: ${m.resolvedSettingsPath}\n")
                append("• Verified Solutions:\n")
                iss.solutions.forEachIndexed { sIdx, step ->
                    append("   ${sIdx + 1}. $step\n")
                }
                if (iss.issueSpecificNotes.isNotEmpty()) {
                    append("• Issue-Specific Rules: ${iss.issueSpecificNotes.joinToString(" | ")}\n")
                }
                append("• Allowed Action IDs (USE ONLY THESE): ${m.allowedActions.joinToString(", ") { it.id }}\n")
                append("• Automation Boundaries: ${iss.androidRestrictions.joinToString("; ")}\n")
                append("• Verification Method: ${iss.verification.joinToString("; ")}\n")
                append("• Hardware Fault Indicators: ${iss.hardwareIndicators.joinToString("; ")}\n")
                append("• Escalation: ${iss.escalation.joinToString("; ")}\n\n")
            }
            append("Telemetry Note: Health=${vitals.healthScore}/100, Temp=${vitals.batteryTempC}C (${vitals.thermalStatus}), RAM=${vitals.ramUsedPercent}%, Storage=${vitals.storageUsedPercent}%, Online=${vitals.isInternetAvailable}\n")
        }
    }

    // Legacy 13-playbook retrieval retained for backward compatibility with existing callers/tests
    fun retrieveRelevantPlaybooks(
        query: String,
        vitals: DeviceVitals,
        limit: Int = 3
    ): List<RagMatchResult> {
        val q = normalizeMultilingualQuery(query)
        val tokens = q.split(Regex("[\\s,?.!;:/]+")).filter { it.length >= 2 }

        val scored = allPlaybooks.map { pb ->
            var score = 0
            val reasons = mutableListOf<String>()

            for (kw in pb.symptomsAndKeywords) {
                if (q.contains(kw.lowercase())) {
                    score += if (kw.contains(" ")) 8 else 5
                    reasons.add("Matched symptom '$kw'")
                }
            }
            val titleLower = pb.problemTitle.lowercase()
            val catLower = pb.category.lowercase()
            for (t in tokens) {
                if (titleLower.contains(t) || catLower.contains(t)) {
                    score += 3
                }
            }
            val vitalsBoost = pb.vitalsBoostTrigger(vitals)
            if (vitalsBoost > 0) {
                score += vitalsBoost
                reasons.add("Correlated with live device telemetry (+$vitalsBoost)")
            }
            RagMatchResult(playbook = pb, score = score, matchReasons = reasons.distinct())
        }

        val positiveMatches = scored.filter { it.score > 0 }.sortedByDescending { it.score }
        return if (positiveMatches.isNotEmpty()) {
            positiveMatches.take(limit)
        } else {
            scored.sortedByDescending { it.playbook.vitalsBoostTrigger(vitals) }.take(limit)
        }
    }

    fun formatRagContextForPrompt(
        matches: List<RagMatchResult>,
        oemProfile: OemCompatibilityProfile
    ): String {
        if (matches.isEmpty()) return ""
        return buildString {
            append("=== VERIFIED PDF KNOWLEDGE BASE (RAG CONTEXT) ===\n")
            matches.forEachIndexed { idx, match ->
                val pb = match.playbook
                append("\n[${idx + 1}] Playbook ID: ${pb.id} | ${pb.problemTitle} (${pb.category})\n")
                append("• Possible Causes: ${pb.possibleCauses.joinToString("; ")}\n")
                append("• OEM Settings Path (${oemProfile.oemName}): ${pb.oemSettingsPathResolver(oemProfile)}\n")
                append("• Approved Action IDs (USE ONLY THESE): ${pb.automaticActionIds.joinToString(", ")}\n")
                append("• Android API Restrictions: ${pb.androidApiRestrictions}\n")
                append("• Verification Method: ${pb.verificationMethod}\n")
                append("• Hardware Fault Indicators: ${pb.hardwareFaultIndicators.joinToString("; ")}\n")
                append("• Escalation: ${pb.escalationInstructions}\n")
            }
        }
    }

    /**
     * Normalizes Hindi (Devanagari + Romanized), Hinglish, Punjabi, and common typos
     * into stable searchable concepts aligned with PDF 1's 466 canonical entries.
     */
    internal fun normalizeMultilingualQuery(query: String): String {
        var q = query.lowercase().trim()
        val replacements = listOf(
            // Punjabi, Hinglish, and Hindi Devanagari/Gurmukhi normalization
            "apne aap band ho janda" to "automatically band restart shutdown",
            "band ho janda" to "automatically band shutdown restart",
            "band ho jaati" to "automatically close band shutdown",
            "band ho jati" to "automatically close band shutdown",
            "band ho jaata" to "automatically close band shutdown",
            "band ho jata" to "automatically close band shutdown",
            "apne aap band" to "automatically close band shutdown",
            "apne aap restart" to "automatically restart",
            "khud band" to "automatically close band",
            "charge bahut slow" to "charging bahut slow",
            "charge slow" to "charging slow",
            "bina wajah garam" to "excessive processing heating garam",
            "garam hota" to "heating garam overheating",
            "garam ho raha" to "heating garam overheating",
            "bahut garam" to "heating overheating garam",
            "touch nahi le raha" to "touch kaam na karna touchscreen",
            "touch kaam nahi kar" to "touch kaam na karna touchscreen",
            "connect nahi ho raha" to "connect na hona pairing",
            "chal nahi raha" to "na chalna kaam na karna",
            "nahi chal raha" to "na chalna kaam na karna",
            "nahi chal rahi" to "na chalna kaam na karna",
            "nahi aa rahi" to "na aana",
            "nahi aa raha" to "na aana",
            "nahi ho raha" to "na hona",
            "jaldi khatam ho rahi" to "jaldi drain khatam",
            "bahut hang ho raha" to "slow chalna hang freeze",
            "hang hota" to "slow chalna hang freeze",
            "bohot hang" to "hang slow freeze",
            "wi-fi" to "wifi",
            "wi fi" to "wifi",
            "khatam" to "drain khatam",
            "aawaz" to "sound speaker aawaz",
            "awaaz" to "sound speaker aawaz",
            // Devanagari Hindi
            "फंस रहा" to "hang slow",
            "धीमा" to "slow",
            "बैटरी" to "battery",
            "चार्ज" to "charging",
            "गर्म" to "heating garam",
            "इंटरनेट" to "internet",
            "नोटिफिकेशन" to "notifications",
            "ब्लूटूथ" to "bluetooth",
            "आवाज़" to "sound speaker",
            "आवाज" to "sound speaker",
            "टच" to "touch",
            "गेम" to "game",
            "बंद" to "band close shutdown",
            // Gurmukhi Punjabi
            "ਬੈਟਰੀ" to "battery",
            "ਗਰਮ" to "heating garam",
            "ਬੰਦ" to "band shutdown",
            "ਹੌਲੀ" to "slow",
            "ਵਾਈਫਾਈ" to "wifi",
            "ਇੰਟਰਨੈੱਟ" to "internet",
            "ਬਲੂਟੁੱਥ" to "bluetooth",
            "ਆਵਾਜ਼" to "sound speaker",
            "ਟੱਚ" to "touch"
        )
        for ((src, dst) in replacements) {
            if (q.contains(src)) {
                q = "$q $dst"
            }
        }
        return q
    }

    private fun inferIntentCategories(q: String): Map<Int, Int> {
        val boost = mutableMapOf<Int, Int>()
        fun add(cat: Int, pts: Int) {
            boost[cat] = (boost[cat] ?: 0) + pts
        }
        if (q.contains("game") || q.contains("bgmi") || q.contains("pubg") || q.contains("fps") || q.contains("ping") || q.contains("free fire")) add(11, 26)
        if (q.contains("boot") || q.contains("logo") || q.contains("restart") || q.contains("shutdown") || q.contains("band ho janda") || q.contains("power on")) add(1, 20)
        val isSlowPerformance = (q.contains("slow") && !q.contains("charg") && !q.contains("internet") && !q.contains("wifi")) ||
            q.contains("hang") || q.contains("lag") || q.contains("freeze")
        if (isSlowPerformance) add(2, 22)
        if (q.contains("double tap") || q.contains("gesture") || q.contains("pocket mode") || q.contains("lift to wake") || q.contains("shake")) add(3, 20)
        if (q.contains("app crash") || q.contains("force close") || q.contains("whatsapp") || q.contains("instagram") || q.contains("keeps stopping")) add(4, 18)
        if (q.contains("battery") || q.contains("charg")) add(5, 22)
        if (q.contains("storage") || q.contains("space") || q.contains("memory full") || q.contains("bhara")) add(6, 20)
        if (q.contains("wifi") || q.contains("wi-fi") || q.contains("internet") || q.contains("network") || q.contains("dns") || q.contains("hotspot")) add(7, 22)
        if (q.contains("touch") || q.contains("display") || q.contains("screen") || q.contains("flicker") || q.contains("ghost") || q.contains("brightness")) add(8, 22)
        if (q.contains("sound") || q.contains("speaker") || q.contains("mic") || q.contains("volume") || q.contains("aawaz") || q.contains("earpiece") || q.contains("ringtone")) add(9, 22)
        if (q.contains("notification") || q.contains("alert") || q.contains("badge")) add(10, 24)
        if (q.contains("bluetooth") || q.contains("earbud") || q.contains("tws") || q.contains("headphone")) add(12, 26)
        if (q.contains("permission") || q.contains("restricted setting")) add(13, 22)
        if (q.contains("update") || q.contains("ota") || q.contains("firmware")) add(14, 22)
        if (q.contains("sync") || q.contains("google account") || q.contains("sign-in") || q.contains("sign in")) add(15, 22)
        if (q.contains("keyboard") || q.contains("typing") || q.contains("gboard") || q.contains("autocorrect")) add(16, 24)
        if (q.contains("talkback") || q.contains("accessibility") || q.contains("screen reader")) add(17, 24)
        if (q.contains("camera") || q.contains("gallery") || q.contains("photo") || q.contains("blur")) add(18, 22)
        if (q.contains("gps") || q.contains("location") || q.contains("maps") || q.contains("compass")) add(19, 22)
        if (q.contains("call") || q.contains("dialer") || q.contains("sms") || q.contains("volte")) add(20, 20)
        if (q.contains("play store") || q.contains("install") || q.contains("apk") || q.contains("download pending")) add(21, 22)
        if (q.contains("home screen") || q.contains("launcher") || q.contains("wallpaper") || q.contains("widget")) add(22, 22)
        if (q.contains("backup") || q.contains("restore")) add(23, 22)
        if (q.contains("system ui") || q.contains("systemui") || q.contains("status bar") || q.contains("quick settings")) add(24, 24)
        if (q.contains("developer option") || q.contains("usb debugging") || q.contains("oem unlock") || q.contains("adb")) add(25, 24)
        if (q.contains("sim") || q.contains("5g") || q.contains("apn") || q.contains("no service") || q.contains("esim")) add(26, 22)
        if (q.contains("heat") || q.contains("garam") || q.contains("thermal") || q.contains("overheat") || q.contains("excessive processing")) add(27, 24)
        if (q.contains("usb") || q.contains("otg") || q.contains("mtp") || q.contains("pc") || q.contains("file transfer")) add(28, 22)
        if (q.contains("work profile") || q.contains("managed") || q.contains("knox") || q.contains("it admin")) add(29, 24)
        return boost
    }

    private fun computeSignatureIssueBoost(q: String, issue: KnowledgeIssueRecord): Int {
        return when (issue.problemId) {
            "AND-02-001" -> if ((q.contains("slow") && !q.contains("charg") && !q.contains("wifi") && !q.contains("internet")) || q.contains("hang")) 18 else 0
            "AND-02-003" -> if (q.contains("freeze") || (q.contains("hang") && q.contains("bar bar"))) 16 else 0
            "AND-05-006" -> if (q.contains("charg") && (q.contains("slow") || q.contains("fast") || q.contains("nahi") || q.contains("late"))) 32 else 0
            "AND-05-001" -> if (q.contains("battery") && (q.contains("drain") || q.contains("jaldi") || q.contains("khatam"))) 28 else 0
            "AND-11-001" -> if (q.contains("game") && (q.contains("band") || q.contains("close") || q.contains("crash"))) 34 else 0
            "AND-07-001" -> if (q.contains("wifi") && (q.contains("internet") || q.contains("na chalna") || q.contains("no internet"))) 28 else 0
            "AND-08-015" -> if (q.contains("touch") && (q.contains("kaam") || q.contains("nahi") || q.contains("respons"))) 28 else 0
            "AND-10-001" -> if (q.contains("notification") && (q.contains("nahi") || q.contains("na aana") || q.contains("missing"))) 28 else 0
            "AND-12-004" -> if (q.contains("bluetooth") && (q.contains("connect") || q.contains("nahi") || q.contains("na hona")) && !q.contains("pair")) 28 else 0
            "AND-12-003" -> if (q.contains("bluetooth") && q.contains("pair")) 28 else 0
            "AND-01-005" -> if (!q.contains("game") && q.contains("band") && (q.contains("janda") || q.contains("automatically") || q.contains("shutdown") || q.contains("apne aap"))) 32 else 0
            "AND-27-001" -> if (q.contains("garam") || q.contains("overheat") || q.contains("heating")) 26 else 0
            else -> 0
        }
    }

    private fun computeTelemetryBoost(vitals: DeviceVitals, issue: KnowledgeIssueRecord): Int {
        return when (issue.categoryNumber) {
            2 -> if (vitals.ramUsedPercent >= 80 || vitals.storageUsedPercent >= 85 || vitals.isLowMemory) 8 else 0
            5 -> if (vitals.batteryTempC >= 40f || vitals.isPowerSaveMode || vitals.batteryLevel <= 20) 6 else 0
            6 -> if (vitals.storageUsedPercent >= 85) 8 else 0
            7 -> if (!vitals.isInternetAvailable) 8 else 0
            9 -> if (vitals.ringerMode != "Normal" || vitals.mediaVolumePercent == 0) 6 else 0
            27 -> if (vitals.batteryTempC >= 41f) 8 else 0
            else -> 0
        }
    }

    private val STOP_WORDS = setOf(
        "mera", "meri", "mere", "phone", "mobile", "device", "hai", "hain", "raha", "rahi", "ho",
        "hota", "hoti", "kar", "karna", "nahi", "na", "nhi", "kharab", "problem", "issue",
        "dikkat", "samasya", "help", "kuch", "batao", "karo", "fix", "please", "bhai", "doctor",
        "check", "my", "this", "the", "is", "are", "and", "or", "for", "with", "not", "par",
        "se", "ko", "ki", "ka", "ke", "me", "mein", "what", "how", "can", "you"
    )
}
