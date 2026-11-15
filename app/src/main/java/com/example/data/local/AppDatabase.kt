package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DiagnosticHistoryEntity::class,
        KnowledgeIssueEntity::class,
        CompatibilityProfileEntity::class,
        KnowledgeImportAuditEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun diagnosticHistoryDao(): DiagnosticHistoryDao
    abstract fun knowledgeDao(): KnowledgeDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `knowledge_issues` (
                        `problemId` TEXT NOT NULL,
                        `categoryId` TEXT NOT NULL,
                        `categoryNumber` INTEGER NOT NULL,
                        `categoryName` TEXT NOT NULL,
                        `canonicalProblemName` TEXT NOT NULL,
                        `searchableText` TEXT NOT NULL,
                        `severity` TEXT NOT NULL,
                        `riskTier` TEXT NOT NULL,
                        `verificationStatus` TEXT NOT NULL,
                        `sourcePdf` TEXT NOT NULL,
                        `sourcePage` INTEGER NOT NULL,
                        `version` INTEGER NOT NULL,
                        `isUserUpdated` INTEGER NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`problemId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `compatibility_profiles` (
                        `profileId` TEXT NOT NULL,
                        `manufacturer` TEXT NOT NULL,
                        `softwareFamily` TEXT NOT NULL,
                        `coverageTier` TEXT NOT NULL,
                        `verificationStatus` TEXT NOT NULL,
                        `isPathVerified` INTEGER NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`profileId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `kb_import_audit` (
                        `id` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `sourceName` TEXT NOT NULL,
                        `addedCount` INTEGER NOT NULL,
                        `updatedCount` INTEGER NOT NULL,
                        `conflictCount` INTEGER NOT NULL,
                        `summary` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smartphone_doctor_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
