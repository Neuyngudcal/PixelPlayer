# PROMPT FOR GEMINI 3.8 FLASH — FIX TRACK SWITCH LAG, FPS DROPS & SWIPE/BUTTON BACKGROUND COLOR DESYNC

You are a Principal Android Performance & Jetpack Compose Engineer working on **PixelPlayer** (`androidx.compose.material3:material3:1.5.0-alpha22`, Material 3 Expressive).
An in-depth architectural and composition audit of the player track-switching pipeline uncovered why:
1. **Manual swiping** on the album carousel only starts changing the background color **after** the album art finishes settling, whereas **pressing Next/Previous** changes the background color immediately alongside (or ahead of) the carousel slide.
2. **Pressing Next/Previous** causes severe **FPS drops / frame jank** due to a simultaneous cascade of recomposition storms, layout-weight thrashing, offscreen sheet recompositions, redundant `animateColorAsState` restarts, Compose subcompositions, and duplicate ViewModel/palette work on the Main Thread.

Your mission is to implement **every fix below** cleanly, systematically, and in strict compliance with **`AGENTS.md`** (5-step M3E workflow, `MotionScheme.expressive()`, draw/layout phase isolation, modifier chain ordering, zero hardcoded colors/strings, and preserving unrelated comments/docstrings).

---

## ROOT CAUSE SUMMARY

| # | Severity | Files | Exact Bottleneck |
| :--- | :--- | :--- | :--- |
| **1** | **CRITICAL** | `SheetThemeState.kt`, `UnifiedPlayerSheetV2.kt`, `UnifiedPlayerSheetLayers.kt`, `UnifiedPlayerOverlaysLayer.kt` | `rememberBatchAnimatedColorScheme` emits a brand-new `ColorScheme` on **every frame** (~350ms, ~42 frames @ 120Hz) and runs **twice** (`albumColorScheme` + `miniPlayerScheme`). Passing this per-frame `ColorScheme` through `LocalMaterialTheme` and `MaterialTheme` recomposes: (a) the entire `FullPlayerContent` tree, (b) the **hidden** `QueueBottomSheet` (kept warm when expanded), (c) the **hidden** `MiniPlayerContentInternal` (alpha = 0f when expanded), and (d) the outer `UnifiedPlayerSheetV2` `Box` via `.background(playerAreaBackground)`. |
| **2** | **CRITICAL** | `ToggleSegmentButton.kt`, `MarqueeText.kt`, `FullPlayerContent.kt`, `WavySliderExpressive.kt` | Reading animated `LocalMaterialTheme.current` in composition scope causes `ToggleSegmentButtonContainer` to cancel and restart 3 `animateColorAsState` coroutines **on every single frame** (~126 restarts per skip), forces `AutoScrollingText`'s `drawWithCache` to rebuild `Brush.horizontalGradient` + `Offscreen` layer every frame, and recomposes `WavySliderExpressive` + `FullPlayerControlsSection` + `FullPlayerSongMetadataSection` every frame. |
| **3** | **CRITICAL** | `AnimatedPlaybackControls.kt`, `FullPlayerContent.kt` | `prevWeight`, `playWeight`, `nextWeight` animate `Modifier.weight(...)` over 400ms (`m3ExpressiveSpatialSpec(400)`), forcing a full `Row` + parent measure/layout pass on every frame during a skip, while `AbsoluteSmoothCornerShape` inside `graphicsLayer` rebuilds clip paths on `playCorner` changes. Also `SongMetadataDisplaySection` redundantly collects `stablePlayerState` again. |
| **4** | **CRITICAL** | `AlbumCarouselSelection.kt`, `RoundedParallaxCarousell.kt`, `OptimizedAlbumArt.kt`, `PrefetchAlbumNeighbors.kt` | (a) `AlbumCarouselSection` reads `carouselState.pagerState.currentPage == index` **in composition** for every page, recomposing all visible pages mid-swipe/skip. (b) `OptimizedAlbumArt` uses `SubcomposeAsyncImage` + `.crossfade(350)` per pager page (creating subcompositions and crossfade transitions inside the pager). (c) `CarouselItemModifierNode` runs double keyline lookups + 4 `MutableState` writes per frame even in `CarouselStyle.NO_PEEK`. (d) `PrefetchAlbumNeighbors` pauses prefetching whenever `pagerState.isScrollInProgress == true`, starving rapid skips. |
| **5** | **HIGH** | `AlbumCarouselSelection.kt`, `FullPlayerContent.kt`, `ThemeStateHolder.kt` | **Manual Swipe vs Button Skip Desync:** Manual swipe never emits a target/preview color until `pagerState.isScrollInProgress` becomes `false` (`userDragSettlePending` -> `latestOnSongSelected`). Button skip yields only 1 microtask (`yield()`), firing `seekToNext()` immediately before Frame 1 of the carousel animation and running color change + player transition + Room/lyrics work on Frame 1. |
| **6** | **HIGH** | `MediaControllerSyncStateHolder.kt`, `PlayerViewModel.kt`, `ColorSchemeProcessor.kt`, `PrewarmFullPlayerState.kt` | (a) `MediaControllerSyncStateHolder` AND `PlayerViewModel.stablePlayerState.mapLatest` **both** call `themeStateHolder.extractAndGenerateColorScheme(...)` on every track switch; `ColorSchemeProcessor.markProcessing`/`markComplete` are never wired up, risking duplicate bitmap decoding & Celebi quantization. (b) `musicRepository.getSong(songId)` triggers a second `stablePlayerState` emission mid-animation. (c) `rememberPrewarmFullPlayer` toggles `prewarmFullPlayer` `true -> false` after 32ms on every `currentSongId` change even when FullPlayer is already expanded. |

