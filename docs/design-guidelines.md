# Quy chuẩn thiết kế PixelPlayer

Tài liệu này là nguồn tham chiếu chung cho giao diện của PixelPlayer: màu sắc, chữ, hình khối,
khoảng cách và đặc biệt là **chuyển động / chuyển cảnh**. Mọi thay đổi UI mới nên tuân theo
tài liệu này; nếu cần làm khác, hãy cập nhật tài liệu trong cùng PR và ghi rõ lý do.

> Thuật ngữ kỹ thuật (tên API Compose, token Material) được giữ nguyên tiếng Anh.

---

## 1. Nguyên tắc chung

1. **Material 3 Expressive + Material You.** App dựa trên Material Design 3, ưu tiên màu động
   từ hình nền hệ thống (Android 12+) hoặc màu lấy từ ảnh bìa album.
2. **Chuyển động có hướng.** Khi người dùng đi tới / quay lại một nơi khác, chuyển động phải
   cho biết *hướng* đi (slide), không chỉ hiện / mờ đi tại chỗ (fade).
3. **Một nguồn token.** Màu, chữ, bo góc, thời lượng animation lấy từ token dùng chung
   (`ui/theme/*`, `presentation/navigation/Transitions.kt`), không hard-code giá trị mới
   rải rác trong màn hình.
4. **Mượt trên máy yếu.** Animation chạy ở draw-phase (`graphicsLayer`) khi có thể; không
   tính toán O(n) trên danh sách lớn mỗi khung hình (xem `app/performance_analysis.md`).

---

## 2. Màu sắc

Nguồn: `ui/theme/Theme.kt`, `ui/theme/Color.kt`, `ui/theme/ColorRoles.kt`.

| Thứ tự ưu tiên | Nguồn màu |
|---|---|
| 1 | Màu lấy từ ảnh bìa (`colorSchemePairOverride`) cho player, lyrics, chi tiết album |
| 2 | Màu động hệ thống `dynamicLight/DarkColorScheme` (Android 12+) |
| 3 | `LightColorScheme` / `DarkColorScheme` mặc định của app |

Quy tắc:

- Chỉ dùng **vai trò màu** của `MaterialTheme.colorScheme` (`primary`, `onPrimary`,
  `primaryContainer`, `surfaceContainer*`…), không dùng mã màu cố định trong màn hình.
- Chữ trên nền màu phải đạt **tỉ lệ tương phản ≥ 4.5:1** (chữ lớn ≥ 3:1). Khi màu nhấn có thể
  không đủ tương phản (ví dụ màu lấy từ ảnh bìa), dùng hàm chọn màu theo tương phản như
  `preferredContrastColor` trong `LyricsSheet.kt`.
- Trạng thái mờ dùng alpha của `onSurface` / `contentColor`: 0.7 (phụ), 0.45 (không hoạt động),
  0.38 (disabled).

---

## 3. Chữ

Nguồn: `ui/theme/Type.kt`.

| Font | Dùng cho |
|---|---|
| `GoogleSansRounded` | Font chính của UI (tiêu đề, nút, nhãn) |
| `MontserratFamily` (`ExpTitleTypography`) | Tiêu đề lớn kiểu "expressive" (display/headline) |
| Font hệ thống (`fontFamily = null`) | **Lời bài hát** và nội dung do người dùng nhập, để hiển thị đủ ký tự Unicode mở rộng |

Quy tắc:

- Dùng style có sẵn của `MaterialTheme.typography` (`titleMedium`, `bodySmall`…), chỉ
  `copy()` để đổi `fontWeight` / `color`, không tự đặt `fontSize` mới nếu không cần.
- Tiêu đề bài hát / nghệ sĩ: `maxLines = 1` + `TextOverflow.Ellipsis` hoặc `AutoScrollingText`.

---

## 4. Hình khối & bo góc

Nguồn: `ui/theme/Shape.kt` (`small = 8dp`, `medium = 16dp`, `large = 24dp`).

Thang bo góc chuẩn: **4 · 8 · 12 · 16 · 24 · 32 dp** và `CircleShape` (bo tròn hoàn toàn).

