package com.example.data.repair

/**
 * Exact Capability Scope & Blueprint Schema aligned with PDF Section 5:
 * "Android AI Automatic Repair — Developer Blueprint & Real-World Repair Catalog".
 */
enum class CapabilityScope {
    OWN_APP,
    USER_SELECTED_URI,
    SHARED_STORAGE,
    VPN
}

enum class BlueprintRiskLevel {
    SAFE_REVERSIBLE,
    CONFIRMATION_REQUIRED,
    UNSUPPORTED
}

enum class RepairPipelineStatus {
    FIXED,
    UNRESOLVED,
    NEEDS_APPROVAL,
    UNSUPPORTED
}

data class RepairPreconditionResult(
    val satisfied: Boolean,
    val reason: String,
    val alreadyHealthy: Boolean = false,
    val snapshotData: Map<String, String> = emptyMap(),
    val missingPermission: String? = null
)

data class RepairExecutionOutcome(
    val success: Boolean,
    val idempotentSkipped: Boolean = false,
    val details: List<String> = emptyList(),
    val errorMessage: String? = null,
    val mutatedSnapshot: Map<String, String> = emptyMap()
)

data class RepairVerificationResult(
    val passed: Boolean,
    val testName: String,
    val observedSummary: String,
    val failureReason: String? = null
)

/**
 * PDF Section 5 Standard RepairDefinition Schema.
 */
data class RepairDefinition(
    val issueId: String,
    val categoryNumber: Int,
    val categoryName: String,
    val complaintExamples: List<String>,
    val supportedAndroidApi: IntRange = 24..36,
    val requiredPermissions: List<String> = emptyList(),
    val capabilityScope: CapabilityScope,
    val preconditionChecks: List<String>,
    val risk: BlueprintRiskLevel,
    val actionHandlerId: String,
    val rollbackHandlerId: String? = null,
    val verificationChecks: List<String>,
    val successMessageKey: String,
    val failureMessageKey: String,
    val maxAttempts: Int = 2,
    val privacyDataCollected: String,
    val mappedDoctorActionIds: List<String> = emptyList(),
    val unsupportedReason: String? = null,
    val skipsQuestionWhenDiagnosable: Boolean = true
)

data class PipelineStageTrace(
    val stageNumber: Int,
    val stageName: String,
    val passed: Boolean,
    val summary: String
)

data class RepairPipelineReport(
    val issueId: String,
    val categoryName: String,
    val definition: RepairDefinition,
    val status: RepairPipelineStatus,
    val userVisibleHeadline: String,
    val userVisibleDetails: String,
    val askedClarifyingQuestions: Boolean,
    val skippedAsIdempotentHealthy: Boolean,
    val rollbackExecuted: Boolean,
    val rollbackSummary: String? = null,
    val verificationResult: RepairVerificationResult?,
    val trace: List<PipelineStageTrace>,
    val mappedDoctorActions: List<String>
)