---

## DETAILED IMPLEMENTATION PLAN

### TASK 1: Eliminate Per-Frame ColorScheme Recomposition Storms (`SheetThemeState.kt`, `UnifiedPlayerSheetV2.kt`, `UnifiedPlayerSheetLayers.kt`, `UnifiedPlayerOverlaysLayer.kt`)

1. **Deduplicate `rememberBatchAnimatedColorScheme` in `SheetThemeState.kt`:**
   - Currently `rememberSheetThemeState` runs `rememberBatchAnimatedColorScheme` twice (`albumColorScheme` and `miniPlayerScheme`) on identical inputs (`rawAlbumColorScheme` == `rawMiniPlayerScheme`).
   - Run **one** `rememberBatchAnimatedColorScheme(rawAlbumColorScheme)` and reuse it (or only compute a separate scheme if their target values actually differ).
   - Provide a **non-animated target `ColorScheme`** (`targetAlbumColorScheme = rawAlbumColorScheme`) inside `SheetThemeState` as well, for offscreen/warm trees that should not recompose 42 times while hidden.

2. **Do NOT animate hidden `QueueBottomSheet` in `UnifiedPlayerOverlaysLayer.kt`:**
   - In `UnifiedPlayerQueueLayer`, when `!showQueueSheet && !isQueueCollapsing` (the warm-but-hidden state), pass the **settled/target** `ColorScheme` (not the frame-by-frame `albumColorScheme`) to `MaterialTheme(colorScheme = ...)`. Only pass the animated `albumColorScheme` when `showQueueSheet || isQueueCollapsing`.
   - This eliminates ~42 full recompositions of the 2,175-line `QueueBottomSheet` on every track switch.

3. **Gate `MiniPlayerContentInternal` visibility in `UnifiedPlayerSheetLayers.kt`:**
   - Look at line 91: MiniPlayer's alpha is `(1f - (f / 0.22f)).coerceIn(0f, 1f)`, meaning it is 100% invisible whenever `f >= 0.22f`.
   - Add `val shouldRenderMiniPlayer by remember { derivedStateOf { playerContentExpansionFraction.value < 0.28f } }`. Wrap the MiniPlayer `Box` in `if (shouldRenderMiniPlayer)` so that when FullPlayer is expanded, changing tracks does NOT tear down and rebuild `MiniPlayerContentInternal`'s `SmartImage`, `AutoScrollingText`, and icons.

