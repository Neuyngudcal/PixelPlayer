# PIXELPLAYER — MATERIAL 3 EXPRESSIVE (M3E) DESIGN & ENGINEERING RULES

You are working on **PixelPlayer**, an Android music player built with **Jetpack Compose** (`androidx.compose.material3:material3:1.5.0-alpha22`), **Material 3 Expressive (`@OptIn(ExperimentalMaterial3ExpressiveApi::class)`)**, **Google Sans Flex Rounded (`GoogleSansRounded`)**, and **Smooth Corners (`AbsoluteSmoothCornerShape`)**.

Every UI/UX design decision and `@Composable` implementation you generate MUST strictly comply with the **Material 3 Expressive (M3E)** specification and the workflow below.

---

## 1. MANDATORY 5-STEP PRE-CODE DESIGN WORKFLOW

Before writing or modifying ANY UI/UX code, you MUST mentally execute and verify this 5-step sequence:

```
[Step 1: Spatial Hierarchy & Responsive Bounds]
       ↓
[Step 2: Semantic 48-Role Color Mapping]
       ↓
[Step 3: Expressive Shape & State Morphing]
       ↓
[Step 4: Synchronized M3E Motion Choreography (Spatial vs. Effects)]
       ↓
[Step 5: Compose Phase Isolation & Modifier Order Audit]
```

### Step 1: Spatial Hierarchy & Responsive Bounds
1. **Identify the Focal (Hero) Element:** Every screen or sheet MUST have at most **one** primary hero element (e.g., Album Art Carousel in FullPlayer, Active Synced Line in LyricsSheet, Primary Play/Pause CTA).
2. **Enforce Portrait & Landscape Symmetry:**
   - NEVER size square or aspect-locked media using `maxWidth` alone. Always bound by `val baseBound = minOf(maxWidth, maxHeight)` inside `BoxWithConstraints`.
   - When hiding a `TopAppBar` in Landscape, all critical actions (Collapse, Cast/Audio Output, Queue, Lyrics) MUST remain accessible in the secondary column/row.
3. **Enforce System Insets & Floating Toolbar Clearance:**
   - Every bottom overlay, floating toolbar, or expandable FAB menu MUST account for `WindowInsets.navigationBars` (or `getNavigationBarHeight()`) PLUS the height of any persistent bottom bar below it.
   - NEVER combine `LazyColumn(contentPadding = PaddingValues(top = X.dp))` with `item { Box(Modifier.fillParentMaxSize()) }` for centered empty/loading states (this shifts the center down by `X / 2` and creates phantom scroll). Use a non-scrolling `Box(Modifier.fillMaxSize().padding(...), contentAlignment = Alignment.Center)`.

### Step 2: Semantic 48-Role Color Mapping
1. **Theme Scope:**
   - Inside the NowPlaying / Player tree (`UnifiedPlayerSheetV2`, `FullPlayerContent`, `LyricsSheet`, `QueueBottomSheet`), ALWAYS read colors from `LocalMaterialTheme.current`.
   - Outside the Player tree, read from `MaterialTheme.colorScheme`.
   - NEVER hardcode hex colors (`Color(0xFF...)`) or use `Color.White` / `Color.Black` for surfaces, icons, or text inside components.
2. **Surface Container Hierarchy (Tonal Elevation over Shadow):**
   - Use M3E tonal surface roles to express depth instead of heavy drop shadows:
     - Background / Deepest layer: `surfaceDim` or `surfaceContainerLowest`
     - Standard cards / Queue items: `surfaceContainerLow` → `surfaceContainer`
     - Elevated toolbars / Floating pills: `surfaceContainerHigh` → `surfaceContainerHighest`
3. **Fixed Accent Roles for Expressive Controls:**
   - Use `primaryFixed`, `primaryFixedDim`, `onPrimaryFixed`, `onPrimaryFixedVariant`, `secondaryFixed`, `secondaryFixedDim`, `onSecondaryFixed`, `tertiaryFixed`, `tertiaryFixedDim`, `onTertiaryFixed` for high-emphasis transport buttons (`AnimatedPlaybackControls`) and toggle segments (`BottomToggleRow`) so contrast stays locked across Light/Dark/Album-Art themes.
