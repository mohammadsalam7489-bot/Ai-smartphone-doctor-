package com.example.data.knowledge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ParsedKnowledgeBundle(
    val schemaVersion: String,
    val sourcePdf: String,
    val categories: List<KnowledgeCategoryRecord>,
    val issues: List<KnowledgeIssueRecord>
)

/**
 * Loads and parses the structured PDF 1 Knowledge Base (29 categories, 466 issues)
 * from `assets/knowledge_base_v2.json` or external JSON/text updates.
 */
object KnowledgeAssetLoader {

    fun loadBundledKnowledgeBase(context: Context): ParsedKnowledgeBundle {
        val jsonText = context.assets.open("knowledge_base_v2.json")
            .bufferedReader()
            .use { it.readText() }
        return parseKnowledgeBundleJson(jsonText)
    }

    fun parseKnowledgeBundleJson(jsonText: String): ParsedKnowledgeBundle {
        val trimmed = jsonText.trim()
        // Support either full bundle object or a single issue JSON or array of issues
        if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            val issues = (0 until arr.length()).mapNotNull { idx ->
                runCatching { parseIssueObject(arr.getJSONObject(idx)) }.getOrNull()
            }
            return ParsedKnowledgeBundle("custom-array", "Imported JSON", emptyList(), issues)
        }

        val root = JSONObject(trimmed)
        if (root.has("problem_id") && !root.has("issues")) {
            val single = parseIssueObject(root)
            return ParsedKnowledgeBundle("custom-single", single.sourcePdf, emptyList(), listOf(single))
        }

        val schemaVersion = root.optString("schema_version", "2.0.0")
        val sourcePdf = root.optString("source_pdf", "Android_AI_Repair_Training_Knowledge_Base_v2.pdf")

        val catsJson = root.optJSONArray("categories") ?: JSONArray()
        val categories = (0 until catsJson.length()).map { i ->
            val obj = catsJson.getJSONObject(i)
            KnowledgeCategoryRecord(
                categoryId = obj.getString("categoryId"),
                categoryNumber = obj.optInt("categoryNumber", i + 1),
                categoryName = obj.getString("categoryName"),
                pageStart = obj.optInt("pageStart", 1),
                pageEnd = obj.optInt("pageEnd", 1),
                sourceChecklistCount = obj.optInt("sourceChecklistCount", 0),
                startByAsking = obj.optString("startByAsking"),
                possibleCauseFamilies = jsonArrayToStrings(obj.optJSONArray("possibleCauseFamilies")),
                readOnlyChecksAndFirstLinePath = obj.optString("readOnlyChecksAndFirstLinePath"),
                automationBoundary = obj.optString("automationBoundary"),
                verification = obj.optString("verification"),
                stopEscalate = obj.optString("stopEscalate")
            )
        }

        val issuesJson = root.optJSONArray("issues") ?: JSONArray()
        val issues = (0 until issuesJson.length()).map { i ->
            parseIssueObject(issuesJson.getJSONObject(i), sourcePdf)
        }

