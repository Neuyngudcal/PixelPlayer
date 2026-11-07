# AGENTS.md — Quy chuẩn chung cho AI agent (Claude Code, Antigravity, …)

> **Nguồn chuẩn duy nhất (single source of truth).** `CLAUDE.md` và `.agent/rules/*` chỉ trỏ về file này
> và `docs/standards/`. Khi đổi quy chuẩn, sửa ở đây trước, rồi đồng bộ phần tóm tắt trong
> `.agent/rules/00-pixelplayer-core.md`.

Tài liệu chi tiết (BẮT BUỘC đọc trước khi viết code trong khu vực tương ứng):

| Khi làm việc với…                                      | Đọc                                                         |
| ------------------------------------------------------ | ----------------------------------------------------------- |
| Kotlin, kiến trúc, data, DI, coroutine, build, test    | [`docs/standards/CODE_STANDARDS.md`](docs/standards/CODE_STANDARDS.md)   |
| Compose UI, theme, màu, shape, motion, haptics, a11y   | [`docs/standards/UI_UX_STANDARDS.md`](docs/standards/UI_UX_STANDARDS.md) |

---

## 1. Dự án là gì

PixelPlayer — trình phát nhạc Android, **100% Kotlin + Jetpack Compose + Material 3 (Expressive)**.

| Module            | Vai trò                                                                     |
| ----------------- | --------------------------------------------------------------------------- |
| `:app`            | Ứng dụng điện thoại (package `com.theveloper.pixelplay`)                    |
| `:shared`         | Hợp đồng dữ liệu phone ↔ Wear (`@Serializable`, phải tương thích ngược)     |
| `:wear`           | Ứng dụng Wear OS (Wear Compose Material 3 + Horologist)                     |
| `:baselineprofile`| Sinh Baseline Profile                                                       |

**Nền tảng cố định — không được hạ cấp hay thay thế:**
Kotlin 2.4 (K2) · AGP 9.2 với *built-in Kotlin* (`android.builtInKotlin=true`, `android.newDsl=true`) ·
Java/JVM target 21 · `minSdk 30` · `compileSdk/targetSdk 37` · KSP2 (không kapt) · Compose BOM 2026.06 ·
Material3 `1.5.0-alpha` (Expressive API) · Hilt 2.59 · Room 2.8 (schema v42) · Media3 1.10 · Coil **2.x** ·
DataStore Preferences · kotlinx.coroutines/serialization/collections-immutable · Timber.

---

## 2. Quy tắc vàng (Golden rules)

1. **Theo pattern đang có, không phát minh pattern mới.** Trước khi viết, tìm 1–2 file cùng loại
   (screen, StateHolder, Repository, DAO…) và bắt chước cấu trúc của chúng.
2. **Không sinh "code cũ"** (xem §3). Chỉ dùng pattern legacy khi đang *sửa* đúng chỗ đã dùng nó
   hoặc khi tương thích dữ liệu bắt buộc — và phải ghi lý do bằng comment `// Legacy: <lý do>`.
3. **Không thêm thư viện, không đổi version** nếu không được yêu cầu. Mọi dependency đi qua
   `gradle/libs.versions.toml`; không hardcode version trong `build.gradle.kts`.
4. **Không phá tương thích dữ liệu người dùng:** Room phải có `Migration` + schema JSON mới;
   format backup, key DataStore, payload `:shared` (Wear) phải đọc được dữ liệu cũ.
5. **UI chỉ dùng token của theme:** `MaterialTheme.colorScheme.*`, `MaterialTheme.typography.*`,
   `ShapeCache`/`MaterialTheme.shapes`. Không hardcode màu, font, chuỗi hiển thị.
6. **Chuỗi hiển thị nằm trong `res/values/strings_*.xml` (tiếng Anh)** và dùng `stringResource()`.
7. **State một chiều:** `StateFlow` (ViewModel/StateHolder) → `collectAsStateWithLifecycle()` (UI)
   → callback lambda (sự kiện). Không LiveData, không `collectAsState()` mới.