4. **Full 48-Role Interpolation:**
   - Any custom `ColorScheme` transition (`lerpColorScheme`) MUST interpolate all **48 fields** of `ColorScheme`, including all 12 `*Fixed*` roles. Never omit fields in `to.copy(...)`.

### Step 3: Expressive Shape System & State Morphing
1. **8-Tier M3 Expressive Shape Scale:**
   - `extraSmall` (`4.dp`), `small` (`8.dp`), `medium` (`12.dp`), `large` (`16.dp`), `largeIncreased` (`20.dp`), `extraLarge` (`28.dp`), `extraLargeIncreased` (`32.dp`), `extraExtraLarge` (`40.dp`), and `Full` (`CircleShape` / `50%`).
2. **Mandatory State Shape Morphing:**
   - Interactive M3E controls MUST morph their corner radius to reflect state transitions:
     - **Play / Pause Primary CTA:** Morph between **Pill/Circle (`50.dp–60.dp`)** when Paused and **Expressive Squircle (`18.dp–26.dp`)** when Playing.
     - **Toggle Segments (`ToggleSegmentButton`):** Morph between **Rounded Rect (`14.dp`)** when Inactive and **Full Pill (`50.dp`)** when Active.
     - **Active List / Queue Item (`QueuePlaylistSongItem`):** Morph between **Card (`22.dp`)** when Inactive and **Pill (`60.dp`)** when Active (`isCurrentSong`).
3. **Squircle (`AbsoluteSmoothCornerShape`) Integrity:**
   - Always pair each corner radius with its matching smoothness parameter (`cornerRadiusTL` with `smoothnessAsPercentTL = 60`, `cornerRadiusTR` with `smoothnessAsPercentTR = 60`, `cornerRadiusBL` with `smoothnessAsPercentBL = 60`, `cornerRadiusBR` with `smoothnessAsPercentBR = 60`).
   - NEVER toggle between `AbsoluteSmoothCornerShape` and `RoundedCornerShape` mid-drag or mid-animation (causes visible corner popping). Only fall back to `Outline.Rounded` when all radii are `<= 1.dp`.

### Step 4: Synchronized M3E Motion Choreography (Official `ExpressiveMotionTokens` & `MotionTokens`)
1. **Physics-First Architecture (`MotionScheme.expressive()` over Fixed Durations):**
   - Material 3 Expressive (`androidx.compose.material3:1.5.0-alpha22`) uses **physics-based Springs** (`dampingRatio`, `stiffness`) instead of arbitrary `tween` durations. Always retrieve specs from `MaterialTheme.motionScheme` / `MotionScheme.expressive()`:
     - **`fastSpatialSpec()` (`damping = 0.6f, stiffness = 800f`, settles ~300–350ms):** Small interactive controls, button press weight transfer (`AnimatedPlaybackControls`), corner radius morphing (`ToggleSegmentButton`), switches, and icon morphs.
     - **`defaultSpatialSpec()` (`damping = 0.8f, stiffness = 380f`, settles ~500ms):** Medium containers, sub-sheets (`QueueBottomSheet`), and Album Art Carousel skips (`AlbumCarouselSection`).
     - **`slowSpatialSpec()` (`damping = 0.8f, stiffness = 200f`, settles ~650–700ms):** Full-screen scene transitions (`MiniPlayer ↔ FullPlayer` in `UnifiedPlayerSheetV2`, `LyricsSheet` full-screen enter/exit).
     - **`fastEffectsSpec()` (`damping = 1.0f, stiffness = 3800f`, settles ~120–150ms):** Quick icon/tint crossfades and micro-alpha changes.
     - **`defaultEffectsSpec()` (`damping = 1.0f, stiffness = 1600f`, settles ~200–250ms):** Standard surface color transitions and scrim/overlay fades.
     - **`slowEffectsSpec()` (`damping = 1.0f, stiffness = 800f`, settles ~300–350ms):** Full-screen ambient palette transitions (`rememberBatchAnimatedColorScheme` in `SheetThemeState`).
