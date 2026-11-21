package com.theveloper.pixelplay.data.telegram

import androidx.annotation.VisibleForTesting
import com.theveloper.pixelplay.data.security.SecretStore
import com.theveloper.pixelplay.data.security.TelegramDbKeyStore
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides and persists a 32-byte random TDLib SQLCipher encryption key protected by AndroidKeyStore.
 * Also tracks whether the local `tdlib` database directory has completed encryption migration.
 */
@Singleton
class TelegramDatabaseKeyProvider internal constructor(
    private val secretStore: SecretStore,
    private val randomBytesGenerator: (Int) -> ByteArray
) {

    @Inject
    constructor(
        @TelegramDbKeyStore secretStore: SecretStore
    ) : this(
        secretStore = secretStore,
        randomBytesGenerator = { size ->
            ByteArray(size).also { SecureRandom().nextBytes(it) }
        }
    )

    private val lock = Any()

    /**
     * Returns the persisted 32-byte encryption key or generates and stores a new one.
     * Returns `null` (fail-closed) if persisting the newly generated key fails.
     */
    fun getOrCreateDatabaseKey(): ByteArray? = synchronized(lock) {
        val existingEncoded = secretStore.getSecret(KEY_DB_ENCRYPTION_KEY)
        if (!existingEncoded.isNullOrBlank()) {
            val decoded = runCatching { Base64.getDecoder().decode(existingEncoded) }.getOrNull()
            if (decoded != null && decoded.size == KEY_LENGTH_BYTES) {
                return decoded.copyOf()
            }
            secretStore.removeSecret(KEY_DB_ENCRYPTION_KEY)
        }

        val generated = randomBytesGenerator(KEY_LENGTH_BYTES)
        if (generated.size != KEY_LENGTH_BYTES) {
            return null
        }
        val encoded = Base64.getEncoder().withoutPadding().encodeToString(generated)
        return if (secretStore.putSecret(KEY_DB_ENCRYPTION_KEY, encoded)) {
            generated.copyOf()
        } else {
            generated.fill(0)
            null
        }
    }

    fun isDatabaseMarkedEncrypted(): Boolean = synchronized(lock) {
        secretStore.getSecret(KEY_DB_ENCRYPTED_MARKER) == MARKER_TRUE
    }

    fun markDatabaseEncrypted(): Boolean = synchronized(lock) {
        secretStore.putSecret(KEY_DB_ENCRYPTED_MARKER, MARKER_TRUE)
    }

    fun shouldMigrateLegacyUnencryptedDatabase(databaseDir: File): Boolean = synchronized(lock) {
        if (isDatabaseMarkedEncrypted()) return false
        return databaseDir.exists() &&
            databaseDir.isDirectory &&
            (databaseDir.listFiles()?.isNotEmpty() == true)
    }

    companion object {
        @VisibleForTesting
        internal const val KEY_DB_ENCRYPTION_KEY = "tdlib_database_encryption_key_b64"

        @VisibleForTesting
        internal const val KEY_DB_ENCRYPTED_MARKER = "tdlib_database_encrypted_marker"

        private const val MARKER_TRUE = "true"
        private const val KEY_LENGTH_BYTES = 32
    }
}
