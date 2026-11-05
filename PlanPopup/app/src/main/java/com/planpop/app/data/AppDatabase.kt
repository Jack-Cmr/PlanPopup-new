package com.planpop.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PlanEntity::class, CompletionEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun planDao(): PlanDao

    abstract fun completionDao(): CompletionDao

    companion object {
        /**
         * v1 -> v2 keeps every existing plan and completion. Old rows have no
         * recurrenceType, so they are explicitly backfilled as DAILY.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE plans ADD COLUMN recurrenceType TEXT")
                database.execSQL("ALTER TABLE plans ADD COLUMN year INTEGER")
                database.execSQL("ALTER TABLE plans ADD COLUMN month INTEGER")
                database.execSQL("ALTER TABLE plans ADD COLUMN day INTEGER")
                database.execSQL("ALTER TABLE plans ADD COLUMN triggeredAt INTEGER")
                database.execSQL(
                    "UPDATE plans SET recurrenceType = '$RECURRENCE_DAILY' " +
                        "WHERE recurrenceType IS NULL"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun create(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "plan_popup.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
