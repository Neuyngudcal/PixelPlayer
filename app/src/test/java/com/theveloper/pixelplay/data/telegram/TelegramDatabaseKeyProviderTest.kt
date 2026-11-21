package com.theveloper.pixelplay.data.telegram

import com.theveloper.pixelplay.data.security.SecretStore
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class TelegramDatabaseKeyProviderTest {

    private class FakeSecretStore(
        private val failWrites: Boolean = false
    ) : SecretStore {
        val map = mutableMapOf<String, String>()

        override fun getSecret(key: String): String? = map[key]

        override fun putSecret(key: String, value: String?): Boolean {
            if (failWrites) return false
            if (value == null) {
                map.remove(key)
            } else {
                map[key] = value
            }
            return true
        }

        override fun removeSecret(key: String) {
            map.remove(key)
        }

        override fun contains(key: String): Boolean = map.containsKey(key)
    }

    @Test
    fun `getOrCreateDatabaseKey generates 32-byte key and reuses persisted value`() {
        val store = FakeSecretStore()
        var callCount = 0
        val provider = TelegramDatabaseKeyProvider(store) { size ->
            callCount++
            ByteArray(size) { index -> (index + 1).toByte() }
        }

        val firstKey = provider.getOrCreateDatabaseKey()
        val secondKey = provider.getOrCreateDatabaseKey()

        assertNotNull(firstKey)
        assertEquals(32, firstKey!!.size)
        assertArrayEquals(firstKey, secondKey)
        assertEquals(1, callCount)
        assertTrue(store.contains(TelegramDatabaseKeyProvider.KEY_DB_ENCRYPTION_KEY))
    }

    @Test
    fun `getOrCreateDatabaseKey returns null when persisting fails`() {
        val store = FakeSecretStore(failWrites = true)
        val provider = TelegramDatabaseKeyProvider(store) { size -> ByteArray(size) { 7 } }

        val key = provider.getOrCreateDatabaseKey()

        assertNull(key)
        assertFalse(store.contains(TelegramDatabaseKeyProvider.KEY_DB_ENCRYPTION_KEY))
    }

    @Test
    fun `shouldMigrateLegacyUnencryptedDatabase detects only non-empty unmarked database dirs`() {
        val store = FakeSecretStore()
        val provider = TelegramDatabaseKeyProvider(store)
        val tempRoot = Files.createTempDirectory("tdlib-migration-check-test")
        try {
            val missingDir = tempRoot.resolve("missing").toFile()
            assertFalse(provider.shouldMigrateLegacyUnencryptedDatabase(missingDir))

            val emptyDir = Files.createDirectories(tempRoot.resolve("empty")).toFile()
            assertFalse(provider.shouldMigrateLegacyUnencryptedDatabase(emptyDir))

            val populatedDir = Files.createDirectories(tempRoot.resolve("legacy")).toFile()
            populatedDir.resolve("td.binlog").writeText("legacy")
            assertTrue(provider.shouldMigrateLegacyUnencryptedDatabase(populatedDir))

            assertTrue(provider.markDatabaseEncrypted())
            assertTrue(provider.isDatabaseMarkedEncrypted())
            assertFalse(provider.shouldMigrateLegacyUnencryptedDatabase(populatedDir))
        } finally {
            tempRoot.toFile().deleteRecursively()
        }
    }
}
