package com.example.data.knowledge

import android.content.Context
import android.net.Uri
import com.example.data.compatibility.DeviceCompatibilityEngine
import com.example.data.compatibility.OemFamilyRecord
import com.example.data.local.KnowledgeImportAuditEntity
import org.json.JSONObject
import java.util.UUID

/**
 * Expandable Knowledge & Compatibility Update Pipeline (KB v2.0 / Manual v3.0 Section 13).
 * Ingests updated JSON bundles or structured plain-text records:
 *  - Compares against existing records by problem_id / profile_id
 *  - Adds new entries
 *  - Creates versioned revisions when existing solutions change
 *  - Flags conflicts (especially against DEVICE-TESTED / LAB-TESTED records)
 *  - Never deletes prior verified records without explicit human review
 */
object KnowledgeUpdateManager {

    fun importFromUri(context: Context, uri: Uri): KnowledgeImportResult {
        val rawText = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull() ?: return KnowledgeImportResult(
            success = false,
            summaryMessage = "Could not read selected file."
        )
        return runImportSync(context, rawText, uri.lastPathSegment ?: "imported_bundle")
    }

    suspend fun ingestUpdateContent(
        context: Context,
        rawContent: String,
        sourceName: String = "manual_update"
    ): KnowledgeImportResult {
        KnowledgeRepository.initializeWithRoom(context)
        return processIngestion(context, rawContent, sourceName)
    }

    fun runImportSync(
        context: Context,
        rawContent: String,
        sourceName: String = "manual_update"
    ): KnowledgeImportResult {
        return kotlinx.coroutines.runBlocking {
            ingestUpdateContent(context, rawContent, sourceName)
        }
    }

    private suspend fun processIngestion(
        context: Context,
        rawContent: String,
        sourceName: String
    ): KnowledgeImportResult {
        KnowledgeRepository.ensureLoadedSynchronously(context)
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) {
            return KnowledgeImportResult(false, summaryMessage = "Empty file — nothing imported.")
        }

