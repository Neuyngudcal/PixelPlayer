@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.theveloper.pixelplay.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayWindowInfo

/**
 * M3 "Container transform" (m3.material.io/styles/motion/transitions), hero-image variant:
 * Library album grid card <-> AlbumDetail header. Set to false to switch the whole feature off
 * (no SharedTransitionLayout, shared modifiers become no-ops, AlbumDetail keeps the slide).
 */
const val EnableAlbumHeroTransition = true

private val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The AnimatedVisibilityScope of the NavHost destination, provided by ScreenWrapper. */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

val LocalAlbumHeroHandoff = staticCompositionLocalOf<AlbumHeroHandoff?> { null }

/**
 * Hands the Album from the Library grid card to AlbumDetail and remembers which navigation is the
 * hero one. Plain fields on purpose: they are only read when a transition starts or when
 * AlbumDetail first composes, never observed.
 */
@Stable
class AlbumHeroHandoff {
    private var pendingAlbumId: Long? = null
    private var heroEntryId: String? = null
    private var seedAlbum: Album? = null

    /** A Library grid card was tapped. */
    fun offer(album: Album) {
        pendingAlbumId = album.id
        seedAlbum = album
    }

    /** Library is RESUMED again, so any hero transition (forward or back) has finished. */
    fun clear() {
        pendingAlbumId = null
        heroEntryId = null
        seedAlbum = null
    }

    /** Album to draw on AlbumDetail's first frame, before Room answers. Only for the hero album. */
    fun seedFor(albumId: Long): Album? = seedAlbum?.takeIf { it.id == albumId }

    internal fun isHeroForward(albumId: Long, entryId: String): Boolean {
        if (pendingAlbumId != albumId) return false
        heroEntryId = entryId // idempotent: enter and exit lambdas both call this
        return true
    }

    internal fun isHeroPop(entryId: String): Boolean = heroEntryId == entryId
}

/** Wraps the NavHost. With the feature off it renders [content] directly (no lookahead cost). */
@Composable
fun AlbumHeroTransitionHost(
    albumHeroHandoff: AlbumHeroHandoff,
    content: @Composable () -> Unit
) {
    if (!EnableAlbumHeroTransition) {
        content()
        return
    }
    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides this,
            LocalAlbumHeroHandoff provides albumHeroHandoff
        ) {
            content()
        }
    }
}

/**
 * True for Library -> AlbumDetail opened from a Library grid card, and for the pop back from that
 * same AlbumDetail entry. Every other way into or out of AlbumDetail keeps the forward/backward slide.
 */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.isAlbumHeroTransition(
    albumHeroHandoff: AlbumHeroHandoff,
    heroAllowed: Boolean
): Boolean {
    if (!heroAllowed) return false
    val fromRoute = initialState.destination.route
    val toRoute = targetState.destination.route
    return when {
        fromRoute == Screen.Library.route && toRoute == Screen.AlbumDetail.route -> {
            val albumId = targetState.arguments?.getString("albumId")?.toLongOrNull() ?: return false
            albumHeroHandoff.isHeroForward(albumId, targetState.id)
        }
        fromRoute == Screen.AlbumDetail.route && toRoute == Screen.Library.route ->
            albumHeroHandoff.isHeroPop(initialState.id)
        else -> false
    }
}

enum class AlbumHeroRole { GridCard, DetailHeader }

// Matches the 20.dp cardShape of AlbumGridItemRedesigned; only its top corners touch the artwork.
private val AlbumGridCardArtworkShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

private val AlbumHeroBoundsTransform = BoundsTransform { _, _ ->
    tween(durationMillis = CONTAINER_TRANSFORM_DURATION, easing = M3EmphasizedEasing)
}

private fun albumHeroKey(albumId: Long): String = "album_hero_$albumId"

/**
 * Shared bounds between the Library grid card artwork ([AlbumHeroRole.GridCard]) and the
 * AlbumDetail header ([AlbumHeroRole.DetailHeader]). No-op when the feature is off, on the
 * navigation rail / two-pane layouts, or outside the NavHost.
 */
@Composable
fun Modifier.albumHeroBounds(albumId: Long, role: AlbumHeroRole): Modifier {
    if (!EnableAlbumHeroTransition) return this
    val windowInfo = LocalPixelPlayWindowInfo.current
    if (windowInfo.useNavigationRail || windowInfo.useTwoPaneDetail) return this
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current ?: return this
    return with(sharedTransitionScope) {
        this@albumHeroBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key = albumHeroKey(albumId)),
            animatedVisibilityScope = animatedVisibilityScope,
            // The outgoing side stays almost opaque until the end (it masks the header image while
            // it decodes); the incoming side shows up early. No blank frames, no long overlap.
            enter = fadeIn(
                animationSpec = tween(
                    durationMillis = CONTAINER_TRANSFORM_DURATION,
                    easing = M3EmphasizedDecelerateEasing
                )
            ),
            exit = fadeOut(
                animationSpec = tween(
                    durationMillis = CONTAINER_TRANSFORM_DURATION,
                    easing = M3EmphasizedAccelerateEasing
                )
            ),
            boundsTransform = AlbumHeroBoundsTransform,
            resizeMode = when (role) {
                // Artwork only: re-crop at every size (different aspect ratios).
                AlbumHeroRole.GridCard -> SharedTransitionScope.ResizeMode.RemeasureToBounds
                // Header with title + FAB: scale, never reflow the text.
                AlbumHeroRole.DetailHeader -> SharedTransitionScope.ResizeMode.scaleToBounds(
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center
                )
            },
            clipInOverlayDuringTransition = OverlayClip(
                when (role) {
                    AlbumHeroRole.GridCard -> AlbumGridCardArtworkShape
                    AlbumHeroRole.DetailHeader -> RectangleShape
                }
            )
        )
    }
}
