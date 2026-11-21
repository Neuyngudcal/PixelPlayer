package com.theveloper.pixelplay.data.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import timber.log.Timber
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Abstraction for encrypted key-value secret storage.
 */
interface SecretStore {
    fun getSecret(key: String): String?
    fun putSecret(key: String, value: String?): Boolean
    fun removeSecret(key: String)
    fun contains(key: String): Boolean
}

/**
 * [SecretStore] backed by private [SharedPreferences] and AES-256-GCM keys held in AndroidKeyStore.
 *
 * Ciphertext is stored as `Base64(12-byte IV || ciphertext+GCM-tag)` with NO_WRAP flags.
 * Fail-closed on corruption: decryption failures remove the corrupted entry and return `null`
 * without ever falling back to plaintext storage.
 */
class KeystoreSecretStore(
    context: Context,
    private val prefsName: String,
    private val keyAlias: String
) : SecretStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private val lock = Any()

    override fun getSecret(key: String): String? = synchronized(lock) {
        val encoded = prefs.getString(key, null) ?: return null
        return try {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            if (payload.size <= IV_LENGTH_BYTES) {
                throw IllegalArgumentException("Corrupted ciphertext payload length")
            }
            val iv = payload.copyOfRange(0, IV_LENGTH_BYTES)
            val cipherBytes = payload.copyOfRange(IV_LENGTH_BYTES, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.w(e, "Failed to decrypt secret entry in %s; purging corrupted key", prefsName)
            prefs.edit().remove(key).apply()
            null
        }
    }

    override fun putSecret(key: String, value: String?): Boolean = synchronized(lock) {
        if (value == null) {
            prefs.edit().remove(key).apply()
            return true
        }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            if (iv == null || iv.size != IV_LENGTH_BYTES) {
                throw GeneralSecurityException("Unexpected GCM IV length: ${iv?.size}")
            }
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
            val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
            prefs.edit().putString(key, encoded).commit()
        } catch (e: Exception) {
            Timber.e(e, "Failed to encrypt secret entry in %s; refusing plaintext write", prefsName)
            false
        }
    }

    override fun removeSecret(key: String) {
        synchronized(lock) {
            prefs.edit().remove(key).apply()
        }
    }

    override fun contains(key: String): Boolean = synchronized(lock) {
        prefs.contains(key)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingEntry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
        if (existingEntry != null) {
            return existingEntry.secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AES_KEY_SIZE_BITS = 256
        private const val IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
