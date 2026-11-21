# KẾ HOẠCH CHUẨN HÓA UI/UX TOÀN APP THEO CHUẨN M3 EXPRESSIVE (AGENTS.md / CLAUDE.md)

> **Trạng thái tài liệu:** Bản 1 (audit đầy đủ + kế hoạch theo đợt + prompt cho Đợt 0 và Đợt 2A).
> Prompt chi tiết cho các đợt còn lại (1, 2B, 3–8) **chưa viết** — xem mục 7.
> Audit thực hiện trên working tree hiện tại (bao gồm 29 file đang sửa dở, chưa commit).

## 0. Cách dùng tài liệu này

1. **Trước tiên tự commit checkpoint** toàn bộ thay đổi đang dở (29 file) để mỗi đợt sau có thể revert độc lập.
2. Mỗi phiên Gemini 3.8 Flash: dán **PROMPT NỀN (mục 5)** trước, sau đó dán **đúng 1 prompt của 1 đợt**. Không gộp nhiều đợt vào một phiên.
3. Sau mỗi đợt: chạy build + unit test + các lệnh `git grep` trong phần "Nghiệm thu", kiểm tra tay trên máy, rồi commit.

---

## 1. Bảng điểm tuân thủ (scorecard)

| Nhóm quy tắc | Trạng thái | Số liệu |
|---|---|---|
| Squircle `AbsoluteSmoothCornerShape` ghép cặp smoothness | ✅ Đạt | 172/172 lời gọi đúng |
| `lerpColorScheme` đủ 48 field | ✅ Đạt | `scoped/SheetThemeState.kt:216` |
| Carousel zIndex theo khoảng cách | ✅ Đạt | `RoundedParallaxCarousell.kt:521` |
| Slider edge inset (`WavySliderExpressive`) | ✅ Đạt | `max(trackEdgePadding, thumbRadius)` dùng chung |
| `baseBound = minOf(maxWidth, maxHeight)` cho album art | ✅ Đạt | `FullPlayerContent.kt:1077` |
| Gesture có `onDragCancel` | ✅ Gần đạt | chỉ thiếu ở `BrickBreakerOverlay.kt:621` (easter egg) |
| Motion dùng `MotionScheme` | ❌ Thấp | chỉ 15/258 file; còn **202** `tween(` + **68** `spring(` tự chế |
| Màu hardcode | ❌ | 52 dòng `Color(0x…)` / 8 file; 39 dòng `Color.White/Black` / 18 file |
| Chuỗi hardcode (stringResource) | ❌ | ~50 `contentDescription` + ~86 `Text("…")` (lẫn TBN/Anh/Việt) |
| Loading indicator M3E | ⚠️ | 43 `CircularProgressIndicator` cũ (~23 cái ≥ 20.dp phải đổi) |
| Phase isolation (đọc animation trong composition) | ❌ | Player sheet back-gesture, swipe mini player, 16 màn hình top-bar co giãn |
| Thứ tự Modifier (`.size` trước `.padding`) | ⚠️ | 5 lỗi thật (xem bảng) |

---

## 2. Bảng phát hiện (ưu tiên P0 → P3)

