package com.example.data.model

import android.graphics.Bitmap

enum class CollectorPrivacyClass {
    PUBLIC_HARDWARE_INFO,
    SYSTEM_RESOURCE_METRICS,
    NETWORK_LINK_STATE,
    AUDIO_SYSTEM_STATE,
    PERMISSION_STATUS
}

data class CollectorCapability(
    val collectorId: String,
    val name: String,
    val dataSource: String,
    val requiredPermission: String,
    val minSdk: Int,
    val oemCompatibility: String,
    val privacyClass: CollectorPrivacyClass,
    val isAvailable: Boolean,
    val currentValueSummary: String,
    val errorNote: String? = null
)

data class DeviceVitals(
    val manufacturer: String,
    val model: String,
    val brand: String,
    val androidVersion: String,
    val apiLevel: Int,
    val batteryLevel: Int,
    val batteryStatus: String,
    val batteryPlugged: String,
    val batteryTempC: Float,
    val batteryHealth: String,
    val storageTotalBytes: Long,
    val storageFreeBytes: Long,
    val storageUsedPercent: Int,
    val ramTotalBytes: Long,
    val ramFreeBytes: Long,
    val ramUsedPercent: Int,
    val isLowMemory: Boolean,
    val networkType: String,
    val isInternetAvailable: Boolean,
    val displayResolution: String,
    val refreshRate: Float,
    val screenTimeoutSeconds: Int,
    val uptimeHours: Float,
    val healthScore: Int,
    // Extended real hardware/system telemetry
    val cpuArchitecture: String = "Unknown",
    val thermalStatus: String = "Nominal",
    val isPowerSaveMode: Boolean = false,
    val isWifiEnabled: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val ringerMode: String = "Normal",
    val mediaVolumePercent: Int = 50,
    val notificationsEnabled: Boolean = true,
    val canWriteSystemSettings: Boolean = false,
    val ownAppExitSummary: String = "Own-app exit check unavailable (< Android 11); 3rd-party crash logs sandboxed by OS",
    val activeCollectorCapabilities: List<CollectorCapability> = emptyList(),
    // Telemetry Verification Provenance (never invent readings without collector confirmation)
    val batteryVerified: Boolean = true,
    val temperatureVerified: Boolean = true,
    val thermalVerified: Boolean = true,
    val ramVerified: Boolean = true,
    val storageVerified: Boolean = true,
    val networkVerified: Boolean = true,
    val displayVerified: Boolean = true,
    val audioVerified: Boolean = true,
    val collectionTimestampMs: Long = System.currentTimeMillis(),
    val unverifiedMetrics: List<String> = emptyList()
)

enum class SenderType {
    USER,
    DOCTOR,
    SYSTEM
}

enum class AutomationCapability(val badgeLabel: String, val explanation: String) {
    NORMAL_API(
        badgeLabel = "AUTO API",
        explanation = "Executed directly via standard documented Android APIs within app scope."
    ),
    SPECIAL_PERMISSION(
        badgeLabel = "SPECIAL PERM",
        explanation = "Requires explicit user-granted special Android permission (e.g. Modify System Settings)."
    ),
    ACCESSIBILITY_SERVICE(
        badgeLabel = "ACCESSIBILITY",
        explanation = "Would require an Accessibility Service; not silently enabled — uses safe System Settings instead."
    ),
    MANUAL_SETTINGS(
        badgeLabel = "SYSTEM SETTINGS",
        explanation = "Android security sandboxing requires user confirmation inside native Android Settings."
    ),
    DEVICE_OWNER(
        badgeLabel = "DEVICE OWNER",
        explanation = "Restricted to managed Device Owner / Enterprise MDM policies; manual guide provided."
    ),
    OEM_RESTRICTED(
        badgeLabel = "OEM RESTRICTED",
        explanation = "Controlled by manufacturer firmware; verified OEM Settings path or search provided."
    )
}

enum class RiskClassification(val label: String) {
    READ_ONLY("Read-Only"),
    SAFE_APP_LEVEL("Safe App-Level"),
    SYSTEM_SETTINGS_INTENT("System Settings"),
    SPECIAL_PERMISSION_WRITE("Special Permission"),
    DESTRUCTIVE_GUARDED("High-Risk Guarded")
}

