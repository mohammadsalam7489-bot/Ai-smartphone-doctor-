package com.example.data.repair

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.example.data.ai.LanguageDetector
import com.example.data.diagnostics.RealDeviceDiagnostics
import com.example.data.model.ActionType
import com.example.data.model.AutomationCapability
import com.example.data.model.AutonomousRepairBundle
import com.example.data.model.AutonomousRepairStep
import com.example.data.model.AutonomousStepStatus
import com.example.data.model.DeviceVitals
import com.example.data.model.DoctorAction
import com.example.data.model.OemCompatibilityProfile
import com.example.data.model.RepairPlan
import java.util.UUID

/**
 * Problem-aware Autonomous Repair Orchestrator.
 * Separates actions into:
 * 1. Executable Autonomous Steps (NORMAL_API or granted SPECIAL_PERMISSION)
 * 2. Permission-Gated Steps (awaiting WRITE_SETTINGS or runtime permission)
 * 3. Guided Settings / In-App Settings Panel Steps (Android OS sandboxed)
 * 4. Honest OS Restriction Notices + Hardware Escalation Notes
 */
object AutonomousRepairOrchestrator {

    private val interactiveHardwareTestTypes = setOf(
        ActionType.RUN_AUDIO_TEST,
        ActionType.RUN_VIBRATION_TEST,
        ActionType.RUN_SCREEN_TEST,
        ActionType.RUN_NETWORK_TEST
    )