| ID | Ưu tiên | Vị trí | Vấn đề | Đợt |
|---|---|---|---|---|
| R-01 | **P0** | `UnifiedPlayerSheetV2.kt:736-742` | Thay đổi chưa commit đã **làm chết setting "Chạm nền để đóng player"** (`tapBackgroundClosesPlayer` đọc ở dòng 217 nhưng không còn dùng). Hồi quy chức năng. | 0 |
| C-01 | P1 | `FullPlayerContent.kt:315-320, 564` | Nút Previous/Next dùng `primary/onPrimary` (không Fixed); biến `transportSkipColors` (secondaryFixedDim) tính ra nhưng **không dùng**. Vi phạm 2.3. | 2A |
| C-02 | P1 | `player/BottomToggleRow.kt:27-35` + `FullPlayerContent.kt:2579-2678` | **2 bản `BottomToggleRow`**: bản public (dùng ở `LyricsMoreBottomSheet`) đọc `MaterialTheme.colorScheme` + role không Fixed; bản private trong FullPlayer thì đúng. Cần gộp 1 bản. | 2A |
| C-03 | P1 | `FullPlayerContent.kt:1022-1033` | `PlayerArtistPickerBottomSheet` nằm trong Player tree nhưng **không bọc `MaterialTheme(LocalMaterialTheme.current)`** → hiện màu theme app thay vì màu album. | 2A |
| C-04 | P2 | `ToggleSegmentButton.kt:45,48` | Default `inactiveColor = Color.Gray`; default `activeCornerRadius = 8.dp` (< 14.dp inactive → morph ngược chuẩn). | 2A |
| C-05 | P2 | `screens/SettingsScreen.kt:458-505` | 22 cặp hex cho danh mục Settings. | 5 |
| C-06 | P2 | `screens/AccountsScreen.kt:607-656` | Màu trạng thái hex pastel cố định → **sai ở Dark mode**. | 5 |
| C-07 | P3 | `StreamingProviderSheet.kt:109-182`, `PlaylistContainer.kt:499`, Jellyfin/Navidrome login+dashboard, `AlbumDetailScreen.kt:513,634`, `ArtistDetailScreen.kt:745,922`, `EqualizerScreen.kt:1860` | Màu thương hiệu/scrim/trắng-đen hardcode trong component. | 5 |
| M-01 | P1 | `LibrarySortBottomSheet.kt:286-306` | **Spring nảy (damping 0.82/0.86) cho màu** — vi phạm trực tiếp 4.3. | 4A |
| M-02 | P1 | `QueueBottomSheet.kt:724-727` | Predictive back của Queue: scale 1→0.90, **không có alpha** (chuẩn: scale 0.92, alpha 0.72). | 2A |
| M-03 | P1 | `LyricsSheet.kt:1060-1069` | Nút Play/Pause: `spring(StiffnessLow)` + `RoundedCornerShape` đọc trong composition (chuẩn: `fastSpatialSpec` + squircle trong `graphicsLayer`). | 2A |
| M-04 | P2 | `FullPlayerContent.kt:682-690, 835, 910, 1068, 1541-1559, 2114-2125` | tween tự chế cho alpha/scale → MotionScheme. | 2B |
| M-05 | P2 | `QueueBottomSheet.kt:1957-1971`, `RoundedParallaxCarousell.kt:325`, `LyricsSheet.kt:633,641,697-699` | tween cho màu/alpha/scale trong Player tree. | 2B |
| M-06 | P2 | `LyricsPredictiveBackHandler.kt:35,43`, `PlayerSheetPredictiveBackHandler.kt:58`, `UnifiedPlayerSheetV2.kt:813,822` | tween không easing cho settle/commit → token chính thức (200ms Accelerate / 300ms Emphasized). | 2B |
| M-07 | P2 | 9 sheet/dialog (`EditSongSheet`, `EditMultipleSongsSheet`, `FileExplorerBottomSheet`, `BackupModuleSelectionDialog`, `PlaylistCreationDialogs`, `ReorderPresetsSheet`, `CreatePlaylistScreen`×2, `SettingsCategoryScreen`×2) | Mẫu lặp `slideInVertically(it/6)+fadeIn(tween(220))`. | 4A |
| M-08 | P2 | `navigation/Transitions.kt`, `AppNavigation.kt:540-548`, `ScreenWrapper.kt:112-173` | 3 hằng thời lượng (350/380/450) + easing trùng lặp 4 nơi; `Easing { f*f*f }` tự chế. Gom về token. | 4C |
| M-09 | P3 | ~25 file màn hình (Library 16, Search 9, ArtistDetail 7, Setup 6…) | tween tự chế ngoài Player tree. | 4B |
| P-01 | P1 | `UnifiedPlayerSheetV2.kt:208,230,390,402` + `scoped/SheetVisualState.kt:38,80,111,161,231,248` | `predictiveBackCollapseProgress` & `swipeDismissProgress` đọc dạng Float trong composition + làm key `remember` → **recompose toàn bộ sheet mỗi frame** khi vuốt back / vuốt mini player. | 3 |
| P-02 | P2 | 16 màn hình (`SettingsScreen.kt:140-201` là mẫu) | `LaunchedEffect(topBarHeight.value)` chạy lại mỗi frame + `topBarHeight.value.toDp()` trong composition. | 8 |
| P-03 | P2 | `QueueBottomSheet.kt:1950-1955` | Offset swipe-dismiss đọc trong composition. | 3 |
| P-04 | P3 | `scoped/SheetThemeState.kt:153-154`, `UnifiedPlayerSheetLayers.kt:139-141` | `miniAppearProgress.value`, `bottomSheetOpenFraction` đọc trong composition. | 3 |
| L-01 | P1 | `subcomps/EnhancedSongListItem.kt:386-387` | `FilledIconButton(.size(36.dp).padding(end=4.dp))` → nút ⋮ **méo 32×36 ở mọi dòng bài hát**. | 6 |
| L-02 | P2 | `SearchScreen.kt:1052`, `NoInternetComponents.kt:85`, `DailyMixSection.kt:234,237` | `.size()` trước `.padding()` (thumbnail Daily Mix bị méo không vuông). | 6 |
| L-03 | P2 | `UnifiedPlayerSheetShared.kt:73-76` | `getNavigationBarHeight()` dùng `WindowInsets.safeDrawing` (gồm cả **IME**) thay vì `navigationBars`. | 3 |
| K-01 | P2 | 43 chỗ `CircularProgressIndicator` | ≥ 20.dp hoặc size mặc định → `LoadingIndicator` / `CircularWavyProgressIndicator`. MiniPlayer (`UnifiedPlayerSheetShared.kt:119`, 24.dp) + indicator preparing dùng màu theme app. | 6 / 2A |
| S-01 | P1 | `AnimatedPlaybackControls.kt:177,248,286`, `UnifiedPlayerSheetShared.kt:109,183,208,230`, `BottomToggleRow.kt:87,105,117`, `FullPlayerContent.kt:292,301,2644,2662,2674`, `LyricsSheet.kt:1085,1294,2307` | Chuỗi hardcode tiếng Tây Ban Nha/Anh trong Player tree. | 2A |
| S-02 | P2 | `OfflineModelHubScreen` (32), `HardwareCheckDialog` (13), `LyricsShareCardSheet` (9), `SettingsCategoryScreen` (8), `LyricsProviderPrioritySheet` (9), `OptimizedAlbumArt` (9), `SearchScreen` (7), `PlaylistContainer` (6), `GenreCategoriesGrid` (3), `SettingsComponents` (5)… | Chuỗi hardcode ngoài Player tree. | 7 |
| D-01 | P3 | `components/WavyMusicSlider.kt` (561 dòng) | Không còn nơi nào gọi → code chết. | 9 |