enum class ActionType {
    // Level 3 System Intents
    OPEN_BATTERY_SETTINGS,
    OPEN_STORAGE_SETTINGS,
    OPEN_APP_SETTINGS,
    OPEN_WIFI_SETTINGS,
    OPEN_DISPLAY_SETTINGS,
    OPEN_SOUND_SETTINGS,
    OPEN_BLUETOOTH_SETTINGS,
    OPEN_SYSTEM_UPDATE,
    OPEN_NOTIFICATION_SETTINGS,
    OPEN_MOBILE_DATA_SETTINGS,
    OPEN_ACCESSIBILITY_SETTINGS,
    OPEN_SETTINGS_SEARCH,
    OPEN_LOCATION_SETTINGS,
    OPEN_SECURITY_PRIVACY_SETTINGS,
    OPEN_ACCOUNTS_SYNC_SETTINGS,
    OPEN_LANGUAGE_INPUT_SETTINGS,
    OPEN_DEFAULT_APPS_SETTINGS,
    OPEN_DATE_TIME_SETTINGS,
    OPEN_DEVELOPER_OPTIONS_SETTINGS,
    // Level 1 Hardware & System Diagnostics
    RUN_AUDIO_TEST,
    RUN_VIBRATION_TEST,
    RUN_SCREEN_TEST,
    RUN_NETWORK_TEST,
    // Level 1 / Level 2 Real Automated Android Actions
    CLEAR_APP_OWN_CACHE,
    REFRESH_NETWORK_SOCKET,
    OPTIMIZE_APP_MEMORY_AND_STATE,
    AUDIT_AUDIO_ROUTING_AND_VOLUME,
    FIX_AUDIO_ROUTING_AND_VOLUME,
    AUDIT_THERMAL_AND_POWER_STATE,
    OPTIMIZE_SCREEN_TIMEOUT_SAFE,
    OPTIMIZE_DISPLAY_POWER_SAFE,
    RUN_OWN_STORAGE_HOUSEKEEPING,
    RUN_PRIVACY_RETENTION_CLEANUP,
    SCHEDULE_PERIODIC_MAINTENANCE,
    // Destructive Guarded Guidance (NEVER auto-executed)
    GUARDED_CLEAR_APP_DATA_GUIDE,
    GUARDED_UNINSTALL_UNUSED_GUIDE,
    GUARDED_RESET_NETWORK_GUIDE
}

enum class RepairLevel(val code: String, val title: String) {
    LEVEL_0("L0", "Information Only"),
    LEVEL_1("L1", "Read-Only Diagnostics"),
    LEVEL_2("L2", "Safe Application-Level Action"),
    LEVEL_3("L3", "User-Approved System Settings"),
    LEVEL_4("L4", "Guided Manual Repair")
}

enum class SeverityLevel(val code: String, val label: String) {
    P0("P0", "Critical (Boot / Safety / Security)"),
    P1("P1", "High (Major Feature Unavailable)"),
    P2("P2", "Medium (Degraded Function)"),
    P3("P3", "Low (Configuration / Convenience)"),
    P4("P4", "Informational (How-to)")
}

enum class RiskTier(val code: String, val label: String) {
    R0("R0", "Read-Only"),
    R1("R1", "Low Reversible"),
    R2("R2", "Moderate (Explain Side Effects)"),
    R3("R3", "Destructive (Backup + Confirm)"),
    R4("R4", "Critical (Official Support Only)")
}

enum class CoverageTier(val code: String, val label: String, val userClaim: String) {
    A_LAB_VERIFIED("Tier A", "Lab Verified", "Exact model/build physically tested."),
    B_OEM_FAMILY_VERIFIED("Tier B", "OEM Family Verified", "Selected models of skin family verified; search fallback provided."),
    C_OS_BASELINE_VERIFIED("Tier C", "OS Baseline Verified", "AOSP/Android baseline verified; OEM menus may differ."),
    D_UNKNOWN("Tier D", "Unknown / Unverified", "Exact steps unverified for this device — use Settings Search.")
}

