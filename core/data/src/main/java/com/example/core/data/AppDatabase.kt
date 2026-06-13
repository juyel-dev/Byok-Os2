package com.example.core.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ChatSession::class,
        ChatMessage::class,
        LlmProvider::class,
        McpServer::class,
        Memory::class,
        AppSetting::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun llmProviderDao(): LlmProviderDao
    abstract fun mcpServerDao(): McpServerDao
    abstract fun memoryDao(): MemoryDao
    abstract fun appSettingDao(): AppSettingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_session_updated_at` ON `chat_sessions` (`updatedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_message_session` ON `chat_messages` (`sessionId`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create temporary table with the correct schema
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `llm_providers_new` (
                        `id` TEXT NOT NULL, 
                        `displayName` TEXT NOT NULL, 
                        `baseUrl` TEXT NOT NULL, 
                        `encryptedApiKey` TEXT NOT NULL, 
                        `modelName` TEXT NOT NULL, 
                        `isActive` INTEGER NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )

                // 2. Copy the data from the old table into the new table
                db.execSQL(
                    """
                    INSERT INTO `llm_providers_new` (
                        `id`, `displayName`, `baseUrl`, `encryptedApiKey`, `modelName`, `isActive`, `createdAt`
                    ) 
                    SELECT `id`, `displayName`, `baseUrl`, `apiKey`, `modelName`, `isActive`, `createdAt` 
                    FROM `llm_providers`
                    """.trimIndent()
                )

                // 3. Drop the old table
                db.execSQL("DROP TABLE IF EXISTS `llm_providers`")

                // 4. Rename the temporary table to the original table name
                db.execSQL("ALTER TABLE `llm_providers_new` RENAME TO `llm_providers`")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "byok_os_database"
                )
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration(true)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        createTriggers(db)
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        createTriggers(db)
                    }

                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        super.onDestructiveMigration(db)
                        Log.w("AppDatabase", "⚠️ WARNING: Destructive migration was triggered! All database tables were recreated and existing data was lost.")
                        createTriggers(db)
                    }

                    private fun createTriggers(db: SupportSQLiteDatabase) {
                        try {
                            db.execSQL(
                                """
                                CREATE TRIGGER IF NOT EXISTS check_llm_providers_insert
                                BEFORE INSERT ON llm_providers
                                BEGIN
                                    SELECT CASE
                                        WHEN NEW.encryptedApiKey IS NULL OR NEW.encryptedApiKey = '' THEN
                                            RAISE(ABORT, 'Encrypted API Key must not be blank or empty!')
                                    END;
                                END;
                                """.trimIndent()
                            )
                            db.execSQL(
                                """
                                CREATE TRIGGER IF NOT EXISTS check_llm_providers_update
                                BEFORE UPDATE ON llm_providers
                                BEGIN
                                    SELECT CASE
                                        WHEN NEW.encryptedApiKey IS NULL OR NEW.encryptedApiKey = '' THEN
                                            RAISE(ABORT, 'Encrypted API Key must not be blank or empty!')
                                    END;
                                END;
                                """.trimIndent()
                            )
                        } catch (e: Exception) {
                            Log.e("AppDatabase", "Error setting up database-level triggers: ", e)
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