---

## 3. Đã đạt chuẩn — KHÔNG được sửa/revert

- Choreography MiniPlayer ↔ FullPlayer (Phase 1 `0→0.22` fade + `-24px`; Phase 2 `0.20→1.0` cubic + scale `0.96→1`) — `UnifiedPlayerSheetLayers.kt:89-93`, `scoped/FullPlayerVisualState.kt`.
- `visualOvershootScaleY` đã nhân `(1f - expansion)`; sheet dùng `slowSpatialSpec()`.
- LyricsSheet: đã bỏ enter-animation chồng; predictive back scale 0.92 / alpha 0.72; empty state không còn `fillParentMaxSize`.
- `rememberBatchAnimatedColorScheme` dùng `slowEffectsSpec()`; carousel dùng `defaultSpatialSpec()` không `delay`.
- `ToggleSegmentButton` morph 14.dp → active, `fastSpatialSpec` + `defaultEffectsSpec`; `QueuePlaylistSongItem` morph 22 → 60.dp.
- Play/Pause FullPlayer: Paused 60.dp (pill) ↔ Playing 26.dp (squircle), đọc trong `graphicsLayer`.
- `Shapes` 8 bậc (`ui/theme/Shape.kt`), `MaterialTheme(motionScheme = MotionScheme.expressive())`.