| Thành phần | Bo góc |
|---|---|
| Chip, badge nhỏ | 8dp |
| Item danh sách, thẻ nhỏ, ô nhập liệu | 12–16dp |
| Thẻ lớn, nút lớn, nhóm cài đặt | 16–24dp |
| Bottom sheet, dialog, thẻ nổi bật | 28–32dp |
| Nút tròn, avatar, ảnh bìa dạng đĩa | `CircleShape` |

- Thẻ / khối lớn có thể dùng `AbsoluteSmoothCornerShape` (bo góc "squircle") để đồng bộ với
  phong cách Pixel; giữ cùng bán kính với bảng trên.
- Giá trị lẻ (10dp, 18dp, 20dp) đang tồn tại trong code — khi sửa màn hình đó, hãy đưa về giá
  trị gần nhất trong thang chuẩn.

---

## 5. Khoảng cách & bố cục

- Lưới **4dp**; khoảng cách thường dùng: 4 · 8 · 12 · 16 · 24 · 32dp.
- Lề ngang màn hình: **16dp** (nội dung đọc dài như lời bài hát: 24dp).
- Khoảng cách giữa các item trong danh sách: 8dp; giữa các nhóm: 16–24dp.
- Vùng chạm tối thiểu **48 × 48dp**.
- App chạy **edge-to-edge**: mọi phần tử neo vào cạnh dưới / trên phải cộng thêm inset của
  thanh hệ thống (`WindowInsets.navigationBars`, `paddingValues` của `Scaffold`), không dùng
  khoảng cách cố định.

---

## 6. Chuyển động & chuyển cảnh

### 6.1 Token

| Token | Giá trị | Dùng cho |
|---|---|---|
| `SCREEN_TRANSITION_DURATION` | **350ms** | Mọi chuyển cảnh màn hình, tab chính, sheet, dialog |
| `EmphasizedEasing` | `CubicBezier(0.2, 0, 0, 1)` | Chuyển cảnh chuẩn (Material 3 Emphasized) |
| `EmphasizedDecelerateEasing` | `CubicBezier(0.05, 0.7, 0.1, 1)` | Phần tử **đi vào** màn hình |
| `EmphasizedAccelerateEasing` | `CubicBezier(0.3, 0, 0.8, 0.15)` | Phần tử **rời khỏi** màn hình |
| Micro-animation | 150–250ms, `tween` hoặc `spring` | Đổi icon, chip, trạng thái nút |

Tất cả token đặt trong `presentation/navigation/Transitions.kt`; màn hình không tự khai báo
thời lượng / easing cho chuyển cảnh.

### 6.2 Quy tắc chính: chuyển cảnh dùng **slide thuần**

Áp dụng cho: điều hướng giữa màn hình, chuyển tab chính, bottom sheet, dialog, màn hình phủ
(player, lyrics, queue).

- **Không dùng `fadeIn` / `fadeOut`** và **không dùng `scaleIn` / `scaleOut`** trong các chuyển
  cảnh này.
- Màn hình mới luôn trượt **toàn bộ** vào (offset 100%), màn hình cũ trượt lệch nhẹ
  (**parallax 25%**) để tạo cảm giác chiều sâu mà không cần mờ.

| Tình huống | Màn hình đi vào | Màn hình rời đi |
|---|---|---|
| Mở màn hình mới (push) | Từ phải: `slideInHorizontally { it }` | Sang trái 25%: `slideOutHorizontally { -it / 4 }` |
| Quay lại (pop / back) | Từ trái 25%: `slideInHorizontally { -it / 4 }` | Sang phải hết: `slideOutHorizontally { it }` |
| Chuyển tab Home ↔ Search ↔ Library | Trượt theo thứ tự tab (tab bên phải vào từ phải), offset 100% | Trượt ngược hướng, offset 100% |
| Bottom sheet / màn hình phủ (lyrics, queue) | Từ dưới lên: `slideInVertically { it }` | Xuống dưới: `slideOutVertically { it }` |
| Dialog | Từ dưới lên một đoạn ngắn: `slideInVertically { it / 4 }` | `slideOutVertically { it / 4 }` |

Ghi chú:

- Với hướng viết từ phải sang trái (tiếng Ả Rập), đảo chiều ngang (`LayoutDirection.Rtl`).
- Màn hình rời đi bằng parallax phải được vẽ **dưới** màn hình đi vào (z-order mặc định của
  `NavHost` đã đảm bảo); nền của màn hình phải đặc (không trong suốt) để không lộ màn hình cũ.
