# PROMPT FOR GEMINI 3.8 FLASH — COMPREHENSIVE PERFORMANCE AUDIT & OPTIMIZATION PLAN FOR PIXELPLAYER

You are a Principal Android Performance & Jetpack Compose Engineer working on **PixelPlayer** (`androidx.compose.material3:material3:1.5.0-alpha22`, Material 3 Expressive).
A deep architectural and frame-timing audit of the codebase uncovered **10 severe performance bottlenecks** causing UI frame drops (jank), recomposition storms, high RAM/GC pressure, and main-thread contention.

Your mission is to implement the fixes below cleanly, systematically, and in strict compliance with **`AGENTS.md`** (Material 3 Expressive 5-step workflow, `MotionScheme.expressive()`, draw/layout phase isolation, modifier chain ordering, and zero hardcoded colors/strings).

---

## EXECUTIVE SUMMARY OF THE 10 SEVERE BOTTLENECKS

| # | Severity | Subsystem / File(s) | Root Cause Summary |
| :--- | :--- | :--- | :--- |
| **1** | **CRITICAL** | `SmartImage.kt` & `OptimizedAlbumArt.kt` | `SmartImage` defaults to `allowHardware = false` (forcing all list/grid bitmaps into software `ARGB_8888` heap RAM instead of GPU `HARDWARE` buffers) and lacks local artwork disk-cache bypass & size-bucketed memory cache keys. |
| **2** | **CRITICAL** | `FullPlayerContent.kt` & `SheetThemeState.kt` | `rememberBatchAnimatedColorScheme` emits a new `ColorScheme` every frame for ~350ms (`slowEffectsSpec()`). `FullPlayerContent` passes derived animated `Color` values as `remember(...)` keys to all 5 section lambdas (`albumCoverSection`, `portraitSongMetadataSection`, `landscapeSongMetadataSection`, `playerProgressSection`, `controlsSection`), forcing a 100% full-subtree recomposition on every frame. |
| **3** | **CRITICAL** | `RoundedParallaxCarousell.kt` | Reads `pagerState.currentPageOffsetFraction` directly in composition scope (no `derivedStateOf`), passes an unremembered `strategy = { pageSize.strategy }` lambda that triggers `node.invalidateMeasurement()` every frame, and uses CPU `Outline.Generic(Path)` instead of GPU `Outline.Rounded(RoundRect)`. |
| **4** | **HIGH** | `EnhancedSongListItem.kt` | Every visible row in `LazyColumn` unconditionally runs `updateTransition` (3 `animateFloat` nodes + 7 `lerpColor` + 3 `lerpDp` + 2 shape allocations) even when `!isHighlighted && !isSelected` (99% of rows). Also has `.size(36.dp).padding(end = 4.dp)` modifier order bug. |
| **5** | **HIGH** | `QueueBottomSheet.kt` & `PlaybackDispatchStateHolder.kt` | `QueueBottomSheet` mutates `mutableStateOf` (`committedDisplaySongIds`, `committedDisplayKeys`) directly inside composition via `remapCommittedKeysForDisplay` keyed on `System.identityHashCode(displaySongs)`. `PlaybackDispatchStateHolder.matchesSongOrder` runs an $O(N)$ list comparison synchronously on the Main Thread. |
| **6** | **HIGH** | `LyricsSheet.kt` | Subscribes to **8 separate `context.dataStore.data.map`** flows directly in composition; passes `currentLineIndex` and `distanceFromCurrent` by value to every visible `LyricLineRow`, triggering mass recompositions and `RenderEffect` blur allocations on every lyric line change. |
| **7** | **HIGH** | `LibraryStateHolder.kt` & `MusicRepositoryImpl.kt` | Eagerly loads and maps the entire SQLite library (`getAudioFiles()`, `getAlbums()`, `getArtists()`, `getMusicFolders()`) into RAM alongside Paging 3 flows. Any single-row update in `songs` (e.g., favorite toggle or lyrics save) invalidates the `songs` table and re-maps thousands of entities without debouncing. |
| **8** | **HIGH** | `MediaControllerSyncStateHolder.kt`, `PrefetchAlbumNeighbors.kt`, `AlbumCarouselSelection.kt` | `syncDisplayedMediaItemIfChanged` cancels in-flight `transitionSchedulerJob` and duplicates transition work on the Main Thread; `PrefetchAlbumNeighbors` enqueues 4 Coil requests mid-scroll; `pendingCarouselIndex` expires at `350ms` before the `~500ms` `defaultSpatialSpec()` settles. |
| **9** | **MEDIUM-HIGH** | `UnifiedPlayerSheetV2.kt` & `UnifiedPlayerSheetLayers.kt` | `UnifiedPlayerPrewarmLayer` composes a hidden `FullPlayerContent` in a separate composition branch while collapsed, then tears it down and composes a brand-new `FullPlayerContent` inside `UnifiedPlayerMiniAndFullLayers` on Frame 1 of the expand gesture. |
| **10** | **MEDIUM-HIGH** | `MarqueeText.kt` | `AutoScrollingText` uses `SubcomposeLayout` (double text measurement) and `CompositingStrategy.Offscreen` + per-frame `Brush.horizontalGradient` allocation instead of Compose's native `basicMarquee` used in `AutoScrollingTextOnDemand`. |