## 4. Ngoại lệ được phép (không tính là vi phạm)

1. `tween` trong `infiniteRepeatable` / vòng lặp tuyến tính: `ShimmerBox`, `SineWaveLine`, `AiPlaylistSheet`, `CastBottomSheet:1352`, vinyl `LyricsSheet:2291`, `PlayingEqIcon:48,60`, `MarqueeText`.
2. `tween` gắn với transition điều hướng (seekable predictive back) và `ScreenWrapper` — **nhưng phải dùng token chung** (Đợt 4C).
3. `Color.Black/Transparent` làm **mask gradient** (`BlendMode.DstIn`) ở `FileExplorerBottomSheet`, `LibraryActionRow`, `PlaylistDetailScreen`; nền đen `ImageCropView`.
4. Fallback tương phản WCAG đen/trắng `LyricsSheet.kt:199-211`.
5. Màu thương hiệu bên thứ ba — được phép nhưng phải gom vào `ui/theme` (Đợt 5).
6. Sheet được bọc `MaterialTheme(colorScheme = albumColorScheme)` (Queue, SongInfo, Cast, các dialog lyrics): đọc `MaterialTheme.colorScheme` **tương đương** `LocalMaterialTheme.current` → hợp lệ.
7. **Cấm** đổi sang `LocalMaterialTheme.current` trong component dùng chung ngoài Player tree (`QueuePlaylistSongItem` dùng ở `PlaylistDetailScreen`, `ToggleSegmentButton` dùng ở `LibraryScreen`): `LocalMaterialTheme` **ném exception** khi không có provider.

---

## 5. PROMPT NỀN — dán đầu mỗi phiên Gemini

```text
Bạn là Senior Android Engineer chuyên Jetpack Compose + Material 3 Expressive, làm việc trên repo PixelPlayer
(material3 1.5.0-alpha22, Kotlin 2.4, AGP 9.2). Đọc kỹ AGENTS.md trước khi sửa bất kỳ dòng nào.

LUẬT CỨNG:
1. Chỉ sửa đúng file/đoạn được giao trong prompt đợt này. Không refactor ngoài phạm vi, không format lại cả file,
   không đổi logic nghiệp vụ, không thêm/đổi dependency, không đổi tên API public trừ khi prompt yêu cầu.
2. Motion: không gian (vị trí, kích thước, scale, bo góc, weight) → MaterialTheme.motionScheme.{fast|default|slow}SpatialSpec();
   màu/alpha/tint → {fast|default|slow}EffectsSpec(). Không spring nảy cho màu/alpha.
   Chỉ dùng tween khi prompt cho phép, kèm token: Enter 400/500ms + CubicBezierEasing(0.05f,0.7f,0.1f,1f);
   Exit 200/250ms + CubicBezierEasing(0.3f,0f,0.8f,0.15f); Morph 300ms + CubicBezierEasing(0.2f,0f,0f,1f).
3. Mọi chỗ dùng MaterialTheme.motionScheme, MotionScheme.expressive(), LoadingIndicator, CircularWavyProgressIndicator,
   LinearWavyProgressIndicator PHẢI có @OptIn(ExperimentalMaterial3ExpressiveApi::class) ở hàm bao quanh
   (repo KHÔNG bật opt-in toàn cục).
4. Màu: trong Player tree đọc LocalMaterialTheme.current; ngoài Player tree đọc MaterialTheme.colorScheme.
   TUYỆT ĐỐI không dùng LocalMaterialTheme trong component dùng chung ngoài Player tree (nó throw khi thiếu provider).
   Không thêm Color(0x...) / Color.White / Color.Black vào component.
5. Chuỗi hiển thị/contentDescription dùng stringResource. Ưu tiên tái sử dụng key có sẵn trong res/values/strings.xml:
   common_play, common_pause, common_previous_track, common_next_track, common_shuffle, common_repeat,
   common_favorite, common_album_art_for_title (%1$s), common_close, common_search, common_clear, common_selected.
   Key mới chỉ thêm vào res/values/ (tiếng Anh), không sửa các values-xx khác.
6. Giá trị animate tần suất cao đọc trong graphicsLayer {}/offset {}/drawBehind {} hoặc qua lambda provider.
7. Thứ tự Modifier: padding(ngoài) → size → clip → background → clickable → padding(trong).
8. Squircle: luôn ghép cornerRadiusXX với smoothnessAsPercentXX = 60.

NGHIỆM THU BẮT BUỘC sau khi sửa (Windows dùng gradlew.bat):
  ./gradlew :app:compileDebugKotlin
  ./gradlew :app:testDebugUnitTest
Báo cáo cuối: danh sách file đã sửa + lý do từng thay đổi + kết quả 2 lệnh trên + kết quả các lệnh git grep
trong mục "Nghiệm thu" của prompt. Nếu có chỗ không làm theo được, nói rõ thay vì tự ý đổi hướng.
```

