---
trigger: always_on
---

# PixelPlayer — quy chuẩn cốt lõi (Antigravity)

Nguồn chuẩn duy nhất là `AGENTS.md` ở gốc repo, chi tiết ở `docs/standards/CODE_STANDARDS.md` và
`docs/standards/UI_UX_STANDARDS.md`. **Trước khi viết hoặc sửa code, đọc `AGENTS.md`** và phần liên quan
của tài liệu chi tiết. Nếu file này mâu thuẫn với `AGENTS.md`, `AGENTS.md` thắng.

## Tóm tắt bắt buộc (đồng bộ với AGENTS.md §2–§3)

- Stack cố định: Kotlin 2.3 (built-in Kotlin của AGP 9), JVM 21, minSdk 30, KSP2, Jetpack Compose +
  Material 3 Expressive, Hilt, Room (có migration), DataStore, Coroutines/Flow, Media3, Coil 2, Timber.
- Theo pattern có sẵn: tìm file cùng loại và bắt chước; không phát minh kiến trúc mới.
- KHÔNG sinh mới: XML layout/Fragment, Compose Material 2, LiveData, `collectAsState()`, `GlobalScope`,
  `runBlocking`, kapt, `android.util.Log`, SharedPreferences, Gson cho model mới, Ktor client/Volley,
  `AsyncImage` trực tiếp/Coil 3/Glide, route type-safe `composable<T>`, test JUnit 4/Mockito,
  nhánh code cho API < 30.
- UI: chỉ dùng `MaterialTheme.colorScheme/typography`, `ShapeCache`, icon `Icons.Rounded`,
  chuỗi trong `res/values/strings_*.xml` + `stringResource()`, ảnh qua `SmartImage`,
  transition qua `Transitions.kt`, haptics qua `LocalAppHapticsConfig`.
- State: `StateFlow` → `collectAsStateWithLifecycle()`; UiState `@Immutable` + `ImmutableList`;
  slice state lớn bằng `.map{}.distinctUntilChanged()`; LazyList có `key`.
- Không thêm logic vào `PlayerViewModel` — tạo `XxxStateHolder`.
- Không phá dữ liệu cũ: Room migration + schema JSON, không đổi key DataStore, backup và payload `:shared`
  tương thích ngược.
- Không đổi dependency/version, không refactor ngoài phạm vi task.
- Kết thúc: chạy `scripts/check-standards.sh`; chạy Gradle nếu có SDK (`:app:assembleDebug`,
  `:app:testDebugUnitTest`); báo cáo trung thực nếu không chạy được.
- Comment trong code bằng tiếng Anh; commit `type(scope): summary`.
