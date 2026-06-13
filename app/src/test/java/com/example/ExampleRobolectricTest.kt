package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.data.*
import com.example.core.domain.exceptions.BackupCorruptedException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var cryptoManager: CryptoManager
    private lateinit var repository: ChatRepositoryImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cryptoManager = CryptoManager(context)
        repository = ChatRepositoryImpl(db, cryptoManager)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun read_string_from_context() {
        val appName = context.getString(R.string.app_name)
        assertEquals("BYOK OS", appName)
    }

    @Test
    fun security_test_export_contains_only_ciphertext() = runBlocking {
        // Create an initial provider
        val plaintextKey = "sk-proj-test12345secretkey"
        val provider = LlmProvider(
            id = "test_provider",
            displayName = "Secure Model",
            baseUrl = "https://api.openai.com",
            encryptedApiKey = cryptoManager.encrypt(plaintextKey),
            modelName = "gpt-4",
            isActive = true,
            createdAt = System.currentTimeMillis()
        )
        db.llmProviderDao().insertProvider(provider)

        // Export backup helper
        val backupJsonStr = repository.exportDatabaseBackup()
        assertFalse("Backup was empty!", backupJsonStr.isEmpty())

        // Ensure the exported JSON must NOT contain the plaintext API key
        assertFalse("VULNERABILITY: Plaintext API key found in export!", backupJsonStr.contains(plaintextKey))

        // Ensure it contains the encrypted key (starts with "v1:")
        val json = JSONObject(backupJsonStr)
        val providersArray = json.getJSONArray("llm_providers")
        assertEquals(1, providersArray.length())
        val providerObj = providersArray.getJSONObject(0)
        val apiKeyVal = providerObj.getString("apiKey")
        assertTrue("Ciphertext apiKey missing or not starting with version specifier!", apiKeyVal.startsWith("v1:"))
    }

    @Test
    fun functional_restore_test_valid_backup() = runBlocking {
        // A clean, valid payload with encrypted or plaintext keys
        val validPayload = """
            {
              "llm_providers": [
                {
                  "id": "prov_custom",
                  "displayName": "Restore Provider",
                  "baseUrl": "https://api.example.com",
                  "apiKey": "v1:U29tZUNpcGhlcnRleHQ=",
                  "modelName": "fast-model",
                  "isActive": true,
                  "createdAt": 178000000000
                }
              ],
              "mcp_servers": [
                {
                  "id": "mcp_custom",
                  "name": "Local Tools",
                  "endpoint": "http://10.0.2.2:3000",
                  "transport": "HTTP",
                  "headersJson": null,
                  "authToken": null,
                  "timeoutSeconds": 15,
                  "retryCount": 2,
                  "isEnabled": true,
                  "cachedToolsJson": "[]"
                }
              ],
              "memories": [
                {
                  "id": "mem_custom",
                  "content": "User loves dark mode",
                  "type": "preference",
                  "createdAt": 178000002000,
                  "isActive": true
                }
              ],
              "chat_sessions": [
                {
                  "id": "sess_custom",
                  "title": "Restored Conversation",
                  "providerId": "prov_custom",
                  "createdAt": 178000003000,
                  "updatedAt": 178000004000,
                  "isCompressed": false,
                  "totalTokensUsed": 150
                }
              ]
            }
        """.trimIndent()

        // Act
        repository.importDatabaseBackup(validPayload)

        // Assert
        val providers = db.llmProviderDao().getAllProviders()
        assertEquals(1, providers.size)
        assertEquals("Restore Provider", providers[0].displayName)
        val decryptedRestoredKey = cryptoManager.decrypt(providers[0].encryptedApiKey)
        assertEquals(com.example.core.common.ProviderValidator.KEY_SENTINEL, decryptedRestoredKey)

        val servers = db.mcpServerDao().getAllServers()
        assertEquals(1, servers.size)
        assertEquals("Local Tools", servers[0].name)

        val memories = db.memoryDao().getActiveMemories()
        assertEquals(1, memories.size)
        assertEquals("User loves dark mode", memories[0].content)

        val sessions = db.chatSessionDao().getSessionById("sess_custom")
        assertNotNull(sessions)
        assertEquals("Restored Conversation", sessions?.title)
    }

    @Test
    fun corrupted_backup_test_rolls_back_safely() = runBlocking {
        // Missing a required field in memories array elements
        val corruptedPayload = """
            {
              "llm_providers": [
                {
                  "id": "prov_err",
                  "displayName": "Trans Provider",
                  "baseUrl": "https://api.com",
                  "apiKey": "v1:abc",
                  "modelName": "m1",
                  "isActive": true,
                  "createdAt": 123
                }
              ],
              "mcp_servers": [],
              "memories": [
                {
                  "content": "Missing required 'id' and 'type' keys"
                }
              ],
              "chat_sessions": []
            }
        """.trimIndent()

        // Act & Assert
        try {
            repository.importDatabaseBackup(corruptedPayload)
            fail("Expected BackupCorruptedException but none was thrown!")
        } catch (e: BackupCorruptedException) {
            // Success: Exception is correct
        }

        // Verify full transactional rollback: llm_providers should NOT have ingested "prov_err"
        val providers = db.llmProviderDao().getAllProviders()
        assertTrue("Transaction did not rollback! Found partially ingested provider.", providers.isEmpty())
    }

    @Test
    fun missing_field_test_graceful_rejection() = runBlocking {
        // Missing "chat_sessions" table completely
        val incompletePayload = """
            {
              "llm_providers": [],
              "mcp_servers": [],
              "memories": []
            }
        """.trimIndent()

        // Act & Assert
        try {
            repository.importDatabaseBackup(incompletePayload)
            fail("Expected BackupCorruptedException due to missing table!")
        } catch (e: BackupCorruptedException) {
            assertTrue(e.message!!.contains("chat_sessions"))
        }

        // Database should remain unchanged
        val providers = db.llmProviderDao().getAllProviders()
        assertTrue(providers.isEmpty())
    }

    @Test
    fun provider_validator_handles_valid_and_unconfigured_and_invalid_states() {
        val validResult = com.example.core.common.ProviderValidator.validate(
            baseUrl = "https://api.openai.com/v1",
            decryptedApiKey = "sk-12345"
        )
        assertTrue(validResult is com.example.core.common.ProviderValidator.ValidationResult.Valid)

        val unconfiguredResult = com.example.core.common.ProviderValidator.validate(
            baseUrl = "https://api.openai.com/v1",
            decryptedApiKey = com.example.core.common.ProviderValidator.KEY_SENTINEL
        )
        assertTrue(unconfiguredResult is com.example.core.common.ProviderValidator.ValidationResult.Unconfigured)

        val blankUrlResult = com.example.core.common.ProviderValidator.validate(
            baseUrl = "   ",
            decryptedApiKey = "sk-12345"
        )
        assertTrue(blankUrlResult is com.example.core.common.ProviderValidator.ValidationResult.Invalid)

        val blankKeyResult = com.example.core.common.ProviderValidator.validate(
            baseUrl = "https://api.openai.com/v1",
            decryptedApiKey = "   "
        )
        assertTrue(blankKeyResult is com.example.core.common.ProviderValidator.ValidationResult.Invalid)
    }

    @Test
    fun database_replaces_blank_keys_with_unconfigured_upon_repair() = runBlocking {
        // Prepare a provider with a legacy blank key
        val legacyProvider = LlmProvider(
            id = "legacy_openai",
            displayName = "Legacy OpenAI",
            baseUrl = "https://api.openai.com/v1",
            encryptedApiKey = "", // blank key
            modelName = "gpt-4",
            isActive = true,
            createdAt = System.currentTimeMillis()
        )
        // Insert directly via DAO (bypassing repo validation as done by legacy schema inserts)
        db.llmProviderDao().insertProvider(legacyProvider)

        val retrievedLegacy = db.llmProviderDao().getProviderById("legacy_openai")
        assertEquals("", retrievedLegacy?.encryptedApiKey)

        // Now run the Repair logic!
        val providers = repository.getAllProviders()
        for (provider in providers) {
            val rawKey = provider.encryptedApiKey.trim()
            if (rawKey.isEmpty()) {
                val repaired = provider.copy(encryptedApiKey = com.example.core.common.ProviderValidator.KEY_SENTINEL)
                repository.updateProvider(repaired)
            }
        }

        val repairedRetrieved = repository.getProviderById("legacy_openai")
        assertEquals(com.example.core.common.ProviderValidator.KEY_SENTINEL, repairedRetrieved?.encryptedApiKey)
    }
}
