package com.theveloper.pixelplay.data.telegram

import com.theveloper.pixelplay.data.stream.CloudStreamSecurity
import com.theveloper.pixelplay.data.stream.LocalProxyServerController
import com.theveloper.pixelplay.data.stream.respondStreamHead
import com.theveloper.pixelplay.utils.LogUtils
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class TelegramStreamProxy @Inject constructor(
    private val telegramRepository: TelegramRepository,
    private val telegramCacheManager: TelegramCacheManager
) {
    companion object {
        private const val MAX_STREAM_STALL_MS = 25_000L
        private const val PATH_WAIT_MAX_ATTEMPTS = 50
        private const val FILE_EXISTS_WAIT_MAX_ATTEMPTS = 300
    }

    private val serverController = LocalProxyServerController(
        proxyTag = "TelegramStreamProxy",
        serverFactory = ::createServer
    )

    private val actualPort: Int
        get() = serverController.actualPort

    private fun createServer(port: Int): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
        return embeddedServer(CIO, port = port, host = "127.0.0.1") {
            routing {
                get("/stream/{fileId}") {
                    handleTelegramStreamRequest(call, isHead = false)
                }
                head("/stream/{fileId}") {
                    handleTelegramStreamRequest(call, isHead = true)
                }
            }
        }
    }

    private suspend fun handleTelegramStreamRequest(call: ApplicationCall, isHead: Boolean) {
        val fileId = call.parameters["fileId"]?.toIntOrNull()
        if (fileId == null || !CloudStreamSecurity.validateTelegramFileId(fileId)) {
            call.respond(HttpStatusCode.BadRequest, "Invalid File ID")
            return
        }

        // Validate Range header BEFORE triggering TDLib downloads or allocating file offsets.
        val rangeValidation = CloudStreamSecurity.validateRangeHeader(call.request.headers["Range"])
        if (!rangeValidation.isValid) {
            call.respond(HttpStatusCode(416, "Range Not Satisfiable"), "Invalid range header")
            return
        }

        // Optional hint from caller; only trusted within sane limits.
        val knownSize = call.parameters["size"]?.toLongOrNull()?.takeIf {
            it in 1..CloudStreamSecurity.MAX_STREAM_CONTENT_LENGTH_BYTES
        } ?: 0L

        LogUtils.d("StreamProxy", "Request (${if (isHead) "HEAD" else "GET"}) for fileId: $fileId")

        // Wait for TDLib to be ready before attempting download
        if (!telegramRepository.isReady()) {
            LogUtils.w("StreamProxy", "TDLib not ready, waiting...")
            val ready = telegramRepository.awaitReady(10_000L)
            if (!ready) {
                LogUtils.e("StreamProxy", null, "TDLib not ready after timeout")
                call.respond(HttpStatusCode.ServiceUnavailable, "Telegram client not ready")
                return
            }
            LogUtils.d("StreamProxy", "TDLib ready, proceeding with request")
        }

        telegramCacheManager.pinFile(fileId)
        try {
            val initialDownloadOffset = computeInitialDownloadOffset(rangeValidation, knownSize)

            // 1. Ensure download is started/prioritized from the requested seek offset
            var fileInfo = telegramRepository.downloadFile(fileId, priority = 1, offset = initialDownloadOffset)

            // 2. Wait for path to be assigned (TDLib might take a moment to allocate the file path)
            var pathWaitCount = 0
            while (fileInfo?.local?.path.isNullOrEmpty() && pathWaitCount < PATH_WAIT_MAX_ATTEMPTS) {
                delay(50)
                fileInfo = telegramRepository.getFile(fileId)
                pathWaitCount++
            }

            if (fileInfo?.local?.path.isNullOrEmpty()) {
                LogUtils.e("StreamProxy", null, "downloadFile returned null/empty path for fileId: $fileId after waiting")
                call.respond(HttpStatusCode.InternalServerError, "Could not get file path")
                return
            }

            val path = fileInfo.local.path
            var expectedSize = fileInfo.expectedSize
            if (knownSize > 0 && expectedSize <= 0L) {
                expectedSize = knownSize
            }

            val file = File(path)

            // Wait for file to be created by TDLib (needed for GET, or for HEAD when expectedSize is unknown)
            if (!isHead || expectedSize <= 0L) {
                var waitCount = 0
                while (!file.exists() && waitCount < FILE_EXISTS_WAIT_MAX_ATTEMPTS) {
                    delay(50)
                    waitCount++
                    if (waitCount % 20 == 0) {
                        LogUtils.d("StreamProxy", "Waiting for file creation: $path ($waitCount/$FILE_EXISTS_WAIT_MAX_ATTEMPTS)")
                    }
                }

                if (!file.exists()) {
                    LogUtils.e("StreamProxy", null, "File not created by TDLib after 15s timeout: $path")
                    call.respond(HttpStatusCode.InternalServerError, "File not created by TDLib")
                    return
                }

                if (!file.isFile) {
                    call.respond(HttpStatusCode.NotFound, "Invalid file")
                    return
                }

                if (expectedSize <= 0L) {
                    expectedSize = file.length()
                }
            }

            if (expectedSize > CloudStreamSecurity.MAX_STREAM_CONTENT_LENGTH_BYTES) {
                call.respond(HttpStatusCode(413, "Payload Too Large"), "File too large for proxy streaming")
                return
            }

            val isRangeRequest = rangeValidation.normalizedHeader != null
            var start = 0L
            var end = if (expectedSize > 0) expectedSize - 1 else Long.MAX_VALUE - 1

            if (isRangeRequest) {
                if (rangeValidation.isSuffixRange) {
                    if (expectedSize <= 0L) {
                        call.respond(HttpStatusCode(416, "Range Not Satisfiable"), "Suffix range requires known size")
                        return
                    }
                    val suffixLength = rangeValidation.endInclusive ?: 0L
                    if (suffixLength <= 0L) {
                        call.respond(HttpStatusCode(416, "Range Not Satisfiable"), "Invalid suffix range")
                        return
                    }
                    start = (expectedSize - suffixLength).coerceAtLeast(0L)
                    end = expectedSize - 1
                } else {
                    start = rangeValidation.startInclusive ?: 0L
                    end = rangeValidation.endInclusive ?: if (expectedSize > 0) expectedSize - 1 else Long.MAX_VALUE - 1
                }
            }

            if (expectedSize > 0 && end >= expectedSize) {
                end = expectedSize - 1
            }

            if (expectedSize > 0 && start >= expectedSize) {
                call.respond(HttpStatusCode(416, "Range Not Satisfiable"))
                return
            }

            if (start > end || start < 0 || end < 0) {
                call.respond(HttpStatusCode(416, "Range Not Satisfiable"))
                return
            }

            val contentLength = end - start + 1
            val statusCode = if (isRangeRequest) HttpStatusCode.PartialContent else HttpStatusCode.OK
            val resolvedHeadLength = when {
                isRangeRequest -> contentLength
                expectedSize > 0L -> expectedSize
                else -> null
            }

            call.response.header("Accept-Ranges", "bytes")
            if (isRangeRequest && expectedSize > 0) {
                call.response.header("Content-Range", "bytes $start-$end/$expectedSize")
            }

            if (isHead) {
                call.respondStreamHead(
                    statusCode = statusCode,
                    contentType = ContentType.Audio.Any,
                    contentLengthBytes = resolvedHeadLength
                )
                return
            }

            if (isRangeRequest) {
                call.response.header("Content-Length", contentLength.toString())
            } else if (expectedSize > 0) {
                call.response.header("Content-Length", expectedSize.toString())
            }

            call.response.status(statusCode)

            // Stream the file
            call.respondBytesWriter(contentType = ContentType.Audio.Any) {
                withContext(Dispatchers.IO) {
                    val raf = RandomAccessFile(file, "r")
                    try {
                        var currentPos = start
                        val buffer = ByteArray(64 * 1024)
                        var stallDelayMs = 50L
                        val maxStallDelayMs = 400L
                        var stallElapsedMs = 0L

                        raf.seek(currentPos)

                        var cachedAvailableUntilPos = if (fileInfo.local.isDownloadingCompleted) {
                            maxOf(fileInfo.local.downloadedPrefixSize, expectedSize, file.length())
                        } else {
                            fileInfo.local.downloadedPrefixSize
                        }

                        while (isActive) {
                            val remaining = end - currentPos + 1
                            if (remaining <= 0) break

                            // Refresh available bytes from the current stream offset when needed
                            if (currentPos >= cachedAvailableUntilPos) {
                                val updatedInfo = telegramRepository.getFile(fileId)
                                val prefixFromOffset = telegramRepository.getFileDownloadedPrefixSize(fileId, currentPos)
                                val contiguousFromZero = updatedInfo?.local?.downloadedPrefixSize ?: 0L
                                val completedSize = if (updatedInfo?.local?.isDownloadingCompleted == true) {
                                    maxOf(contiguousFromZero, expectedSize, file.length())
                                } else {
                                    0L
                                }
                                cachedAvailableUntilPos = maxOf(
                                    currentPos + prefixFromOffset,
                                    contiguousFromZero,
                                    completedSize
                                )

                                if (currentPos >= cachedAvailableUntilPos) {
                                    if (updatedInfo?.local?.isDownloadingCompleted == true) {
                                        break
                                    }

                                    if (updatedInfo?.local?.canBeDownloaded == false) {
                                        throw IOException("TDLib download cancelled or unavailable for fileId=$fileId")
                                    }

                                    if (stallElapsedMs >= MAX_STREAM_STALL_MS) {
                                        throw IOException("Telegram stream stalled after ${MAX_STREAM_STALL_MS}ms for fileId=$fileId at offset=$currentPos")
                                    }

                                    delay(stallDelayMs)
                                    stallElapsedMs += stallDelayMs
                                    stallDelayMs = (stallDelayMs * 2).coerceAtMost(maxStallDelayMs)
                                    continue
                                } else {
                                    stallDelayMs = 50L
                                    stallElapsedMs = 0L
                                }
                            }

                            val remainingValid = cachedAvailableUntilPos - currentPos
                            val toRead = min(buffer.size.toLong(), min(remaining, remainingValid)).toInt()
                            if (toRead <= 0) {
                                delay(10)
                                continue
                            }

                            val read = raf.read(buffer, 0, toRead)
                            if (read > 0) {
                                writeFully(buffer, 0, read)
                                currentPos += read
                                stallDelayMs = 50L
                                stallElapsedMs = 0L
                            } else {
                                if (stallElapsedMs >= MAX_STREAM_STALL_MS) {
                                    throw IOException("Telegram file read stalled for fileId=$fileId at offset=$currentPos")
                                }
                                delay(25)
                                stallElapsedMs += 25L
                            }
                        }
                    } catch (e: Exception) {
                        val msg = e.toString()
                        if (msg.contains("ChannelWriteException") ||
                            msg.contains("ClosedChannelException") ||
                            msg.contains("Broken pipe") ||
                            msg.contains("WriteTimeoutException") ||
                            msg.contains("JobCancellationException")) {
                            // Client disconnected, normal behavior
                        } else {
                            LogUtils.e("StreamProxy", e, "Streaming error")
                            throw e
                        }
                    } finally {
                        raf.close()
                    }
                }
            }
        } finally {
            telegramCacheManager.unpinFile(fileId)
        }
    }

    private fun computeInitialDownloadOffset(
        rangeValidation: CloudStreamSecurity.RangeHeaderValidation,
        knownSize: Long
    ): Long {
        if (rangeValidation.normalizedHeader == null) return 0L
        return if (rangeValidation.isSuffixRange) {
            val suffixLength = rangeValidation.endInclusive ?: 0L
            if (knownSize > 0L && suffixLength > 0L) {
                (knownSize - suffixLength).coerceAtLeast(0L)
            } else {
                0L
            }
        } else {
            (rangeValidation.startInclusive ?: 0L).coerceAtLeast(0L)
        }
    }

    fun startIfNeeded() {
        serverController.startIfNeeded()
    }

    fun start() {
        serverController.start()
    }

    fun stop() {
        serverController.stop()
    }

    fun getProxyUrl(fileId: Int, knownSize: Long = 0): String {
        val port = actualPort
        if (port == 0) {
            LogUtils.w("StreamProxy", "getProxyUrl called but actualPort is 0")
            return ""
        }
        if (!CloudStreamSecurity.validateTelegramFileId(fileId)) {
            LogUtils.w("StreamProxy", "getProxyUrl rejected invalid fileId: $fileId")
            return ""
        }
        val safeKnownSize = knownSize.takeIf { it in 0..CloudStreamSecurity.MAX_STREAM_CONTENT_LENGTH_BYTES } ?: 0L
        val url = "http://127.0.0.1:$port/stream/$fileId?size=$safeKnownSize"
        LogUtils.d("StreamProxy", "Generated Proxy URL: $url")
        return url
    }

    /**
     * Quick check if the proxy server is ready (port is bound).
     */
    fun isReady(): Boolean = serverController.isReady()

    /**
     * Suspends until the proxy server is ready (port bound).
     * @param timeoutMs Maximum time to wait
     * @return true if ready, false if timed out
     */
    suspend fun awaitReady(timeoutMs: Long = 10_000L): Boolean =
        serverController.awaitReady(timeoutMs)

    suspend fun ensureReady(timeoutMs: Long = 10_000L): Boolean =
        serverController.ensureReady(timeoutMs)
}