2. **Official M3 Duration & Easing Tokens (Only When `tween` Is Required by an API):**
   - When an API requires a duration-based `tween` (e.g., Predictive Back settle or phased `AnimatedVisibility`):
     - **Enter / Expand (`DurationLong2 = 500ms` or `DurationMedium4 = 400ms`):** Pair with `EasingEmphasizedDecelerateCubicBezier = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)`.
     - **Exit / Collapse (`DurationShort4 = 200ms` or `DurationMedium1 = 250ms`):** Pair with `EasingEmphasizedAccelerateCubicBezier = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)`.
     - **Standard Morph (`DurationMedium2 = 300ms`):** Pair with `EasingEmphasizedCubicBezier = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)`.
     - **NEVER** use `DurationExtraLong4` (`1000ms`) for interactive navigation or button state changes.
3. **Strict Separation of Spatial vs. Effects Motion:**
   - **Spatial Motion** (`*SpatialSpec`, `dampingRatio = 0.6f..0.8f`): Use ONLY for position, translation, sheet expansion, bounds, weight, scale, and corner radius.
   - **Effects Motion** (`*EffectsSpec`, `dampingRatio = 1.0f` critically damped): Use ONLY for color, alpha/opacity, and tint. **NEVER** use bouncy springs (`dampingRatio < 1f`) for color or alpha animations.
4. **Synchronized Multi-Layer Transitions (Zero Phase Drift):**
   - **MiniPlayer ↔ FullPlayer Expand/Collapse:**
     - Phase 1 (`0.00 -> 0.22`): MiniPlayer fades out (`alpha = 1f -> 0f`) and translates slightly upward (`-24px`).
     - Phase 2 (`0.20 -> 1.00`): FullPlayer fades in along a cubic decelerate curve and scales from `0.96f -> 1.0f` (never overlap MiniPlayer and FullPlayer at 50% opacity).
   - **Track Skip / Carousel Scroll + Ambient Color Bleed:**
     - `carouselAnimationSpec` (`defaultSpatialSpec()`) and `rememberBatchAnimatedColorScheme` (`slowEffectsSpec()`) MUST trigger simultaneously without artificial `delay(...)` on `onPrevious()` / `onNext()`.
   - **Sub-Sheets (`LyricsSheet` & `QueueBottomSheet`):**
     - Enter/Exit animations MUST use a single owner (`defaultSpatialSpec()` / `slowSpatialSpec()` with `scale = 0.94f -> 1.0f` depth parallax). NEVER stack two independent enter animations on the same sheet.
5. **Non-Uniform Scale Guardrail:**
   - NEVER apply non-uniform `scaleY != scaleX` (`visualOvershootScaleY`) directly to a full-screen container holding square album art or circular buttons. Dampen any vertical squash/stretch by `(1f - expansionFraction)` so it only affects the collapsed MiniPlayer bar.

### Step 5: Compose Phase Isolation, Gestures & Modifier Order
1. **Draw-Phase & Layout-Phase Isolation (60/120fps Rule):**
   - High-frequency animated values (`expansionFraction`, `sheetTranslationY`, `playbackPosition`, `backProgress`, `swipeOffset`) MUST be passed via lambda providers (`() -> Float`) and read inside `Modifier.graphicsLayer { ... }`, `Modifier.offset { ... }`, or `Canvas`.
   - NEVER pass rapidly animating `TextStyle(color = animatedColor)` as a `remember(style)` key; key only on geometric font properties (`fontSize`, `fontWeight`, `fontFamily`, `letterSpacing`, `screenWidthDp`, `orientation`).
