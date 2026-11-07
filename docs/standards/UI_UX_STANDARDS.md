# Quy chuẩn UI/UX — PixelPlayer

> Ngôn ngữ thiết kế: **Material 3 Expressive + Material You**, cảm giác "Pixel" — bo góc mềm (squircle),
> chuyển động lò xo, màu động theo hình nền hoặc ảnh bìa album, haptics tinh tế.
> Quy tắc tổng quát ở [`AGENTS.md`](../../AGENTS.md). Code liên quan: `ui/theme/`, `presentation/components/`,
> `presentation/navigation/Transitions.kt`, `presentation/utils/AppHaptics.kt`.

---

## 1. Nguyên tắc trải nghiệm

1. **Âm nhạc là trung tâm:** ảnh bìa và player luôn là điểm nhấn; chrome (thanh, nút) lùi lại.
2. **Nhất quán hơn sáng tạo:** màn hình mới phải trông như "anh em" với màn hình cũ — tái sử dụng
   component có sẵn (§7) trước khi viết mới.
3. **Phản hồi tức thì:** mọi thao tác có phản hồi thị giác (ripple/scale/morph) trong ≤ 100 ms; thao tác
   quan trọng có haptic (tôn trọng cài đặt người dùng).
4. **Mượt trên máy tầm trung:** thiết kế giả định thư viện 5 000+ bài trên Snapdragon 680 / 4 GB RAM.
   Hiệu ứng đẹp nhưng làm giật khung hình là lỗi (xem §9).
5. **Tôn trọng người dùng:** hỗ trợ dark/light, màu động, cỡ chữ hệ thống, TalkBack, RTL (có `values-ar`).

---

## 2. Màu sắc

- **Chỉ dùng color role:** `MaterialTheme.colorScheme.primary/onPrimary/primaryContainer/…/surfaceContainer*`.
  Không `Color(0xFF…)`, `Color.Black/White` trong composable màn hình (trừ scrim/overlay trên ảnh, phải có comment).
- Nguồn màu do `PixelPlayTheme` quyết định: (1) override từ ảnh bìa album (`ColorSchemePair`,
  sinh bởi `ui/theme/ColorRoles.kt` theo `AlbumArtPaletteStyle`), (2) dynamic color Android 12+,
  (3) `DarkColorScheme`/`LightColorScheme`. Component **không** tự gọi `dynamicColorScheme` hay tự trích palette.
- Màu cố định hợp lệ chỉ nằm trong `ui/theme/Color.kt`, `ui/theme/GenreColors.kt` (màu thể loại),
  và palette Wear (`WearThemePaletteFactory`). Cần màu thương hiệu mới → thêm vào `Color.kt`, đặt tên ngữ nghĩa.
- Bề mặt phân tầng: nền `surface`/`background`; thẻ/nhóm cài đặt `surfaceContainer`; phần tử nổi
  `surfaceContainerHigh/Highest`. Văn bản phụ `onSurfaceVariant`. Viền `outlineVariant`.
- Cặp màu luôn đi đôi: nền `xxxContainer` ↔ chữ/icon `onXxxContainer`. Không đặt `primary` lên `primaryContainer`.
- Kiểm tra dark mode bằng `LocalPixelPlayDarkTheme.current` (không gọi `isSystemInDarkTheme()` trong component).
- Độ tương phản chữ ≥ 4.5:1 (chữ thường), ≥ 3:1 (chữ lớn, icon). Chữ đè lên ảnh bìa phải có gradient scrim.

## 3. Typography

- Chỉ dùng `MaterialTheme.typography.*`. Font mặc định: **Google Sans Flex Rounded** (`GoogleSansRounded`,
  `res/font/gflex_variable`). Không thêm font khác.
- Tiêu đề "expressive" lớn (header màn hình chính, hero) dùng `ExpTitleTypography` (Montserrat, scaleX) —
  chỉ cho tiêu đề display, không cho body.
- Bảng dùng thường gặp:

| Vai trò                          | Style                                  |
| -------------------------------- | -------------------------------------- |
| Tiêu đề màn hình (top bar lớn)   | `headlineMedium` / `headlineLarge`     |
| Tiêu đề section, tên bài trong list | `titleMedium` (đậm vừa)            |
| Dòng phụ (nghệ sĩ, album, mô tả) | `bodyMedium` / `bodySmall` + `onSurfaceVariant` |
| Nhãn nút, chip                   | `labelLarge`                           |
| Tiêu đề nhóm cài đặt             | `labelMedium` + `primary` + Bold       |

