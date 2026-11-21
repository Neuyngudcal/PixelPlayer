package com.theveloper.pixelplay.data.qqmusic

import android.content.Context
import android.content.SharedPreferences
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.QqMusicDao
import com.theveloper.pixelplay.data.network.qqmusic.QqMusicApiService
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.security.SecureSharedPreferencesFactory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QqMusicRepositorySongUrlTest {

    private fun createRepository(api: QqMusicApiService): QqMusicRepository {
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getString(any(), any()) } returns null
        mockkObject(SecureSharedPreferencesFactory)
        every { SecureSharedPreferencesFactory.create(any(), any()) } returns prefs

        val context = mockk<Context>(relaxed = true)
        val dao = mockk<QqMusicDao>(relaxed = true)
        val musicDao = mockk<MusicDao>(relaxed = true)
        val playlistPrefs = mockk<PlaylistPreferencesRepository>(relaxed = true)
        return QqMusicRepository(api, dao, musicDao, playlistPrefs, context)
    }

    @Test
    fun `getSongUrl coalesces concurrent callers within 1500ms without throttling`() = runBlocking {
        try {
            val api = mockk<QqMusicApiService>(relaxed = true)
            every { api.hasLogin() } returns true
            coEvery { api.getSongDownloadUrl("001abc", filename = null) } coAnswers {
                delay(120L)
                """{"req_0":{"data":{"midurlinfo":[{"purl":"C400001media.m4a?vkey=123"}]}}}"""
            }
            coEvery { api.getSongDownloadUrl("001abc", filename = "M500001media.mp3") } returns
                """{"req_0":{"data":{"midurlinfo":[{"purl":"M500001media.mp3?vkey=456"}]}}}"""

            val repo = createRepository(api)

            val warmupCall = async { repo.getSongUrl("001abc") }
            delay(30L)
            val proxyCall = async { repo.getSongUrl("001abc") }

            val r1 = warmupCall.await()
            val r2 = proxyCall.await()

            assertTrue(r1.isSuccess)
            assertTrue(r2.isSuccess)
            assertEquals("https://ws.stream.qqmusic.qq.com/M500001media.mp3?vkey=456", r1.getOrNull())
            assertEquals("https://ws.stream.qqmusic.qq.com/M500001media.mp3?vkey=456", r2.getOrNull())
            coVerify(exactly = 1) { api.getSongDownloadUrl("001abc", filename = null) }
            coVerify(exactly = 1) { api.getSongDownloadUrl("001abc", filename = "M500001media.mp3") }
            assertEquals(0, repo.songUrlCoalescer.inFlightCount())
        } finally {
            unmockkObject(SecureSharedPreferencesFactory)
        }
    }

    @Test
    fun `getSongUrl cleans up inFlight entry on cancellation`() = runBlocking {
        try {
            val api = mockk<QqMusicApiService>(relaxed = true)
            every { api.hasLogin() } returns true
            val requestStarted = CompletableDeferred<Unit>()
            val gate = CompletableDeferred<Unit>()
            coEvery { api.getSongDownloadUrl("002cancel", filename = null) } coAnswers {
                requestStarted.complete(Unit)
                gate.await()
                """{"req_0":{"data":{"midurlinfo":[{"purl":""}]}}}"""
            }

            val repo = createRepository(api)
            val job = async { repo.getSongUrl("002cancel") }
            requestStarted.await()
            assertEquals(1, repo.songUrlCoalescer.inFlightCount())

            job.cancel(CancellationException("proxy disconnected"))
            runCatching { job.await() }

            assertEquals(0, repo.songUrlCoalescer.inFlightCount())
        } finally {
            unmockkObject(SecureSharedPreferencesFactory)
        }
    }
}