        return ParsedKnowledgeBundle(
            schemaVersion = schemaVersion,
            sourcePdf = sourcePdf,
            categories = categories,
            issues = issues
        )
    }

    fun parseIssueObject(
        obj: JSONObject,
        defaultSourcePdf: String = "Android_AI_Repair_Training_Knowledge_Base_v2.pdf"
    ): KnowledgeIssueRecord {
        val pid = obj.getString("problem_id")
        val catNum = obj.optInt("category_number", pid.split("-").getOrNull(1)?.toIntOrNull() ?: 0)
        val revArr = obj.optJSONArray("revision_history")
        val revisions = mutableListOf<SolutionRevision>()
        if (revArr != null) {
            for (i in 0 until revArr.length()) {
                val r = revArr.optJSONObject(i) ?: continue
                revisions.add(
                    SolutionRevision(
                        version = r.optInt("version", 1),
                        updatedAt = r.optLong("updated_at", 0L),
                        previousSolutions = jsonArrayToStrings(r.optJSONArray("previous_solutions")),
                        note = r.optString("note", "")
                    )
                )
            }
        }
        return KnowledgeIssueRecord(
            problemId = pid,
            categoryId = obj.optString("category_id", "CAT-${catNum.toString().padStart(2, '0')}"),
            categoryNumber = catNum,
            categoryName = obj.optString("category_name", obj.optString("category", "General Android")),
            canonicalProblemName = obj.optString("canonical_problem_name", obj.optString("problem", pid)),
            userPhrases = jsonArrayToStrings(obj.optJSONArray("user_phrases")),
            severity = obj.optString("severity", "P2"),
            riskTier = obj.optString("risk_tier", "R1"),
            possibleCauses = jsonArrayToStrings(obj.optJSONArray("possible_causes")),
            diagnosticQuestions = jsonArrayToStrings(obj.optJSONArray("diagnostic_questions")),
            diagnosticRules = jsonArrayToStrings(obj.optJSONArray("diagnostic_rules")),
            solutions = jsonArrayToStrings(obj.optJSONArray("solutions")),
            alternativeSolutions = jsonArrayToStrings(obj.optJSONArray("alternative_solutions")),
            automaticActionIds = jsonArrayToStrings(obj.optJSONArray("automatic_action_ids")),
            notAutomatable = jsonArrayToStrings(obj.optJSONArray("not_automatable")),
            requiredPermissions = jsonArrayToStrings(obj.optJSONArray("required_permissions")),
            androidRestrictions = jsonArrayToStrings(obj.optJSONArray("android_restrictions")),
            oemSettingsKey = obj.optString("oem_settings_key", "settings_search"),
            oemCompatibility = jsonArrayToStrings(obj.optJSONArray("oem_compatibility")),
            verification = jsonArrayToStrings(obj.optJSONArray("verification")),
            hardwareIndicators = jsonArrayToStrings(obj.optJSONArray("hardware_indicators")),
            escalation = jsonArrayToStrings(obj.optJSONArray("escalation")),
            issueSpecificNotes = jsonArrayToStrings(obj.optJSONArray("issue_specific_notes")),
            hasIssueSpecificNote = obj.optBoolean("has_issue_specific_note", false),
            sourcePdf = obj.optString("source_pdf", defaultSourcePdf),
            sourcePage = obj.optInt("source_page", 1),
            verificationStatus = obj.optString("verification_status", "DOC-REFERENCED"),
            relatedOverlaps = jsonArrayToStrings(obj.optJSONArray("related_overlaps")),
            version = obj.optInt("version", 1),
            revisionHistory = revisions
        )
    }

    fun issueToJsonString(issue: KnowledgeIssueRecord): String {
        val obj = JSONObject().apply {
            put("problem_id", issue.problemId)
            put("category_id", issue.categoryId)
            put("category_number", issue.categoryNumber)
            put("category_name", issue.categoryName)
            put("canonical_problem_name", issue.canonicalProblemName)
            put("user_phrases", JSONArray(issue.userPhrases))
            put("severity", issue.severity)
            put("risk_tier", issue.riskTier)
            put("possible_causes", JSONArray(issue.possibleCauses))
            put("diagnostic_questions", JSONArray(issue.diagnosticQuestions))
            put("diagnostic_rules", JSONArray(issue.diagnosticRules))
            put("solutions", JSONArray(issue.solutions))
            put("alternative_solutions", JSONArray(issue.alternativeSolutions))
            put("automatic_action_ids", JSONArray(issue.automaticActionIds))
            put("not_automatable", JSONArray(issue.notAutomatable))
            put("required_permissions", JSONArray(issue.requiredPermissions))
            put("android_restrictions", JSONArray(issue.androidRestrictions))
            put("oem_settings_key", issue.oemSettingsKey)
            put("oem_compatibility", JSONArray(issue.oemCompatibility))
            put("verification", JSONArray(issue.verification))
            put("hardware_indicators", JSONArray(issue.hardwareIndicators))
            put("escalation", JSONArray(issue.escalation))
            put("issue_specific_notes", JSONArray(issue.issueSpecificNotes))
            put("has_issue_specific_note", issue.hasIssueSpecificNote)
            put("source_pdf", issue.sourcePdf)
            put("source_page", issue.sourcePage)
            put("verification_status", issue.verificationStatus)
            put("related_overlaps", JSONArray(issue.relatedOverlaps))
            put("version", issue.version)
            val revArr = JSONArray()
            issue.revisionHistory.forEach { rev ->
                revArr.put(
                    JSONObject().apply {
                        put("version", rev.version)
                        put("updated_at", rev.updatedAt)
                        put("previous_solutions", JSONArray(rev.previousSolutions))
                        put("note", rev.note)
                    }
                )
            }
            put("revision_history", revArr)
        }
        return obj.toString()
    }

    private fun jsonArrayToStrings(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { idx ->
            arr.optString(idx)?.takeIf { it.isNotBlank() }
        }
    }
}