---

## 6. KẾ HOẠCH THEO ĐỢT

| Đợt | Nội dung | Phát hiện | Rủi ro | Prompt |
|---|---|---|---|---|
| 0 | Checkpoint + sửa hồi quy setting chạm nền | R-01 | Thấp | ✅ mục 6.0 |
| 1 | Token nền: `ui/theme/MotionTokens.kt` (thời lượng/easing chính thức, gom trùng lặp) + `ui/theme/ExtendedColors.kt` (success/warning/info sáng–tối, harmonize với primary bằng `MaterialColors.harmonize`) + `BrandColors` | M-08, C-05..07 (chuẩn bị) | Thấp | ⏳ chưa viết |
| 2A | Player tree: màu Fixed, gộp BottomToggleRow, chuỗi, theme ArtistPicker, Queue back, nút Play Lyrics, indicator MiniPlayer | C-01..04, M-02, M-03, S-01, K-01(mini) | Trung bình | ✅ mục 6.2A |
| 2B | Player tree: tween → MotionScheme (FullPlayer, Lyrics, Queue item, carousel, back handlers) | M-04..06 | Thấp | ⏳ |
| 3 | Hiệu năng Player: providers cho back/swipe progress; `navigationBars`; swipe item queue | P-01, P-03, P-04, L-03 | **Cao** — test tay kỹ | ⏳ |
| 4A/4B/4C | Motion ngoài Player: sheet/dialog; màn hình; điều hướng + ScreenWrapper dùng token | M-01, M-07..09 | Trung bình | ⏳ |
| 5 | Màu ngoài Player: Settings/Accounts/brand/scrim | C-05..07 | Trung bình (đổi giao diện) | ⏳ |
| 6 | Thứ tự Modifier + loading indicator M3E | L-01, L-02, K-01 | Thấp | ⏳ |
| 7 | Chuỗi/i18n toàn app | S-02 | Thấp | ⏳ |
| 8 | Gom 16 top-bar co giãn về 1 state holder dùng chung | P-02 | **Cao** | ⏳ |
| 9 | Dọn code chết | D-01 | Thấp | ⏳ |

### 6.0 PROMPT ĐỢT 0 — Sửa hồi quy "Chạm nền để đóng player"