4. **Move `playerAreaBackground` to Draw Phase in `UnifiedPlayerSheetV2.kt`:**
   - Around line 696, replace:
     ```kotlin
     .background(
         color = playerAreaBackground,
         shape = sheetInteractionState.playerShadowShape
     )
     ```
     with a lambda-backed `drawBehind` (or `graphicsLayer` + `drawBehind` inside the clipped bounds, reading `() -> sheetThemeState.playerAreaBackground` without triggering outer `Box` recomposition):
     - Note: because `.clip(sheetInteractionState.playerShadowShape)` immediately follows, `.drawBehind { drawRect(color = playerAreaBackgroundProvider()) }` placed **after** `.clip(sheetInteractionState.playerShadowShape)` (or `drawOutline(shape.createOutline(...), color = playerAreaBackgroundProvider())` before `.clip`) keeps the exact rounded silhouette while moving background color animation **100% into the Draw Phase** (0 recompositions on `UnifiedPlayerSheetV2` per frame).

---

### TASK 2: Isolate Color Animation Reads in `FullPlayerContent.kt`, `ToggleSegmentButton.kt`, `MarqueeText.kt`, and `WavySliderExpressive.kt`

1. **Fix `ToggleSegmentButtonContainer` in `ToggleSegmentButton.kt`:**
   - Currently `animateColorAsState(targetValue = ...)` restarts on every frame because `activeColor` / `inactiveColor` come from `LocalMaterialTheme.current` while `rememberBatchAnimatedColorScheme` is ticking.
   - Replace `animateColorAsState` with a `Float` state transition (`val activeFraction by animateFloatAsState(targetValue = if (active) 1f else 0f, animationSpec = motionScheme.defaultEffectsSpec(), label = "segmentActiveFraction")`), and compute the blended color inside `.drawBehind { ... }` using `rememberUpdatedState` for `activeColor`, `inactiveColor`, `enabled`:
     ```kotlin
     val latestActiveColor by rememberUpdatedState(activeColor)
     val latestInactiveColor by rememberUpdatedState(inactiveColor)
     val latestEnabled by rememberUpdatedState(enabled)
     ```
     Inside `.drawBehind { val base = lerp(latestInactiveColor, latestActiveColor, activeFraction); drawRect(if (latestEnabled) base else base.copy(alpha = 0.5f)) }`.
   - This eliminates 3 concurrent `animateColorAsState` coroutine cancellations per frame across Shuffle/Repeat/Favorite buttons!

2. **Fix `AutoScrollingText` in `MarqueeText.kt`:**
   - Currently `drawWithCache` captures `gradientEdgeColor` (a `Color` parameter that changes every frame during palette animation), forcing `drawWithCache` to invalidate and reallocate `Brush.horizontalGradient` + `Offscreen` layer 42 times per skip.
   - Actually, look at lines 163–196 of `MarqueeText.kt`: `BlendMode.DstIn` only uses the **alpha channel** of the destination-in gradient (`Color.Transparent` vs opaque `Color.Black` / `gradientEdgeColor.copy(alpha = 1f)`)! The RGB values of `gradientEdgeColor` in `BlendMode.DstIn` are ignored by Porter-Duff `DstIn` (`Sa * Dc, Sa * Da`)!
   - Replace `gradientEdgeColor` inside `DstIn` `Brush.horizontalGradient` with `Color.Black` (alpha = 1f) and animate only a `Float` `leftEdgeAlpha` via `animateFloatAsState` (read inside `onDrawWithContent` or `drawRect(brush, alpha = ...)`), so `drawWithCache` NEVER invalidates when `LocalMaterialTheme` colors change!

3. **Avoid Parent Scope Recomposition & Duplicate Collection in `FullPlayerContent.kt`:**
   - Remove the duplicate `val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()` inside `SongMetadataDisplaySection` (line 1606). Pass `isBufferingProvider: () -> Boolean` from `UnifiedPlayerSheetLayers.kt` -> `FullPlayerContent` -> `FullPlayerSongMetadataSection` -> `SongMetadataDisplaySection`.
   - In `FullPlayerControlsSection`, `FullPlayerProgressSection`, `FullPlayerSongMetadataSection`, and `BottomToggleRow`, avoid reading `LocalMaterialTheme.current` at the outer container level if only leaf `Icon`/`Text`/`Canvas` needs it, OR pass color providers (`() -> Color`) to `WavySliderExpressive` / `AnimatedPlaybackControls` / `EfficientTimeLabels` so container layouts don't recompose on every frame of color bleed.
   - Specifically in `WavySliderExpressive.kt`: add an overload or update `activeTrackColor`, `inactiveTrackColor`, `thumbColor` (or wrap via `rememberUpdatedState` + `Canvas` / draw-only lambda) so color changes during palette transitions only trigger `Draw` invalidation, not `WavySliderExpressive` recomposition.