- Chỉ chỉnh `fontWeight`/`color` khi cần; không ghi đè `fontSize` bằng số cứng. Dùng `sp` cho chữ (không `dp`).
- Văn bản có thể dài (tên bài/nghệ sĩ): `maxLines = 1` + `overflow = TextOverflow.Ellipsis`, hoặc marquee
  có kiểm soát ở player.

## 4. Shape (bo góc)

- Phong cách: **smooth corner (squircle)** qua `racra AbsoluteSmoothCornerShape`, `smoothnessAsPercent = 60`.
- **Dùng instance cache:** `ShapeCache.smooth8 … smooth32`, `ShapeCache.smoothPill`. Không tạo
  `AbsoluteSmoothCornerShape(...)` mới bên trong item của `LazyColumn/LazyGrid` (tốn path mỗi lần compose).
  Bán kính chưa có trong cache mà dùng ≥ 2 nơi → thêm vào `ShapeCache`.
- Bảng bán kính:

| Thành phần                               | Bán kính                       |
| ---------------------------------------- | ------------------------------ |
| Chip nhỏ, badge, thumbnail nhỏ           | 8–10 dp                        |
| Item danh sách bài hát, item cài đặt     | 10–12 dp (nhóm item liền nhau: góc ngoài lớn, góc trong nhỏ) |
| Thẻ album/playlist                       | 16 dp                          |
| Thẻ lớn, panel                           | 20–24 dp                       |
| Dialog                                   | 24–28 dp                       |
| Bottom sheet, panel nổi                  | 28–32 dp (góc trên)            |
| Nút, FAB pill, segmented                 | `smoothPill` / `CircleShape`   |
| Hình trang trí expressive (ảnh nghệ sĩ, icon nền) | Shape tuỳ biến trong `utils/shapes/` (`RoundedStarShape`, `PolygonShape`, `createRoundedHexagonShape(…)`) hoặc `androidx.graphics.shapes` — tạo trong `remember` |

- `RoundedCornerShape` vẫn chấp nhận cho phần tử nhỏ không nổi bật (track progress, indicator) và trong code cũ.
- Người dùng có thể chỉnh bo góc thanh điều hướng (`NavBarCornerRadiusScreen`) — đừng hardcode bo góc cho nav bar.

## 5. Spacing & layout

- Lưới **4 dp**. Giá trị chuẩn: `4, 8, 12, 16, 24, 32` (thỉnh thoảng 6/10/14/20 cho tinh chỉnh nội bộ component).
- Lề ngang màn hình: **16 dp**. Khoảng cách giữa section: 24 dp. Khoảng cách item trong list: 2–8 dp.
- Kích thước icon: 24 dp (mặc định), 20 dp (trong list/item dày), 18 dp (inline với chữ). Vùng chạm tối thiểu **48 dp**.
- Ảnh bìa trong list: 48–56 dp, dùng `SmartImageListTargetSize`/`SmartImageCompactListTargetSize`.
- **Edge-to-edge:** status/navigation bar trong suốt (`PixelPlayStatusBarStyle`). Nội dung cuộn phải chừa:
  - `WindowInsets.statusBars` ở trên (hoặc top bar tự xử lý),
  - phía dưới: `MiniPlayerHeight` (64 dp) + `MiniPlayerBottomSpacer` + chiều cao nav bar + `WindowInsets.navigationBars`
    — dùng các hằng/hàm hiện có (`calculateBottomPadding`, `NavBarContentHeight`), không tự cộng số cứng.
- Màn hình phải hoạt động ở cỡ chữ 200 % và chiều rộng 320 dp (không cắt nội dung quan trọng).

## 6. Motion

- **Điều hướng màn hình:** chỉ dùng helper trong `presentation/navigation/Transitions.kt`
  (`aospSharedAxisEnter/Exit/PopEnter/PopExit`, `M3EmphasizedEasing`, `AOSP_TRANSITION_DURATION = 350`).
  Không tự viết transition riêng cho từng route.
- **Component:** ưu tiên spring / `MotionScheme.expressive()` (`fastSpatialSpec`, `defaultSpatialSpec`,
  `defaultEffectsSpec`). Dùng `tween` chỉ khi cần thời lượng xác định (fade, đồng bộ với nhạc).
  - Spatial (vị trí, kích thước, scale): spring có độ nảy nhẹ.
  - Effects (alpha, màu): không nảy.