    fun buildBundle(
        context: Context?,
        userProblem: String,
        vitals: DeviceVitals,
        recommendedActions: List<DoctorAction>,
        repairPlan: RepairPlan?,
        oemProfile: OemCompatibilityProfile,
        language: String,
        canWriteOverride: Boolean? = null
    ): AutonomousRepairBundle? {
        if (repairPlan?.playbookId == "CLARIFY-REQUIRED") {
            return null
        }

        val combinedText = "$userProblem ${repairPlan?.problemTitle.orEmpty()}".lowercase()
        val candidateIds = linkedSetOf<String>()
        recommendedActions
            .filter { it.actionType !in interactiveHardwareTestTypes }
            .forEach { candidateIds.add(it.id) }

        // Enrich with relevant real autonomous Android API executors for the reported symptom
        val isPerformanceOrStorage = combinedText.containsAny(
            "hang", "slow", "lag", "freeze", "stuck", "storage", "space", "memory", "ram", "crash",
            "धीमा", "हैंंग", "हैंग", "लैग", "स्टोरेज", "मेमोरी", "रैम", "क्रैश"
        )
        val isBatteryOrThermal = combinedText.containsAny(
            "battery", "drain", "charge", "heat", "hot", "warm", "overheat", "fps", "display", "screen", "timeout",
            "बैटरी", "चार्ज", "गर्म", "ओवरहीट", "तापमान", "डिस्प्ले", "स्क्रीन"
        )
        val isNetwork = combinedText.containsAny(
            "wifi", "wi-fi", "internet", "net", "network", "dns", "data", "signal", "ping", "play store", "download",
            "वाई-फाई", "वाईफाई", "इंटरनेट", "नेट", "नेटवर्क", "सिग्नल"
        )
        val isAudio = combinedText.containsAny(
            "sound", "audio", "speaker", "volume", "mute", "silent", "ring", "mic", "voice", "call",
            "आवाज़", "आवाज", "स्पीकर", "वॉल्यूम", "साउंड", "म्यूट"
        )

        if (isPerformanceOrStorage) {
            candidateIds.add("auto_clear_own_cache")
            candidateIds.add("auto_optimize_memory")
            if (recommendedActions.none { it.id == "repair_app_cache" }) candidateIds.add("repair_app_cache")
        }
        if (isBatteryOrThermal) {
            candidateIds.add("auto_audit_thermal_power")
            candidateIds.add("auto_optimize_display_power")
            if (recommendedActions.none { it.id == "repair_battery_saver" }) candidateIds.add("repair_battery_saver")
        }
        if (isNetwork) {
            candidateIds.add("auto_refresh_network_socket")
            if (recommendedActions.none { it.id == "repair_network_reset" }) candidateIds.add("repair_network_reset")
        }
        if (isAudio) {
            candidateIds.add("auto_fix_audio_output")
            candidateIds.remove("auto_audit_audio") // Prefer autonomous unmute & routing fix over read-only audit
            if (recommendedActions.none { it.id == "repair_sound_levels" }) candidateIds.add("repair_sound_levels")
        }

        // Deduplicate screen timeout vs unified display optimizer
        if (candidateIds.contains("auto_optimize_display_power")) {
            candidateIds.remove("auto_screen_timeout_safe")
        }

        val resolvedActions = candidateIds.mapNotNull { SafeRepairRegistry.findAction(it) }
        if (resolvedActions.isEmpty()) return null

        val canWrite = canWriteOverride
            ?: context?.let { runCatching { Settings.System.canWrite(it) }.getOrDefault(false) }
            ?: vitals.canWriteSystemSettings

        val executableSteps = mutableListOf<AutonomousRepairStep>()
        val permissionSteps = mutableListOf<AutonomousRepairStep>()
        val guidedSteps = mutableListOf<AutonomousRepairStep>()

        resolvedActions.forEach { action ->
            val eligibility = if (context != null) {
                CapabilityEvaluator.evaluate(
                    context = context,
                    action = action,
                    apiLevel = vitals.apiLevel,
                    canWriteOverride = canWrite
                )
            } else {
                fallbackEligibility(action, vitals.apiLevel, canWrite)
            }

            if (!eligibility.isRegistered || !eligibility.isSupportedOnDevice) return@forEach

            val step = AutonomousRepairStep(
                action = action,
                capability = eligibility.capability,
                permissionGranted = eligibility.permissionGranted,
                restrictionSummary = eligibility.restrictionSummary
            )

            when {
                eligibility.capability == AutomationCapability.NORMAL_API -> {
                    executableSteps.add(step.copy(status = AutonomousStepStatus.PENDING_APPROVAL))
                }
                eligibility.capability == AutomationCapability.SPECIAL_PERMISSION && eligibility.permissionGranted -> {
                    executableSteps.add(step.copy(status = AutonomousStepStatus.PENDING_APPROVAL))
                }
                eligibility.capability == AutomationCapability.SPECIAL_PERMISSION -> {
                    permissionSteps.add(step.copy(status = AutonomousStepStatus.PERMISSION_REQUIRED))
                }
                else -> {
                    guidedSteps.add(step.copy(status = AutonomousStepStatus.SETTINGS_GUIDED))
                }
            }
        }

        val osBlockedNotices = buildOsBlockedNotices(
            isPerformanceOrStorage = isPerformanceOrStorage,
            isBatteryOrThermal = isBatteryOrThermal,
            isNetwork = isNetwork,
            isAudio = isAudio,
            vitals = vitals,
            oemProfile = oemProfile,
            language = language
        )

        val hardwareNotes = buildHardwareNotes(
            isBatteryOrThermal = isBatteryOrThermal,
            isAudio = isAudio,
            vitals = vitals,
            language = language
        )

        if (executableSteps.isEmpty() && permissionSteps.isEmpty() && guidedSteps.isEmpty()) {
            return null
        }

        return AutonomousRepairBundle(
            bundleId = UUID.randomUUID().toString(),
            problemTitle = repairPlan?.problemTitle ?: userProblem.take(60),
            executableAutoSteps = executableSteps,
            permissionGatedSteps = permissionSteps,
            guidedSettingsSteps = guidedSteps,
            osBlockedNotices = osBlockedNotices,
            hardwareEscalationNotes = hardwareNotes
        )
    }

    private fun fallbackEligibility(
        action: DoctorAction,
        apiLevel: Int,
        canWrite: Boolean
    ): ActionEligibility {
        return when (action.actionType) {
            ActionType.OPTIMIZE_SCREEN_TIMEOUT_SAFE,
            ActionType.OPTIMIZE_DISPLAY_POWER_SAFE -> ActionEligibility(
                actionId = action.id,
                isRegistered = true,
                isSupportedOnDevice = apiLevel >= action.minSdk,
                capability = AutomationCapability.SPECIAL_PERMISSION,
                permissionGranted = canWrite,
                requiresUserConfirmation = true,
                restrictionSummary = if (canWrite) "WRITE_SETTINGS granted." else "Requires WRITE_SETTINGS permission."
            )
            ActionType.CLEAR_APP_OWN_CACHE,
            ActionType.REFRESH_NETWORK_SOCKET,
            ActionType.OPTIMIZE_APP_MEMORY_AND_STATE,
            ActionType.FIX_AUDIO_ROUTING_AND_VOLUME,
            ActionType.AUDIT_AUDIO_ROUTING_AND_VOLUME,
            ActionType.AUDIT_THERMAL_AND_POWER_STATE -> ActionEligibility(
                actionId = action.id,
                isRegistered = true,
                isSupportedOnDevice = apiLevel >= action.minSdk,
                capability = AutomationCapability.NORMAL_API,
                permissionGranted = true,
                requiresUserConfirmation = false,
                restrictionSummary = "Supported directly via documented Android APIs."
            )
            else -> ActionEligibility(
                actionId = action.id,
                isRegistered = true,
                isSupportedOnDevice = apiLevel >= action.minSdk,
                capability = action.capability,
                permissionGranted = true,
                requiresUserConfirmation = true,
                restrictionSummary = action.capability.explanation
            )
        }
    }