---

### TASK 3: Eliminate Layout-Weight Thrashing in `AnimatedPlaybackControls.kt`

1. **Replace `m3ExpressiveSpatialSpec(400)` with `MotionScheme.expressive().fastSpatialSpec()` in `FullPlayerContent.kt` (line 1235):**
   - Per `AGENTS.md` Step 4, small interactive controls (`AnimatedPlaybackControls`) MUST use `MaterialTheme.motionScheme.fastSpatialSpec()` instead of a 400ms tween.

2. **Eliminate Per-Frame `Modifier.weight(...)` Re-Measurement in `AnimatedPlaybackControls.kt`:**
   - Currently animating `prevWeight`, `playWeight`, `nextWeight` via `Modifier.weight(...)` triggers a full `Row` + parent re-measure and re-layout on every frame for 400ms.
   - Replace the 3 `Modifier.weight(...)` modifiers with a custom lightweight `Layout` (or `Modifier.layout` / horizontal proportional placement inside a single `Layout` composable) that reads `prevWeight`, `playWeight`, `nextWeight` **inside the Measure/Place block** ONLY for those 3 child boxes, keeping the outer `AnimatedPlaybackControls` size fixed (`fillMaxWidth().height(height)`) so parent containers never re-measure!
   - Alternatively (even cleaner and zero-allocation): cache `AbsoluteSmoothCornerShape` only when `playCorner` changes in `drawWithCache` / `graphicsLayer`, and ensure `playCorner` uses `motionScheme.fastSpatialSpec<Dp>()`.
   - Reduce `lastClicked` lock duration for `NEXT`/`PREVIOUS` from `600L` to match the actual `fastSpatialSpec()` settle (~`280L`–`320L`), or only lock `playPauseVisualState` briefly (`250L`) so rapid skips feel responsive rather than sluggish.

---

### TASK 4: Optimize `AlbumCarouselSection`, `RoundedParallaxCarousell`, `OptimizedAlbumArt` & `PrefetchAlbumNeighbors`

1. **Remove `currentPage` Composition Read in `AlbumCarouselSelection.kt` (line 207):**
   - Remove `val isFocusedItem = carouselState.pagerState.currentPage == index` from the item `content = { index -> ... }` composition scope.
   - Instead, keep `clickable` enabled when `song.albumId != -1L` and check `if (carouselState.pagerState.currentPage == index) onAlbumClick(song)` inside `onClick`, eliminating mid-scroll full-page recomposition when `currentPage` flips!

2. **Eliminate `SubcomposeAsyncImage` & Crossfade Overhead in `AlbumCarouselSection` / `OptimizedAlbumArt.kt`:**
   - In `OptimizedAlbumArt.kt`, add a fast path (or parameter `enableCrossfade: Boolean = false` / `useSubcomposition: Boolean = false` for pager items when `placeholderModel == null` and `uri != null`):
     - Use Coil's `AsyncImage` directly instead of `SubcomposeAsyncImage` when `placeholderModel == null` (which is true for all local/non-Telegram songs!). `SubcomposeAsyncImage` creates a separate `SubcomposeLayout` per pager item and triggers two composition passes (`Loading` -> `Success`).
     - Set `crossfade(0)` (no crossfade) for `AlbumCarouselSection` items because the horizontal pager slide IS the visual transition; crossfading inside an already-sliding card causes muddy alpha blending and extra GPU overdraw, especially when images are prefetched into MemoryCache.
     - Keep `placeholderModel` fallback (`SubcomposeAsyncImage`) only when `placeholderModel != null` (Telegram low-res thumb -> high-res).

