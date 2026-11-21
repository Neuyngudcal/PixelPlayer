package com.theveloper.pixelplay.data.backup.module

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.theveloper.pixelplay.data.preferences.PreferenceBackupEntry
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Files

class GlobalSettingsModuleHandlerTest {

    @Test
    fun `export and restore omit sensitive keys backup history and local offline paths`() = runTest {
        val tempDir = Files.createTempDirectory("global-settings-handler-test")
        try {
            val dataStore = PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
            )
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("nav_bar_style")] = "floating"
                prefs[stringPreferencesKey("gemini_api_key")] = "secret-api-key"
                prefs[stringPreferencesKey("active_offline_model_path")] = "/data/user/0/model.gguf"
                prefs[stringPreferencesKey("backup_history_json")] = "[{\"id\":\"keep\"}]"
            }
            val repo = UserPreferencesRepository(dataStore = dataStore, json = Json)
            val gson = Gson()
            val handler = GlobalSettingsModuleHandler(repo, gson)

            val exportedJson = handler.export()
            val listType = TypeToken.getParameterized(List::class.java, PreferenceBackupEntry::class.java).type
            val exported: List<PreferenceBackupEntry> = gson.fromJson(exportedJson, listType)
            assertEquals(listOf("nav_bar_style"), exported.map { it.key })

            val maliciousPayload = gson.toJson(
                listOf(
                    PreferenceBackupEntry(key = "nav_bar_style", type = "string", stringValue = "compact"),
                    PreferenceBackupEntry(key = "openai_api_key", type = "string", stringValue = "sk-evil"),
                    PreferenceBackupEntry(key = "active_offline_model_path", type = "string", stringValue = "/evil/gguf"),
                    PreferenceBackupEntry(key = "backup_history_json", type = "string", stringValue = "wiped"),
                    PreferenceBackupEntry(key = "auth_secret", type = "string", stringValue = "evil")
                )
            )
            handler.restore(maliciousPayload)

            val afterRestore = dataStore.data.first()
            assertEquals("compact", afterRestore[stringPreferencesKey("nav_bar_style")])
            assertEquals("[{\"id\":\"keep\"}]", afterRestore[stringPreferencesKey("backup_history_json")])
            assertEquals("/data/user/0/model.gguf", afterRestore[stringPreferencesKey("active_offline_model_path")])
            assertFalse(afterRestore.contains(stringPreferencesKey("openai_api_key")))
            assertFalse(afterRestore.contains(stringPreferencesKey("auth_secret")))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