---

## DETAILED STEP-BY-STEP IMPLEMENTATION INSTRUCTIONS

### TASK 1: Enable Hardware Bitmaps & Unified Cache Keys in `SmartImage.kt`
**File:** `app/src/main/java/com/theveloper/pixelplay/presentation/components/SmartImage.kt`

**Why:** `SmartImage` is used in every song row, album card, queue item, and mini-player thumbnail. Having `allowHardware: Boolean = false` as the default forces every thumbnail into heap memory (`ARGB_8888`), increasing GC pauses and RenderThread upload overhead.

**Changes:**
1. Change the default parameter `allowHardware: Boolean = false` (line 57) to `allowHardware: Boolean = true` (only callers that explicitly inspect pixels on the CPU, if any, should pass `allowHardware = false`).
2. Apply the same local artwork disk-cache bypass (`LocalArtworkUri.isLocalArtworkUri`) and size-bucketed memory cache key (`albumArtMemoryCacheKey`) used in `OptimizedAlbumArt.kt`:
   ```kotlin
   val isLocalArtwork = model is String && LocalArtworkUri.isLocalArtworkUri(model)
   val effectiveDiskCachePolicy = if (isLocalArtwork) CachePolicy.DISABLED else diskCachePolicy
   val memoryKey = remember(model, targetSize) {
       (model as? String)?.let { albumArtMemoryCacheKey(it, targetSize) }
   }
   ```
3. Inside the `remember(model, ...)` block for `ImageRequest.Builder(context)`:
   ```kotlin
   .diskCachePolicy(effectiveDiskCachePolicy)
   .apply {
       if (memoryKey != null) {
           memoryCacheKey(memoryKey)
       }
   }
   ```

---

### TASK 2: Stop Per-Frame Full-Tree Recomposition in `FullPlayerContent.kt`
**File:** `app/src/main/java/com/theveloper/pixelplay/presentation/components/player/FullPlayerContent.kt`

**Why:** `rememberBatchAnimatedColorScheme` (`SheetThemeState.kt`) animates `LocalMaterialTheme.current` over ~350ms (`slowEffectsSpec()`), emitting a new `ColorScheme` on every frame. Passing derived `Color` values into the `remember(...)` keys of `albumCoverSection`, `portraitSongMetadataSection`, `landscapeSongMetadataSection`, `playerProgressSection`, and `controlsSection` invalidates all 5 section lambdas on every frame, forcing the entire `FullPlayerContent` tree (including `AlbumCarouselSection`) to recompose during track transitions.

**Changes:**
1. **In `albumCoverSection` (around line 585):**
   - Remove `placeholderColor` and `placeholderOnColor` from the `remember(...)` keys of `albumCoverSection`.
   - Inside `FullPlayerAlbumCoverSection`, read `val colorScheme = LocalMaterialTheme.current` directly inside the `placeholder = { ... }` lambda so `FullPlayerAlbumCoverSection` and `AlbumCarouselSection` NEVER recompose when `LocalMaterialTheme.current` animates!
2. **In `portraitSongMetadataSection` & `landscapeSongMetadataSection` (around lines 616–676):**
   - Remove `playerOnBaseColor`, `gradientEdgeColor`, `chipColor`, and `chipContentColor` from the `remember(...)` keys of both metadata section lambdas.
   - Read `LocalMaterialTheme.current` inside `FullPlayerSongMetadataSection` (which is a tiny leaf composable) instead of recreating the `@Composable` section lambdas at the root.
