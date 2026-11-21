package com.theveloper.pixelplay.data.telegram

import android.content.Context
import com.theveloper.pixelplay.data.database.TelegramDao
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class TelegramCacheAndStreamTest {

    @Test
    fun `TelegramCacheManager protects pinned and crossfade files and cancels re-activated evictions`() = runBlocking {
        val context = mockk<Context>(relaxed = true)
        val clientManager = mockk<TelegramClientManager>(relaxed = true)
        coEvery { clientManager.sendRequest<TdApi.Ok>(any<TdApi.DeleteFile>()) } returns TdApi.Ok()

        val cacheManager = TelegramCacheManager(context, clientManager).apply {
            evictionGraceDelayMs = 60L
        }

        // File 1 is crossfade secondary active; File 2 is explicitly pinned by an active stream
        cacheManager.pinFile(2)
        cacheManager.setActivePlaybackFiles(primaryFileId = 10, secondaryFileId = 1)
        cacheManager.setActivePlayback(2)
        cacheManager.setActivePlayback(3)
        cacheManager.setActivePlayback(4)
        cacheManager.setActivePlayback(5)
        cacheManager.setActivePlayback(6)
        // Adding 7 & 8 pushes oldest unprotected files past HISTORY_CACHE_LIMIT (5)
        cacheManager.setActivePlayback(7)
        // Re-activate 3 within the grace window after it becomes eligible for eviction
        cacheManager.setActivePlayback(8)
        cacheManager.setActivePlayback(3)

        delay(180L)

        // File 1 (secondaryActiveFileId), File 2 (pinned), and File 3 (re-activated) must NOT be deleted
        coVerify(exactly = 0) {
            clientManager.sendRequest<TdApi.Ok>(match<TdApi.DeleteFile> { it.fileId == 1 || it.fileId == 2 || it.fileId == 3 })
        }
        // Unprotected evicted file 10 MUST be deleted
        coVerify(exactly = 1) {
            clientManager.sendRequest<TdApi.Ok>(match<TdApi.DeleteFile> { it.fileId == 10 })
        }

        cacheManager.unpinFile(2)
    }

    @Test
    fun `TelegramRepository forwards offset in downloadFile and GetFileDownloadedPrefixSize and eliminates update race`() = runBlocking {
        val updatesFlow = MutableSharedFlow<TdApi.Object>(extraBufferCapacity = 16)
        val authFlow = MutableStateFlow<TdApi.AuthorizationState?>(TdApi.AuthorizationStateReady())
        val clientManager = mockk<TelegramClientManager>(relaxed = true)
        val dao = mockk<TelegramDao>(relaxed = true)
        val playlistPrefs = mockk<PlaylistPreferencesRepository>(relaxed = true)

        every { clientManager.updates } returns updatesFlow
        every { clientManager.authorizationState } returns authFlow

        val tempFile = Files.createTempFile("tg_repo_test", ".mp3").toFile().apply {
            deleteOnExit()
            writeBytes(ByteArray(2 * 1024 * 1024) { it.toByte() })
        }

        val largeDownloadingFile = TdApi.File().apply {
            id = 500
            size = 2L * 1024 * 1024
            expectedSize = 2L * 1024 * 1024
            local = TdApi.LocalFile().apply {
                path = tempFile.absolutePath
                isDownloadingCompleted = false
                canBeDownloaded = true
            }
        }
        val largeCompletedFile = TdApi.File().apply {
            id = 500
            size = 2L * 1024 * 1024
            expectedSize = 2L * 1024 * 1024
            local = TdApi.LocalFile().apply {
                path = tempFile.absolutePath
                isDownloadingCompleted = true
                canBeDownloaded = true
                downloadedPrefixSize = size
            }
        }

        val downloadCalls = AtomicInteger(0)
        coEvery { clientManager.sendRequest<TdApi.File>(match<TdApi.GetFile> { it.fileId == 500 }) } returns largeDownloadingFile
        coEvery { clientManager.sendRequest<TdApi.File>(match<TdApi.DownloadFile> { it.fileId == 500 && it.synchronous }) } throws IllegalStateException("Expected async path for >1MB file")
        coEvery { clientManager.sendRequest<TdApi.File>(match<TdApi.DownloadFile> { it.fileId == 500 && !it.synchronous }) } coAnswers {
            downloadCalls.incrementAndGet()
            // Emit UpdateFile immediately during DownloadFile execution to verify collector was already subscribed
            updatesFlow.emit(TdApi.UpdateFile(largeCompletedFile))
            largeDownloadingFile
        }
        coEvery {
            clientManager.sendRequest<TdApi.File>(match<TdApi.DownloadFile> { it.fileId == 99 && it.offset == 4096L && it.limit == 8192L })
        } returns TdApi.File().apply { id = 99 }
        coEvery {
            clientManager.sendRequest<TdApi.FileDownloadedPrefixSize>(match<TdApi.GetFileDownloadedPrefixSize> { it.fileId == 99 && it.offset == 4096L })
        } returns TdApi.FileDownloadedPrefixSize(16384L)

        val repo = TelegramRepository(clientManager, dao, playlistPrefs)

        val offsetFile = repo.downloadFile(fileId = 99, priority = 2, offset = 4096L, limit = 8192L)
        assertEquals(99, offsetFile?.id)
        assertEquals(16384L, repo.getFileDownloadedPrefixSize(99, 4096L))

        val concurrentResults = (1..5).map {
            async { repo.downloadFileAwait(500) }
        }.awaitAll()

        assertTrue(concurrentResults.all { it == tempFile.absolutePath })
        assertEquals(1, downloadCalls.get())
    }

    @Test
    fun `TelegramStreamProxy supports HEAD and offset-aware mid-file Range streaming`() = runBlocking {
        val repo = mockk<TelegramRepository>(relaxed = true)
        every { repo.isReady() } returns true

        val payload = ByteArray(4096) { idx -> (idx % 251).toByte() }
        val tempFile: File = Files.createTempFile("tg_stream_proxy", ".flac").toFile().apply {
            deleteOnExit()
            writeBytes(payload)
        }

        val initialFileInfo = TdApi.File().apply {
            id = 42
            size = payload.size.toLong()
            expectedSize = payload.size.toLong()
            local = TdApi.LocalFile().apply {
                path = tempFile.absolutePath
                isDownloadingCompleted = false
                canBeDownloaded = true
                // Contiguous prefix from byte 0 is 0L, simulating mid-file seek buffering at offset 1024
                downloadedPrefixSize = 0L
            }
        }

        coEvery { repo.downloadFile(42, 1, offset = 1024L, limit = 0L) } returns initialFileInfo
        coEvery { repo.downloadFile(42, 1, offset = 0L, limit = 0L) } returns initialFileInfo
        coEvery { repo.getFile(42) } returns initialFileInfo
        coEvery { repo.getFileDownloadedPrefixSize(42, 1024L) } returns 1024L

        val cacheManager = mockk<TelegramCacheManager>(relaxed = true)
        val proxy = TelegramStreamProxy(repo, cacheManager)
        try {
            assertTrue(proxy.ensureReady(5_000L))
            val url = proxy.getProxyUrl(42, payload.size.toLong())
            assertTrue(url.isNotEmpty())

            val http = OkHttpClient()

            // Verify HEAD route responds with metadata headers and empty body
            val headResponse = http.newCall(Request.Builder().head().url(url).build()).execute()
            headResponse.use { resp ->
                assertEquals(200, resp.code)
                assertEquals("bytes", resp.header("Accept-Ranges"))
                assertEquals(payload.size.toString(), resp.header("Content-Length"))
                assertEquals(0, resp.body.bytes().size)
            }

            // Verify GET with Range forwards offset=1024 to TDLib and reads via GetFileDownloadedPrefixSize(42, 1024)
            val rangeResponse = http.newCall(
                Request.Builder().get().url(url).header("Range", "bytes=1024-2047").build()
            ).execute()
            rangeResponse.use { resp ->
                assertEquals(206, resp.code)
                assertEquals("bytes 1024-2047/${payload.size}", resp.header("Content-Range"))
                assertEquals("1024", resp.header("Content-Length"))
                assertArrayEquals(payload.copyOfRange(1024, 2048), resp.body.bytes())
            }

            coVerify(atLeast = 1) { repo.downloadFile(42, 1, offset = 1024L, limit = 0L) }
            coVerify(atLeast = 1) { repo.getFileDownloadedPrefixSize(42, 1024L) }
            verify(atLeast = 1) { cacheManager.pinFile(42) }
            verify(atLeast = 1) { cacheManager.unpinFile(42) }
        } finally {
            proxy.stop()
        }
    }
}
