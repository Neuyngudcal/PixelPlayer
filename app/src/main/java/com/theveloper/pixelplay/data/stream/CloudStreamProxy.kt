package com.theveloper.pixelplay.data.stream

import android.net.Uri
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
import java.util.concurrent.ConcurrentHashMap

/**
 * Abstract base class for local HTTP proxy servers that stream cloud music audio.
 *
 * Subclasses define the route, ID type, validation, allowed hosts, and URL resolution.
 * The base class handles the full Ktor CIO server lifecycle, URL caching, and OkHttp
 * proxying with security checks via [CloudStreamSecurity].
 *
 * @param K The song identifier type (e.g. [String] for QQ Music songMid, [Long] for Netease songId)
 */
abstract class CloudStreamProxy<K : Any>(
    private val okHttpClient: OkHttpClient
) {
    // ─── Subclass Configuration ────────────────────────────────────────

    protected abstract val allowedHostSuffixes: Set<String>
    protected abstract val cacheExpirationMs: Long
    protected abstract val proxyTag: String

    /** Route path registered with Ktor, e.g. "/qqmusic/{songMid}" */
    protected abstract val routePath: String
    /** The parameter name inside the route path, e.g. "songMid" */
    protected abstract val routeParamName: String
    /** URI scheme this proxy handles, e.g. "qqmusic" or "netease" */
    protected abstract val uriScheme: String
    /** URL path prefix for proxy URLs, e.g. "/qqmusic" or "/netease" */
    protected abstract val routePrefix: String

    /** Parse the raw route parameter string into the typed ID, or null if invalid */
    protected abstract fun parseRouteParam(value: String): K?
    /** Validate whether the given ID is acceptable */
    protected abstract fun validateId(id: K): Boolean
    /** Convert the ID to a string for use in URLs */
    protected abstract fun formatIdForUrl(id: K): String
    /** Resolve the actual streaming URL for the given song ID */
    protected abstract suspend fun resolveStreamUrl(id: K): String?

    // ─── Server State ──────────────────────────────────────────────────

    private val serverController by lazy {
        LocalProxyServerController(proxyTag = proxyTag, serverFactory = ::createServer)
    }

    private val actualPort: Int
        get() = serverController.actualPort

    private val allowHttpForConfiguredHosts: Boolean
        get() = uriScheme == "navidrome" || uriScheme == "jellyfin"

    private val urlCache = ConcurrentHashMap<K, CachedUrl>()

    private data class CachedUrl(val url: String, val timestamp: Long, val expirationMs: Long) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > expirationMs
    }

    // ─── Public API ────────────────────────────────────────────────────

    fun isReady(): Boolean = serverController.isReady()

    fun startIfNeeded() = serverController.startIfNeeded()

    suspend fun awaitReady(timeoutMs: Long = 10_000L): Boolean =
        serverController.awaitReady(timeoutMs)

    suspend fun ensureReady(timeoutMs: Long = 10_000L): Boolean =
        serverController.ensureReady(timeoutMs)

    fun getProxyUrl(id: K): String {
        val port = actualPort
        if (port == 0) return ""
        if (!validateId(id)) return ""
        return "http://127.0.0.1:$port$routePrefix/${formatIdForUrl(id)}"
    }

    /**
     * Parse a cloud URI (e.g. "qqmusic://xxxx" or "netease://12345") and return
     * the local proxy URL. Returns null if the URI doesn't match this proxy's scheme.
     */
    fun resolveUri(uriString: String): String? {
        val uri = Uri.parse(uriString)
        if (uri.scheme != uriScheme) return null
        val rawId = extractIdFromUri(uri) ?: return null
        val id = parseRouteParam(rawId) ?: return null
        if (!validateId(id)) return null
        return getProxyUrl(id)
    }

    fun start() = serverController.start()

    fun stop() {
        serverController.stop()
        urlCache.clear()
    }

    // ─── Overridable Hooks ─────────────────────────────────────────────

    /** Extract the raw ID string from a parsed URI. Override for custom URI layouts. */
    protected open fun extractIdFromUri(uri: Uri): String? = uri.host

    // ─── Internal ──────────────────────────────────────────────────────

    protected suspend fun getOrFetchStreamUrl(id: K, forceRefresh: Boolean = false): String? {
        if (forceRefresh) {
            urlCache.remove(id)
        } else {
            urlCache[id]?.let { cached ->
                if (!cached.isExpired()) return cached.url
            }
        }
        return resolveStreamUrl(id)?.also { url ->
            urlCache[id] = CachedUrl(url, System.currentTimeMillis(), cacheExpirationMs)
        }
    }

    private fun createServer(port: Int): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
        return embeddedServer(CIO, port = port, host = "127.0.0.1") {
            routing {
                get(routePath) {
                    handleStreamRequest(call, isHead = false)
                }
                head(routePath) {
                    handleStreamRequest(call, isHead = true)
                }
            }
        }
    }

    private suspend fun handleStreamRequest(call: ApplicationCall, isHead: Boolean) {
        val rawParam = call.parameters[routeParamName]
        val id = rawParam?.let { parseRouteParam(it) }
        if (id == null || !validateId(id)) {
            call.respond(HttpStatusCode.BadRequest, "Invalid ID")
            return
        }

        try {
            val rangeValidation = CloudStreamSecurity.validateRangeHeader(
                call.request.headers["Range"]
            )
            if (!rangeValidation.isValid) {
                call.respond(
                    HttpStatusCode(416, "Range Not Satisfiable"),
                    "Invalid range header"
                )
                return
            }

            val initialUrl = getOrFetchStreamUrl(id)
            if (initialUrl.isNullOrBlank()) {
                call.respond(HttpStatusCode.NotFound, "No stream URL available")
                return
            }

            var currentUrl = initialUrl
            var responseResult = executeValidatedUpstreamRequest(
                streamUrl = currentUrl,
                normalizedRangeHeader = rangeValidation.normalizedHeader
            )

            // Invalidate expired/rejected cached URL on 401/403 and retry at most once with a fresh URL.
            if (responseResult is CloudStreamSecurity.ValidatedRequestResult.Success &&
                (responseResult.response.code == 401 || responseResult.response.code == 403)
            ) {
                responseResult.response.close()
                urlCache.remove(id)
                val refreshedUrl = getOrFetchStreamUrl(id, forceRefresh = true)
                if (!refreshedUrl.isNullOrBlank() && refreshedUrl != currentUrl) {
                    currentUrl = refreshedUrl
                    responseResult = executeValidatedUpstreamRequest(
                        streamUrl = currentUrl,
                        normalizedRangeHeader = rangeValidation.normalizedHeader
                    )
                } else {
                    val deniedCode = responseResult.response.code
                    call.respond(
                        CloudStreamSecurity.mapUpstreamStatusToProxyStatus(deniedCode),
                        "Upstream stream request failed"
                    )
                    return
                }
            }

            val upstreamResponse: Response = when (responseResult) {
                is CloudStreamSecurity.ValidatedRequestResult.Rejected -> {
                    Timber.w("$proxyTag rejected stream URL: ${responseResult.reason}")
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
                    call.respond(
                        HttpStatusCode.BadGateway,
                        "Unsupported stream content type"
                    )
                    return
                }

                val contentLength = upstream.header("Content-Length")
                if (!CloudStreamSecurity.isAcceptableContentLength(contentLength)) {
                    call.respond(
                        HttpStatusCode(413, "Payload Too Large"),
                        "Stream content too large"
                    )
                    return
                }

                val contentRange = upstream.header("Content-Range")
                val acceptRanges = upstream.header("Accept-Ranges")
                val responseContentType = contentTypeHeader
                    ?.substringBefore(';')
                    ?.trim()
                    ?.let { raw ->
                        runCatching { ContentType.parse(raw) }.getOrNull()
                    }
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
                Timber.w(e, "$proxyTag stream failed")
            }
        }
    }

    private suspend fun executeValidatedUpstreamRequest(
        streamUrl: String,
        normalizedRangeHeader: String?
    ): CloudStreamSecurity.ValidatedRequestResult = withContext(Dispatchers.IO) {
        CloudStreamSecurity.executeWithValidatedRedirects(
            client = okHttpClient,
            initialUrl = streamUrl,
            allowedHostSuffixes = allowedHostSuffixes,
            allowHttpForAllowedHosts = allowHttpForConfiguredHosts
        ) { targetUrl, _ ->
            Request.Builder()
                .url(targetUrl)
                .apply {
                    normalizedRangeHeader?.let { header("Range", it) }
                }
                .build()
        }
    }
}