3. **Fast-Path `CarouselItemModifierNode` for `CarouselStyle.NO_PEEK` in `RoundedParallaxCarousell.kt`:**
   - In `RoundedCarousel`, when `carouselStyle == CarouselStyle.NO_PEEK`, every item has fixed width `carouselWidthPx` and full bounds (`maskRect` does not shrink/morph like `ONE_PEEK`/`TWO_PEEK`).
   - Add a lightweight path (or short-circuit in `CarouselItemModifierNode`) for `NO_PEEK` that skips `getKeylineListForScrollOffset(..., roundToNearestStep = true)` and skips updating the 4 `CarouselItemDrawInfoImpl` `MutableState`s per frame when `maskRect` is simply full layer bounds, applying a static `RoundedCornerShape(itemCornerRadius)` clip instead of per-frame `Shape.createOutline` intersection!
   - Also in `RoundedCarousel` (lines 314–318), replace `tween(durationMillis = 200)` with `MaterialTheme.motionScheme.fastEffectsSpec()`.

4. **Do NOT Block `PrefetchAlbumNeighbors` During Scrolls (`PrefetchAlbumNeighbors.kt`):**
   - Currently line 76 has `if (pagerState.isScrollInProgress) null`, which cancels/skips neighbor prefetching while the user is swiping or rapid-skipping!
   - Change `PrefetchAlbumNeighbors` to prefetch `anchorIndex - radius .. anchorIndex + radius` (use `radius = 2` in `AlbumCarouselSection`) keyed on `anchorIndex ?: pagerState.currentPage` **regardless of `isScrollInProgress`**, deduplicating recently enqueued URIs so rapid Next/Prev taps always find the next 1–2 covers already decoded in Coil's `MemoryCache`!

---

### TASK 5: Synchronize Background Color Between Manual Swipe and Button Skip + Remove Frame-1 Skip Contention

1. **Interactive Background Color Sync During Manual Swipe (`AlbumCarouselSelection.kt` + `SheetThemeState.kt` + `ThemeStateHolder.kt`):**
   - Expose a live swipe-palette preview from `AlbumCarouselSection` (only while `isUserDragging || userDragSettlePending`):
     - Using `snapshotFlow { pagerState.currentPage to pagerState.currentPageOffsetFraction }` (or a `State`/callback passed up to `UnifiedPlayerSheetV2` -> `rememberSheetThemeState`), determine the current page's song URI and the target neighbor page's song URI (`offsetFraction > 0 -> currentPage + 1`, `offsetFraction < 0 -> currentPage - 1`), and pass `(leftUri, rightUri, fraction)` or `previewTargetUri`!
     - **Recommended clean M3E behavior (zero audio stutter, instant visual feedback):**
       - Do **NOT** call `onSongSelected` mid-drag (that would thrash ExoPlayer). Keep `onSongSelected` on settle (`!isScrollInProgress`).
       - **DO** resolve the neighbor song's already-cached `ColorSchemePair?` via `themeStateHolder.getAlbumColorSchemeFlow(neighborUri, eager = true)` (already built into `ThemeStateHolder`!) and smoothly interpolate (`lerpColorScheme(currentScheme, neighborScheme, swipeFraction)`) **in real time with the user's finger** during `isUserDragging || userDragSettlePending` (or snap the target scheme as soon as `settledPage` / midpoint `abs(offsetFraction) > 0.5f` crosses during a fling)!
       - When the swipe settles on the new song, `currentSong` updates to match the already-blended scheme seamlessly (`toScheme == target`), so there is **zero post-settle color delay** and **zero flicker**!
       - If the user cancels the swipe and snaps back to the original page, the background color glides back seamlessly with the album art!

2. **Coordinate Button Skip Timing (`FullPlayerContent.kt`):**
   - In `FullPlayerContent.kt` (lines 448–459), `kotlinx.coroutines.yield()` only yields one coroutine dispatch, not a UI frame, so `MediaController.seekToNext()` + `onMediaItemTransition` + DB/lyrics work collides with Frame 1 of `carouselState.animateScrollToItem(...)`.
   - Use `androidx.compose.runtime.withFrameNanos { }` (wait for the optimistic carousel scroll frame to commit to Choreographer) before dispatching `latestOnNext()` / `latestOnPrevious()`, and keep `SKIP_COMMAND_GUARD_MS` minimal (`~64L`–`80L`) so rapid taps feel instant while Frame 1 of the carousel animation stays locked at 120 FPS.
   - In `LaunchedEffect(currentQueueIndex, pendingCarouselIndex)` (lines 433–446), avoid holding `pendingCarouselIndex` for an arbitrary `delay(550)`/`950` if `carouselState` has already settled; clear `pendingCarouselIndex` cleanly as soon as `currentQueueIndex == pendingCarouselIndex` and the scroll settles (or after a safety timeout).

