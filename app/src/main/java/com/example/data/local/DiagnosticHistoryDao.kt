package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosticHistoryDao {
    @Query("SELECT * FROM diagnostic_history ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<DiagnosticHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DiagnosticHistoryEntity)

    @Query("DELETE FROM diagnostic_history WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM diagnostic_history")
    suspend fun clearAll()
}
