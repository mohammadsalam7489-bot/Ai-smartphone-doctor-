package com.example.data.knowledge

import android.content.Context
import com.example.data.compatibility.DeviceCompatibilityEngine
import com.example.data.compatibility.OemFamilyRecord
import com.example.data.local.AppDatabase
import com.example.data.local.CompatibilityProfileEntity
import com.example.data.local.KnowledgeDao
import com.example.data.local.KnowledgeImportAuditEntity
import com.example.data.local.KnowledgeIssueEntity
import com.example.data.model.CoverageTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Unified Persistent Knowledge Base & Compatibility Repository.
 * Seeds Room SQLite from `assets/knowledge_base_v2.json` (29 categories, 466 issues)
 * and `assets/device_compatibility_v3.json` (30 OEM families, Android 7–18+),
 * while preserving any user-imported updates and version revisions across restarts.
 */
object KnowledgeRepository {

    private val mutex = Mutex()
    @Volatile
    private var memoryLoaded = false
    @Volatile
    private var roomSeeded = false

    private val _categories = MutableStateFlow<List<KnowledgeCategoryRecord>>(emptyList())
    val categories: StateFlow<List<KnowledgeCategoryRecord>> = _categories.asStateFlow()

    private val _issues = MutableStateFlow<List<KnowledgeIssueRecord>>(emptyList())
    val issues: StateFlow<List<KnowledgeIssueRecord>> = _issues.asStateFlow()

    private val _importAudits = MutableStateFlow<List<KnowledgeImportAuditEntity>>(emptyList())
    val importAudits: StateFlow<List<KnowledgeImportAuditEntity>> = _importAudits.asStateFlow()

    private var customUpdatedCount = 0

    private const val BUNDLED_KB_REVISION = 2

    fun ensureLoadedSynchronously(context: Context) {
        if (memoryLoaded && _issues.value.isNotEmpty()) return
        synchronized(this) {
            if (memoryLoaded && _issues.value.isNotEmpty()) return
            DeviceCompatibilityEngine.ensureLoaded(context)
            val bundle = KnowledgeAssetLoader.loadBundledKnowledgeBase(context)
            _categories.value = bundle.categories
            _issues.value = bundle.issues
            memoryLoaded = true
        }
    }