---

### TASK 6: Deduplicate Color Extraction & Defer Non-Critical Post-Skip Work (`MediaControllerSyncStateHolder.kt`, `PlayerViewModel.kt`, `ColorSchemeProcessor.kt`, `PrewarmFullPlayerState.kt`)

1. **Deduplicate `extractAndGenerateColorScheme` calls:**
   - `PlayerViewModel.kt` (lines 805–819) already observes `stablePlayerState.map { it.currentSong?.albumArtUriString }.distinctUntilChanged().mapLatest { ... }`.
   - Remove the redundant `themeStateHolder.extractAndGenerateColorScheme(...)` launches inside `MediaControllerSyncStateHolder.kt` (`syncDisplayedMediaItemIfChanged` line 448, `applyInitialControllerState` line 527, `onMediaItemTransition` line 736) — OR route all of them through a single `mapLatest` pipeline in `PlayerViewModel` / `ThemeStateHolder` so the same track transition never runs `extractAndGenerateColorScheme` twice!
   - In `ColorSchemeProcessor.kt`, wire up `inProgressUris` / `CompletableDeferred<ColorSchemePair?>` deduplication inside `getOrGenerateColorScheme` so concurrent requests (e.g., neighbor preloader + active track extractor) for the same `cacheKey` coalesce onto a single decode + Celebi quantization job.
   - Ensure `ThemeStateHolder.extractAndGenerateColorScheme` checks `colorSchemeProcessor`'s in-memory `LruCache` synchronously/fast, and dispatches any Room DB read (`loadCachedColorScheme`) or bitmap work on `Dispatchers.IO` rather than starting on `Dispatchers.Main.immediate`.

2. **Prevent Redundant Post-Skip Recompositions in `PlayerViewModel.kt` & `PrewarmFullPlayerState.kt`:**
   - In `PlayerViewModel.kt` lines 880–900 (`flatMapLatest { musicRepository.getSong(songId) }`): after `onMediaItemTransition` updates `stablePlayerState`, defer the Room observation or skip emitting a `stablePlayerState` update if the display-relevant fields (`title`, `artist`, `album`, `albumArtUriString`, `duration`, `lyrics`) haven't meaningfully changed for UI rendering during the skip animation.
   - Similarly, in `MediaControllerSyncStateHolder.kt:738`, defer `cb.loadLyricsForCurrentSong()` slightly (e.g., `delay(180)` inside `transitionSchedulerJob` if the lyrics sheet is closed) so LRC file parsing/DB I/O never competes with the ~300–500ms skip animation!
   - In `PrewarmFullPlayerState.kt`: do **not** run the `prewarmFullPlayer = true -> delay(32) -> false` toggle when the sheet is already expanded (`currentSheetState == PlayerSheetState.EXPANDED`) — pass `isExpanded: Boolean` into `rememberPrewarmFullPlayer` and return `false` immediately when `isExpanded == true`.

---

## VERIFICATION CHECKLIST (MUST COMPLETE AFTER IMPLEMENTATION)

1. Run Kotlin compilation / unit tests (`./gradlew testDebugUnitTest` or the relevant test tasks) and fix any compile/test issues.
2. Verify against `AGENTS.md`:
   - Spatial animations (`AnimatedPlaybackControls`, `AlbumCarouselSection`) use `MotionScheme.expressive().fastSpatialSpec()` / `defaultSpatialSpec()`.
   - Effects animations (`rememberBatchAnimatedColorScheme`, `ToggleSegmentButton`, `CarouselItemAlpha`) use `MotionScheme.expressive().*EffectsSpec()` (`dampingRatio = 1.0f`).
   - Draw/layout phase isolation: background color (`playerAreaBackground`), button segment fill, and slider colors read inside `drawBehind`/`Canvas`/`graphicsLayer`.
   - Zero duplicate `extractAndGenerateColorScheme` calls on track skip.
   - Manual swipe smoothly transitions background color **during** the swipe (in sync with the album art), without breaking audio settle behavior or cancel-swipe rollback.
   - Hidden `QueueBottomSheet` and hidden `MiniPlayerContentInternal` do NOT recompose on frame ticks during FullPlayer track switches.
