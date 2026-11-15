package com.example.data.knowledge

import com.example.data.model.CoverageTier
import com.example.data.model.DoctorAction

data class KnowledgeCategoryRecord(
    val categoryId: String,
    val categoryNumber: Int,
    val categoryName: String,
    val pageStart: Int,
    val pageEnd: Int,
    val sourceChecklistCount: Int,
    val startByAsking: String,
    val possibleCauseFamilies: List<String>,
    val readOnlyChecksAndFirstLinePath: String,
    val automationBoundary: String,
    val verification: String,
    val stopEscalate: String
)

data class SolutionRevision(
    val version: Int,
    val updatedAt: Long,
    val previousSolutions: List<String>,
    val note: String
)

data class KnowledgeIssueRecord(
    val problemId: String,
    val categoryId: String,
    val categoryNumber: Int,
    val categoryName: String,
    val canonicalProblemName: String,
    val userPhrases: List<String>,
    val severity: String,
    val riskTier: String,
    val possibleCauses: List<String>,
    val diagnosticQuestions: List<String>,
    val diagnosticRules: List<String>,
    val solutions: List<String>,
    val alternativeSolutions: List<String>,
    val automaticActionIds: List<String>,
    val notAutomatable: List<String>,
    val requiredPermissions: List<String>,
    val androidRestrictions: List<String>,
    val oemSettingsKey: String,
    val oemCompatibility: List<String>,
    val verification: List<String>,
    val hardwareIndicators: List<String>,
    val escalation: List<String>,
    val issueSpecificNotes: List<String>,
    val hasIssueSpecificNote: Boolean,
    val sourcePdf: String,
    val sourcePage: Int,
    val verificationStatus: String,
    val relatedOverlaps: List<String> = emptyList(),
    val version: Int = 1,
    val revisionHistory: List<SolutionRevision> = emptyList()
)

data class IssueRagMatch(
    val issue: KnowledgeIssueRecord,
    val category: KnowledgeCategoryRecord?,
    val score: Int,
    val matchReasons: List<String>,
    val resolvedSettingsPath: String,
    val isSettingsPathVerified: Boolean,
    val coverageTier: CoverageTier,
    val allowedActions: List<DoctorAction>,
    val blockedActionNotes: List<String>
)

data class OverlapAuditGroup(
    val primaryId: String,
    val primaryTitle: String,
    val overlappingIds: List<String>,
    val reason: String
)

data class KnowledgeCoverageAudit(
    val totalCategories: Int,
    val totalIssues: Int,
    val uniqueProblemIds: Int,
    val missingFromExpected466Count: Int,
    val issuesWithBespokeNotes: Int,
    val issuesUsingCategoryWorkflow: Int,
    val incompleteFieldIssuesCount: Int,
    val overlappingIssuesCount: Int,
    val overlapGroups: List<OverlapAuditGroup>,
    val verificationStatusBreakdown: Map<String, Int>,
    val categoryIssueCounts: Map<String, Int>,
    val totalOemFamilies: Int,
    val androidVersionsSupported: List<String>,
    val customOrUpdatedIssuesCount: Int,
    val categoryWorkflowIssueIds: List<String> = emptyList()
)

data class UpdateConflictItem(
    val recordId: String,
    val reason: String,
    val existingStatus: String
)

data class KnowledgeImportResult(
    val success: Boolean,
    val addedIssueIds: List<String> = emptyList(),
    val updatedIssueIds: List<String> = emptyList(),
    val addedProfileIds: List<String> = emptyList(),
    val updatedProfileIds: List<String> = emptyList(),
    val unchangedCount: Int = 0,
    val preservedVerifiedCount: Int = 0,
    val conflicts: List<UpdateConflictItem> = emptyList(),
    val summaryMessage: String = ""
)
