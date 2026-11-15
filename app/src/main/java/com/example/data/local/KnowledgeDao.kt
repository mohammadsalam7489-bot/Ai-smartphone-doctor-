package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgeDao {

    @Query("SELECT COUNT(*) FROM knowledge_issues")
    suspend fun getIssueCount(): Int

    @Query("SELECT * FROM knowledge_issues ORDER BY categoryNumber ASC, problemId ASC")
    suspend fun getAllIssues(): List<KnowledgeIssueEntity>

    @Query("SELECT * FROM knowledge_issues ORDER BY categoryNumber ASC, problemId ASC")
    fun observeAllIssues(): Flow<List<KnowledgeIssueEntity>>

    @Query("SELECT * FROM knowledge_issues WHERE problemId = :problemId LIMIT 1")
    suspend fun getIssueById(problemId: String): KnowledgeIssueEntity?

    @Query("SELECT * FROM knowledge_issues WHERE searchableText LIKE '%' || :query || '%' ORDER BY problemId ASC LIMIT :limit")
    suspend fun searchIssuesByText(query: String, limit: Int = 25): List<KnowledgeIssueEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIssuesIfAbsent(items: List<KnowledgeIssueEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIssue(item: KnowledgeIssueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIssues(items: List<KnowledgeIssueEntity>)

    @Query("SELECT * FROM compatibility_profiles ORDER BY manufacturer ASC")
    suspend fun getAllCompatibilityProfiles(): List<CompatibilityProfileEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProfilesIfAbsent(items: List<CompatibilityProfileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(item: CompatibilityProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportAudit(item: KnowledgeImportAuditEntity)

    @Query("SELECT * FROM kb_import_audit ORDER BY timestamp DESC")
    fun observeImportAudits(): Flow<List<KnowledgeImportAuditEntity>>
}