- **Predictive back** (Android 13+): trong lúc kéo, màn hình hiện tại trượt / thu theo cử chỉ;
  khi thả, hoàn tất bằng đúng chuyển cảnh pop ở bảng trên.
- `AlertDialog` / `ModalBottomSheet` của Material dùng animation của window hoặc của component.
  `ModalBottomSheet` vốn đã trượt từ dưới lên — giữ nguyên. Dialog tự dựng bằng
  `AnimatedVisibility` phải theo bảng trên.

### 6.3 Khi nào vẫn được dùng fade

Fade **chỉ** dùng cho hiệu ứng nhỏ, tại chỗ, không mang ý nghĩa "đi tới nơi khác":

- Đổi icon (play ↔ pause, yêu thích), `Crossfade` giữa trạng thái của cùng một phần tử.
- Hiện / ẩn chip, badge, nhãn phụ, thanh tiến trình, placeholder ảnh khi tải xong.
- Lớp mờ (scrim) phía sau sheet / dialog.
- Hiệu ứng nổi bật dòng lời bài hát.

Nếu không chắc: phần tử đó có làm **thay đổi nơi người dùng đang đứng** không? Có → slide.
Không → được fade.

### 6.4 Hiệu năng

- Ưu tiên đọc giá trị animation trong `graphicsLayer {}` / lambda để không recompose mỗi khung.
- Không chạy `AnimatedVisibility` riêng cho từng item trong danh sách dài.
- Tôn trọng thang animation của hệ thống (Developer options / "Remove animations"); không ép
  thời lượng tối thiểu.

---

## 7. Khả năng tiếp cận & đa ngôn ngữ

- Mọi chuỗi hiển thị và `contentDescription` lấy từ `strings*.xml`, **không hard-code**
  (kể cả tiếng Việt). Bản dịch tiếng Việt nằm trong `res/values-vi/`.
- Icon chỉ mang tính trang trí: `contentDescription = null`.
- Tương phản và vùng chạm: xem mục 2 và 5.

---

## 8. Lộ trình áp dụng quy chuẩn chuyển cảnh

Hiện trạng (trước khi áp dụng) và việc cần làm:

| File | Hiện trạng | Việc cần làm |
|---|---|---|
| `presentation/navigation/Transitions.kt` | Hai bộ transition: `aospSharedAxis*` (slide 1/3 + fade/scale, 350ms) và `enter/exit/popEnter/popExitTransition` (slide 50% + scale + fade, 450ms) | Gộp còn **một** bộ slide thuần theo mục 6.2, thêm token mục 6.1 |
| `presentation/navigation/AppNavigation.kt` | `NavHost` dùng `aospSharedAxis*`; tab chính dùng slide 50% + fade, 380ms | Dùng bộ transition mới; tab chính slide 100%, bỏ fade, 350ms |
| `presentation/components/player/FullPlayerContent.kt` | Lyrics sheet: slide 1/5 + fade | Slide dọc toàn chiều cao, bỏ fade |
| `presentation/components/LyricsSheet.kt` | Predictive back: scale + slide + alpha | Bỏ alpha/scale, chỉ slide theo cử chỉ |
| Dialog / sheet tự dựng (`QueueBottomSheet.kt`, `PlaylistCreationDialogs.kt`, `EditSongSheet.kt`, `FileExplorerBottomSheet.kt`, `BackupModuleSelectionDialog.kt`…) | Nhiều chỗ `slideInVertically + fadeIn` | Bỏ phần fade của chuyển cảnh (giữ fade của scrim) |

---

## 9. Checklist cho PR có thay đổi UI

- [ ] Màu lấy từ `colorScheme`, chữ đạt tương phản ≥ 4.5:1.
- [ ] Bo góc, khoảng cách nằm trong thang chuẩn.
- [ ] Chuyển cảnh màn hình / sheet / dialog dùng slide thuần từ `Transitions.kt`, không fade.
- [ ] Fade chỉ dùng cho micro-animation (mục 6.3).
- [ ] Phần tử neo cạnh màn hình đã cộng inset hệ thống.
- [ ] Không có chuỗi hard-code; có bản dịch `values-vi` nếu thêm chuỗi mới.
- [ ] Đã thử ở chế độ sáng / tối, màn hình nhỏ và xoay ngang.
