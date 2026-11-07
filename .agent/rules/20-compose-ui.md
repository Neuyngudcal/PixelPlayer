---
trigger: model_decision
description: Áp dụng khi tạo hoặc sửa giao diện — Composable, màn hình, component, theme, màu, shape, animation, chuỗi hiển thị, Wear OS UI.
---

# UI/UX — PixelPlayer

Tuân theo `docs/standards/UI_UX_STANDARDS.md`.

Nhắc nhanh:

- Dùng component có sẵn trước (bảng §7 của tài liệu UI): `SmartImage`, `CollapsibleCommonTopBar`,
  `ExpressiveScrollBar`, `LoadingIndicator`, `ShimmerBox`, `ToggleSegmentButton`, `SettingsItem`, …
- Màu = color role; chữ = `MaterialTheme.typography`; bo góc = `ShapeCache.smoothXX`; icon = Rounded.
- Spacing lưới 4 dp, lề màn hình 16 dp, vùng chạm ≥ 48 dp, chừa chỗ cho mini player + nav bar + insets.
- Motion: `Transitions.kt` cho điều hướng, `MotionScheme.expressive()`/spring cho component.
- Component tái sử dụng nhận `modifier: Modifier = Modifier`, dữ liệu + lambda; không nhận ViewModel.
- Kiểm tra light/dark/màu ảnh bìa, cỡ chữ lớn, RTL, TalkBack.