data class DoctorAction(
    val id: String,
    val title: String,
    val description: String,
    val actionType: ActionType,
    val level: RepairLevel = RepairLevel.LEVEL_3,
    val riskLevel: String = "Safe",
    val isReversible: Boolean = true,
    val whyRequired: String = "Required to optimize device configuration",
    val whatWillChange: String = "Opens verified Android system settings",
    val capability: AutomationCapability = AutomationCapability.MANUAL_SETTINGS,
    val riskClass: RiskClassification = RiskClassification.SYSTEM_SETTINGS_INTENT,
    val requiredPermission: String? = null,
    val minSdk: Int = 24,
    val verificationMethod: String = "Compare before/after device telemetry and confirm outcome",
    val backupGuidance: String? = null,
    val isDestructive: Boolean = false,
    val fallbackManualSteps: List<String> = emptyList()
)

enum class AiServiceStatus(val badgeText: String, val isCloudAi: Boolean) {
    ONLINE_GEMINI("GEMINI 3.8 FLASH ACTIVE", true),
    UNAVAILABLE_OFFLINE_FALLBACK("AI UNAVAILABLE • PLAYBOOK MODE", false),
    TIMEOUT_FALLBACK("AI TIMED OUT • PLAYBOOK MODE", false),
    ERROR_FALLBACK("AI ERROR • PLAYBOOK MODE", false)
}

data class RepairPlan(
    val playbookId: String,
    val problemTitle: String,
    val confidenceLevel: String,
    val confirmedEvidence: List<String>,
    val possibleCauses: List<String>,
    val diagnosticQuestions: List<String>,
    val unverifiedAssumptions: List<String>,
    val hardwareFaultIndicators: List<String>,
    val escalationInstructions: String,
    val verificationMethod: String,
    val matchedIssueId: String? = null,
    val sourceCitation: String? = null,
    val severityLabel: String? = null,
    val riskTierLabel: String? = null,
    val coverageTierLabel: String? = null,
    val oemResolvedPath: String? = null,
    val relatedOverlapIds: List<String> = emptyList()
)

enum class VerificationOutcome(val label: String) {
    VERIFIED_IMPROVED("Verified Improved"),
    NEEDS_USER_CONFIRMATION("Needs User Confirmation"),
    NO_MEASURABLE_CHANGE("No Measurable Change"),
    EXECUTION_FAILED("Execution Failed")
}

data class BeforeAfterComparison(
    val actionId: String,
    val actionTitle: String,
    val beforeSummary: String,
    val afterSummary: String,
    val metricDeltas: List<String>,
    val outcome: VerificationOutcome,
    val technicalMessage: String,
    val nextAlternativeAction: DoctorAction? = null
)

enum class CapabilityTierCategory(val order: Int) {
    AUTO_EXECUTED(1),
    PERMISSION_GATED(2),
    IN_APP_PANEL_OR_SETTINGS(3),
    OS_SANDBOX_BLOCKED(4),
    HARDWARE_SERVICE_REQUIRED(5)
}

data class CapabilityMatrixEntry(
    val id: String,
    val titleEn: String,
    val titleHi: String,
    val category: CapabilityTierCategory,
    val androidApiOrPermission: String,
    val isCurrentlyExecutableOnDevice: Boolean,
    val statusDetailEn: String,
    val statusDetailHi: String,
    val guidedAlternativeActionId: String? = null
)

enum class AutonomousStepStatus {
    PENDING_APPROVAL,
    EXECUTING,
    VERIFIED_IMPROVED,
    EXECUTED_NO_SHIFT,
    NEEDS_USER_CONFIRMATION,
    PERMISSION_REQUIRED,
    SETTINGS_GUIDED,
    BLOCKED_OR_FAILED
}

data class AutonomousRepairStep(
    val action: DoctorAction,
    val capability: AutomationCapability,
    val permissionGranted: Boolean,
    val restrictionSummary: String,
    val status: AutonomousStepStatus = AutonomousStepStatus.PENDING_APPROVAL,
    val resultMessage: String? = null,
    val measuredDetails: List<String> = emptyList(),
    val beforeAfter: BeforeAfterComparison? = null
)