```text
NHIỆM VỤ: Khôi phục hiệu lực setting tapBackgroundClosesPlayer mà không làm mất cải tiến hiện tại.

File: app/src/main/java/com/theveloper/pixelplay/presentation/components/UnifiedPlayerSheetV2.kt

Tìm (khoảng dòng 736-742):
                            .clickable(
                                enabled = currentSheetContentState == PlayerSheetState.COLLAPSED,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                playerViewModel.expandPlayerSheet()
                            }

Thay bằng:
                            .clickable(
                                enabled = tapBackgroundClosesPlayer ||
                                    currentSheetContentState == PlayerSheetState.COLLAPSED,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (currentSheetContentState == PlayerSheetState.COLLAPSED) {
                                    playerViewModel.expandPlayerSheet()
                                } else if (tapBackgroundClosesPlayer) {
                                    playerViewModel.collapsePlayerSheet()
                                }
                            }

Không sửa gì khác. Biến tapBackgroundClosesPlayer đã có sẵn ở dòng ~217.

NGHIỆM THU:
- git grep -n "tapBackgroundClosesPlayer" -- app/src/main/java/com/theveloper/pixelplay/presentation/components/UnifiedPlayerSheetV2.kt
  → phải có ≥ 3 dòng (khai báo + enabled + nhánh collapse).
- Test tay: tắt setting → chạm nền FullPlayer không đóng; bật setting → chạm nền đóng; MiniPlayer chạm → mở.
```

### 6.2A PROMPT ĐỢT 2A — Player tree: màu Fixed, gộp BottomToggleRow, chuỗi, theme, predictive back

