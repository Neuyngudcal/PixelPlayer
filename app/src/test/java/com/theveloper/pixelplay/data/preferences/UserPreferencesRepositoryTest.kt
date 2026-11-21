package com.theveloper.pixelplay.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class UserPreferencesRepositoryTest {

    @Test
    fun `default artist delimiters avoid common characters inside artist names`() {
        assertEquals(listOf(";"), UserPreferencesRepository.DEFAULT_ARTIST_DELIMITERS)
    }

    @Test
    fun `artistDelimitersFlow normalizes stored legacy defaults`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            repository.setArtistDelimiters(listOf("/", ";", ",", "+", "&"))

            assertEquals(
                UserPreferencesRepository.DEFAULT_ARTIST_DELIMITERS,
                repository.artistDelimitersFlow.first()
            )
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `clearPreferencesExceptKeys preserves initial setup completion`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            repository.setInitialSetupDone(true)
            repository.setNavBarStyle("compact")

            repository.clearPreferencesExceptKeys(emptySet())

            assertTrue(repository.initialSetupDoneFlow.first())
            assertEquals(NavBarStyle.DEFAULT, repository.navBarStyleFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `importPreferencesFromBackup clearExisting preserves initial setup completion`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            repository.setInitialSetupDone(true)
            repository.setNavBarStyle("compact")

            repository.importPreferencesFromBackup(
                entries = listOf(
                    PreferenceBackupEntry(
                        key = "nav_bar_style",
                        type = "string",
                        stringValue = "restored"
                    )
                ),
                clearExisting = true
            )

            assertTrue(repository.initialSetupDoneFlow.first())
            assertEquals("restored", repository.navBarStyleFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `navBarCornerRadiusFlow clamps values outside the supported UI range`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            repository.setNavBarCornerRadius(-1)
            assertEquals(MIN_NAV_BAR_CORNER_RADIUS, repository.navBarCornerRadiusFlow.first())

            repository.setNavBarCornerRadius(999)
            assertEquals(MAX_NAV_BAR_CORNER_RADIUS, repository.navBarCornerRadiusFlow.first())

            repository.importPreferencesFromBackup(
                entries = listOf(
                    PreferenceBackupEntry(
                        key = "nav_bar_corner_radius",
                        type = "int",
                        intValue = -1
                    )
                ),
                clearExisting = false
            )
            assertEquals(MIN_NAV_BAR_CORNER_RADIUS, repository.navBarCornerRadiusFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `exportPreferencesForBackup excludes sensitive credentials local paths and backup history`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val dataStore = PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
            )
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("nav_bar_style")] = "compact"
                prefs[stringPreferencesKey("gemini_api_key")] = "AIza-secret"
                prefs[stringPreferencesKey("custom_provider_secret")] = "s3cr3t"
                prefs[stringPreferencesKey("auth_token")] = "tok"
                prefs[stringPreferencesKey("server_password")] = "pw"
                prefs[stringPreferencesKey("session_cookie")] = "c=1"
                prefs[stringPreferencesKey("active_offline_model_path")] = "/data/user/0/model.gguf"
                prefs[stringPreferencesKey("active_offline_model_name")] = "LocalModel"
                prefs[stringPreferencesKey("active_offline_model_id")] = "model-1"
                prefs[stringPreferencesKey("backup_history_json")] = "[]"
            }
            val repository = UserPreferencesRepository(dataStore = dataStore, json = Json)

            val exportedKeys = repository.exportPreferencesForBackup().map { it.key }.toSet()

            assertEquals(setOf("nav_bar_style"), exportedKeys)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `importPreferencesFromBackup ignores excluded keys and clearPreferencesByKeys never wipes them`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val dataStore = PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
            )
            dataStore.edit { prefs ->
                prefs[stringPreferencesKey("backup_history_json")] = "[{\"id\":\"1\"}]"
                prefs[stringPreferencesKey("active_offline_model_path")] = "/local/model.gguf"
            }
            val repository = UserPreferencesRepository(dataStore = dataStore, json = Json)

            repository.importPreferencesFromBackup(
                entries = listOf(
                    PreferenceBackupEntry(key = "nav_bar_style", type = "string", stringValue = "minimal"),
                    PreferenceBackupEntry(key = "gemini_api_key", type = "string", stringValue = "injected-key"),
                    PreferenceBackupEntry(key = "backup_history_json", type = "string", stringValue = "tampered"),
                    PreferenceBackupEntry(key = "active_offline_model_path", type = "string", stringValue = "/evil/path"),
                    PreferenceBackupEntry(key = "user_token", type = "string", stringValue = "injected-token")
                ),
                clearExisting = true
            )

            val snapshot = dataStore.data.first()
            assertEquals("minimal", snapshot[stringPreferencesKey("nav_bar_style")])
            assertEquals("[{\"id\":\"1\"}]", snapshot[stringPreferencesKey("backup_history_json")])
            assertEquals("/local/model.gguf", snapshot[stringPreferencesKey("active_offline_model_path")])
            assertFalse(snapshot.contains(stringPreferencesKey("gemini_api_key")))
            assertFalse(snapshot.contains(stringPreferencesKey("user_token")))

            repository.clearPreferencesByKeys(setOf("backup_history_json", "nav_bar_style"))
            val afterTargetedClear = dataStore.data.first()
            assertEquals("[{\"id\":\"1\"}]", afterTargetedClear[stringPreferencesKey("backup_history_json")])
            assertFalse(afterTargetedClear.contains(stringPreferencesKey("nav_bar_style")))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