data class AutonomousRepairBundle(
    val bundleId: String,
    val problemTitle: String,
    val executableAutoSteps: List<AutonomousRepairStep> = emptyList(),
    val permissionGatedSteps: List<AutonomousRepairStep> = emptyList(),
    val guidedSettingsSteps: List<AutonomousRepairStep> = emptyList(),
    val osBlockedNotices: List<String> = emptyList(),
    val hardwareEscalationNotes: List<String> = emptyList(),
    val isExecuting: Boolean = false,
    val isApprovedAndExecuted: Boolean = false,
    val beforeVitalsSummary: String? = null,
    val afterVitalsSummary: String? = null,
    val overallOutcomeSummary: String? = null,
    val needsPhysicalConfirmation: Boolean = false
)

data class ChatMessage(
    val id: String,
    val sender: SenderType,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val actions: List<DoctorAction> = emptyList(),
    val vitalsSnapshot: String? = null,
    val attachedScreenshot: Bitmap? = null,
    val attachedScreenshotName: String? = null,
    val verificationRequestedForAction: DoctorAction? = null,
    val repairPlan: RepairPlan? = null,
    val verificationComparison: BeforeAfterComparison? = null,
    val autonomousBundle: AutonomousRepairBundle? = null,
    val aiStatus: AiServiceStatus? = null,
    val aiStatusDetail: String? = null,
    val responseLanguage: String? = null,
    val retryPrompt: String? = null,
    val retryScreenshot: Bitmap? = null,
    val canRetryCloudAi: Boolean = false
)

data class DiagnosticHistoryItem(
    val id: String,
    val timestamp: Long,
    val problemTitle: String,
    val category: String,
    val diagnosisSummary: String,
    val actionExecuted: String?,
    val verificationStatus: String,
    val playbookId: String? = null,
    val capabilityUsed: String? = null,
    val beforeSnapshot: String? = null,
    val afterSnapshot: String? = null,
    val aiModeUsed: String? = null
)

data class AndroidVersionCompatibility(
    val apiLevel: Int,
    val versionLabel: String,
    val isFutureOrPreview: Boolean,
    val apiRestrictions: List<String>,
    val supportedAutomationNotes: String
)

data class SettingsPathResolution(
    val actionId: String,
    val canonicalSetting: String,
    val resolvedPath: String,
    val oemAliases: List<String>,
    val fallbackSearchTerms: List<String>,
    val isVerifiedPath: Boolean,
    val coverageTier: CoverageTier
)

data class DeviceIdentitySummary(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    val androidRelease: String,
    val apiLevel: Int,
    val securityPatch: String,
    val formFactor: String,
    val gmsExpected: Boolean
)

data class OemCompatibilityProfile(
    val oemName: String,
    val osFlavor: String,
    val batterySettingsPath: String,
    val storageSettingsPath: String,
    val appCacheSettingsPath: String,
    val specialNotes: String,
    val networkSettingsPath: String = "Settings > Network & Internet / Connections",
    val notificationSettingsPath: String = "Settings > Apps > [Select App] > Notifications",
    val displaySettingsPath: String = "Settings > Display",
    val soundSettingsPath: String = "Settings > Sound & vibration",
    val bluetoothSettingsPath: String = "Settings > Connected devices / Bluetooth",
    val systemUpdateSettingsPath: String = "Settings > System > Software update",
    val accessibilitySettingsPath: String = "Settings > Accessibility",
    val locationSettingsPath: String = "Settings > Location",
    val securitySettingsPath: String = "Settings > Security & privacy",
    val accountsSettingsPath: String = "Settings > Accounts / Users & accounts",
    val languageSettingsPath: String = "Settings > System > Languages & input",
    val defaultAppsSettingsPath: String = "Settings > Apps > Default apps",
    val developerSettingsPath: String = "Settings > System > Developer options",
    val profileId: String = "OEM-GENERIC",
    val coverageTier: CoverageTier = CoverageTier.D_UNKNOWN,
    val verificationStatus: String = "doc_referenced",
    val gmsAvailable: Boolean = true,
    val formFactors: List<String> = listOf("phone"),
    val isPathVerifiedForBuild: Boolean = true,
    val androidCompatibility: AndroidVersionCompatibility = AndroidVersionCompatibility(
        apiLevel = 34,
        versionLabel = "Android 14+",
        isFutureOrPreview = false,
        apiRestrictions = emptyList(),
        supportedAutomationNotes = "Standard Android APIs supported."
    ),
    val lastVerifiedDate: String = "2026-11-12"
)