    private fun buildOsBlockedNotices(
        isPerformanceOrStorage: Boolean,
        isBatteryOrThermal: Boolean,
        isNetwork: Boolean,
        isAudio: Boolean,
        vitals: DeviceVitals,
        oemProfile: OemCompatibilityProfile,
        language: String
    ): List<String> = buildList {
        val androidVer = vitals.androidVersion
        when (language) {
            LanguageDetector.LANG_HINDI -> {
                if (isPerformanceOrStorage) {
                    add("OS प्रतिबंध (Android $androidVer सैंडबॉक्स): कोई भी सामान्य ऐप दूसरे ऐप्स का कैश नहीं मिटा सकता और न उन्हें चुपचाप Force Stop कर सकता है। विकल्प: नीचे दिया ऐप मैनेजर खोलें।")
                }
                if (isNetwork && vitals.apiLevel >= Build.VERSION_CODES.Q) {
                    add("OS प्रतिबंध (Android 10+): सुरक्षा नियम ऐप्स को अपने आप वाई-फाई या मोबाइल डेटा बंद/चालू करने से रोकते हैं। विकल्प: इन-ऐप इंटरनेट पैनल से तुरंत बदलें।")
                }
                if (isBatteryOrThermal) {
                    add("OS प्रतिबंध: सिस्टम बैटरी सेवर को चुपचाप चालू करना केवल सिस्टम/ओएस स्तर पर संभव है। विकल्प: ${oemProfile.batterySettingsPath} सीधे खोलें।")
                }
                if (isAudio) {
                    add("OS सीमा: मीडिया वॉल्यूम और कॉल रूटिंग ऐप स्वतः सुधार सकता है, लेकिन DND / रिंगर नीति केवल सिस्टम वॉल्यूम पैनल से बदलती है।")
                }
            }
            LanguageDetector.LANG_HINGLISH -> {
                if (isPerformanceOrStorage) {
                    add("OS Restriction (Android $androidVer Sandbox): Apps doosre apps ka cache clear ya silently force-stop nahi kar sakte. Guided App Manager button neeche diya hai.")
                }
                if (isNetwork && vitals.apiLevel >= Build.VERSION_CODES.Q) {
                    add("OS Restriction (Android 10+): Apps silently Wi-Fi ya Mobile Data toggle nahi kar sakte. Neeche In-App Internet Panel diya hai.")
                }
                if (isBatteryOrThermal) {
                    add("OS Restriction: System Battery Saver silently ON nahi ho sakta. Seedha ${oemProfile.batterySettingsPath} kholne ka option diya hai.")
                }
                if (isAudio) {
                    add("OS Limit: Media volume aur stuck routing auto-fix ho sakta hai, par DND / Ringer mode sirf In-App Volume Panel se badalta hai.")
                }
            }
            else -> {
                if (isPerformanceOrStorage) {
                    add("OS Restriction (Android $androidVer Sandbox): Standard apps cannot silently wipe 3rd-party app caches or force-stop background apps. Guided App Manager is attached below.")
                }
                if (isNetwork && vitals.apiLevel >= Build.VERSION_CODES.Q) {
                    add("OS Restriction (Android 10+): Apps cannot silently toggle Wi-Fi or Mobile Data radios. Use the In-App Internet Panel below.")
                }
                if (isBatteryOrThermal) {
                    add("OS Restriction: System Battery Saver cannot be toggled silently by user apps. Direct ${oemProfile.batterySettingsPath} shortcut is provided.")
                }
                if (isAudio) {
                    add("OS Boundary: Media stream volume & communication routing can be fixed automatically; DND / Ringer overrides require the Volume Panel.")
                }
            }
        }
    }

