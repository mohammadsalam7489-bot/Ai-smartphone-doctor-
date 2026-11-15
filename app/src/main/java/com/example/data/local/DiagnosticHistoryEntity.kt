package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diagnostic_history")
data class DiagnosticHistoryEntity(
    @PrimaryKey val id: String,
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