3. **In `playerProgressSection` (around line 678):**
   - Remove `progressActiveColor` and `playerOnBaseColor` from the `remember(...)` keys of `playerProgressSection`.
   - Read `LocalMaterialTheme.current` inside `FullPlayerProgressSection`.
4. **In `controlsSection` (around line 711):**
   - Remove `transportPlayPauseColors`, `transportSkipColors`, and `playerOnBaseColor` from the `remember(...)` keys of `controlsSection`.
   - Compute `transportPlayPauseColors` and `transportSkipColors` inside `FullPlayerControlsSection` from `LocalMaterialTheme.current`.
5. **In `LaunchedEffect(currentQueueIndex, pendingCarouselIndex)` (around line 433):**
   - Increase the settle delay from `350` to `550` (and fallback from `750` to `950`) so `pendingCarouselIndex` remains locked for the full duration of `MotionScheme.expressive().defaultSpatialSpec()` (~500ms).

---

### TASK 3: Eliminate Per-Frame `invalidateMeasurement()` & CPU `clipPath` in `RoundedParallaxCarousell.kt`
**File:** `app/src/main/java/com/theveloper/pixelplay/presentation/components/RoundedParallaxCarousell.kt`

**Why:**
1. Reading `state.pagerState.currentPageOffsetFraction > 0.01f` directly in composition scope forces every visible carousel page to recompose on every frame of scroll.
2. Passing an unremembered lambda `strategy = { pageSize.strategy }` allocates a new Function0 instance on every recomposition, causing `CarouselItemModifierNodeElement.update()` to call `node.invalidateMeasurement()` on every scroll frame.
3. `Outline.Generic(path)` forces CPU rasterized path clipping instead of GPU hardware rounded-rect clipping.

**Changes:**
1. Right after `val pageSize = remember(keylineList) { ... }` (around line 264), memoize the strategy provider lambda:
   ```kotlin
   val strategyProvider = remember(pageSize) { { pageSize.strategy } }
   ```
2. Inside `HorizontalPager { page -> ... }` (around line 284), replace `Outline.Generic(path)` with `Outline.Rounded(RoundRect)`:
   ```kotlin
   val density = LocalDensity.current
   val clipShape = remember(carouselItemInfo, itemCornerRadius, density) {
       object : Shape {
           override fun createOutline(
               size: Size,
               layoutDirection: LayoutDirection,
               density: Density
           ): Outline {
               val layerBounds = Rect(0f, 0f, size.width, size.height)
               val rect = carouselItemInfo.maskRect.intersect(layerBounds).inflate(0.5f)
               val r = with(density) { itemCornerRadius.toPx() }
               return Outline.Rounded(
                   androidx.compose.ui.geometry.RoundRect(
                       rect = rect,
                       cornerRadius = CornerRadius(r, r)
                   )
               )
           }
       }
   }
   ```
3. Wrap `isPageVisible` in `derivedStateOf` so `currentPageOffsetFraction` is never read directly in composition scope, and pass `strategy = strategyProvider`:
   ```kotlin
   val isPageVisible by remember(state.pagerState, page, carouselStyle) {
       derivedStateOf {
           if (carouselStyle != CarouselStyle.ONE_PEEK) {
               true
           } else {
               val maxVisiblePage = if (state.pagerState.currentPageOffsetFraction > 0.01f) {
                   state.pagerState.currentPage + 2
               } else {
                   state.pagerState.currentPage + 1
               }
               page <= maxVisiblePage
           }
       }
   }
   val animatedAlpha by animateFloatAsState(
       targetValue = if (isPageVisible) 1f else 0f,
       animationSpec = tween(durationMillis = 200),
       label = "CarouselItemAlpha"
   )

   Box(
       modifier = Modifier
           .graphicsLayer { alpha = animatedAlpha }
           .carouselItem(
               index = page,
               state = state,
               strategy = strategyProvider,
               carouselItemDrawInfo = carouselItemInfo,
               clipShape = clipShape
           )
   ) {
       scope.content(page)
   }
   ```
4. In `CarouselItemModifierNodeElement.update(node: CarouselItemModifierNode)` (around line 480), only invalidate measurement when layout inputs change:
   ```kotlin
   override fun update(node: CarouselItemModifierNode) {
       val needsRelayout = node.index != index || node.state !== state || node.strategy !== strategy
       node.index = index
       node.state = state
       node.strategy = strategy
       node.carouselItemDrawInfo = carouselItemDrawInfo
       node.clipShape = clipShape
       if (needsRelayout) {
           node.invalidateMeasurement()
       }
   }
   ```

