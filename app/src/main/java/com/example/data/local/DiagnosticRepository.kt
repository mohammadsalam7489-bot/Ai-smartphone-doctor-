package com.example.data.local

import com.example.data.model.DiagnosticHistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DiagnosticRepository(private val dao: DiagnosticHistoryDao) {

    val historyItems: Flow<List<DiagnosticHistoryItem>> = dao.observeAll().map { entities ->
        entities.map { entity ->
            DiagnosticHistoryItem(
                id = entity.id,
                timestamp = entity.timestamp,
                problemTitle = entity.problemTitle,
                category = entity.category,
                diagnosisSummary = entity.diagnosisSummary,
                actionExecuted = entity.actionExecuted,
                verificationStatus = entity.verificationStatus,
                playbookId = entity.playbookId,
                capabilityUsed = entity.capabilityUsed,
                beforeSnapshot = entity.beforeSnapshot,
                afterSnapshot = entity.afterSnapshot,
                aiModeUsed = entity.aiModeUsed
            )
        }
    }

    suspend fun saveHistoryItem(item: DiagnosticHistoryItem) {
        dao.insert(
            DiagnosticHistoryEntity(
                id = item.id,
                timestamp = item.timestamp,
                problemTitle = item.problemTitle,
                category = item.category,
                diagnosisSummary = item.diagnosisSummary,
                actionExecuted = item.actionExecuted,
                verificationStatus = item.verificationStatus,
                playbookId = item.playbookId,
                capabilityUsed = item.capabilityUsed,
                beforeSnapshot = item.beforeSnapshot,
                afterSnapshot = item.afterSnapshot,
                aiModeUsed = item.aiModeUsed
            )
        )
    }

    suspend fun deleteItem(id: String) {
        dao.deleteById(id)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }
}
