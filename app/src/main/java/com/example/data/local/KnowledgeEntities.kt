package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "knowledge_issues")
data class KnowledgeIssueEntity(
    @PrimaryKey val problemId: String,
    val categoryId: String,
    val categoryNumber: Int,
    val categoryName: String,
    val canonicalProblemName: String,
    val searchableText: String,
    val severity: String,
    val riskTier: String,
    val verificationStatus: String,
    val sourcePdf: String,
    val sourcePage: Int,
    val version: Int,
    val isUserUpdated: Boolean,
    val payloadJson: String,
    val updatedAt: Long
)

@Entity(tableName = "compatibility_profiles")
data class CompatibilityProfileEntity(
    @PrimaryKey val profileId: String,
    val manufacturer: String,
    val softwareFamily: String,
    val coverageTier: String,
    val verificationStatus: String,
    val isPathVerified: Boolean,
    val payloadJson: String,
    val updatedAt: Long
)

@Entity(tableName = "kb_import_audit")
data class KnowledgeImportAuditEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val sourceName: String,
    val addedCount: Int,
    val updatedCount: Int,
    val conflictCount: Int,
    val summary: String
)
