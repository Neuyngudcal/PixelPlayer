package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.theveloper.pixelplay.presentation.components.albumArtMemoryCacheKey
import com.theveloper.pixelplay.presentation.viewmodel.ThemeStateHolder
import com.theveloper.pixelplay.utils.LocalArtworkUri
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

private data class GridScrollViewportSnapshot(
    val isScrolling: Boolean,
    val firstVisible: Int,
    val lastVisible: Int
)

private val LibraryGridArtworkTargetSize = Size(256, 256)
private const val SCROLL_IDLE_SETTLE_DELAY_MS = 90L
private const val HYDRATE_AHEAD_COUNT = 4
private const val PREFETCH_AHEAD_COUNT = 6

/**
 * Coordinates scroll-idle Coil prefetching and batch dynamic palette hydration for
 * library grids so flinging stays completely free of bitmap decodes and color extraction.
 */
@Composable
internal fun LibraryGridScrollIdleEffects(
    gridState: LazyGridState,
    itemCount: Int,
    peekArtUri: (Int) -> String?,
    themeStateHolder: ThemeStateHolder
) {
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    val latestPeekArtUri by rememberUpdatedState(peekArtUri)

    LaunchedEffect(gridState, itemCount, themeStateHolder) {
        if (itemCount <= 0) return@LaunchedEffect

        snapshotFlow {
            val visibleItems = gridState.layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                GridScrollViewportSnapshot(
                    isScrolling = gridState.isScrollInProgress,
                    firstVisible = -1,
                    lastVisible = -1
                )
            } else {
                GridScrollViewportSnapshot(
                    isScrolling = gridState.isScrollInProgress,
                    firstVisible = visibleItems.first().index,
                    lastVisible = visibleItems.last().index
                )
            }
        }
            .distinctUntilChanged()
            .collectLatest { snapshot ->
                if (snapshot.isScrolling || snapshot.firstVisible < 0 || snapshot.lastVisible < snapshot.firstVisible) {
                    return@collectLatest
                }

                delay(SCROLL_IDLE_SETTLE_DELAY_MS)

                val maxIndex = itemCount - 1
                val hydrateStart = snapshot.firstVisible.coerceIn(0, maxIndex)
                val hydrateEnd = (snapshot.lastVisible + HYDRATE_AHEAD_COUNT).coerceAtMost(maxIndex)

                val visibleAndNearUris = (hydrateStart..hydrateEnd)
                    .mapNotNull { index -> latestPeekArtUri(index)?.takeIf { it.isNotBlank() } }
                    .distinct()

                val prefetchStart = snapshot.lastVisible + 1
                val prefetchEnd = (snapshot.lastVisible + PREFETCH_AHEAD_COUNT).coerceAtMost(maxIndex)
                if (prefetchStart <= prefetchEnd) {
                    (prefetchStart..prefetchEnd)
                        .mapNotNull { index -> latestPeekArtUri(index)?.takeIf { it.isNotBlank() } }
                        .distinct()
                        .forEach { uri ->
                            val memoryKey = albumArtMemoryCacheKey(uri, LibraryGridArtworkTargetSize)
                            val isLocal = LocalArtworkUri.isLocalArtworkUri(uri)
                            val request = ImageRequest.Builder(context)
                                .data(uri)
                                .size(LibraryGridArtworkTargetSize)
                                .diskCachePolicy(if (isLocal) CachePolicy.DISABLED else CachePolicy.ENABLED)
                                .apply {
                                    if (memoryKey != null) {
                                        memoryCacheKey(memoryKey)
                                    }
                                }
                                .build()
                            imageLoader.enqueue(request)
                        }
                }

                if (visibleAndNearUris.isNotEmpty()) {
                    themeStateHolder.hydrateAlbumColorSchemes(visibleAndNearUris)
                }
            }
    }
}
