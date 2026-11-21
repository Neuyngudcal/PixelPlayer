package com.theveloper.pixelplay.data.stream

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.response.respond
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.net.ServerSocket

/**
 * Shared thread-safe single-flight lifecycle controller for local loopback Ktor CIO proxies.
 * Ensures concurrent [startIfNeeded] / [ensureReady] callers share one startup flight
 * without leaking server instances or cancelling an active startup.
 */
internal class LocalProxyServerController(
    private val proxyTag: String,
    private val serverFactory: (port: Int) -> EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
) {
    @Volatile
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    @Volatile
    var actualPort: Int = 0
        private set

    private val startMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var inFlightStart: Deferred<Boolean>? = null

    fun isReady(): Boolean = actualPort > 0 && server != null

    fun startIfNeeded() {
        if (isReady() || inFlightStart?.isActive == true) return
        scope.launch {
            getOrCreateStartDeferred()
        }
    }

    fun start() {
        stop()
        scope.launch {
            getOrCreateStartDeferred()
        }
    }

    suspend fun awaitReady(timeoutMs: Long = 10_000L): Boolean {
        if (isReady()) return true
        val startJob = inFlightStart ?: return isReady()
        return withTimeoutOrNull(timeoutMs) {
            startJob.await()
        } == true && isReady()
    }

    suspend fun ensureReady(timeoutMs: Long = 10_000L): Boolean {
        if (isReady()) return true
        val startJob = getOrCreateStartDeferred()
        return withTimeoutOrNull(timeoutMs) {
            startJob.await()
        } == true && isReady()
    }

    fun stop() {
        val currentServer = server
        val currentStart = inFlightStart
        inFlightStart = null
        server = null
        actualPort = 0
        currentStart?.cancel()
        scope.coroutineContext.cancelChildren()
        runCatching { currentServer?.stop(1000, 2000) }
        Timber.d("$proxyTag stopped")
    }

    private suspend fun getOrCreateStartDeferred(): Deferred<Boolean> = startMutex.withLock {
        if (isReady()) {
            inFlightStart?.takeIf { it.isCompleted }?.let { return@withLock it }
        }
        inFlightStart?.takeIf { it.isActive }?.let { return@withLock it }

        val deferred = scope.async {
            var createdServer: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
            try {
                val freePort = ServerSocket(0).use { it.localPort }
                createdServer = serverFactory(freePort)
                createdServer.start(wait = false)
                server = createdServer
                actualPort = freePort
                Timber.d("$proxyTag started on port $actualPort")
                true
            } catch (ce: CancellationException) {
                runCatching { createdServer?.stop(0, 500) }
                if (server === createdServer) {
                    server = null
                    actualPort = 0
                }
                Timber.d("$proxyTag start cancelled")
                throw ce
            } catch (e: Exception) {
                runCatching { createdServer?.stop(0, 500) }
                Timber.e(e, "Failed to start $proxyTag")
                false
            }
        }
        deferred.invokeOnCompletion {
            if (inFlightStart === deferred && !deferred.isActive) {
                // Leave completed deferred cleared lazily on next start check if needed
            }
        }
        inFlightStart = deferred
        deferred
    }
}

/**
 * Responds to HTTP HEAD requests for streamed audio without overwriting Content-Length to 0.
 */
internal suspend fun ApplicationCall.respondStreamHead(
    statusCode: HttpStatusCode,
    contentType: ContentType,
    contentLengthBytes: Long?
) {
    respond(
        object : OutgoingContent.NoContent() {
            override val status: HttpStatusCode = statusCode
            override val contentType: ContentType = contentType
            override val contentLength: Long? = contentLengthBytes
        }
    )
}