        val incomingIssues = mutableListOf<KnowledgeIssueRecord>()
        val incomingProfiles = mutableListOf<OemFamilyRecord>()

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val bundle = runCatching { KnowledgeAssetLoader.parseKnowledgeBundleJson(trimmed) }
                .getOrNull()
            if (bundle != null) {
                incomingIssues.addAll(bundle.issues)
            }
            if (trimmed.startsWith("{")) {
                runCatching {
                    val root = JSONObject(trimmed)
                    if (root.has("oem_families")) {
                        val arr = root.getJSONArray("oem_families")
                        for (i in 0 until arr.length()) {
                            incomingProfiles.add(KnowledgeRepository.parseOemFamilyJson(arr.getJSONObject(i)))
                        }
                    } else if (root.has("profile_id") && root.has("manufacturer")) {
                        incomingProfiles.add(KnowledgeRepository.parseOemFamilyJson(root))
                    }
                }
            }
        } else {
            // Structured plain-text / PDF-extracted fallback parser (`AND-XX-YYY — Title`)
            incomingIssues.addAll(parseStructuredTextIssues(trimmed, sourceName))
        }

        if (incomingIssues.isEmpty() && incomingProfiles.isEmpty()) {
            return KnowledgeImportResult(
                success = false,
                summaryMessage = "No valid problem_id (AND-XX-YYY) or OEM profile records found in '$sourceName'."
            )
        }

        val existingMap = KnowledgeRepository.getAllIssues().associateBy { it.problemId }
        val existingProfilesMap = DeviceCompatibilityEngine.getAllRegisteredFamilies().associateBy { it.profileId }

        val toSave = mutableListOf<Pair<KnowledgeIssueRecord, Boolean>>()
        val addedIds = mutableListOf<String>()
        val updatedIds = mutableListOf<String>()
        val conflicts = mutableListOf<UpdateConflictItem>()
        var unchanged = 0
        var preservedVerified = 0

        val now = System.currentTimeMillis()
        for (incoming in incomingIssues) {
            val existing = existingMap[incoming.problemId]
            if (existing == null) {
                toSave.add(incoming to true)
                addedIds.add(incoming.problemId)
            } else {
                val solutionsChanged = incoming.solutions.isNotEmpty() && incoming.solutions != existing.solutions
                val causesChanged = incoming.possibleCauses.isNotEmpty() && incoming.possibleCauses != existing.possibleCauses
                val isHighVerificationExisting = existing.verificationStatus in listOf("LAB-TESTED", "DEVICE-TESTED")

                if (!solutionsChanged && !causesChanged && incoming.canonicalProblemName == existing.canonicalProblemName) {
                    unchanged++
                    if (isHighVerificationExisting) preservedVerified++
                } else if (isHighVerificationExisting && solutionsChanged) {
                    // Conflict protection: never overwrite lab/device-tested steps without flagging conflict
                    conflicts.add(
                        UpdateConflictItem(
                            recordId = existing.problemId,
                            reason = "Incoming solutions differ from existing ${existing.verificationStatus} record; kept verified record primary and appended revision note.",
                            existingStatus = existing.verificationStatus
                        )
                    )
                    preservedVerified++
                    val revision = SolutionRevision(
                        version = existing.version + 1,
                        updatedAt = now,
                        previousSolutions = incoming.solutions,
                        note = "Pending review candidate from $sourceName (conflict with ${existing.verificationStatus})"
                    )
                    val flaggedRecord = existing.copy(
                        revisionHistory = existing.revisionHistory + revision
                    )
                    toSave.add(flaggedRecord to true)
                } else {
                    // Valid update: bump version and preserve previous solutions in revisionHistory
                    if (solutionsChanged) {
                        conflicts.add(
                            UpdateConflictItem(
                                recordId = existing.problemId,
                                reason = "Solution steps updated from '$sourceName' (v${existing.version} -> v${existing.version + 1}; prior steps archived).",
                                existingStatus = existing.verificationStatus
                            )
                        )
                    }
                    val archivedRevision = SolutionRevision(
                        version = existing.version,
                        updatedAt = now,
                        previousSolutions = existing.solutions,
                        note = "Archived before update from $sourceName"
                    )
                    val merged = existing.copy(
                        canonicalProblemName = incoming.canonicalProblemName.ifBlank { existing.canonicalProblemName },
                        userPhrases = (existing.userPhrases + incoming.userPhrases).distinct(),
                        possibleCauses = incoming.possibleCauses.ifEmpty { existing.possibleCauses },
                        diagnosticQuestions = incoming.diagnosticQuestions.ifEmpty { existing.diagnosticQuestions },
                        solutions = incoming.solutions.ifEmpty { existing.solutions },
                        alternativeSolutions = (existing.alternativeSolutions + incoming.alternativeSolutions).distinct(),
                        verification = incoming.verification.ifEmpty { existing.verification },
                        escalation = incoming.escalation.ifEmpty { existing.escalation },
                        verificationStatus = incoming.verificationStatus.ifBlank { existing.verificationStatus },
                        version = existing.version + 1,
                        revisionHistory = existing.revisionHistory + archivedRevision
                    )
                    toSave.add(merged to true)
                    updatedIds.add(existing.problemId)
                }
            }
        }

        val addedProfiles = mutableListOf<String>()
        val updatedProfiles = mutableListOf<String>()
        incomingProfiles.forEach { prof ->
            if (existingProfilesMap.containsKey(prof.profileId)) {
                updatedProfiles.add(prof.profileId)
            } else {
                addedProfiles.add(prof.profileId)
            }
        }

        val summary = buildString {
            append("Imported '$sourceName': ")
            append("${addedIds.size} new issue(s), ${updatedIds.size} updated (versioned), ")
            append("${addedProfiles.size + updatedProfiles.size} OEM profile(s), ")
            append("${conflicts.size} conflict/diff notice(s), $unchanged unchanged. Verified records preserved.")
        }

        val audit = KnowledgeImportAuditEntity(
            id = UUID.randomUUID().toString(),
            timestamp = now,
            sourceName = sourceName,
            addedCount = addedIds.size + addedProfiles.size,
            updatedCount = updatedIds.size + updatedProfiles.size,
            conflictCount = conflicts.size,
            summary = summary
        )

        KnowledgeRepository.saveUpdatedOrNewIssues(
            context = context,
            recordsToSave = toSave,
            newProfiles = incomingProfiles,
            auditEntry = audit
        )

        return KnowledgeImportResult(
            success = true,
            addedIssueIds = addedIds,
            updatedIssueIds = updatedIds,
            addedProfileIds = addedProfiles,
            updatedProfileIds = updatedProfiles,
            unchangedCount = unchanged,
            preservedVerifiedCount = preservedVerified,
            conflicts = conflicts,
            summaryMessage = summary
        )
    }

    private fun parseStructuredTextIssues(text: String, sourceName: String): List<KnowledgeIssueRecord> {
        val headerRegex = Regex("""(AND-\d{2}-\d{3})\s*[—–-]\s*([^\n]+)""")
        val matches = headerRegex.findAll(text).toList()
        if (matches.isEmpty()) return emptyList()

        return matches.mapIndexed { idx, m ->
            val pid = m.groupValues[1].trim()
            val title = m.groupValues[2].trim().trimEnd('.')
            val start = m.range.last + 1
            val end = if (idx + 1 < matches.size) matches[idx + 1].range.first else text.length
            val block = text.substring(start, end)

            val catNum = pid.split("-").getOrNull(1)?.toIntOrNull() ?: 1
            val causes = Regex("""causes?:\s*([^\n]+)""", RegexOption.IGNORE_CASE)
                .find(block)?.groupValues?.get(1)?.split(",", ";")?.map { it.trim() }?.filter { it.isNotBlank() }
                .orEmpty()
            val steps = Regex("""(?:workflow|solutions?|steps?):\s*([^\n]+)""", RegexOption.IGNORE_CASE)
                .find(block)?.groupValues?.get(1)?.split(Regex("""\s*\d+\)\s*"""))?.map { it.trim() }?.filter { it.isNotBlank() }
                .orEmpty()

            KnowledgeIssueRecord(
                problemId = pid,
                categoryId = "CAT-${catNum.toString().padStart(2, '0')}",
                categoryNumber = catNum,
                categoryName = "Imported Category $catNum",
                canonicalProblemName = title,
                userPhrases = listOf(title),
                severity = "P2",
                riskTier = "R1",
                possibleCauses = causes.ifEmpty { listOf("Check telemetry and app/system logs") },
                diagnosticQuestions = listOf("When did '$title' start and does it happen across multiple apps?"),
                diagnosticRules = emptyList(),
                solutions = steps.ifEmpty { listOf("Review official Settings safely for $title and retest original task.") },
                alternativeSolutions = emptyList(),
                automaticActionIds = listOf("repair_settings_search"),
                notAutomatable = listOf("Imported text workflow; OS sandbox limits apply"),
                requiredPermissions = emptyList(),
                androidRestrictions = listOf("User-confirmed Settings navigation only"),
                oemSettingsKey = "settings_search",
                oemCompatibility = listOf("All supported Android profiles"),
                verification = listOf("Repeat original task and verify outcome"),
                hardwareIndicators = listOf("Physical inspection required if unresolved in Safe Mode"),
                escalation = listOf("Escalate to OEM authorized service if unresolved"),
                issueSpecificNotes = emptyList(),
                hasIssueSpecificNote = false,
                sourcePdf = sourceName,
                sourcePage = 1,
                verificationStatus = "DOC-REFERENCED"
            )
        }
    }
}
