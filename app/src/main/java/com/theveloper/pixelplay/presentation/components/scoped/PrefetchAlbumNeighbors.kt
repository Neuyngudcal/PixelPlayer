package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.Disposable
import coil.size.Size
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.albumArtMemoryCacheKey
import com.theveloper.pixelplay.presentation.components.safeAlbumArtTargetSize
import com.theveloper.pixelplay.utils.LocalArtworkUri
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun PrefetchAlbumNeighborsImg(
    current: Song?,
    queue: ImmutableList<Song>,
    radius: Int = 1
) {
    if (current == null) return
    val context = LocalContext.current
    val loader = coil.Coil.imageLoader(context)
    val index = remember(current, queue) { queue.indexOfFirst { it.id == current.id } }
    LaunchedEffect(index, queue) {
        if (index == -1) return@LaunchedEffect
        val bounds = (maxOf(0, index - radius))..(minOf(queue.lastIndex, index + radius))
        for (i in bounds) {
            if (i == index) continue
            queue[i].albumArtUriString?.let { data ->
                val diskPolicy = if (LocalArtworkUri.isLocalArtworkUri(data)) CachePolicy.DISABLED else CachePolicy.ENABLED
                val targetSize = safeAlbumArtTargetSize(Size.ORIGINAL)
                val memoryCacheKey = albumArtMemoryCacheKey(data, targetSize)
                val req = coil.request.ImageRequest.Builder(context)
                    .data(data)
                    .apply {
                        if (memoryCacheKey != null) {
                            memoryCacheKey(memoryCacheKey)
                        }
                    }
                    .diskCacheKey(if (diskPolicy == CachePolicy.DISABLED) null else memoryCacheKey)
                    .diskCachePolicy(diskPolicy)
                    .size(targetSize)
                    .build()
                loader.enqueue(req)
            }
        }
    }
}


@Composable
fun PrefetchAlbumNeighbors(
    isActive: Boolean,
    pagerState: PagerState,
    queue: ImmutableList<Song>,
    radius: Int = 1,
    targetSize: Size = Size(600, 600),
    anchorIndex: Int? = null
) {
    if (!isActive || queue.isEmpty()) return
    val context = LocalContext.current
    val imageLoader = coil.Coil.imageLoader(context)
    val requestTargetSize = remember(targetSize) {
        safeAlbumArtTargetSize(targetSize)
    }

    val latestAnchorIndex = androidx.compose.runtime.rememberUpdatedState(anchorIndex)
    val completedUris = remember(queue, requestTargetSize) { mutableSetOf<String>() }

    LaunchedEffect(pagerState, queue, requestTargetSize, radius) {
        snapshotFlow {
            (latestAnchorIndex.value ?: pagerState.currentPage) to pagerState.isScrollInProgress
        }
            .distinctUntilChanged()
            .collectLatest { (page, isScrolling) ->
                // Defer neighbor prefetch while the carousel is actively scrolling/dragging so
                // high-resolution bitmap decodes never compete with 60/120fps transition frames.
                if (isScrolling) return@collectLatest
                delay(140L)

                val indices = (page - radius..page + radius)
                    .filter { it in queue.indices && it != page }
                val inFlightDisposables = mutableListOf<Pair<String, Disposable>>()
                val memoryCache = imageLoader.memoryCache
                try {
                    indices.forEach { idx ->
                        queue[idx].albumArtUriString?.let { uri ->
                            val memoryCacheKey = albumArtMemoryCacheKey(uri, requestTargetSize)
                            if (memoryCacheKey != null && memoryCache?.get(MemoryCache.Key(memoryCacheKey)) != null) {
                                completedUris.add(uri)
                                return@let
                            }
                            if (uri in completedUris) return@let
                            val diskPolicy = if (LocalArtworkUri.isLocalArtworkUri(uri)) {
                                CachePolicy.DISABLED
                            } else {
                                CachePolicy.ENABLED
                            }
                            val req = coil.request.ImageRequest.Builder(context)
                                .data(uri)
                                .size(requestTargetSize)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .apply {
                                    if (memoryCacheKey != null) {
                                        memoryCacheKey(memoryCacheKey)
                                    }
                                }
                                .diskCachePolicy(diskPolicy)
                                .networkCachePolicy(CachePolicy.ENABLED)
                                .allowHardware(true)
                                .build()
                            inFlightDisposables.add(uri to imageLoader.enqueue(req))
                        }
                    }
                    inFlightDisposables.forEach { (uri, disposable) ->
                        disposable.job.await()
                        completedUris.add(uri)
                    }
                } finally {
                    inFlightDisposables.forEach { (_, disposable) ->
                        if (!disposable.isDisposed) {
                            disposable.dispose()
                        }
                    }
                }
            }
    }
}
