package com.theveloper.pixelplay.data.security

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SecureSharedPreferencesFactoryTest {

    @Test
    fun `create migrates legacy plain prefs into encrypted prefs and deletes plain file`() {
        val plainPrefs = SecureSharedPreferencesFactory.InMemorySharedPreferences().apply {
            edit()
                .putString("cookie", "MUSIC_U=secret_token")
                .putLong("uid", 12345L)
                .putBoolean("logged_in", true)
                .commit()
        }
        val encryptedPrefs = SecureSharedPreferencesFactory.InMemorySharedPreferences()

        val context = mockk<Context>(relaxed = true)
        every { context.getSharedPreferences("netease_prefs_plain", Context.MODE_PRIVATE) } returns plainPrefs
        every { context.deleteSharedPreferences("netease_prefs_plain") } returns true

        val resolved = SecureSharedPreferencesFactory.create(context, "netease_prefs") {
            encryptedPrefs
        }

        assertSame(encryptedPrefs, resolved)
        assertEquals("MUSIC_U=secret_token", resolved.getString("cookie", null))
        assertEquals(12345L, resolved.getLong("uid", 0L))
        assertTrue(resolved.getBoolean("logged_in", false))
        assertTrue(plainPrefs.all.isEmpty())
        verify(exactly = 1) { context.deleteSharedPreferences("netease_prefs_plain") }
    }

    @Test
    fun `create retries once after deleting corrupted encrypted store`() {
        val plainPrefs = SecureSharedPreferencesFactory.InMemorySharedPreferences()
        val recoveredEncryptedPrefs = SecureSharedPreferencesFactory.InMemorySharedPreferences()

        val context = mockk<Context>(relaxed = true)
        every { context.getSharedPreferences("jellyfin_prefs_plain", Context.MODE_PRIVATE) } returns plainPrefs
        every { context.deleteSharedPreferences("jellyfin_prefs") } returns true

        var attempts = 0
        val resolved = SecureSharedPreferencesFactory.create(context, "jellyfin_prefs") {
            attempts++
            if (attempts == 1) {
                throw javax.crypto.AEADBadTagException("corrupted keyset")
            }
            recoveredEncryptedPrefs
        }

        assertEquals(2, attempts)
        assertSame(recoveredEncryptedPrefs, resolved)
        verify(exactly = 1) { context.deleteSharedPreferences("jellyfin_prefs") }
    }

    @Test
    fun `create fails closed with in-memory prefs when encryption repeatedly fails`() {
        val plainPrefs = SecureSharedPreferencesFactory.InMemorySharedPreferences().apply {
            edit().putString("token", "legacy_plain").commit()
        }

        val context = mockk<Context>(relaxed = true)
        every { context.getSharedPreferences("gdrive_prefs_plain", Context.MODE_PRIVATE) } returns plainPrefs
        every { context.deleteSharedPreferences(any()) } returns true

        val resolved = SecureSharedPreferencesFactory.create(context, "gdrive_prefs") {
            throw IllegalStateException("Keystore locked")
        }

        assertTrue(resolved is SecureSharedPreferencesFactory.InMemorySharedPreferences)
        assertNotSame(plainPrefs, resolved)
        assertFalse(resolved.contains("token"))
        resolved.edit().putString("session", "ephemeral").commit()
        assertEquals("ephemeral", resolved.getString("session", null))
        verify(exactly = 1) { context.deleteSharedPreferences("gdrive_prefs_plain") }
    }
}
