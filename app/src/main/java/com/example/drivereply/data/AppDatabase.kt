package com.example.drivereply.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@Database(
    entities = [MessageTemplate::class, ReplyLogEntry::class, TemplateRule::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun messageTemplateDao(): MessageTemplateDao
    abstract fun replyLogDao(): ReplyLogDao
    abstract fun templateRuleDao(): TemplateRuleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

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
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }

    private class PrepopulateCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    val dao = database.messageTemplateDao()
                    dao.insert(
                        MessageTemplate(
                            id = UUID.randomUUID().toString(),
                            name = "Driving",
                            body = "I'm currently driving and can't respond. I'll get back to you when I'm safely stopped. \uD83D\uDE97",
                            isActive = true
                        )
                    )
                    dao.insert(
                        MessageTemplate(
                            id = UUID.randomUUID().toString(),
                            name = "Busy",
                            body = "I'm busy right now and can't reply. I'll get back to you soon."
                        )
                    )
                }
            }
        }
    }
}