    suspend fun initializeWithRoom(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoadedSynchronously(context)
            if (roomSeeded) return@withLock

            val prefs = context.getSharedPreferences("kb_meta", Context.MODE_PRIVATE)
            val storedRevision = prefs.getInt("bundled_kb_revision", 0)
            val dao = AppDatabase.getInstance(context).knowledgeDao()
            val existingCount = dao.getIssueCount()
            if (existingCount == 0) {
                val now = System.currentTimeMillis()
                val entities = _issues.value.map { toEntity(it, isUserUpdated = false, updatedAt = now) }
                dao.insertIssuesIfAbsent(entities)
                prefs.edit().putInt("bundled_kb_revision", BUNDLED_KB_REVISION).apply()
            } else {
                val persisted = dao.getAllIssues()
                if (storedRevision < BUNDLED_KB_REVISION) {
                    val userUpdatedIds = persisted.filter { it.isUserUpdated }.map { it.problemId }.toSet()
                    val now = System.currentTimeMillis()
                    val refreshed = _issues.value
                        .filter { it.problemId !in userUpdatedIds }
                        .map { toEntity(it, isUserUpdated = false, updatedAt = now) }
                    if (refreshed.isNotEmpty()) {
                        dao.upsertIssues(refreshed)
                    }
                    prefs.edit().putInt("bundled_kb_revision", BUNDLED_KB_REVISION).apply()
                }
                val reloaded = dao.getAllIssues()
                if (reloaded.isNotEmpty()) {
                    val parsed = reloaded.mapNotNull { entity ->
                        runCatching {
                            KnowledgeAssetLoader.parseIssueObject(JSONObject(entity.payloadJson))
                        }.getOrNull()
                    }
                    if (parsed.isNotEmpty()) {
                        _issues.value = parsed.sortedWith(
                            compareBy<KnowledgeIssueRecord> { it.categoryNumber }.thenBy { it.problemId }
                        )
                        customUpdatedCount = reloaded.count { it.isUserUpdated }
                    }
                }
            }

            // Seed Compatibility Profiles in Room idempotently
            val compatEntities = DeviceCompatibilityEngine.getAllRegisteredFamilies().map { fam ->
                toCompatEntity(fam, System.currentTimeMillis())
            }
            dao.insertProfilesIfAbsent(compatEntities)

            // Apply any persisted custom compatibility overrides back into engine
            dao.getAllCompatibilityProfiles().forEach { entity ->
                runCatching {
                    val obj = JSONObject(entity.payloadJson)
                    val fam = parseOemFamilyJson(obj)
                    DeviceCompatibilityEngine.registerOrUpdateCustomProfile(fam)
                }
            }

            roomSeeded = true
        }
    }

    fun getAllCategories(): List<KnowledgeCategoryRecord> = _categories.value

    fun getAllIssues(): List<KnowledgeIssueRecord> = _issues.value

    fun findIssueById(problemId: String?): KnowledgeIssueRecord? {
        if (problemId.isNullOrBlank()) return null
        return _issues.value.find { it.problemId.equals(problemId.trim(), ignoreCase = true) }
    }

    fun findCategoryByNumber(categoryNumber: Int): KnowledgeCategoryRecord? {
        return _categories.value.find { it.categoryNumber == categoryNumber }
    }

    fun getCustomUpdatedCount(): Int = customUpdatedCount

    suspend fun saveUpdatedOrNewIssues(
        context: Context,
        recordsToSave: List<Pair<KnowledgeIssueRecord, Boolean>>,
        newProfiles: List<OemFamilyRecord>,
        auditEntry: KnowledgeImportAuditEntity
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureLoadedSynchronously(context)
            val dao: KnowledgeDao = AppDatabase.getInstance(context).knowledgeDao()
            val now = System.currentTimeMillis()

            if (recordsToSave.isNotEmpty()) {
                val entities = recordsToSave.map { (rec, isUpdated) ->
                    toEntity(rec, isUserUpdated = isUpdated, updatedAt = now)
                }
                dao.upsertIssues(entities)

                val map = _issues.value.associateBy { it.problemId }.toMutableMap()
                recordsToSave.forEach { (rec, _) -> map[rec.problemId] = rec }
                _issues.value = map.values.sortedWith(
                    compareBy<KnowledgeIssueRecord> { it.categoryNumber }.thenBy { it.problemId }
                )
                customUpdatedCount = dao.getAllIssues().count { it.isUserUpdated }
            }

            newProfiles.forEach { fam ->
                DeviceCompatibilityEngine.registerOrUpdateCustomProfile(fam)
                dao.upsertProfile(toCompatEntity(fam, now))
            }

            dao.insertImportAudit(auditEntry)
            _importAudits.value = listOf(auditEntry) + _importAudits.value
        }
    }

    /**
     * Used cleanly in unit tests or forced re-seed checks without leaking global singleton state.
     */
    internal fun resetMemoryCacheForTests() {
        memoryLoaded = false
        roomSeeded = false
        customUpdatedCount = 0
        _categories.value = emptyList()
        _issues.value = emptyList()
    }

    private fun toEntity(
        issue: KnowledgeIssueRecord,
        isUserUpdated: Boolean,
        updatedAt: Long
    ): KnowledgeIssueEntity {
        val searchText = buildString {
            append(issue.problemId).append(' ')
            append(issue.canonicalProblemName).append(' ')
            append(issue.categoryName).append(' ')
            append(issue.userPhrases.joinToString(" ")).append(' ')
            append(issue.possibleCauses.joinToString(" "))
        }.lowercase()

        return KnowledgeIssueEntity(
            problemId = issue.problemId,
            categoryId = issue.categoryId,
            categoryNumber = issue.categoryNumber,
            categoryName = issue.categoryName,
            canonicalProblemName = issue.canonicalProblemName,
            searchableText = searchText,
            severity = issue.severity,
            riskTier = issue.riskTier,
            verificationStatus = issue.verificationStatus,
            sourcePdf = issue.sourcePdf,
            sourcePage = issue.sourcePage,
            version = issue.version,
            isUserUpdated = isUserUpdated,
            payloadJson = KnowledgeAssetLoader.issueToJsonString(issue),
            updatedAt = updatedAt
        )
    }

    private fun toCompatEntity(fam: OemFamilyRecord, updatedAt: Long): CompatibilityProfileEntity {
        val obj = JSONObject().apply {
            put("profile_id", fam.profileId)
            put("manufacturer", fam.manufacturer)
            put("aliases", JSONArray(fam.aliases))
            put("software_family", fam.softwareFamily)
            put("profile_notes", fam.profileNotes)
            put("gms_available", fam.gmsAvailable)
            put("form_factors", JSONArray(fam.formFactors))
            put("coverage_tier", fam.coverageTier.name)
            put("verification_status", fam.verificationStatus)
            put("is_path_verified", fam.isPathVerified)
            put("settings_paths", JSONObject(fam.settingsPaths))
        }
        return CompatibilityProfileEntity(
            profileId = fam.profileId,
            manufacturer = fam.manufacturer,
            softwareFamily = fam.softwareFamily,
            coverageTier = fam.coverageTier.name,
            verificationStatus = fam.verificationStatus,
            isPathVerified = fam.isPathVerified,
            payloadJson = obj.toString(),
            updatedAt = updatedAt
        )
    }

    internal fun parseOemFamilyJson(obj: JSONObject): OemFamilyRecord {
        val aliasesArr = obj.optJSONArray("aliases")
        val aliases = mutableListOf<String>()
        if (aliasesArr != null) {
            for (i in 0 until aliasesArr.length()) aliases.add(aliasesArr.getString(i))
        }
        val ffArr = obj.optJSONArray("form_factors")
        val formFactors = mutableListOf<String>()
        if (ffArr != null) {
            for (i in 0 until ffArr.length()) formFactors.add(ffArr.getString(i))
        }
        val pathsObj = obj.optJSONObject("settings_paths")
        val pathsMap = mutableMapOf<String, String>()
        if (pathsObj != null) {
            val keys = pathsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                pathsMap[k] = pathsObj.getString(k)
            }
        }
        val tier = runCatching {
            CoverageTier.valueOf(obj.optString("coverage_tier", "D_UNKNOWN"))
        }.getOrDefault(CoverageTier.D_UNKNOWN)

        return OemFamilyRecord(
            profileId = obj.getString("profile_id"),
            manufacturer = obj.getString("manufacturer"),
            aliases = aliases.ifEmpty { listOf(obj.getString("manufacturer").lowercase()) },
            softwareFamily = obj.optString("software_family", "Android"),
            profileNotes = obj.optString("profile_notes", ""),
            gmsAvailable = obj.optBoolean("gms_available", true),
            formFactors = formFactors.ifEmpty { listOf("phone") },
            coverageTier = tier,
            verificationStatus = obj.optString("verification_status", "doc_referenced"),
            isPathVerified = obj.optBoolean("is_path_verified", false),
            settingsPaths = pathsMap
        )
    }
}