8. **Không nhồi thêm vào `PlayerViewModel`** (đã > 3000 dòng). Logic mới → `XxxStateHolder` riêng.
9. **Hiệu năng là tiêu chí chấp nhận:** list có `key`, state được "slice", collection trong UI state là
   `ImmutableList`, không chạy I/O trên main thread. Xem §Performance trong 2 tài liệu chi tiết.
10. **Thay đổi nhỏ, đúng phạm vi.** Không reformat/rename/refactor ngoài phạm vi yêu cầu; không xoá
    code "trông có vẻ thừa" mà chưa kiểm tra usages.

---

## 3. Chuẩn hiện hành vs. pattern cũ (cấm sinh mới)

| Chủ đề          | ✅ Dùng                                                                 | ⛔ Không sinh mới                                              |
| --------------- | ----------------------------------------------------------------------- | -------------------------------------------------------------- |
| UI toolkit      | Jetpack Compose + `androidx.compose.material3`                          | XML layout/View, Fragment, `androidx.compose.material` (M2)    |
| State           | `StateFlow` + `stateIn(...)`, `collectAsStateWithLifecycle()`           | `LiveData`, `collectAsState()`, `mutableStateOf` trong ViewModel cho state chia sẻ |
| Async           | Coroutines, `viewModelScope`, scope `@AppScope` được inject             | `GlobalScope`, `runBlocking`, `AsyncTask`, `Thread {}`, RxJava, callback lồng nhau |
| DI              | Hilt (`@HiltViewModel`, `@Inject constructor`, `@Module` trong `di/`)   | Khởi tạo thủ công singleton, service locator, Koin             |
| Annotation proc.| KSP                                                                     | kapt                                                           |
| Lưu cài đặt     | DataStore trong `data/preferences/*PreferencesRepository`               | `SharedPreferences` mới (chỉ giữ chỗ đã có)                    |
| JSON            | `kotlinx.serialization` (`@Serializable`)                               | Gson cho model **mới** (Gson chỉ trong pipeline đã có: backup, Retrofit Deezer/LrcLib, Room converter) |
| Mạng            | OkHttp client từ DI + `data/network/<provider>/XxxApiService.kt`        | Ktor client, Volley, `HttpURLConnection`, OkHttpClient tự tạo  |
| Ảnh             | `SmartImage(...)` (Coil 2)                                              | `AsyncImage` trực tiếp, Coil 3 (`coil3.*`), Glide, Picasso     |
| Log             | `Timber`                                                                | `android.util.Log`, `println`                                  |
| Điều hướng      | `Screen` sealed class (string route) + `AppNavigation.kt` + `Transitions.kt` | Type-safe route `composable<T>`, Navigation 3, route string rời rạc |
| Shape bo góc    | `ShapeCache.smoothXX`, `MaterialTheme.shapes`, shape trong `utils/shapes/` | `AbsoluteSmoothCornerShape(...)` tạo mới trong item của Lazy list |
| Haptics         | `performAppHapticFeedback(LocalAppHapticsConfig.current, …)`            | `view.performHapticFeedback` bỏ qua cài đặt người dùng         |
| Dark mode       | `LocalPixelPlayDarkTheme.current`                                       | `isSystemInDarkTheme()` bên trong component                    |
| Test            | JUnit 5 (Jupiter) + MockK + Turbine + Truth + `MainCoroutineExtension`  | Test JUnit 4 mới, Mockito, Robolectric                         |
| Comment/doc     | Tiếng Anh, KDoc cho API public                                          | Comment tiếng Tây Ban Nha/Việt mới trong source                |
| API level       | Code cho API ≥ 30; `Build.VERSION.SDK_INT` check chỉ cho API > 30       | Nhánh code cho API < 30, `@Suppress("DEPRECATION")` không lý do |

**Khi nào được dùng pattern cũ:** (a) sửa bug ngay trong đoạn code legacy mà việc migrate vượt phạm vi;
(b) bắt buộc để tương thích dữ liệu/định dạng (backup Gson, Room converter, payload Wear);
(c) API mới không tồn tại trên thư viện đang dùng. Luôn ghi `// Legacy: <lý do>`.

