package com.drivereply.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

@Database(
    entities = [MessageTemplate::class, ReplyLogEntry::class, TemplateRule::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun messageTemplateDao(): MessageTemplateDao
    abstract fun replyLogDao(): ReplyLogDao
    abstract fun templateRuleDao(): TemplateRuleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE reply_log ADD COLUMN packageName TEXT NOT NULL DEFAULT 'com.whatsapp'"
                )
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `template_rules` (
                        `id` TEXT NOT NULL,
                        `templateId` TEXT NOT NULL,
                        `contactName` TEXT,
                        `daysOfWeek` TEXT,
                        `startTime` INTEGER,
                        `endTime` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reply_log_timestamp` ON `reply_log` (`timestamp`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_template_rules_templateId` ON `template_rules` (`templateId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_template_rules_contactName` ON `template_rules` (`contactName`)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "drivereply.db"
            )
                .addCallback(PrepopulateCallback())
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
        }
    }

    private class PrepopulateCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            val now = System.currentTimeMillis()
            db.execSQL(
                "INSERT OR IGNORE INTO `message_templates` (`id`, `name`, `body`, `isActive`, `createdAt`) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any>(
                    UUID.randomUUID().toString(),
                    "Driving",
                    "I'm currently driving and can't respond. I'll get back to you when I'm safely stopped. \uD83D\uDE97",
                    1,
                    now
                )
            )
            db.execSQL(
                "INSERT OR IGNORE INTO `message_templates` (`id`, `name`, `body`, `isActive`, `createdAt`) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any>(
                    UUID.randomUUID().toString(),
                    "Busy",
                    "I'm busy right now and can't reply. I'll get back to you soon.",
                    0,
                    now + 1L
                )
            )
        }
    }
}
