package com.theveloper.pixelplay.data.remote.qqmusic

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.VisibleForTesting
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Android implementation of QQ Music Signature (sign) generation.
 * Uses an off-screen isolated WebView to execute the obfuscated logic from Assets
 * without loading remote pages or external network resources.
 */
class QQSignGenerator @VisibleForTesting internal constructor(
    context: Context,
    private val mainHandlerProvider: () -> Handler,
    private val isMainThreadCheck: () -> Boolean,
    private val webViewFactory: (Context) -> WebView,
    private val idleDestroyTimeoutMs: Long,
    private val clock: () -> Long
) {
    constructor(context: Context) : this(
        context = context,
        mainHandlerProvider = { Handler(Looper.getMainLooper()) },
        isMainThreadCheck = { Looper.myLooper() == Looper.getMainLooper() },
        webViewFactory = { ctx -> WebView(ctx) },
        idleDestroyTimeoutMs = DEFAULT_IDLE_DESTROY_TIMEOUT_MS,
        clock = { System.currentTimeMillis() }
    )

    companion object {
        private const val BASE_ORIGIN_URL = "https://y.qq.com/"
        private const val ISOLATED_HTML = "<!DOCTYPE html><html><head></head><body></body></html>"
        private const val JS_BRIDGE_NAME = "AndroidBridge"
        private const val DEFAULT_IDLE_DESTROY_TIMEOUT_MS = 150_000L

        internal fun blockedWebResourceResponse(): WebResourceResponse {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                WebResourceResponse(
                    "text/plain",
                    "UTF-8",
                    403,
                    "Blocked",
                    emptyMap(),
                    ByteArrayInputStream(ByteArray(0))
                )
            } else {
                WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
            }
        }
    }

    private val appContext = context.applicationContext ?: context
    private val mainHandler: Handler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { mainHandlerProvider() }
    private val signLock = ReentrantLock()

    @Volatile
    private var webView: WebView? = null

    @Volatile
    private var webViewReady: Boolean = false

    @Volatile
    private var lastActivityAtMs: Long = 0L

    @Volatile
    private var encryptLatch: CountDownLatch? = null

    @Volatile
    private var encryptResultRef: AtomicReference<String?>? = null

    private val idleDestroyRunnable = Runnable {
        if (!signLock.tryLock()) {
            // An operation is actively running; it will reschedule idle cleanup in its finally block.
            return@Runnable
        }
        try {
            val idleElapsed = clock() - lastActivityAtMs
            if (idleElapsed >= idleDestroyTimeoutMs) {
                destroyWebViewOnCurrentThreadLocked()
            }
        } finally {
            signLock.unlock()
        }
    }

    private inner class JsBridge {
        @JavascriptInterface
        fun onEncryptResult(value: String?) {
            val resultRef = encryptResultRef
            val latch = encryptLatch
            resultRef?.set(value)
            latch?.countDown()
        }
    }

    private val jsContent: String by lazy {
        appContext.assets.open("qq_sign.js").use { inputStream ->
            InputStreamReader(inputStream).readText()
        }
    }

    private val vmDecryptContent: String? by lazy {
        runCatching {
            appContext.assets.open("vm_new.js").use { inputStream ->
                InputStreamReader(inputStream).readText()
            }
        }.getOrNull()
    }

    private fun scheduleIdleDestroyLocked() {
        lastActivityAtMs = clock()
        mainHandler.removeCallbacks(idleDestroyRunnable)
        mainHandler.postDelayed(idleDestroyRunnable, idleDestroyTimeoutMs)
    }

    private fun cancelIdleDestroyLocked() {
        mainHandler.removeCallbacks(idleDestroyRunnable)
        lastActivityAtMs = clock()
    }

    private fun ensureWebViewLocked(): WebView {
        val existing = webView
        if (existing != null && webViewReady) {
            return existing
        }

        // Tear down any partially initialized WebView before creating a new instance.
        if (existing != null) {
            webView = null
            webViewReady = false
            postTeardownWebView(existing)
        }

        val created = AtomicReference<WebView>()
        val initError = AtomicReference<Throwable?>()
        val timedOut = AtomicBoolean(false)
        val createdLatch = CountDownLatch(1)
        val readyLatch = CountDownLatch(1)

        mainHandler.post {
            var instance: WebView? = null
            try {
                if (timedOut.get()) return@post
                instance = webViewFactory(appContext).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = false
                        databaseEnabled = false
                        cacheMode = WebSettings.LOAD_NO_CACHE
                        allowFileAccess = false
                        allowContentAccess = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            allowFileAccessFromFileURLs = false
                            allowUniversalAccessFromFileURLs = false
                        }
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            safeBrowsingEnabled = true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (!timedOut.get()) {
                                webViewReady = true
                            }
                            readyLatch.countDown()
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean = true

                        @Suppress("OVERRIDE_DEPRECATION")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = true

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse {
                            return blockedWebResourceResponse()
                        }
                    }
                    addJavascriptInterface(JsBridge(), JS_BRIDGE_NAME)
                    // Load local empty HTML anchored on the https://y.qq.com/ origin so
                    // Web Crypto APIs work without fetching remote pages, scripts, or trackers.
                    loadDataWithBaseURL(
                        BASE_ORIGIN_URL,
                        ISOLATED_HTML,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
                if (timedOut.get()) {
                    teardownWebView(instance)
                } else {
                    created.set(instance)
                }
            } catch (t: Throwable) {
                initError.set(t)
                instance?.let { runCatching { teardownWebView(it) } }
            } finally {
                createdLatch.countDown()
            }
        }

        if (!createdLatch.await(2, TimeUnit.SECONDS)) {
            timedOut.set(true)
            created.getAndSet(null)?.let { postTeardownWebView(it) }
            throw IllegalStateException("Timed out creating WebView signer")
        }

        initError.get()?.let { error ->
            throw IllegalStateException("Failed to initialize WebView signer", error)
        }

        val instance = created.get()
            ?: throw IllegalStateException("Failed to initialize WebView signer")

        if (!webViewReady && !readyLatch.await(8, TimeUnit.SECONDS)) {
            timedOut.set(true)
            webView = null
            webViewReady = false
            postTeardownWebView(instance)
            throw IllegalStateException("Timed out waiting for isolated WebView signer origin")
        }

        webView = instance
        return instance
    }

    private fun destroyWebViewOnCurrentThreadLocked() {
        val current = webView
        webView = null
        webViewReady = false
        encryptLatch?.countDown()
        encryptLatch = null
        encryptResultRef = null
        if (current != null) {
            teardownWebView(current)
        }
    }

    private fun postTeardownWebView(view: WebView) {
        mainHandler.post {
            teardownWebView(view)
        }
    }

    private fun teardownWebView(view: WebView) {
        runCatching { view.stopLoading() }
        runCatching { view.removeJavascriptInterface(JS_BRIDGE_NAME) }
        runCatching { view.loadUrl("about:blank") }
        runCatching { view.clearHistory() }
        runCatching { view.removeAllViews() }
        runCatching { view.destroy() }
    }

    /**
     * Releases the underlying Chromium WebView renderer immediately.
     */
    fun destroy() {
        mainHandler.removeCallbacks(idleDestroyRunnable)
        if (isMainThreadCheck()) {
            signLock.withLock {
                destroyWebViewOnCurrentThreadLocked()
            }
            return
        }

        val destroyedLatch = CountDownLatch(1)
        val viewToDestroy: WebView? = signLock.withLock {
            val current = webView
            webView = null
            webViewReady = false
            encryptLatch?.countDown()
            encryptLatch = null
            encryptResultRef = null
            current
        }

        if (viewToDestroy == null) return

        mainHandler.post {
            try {
                teardownWebView(viewToDestroy)
            } finally {
                destroyedLatch.countDown()
            }
        }
        destroyedLatch.await(2, TimeUnit.SECONDS)
    }

    private fun decodeEvaluateResult(raw: String?): String? {
        if (raw == null || raw == "null" || raw.isBlank()) return null
        return try {
            if (raw.startsWith('"')) JSONArray("[$raw]").getString(0) else raw
        } catch (_: Exception) {
            raw
        }
    }

    /**
     * Generates a `zzb...` signature for the given request JSON string.
     */
    fun generateSign(jsonData: String): String? {
        return try {
            signLock.withLock {
                if (isMainThreadCheck()) {
                    Timber.e("generateSign should not run on main thread")
                    return null
                }

                cancelIdleDestroyLocked()
                try {
                    val signerWebView = ensureWebViewLocked()
                    val quotedJson = JSONObject.quote(jsonData)
                    val evalScript = "(function(){$jsContent; return getSign($quotedJson);})()"

                    val resultRef = AtomicReference<String?>()
                    val latch = CountDownLatch(1)
                    mainHandler.post {
                        signerWebView.evaluateJavascript(evalScript) { value ->
                            resultRef.set(decodeEvaluateResult(value))
                            latch.countDown()
                        }
                    }

                    if (!latch.await(3, TimeUnit.SECONDS)) {
                        Timber.e("WebView Sign timeout")
                        return null
                    }
                    resultRef.get()
                } finally {
                    scheduleIdleDestroyLocked()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "WebView Sign error")
            null
        }
    }

    /**
     * Uses vm_new.js __cgiDecrypt to keep behavior aligned with reverse-engineering scripts.
     */
    fun decryptResponseWithVm(encryptedData: ByteArray): String? {
        val vmCode = vmDecryptContent ?: return null
        return try {
            signLock.withLock {
                if (isMainThreadCheck()) {
                    Timber.e("decryptResponseWithVm should not run on main thread")
                    return null
                }

                cancelIdleDestroyLocked()
                try {
                    val signerWebView = ensureWebViewLocked()
                    val b64 = Base64.encodeToString(encryptedData, Base64.NO_WRAP)
                    val quotedB64 = JSONObject.quote(b64)
                    val script = """
                        (function() {
                            var e = (typeof globalThis !== 'undefined') ? globalThis : this;
                            var oe = (typeof e !== 'undefined') ? e : ((typeof window !== 'undefined') ? window : ((typeof self !== 'undefined') ? self : this));
                            $vmCode
                            var raw = $quotedB64;
                            var bin = atob(raw);
                            var bytes = new Uint8Array(bin.length);
                            for (var i = 0; i < bin.length; i++) {
                                bytes[i] = bin.charCodeAt(i);
                            }
                            return oe.__cgiDecrypt(bytes.buffer);
                        })();
                    """.trimIndent()

                    val resultRef = AtomicReference<String?>()
                    val latch = CountDownLatch(1)
                    mainHandler.post {
                        signerWebView.evaluateJavascript(script) { value ->
                            resultRef.set(decodeEvaluateResult(value))
                            latch.countDown()
                        }
                    }

                    if (!latch.await(3, TimeUnit.SECONDS)) {
                        Timber.e("WebView vm_new decrypt timeout")
                        return null
                    }
                    resultRef.get()
                } finally {
                    scheduleIdleDestroyLocked()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "WebView vm_new decrypt error")
            null
        }
    }

    /**
     * Uses vm_new.js __cgiEncrypt for request body encryption.
     */
    fun encryptRequestWithVm(plaintext: String): String? {
        val vmCode = vmDecryptContent ?: return null
        return try {
            signLock.withLock {
                if (isMainThreadCheck()) {
                    Timber.e("encryptRequestWithVm should not run on main thread")
                    return null
                }

                cancelIdleDestroyLocked()
                val latch = CountDownLatch(1)
                val resultRef = AtomicReference<String?>()
                encryptLatch = latch
                encryptResultRef = resultRef

                try {
                    val signerWebView = ensureWebViewLocked()
                    val quotedPlaintext = JSONObject.quote(plaintext)
                    val script = """
                        (function() {
                            try {
                                var e = (typeof globalThis !== 'undefined') ? globalThis : this;
                                var oe = (typeof e !== 'undefined') ? e : ((typeof window !== 'undefined') ? window : ((typeof self !== 'undefined') ? self : this));
                                $vmCode
                                var payload = $quotedPlaintext;
                                var maybePromise = oe.__cgiEncrypt(payload);
                                if (maybePromise && typeof maybePromise.then === 'function') {
                                    maybePromise.then(function(encrypted) {
                                        AndroidBridge.onEncryptResult(encrypted || "");
                                    }).catch(function() {
                                        AndroidBridge.onEncryptResult("");
                                    });
                                } else {
                                    AndroidBridge.onEncryptResult(maybePromise || "");
                                }
                            } catch (err) {
                                AndroidBridge.onEncryptResult("");
                            }
                        })();
                    """.trimIndent()

                    mainHandler.post {
                        signerWebView.evaluateJavascript(script, null)
                    }

                    if (!latch.await(3, TimeUnit.SECONDS)) {
                        Timber.e("WebView vm_new encrypt timeout")
                        return null
                    }

                    val value = resultRef.get()
                    if (value.isNullOrBlank()) null else value
                } finally {
                    encryptLatch = null
                    encryptResultRef = null
                    scheduleIdleDestroyLocked()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "WebView vm_new encrypt error")
            null
        }
    }
}