    private fun buildHardwareNotes(
        isBatteryOrThermal: Boolean,
        isAudio: Boolean,
        vitals: DeviceVitals,
        language: String
    ): List<String> = buildList {
        val tempHot = vitals.temperatureVerified && vitals.batteryTempC >= 44f
        val badBatteryHealth = vitals.batteryHealth in listOf("Dead", "Overheating", "Over Voltage")
        if (tempHot || badBatteryHealth) {
            when (language) {
                LanguageDetector.LANG_HINDI ->
                    add("हार्डवेयर चेतावनी: लाइव सेंसर बैटरी स्वास्थ्य '${vitals.batteryHealth}' (${if (vitals.temperatureVerified) "${vitals.batteryTempC}°C" else "तापमान अनुपलब्ध"}) दिखाता है — यदि ठंडा न हो तो सर्विस केंद्र पर बैटरी जांच कराएं।")
                LanguageDetector.LANG_HINGLISH ->
                    add("Hardware Alert: Live sensor battery health '${vitals.batteryHealth}' (${if (vitals.temperatureVerified) "${vitals.batteryTempC}°C" else "Temp N/A"}) dikha raha hai — service center inspection zaroori ho sakti hai.")
                else ->
                    add("Hardware Alert: Live telemetry shows Battery Health '${vitals.batteryHealth}' (${if (vitals.temperatureVerified) "${vitals.batteryTempC}°C" else "Temp N/A"}) — inspect hardware at an authorized service center if persistent.")
            }
        } else if (isBatteryOrThermal) {
            when (language) {
                LanguageDetector.LANG_HINDI ->
                    add("यदि सुरक्षित सॉफ़्टवेयर सुधार के बाद भी फोन बिना उपयोग के 45°C+ रहे या बैटरी फूली दिखे, तो हार्डवेयर जांच आवश्यक है।")
                LanguageDetector.LANG_HINGLISH ->
                    add("Agar software fix ke baad bhi phone bina use ke 45°C+ garam rahe ya battery phooli lage, toh service center par dikhayein.")
                else ->
                    add("Hardware check needed only if phone stays >45°C idle or battery shows physical swelling after software fixes.")
            }
        } else if (isAudio) {
            when (language) {
                LanguageDetector.LANG_HINDI ->
                    add("यदि मीडिया वॉल्यूम सुधारने और ब्लूटूथ बंद करने पर भी 587Hz परीक्षण ध्वनि साफ न सुनाई दे, तो स्पीकर कॉइल हार्डवेयर जांच कराएं।")
                LanguageDetector.LANG_HINGLISH ->
                    add("Agar media volume fix aur Bluetooth OFF hone par bhi 587Hz test tone saaf na aaye, toh speaker hardware check karayein.")
                else ->
                    add("If 587Hz test tone remains silent or crackling after volume fix with Bluetooth OFF, speaker hardware service is required.")
            }
        }
    }

    fun formatVitalsSnapshotLine(vitals: DeviceVitals, language: String): String {
        val ramTxt = if (vitals.ramVerified) RealDeviceDiagnostics.formatBytes(vitals.ramFreeBytes) else "N/A"
        val storageTxt = if (vitals.storageVerified) RealDeviceDiagnostics.formatBytes(vitals.storageFreeBytes) else "N/A"
        val tempTxt = if (vitals.temperatureVerified) "${vitals.batteryTempC}°C" else "N/A"
        val sleepTxt = if (vitals.screenTimeoutSeconds >= 0) "${vitals.screenTimeoutSeconds}s" else "N/A"
        val volTxt = if (vitals.audioVerified) "${vitals.mediaVolumePercent}%" else "N/A"
        return when (language) {
            LanguageDetector.LANG_HINDI ->
                "स्कोर ${vitals.healthScore}/100 • रैम $ramTxt • स्टोरेज $storageTxt • ताप $tempTxt • स्लीप $sleepTxt • वॉल्यूम $volTxt"
            else ->
                "Score ${vitals.healthScore}/100 • RAM $ramTxt • Storage $storageTxt • Temp $tempTxt • Sleep $sleepTxt • Vol $volTxt"
        }
    }

    private fun String.containsAny(vararg tokens: String): Boolean = tokens.any { this.contains(it) }
}