- Thời lượng tham khảo: micro-interaction 100–200 ms, mở rộng/thu gọn 250–350 ms, sheet/player 350–500 ms.
- `AnimatedContent`/`Crossfade` cho chuyển trạng thái nội dung (loading → content → empty).
- **Giới hạn trong Lazy list:** tối đa 1–2 `animate*AsState` mỗi item; không `AnimatedVisibility` cho từng item
  của section dài — dùng `Modifier.animateItem()` của LazyList.
- Đọc giá trị animation trong `graphicsLayer { }` / lambda modifier để chỉ invalidate draw, không recompose.
- Tôn trọng "giảm chuyển động" của hệ thống khi thêm animation trang trí lặp vô hạn.

## 7. Component — dùng cái có sẵn trước

| Nhu cầu                       | Dùng                                                                                   |
| ----------------------------- | -------------------------------------------------------------------------------------- |
| Ảnh bìa/ảnh nghệ sĩ           | `SmartImage(model, contentDescription, targetSize = …, shape = ShapeCache.smoothXX)` |
| Top bar co giãn màn hình con  | `CollapsibleCommonTopBar`                                                              |
| Top bar có gradient thể loại  | `GenreGradientTopBar` / `HomeGradientTopBar`                                           |
| Thanh cuộn nhanh có nhãn      | `ExpressiveScrollBar` (tôn trọng `LocalShowScrollbar`)                                 |
| Loading                       | `LoadingIndicator` / `ContainedLoadingIndicator` (M3 Expressive); placeholder list: `ShimmerBox` |
| Tiến trình                    | `LinearWavyProgressIndicator` / `CircularWavyProgressIndicator`                        |
| Trạng thái rỗng               | Mẫu `LibraryExpressiveEmptyState` (icon trong khối bo tròn + tiêu đề + mô tả + hành động) |
| Offline / lỗi mạng            | `ExpressiveOfflineState`, `ExpressiveOfflineDialog`                                    |
| Hoàn tác sau khi xoá          | `DismissUndoBar`                                                                       |
| Lựa chọn 2–4 tuỳ chọn         | `ToggleSegmentButton`                                                                  |
| Mục cài đặt                   | `SettingsSection`, `SettingsItem`, `SwitchSettingItem` (`SettingsComponents.kt`)       |
| Tuỳ chọn/hành động phụ        | `ModalBottomSheet` (ưu tiên hơn dialog cho danh sách hành động)                        |
| Xác nhận phá huỷ              | `AlertDialog` — nút xác nhận mô tả hành động ("Delete"), không "OK"                    |
| Nút chính / phụ               | `Button` / `FilledTonalButton` (phổ biến nhất) / `TextButton`; icon-only: `IconButton`/`FilledTonalIconButton` |

- Component mới tái sử dụng được → đặt trong `presentation/components/`, có tham số
  `modifier: Modifier = Modifier` (tham số tuỳ chọn đầu tiên), nhận dữ liệu + lambda, **không** nhận ViewModel.
- Chỉ màn hình cấp route (`XxxScreen`) được lấy ViewModel qua `hiltViewModel()`.

## 8. Icon & hình ảnh

- Icon Material **Rounded**: `Icons.Rounded.*`, `Icons.AutoMirrored.Rounded.*` (cho icon có hướng — hỗ trợ RTL),
  hoặc drawable `R.drawable.rounded_*`. Không trộn Filled/Outlined trừ khi biểu thị trạng thái (vd. tim rỗng/đầy).
- Icon mới dạng vector XML đặt tên `rounded_<tên>.xml`.
- Ảnh: luôn qua `SmartImage` với `targetSize` phù hợp (không tải ảnh full-res cho thumbnail); placeholder
  `ic_music_placeholder`.

## 9. Hiệu năng UI (bắt buộc)

- `LazyColumn/LazyGrid`: `items(list, key = { it.id }, contentType = { … })`.
- **Slice state:** không collect cả `PlayerUiState` để đọc 1–2 field. Dùng
  `viewModel.playerUiState.map { it.foo }.distinctUntilChanged().collectAsStateWithLifecycle(initial)`
  (mẫu: `UnifiedPlayerSheetV2.kt`), hoặc expose `StateFlow` riêng.
- Vị trí phát nhạc (cập nhật liên tục) chỉ được đọc ở composable nhỏ nhất cần nó (slider, thời gian).
- Không sắp xếp/lọc/`toImmutableList()` danh sách lớn trong composition — làm ở ViewModel/StateHolder
  (`Dispatchers.Default`), hoặc `remember(key) { … }` nếu nhỏ.
