package com.example.data.knowledge

import android.content.Context
import com.example.data.compatibility.DeviceCompatibilityEngine

/**
 * Programmatic, honest Knowledge Base & Compatibility Coverage Auditor.
 * Computes live verification metrics across all categories, issue IDs, overlaps,
 * bespoke notes vs inherited category workflows, and OEM compatibility profiles.
 */
object KnowledgeCoverageAuditor {

    fun generateLiveAudit(context: Context? = null): KnowledgeCoverageAudit {
        if (context != null) {
            KnowledgeRepository.ensureLoadedSynchronously(context)
        }
        val categories = KnowledgeRepository.getAllCategories()
        val issues = KnowledgeRepository.getAllIssues()
        val uniqueIds = issues.map { it.problemId }.toSet()

        // Verify exact expected sequential IDs across all 29 categories
        val expectedCounts = listOf(
            15, 20, 25, 25, 17, 20, 20, 18, 16, 14,
            30, 14, 20, 20, 14, 16, 15, 16, 12, 14,
            14, 14, 12, 15, 10, 12, 8, 10, 10
        )
        val expectedIds = buildSet {
            expectedCounts.forEachIndexed { idx, count ->
                val cat = (idx + 1).toString().padStart(2, '0')
                for (n in 1..count) {
                    add("AND-$cat-${n.toString().padStart(3, '0')}")
                }
            }
        }
        val missingIds = expectedIds - uniqueIds

        val bespokeCount = issues.count { it.hasIssueSpecificNote }
        val inheritedIds = issues.filter { !it.hasIssueSpecificNote }.map { it.problemId }
        val inheritedCount = inheritedIds.size
        val incompleteCount = issues.count {
            it.solutions.isEmpty() || it.possibleCauses.isEmpty() || it.diagnosticQuestions.isEmpty()
        }

        val overlapIssues = issues.filter { it.relatedOverlaps.isNotEmpty() }
        val seenPairs = mutableSetOf<String>()
        val overlapGroups = mutableListOf<OverlapAuditGroup>()
        overlapIssues.forEach { iss ->
            val groupKey = (listOf(iss.problemId) + iss.relatedOverlaps).sorted().joinToString("|")
            if (seenPairs.add(groupKey)) {
                overlapGroups.add(
                    OverlapAuditGroup(
                        primaryId = iss.problemId,
                        primaryTitle = iss.canonicalProblemName,
                        overlappingIds = iss.relatedOverlaps,
                        reason = "Cross-category symptom overlap retained intentionally without auto-deletion"
                    )
                )
            }
        }

        val statusBreakdown = issues.groupingBy { it.verificationStatus }.eachCount()
        val catCounts = categories.associate { cat ->
            "${cat.categoryNumber}. ${cat.categoryName}" to issues.count { it.categoryNumber == cat.categoryNumber }
        }

        val compatFamilies = DeviceCompatibilityEngine.getAllRegisteredFamilies()
        val androidVersions = listOf(
            "Android 7–9 (API 24–28)",
            "Android 10–12L (API 29–32)",
            "Android 13 (API 33)",
            "Android 14 (API 34)",
            "Android 15 (API 35)",
            "Android 16 (API 36)",
            "Android 17 (API 37 Stable)",
            "Android 18+ (API 38+ Future Placeholder)"
        )

        return KnowledgeCoverageAudit(
            totalCategories = categories.size,
            totalIssues = issues.size,
            uniqueProblemIds = uniqueIds.size,
            missingFromExpected466Count = missingIds.size,
            issuesWithBespokeNotes = bespokeCount,
            issuesUsingCategoryWorkflow = inheritedCount,
            incompleteFieldIssuesCount = incompleteCount,
            overlappingIssuesCount = overlapIssues.size,
            overlapGroups = overlapGroups,
            verificationStatusBreakdown = statusBreakdown,
            categoryIssueCounts = catCounts,
            totalOemFamilies = compatFamilies.size,
            androidVersionsSupported = androidVersions,
            customOrUpdatedIssuesCount = KnowledgeRepository.getCustomUpdatedCount(),
            categoryWorkflowIssueIds = inheritedIds
        )
    }
}
