package com.example.data.repair

import com.example.data.ai.LanguageDetector
import com.example.data.diagnostics.RealDeviceDiagnostics
import com.example.data.knowledge.DiagnosticPlaybook
import com.example.data.model.ActionType
import com.example.data.model.BeforeAfterComparison
import com.example.data.model.DeviceVitals
import com.example.data.model.DoctorAction
import com.example.data.model.VerificationOutcome

/**
 * Compulsory Repair Verification Engine.
 * Never assumes a repair succeeded without comparing Before & After diagnostics
 * or obtaining explicit user confirmation when changes occur outside app sandbox.
 */
object RepairVerificationEngine {

    fun verifyBeforeAfter(
        action: DoctorAction,
        before: DeviceVitals,
        after: DeviceVitals,
        executionResult: ActionResult,
        playbook: DiagnosticPlaybook? = null,
        alreadyTriedActionIds: Set<String> = emptySet(),
        issueId: String? = null,
        language: String = LanguageDetector.LANG_ENGLISH
    ): BeforeAfterComparison {
        val deltas = mutableListOf<String>()

        val storageDelta = if (before.storageVerified && after.storageVerified) {
            after.storageFreeBytes - before.storageFreeBytes
        } else 0L
        if (storageDelta != 0L) {
            val sign = if (storageDelta > 0) "+" else "-"
            val label = if (language == LanguageDetector.LANG_HINDI) "खाली स्टोरेज" else "Free Storage"
            deltas.add("$label: $sign${RealDeviceDiagnostics.formatBytes(kotlin.math.abs(storageDelta))}")
        }

        val ramDelta = if (before.ramVerified && after.ramVerified) {
            after.ramFreeBytes - before.ramFreeBytes
        } else 0L
        if (kotlin.math.abs(ramDelta) >= 1024 * 1024L) {
            val sign = if (ramDelta > 0) "+" else "-"
            val label = if (language == LanguageDetector.LANG_HINDI) "खाली रैम" else "Free RAM"
            deltas.add("$label: $sign${RealDeviceDiagnostics.formatBytes(kotlin.math.abs(ramDelta))}")
        }

        val tempDelta = if (before.temperatureVerified && after.temperatureVerified) {
            after.batteryTempC - before.batteryTempC
        } else 0f
        if (kotlin.math.abs(tempDelta) >= 0.2f) {
            val sign = if (tempDelta > 0) "+" else ""
            val label = if (language == LanguageDetector.LANG_HINDI) "बैटरी तापमान" else "Battery Temp"
            deltas.add("$label: $sign${String.format(java.util.Locale.US, "%.1f", tempDelta)}°C")
        }

        if (before.screenTimeoutSeconds >= 0 && after.screenTimeoutSeconds >= 0 &&
            before.screenTimeoutSeconds != after.screenTimeoutSeconds
        ) {
            val label = if (language == LanguageDetector.LANG_HINDI) "स्क्रीन टाइमआउट" else "Screen Timeout"
            deltas.add("$label: ${before.screenTimeoutSeconds}s -> ${after.screenTimeoutSeconds}s")
        }

        if (before.networkVerified && after.networkVerified && before.isInternetAvailable != after.isInternetAvailable) {
            deltas.add("Internet: ${if (before.isInternetAvailable) "Online" else "Offline"} -> ${if (after.isInternetAvailable) "Online" else "Offline"}")
        }

        if (before.isPowerSaveMode != after.isPowerSaveMode) {
            val label = if (language == LanguageDetector.LANG_HINDI) "बैटरी सेवर" else "Battery Saver"
            deltas.add("$label: ${before.isPowerSaveMode} -> ${after.isPowerSaveMode}")
        }

        if (before.audioVerified && after.audioVerified && before.mediaVolumePercent != after.mediaVolumePercent) {
            val label = if (language == LanguageDetector.LANG_HINDI) "मीडिया वॉल्यूम" else "Media Volume"
            deltas.add("$label: ${before.mediaVolumePercent}% -> ${after.mediaVolumePercent}%")
        }

        val scoreDelta = after.healthScore - before.healthScore
        if (scoreDelta != 0) {
            val sign = if (scoreDelta > 0) "+" else ""
            val label = if (language == LanguageDetector.LANG_HINDI) "स्वास्थ्य स्कोर" else "Health Score"
            deltas.add("$label: ${before.healthScore} -> ${after.healthScore} ($sign$scoreDelta)")
        }

        if (deltas.isEmpty()) {
            deltas.add(
                when (language) {
                    LanguageDetector.LANG_HINDI -> "सिस्टम-व्यापी आँकड़ों में तुरंत कोई बड़ा बदलाव नहीं दिखा (${executionResult.measuredDetails.firstOrNull() ?: "सैंडबॉक्स कार्य"})।"
                    LanguageDetector.LANG_HINGLISH -> "System metrics mein turant koi bada badlav nahi dikha (${executionResult.measuredDetails.firstOrNull() ?: "sandboxed action"})."
                    else -> "No immediate system-wide numeric shift detected (${executionResult.measuredDetails.firstOrNull() ?: "sandboxed action"})."
                }
            )
        }

        val outcome = when {
            !executionResult.isSuccess -> VerificationOutcome.EXECUTION_FAILED
            executionResult.verifiedMetricImproved -> VerificationOutcome.VERIFIED_IMPROVED
            (action.actionType == ActionType.OPTIMIZE_SCREEN_TIMEOUT_SAFE ||
                action.actionType == ActionType.OPTIMIZE_DISPLAY_POWER_SAFE) &&
                after.screenTimeoutSeconds >= 0 &&
                before.screenTimeoutSeconds >= 0 &&
                after.screenTimeoutSeconds < before.screenTimeoutSeconds -> VerificationOutcome.VERIFIED_IMPROVED
            action.actionType == ActionType.FIX_AUDIO_ROUTING_AND_VOLUME &&
                after.audioVerified && before.audioVerified &&
                after.mediaVolumePercent > before.mediaVolumePercent -> VerificationOutcome.VERIFIED_IMPROVED
            storageDelta > 100 * 1024L || ramDelta > 10 * 1024 * 1024L ||
                scoreDelta > 0 || (after.isPowerSaveMode && !before.isPowerSaveMode) ->
                VerificationOutcome.VERIFIED_IMPROVED
            executionResult.requiresUserConfirmationToVerify ->
                VerificationOutcome.NEEDS_USER_CONFIRMATION
            else -> VerificationOutcome.NO_MEASURABLE_CHANGE
        }

        val nextAlternative = if (outcome == VerificationOutcome.EXECUTION_FAILED ||
            outcome == VerificationOutcome.NO_MEASURABLE_CHANGE ||
            outcome == VerificationOutcome.NEEDS_USER_CONFIRMATION
        ) {
            selectNextSafeAlternative(action.id, playbook, alreadyTriedActionIds, issueId = issueId)
        } else null

        fun formatSummarySnapshot(v: DeviceVitals): String {
            val ramTxt = if (v.ramVerified) RealDeviceDiagnostics.formatBytes(v.ramFreeBytes) else "N/A"
            val stTxt = if (v.storageVerified) RealDeviceDiagnostics.formatBytes(v.storageFreeBytes) else "N/A"
            val tTxt = if (v.temperatureVerified) "${v.batteryTempC}°C" else "N/A"
            val slpTxt = if (v.screenTimeoutSeconds >= 0) "${v.screenTimeoutSeconds}s" else "N/A"
            return "Score ${v.healthScore}/100 • RAM $ramTxt • Storage $stTxt • Temp $tTxt • Sleep $slpTxt"
        }

        val beforeSummary = formatSummarySnapshot(before)
        val afterSummary = formatSummarySnapshot(after)

        val technicalMessage = when (language) {
            LanguageDetector.LANG_HINDI -> when (outcome) {
                VerificationOutcome.VERIFIED_IMPROVED -> "सत्यापित सुधार: ${executionResult.message}"
                VerificationOutcome.NEEDS_USER_CONFIRMATION -> "कार्य पूर्ण हुआ (${executionResult.message})। एंड्रॉइड सैंडबॉक्स के कारण अन्य ऐप्स के बदलाव बाहरी हैं — कृपया नीचे पुष्टि करें कि समस्या ठीक हुई या नहीं।"
                VerificationOutcome.NO_MEASURABLE_CHANGE -> "कार्य चलाया गया (${executionResult.message}), लेकिन अभी सेंसर मान में कोई मापने योग्य अंतर नहीं दिखा। नीचे अगला सुरक्षित चरण आज़माएं।"
                VerificationOutcome.EXECUTION_FAILED -> "कार्य स्वतः पूर्ण नहीं हो सका: ${executionResult.message}"
            }
            LanguageDetector.LANG_HINGLISH -> when (outcome) {
                VerificationOutcome.VERIFIED_IMPROVED -> "Verified sudhar mila: ${executionResult.message}"
                VerificationOutcome.NEEDS_USER_CONFIRMATION -> "Action run ho gaya (${executionResult.message}). Android privacy sandbox ki wajah se please confirm karein ki problem theek hui ya nahi."
                VerificationOutcome.NO_MEASURABLE_CHANGE -> "Action run hua (${executionResult.message}), par system metrics mein turant badlav nahi dikha. Neeche next safe option try karein."
                VerificationOutcome.EXECUTION_FAILED -> "Action automatic complete nahi ho paya: ${executionResult.message}"
            }
            else -> when (outcome) {
                VerificationOutcome.VERIFIED_IMPROVED -> "Verified improvement: ${executionResult.message}"
                VerificationOutcome.NEEDS_USER_CONFIRMATION -> "Action executed (${executionResult.message}). Because Android isolates third-party app changes, please confirm whether the issue improved."
                VerificationOutcome.NO_MEASURABLE_CHANGE -> "Executed (${executionResult.message}), but system metrics show no measurable change yet. Try the next safe step below."
                VerificationOutcome.EXECUTION_FAILED -> "Action could not complete automatically: ${executionResult.message}"
            }
        }

        return BeforeAfterComparison(
            actionId = action.id,
            actionTitle = action.title,
            beforeSummary = beforeSummary,
            afterSummary = afterSummary,
            metricDeltas = deltas,
            outcome = outcome,
            technicalMessage = technicalMessage,
            nextAlternativeAction = nextAlternative
        )
    }

    fun selectNextSafeAlternative(
        failedOrCurrentActionId: String,
        playbook: DiagnosticPlaybook?,
        alreadyTriedActionIds: Set<String>,
        issueId: String? = null
    ): DoctorAction? {
        val tried = alreadyTriedActionIds + failedOrCurrentActionId
        val issueActions = issueId?.let { id ->
            com.example.data.knowledge.KnowledgeRepository.findIssueById(id)?.automaticActionIds
        }.orEmpty()
        val sourceIds = (issueActions + playbook?.automaticActionIds.orEmpty()).distinct()

        val candidates = sourceIds
            .mapNotNull { SafeRepairRegistry.findAction(it) }
            .filter { !it.isDestructive && it.id !in tried }

        if (candidates.isNotEmpty()) return candidates.first()

        val fallbackSequence = listOf(
            "repair_app_cache",
            "repair_storage_cleaner",
            "repair_battery_saver",
            "repair_network_reset",
            "repair_system_update",
            "repair_settings_search"
        )
        return fallbackSequence
            .firstOrNull { it !in tried }
            ?.let { SafeRepairRegistry.findAction(it) }
    }
}
