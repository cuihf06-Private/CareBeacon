package com.carebeacon.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Reminder::class, AckLog::class, Account::class, Relationship::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun reminderDao(): ReminderDao
    abstract fun ackLogDao(): AckLogDao
    abstract fun accountDao(): AccountDao
    abstract fun relationshipDao(): RelationshipDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /**
         * v1 → v2: introduce `accounts` and `relationships` for the new account
         * model. The new tables start empty; PR2 will back-fill old reminders
         * via a one-shot migration that also creates a `legacy` account.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `accounts` (
                        `id` TEXT NOT NULL,
                        `username` TEXT NOT NULL,
                        `display_name` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_accounts_username` ON `accounts` (`username`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `relationships` (
                        `id` TEXT NOT NULL,
                        `ward_id` TEXT NOT NULL,
                        `guardian_id` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `invited_at` INTEGER NOT NULL,
                        `accepted_at` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_relationships_ward_id` ON `relationships` (`ward_id`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_relationships_guardian_id` " +
                        "ON `relationships` (`guardian_id`)"
                )
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "carebeacon.db"
            )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration() // safety net for dev
                .build()
                .also { instance = it }
        }
    }
}