2. **Modifier Chain Ordering:**
   - **Outer Margin vs. Inner Padding:** Always place outer margin `.padding(...)` **BEFORE** `.size(...)` / `.width(...)` / `.height(...)`. Placing `.padding` after `.size(36.dp)` shrinks and distorts the button surface!
   - **Clipping & Touch Target:** Always order `.padding(outer)` → `.size(...)` → `.clip(shape)` → `.background(color)` → `.clickable(...)` → `.padding(inner)`.
3. **Gesture Completeness & Edge Alignment:**
   - Every `detectHorizontalDragGestures` or `detectVerticalDragGestures` call MUST implement `onDragCancel` (routing to `onDragEnd()` or resetting the `Animatable` to `0f`) so interrupted gestures never leave UI stuck mid-swipe.
   - Custom sliders (`WavySliderExpressive`, `PlayerSeekBar`) MUST use identical horizontal edge insets (`max(trackEdgePadding, thumbRadius)`) for the track indicator, thumb `Canvas`, and `pointerInput` hit area so 0% and 100% thumb positions align with the track ends.
   - Predictive Back handlers MUST drive `scale` (`1f -> 0.92f`), `translationY`, and `alpha` (`1f -> 0.72f`) inside `graphicsLayer`, and toolbar navigation buttons (`Back`, `More`) MUST remain visible even in empty/loading states (`showSyncedLyrics == null`).

---

## 2. PREFERRED M3 EXPRESSIVE COMPONENTS CATALOG

When building or refactoring UI in PixelPlayer, prefer official **Material 3 Expressive** components over legacy Material 2 / basic layouts:

| UI Pattern | Mandatory M3 Expressive Component / Pattern | Forbidden Pattern |
| :--- | :--- | :--- |
| **Indeterminate Loading** | `LoadingIndicator` / `CircularWavyProgressIndicator` / `LinearWavyProgressIndicator` | Legacy flat `CircularProgressIndicator` (unless inside tiny <20.dp slot) |
| **Audio Seek / Progress** | `WavySliderExpressive` / `PlayerSeekBar` with synchronized thumb & wave gap | Unpadded `Slider` with misaligned thumb center at 0%/100% |
| **Transport & Action Bars** | Connected shape-morphing button groups (`AnimatedPlaybackControls`, `BottomToggleRow`, `LyricsFloatingToolbar`) | Disconnected plain `IconButton` rows without container shape or press weight transfer |
| **Album / Media Browsing** | `RoundedHorizontalMultiBrowseCarousel` (`NO_PEEK`, `ONE_PEEK`, `TWO_PEEK`) with distance-based `zIndex = 1f / (1f + abs(index - currentPage))` | `zIndex = 1f / index` or abrupt `alpha = 0f` pop-in on neighbor pages |
| **Modal / Sub-Sheet Transitions** | `MotionScheme.expressive().defaultSpatialSpec()` / `slowSpatialSpec()` + background depth scale (`0.95f`) + Predictive Back | Abrupt `snapTo` without transition or stacked double enter animations |

---

## 3. PRE-OUTPUT SELF-VERIFICATION CHECKLIST

Before returning code to the user, verify every item below:
- [ ] **No Hardcoded Colors/Strings:** All colors come from `LocalMaterialTheme.current` / `MaterialTheme.colorScheme`; all user-facing strings use `stringResource(R.string...)`.
- [ ] **Modifier Order:** No `.padding(...)` appears after `.size(...)` on `IconButton` / `FilledIconButton` / `Surface` unless inner padding is explicitly intended.
- [ ] **Official M3E Motion Physics:** Spatial animations use `MotionScheme.expressive().{fast|default|slow}SpatialSpec()`; color/alpha animations use `{fast|default|slow}EffectsSpec()` (`dampingRatio = 1.0f`).
- [ ] **No Recomposition Leaks:** Per-frame animations are read inside `graphicsLayer` / `offset` / `drawWithContent`, not in composition scope.
- [ ] **Landscape & Insets Safe:** Media containers use `minOf(maxWidth, maxHeight)`; bottom menus/toolbars include `navigationBarHeight` and toolbar height offsets.