```text
NHIỆM VỤ (6 việc, làm theo thứ tự, chỉ trong các file nêu tên):

VIỆC 1 — Nút Previous/Next dùng Fixed role (FullPlayerContent.kt)
- Xóa khối (dòng ~317-320):
    val transportSkipButtonColors = TransportButtonColors(
        container = playerAccentColor,
        content = playerOnAccentColor
    )
- Ở chỗ truyền (dòng ~564) đổi `transportSkipColors = transportSkipButtonColors,`
  thành `transportSkipColors = transportSkipColors,`
- GIỮ NGUYÊN playerAccentColor / playerOnAccentColor (còn dùng cho chip và nút top bar).

VIỆC 2 — Gộp BottomToggleRow về MỘT bản public (player/BottomToggleRow.kt)
- Thay chữ ký hàm public thành (thêm 3 tham số mới, đổi default sang Fixed role của LocalMaterialTheme):
    fun BottomToggleRow(
        modifier: Modifier,
        isShuffleEnabled: Boolean,
        repeatMode: Int,
        isFavoriteProvider: () -> Boolean,
        onShuffleToggle: () -> Unit,
        onRepeatToggle: () -> Unit,
        onFavoriteToggle: () -> Unit,
        isShuffleTransitionInProgress: Boolean = false,
        activeColorMain: Color = LocalMaterialTheme.current.primaryFixed,
        activeColorSecondary: Color = LocalMaterialTheme.current.secondaryFixed,
        activeColorTertiary: Color = LocalMaterialTheme.current.tertiaryFixed,
        onActiveColorMain: Color = LocalMaterialTheme.current.onPrimaryFixed,
        onActiveColorSecondary: Color = LocalMaterialTheme.current.onSecondaryFixed,
        onActiveColorTertiary: Color = LocalMaterialTheme.current.onTertiaryFixed,
        inactiveColor: Color = LocalMaterialTheme.current.surfaceContainerHighest,
        inactiveContentColor: Color = LocalMaterialTheme.current.onSurfaceVariant,
        containerColor: Color = LocalMaterialTheme.current.surfaceContainer,
        contentPadding: Dp = 8.dp,
        itemSpacing: Dp = 8.dp
    )
- Thân hàm: dùng `.padding(contentPadding)` và `Arrangement.spacedBy(itemSpacing)` thay cho 8.dp cứng;
  bỏ `.background(Color.Transparent)` thừa; tạo shape 1 lần bằng remember và ghép đúng cặp
  (cornerRadiusTL+smoothnessAsPercentTL, TR+TR, BL+BL, BR+BR, tất cả 60.dp / 60).
- Nút Shuffle thêm `enabled = !isShuffleTransitionInProgress`.
- contentDesc: stringResource(R.string.common_shuffle) / common_repeat / common_favorite.
- Bỏ import MaterialTheme nếu không còn dùng; thêm import Dp, stringResource, remember.
- FullPlayerContent.kt: XÓA toàn bộ hàm `private fun BottomToggleRow(...)` (từ `@Composable` dòng ~2579 tới hết file),
  và sửa lời gọi (dòng ~1217) thành:
            BottomToggleRow(
                modifier = Modifier
                    .padding(horizontal = 26.dp)
                    .padding(bottom = 6.dp)
                    .fillMaxWidth()
                    .heightIn(min = 60.dp, max = 80.dp),
                isShuffleEnabled = isShuffleEnabledProvider(),
                isShuffleTransitionInProgress = shuffleTransitionInProgress,
                repeatMode = repeatModeProvider(),
                isFavoriteProvider = isFavoriteProvider,
                onShuffleToggle = onShuffleToggle,
                onRepeatToggle = onRepeatToggle,
                onFavoriteToggle = onFavoriteToggle,
                inactiveColor = LocalMaterialTheme.current.onSurface.copy(alpha = 0.07f),
                inactiveContentColor = LocalMaterialTheme.current.onSurface,
                containerColor = LocalMaterialTheme.current.surfaceContainerLowest.copy(alpha = 0.7f),
                contentPadding = 6.dp,
                itemSpacing = 6.dp
            )
  (Kích thước hiển thị giữ nguyên: tổng cao 66–86.dp như cũ, chỉ sửa thứ tự Modifier.)
- subcomps/LyricsMoreBottomSheet.kt dòng ~639: đổi modifier thành
  Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(74.dp)

VIỆC 3 — Chuỗi hardcode trong Player tree
- player/AnimatedPlaybackControls.kt: "Anterior"→stringResource(R.string.common_previous_track),
  "Siguiente"→common_next_track, "Pausar"/"Reproducir"→common_pause/common_play.
  Đổi tên tham số cho đúng nghĩa (không caller nào truyền theo tên):
  playPauseCornerPlaying: Dp = 60.dp  → playPauseCornerWhenPaused: Dp = 60.dp
  playPauseCornerPaused: Dp = 26.dp   → playPauseCornerWhenPlaying: Dp = 26.dp
  và targetValue = if (playPauseVisualState) playPauseCornerWhenPlaying else playPauseCornerWhenPaused
- UnifiedPlayerSheetShared.kt (MiniPlayer): "Carátula de ${song.title}" →
  stringResource(R.string.common_album_art_for_title, song.title); "Anterior"/"Siguiente"/"Pausar"/"Reproducir" như trên.
  Nút Next thêm haptic giống nút Previous.
- FullPlayerContent.kt dòng ~292 và ~301: thay sendToast("No song selected for lyrics import.") và
  sendToast("Error reading file.") bằng chuỗi lấy từ resource (thêm key player_toast_no_song_for_lyrics_import,
  player_toast_error_reading_file vào res/values/strings_player.xml; lấy chuỗi bằng context.getString hoặc
  stringResource đọc trước ngoài lambda).
- LyricsSheet.kt: "Pause"(~1085)→common_pause; "Show Controls"(~1294)→key mới lyrics_cd_show_controls;
  "Cover Art"(~2307)→stringResource(R.string.common_album_art_for_title, song.title).

VIỆC 4 — Theme album cho Artist picker (FullPlayerContent.kt dòng ~1023)
  Bọc lời gọi PlayerArtistPickerBottomSheet(...) trong:
    MaterialTheme(colorScheme = LocalMaterialTheme.current) { PlayerArtistPickerBottomSheet(...) }

VIỆC 5 — Predictive back của Queue (QueueBottomSheet.kt dòng ~724-727)
  Thay `val scale = 1f - (p * 0.1f)` bằng `val scale = androidx.compose.ui.util.lerp(1f, 0.92f, p)`
  và thêm ngay sau scaleY: `alpha = androidx.compose.ui.util.lerp(1f, 0.72f, p)`. Giữ nguyên phần còn lại.

VIỆC 6 — Nút Play/Pause trong LyricsSheet (dòng ~1060-1069)
  - animationSpec = MaterialTheme.motionScheme.fastSpatialSpec() (thêm @OptIn nếu thiếu).
  - Thay `.clip(RoundedCornerShape(playPauseCornerRadius))` bằng:
        .graphicsLayer {
            clip = true
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusTL = playPauseCornerRadius, smoothnessAsPercentTL = 60,
                cornerRadiusTR = playPauseCornerRadius, smoothnessAsPercentTR = 60,
                cornerRadiusBL = playPauseCornerRadius, smoothnessAsPercentBL = 60,
                cornerRadiusBR = playPauseCornerRadius, smoothnessAsPercentBR = 60
            )
        }
  - MiniPlayer (UnifiedPlayerSheetShared.kt ~119-125): thay CircularProgressIndicator(24.dp) bằng
    CircularWavyProgressIndicator(modifier = Modifier.size(24.dp),
        color = LocalMaterialTheme.current.onPrimaryContainer,
        trackColor = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.2f));
    indicator isPreparingPlayback cũng truyền đúng 2 màu này.

KHÔNG ĐƯỢC: đổi LocalMaterialTheme trong QueuePlaylistSongItem/ToggleSegmentButton (dùng chung ngoài Player tree);
đụng tới choreography Mini↔Full, SheetThemeState, carousel.

NGHIỆM THU:
- git grep -n "private fun BottomToggleRow" → 0 kết quả
- git grep -n "transportSkipButtonColors" → 0 kết quả
- git grep -nE "\"(Anterior|Siguiente|Pausar|Reproducir|Aleatorio|Repetir|Favorito|Carátula)" -- "app/src/main/java" → 0 kết quả
- git grep -n "0.1f)" -- app/src/main/java/com/theveloper/pixelplay/presentation/components/QueueBottomSheet.kt → không còn dòng scale
- Build + unit test pass. Test tay: Light/Dark/Album-art theme; bật/tắt shuffle, repeat, favorite ở FullPlayer
  và Lyrics → More; vuốt back trên Queue (mờ dần 0.72); mở artist picker có màu album; TalkBack đọc đúng nhãn.
```

