# Quy chuẩn Code — PixelPlayer

> Áp dụng cho mọi code do người và AI agent (Claude Code, Antigravity, …) viết.
> Quy tắc tổng quát và bảng "chuẩn mới vs. pattern cũ" nằm ở [`AGENTS.md`](../../AGENTS.md) — tài liệu này
> đi vào chi tiết và ví dụ. Các con số trong ngoặc là kết quả khảo sát codebase (11/2026) để giải thích
> *vì sao* chuẩn được chọn.

---

## 1. Toolchain & build

| Hạng mục              | Chuẩn                                                                                 |
| --------------------- | ------------------------------------------------------------------------------------- |
| Kotlin                | 2.4 (K2), `kotlin.code.style=official`                                                |
| Plugin Kotlin         | **Built-in Kotlin của AGP 9** — *không* thêm `org.jetbrains.kotlin.android`           |
| JVM                   | `JavaVersion.VERSION_21`, `JvmTarget.JVM_21`, có core library desugaring              |
| Annotation processing | **KSP2** (`ksp(...)`) — không `kapt`                                                  |
| Dependency            | Chỉ qua `gradle/libs.versions.toml` → `libs.xxx`                                      |
| Repository Maven      | Khai báo tập trung ở `settings.gradle.kts`; JitPack chỉ cho các `includeGroup` đã liệt kê |
| SDK                   | `minSdk 30`, `compileSdk/targetSdk 37`                                                |

Quy tắc:

- Không đổi version/thư viện nếu task không yêu cầu. Nếu thật sự cần thư viện mới: thêm vào
  `[versions]` + `[libraries]` của catalog, giải thích lý do, ưu tiên thư viện AndroidX/Jetbrains.
- Không thêm lại các cờ Gradle đã bị gỡ trong `gradle.properties` (vd. `android.enableR8.fullMode=false`).
- Không dùng API đã deprecated khi có thay thế trên `minSdk 30`. Nếu bắt buộc dùng,
  `@Suppress("DEPRECATION")` ở phạm vi nhỏ nhất + comment lý do.
- Kiểm tra version: `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` (31) trở lên là hợp lệ; không viết
  nhánh cho API < 30.

Lệnh kiểm tra:

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew :app:testDebugUnitTest
```

```bash
./gradlew :wear:assembleDebug
```

---

## 2. Kiến trúc

```
UI (Composable)  ──event lambda──▶  ViewModel (@HiltViewModel)
      ▲                                  │ ủy quyền logic
      │ StateFlow → collectAsStateWithLifecycle   ▼
      └──────────────────────────  StateHolder (@Singleton, theo feature)
                                         │
                                         ▼
                         Repository (interface + Impl) / PreferencesRepository
                                         │
                         Room DAO · DataStore · ApiService (OkHttp/Retrofit) · MediaStore · Media3
