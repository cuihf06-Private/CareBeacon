package com.carebeacon.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Reminder::class, AckLog::class, Account::class, Relationship::class],
    version = 3,
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
         * model. The new tables start empty; v2 → v3 back-fills reminders.
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

        /**
         * v2 → v3: add [Reminder.wardId] / [Reminder.guardianId] and back-fill
         * old rows to a single `legacy` account under a self-relationship.
         *
         * The `legacy` account's id is a fixed constant ([LEGACY_ACCOUNT_ID]) so
         * the user can find it via username `legacy` from the AuthScreen.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add the new columns. They are NOT NULL with a default of '' so
                // existing rows remain valid before we back-fill them.
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `ward_id` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `guardian_id` TEXT NOT NULL DEFAULT ''")

                // Seed the legacy account + self-relationship so old reminders
                // can be attributed and the user can log back in.
                val now = System.currentTimeMillis()
                db.execSQL(
                    "INSERT OR IGNORE INTO `accounts` (`id`, `username`, `display_name`, `created_at`) " +
                        "VALUES ('$LEGACY_ACCOUNT_ID', 'legacy', 'Legacy (imported data)', $now)"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO `relationships` " +
                        "(`id`, `ward_id`, `guardian_id`, `status`, `invited_at`, `accepted_at`) " +
                        "VALUES ('$LEGACY_RELATIONSHIP_ID', '$LEGACY_ACCOUNT_ID', " +
                        "'$LEGACY_ACCOUNT_ID', 'ACCEPTED', $now, $now)"
                )
                db.execSQL(
                    "UPDATE `reminders` SET `ward_id` = '$LEGACY_ACCOUNT_ID', " +
                        "`guardian_id` = '$LEGACY_ACCOUNT_ID' WHERE `ward_id` = ''"
                )
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "carebeacon.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration() // safety net for dev
                .build()
                .also { instance = it }
        }
    }
}