---

## 7. Phần CHƯA hoàn thành (cần viết tiếp)

Prompt chi tiết cho Đợt **1, 2B, 3, 4A, 4B, 4C, 5, 6, 7, 8, 9** chưa được viết. Bảng phát hiện ở mục 2 đã có đủ vị trí file/dòng để viết tiếp. Các lưu ý kỹ thuật đã xác minh cho các đợt sau:

- **Đợt 3 (P-01):** đừng đọc `StateFlow.value` trong lambda — không được snapshot theo dõi. Giữ đối tượng `State` từ `collectAsStateWithLifecycle()` (không dùng `by`), truyền provider `{ state.value }`, bỏ progress khỏi key của `remember` trong `SheetVisualState`.
- **Đợt 1:** `com.google.android.material.color.MaterialColors.harmonize(Int, Int)` có sẵn (thư viện MDC đã nằm trong classpath).
- **Đã xác minh trên AAR 1.5.0-alpha22:** `MotionScheme` có đủ 6 spec; `MaterialTheme(colorScheme, shapes, typography)` 3 tham số **kế thừa** `motionScheme` bên ngoài; `LoadingIndicator(modifier, color, polygons)`, `ContainedLoadingIndicator(...)`, `CircularWavyProgressIndicator(modifier, color, trackColor, …)` đúng chữ ký.
- **Đợt 5 (Settings):** đề xuất ánh xạ danh mục sang các cặp role `primary/secondary/tertiary` × `Container`, About dùng `surfaceContainerHighest`. Phương án thay thế: giữ nguyên bảng màu nhưng chuyển vào `ui/theme`. **Bạn cần chọn** trước khi viết prompt.
