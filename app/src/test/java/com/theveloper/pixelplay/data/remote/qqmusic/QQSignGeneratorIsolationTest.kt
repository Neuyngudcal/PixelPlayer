package com.theveloper.pixelplay.data.remote.qqmusic

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import android.os.Handler
import android.webkit.ValueCallback
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class QQSignGeneratorIsolationTest {

    @Test
    fun `QQSignGenerator isolates WebView origin disables remote load and cleans up lifecycle`() {
        val assetManager = mockk<AssetManager>()
        every { assetManager.open("qq_sign.js") } answers {
            ByteArrayInputStream("function getSign(x){return 'zzb_ok';}".toByteArray())
        }
        every { assetManager.open("vm_new.js") } answers {
            ByteArrayInputStream("".toByteArray())
        }

        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns context
        every { context.assets } returns assetManager

        val executor = Executors.newSingleThreadExecutor()
        val scheduledDelayedRunnables = mutableListOf<Runnable>()
        val mockHandler = mockk<Handler>(relaxed = true)
        every { mockHandler.post(any()) } answers {
            val r = firstArg<Runnable>()
            executor.execute(r)
            true
        }
        every { mockHandler.removeCallbacks(any()) } answers {
            scheduledDelayedRunnables.remove(firstArg())
        }
        every { mockHandler.postDelayed(any(), any()) } answers {
            val r = firstArg<Runnable>()
            scheduledDelayedRunnables.add(r)
            true
        }

        val webSettings = mockk<WebSettings>(relaxed = true)
        val mockWebView = mockk<WebView>(relaxed = true)
        every { mockWebView.settings } returns webSettings

        var capturedClient: WebViewClient? = null
        every { mockWebView.webViewClient = any() } answers {
            capturedClient = firstArg()
        }
        every { mockWebView.loadDataWithBaseURL(any(), any(), any(), any(), any()) } answers {
            capturedClient?.onPageFinished(mockWebView, "https://y.qq.com/")
        }
        every { mockWebView.evaluateJavascript(any(), any()) } answers {
            val cb = secondArg<ValueCallback<String>?>()
            cb?.onReceiveValue("\"zzb_signed_123\"")
        }

        val factoryCount = AtomicInteger(0)
        val fakeClock = AtomicLong(1_000L)

        val generator = QQSignGenerator(
            context = context,
            mainHandlerProvider = { mockHandler },
            isMainThreadCheck = { false },
            webViewFactory = {
                factoryCount.incrementAndGet()
                mockWebView
            },
            idleDestroyTimeoutMs = 150_000L,
            clock = { fakeClock.get() }
        )

        try {
            val sign1 = generator.generateSign("""{"comm":{}}""")
            val sign2 = generator.generateSign("""{"comm":{}}""")

            assertEquals("zzb_signed_123", sign1)
            assertEquals("zzb_signed_123", sign2)
            assertEquals(1, factoryCount.get())

            // Verify isolated origin loadDataWithBaseURL and zero live loadUrl("https://y.qq.com/")
            verify(exactly = 1) {
                mockWebView.loadDataWithBaseURL(
                    "https://y.qq.com/",
                    "<!DOCTYPE html><html><head></head><body></body></html>",
                    "text/html",
                    "UTF-8",
                    null
                )
            }
            verify(exactly = 0) { mockWebView.loadUrl("https://y.qq.com/") }

            // Verify hardened WebSettings
            verify { webSettings.javaScriptEnabled = true }
            verify { webSettings.domStorageEnabled = false }
            verify { webSettings.databaseEnabled = false }
            verify { webSettings.cacheMode = WebSettings.LOAD_NO_CACHE }
            verify { webSettings.allowFileAccess = false }
            verify { webSettings.allowContentAccess = false }
            verify { webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW }

            // Verify navigation and network subresource requests are blocked by WebViewClient
            val client = capturedClient
            assertNotNull(client)
            val request = mockk<WebResourceRequest>(relaxed = true)
            every { request.url } returns mockk<Uri>(relaxed = true)
            assertTrue(client!!.shouldOverrideUrlLoading(mockWebView, request))
            @Suppress("DEPRECATION")
            assertTrue(client.shouldOverrideUrlLoading(mockWebView, "https://evil.example.com"))
            assertNotNull(client.shouldInterceptRequest(mockWebView, request))

            // Simulate idle timeout firing on main thread -> tears down WebView
            fakeClock.addAndGet(160_000L)
            assertTrue(scheduledDelayedRunnables.isNotEmpty())
            scheduledDelayedRunnables.last().run()

            verify(exactly = 1) { mockWebView.removeJavascriptInterface("AndroidBridge") }
            verify(exactly = 1) { mockWebView.destroy() }

            // Next call creates a fresh isolated WebView, then explicit destroy() cleans it up too
            val sign3 = generator.generateSign("""{"comm":{}}""")
            assertEquals("zzb_signed_123", sign3)
            assertEquals(2, factoryCount.get())

            generator.destroy()
            verify(exactly = 2) { mockWebView.removeJavascriptInterface("AndroidBridge") }
            verify(exactly = 2) { mockWebView.destroy() }
        } finally {
            generator.destroy()
            executor.shutdownNow()
        }
    }
}