---

## 4. Bản đồ code (đặt file ở đâu)

```
app/src/main/java/com/theveloper/pixelplay/
├── data/
│   ├── database/        Room: *Entity, *Dao, PixelPlayDatabase (MIGRATION_x_y)
│   ├── model/           Domain model @Immutable (Song, Album, …) — stable theo compose_stability.conf
│   ├── preferences/     DataStore *PreferencesRepository + enum cài đặt
│   ├── repository/      Interface + *Impl (MusicRepository, LyricsRepository, …)
│   ├── network/<p>/     ApiService cho từng provider (deezer, lyrics, jellyfin, navidrome, …)
│   ├── <provider>/      Repository/model của nguồn nhạc cloud (jellyfin, navidrome, gdrive, telegram…)
│   ├── service/         MusicService, player engine, cast, wear, auto, tile, http
│   └── worker/          WorkManager (HiltWorker)
├── di/                  AppModule, BackupModule, Qualifiers (@AppScope, @FastOkHttpClient…)
├── presentation/
│   ├── navigation/      Screen.kt, AppNavigation.kt, Transitions.kt
│   ├── screens/         Màn hình cấp route (XxxScreen.kt)
│   ├── components/      Composable tái sử dụng (SmartImage, ExpressiveScrollBar, sheets, dialogs)
│   ├── viewmodel/       *ViewModel (@HiltViewModel) + *StateHolder (@Singleton) + *UiState
│   ├── <provider>/      auth/ + dashboard/ cho từng nguồn cloud
│   └── utils/           AppHaptics, GenreIconProvider
├── ui/theme/            Color, ColorRoles, Theme, Type, Shape, ShapeCache, GenreColors
├── ui/glancewidget/     Widget Glance
└── utils/               Tiện ích không phụ thuộc UI
```

---

## 5. Definition of Done (checklist trước khi kết thúc tác vụ)

- [ ] Code nằm đúng package theo §4, tên theo quy ước (`XxxScreen`, `XxxViewModel`, `XxxStateHolder`,
      `XxxRepository`/`XxxRepositoryImpl`, `XxxDao`, `XxxEntity`, `XxxUiState`).
- [ ] Không có mục nào ở cột "⛔" của §3 (trừ khi có `// Legacy:` hợp lệ).
      **Chạy `scripts/check-standards.sh`** — chỉ kiểm tra dòng *mới thêm*; phải 0 ERROR, WARN phải sửa
      hoặc ghi `// Legacy: <lý do>` trên cùng dòng. (Claude: `/check-standards`, Antigravity: workflow `/check-standards`.)
- [ ] Chuỗi mới ở `res/values/strings_*.xml`; màu/shape/typography lấy từ theme.
- [ ] Thay đổi schema Room → tăng `version`, thêm `MIGRATION_x_y`, đăng ký trong `AppModule`, commit `app/schemas/.../<n>.json`.
- [ ] Thay đổi `:shared` → vẫn decode được payload cũ (field mới có default).
- [ ] Logic không tầm thường có unit test (JUnit5) trong `app/src/test/...`.
- [ ] Đã chạy được (nếu môi trường có Android SDK):
      `./gradlew :app:assembleDebug` · `./gradlew :app:testDebugUnitTest` · (đụng `:shared`/`:wear`) `./gradlew :wear:assembleDebug`.
      Nếu không chạy được, nói rõ điều đó trong báo cáo — không khẳng định "đã build OK".
- [ ] Tính năng/thay đổi đáng kể → thêm dòng vào `CHANGELOG.md` mục `## [Unreleased]` (Keep a Changelog).

## 6. Git

- Commit message tiếng Anh, dạng `type(scope): summary` — `type` ∈ `feat|fix|perf|refactor|ui|docs|test|build|chore`.
  Ví dụ: `feat(lyrics): add offset control to sync sheet`.
- Mỗi commit một mục đích; không trộn refactor với feature.
- Không commit `keystore.properties`, `local.properties`, `*.jks`, API key, token.
