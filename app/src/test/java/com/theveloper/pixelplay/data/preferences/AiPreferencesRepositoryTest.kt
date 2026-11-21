package com.theveloper.pixelplay.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.theveloper.pixelplay.data.ai.provider.AiProvider
import com.theveloper.pixelplay.data.security.SecretStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class AiPreferencesRepositoryTest {

    private class FakeSecretStore(
        initial: Map<String, String> = emptyMap()
    ) : SecretStore {
        val entries = initial.toMutableMap()

        override fun getSecret(key: String): String? = entries[key]

        override fun putSecret(key: String, value: String?): Boolean {
            if (value == null) {
                entries.remove(key)
            } else {
                entries[key] = value
            }
            return true
        }

        override fun removeSecret(key: String) {
            entries.remove(key)
        }

        override fun contains(key: String): Boolean = entries.containsKey(key)
    }

    @Test
    fun `setApiKey writes only to SecretStore and never to DataStore`() = runTest {
        val tempDir = Files.createTempDirectory("ai-prefs-test")
        try {
            val dataStore = PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
            )
            val secretStore = FakeSecretStore()
            val repo = AiPreferencesRepository(dataStore, secretStore)

            repo.setApiKey(AiProvider.GEMINI, "  AIza-super-secret  ")

            assertEquals("AIza-super-secret", secretStore.getSecret("gemini_api_key"))
            assertEquals("AIza-super-secret", repo.getApiKey(AiProvider.GEMINI).first())
            assertTrue(repo.hasActiveProviderConfigured.first())
            assertFalse(dataStore.data.first().contains(stringPreferencesKey("gemini_api_key")))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `legacy plaintext DataStore API keys migrate once to SecretStore and are removed`() = runTest {
        val tempDir = Files.createTempDirectory("ai-prefs-migration-test")
        try {
            val dataStore = PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
            )
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("gemini_api_key")] = "legacy-gemini-key"
                prefs[stringPreferencesKey("openai_api_key")] = "legacy-openai-key"
                prefs[stringPreferencesKey("ai_provider")] = "OPENAI"
            }
            val secretStore = FakeSecretStore()
            val repo = AiPreferencesRepository(dataStore, secretStore)

            assertEquals("legacy-gemini-key", repo.getApiKey(AiProvider.GEMINI).first())
            assertEquals("legacy-openai-key", repo.getApiKey(AiProvider.OPENAI).first())
            assertTrue(repo.hasActiveProviderConfigured.first())

            assertEquals("legacy-gemini-key", secretStore.getSecret("gemini_api_key"))
            assertEquals("legacy-openai-key", secretStore.getSecret("openai_api_key"))
            val postMigrationPrefs = dataStore.data.first()
            assertFalse(postMigrationPrefs.contains(stringPreferencesKey("gemini_api_key")))
            assertFalse(postMigrationPrefs.contains(stringPreferencesKey("openai_api_key")))
            assertEquals("OPENAI", postMigrationPrefs[stringPreferencesKey("ai_provider")])
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `setting blank API key removes secret entry cleanly`() = runTest {
        val tempDir = Files.createTempDirectory("ai-prefs-clear-test")
        try {
            val dataStore = PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
            )
            val secretStore = FakeSecretStore(mapOf("groq_api_key" to "gsk_123"))
            val repo = AiPreferencesRepository(dataStore, secretStore)

            assertEquals("gsk_123", repo.getApiKey(AiProvider.GROQ).first())
            repo.setApiKey(AiProvider.GROQ, "   ")

            assertFalse(secretStore.contains("groq_api_key"))
            assertEquals("", repo.getApiKey(AiProvider.GROQ).first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