---

### TASK 4: Fast-Path Static Rows & Fix Modifier Order in `EnhancedSongListItem.kt`
**File:** `app/src/main/java/com/theveloper/pixelplay/presentation/components/subcomps/EnhancedSongListItem.kt`

**Why:** 99% of rows in `LazyColumn` are neither the active playing song (`isHighlighted == false`) nor multi-selected (`isSelected == false`). Creating `updateTransition` with 3 `animateFloat` animations, 7 `lerpColor` calls, 3 `lerpDp` calls, and 2 `AbsoluteSmoothCornerShape` objects on every newly scrolled row causes unnecessary CPU & allocation overhead during fast fling gestures.

**Changes:**
1. Track whether a bound row has ever been active during its composition lifetime:
   ```kotlin
   val isHighlighted = (isCurrentSong || isPlaying) && !isSelectionMode
   var hasEverAnimated by remember { mutableStateOf(isHighlighted || isSelected) }
   if (isHighlighted || isSelected) {
       hasEverAnimated = true
   }
   ```
2. When `!hasEverAnimated` (the fast path for 99% of list rows), skip `updateTransition`, `lerpColor`, and `lerpDp` completely and assign static values (`highlightProgress = 0f`, `selectionVisualProgress = 0f`, `selectionScaleProgress = 0f`, `animatedCornerRadius = baseRectRadius`, `animatedAlbumRadius = 12.dp`, and direct `colors.*` references). Only instantiate `updateTransition` inside `if (hasEverAnimated)`.
3. Fix the modifier order violation at line 386 (`AGENTS.md` Step 5.2 — never place `.padding` after `.size`):
   ```kotlin
   // BEFORE (BUG - shrinks button width from 36.dp to 32.dp):
   modifier = Modifier.size(36.dp).padding(end = 4.dp)

   // AFTER (CORRECT):
   modifier = Modifier.padding(end = 4.dp).size(36.dp)
   ```

---

### TASK 5: Fix In-Composition State Mutation in `QueueBottomSheet.kt` & Main-Thread Queue Check in `PlaybackDispatchStateHolder.kt`
**Files:**
- `app/src/main/java/com/theveloper/pixelplay/presentation/components/QueueBottomSheet.kt`
- `app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/PlaybackDispatchStateHolder.kt`

**Why:**
1. In `QueueBottomSheet.kt` (lines 331–337, 439–483), `displaySongsSignature` uses `System.identityHashCode(displaySongs)` and calls `remapCommittedKeysForDisplay(displaySongs)` **directly inside the `@Composable` function body**, mutating `committedDisplaySongIds`, `committedDisplayKeys`, and `reorderPreviewQueueSignature` during composition!
2. In `PlaybackDispatchStateHolder.kt` (lines 398–406, 446–449), `currentQueue.matchesSongOrder(playbackContext)` iterates through the entire song list synchronously on the Main Thread when starting playback.

**Changes:**
1. **In `QueueBottomSheet.kt`:**
   - Move `remapCommittedKeysForDisplay` out of the composition body or compute keys deterministically via `remember(displaySongs)` without mutating `mutableStateOf` during composition. Specifically, keep a `remember`ed holder (`private class CommittedKeyTracker(var songIds: List<String>, var keys: List<String>)`) updated inside `remember(displaySongs)` so keys are derived in a single pass with **zero** `mutableStateOf` writes during composition, and update `reorderPreviewQueue` inside `LaunchedEffect(displaySongsSignature, isReordering, pendingReorderCommit)`.
2. **In `PlaybackDispatchStateHolder.kt`:**
   - Move the `currentQueue.matchesSongOrder(playbackContext)` check inside the background/dispatcher coroutine or add a fast reference-equality check (`if (this === contextSongs) return true`) at the top of `matchesSongOrder`:
     ```kotlin
     private fun List<Song>.matchesSongOrder(contextSongs: List<Song>): Boolean {
         if (this === contextSongs) return true
         if (size != contextSongs.size) return false
         if (isEmpty()) return true
         // Fast-check boundary elements before scanning all indices
         if (first().id != contextSongs.first().id || last().id != contextSongs.last().id) return false
         return indices.all { index -> this[index].id == contextSongs[index].id }
     }
     ```

---

### TASK 6: Consolidate `DataStore` Flows & Isolate Lyric Row Blur in `LyricsSheet.kt`
**File:** `app/src/main/java/com/theveloper/pixelplay/presentation/components/LyricsSheet.kt`

