package com.theveloper.pixelplay.data.service

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.theveloper.pixelplay.data.diagnostics.PerformanceMetrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class CoilBitmapLoader(private val context: Context, private val scope: CoroutineScope) : BitmapLoader {

    companion object {
        // Large enough for lock screen / media surfaces, but bounded so we never hand
        // unbounded original artwork to MediaSession/SystemUI IPC.
        private const val MAX_NOTIFICATION_ARTWORK_SIZE_PX = 1024
        private const val RECENT_BITMAP_CACHE_ENTRIES = 3
    }

    // Small dedicated cache for the active track + immediate neighbors so repeated Media3
    // notification refreshes (e.g. onMediaItemTransition + onMediaMetadataChanged + button updates)
    // do not re-decode the same 1024x1024 software bitmap from disk multiple times per transition.
    // Evicted entries are left to GC (never manually recycled) so SystemUI IPC stays safe.
    private val recentBitmapCache = LruCache<String, Bitmap>(RECENT_BITMAP_CACHE_ENTRIES)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val cacheKey = uri.toString()
        synchronized(recentBitmapCache) {
            val cached = recentBitmapCache.get(cacheKey)
            if (cached != null && !cached.isRecycled) {
                return Futures.immediateFuture(cached)
            }
        }
        return loadBitmapInternal(uri, cacheKey)
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        return loadBitmapInternal(data, cacheKey = null)
    }

    private fun loadBitmapInternal(data: Any, cacheKey: String?): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()

        val job = scope.launch(Dispatchers.IO) {
            val startNanos = System.nanoTime()
            try {
                val request = ImageRequest.Builder(context)
                    .data(data)
                    // Preserve enough resolution for media surfaces while preventing huge
                    // album art from destabilizing notification/SystemUI rendering.
                    .size(MAX_NOTIFICATION_ARTWORK_SIZE_PX, MAX_NOTIFICATION_ARTWORK_SIZE_PX)
                    .precision(Precision.INEXACT)
                    .allowHardware(false) // Bitmap must not be hardware for MediaSession
                    // Disable Coil's shared memory cache so Coil does not hold a reference that
                    // could be recycled on memory trim while Media3 still uses it for IPC.
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build()

                val result = context.imageLoader.execute(request)
                val drawable = result.drawable

                if (drawable != null) {
                    val bitmap = drawable.toBitmap()
                    if (cacheKey != null && !bitmap.isRecycled) {
                        synchronized(recentBitmapCache) {
                            recentBitmapCache.put(cacheKey, bitmap)
                        }
                    }
                    PerformanceMetrics.recordTiming(
                        PerformanceMetrics.Timings.ARTWORK_DECODE,
                        (System.nanoTime() - startNanos) / 1_000_000
                    )
                    PerformanceMetrics.recordDecodedArtworkDimensions(bitmap.width, bitmap.height)
                    future.set(bitmap)
                } else {
                    future.setException(IllegalStateException("Coil returned null drawable for data: $data"))
                }
            } catch (e: CancellationException) {
                future.cancel(false)
                throw e
            } catch (e: Exception) {
                future.setException(e)
            }
        }

        future.addListener(
            {
                if (future.isCancelled) {
                    job.cancel()
                }
            },
            MoreExecutors.directExecutor()
        )
        return future
    }

    override fun supportsMimeType(mimeType: String): Boolean {
        return true // Coil supports most image types
    }
}