- Lambda truyền xuống item nên ổn định (`remember` hoặc method reference).
- Hiệu ứng blur/shader nặng phải tôn trọng cờ tắt blur (`disableBlurAllOver`) và có fallback.
- Màn hình mới quan trọng nên được thêm vào baseline profile (`:baselineprofile`).

## 10. Haptics

- Dùng `LocalAppHapticsConfig.current` + `View.performAppHapticFeedback(...)` /
  `performAppCompatHapticFeedback(...)` (tự bỏ qua khi người dùng tắt haptics).
- Gợi ý: `CONTEXT_CLICK`/`CLOCK_TICK` cho toggle, kéo slider qua nấc; `LONG_PRESS` cho chọn nhiều;
  `CONFIRM`/`REJECT` (API 30+) cho thành công/thất bại. Không rung cho mỗi lần cuộn.

## 11. Nội dung & ngôn ngữ (UX copy)

- Mọi chuỗi trong `res/values/strings_<feature>.xml` (tiếng Anh là ngôn ngữ gốc):
  `strings_player.xml`, `strings_library.xml`, `strings_settings.xml`, `strings_screens.xml`,
  `strings_home_screen.xml`, `strings_cloud_services.xml`, `strings_equalizer.xml`, `strings_widget.xml`;
  chung → `strings.xml`. Tên key: `<feature>_<ngữ cảnh>_<vai trò>` (vd. `lyrics_sync_offset_title`).
- Bản dịch (`values-xx`) có thể bổ sung sau; thiếu bản dịch sẽ fallback tiếng Anh — **không** đặt chuỗi
  ngôn ngữ khác vào `values/`.
- Số nhiều dùng `<plurals>` + `pluralStringResource`; định dạng dùng placeholder `%1$s`, `%1$d`.
- Văn phong: ngắn, chủ động, Sentence case ("Add to playlist", không "ADD TO PLAYLIST").
  Lỗi = điều gì xảy ra + cách khắc phục ("Couldn't load lyrics. Check your connection and try again.").
- Trạng thái rỗng luôn có lời giải thích và (nếu có) hành động tiếp theo.

## 12. Accessibility

- `contentDescription`: bắt buộc cho icon/ảnh mang nghĩa và mọi `IconButton`; `null` chỉ cho phần tử trang trí
  hoặc khi chữ bên cạnh đã mô tả đủ.
- Vùng chạm ≥ 48 × 48 dp (`Modifier.minimumInteractiveComponentSize()` nếu icon nhỏ).
- Phần tử tuỳ biến có vai trò: `Modifier.semantics { role = Role.Button / Role.Switch; stateDescription = … }`;
  gộp item list bằng `Modifier.semantics(mergeDescendants = true)`.
- Slider/seekbar tuỳ biến (Wavy) cần `progressSemantics`/`setProgress` để TalkBack điều khiển được.
- Không truyền đạt thông tin chỉ bằng màu (bài đang phát: màu + icon equalizer/indicator).

## 13. Wear OS (`:wear`)

- Không dùng component Compose điện thoại (`androidx.compose.material3`) trong `:wear`.
- Hiện trạng: phần lớn màn hình dùng `androidx.wear.compose.material` + Horologist, một số đã dùng
  `androidx.wear.compose.material3`. **Theo thư viện mà file/màn hình đó đang dùng; không trộn hai thư viện
  trong cùng một màn hình.** Chuyển Wear sang Material 3 là một task riêng, không làm lẻ tẻ.
- Danh sách: `ScalingLazyColumn` (theo Horologist layout như hiện có), vùng chạm ≥ 48 dp.
- Màu lấy từ `WearThemePalette` gửi từ điện thoại (`:shared`), có fallback khi chưa đồng bộ.

## 14. Checklist UI trước khi hoàn tất

- [ ] Đã kiểm tra light + dark + màu từ ảnh bìa (không có màu cứng).
- [ ] Không chuỗi cứng; có `contentDescription` hợp lý.
- [ ] Dùng component có sẵn ở §7; shape từ `ShapeCache`; icon Rounded.
- [ ] Insets đúng, nội dung không bị mini player/nav bar che.
- [ ] Lazy list có `key`; state được slice; không tính toán nặng trong composition.
- [ ] Motion dùng `Transitions.kt` / `MotionScheme.expressive()`; haptics qua `LocalAppHapticsConfig`.
- [ ] Hoạt động ở cỡ chữ lớn và RTL.