**Why:**
1. Lines 326–389 create **8 separate `context.dataStore.data.map`** flows directly inside `LyricsSheet` composition, creating 8 independent `DataStore` collectors every time `LyricsSheet` is composed.
2. In `SyncedLyricsList` (lines 1531–1560), passing `currentLineIndex` and `distanceFromCurrent` directly to every visible `LyricLineRow` forces every visible row to recompose and recalculate `animateFloatAsState` for `Modifier.blur(...)` on every line change.

**Changes:**
1. Combine the 8 `DataStore` preferences in `LyricsSheet.kt` into a single `private data class LyricsUiPreferences(...)` and collect a **single** `remember(context) { context.dataStore.data.map { prefs -> LyricsUiPreferences(...) }.distinctUntilChanged() }` flow with `collectAsState`.
2. In `SyncedLyricsList` / `LyricLineRow` (lines 1531–1560, 1703–1740):
   - Clamp `val bucketedDistance = distanceFromCurrent.coerceAtMost(4)` before passing it to `LyricLineRow`! Since `calculateBlurRadius` returns the exact same `maxBlur` for all `distance >= 4` (`else -> maxBlur`), clamping `distanceFromCurrent` to `0..4` ensures that lines more than 4 rows away from the active lyric line **never recompose** when `currentLineIndex` advances!
   - Also pass `isPastLine = currentLineIndex != -1 && index < currentLineIndex` as a `Boolean` instead of passing raw `currentLineIndex: Int` into `LyricLineRow`, so `LyricLineRow` only takes `isCurrentLine: Boolean`, `isPastLine: Boolean`, and `clampedDistanceFromCurrent: Int` (`0..4`).

---

### TASK 7: Debounce Room Table Invalidations & Avoid Redundant Mapping in `LibraryStateHolder.kt`
**File:** `app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/LibraryStateHolder.kt`

**Why:** Whenever a single song's `is_favorite` or `lyrics` column is updated in SQLite, Room invalidates the `songs` table. `startObservingSongs()`, `startObservingAlbums()`, `startObservingArtists()`, and `startObservingFolders()` immediately re-query and re-map thousands of `SongEntity`, `AlbumEntity`, and `ArtistEntity` objects in parallel.

**Changes:**
1. In `startObservingSongs()`, `startObservingAlbums()`, `startObservingArtists()`, and `startObservingFolders()` (`LibraryStateHolder.kt` lines 258–335):
   - Add `.distinctUntilChanged()` on the repository flows before transforming/sorting on `Dispatchers.Default`.
   - In `startObservingSongs()`, only rebuild `_allSongsById` and `_allSongs` if the emitted list is not structurally equal to `_allSongs.value`.

---

### TASK 8: Fix ExoPlayer Transition Contention & Mid-Scroll Prefetch (`MediaControllerSyncStateHolder.kt`, `PrefetchAlbumNeighbors.kt`, `AlbumCarouselSelection.kt`)
**Files:**
- `app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/MediaControllerSyncStateHolder.kt`
- `app/src/main/java/com/theveloper/pixelplay/presentation/components/scoped/PrefetchAlbumNeighbors.kt`
- `app/src/main/java/com/theveloper/pixelplay/presentation/components/AlbumCarouselSelection.kt`

**Changes:**
1. **In `MediaControllerSyncStateHolder.kt` (around line 399):**
   - At the start of `syncDisplayedMediaItemIfChanged(player: Player)`, return early if `transitionSchedulerJob?.isActive == true`:
     ```kotlin
     private fun syncDisplayedMediaItemIfChanged(player: Player) {
         if (isRemoteSessionControllingPlayback()) return
         if (transitionSchedulerJob?.isActive == true) return
     ```
   - This prevents `onMediaMetadataChanged` and `onTimelineChanged` from canceling the in-flight `onMediaItemTransition` coroutine and duplicating transition work on the Main Thread.
2. **In `PrefetchAlbumNeighbors.kt` (around line 72):**
   - Use `rememberUpdatedState(anchorIndex)` instead of keying `LaunchedEffect` on `anchorIndex`, and wait until `!pagerState.isScrollInProgress` before enqueuing neighbor Coil requests:
     ```kotlin
     val latestAnchorIndex = androidx.compose.runtime.rememberUpdatedState(anchorIndex)

     LaunchedEffect(pagerState, queue, requestTargetSize) {
         snapshotFlow {
             if (pagerState.isScrollInProgress) null
             else latestAnchorIndex.value ?: pagerState.currentPage
         }
             .distinctUntilChanged()
             .collect { page ->
                 if (page == null) return@collect
                 // enqueue neighbor requests...
             }
     }
     ```