```

### 2.1 Tầng data

- **Repository:** interface `XxxRepository` + `XxxRepositoryImpl @Inject constructor(...)`, bind trong `di/`.
  Repository của nguồn cloud đặt trong `data/<provider>/` (vd. `data/jellyfin/JellyfinRepository.kt`).
- Repository trả `Flow<T>` cho dữ liệu quan sát được, `suspend fun` cho thao tác một lần. Không trả
  `LiveData`, không nhận callback.
- **Chuyển thread ở tầng data**, không ở UI: `withContext(Dispatchers.IO)` cho I/O, `Dispatchers.Default`
  cho CPU (parse, palette, sort lớn). Class cần test nên nhận dispatcher qua constructor có default:
  `private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO`.
- **Paging:** danh sách thư viện lớn dùng Paging 3 (`room-paging`), ViewModel gọi `.cachedIn(viewModelScope)`.

### 2.2 Room (`data/database/`)

- Đặt tên: `XxxEntity` (table), `XxxDao`, quan hệ `XxxCrossRef`, FTS `XxxFtsEntity`.
- **Mọi thay đổi schema:**
  1. Tăng `version` trong `PixelPlayDatabase` (hiện tại 42).
  2. Viết `MIGRATION_<old>_<new>` trong `PixelPlayDatabase` (SQL tường minh).
  3. Đăng ký trong `AppModule.provideDatabase(...)` `.addMigrations(...)`.
  4. Commit file schema `app/schemas/.../<new>.json` (đã bật `exportSchema = true`).
- `fallbackToDestructiveMigration` **chỉ** tồn tại cho debug build — không thêm cho release.
- Query nặng (thống kê) phải là SQL aggregate một lượt, không load cả thư viện vào bộ nhớ.

### 2.3 Cài đặt người dùng (`data/preferences/`)

- Dùng **DataStore Preferences** qua `UserPreferencesRepository`, `ThemePreferencesRepository`,
  `AiPreferencesRepository`, `EqualizerPreferencesRepository`, `PlaylistPreferencesRepository`.
  Chọn repository đúng miền; chỉ tạo repository mới khi có miền hoàn toàn mới.
- Mẫu: `val xxxFlow: Flow<T> = dataStore.data.map { it[KEY] ?: DEFAULT }.distinctUntilChanged()` +
  `suspend fun setXxx(value: T) { dataStore.edit { it[KEY] = value } }`.
- **Không đổi tên key đã phát hành** (mất cài đặt người dùng). Enum lưu bằng `name` và có fallback khi
  parse lỗi.
- Cài đặt mới cần được xem xét đưa vào backup (`data/backup/module/GlobalSettingsModuleHandler.kt`).

### 2.4 Mạng (`data/network/<provider>/`)

- Dùng `OkHttpClient` được inject (có qualifier `@FastOkHttpClient` cho timeout ngắn). Không tạo client mới.
- Provider mới: `XxxApiService.kt` (OkHttp hoặc Retrofit nếu provider đó đã dùng Retrofit) + model
  `@Serializable` + repository. Retry dùng `utils/NetworkRetryUtils.kt`.
- Cho phép HTTP thuần chỉ với host mạng LAN (Navidrome/Jellyfin) như hiện hành.
- **Không log** token, mật khẩu, URL có credential, đường dẫn file hay metadata thư viện của người dùng.

### 2.5 Serialization

- Model mới: `kotlinx.serialization` (`@Serializable`, `Json { ignoreUnknownKeys = true }`).
- Gson **chỉ** tiếp tục dùng trong: định dạng backup (`data/backup/**`, `@BackupGson`), Retrofit
  Deezer/LrcLib (`converter-gson`), Room converter đã có. Không chuyển các chỗ này sang thư viện khác vì
  sẽ phá tương thích file backup cũ (`LegacyPayloadAdapter`).
- Payload trong `:shared` (Wear) là hợp đồng giữa 2 app có thể khác version → field mới **luôn có giá trị
  mặc định**, không xoá/đổi tên field.

### 2.6 Tầng presentation — ViewModel & StateHolder

- `@HiltViewModel class XxxViewModel @Inject constructor(...) : ViewModel()`.
- **StateHolder** (`presentation/viewmodel/XxxStateHolder.kt`): class `@Singleton` gom logic một feature
  (queue, lyrics, cast, search, sleep timer…). Nhận `CoroutineScope` của ViewModel qua
  `initialize(scope)` hoặc một class `XxxCallbacks(scope, …)` (xem `QueueStateHolder`,
  `PlaybackDispatchStateHolder`). Không tự tạo scope sống mãi.
- `PlayerViewModel` chỉ được *điều phối* StateHolder; tính năng mới → StateHolder mới, rồi inject.
- Expose state:
  ```kotlin
  private val _uiState = MutableStateFlow(XxxUiState())
  val uiState: StateFlow<XxxUiState> = _uiState.asStateFlow()
  // hoặc dẫn xuất:
  val foo: StateFlow<Foo> = repo.fooFlow
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Foo.Default)
  ```
  Cập nhật bằng `_uiState.update { it.copy(...) }` — không gán `.value` từ nhiều coroutine.
- `XxxUiState` là `data class` `@Immutable`, collection là `ImmutableList`/`ImmutableMap`
  (`persistentListOf()`, `.toImmutableList()`). Nếu class không nằm trong `data.model.**`, thêm FQCN vào
  `app/compose_stability.conf`.
- Sự kiện một lần (toast, snackbar, điều hướng): `MutableSharedFlow<Event>(extraBufferCapacity = 1)`
  expose dạng `SharedFlow` (pattern đang dùng trong codebase), UI thu bằng `LaunchedEffect`.
  Không nhét cờ boolean "đã hiển thị" vào UiState.

### 2.7 DI (`di/`)

- Module là `object` `@Module @InstallIn(SingletonComponent::class)`; provider `@Provides @Singleton`.
- Qualifier khai báo trong `di/Qualifiers.kt` (`@AppScope`, `@FastOkHttpClient`, `@DeezerRetrofit`,
  `@BackupGson`). Scope cấp ứng dụng: inject `@AppScope CoroutineScope` — **không** `GlobalScope`.
- Worker: `@HiltWorker` + `@AssistedInject`.

---

## 3. Coroutines & threading

- Không `GlobalScope`, `runBlocking` (kể cả trong `Service`/`TileService` — dùng scope gắn lifecycle).
- Không `Thread.sleep`; dùng `delay`.
- Không bắt `CancellationException` rồi nuốt: dùng `runCatching` cẩn thận hoặc `catch (e: Exception)` +
  `if (e is CancellationException) throw e`.
- Flow nóng cho UI: `stateIn(..., SharingStarted.WhileSubscribed(5_000), initial)`.
- Flow tần suất cao (vị trí phát nhạc, ~250 ms) phải tách riêng, không gộp vào `UiState` lớn.

---

## 4. Kotlin style

- Theo [Kotlin official style](https://kotlinlang.org/docs/coding-conventions.html), indent 4 space,
  trailing comma ở danh sách tham số nhiều dòng.
- Đặt tên: `PascalCase` cho class/Composable, `camelCase` cho hàm/biến, `UPPER_SNAKE` cho `const val`,
  hằng số đặt trong `companion object` hoặc top-level `private const val`.
- Ưu tiên `val`, `data class`, `sealed interface` cho trạng thái/kết quả (`sealed interface Result { … }`).
- Không dùng `!!` — dùng `?:`, `requireNotNull(x) { "msg" }`, hoặc early return.
- Hàm > ~60 dòng hoặc file > ~800 dòng: tách. Không làm file khổng lồ hiện có (PlayerViewModel,
  CastTransferStateHolder, SettingsViewModel) to thêm.
- Comment & KDoc **bằng tiếng Anh**, giải thích *tại sao*. Comment tối ưu hiệu năng dùng tiền tố như code
  đang dùng (`// P2-1:`, `OPT #6`) khi tham chiếu tới báo cáo hiệu năng.
- Log: `Timber.d/i/w/e(...)` với tag mặc định; dùng `Timber.tag("X")` khi cần. Không log PII.
- Không để `TODO` không có ngữ cảnh: `// TODO(<scope>): <việc cần làm>`.

---

## 5. Test

- Unit test: `app/src/test/java/com/theveloper/pixelplay/<cùng package>/XxxTest.kt`.
- Stack: **JUnit 5** (`org.junit.jupiter.api.*`, `@Nested`, `@DisplayName`, `@ParameterizedTest`),
  **MockK**, **Turbine** (`flow.test { }`), **Truth** hoặc Jupiter assertions, `kotlinx-coroutines-test`.
- Coroutine: `@ExtendWith(MainCoroutineExtension::class)` + `runTest { }`.
- JUnit 4 chỉ còn để chạy test cũ qua vintage engine — không viết test JUnit 4 mới.
- Ưu tiên test logic thuần (parser, sort, queue, color, metrics) — tách khỏi Android framework để test được.
- Migration Room: test trong `androidTest` với `MigrationTestHelper` khi thêm migration phức tạp.

---

## 6. Bảo mật & quyền riêng tư

- Không commit secret; giá trị nhạy cảm đọc từ `local.properties`/`keystore.properties` qua `BuildConfig`.
- Credential của provider lưu bằng `androidx.security.crypto` như code hiện có.
- Báo cáo chẩn đoán/hiệu năng không chứa đường dẫn, tên bài, nghệ sĩ (xem `docs/performance-report.md`).
- Nội dung import từ ngoài (lyrics, backup) phải qua bước validate như `LyricsImportSecurity.kt`,
  `data/backup/validation/`.

---

## 7. Nợ kỹ thuật đã biết — không tự ý sửa ngoài phạm vi

Các pattern cũ dưới đây đang tồn tại (khảo sát 11/2026). Agent **không được** sinh thêm, nhưng cũng
**không** tự ý dọn dẹp khi task không yêu cầu (tránh diff khổng lồ, rủi ro hồi quy). Chỉ sửa khi đang
chạm đúng đoạn code đó hoặc khi có task migrate riêng.

| Pattern cũ                         | Hiện trạng                                             |
| ---------------------------------- | ------------------------------------------------------ |
| `collectAsState()`                 | 2 file ở `:app`, 11 file ở `:wear`                     |
| `android.util.Log`                 | ~31 file — thay bằng Timber khi chạm vào               |
| `Text("…")` chuỗi cứng             | ~9 file                                                |
| `Color(0x…)` ngoài `ui/theme`      | `SettingsScreen`, `AccountsScreen`, `StreamingProviderSheet`, … |
| `SharedPreferences`                | ~8 file (repository cloud, crash handler, locale)      |
| `AbsoluteSmoothCornerShape(...)` inline | ~60 file — chuyển sang `ShapeCache` khi chạm vào  |
| Test JUnit 4                       | còn chạy qua vintage engine                            |
| Comment tiếng Tây Ban Nha          | ~22 file — dịch sang tiếng Anh khi sửa đoạn đó         |
| `PlayerViewModel` > 3000 dòng      | tiếp tục tách sang StateHolder                         |
| Wear dùng `wear.compose.material` (M2) | đa số màn hình Wear                                |

`scripts/check-standards.sh <empty-tree-hash>` liệt kê toàn bộ để lập kế hoạch migrate:
`scripts/check-standards.sh $(git hash-object -t tree /dev/null)`.

---

## 8. Checklist nhanh khi review code (người hoặc AI)

- [ ] Có dùng bất kỳ mục "⛔" trong `AGENTS.md §3` không?
- [ ] Có I/O/CPU nặng trên main thread không?
- [ ] Có làm vỡ dữ liệu cũ (Room, DataStore key, backup, Wear payload) không?
- [ ] UiState có immutable/stable không? Có cần thêm vào `compose_stability.conf`?
- [ ] Có test cho logic mới chưa?
- [ ] Có thay đổi ngoài phạm vi yêu cầu không?
