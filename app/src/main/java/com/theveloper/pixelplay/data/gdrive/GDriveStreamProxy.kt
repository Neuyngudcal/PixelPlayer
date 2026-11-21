package com.theveloper.pixelplay.data.gdrive

import android.net.Uri
import com.theveloper.pixelplay.data.stream.CloudStreamSecurity
import com.theveloper.pixelplay.data.stream.LocalProxyServerController
import com.theveloper.pixelplay.data.stream.respondStreamHead
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
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
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local HTTP proxy server for streaming Google Drive audio.
 *
 * Resolves `gdrive://{fileId}` URIs by proxying requests to the Drive REST API
 * with the required Authorization header. Follows the same architectural pattern
 * as [NeteaseStreamProxy] and [TelegramStreamProxy] using Ktor CIO.
 */
@Singleton
class GDriveStreamProxy @Inject constructor(
    private val repository: GDriveRepository,
    private val okHttpClient: OkHttpClient
) {
    private companion object {
        val ALLOWED_REMOTE_HOST_SUFFIXES = setOf(
            "googleapis.com",
            "googleusercontent.com"
        )
    }

    private val serverController by lazy {
        LocalProxyServerController(proxyTag = "GDriveStreamProxy", serverFactory = ::createServer)
    }

    private val actualPort: Int
        get() = serverController.actualPort

    fun isReady(): Boolean = serverController.isReady()

    fun startIfNeeded() = serverController.startIfNeeded()

    suspend fun awaitReady(timeoutMs: Long = 10_000L): Boolean =
        serverController.awaitReady(timeoutMs)

    suspend fun ensureReady(timeoutMs: Long = 10_000L): Boolean =
        serverController.ensureReady(timeoutMs)

    fun getProxyUrl(fileId: String): String {
        val port = actualPort
        if (port == 0) {
            Timber.w("GDriveStreamProxy: getProxyUrl called but actualPort is 0")
            return ""
        }
        if (!CloudStreamSecurity.validateGDriveFileId(fileId)) {
            Timber.w("GDriveStreamProxy: getProxyUrl rejected invalid fileId")
            return ""
        }
        return "http://127.0.0.1:$port/gdrive/$fileId"
    }

    /**
     * Parse a `gdrive://` URI and return the proxy URL.
     * Returns null if the URI is not a valid GDrive URI.
     */
    fun resolveGDriveUri(uriString: String): String? {
        val uri = Uri.parse(uriString)
        if (uri.scheme != "gdrive") return null
        val fileId = uri.host ?: return null
        if (!CloudStreamSecurity.validateGDriveFileId(fileId)) return null
        return getProxyUrl(fileId)
    }

    fun start() = serverController.start()

    fun stop() = serverController.stop()

    private fun createServer(port: Int): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
        return embeddedServer(CIO, port = port, host = "127.0.0.1") {
            routing {
                get("/gdrive/{fileId}") {
                    handleGDriveRequest(call, isHead = false)
                }
                head("/gdrive/{fileId}") {
                    handleGDriveRequest(call, isHead = true)
                }
            }
        }
    }

    private suspend fun handleGDriveRequest(call: ApplicationCall, isHead: Boolean) {
        val fileId = call.parameters["fileId"]
        if (fileId.isNullOrBlank() || !CloudStreamSecurity.validateGDriveFileId(fileId)) {
            call.respond(HttpStatusCode.BadRequest, "Invalid File ID")
            return
        }

        try {
            val rangeValidation = CloudStreamSecurity.validateRangeHeader(call.request.headers["Range"])
            if (!rangeValidation.isValid) {
                call.respond(HttpStatusCode(416, "Range Not Satisfiable"), "Invalid range header")
                return
            }

            val streamUrl = repository.getStreamUrl(fileId)
            val authHeader = repository.getAuthHeader()
            if (authHeader.isBlank() || authHeader == "Bearer ") {
                call.respond(HttpStatusCode.Unauthorized, "No auth token")
                return
            }

            var responseResult = executeValidatedGDriveRequest(
                streamUrl = streamUrl,
                authHeader = authHeader,
                normalizedRangeHeader = rangeValidation.normalizedHeader
            )

            // If 401, try refreshing the token and retry once
            if (responseResult is CloudStreamSecurity.ValidatedRequestResult.Success &&
                responseResult.response.code == 401
            ) {
                responseResult.response.close()
                Timber.d("GDriveStreamProxy: 401 received, refreshing token...")
                val refreshResult = repository.refreshAccessToken()
                if (refreshResult.isSuccess) {
                    val newAuthHeader = repository.getAuthHeader()
                    if (newAuthHeader.isBlank() || newAuthHeader == "Bearer ") {
                        call.respond(HttpStatusCode.Unauthorized, "Token refresh failed")
                        return
                    }
                    responseResult = executeValidatedGDriveRequest(
                        streamUrl = streamUrl,
                        authHeader = newAuthHeader,
                        normalizedRangeHeader = rangeValidation.normalizedHeader
                    )
                } else {
                    call.respond(HttpStatusCode.Unauthorized, "Token refresh failed")
                    return
                }
            }

            val upstreamResponse: Response = when (responseResult) {
                is CloudStreamSecurity.ValidatedRequestResult.Rejected -> {
                    Timber.w("GDriveStreamProxy rejected stream URL: ${responseResult.reason}")
                    call.respond(HttpStatusCode.BadGateway, "Rejected upstream stream URL")
                    return
                }
                is CloudStreamSecurity.ValidatedRequestResult.Success -> responseResult.response
            }

            upstreamResponse.use { upstream ->
                if (upstream.code != 200 && upstream.code != 206) {
                    call.respond(
                        CloudStreamSecurity.mapUpstreamStatusToProxyStatus(upstream.code),
                        "Upstream stream request failed"
                    )
                    return
                }

                val contentTypeHeader = upstream.header("Content-Type")
                if (!CloudStreamSecurity.isSupportedAudioContentType(contentTypeHeader)) {
                    call.respond(HttpStatusCode.BadGateway, "Unsupported stream content type")
                    return
                }

                val contentLength = upstream.header("Content-Length")
                if (!CloudStreamSecurity.isAcceptableContentLength(contentLength)) {
                    call.respond(HttpStatusCode(413, "Payload Too Large"), "Stream content too large")
                    return
                }

                val contentRange = upstream.header("Content-Range")
                val acceptRanges = upstream.header("Accept-Ranges")
                val responseContentType = contentTypeHeader
                    ?.substringBefore(';')
                    ?.trim()
                    ?.let { raw -> runCatching { ContentType.parse(raw) }.getOrNull() }
                    ?: ContentType.Audio.Any

                val statusCode = if (upstream.code == 206) {
                    HttpStatusCode.PartialContent
                } else {
                    HttpStatusCode.OK
                }
                call.response.header("Accept-Ranges", acceptRanges ?: "bytes")
                contentRange?.let { call.response.header("Content-Range", it) }
                if (isHead) {
                    call.respondStreamHead(
                        statusCode = statusCode,
                        contentType = responseContentType,
                        contentLengthBytes = contentLength?.toLongOrNull()
                    )
                    return
                }
                contentLength?.let { call.response.header("Content-Length", it) }

                call.response.status(statusCode)
                val body = upstream.body
                call.respondBytesWriter(contentType = responseContentType) {
                    withContext(Dispatchers.IO) {
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                writeFully(buffer, 0, bytesRead)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            val msg = e.toString()
            if (msg.contains("ChannelWriteException") ||
                msg.contains("ClosedChannelException") ||
                msg.contains("Broken pipe") ||
                msg.contains("JobCancellationException")
            ) {
                // Client disconnected, normal behavior
            } else {
                Timber.e(e, "Error streaming GDrive file $fileId")
            }
        }
    }

    private suspend fun executeValidatedGDriveRequest(
        streamUrl: String,
        authHeader: String,
        normalizedRangeHeader: String?
    ): CloudStreamSecurity.ValidatedRequestResult = withContext(Dispatchers.IO) {
        CloudStreamSecurity.executeWithValidatedRedirects(
            client = okHttpClient,
            initialUrl = streamUrl,
            allowedHostSuffixes = ALLOWED_REMOTE_HOST_SUFFIXES,
            allowHttpForAllowedHosts = false
        ) { targetUrl, isInitialHop ->
            Request.Builder()
                .url(targetUrl)
                .apply {
                    if (isInitialHop) {
                        header("Authorization", authHeader)
                    }
                    normalizedRangeHeader?.let { header("Range", it) }
                }
                .build()
        }
    }
}
