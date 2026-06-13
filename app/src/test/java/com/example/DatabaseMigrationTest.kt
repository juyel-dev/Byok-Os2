package com.example

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.core.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class DatabaseMigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    @Config(sdk = [28]) // Android 9 / API 28
    fun testMigrationFrom2To3_onApi28_preservesData() {
        val database = helper.createDatabase(TEST_DB, 2)

        // Insert mock data using ContentValues
        val values = ContentValues().apply {
            put("id", "provider_api28")
            put("displayName", "Local Provider API 28")
            put("baseUrl", "http://localhost")
            put("apiKey", "apiKey-value-api28")
            put("modelName", "local-gemini")
            put("isActive", 1)
            put("createdAt", 178000000000L)
        }
        database.insert("llm_providers", SQLiteDatabase.CONFLICT_REPLACE, values)
        database.close()

        // Run migration to version 3
        val migratedDatabase = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            AppDatabase.MIGRATION_2_3
        )

        // Query migrated database and verify keys are correctly renamed and data is preserved
        val cursor = migratedDatabase.query("SELECT * FROM llm_providers")
        assertTrue("Cursor should not be empty", cursor.moveToFirst())

        val idIndex = cursor.getColumnIndex("id")
        val displayNameIndex = cursor.getColumnIndex("displayName")
        val baseUrlIndex = cursor.getColumnIndex("baseUrl")
        val encryptedApiKeyIndex = cursor.getColumnIndex("encryptedApiKey")
        val modelNameIndex = cursor.getColumnIndex("modelName")
        val isActiveIndex = cursor.getColumnIndex("isActive")

        assertEquals("provider_api28", cursor.getString(idIndex))
        assertEquals("Local Provider API 28", cursor.getString(displayNameIndex))
        assertEquals("http://localhost", cursor.getString(baseUrlIndex))
        assertEquals("apiKey-value-api28", cursor.getString(encryptedApiKeyIndex))
        assertEquals("local-gemini", cursor.getString(modelNameIndex))
        assertEquals(1, cursor.getInt(isActiveIndex))
        
        cursor.close()
    }

    @Test
    @Config(sdk = [34]) // Android 14+ / API 34
    fun testMigrationFrom2To3_onApi34_preservesData() {
        val database = helper.createDatabase(TEST_DB, 2)

        // Insert mock data
        val values = ContentValues().apply {
            put("id", "provider_api34")
            put("displayName", "Cloud Provider API 34")
            put("baseUrl", "https://api.openai.com")
            put("apiKey", "apiKey-value-api34")
            put("modelName", "gpt-4")
            put("isActive", 0)
            put("createdAt", 179000000000L)
        }
        database.insert("llm_providers", SQLiteDatabase.CONFLICT_REPLACE, values)
        database.close()

        // Run migration to 3
        val migratedDatabase = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            AppDatabase.MIGRATION_2_3
        )

        val cursor = migratedDatabase.query("SELECT * FROM llm_providers")
        assertTrue("Cursor should not be empty", cursor.moveToFirst())

        val idIndex = cursor.getColumnIndex("id")
        val displayNameIndex = cursor.getColumnIndex("displayName")
        val baseUrlIndex = cursor.getColumnIndex("baseUrl")
        val encryptedApiKeyIndex = cursor.getColumnIndex("encryptedApiKey")
        val modelNameIndex = cursor.getColumnIndex("modelName")
        val isActiveIndex = cursor.getColumnIndex("isActive")

        assertEquals("provider_api34", cursor.getString(idIndex))
        assertEquals("Cloud Provider API 34", cursor.getString(displayNameIndex))
        assertEquals("https://api.openai.com", cursor.getString(baseUrlIndex))
        assertEquals("apiKey-value-api34", cursor.getString(encryptedApiKeyIndex))
        assertEquals("gpt-4", cursor.getString(modelNameIndex))
        assertEquals(0, cursor.getInt(isActiveIndex))

        cursor.close()
    }

    @Test
    fun testFreshInstallCreatesCorrectLatestSchema() {
        // MigrationTestHelper can create the latest database (v3) directly
        val database = helper.createDatabase(TEST_DB, 3)
        
        // Let's verify that the table has 'encryptedApiKey' and not 'apiKey'
        val cursor = database.query("PRAGMA table_info(llm_providers)")
        var hasEncryptedApiKey = false
        var hasApiKey = false

        while (cursor.moveToNext()) {
            val nameIndex = cursor.getColumnIndex("name")
            val columnName = cursor.getString(nameIndex)
            if (columnName == "encryptedApiKey") {
                hasEncryptedApiKey = true
            }
            if (columnName == "apiKey") {
                hasApiKey = true
            }
        }
        cursor.close()

        assertTrue("Latest schema must contain encryptedApiKey column", hasEncryptedApiKey)
        assertTrue("Latest schema must NOT contain apiKey column", !hasApiKey)
        database.close()
    }

    @Test
    fun testMigrationRollbackOnFailure() {
        // Create schema version 2 database with valid row
        val database = helper.createDatabase(TEST_DB, 2)
        val values = ContentValues().apply {
            put("id", "rollback_prov")
            put("displayName", "Rollback Test")
            put("baseUrl", "http://example.com")
            put("apiKey", "secret-key")
            put("modelName", "model-r")
            put("isActive", 1)
            put("createdAt", 123456L)
        }
        database.insert("llm_providers", SQLiteDatabase.CONFLICT_REPLACE, values)
        database.close()

        // Create an intentional failing migration
        val failingMigration = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Step 1 & 2 succeed
                db.execSQL("CREATE TABLE `llm_providers_new` (`id` TEXT PRIMARY KEY NOT NULL, `encryptedApiKey` TEXT NOT NULL)")
                db.execSQL("INSERT INTO `llm_providers_new` (id, encryptedApiKey) SELECT id, apiKey FROM llm_providers")
                
                // Intentionally throw exception to crash/abort the migration middle-way
                throw RuntimeException("Simulated interrupted transaction crash")
            }
        }

        try {
            helper.runMigrationsAndValidate(
                TEST_DB,
                3,
                true,
                failingMigration
            )
            fail("Migration was expected to abort with exception!")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("Simulated interrupted transaction crash") || e.cause?.message?.contains("Simulated interrupted transaction crash") == true)
        }

        // Now, re-open the database as version 2 to verify data was untouched and transaction rolled back successfully
        val dbFile = InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath(TEST_DB)
        val reopenedDb = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        val checkCursor = reopenedDb.rawQuery("SELECT * FROM llm_providers", null)
        assertTrue("Old data should be preserved due to transaction rollback", checkCursor.moveToFirst())
        
        val idIdx = checkCursor.getColumnIndex("id")
        val apiKeyIdx = checkCursor.getColumnIndex("apiKey")
        assertEquals("rollback_prov", checkCursor.getString(idIdx))
        assertEquals("secret-key", checkCursor.getString(apiKeyIdx))
        checkCursor.close()
        reopenedDb.close()
    }
}
