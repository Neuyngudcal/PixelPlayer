package com.theveloper.pixelplay.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Factory for encrypted [SharedPreferences] instances with:
 * - One-time migration and deletion of legacy `<prefsName>_plain` files.
 * - Single delete-and-retry recovery on [EncryptedSharedPreferences] corruption.
 * - Fail-closed process-lifetime [InMemorySharedPreferences] fallback (never plaintext on disk).
 */
object SecureSharedPreferencesFactory {

    private const val PLAIN_SUFFIX = "_plain"

    fun create(context: Context, prefsName: String): SharedPreferences {
        val appContext = context.applicationContext ?: context
        return create(appContext, prefsName) {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                prefsName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    @VisibleForTesting
    internal fun create(
        context: Context,
        prefsName: String,
        encryptedPrefsProvider: () -> SharedPreferences
    ): SharedPreferences {
        val plainName = "$prefsName$PLAIN_SUFFIX"
        val securePrefs = try {
            encryptedPrefsProvider()
        } catch (firstFailure: Exception) {
            Timber.w(firstFailure, "Corrupted or unreadable EncryptedSharedPreferences for %s; resetting once", prefsName)
            deleteSharedPreferencesCompat(context, prefsName)
            try {
                encryptedPrefsProvider()
            } catch (secondFailure: Exception) {
                Timber.e(
                    secondFailure,
                    "EncryptedSharedPreferences unavailable for %s; falling back to fail-closed in-memory store",
                    prefsName
                )
                deleteSharedPreferencesCompat(context, plainName)
                return InMemorySharedPreferences()
            }
        }

        migrateAndDeletePlainPrefs(context, plainName, securePrefs)
        return securePrefs
    }

    private fun migrateAndDeletePlainPrefs(
        context: Context,
        plainName: String,
        targetPrefs: SharedPreferences
    ) {
        val plainPrefsFile = sharedPrefsXmlFile(context, plainName)
        val plainPrefs = context.getSharedPreferences(plainName, Context.MODE_PRIVATE)
        val legacyEntries = try {
            plainPrefs.all
        } catch (e: Exception) {
            Timber.w(e, "Failed reading legacy plaintext prefs %s; purging", plainName)
            emptyMap<String, Any?>()
        }

        if (legacyEntries.isNotEmpty()) {
            val editor = targetPrefs.edit()
            var copiedCount = 0
            for ((key, value) in legacyEntries) {
                if (targetPrefs.contains(key)) continue
                when (value) {
                    is String -> {
                        editor.putString(key, value)
                        copiedCount++
                    }
                    is Boolean -> {
                        editor.putBoolean(key, value)
                        copiedCount++
                    }
                    is Int -> {
                        editor.putInt(key, value)
                        copiedCount++
                    }
                    is Long -> {
                        editor.putLong(key, value)
                        copiedCount++
                    }
                    is Float -> {
                        editor.putFloat(key, value)
                        copiedCount++
                    }
                    is Set<*> -> {
                        val stringSet = value.filterIsInstance<String>().toSet()
                        if (stringSet.size == value.size) {
                            editor.putStringSet(key, stringSet)
                            copiedCount++
                        }
                    }
                }
            }
            if (copiedCount > 0) {
                editor.commit()
            }
        }

        if (legacyEntries.isNotEmpty() || plainPrefsFile?.exists() == true) {
            plainPrefs.edit().clear().commit()
            deleteSharedPreferencesCompat(context, plainName)
        }
    }

    private fun sharedPrefsXmlFile(context: Context, prefsName: String): File? {
        val dataDir = runCatching { context.applicationInfo?.dataDir }.getOrNull() ?: return null
        return File(File(dataDir, "shared_prefs"), "$prefsName.xml")
    }

    private fun deleteSharedPreferencesCompat(context: Context, prefsName: String) {
        try {
            context.deleteSharedPreferences(prefsName)
        } catch (e: Exception) {
            Timber.w(e, "deleteSharedPreferences(%s) failed", prefsName)
        }
        try {
            val file = sharedPrefsXmlFile(context, prefsName)
            if (file != null && file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {
            // Best-effort cleanup alongside deleteSharedPreferences
        }
    }

    /**
     * Fail-closed ephemeral [SharedPreferences] kept only in memory for the current process.
     */
    internal class InMemorySharedPreferences : SharedPreferences {
        private val lock = Any()
        private val values = mutableMapOf<String, Any?>()
        private val listeners = CopyOnWriteArraySet<SharedPreferences.OnSharedPreferenceChangeListener>()

        override fun getAll(): MutableMap<String, *> = synchronized(lock) { values.toMutableMap() }

        override fun getString(key: String?, defValue: String?): String? = synchronized(lock) {
            if (key != null && values.containsKey(key)) values[key] as? String else defValue
        }

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = synchronized(lock) {
            if (key != null && values.containsKey(key)) {
                (values[key] as? Set<String>)?.toMutableSet()
            } else {
                defValues?.toMutableSet()
            }
        }

        override fun getInt(key: String?, defValue: Int): Int = synchronized(lock) {
            (values[key] as? Int) ?: defValue
        }

        override fun getLong(key: String?, defValue: Long): Long = synchronized(lock) {
            (values[key] as? Long) ?: defValue
        }

        override fun getFloat(key: String?, defValue: Float): Float = synchronized(lock) {
            (values[key] as? Float) ?: defValue
        }

        override fun getBoolean(key: String?, defValue: Boolean): Boolean = synchronized(lock) {
            (values[key] as? Boolean) ?: defValue
        }

        override fun contains(key: String?): Boolean = synchronized(lock) {
            key != null && values.containsKey(key)
        }

        override fun edit(): SharedPreferences.Editor = InMemoryEditor()

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
            if (listener != null) listeners.add(listener)
        }

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
            if (listener != null) listeners.remove(listener)
        }

        private inner class InMemoryEditor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearRequested = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = values?.toSet()
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }

            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = REMOVE_SENTINEL
            }

            override fun clear(): SharedPreferences.Editor = apply {
                clearRequested = true
            }

            override fun commit(): Boolean {
                val changedKeys = synchronized(lock) {
                    val modified = mutableListOf<String>()
                    if (clearRequested) {
                        modified.addAll(values.keys)
                        values.clear()
                    }
                    for ((k, v) in pending) {
                        if (v === REMOVE_SENTINEL || v == null) {
                            if (values.containsKey(k)) {
                                values.remove(k)
                                modified.add(k)
                            }
                        } else {
                            values[k] = v
                            modified.add(k)
                        }
                    }
                    modified.distinct()
                }
                for (key in changedKeys) {
                    for (listener in listeners) {
                        listener.onSharedPreferenceChanged(this@InMemorySharedPreferences, key)
                    }
                }
                return true
            }

            override fun apply() {
                commit()
            }
        }

        private companion object {
            val REMOVE_SENTINEL = Any()
        }
    }
}