3. **In `AlbumCarouselSelection.kt` (around line 191):**
   - Keep `val latestRequestedScrollIndex by rememberUpdatedState(requestedScrollIndex)` and ignore manual-swipe settle events when `latestRequestedScrollIndex == settled` so programmatic button skips never re-trigger `latestOnSongSelected`.

---

### TASK 9: Eliminate First-Frame Expand Spike Between `UnifiedPlayerPrewarmLayer` and `UnifiedPlayerMiniAndFullLayers`
**Files:**
- `app/src/main/java/com/theveloper/pixelplay/presentation/components/UnifiedPlayerSheetV2.kt`
- `app/src/main/java/com/theveloper/pixelplay/presentation/components/UnifiedPlayerSheetLayers.kt`

**Why:** Currently, `UnifiedPlayerPrewarmLayer` composes a separate `FullPlayerContent` while `expansionFraction == 0f`. On the first frame of dragging (`expansionFraction > 0f`), `prewarmFullPlayer` becomes `false` (tearing down the prewarm tree) while `shouldRenderFullPlayer` becomes `true` (composing a brand-new `FullPlayerContent` inside `UnifiedPlayerMiniAndFullLayers`). Tearing down and composing `FullPlayerContent` from scratch on Frame 1 of the swipe gesture causes a first-frame stutter!

**Changes:**
1. In `UnifiedPlayerSheetV2.kt` (lines 298–303, 779–796) and `UnifiedPlayerSheetLayers.kt`:
   - Instead of rendering a separate `UnifiedPlayerPrewarmLayer` outside `UnifiedPlayerMiniAndFullLayers` that gets destroyed when `expansionFraction > 0f`, keep `FullPlayerContent` inside `UnifiedPlayerMiniAndFullLayers` whenever `shouldRenderFullPlayer || prewarmFullPlayer` is `true`!
   - When `expansionFraction == 0f` (prewarm state), `UnifiedPlayerMiniAndFullLayers` already sets `alpha = fullPlayerAlpha` (`0f`), `scaleX/Y = 0.96f`, and disables touch interception (`shouldBlockFullPlayerTouches = expansionFraction <= 0.05f`), so keeping the **same** composition instance alive transitions seamlessly from `expansionFraction == 0f` (prewarmed) into `expansionFraction > 0f` (expanding) with **zero composition or measurement work on Frame 1**!
   - Remove the redundant `UnifiedPlayerPrewarmLayer` call in `UnifiedPlayerSheetV2.kt`.

---

### TASK 10: Replace `SubcomposeLayout` in `AutoScrollingText` with `basicMarquee` (`MarqueeText.kt`)
**File:** `app/src/main/java/com/theveloper/pixelplay/presentation/components/MarqueeText.kt`

**Why:** `AutoScrollingText` (lines 94–213) uses `SubcomposeLayout` (measuring text twice via subcomposition) and allocates a `Brush.horizontalGradient` on every draw frame inside an offscreen layer (`CompositingStrategy.Offscreen`), whereas `AutoScrollingTextOnDemand` (lines 26–91 in the same file) already uses Compose's fast native `Modifier.basicMarquee` + cached `rememberGraphicsLayer`!

**Changes:**
1. Refactor `AutoScrollingText` in `MarqueeText.kt` to delegate to `AutoScrollingTextOnDemand(..., enabled = true)` (or use `Modifier.basicMarquee` + cached gradient `Brush` instead of `SubcomposeLayout`), eliminating subcomposition and per-frame `Brush` allocations across `QueuePlaylistSongItem` and player headers.

---

## PRE-OUTPUT VERIFICATION CHECKLIST
Before finishing, run and verify:
- [ ] Run `./gradlew compileDebugKotlin` and ensure **0 errors**.
- [ ] **M3E Compliance (`AGENTS.md`):**
  - No hardcoded `Color(...)` or hardcoded UI strings.
  - All spatial animations use `MotionScheme.expressive().{fast|default|slow}SpatialSpec()` and effects animations use `{fast|default|slow}EffectsSpec()`.
  - Modifier chains place outer `.padding(...)` **before** `.size(...)`.
  - Per-frame values (`currentPageOffsetFraction`, `expansionFraction`, animated colors) are isolated from root composition scope.
