# PixelPlayer — Stage 2 Playback Engine, Media3, DSP, Cast, Proxies & Wear OS Remediation Plan & Prompts (for Gemini 4 Hydrogen High Thinking)

> **Target Model:** Gemini 4 Hydrogen High Thinking  
> **Scope:** Giải quyết toàn diện **27 vấn đề Giai Đoạn 2 (`2.1` – `2.27`)** trên `app/`, `wear/` và `shared/` thành **7 đợt sửa (Waves)** độc lập theo từng miền kiến trúc, đảm bảo không xung đột file giữa các đợt liên tiếp và giữ nguyên các hàm/cơ chế đã được vá sẵn trong mã nguồn hiện tại.  
> **Project Conventions:** Tuân thủ `AGENTS.md` (không nhân bản logic dùng chung, fail-closed cho bảo mật/SSRF, tách I/O khỏi Main Thread và Ktor CIO event loop, bảo toàn tương thích ngược của DB/MediaSession/Wear OS).

---

## 1. Chiến Lược Chia 7 Đợt Cho Gemini 4 Hydrogen High Thinking

Vì Stage 2 trải rộng qua 25+ file lớn nhỏ (riêng `MusicService.kt` ~2.917 dòng, `MediaFileHttpServerService.kt` ~2.939 dòng, `DualPlayerEngine.kt` ~1.581 dòng, `SongMetadataEditor.kt` ~1.388 dòng, `CastPlayer.kt` ~1.130 dòng, `WearTransferRepository.kt` ~1.090 dòng), gộp cả 27 mục vào 1 prompt sẽ gây tràn ngữ cảnh và dễ đè sửa chéo nhau. Chia thành **7 Đợt (Waves)** tối ưu cho **Gemini 4 Hydrogen High Thinking**:

| Đợt | Phạm Vi Mã Vấn Đề | Miền Kiến Trúc | Số File Trọng Tâm | Phụ Thuộc & Thứ Tự Chạy |
| :--- | :--- | :--- | :--- | :--- |
| **Đợt 1** | `2.1`, `2.2`, `2.3`, `2.4`, `2.5`, `2.6`, `2.8` | Service Lifecycle, `dataSync` FGS `onTimeout`, Sleep Timer & `MusicService` Commands/Widget | ~7 file (`AndroidManifest.xml`, `WatchTransferForegroundService.kt`, `ModelDownloadService.kt`, `SleepTimerReceiver.kt`, `SleepTimerStateHolder.kt`, `MusicService.kt`, `WidgetUpdateManager.kt`) | **Chạy đầu tiên** (giữ ổn định khung Service & `MusicService.kt` trước khi Đợt 2 sửa listener/swap) |
| **Đợt 2** | `2.7`, `2.9`, `2.10`, `2.11`, `2.12`, `2.13` | `DualPlayerEngine`, `TransitionController`, URI Resolution, 200-Item Queue & Listener Swap | 3 file chính (`DualPlayerEngine.kt`, `TransitionController.kt`, `MusicService.kt`) | **Chạy sau Đợt 1** (cùng chạm `MusicService.kt` nhưng ở vùng `countedPlayListener` & `playerSwapListener`) |
| **Đợt 3** | `2.14`, `2.15`, `2.16`, `2.17`, `2.18`, `2.25` | Audio DSP (`HiResSampleRateCap`, `SurroundDownmix`), ReplayGain / Opus R128 & `EqualizerManager` | 6 file nguồn + 2 file test (`ReplayGainProcessor.kt`, `ReplayGainManager.kt`, `AudioMetadataReader.kt`, `HiResSampleRateCapAudioProcessor.kt`, `SurroundDownmixProcessor.kt`, `EqualizerManager.kt`) | **Độc lập** (có thể chạy song song sau Đợt 2) |
| **Đợt 4** | `2.19`, `2.20` | Google Cast Stack (`CastPlayer` & `MediaFileHttpServerService`) | 2 file rất lớn (~4.069 dòng gộp) + test Cast | **Độc lập** |
| **Đợt 5** | `2.21`, `2.22` | Stream Proxies (`CloudStreamProxy`, `GDrive`, `Telegram`), SSRF Hardening & Telegram Cache/Download | 7 file nguồn + `CloudStreamSecurityTest.kt` | **Chạy sau Đợt 2** (phối hợp sạch với `DualPlayerEngine.resolveDataSpec`) |
| **Đợt 6** | `2.23`, `2.24` | Netease/QQ Music URL Request Coalescing & `QQSignGenerator` WebView Isolation | 3 file (`NeteaseRepository.kt`, `QqMusicRepository.kt`, `QQSignGenerator.kt`) + unit test mới | **Độc lập** |
| **Đợt 7** | `2.26`, `2.27` | `SongMetadataEditor` DB Sync/FLAC Routing & Wear OS Transfer/Manifest/Migration | 6 file (`SongMetadataEditor.kt`, `MusicDao.kt`, `PhoneDirectWatchTransferCoordinator.kt`, `wear/AndroidManifest.xml`, `WearModule.kt`, `WearTransferRepository.kt`) | **Độc lập** |

> [!IMPORTANT]
> **Đối Chiếu Thực Tế Mã Nguồn Hiện Tại (Tránh Prompt Drift):**
> Trước khi giao prompt cho Gemini 4 Hydrogen High Thinking, các điểm đã thay đổi hoặc tiến hóa một phần so với mô tả ban đầu đã được ghi rõ vào từng Đợt:
> - **`2.9`:** `DualPlayerEngine` (`line 11, 762`) đang dùng `android.util.LruCache<String, Uri>(100)` (bản thân SDK Android đồng bộ `synchronized(this)`, khác `LinkedHashMap`), nhưng vẫn cần cơ chế bounded + TTL + single-flight `CompletableDeferred` cho `2.10`.
> - **`2.11`:** `performOverlapTransition` (`DualPlayerEngine.kt:1446-1447`) đã giữ `activeWindowStartIndex`/`activePlayerUsesWindowedQueue`, nhưng **`rebuildPlayersPreservingMasterState` (`943-978`)** vẫn sao chép từ `playerA` (200 bài khi đang windowed) thay vì khôi phục từ `queueSnapshot`.
> - **`2.14`:** `ReplayGainProcessor.kt` (`1-274`) hiện **không còn** `targetGainDb`/`currentGainDb`/`currentGainLinear` chạy trên luồng audio DSP nữa; hiện tại dùng `player.volume` trên Main Thread (`expectedVolume`/`pendingVolume`/`lastAppliedVolume`). Lỗi còn sót là `ReplayGainManager.gainDbToVolume` trả `[0f, 2f]` nhưng `player.volume` cắt cứng tại `[0f, 1f]` (mất toàn bộ boost dương), cùng nguy cơ race `pendingVolume` khi crossfade/skip nhanh.
> - **`2.17`:** `HiResSampleRateCapAudioProcessor.kt` (`1-271`) **không còn** `resampleLinear` / `targetSampleRateCapHz` / `previousFrameSamples`; hiện tại đã chuyển sang chia nguyên `downsampleFactor = ceil(sampleRate / 192000)` (không còn lệch pitch, pass 4 test hiện có). Lỗi DSP còn sót là `queueEndOfStream()` (`line 251-254`) xóa `pendingSize = 0` làm rớt tới `downsampleFactor - 1` frame cuối, và bộ lọc boxcar (`1/M`) suy hao aliasing kém (~-13dB).
> - **`2.19` & `2.20`:** Ktor đang dùng engine **`CIO`** (`MediaFileHttpServerService.kt:384`), không phải Netty; 3 map tĩnh trong `CastPlayer.kt:43-45` là `extractorMimeCache`, `retrieverMimeCache`, `signatureMimeCache`.
> - **`2.22`:** `TelegramStreamProxy.kt:228-235` đã có backoff `50ms..400ms`, nhưng vòng `while (true)` vẫn **thiếu timeout kết thúc khi kẹt mạng/TDLib**, `downloadFile(fileId, 1)` vẫn dùng `offset = 0`, và `TelegramRepository.downloadFileAwait` (`637-723`) vẫn bị TOCTOU `activeDownloads` + đăng ký `updates` sau khi gửi `TdApi.DownloadFile`.

---

## Đợt 1 — Service Lifecycle, `dataSync` FGS `onTimeout`, Sleep Timer & `MusicService` Commands/Widget (`2.1`, `2.2`, `2.3`, `2.4`, `2.5`, `2.6`, `2.8`)

```text
You are a Senior Android Systems & Media3 Engineer working on PixelPlayer (`c:\Users\lacdu\Downloads\PixelPlayer-master`).
Read `AGENTS.md` first, then implement **Wave 1 (`2.1`, `2.2`, `2.3`, `2.4`, `2.5`, `2.6`, `2.8`)**.
Read the exact target regions of every file below before editing. Do NOT invent imports, fields, or APIs.

### Target Files
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/theveloper/pixelplay/data/service/wear/WatchTransferForegroundService.kt`
- `app/src/main/java/com/theveloper/pixelplay/data/ai/offline/ModelDownloadService.kt`
- `app/src/main/java/com/theveloper/pixelplay/data/service/SleepTimerReceiver.kt`
- `app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/SleepTimerStateHolder.kt` (inspect alarm scheduling/canceling)
- `app/src/main/java/com/theveloper/pixelplay/data/service/MusicService.kt`
- `app/src/main/java/com/theveloper/pixelplay/data/service/WidgetUpdateManager.kt`

---

### Task 2.1 — Handle `Service.onTimeout(startId, fgsType)` for `dataSync` Foreground Services
**Current Facts:**
- `AndroidManifest.xml` (`~lines 246-251`) declares `WatchTransferForegroundService` and `ModelDownloadService` with `android:foregroundServiceType="dataSync"` (`targetSdk = 35`).
- Neither `WatchTransferForegroundService` nor `ModelDownloadService` overrides `onTimeout(startId: Int, fgsType: Int)`.
- `ModelDownloadService.onStartCommand` (`lines 389-415`) returns `START_STICKY`.

**Requirements:**
1. In `WatchTransferForegroundService`:
   - Override `onTimeout(startId: Int, fgsType: Int)` (API 35+ signature `override fun onTimeout(startId: Int, fgsType: Int)` — verify compileSdk >= 35; also add safe error logging). Cancel/mark any active watch transfers as interrupted or timed-out via the existing transfer cancellation store if appropriate, call `stopForegroundCompat()` (removing notification), and call `stopSelf(startId)` cleanly within the Android 15 timeout grace window. Also override the 1-arg `onTimeout(startId: Int)` only if needed or call shared `handleServiceTimeout(startId)`.
2. In `ModelDownloadService`:
   - Override `onTimeout(startId: Int, fgsType: Int)`: cancel active GGUF download jobs gracefully (`downloadManager.cancelAll()` or expose a helper on `OfflineModelDownloadManager` preserving partial `.tmp` files for resume!), call `stopForeground(STOP_FOREGROUND_REMOVE)`, and call `stopSelf(startId)`.
   - Change `onStartCommand` return value from `START_STICKY` to `START_NOT_STICKY` so the OS does not auto-restart an orphaned empty `dataSync` FGS after process death/timeout.

---

### Task 2.2 — Fix `BackgroundServiceStartNotAllowedException` from `SleepTimerReceiver`
**Current Facts:**
- `SleepTimerReceiver.kt` (`lines 8-20`) unconditionally calls `context.startService(serviceIntent)` with `action = ACTION_SLEEP_TIMER_EXPIRED` inside `BroadcastReceiver.onReceive()`.
- On Android 12+, if playback is already paused and the app is backgrounded (`MusicService` is no longer foreground), `context.startService()` throws `BackgroundServiceStartNotAllowedException`, and starting it foreground with an empty notification causes `ForegroundServiceStartNotAllowedException`.

**Requirements:**
1. Inspect `SleepTimerStateHolder.kt` (`lines 55-125`), `SleepTimerReceiver.kt`, and `MusicService.kt` (`ACTION_SLEEP_TIMER_EXPIRED` around `line 1171`).
2. Design a background-safe expiration flow that **never crashes when `MusicService` is stopped or backgrounded/paused**:
   - Introduce a lightweight `@Singleton` in-process coordinator (e.g., `SleepTimerCoordinator` or callback/SharedFlow injected into `MusicService` and accessed safely from `SleepTimerReceiver` via Hilt `@EntryPoint` if running, OR an atomic active-service reference + persisted timer cleanup) AND ensure `MusicService` cancels any pending duration `AlarmManager` timer when `MusicService` permanently stops (`onDestroy()` / `stopPlaybackAndUnload()`) or pauses if the timer shouldn't wake a dead/paused background service.
   - Inside `SleepTimerReceiver.onReceive()`: if `MusicService` is currently alive in this process, notify it in-process (post on Main looper / SharedFlow) to pause & clear timer state directly **without calling `context.startService()`**; if the service is NOT alive, simply clear any stale persisted sleep timer expiration preference/alarm state and return safely. Never call `startService()` / `startForegroundService()` from background `BroadcastReceiver.onReceive()`.

---

### Task 2.3 — Remove Main-Thread `UncaughtExceptionHandler` Swallowing & Prevent Root `ForegroundServiceStartNotAllowedException`
**Current Facts:**
- `MusicService.kt` (`lines 405-415`): `installForegroundStartCrashGuard()` overrides `Thread.currentThread().setUncaughtExceptionHandler` on Main Thread and swallows `ForegroundServiceStartNotAllowedException` WITHOUT invoking `existingHandler?.uncaughtException(thread, throwable)`. Because `Looper.loop()` has already unwound on the Main Thread when an uncaught exception reaches the handler, swallowing it leaves a dead UI thread / zombie ANR process.

**Requirements:**
1. Remove the Main Thread `UncaughtExceptionHandler` swallowing in `installForegroundStartCrashGuard()` (and remove `installForegroundStartCrashGuard()` altogether once root triggers are guarded, OR if kept for diagnostics logging, ALWAYS delegate `existingHandler?.uncaughtException(thread, throwable)` in `finally` — never swallow an uncaught Main-Thread exception).
2. Audit why Media3 `MediaNotificationManager` or `MusicService` triggered `ForegroundServiceStartNotAllowedException`: check `onUpdateNotification` / `updateNotification` / Cast / background command handling (`onStartCommand`, notification updates when `!playWhenReady` and app in background), ensuring `MusicService` never forces `startForeground()` illegally when backgrounded with no foreground exemption, catching `ForegroundServiceStartNotAllowedException` at the direct `startForeground` / notification update boundary **before** it escapes to `Looper.loop()`.

---

### Task 2.4 — Fix Premature `stopForeground(STOP_FOREGROUND_REMOVE)` Race in `MusicService.onStartCommand`
**Current Facts:**
- `MusicService.kt` (`lines 422-426, 849-913, 1074-1188`) sets `temporaryForegroundStartedInOnCreate = true` unconditionally on API 26+ and at the end of `onStartCommand` (`lines 1179-1186`) checks `mediaSession?.player?.hasForegroundPlaybackIntent() != true`, immediately calling `stopForeground(STOP_FOREGROUND_REMOVE)` + `stopSelfResult(startId)` even while `restorePlaybackQueueSnapshotIfNeeded()` or `onSetMediaItems` (`serviceScope.future` on `Dispatchers.IO`) or `FAVORITE` coroutine is still asynchronously preparing playback.

**Requirements:**
1. Track an explicit atomic / Main-thread counter or state for in-flight async startup/playback-preparation jobs (`pendingStartupPlaybackJobs` or `isStartingAsyncPlayback`), incremented before launching `onSetMediaItems` / `restorePlaybackQueueSnapshotIfNeeded()` / media-button intent resolution and decremented in `finally`.
2. In `onStartCommand`: do NOT immediately execute `stopForeground(STOP_FOREGROUND_REMOVE)` / `stopSelfResult(startId)` while `pendingAsyncStartupCount > 0`. Wait until the in-flight async job completes, and ONLY then re-evaluate `hasForegroundPlaybackIntent()` (while also satisfying Android's requirement that `startForeground()` has already been called cleanly if started via `startForegroundService()`). Do NOT break valid self-stop when the service was legitimately started with no playback intent and nothing is pending.

---

### Task 2.5 — Register `CUSTOM_COMMAND_CANCEL_COUNTED_PLAY` in `availableSessionCommands`
**Current Facts:**
- `MusicService.kt` (`line 649`) handles `MusicNotificationProvider.CUSTOM_COMMAND_CANCEL_COUNTED_PLAY` in `onCustomCommand`, while `onConnect` (`lines 580-593`) omits it from `sessionCommandsBuilder`.

**Requirements:**
1. Add `.add(SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_CANCEL_COUNTED_PLAY, Bundle.EMPTY))` to `sessionCommandsBuilder` in `onConnect` (`MusicService.kt:580-593`).
2. Audit `onCustomCommand` and `onConnect` together to verify no other handled custom command is missing from `availableSessionCommands`.

---

### Task 2.6 — Flush `WidgetUpdateManager` Synchronously Before `stopSelf()` in `stopPlaybackAndUnload()`
**Current Facts:**
- `MusicService.kt` (`lines 2526-2529`): `stopPlaybackAndUnload()` calls `widgetUpdateManager.requestFullUpdate(true)` and immediately calls `stopSelf()`.
- `WidgetUpdateManager.requestFullUpdate(force = true)` (`lines 59-68`) launches on `serviceScope` with `delay(250L)` (`FORCED_DEBOUNCE_MS = 250L`). `MusicService.onDestroy()` (`line 1521`) cancels `serviceScope` before the 250ms debounce elapses, leaving homescreen widgets stuck in "Playing" state.

**Requirements:**
1. Add `suspend fun flushImmediateUpdate()` (or non-debounced synchronous/awaited flush) in `WidgetUpdateManager.kt` that cancels pending `debouncedUpdateJob`/`followUpUpdateJob` and executes `processUpdateInternal()` immediately with `0L` delay.
2. In `MusicService.stopPlaybackAndUnload()` (and before `stopSelf()` teardown), trigger `serviceScope.launch` that awaits `widgetUpdateManager.flushImmediateUpdate()` (with a short `withTimeoutOrNull`) **before** calling `stopSelf()`, OR trigger `stopSelf()` in `finally` right after the immediate widget/Wear flush completes cleanly (guarding against re-entrant playback start during the flush so `stopSelf()` isn't called if playback resumed!).

---

### Task 2.8 — Propagate `ListenableFuture` Cancellation in `CoroutineScope.future`
**Current Facts:**
- `MusicService.kt` (`lines 2906-2916`): custom `CoroutineScope.future` extension launches a `Job` and wires `SettableFuture`, without attaching a cancellation listener to `future`.

**Requirements:**
1. Register `future.addListener({ if (future.isCancelled) job.cancel() }, MoreExecutors.directExecutor())` inside `CoroutineScope.future`.
2. Ensure if `job` is cancelled, `future.cancel(false)` is called cleanly without masking real exceptions via `setException(e)`.

---

### Verification (Wave 1)
- Add/update JUnit unit tests covering:
  - `SleepTimerReceiver` behavior when service is inactive/paused (zero `startService` calls in background)
  - `WidgetUpdateManager` immediate flush vs debounced update
  - `CoroutineScope.future` cancels underlying coroutine `Job` on `future.cancel(true)`
- Run:
  `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.theveloper.pixelplay.data.service.*"`
```

---

## Đợt 2 — `DualPlayerEngine`, `TransitionController` & Crossfade Swap Safety (`2.7`, `2.9`, `2.10`, `2.11`, `2.12`, `2.13`)

```text
You are a Senior Android Media3 / ExoPlayer Concurrency Engineer working on PixelPlayer (`c:\Users\lacdu\Downloads\PixelPlayer-master`).
Read `AGENTS.md` first, then implement **Wave 2 (`2.7`, `2.9`, `2.10`, `2.11`, `2.12`, `2.13`)**.
Read the referenced line ranges before editing. Do NOT invent fields or APIs.

### Target Files
- `app/src/main/java/com/theveloper/pixelplay/data/service/player/DualPlayerEngine.kt`
- `app/src/main/java/com/theveloper/pixelplay/data/service/player/TransitionController.kt`
- `app/src/main/java/com/theveloper/pixelplay/data/service/MusicService.kt` (`countedPlayListener` & swap wiring around `lines 324-354, 2875-2905`)

---

### Task 2.7 — Prevent `countedPlayListener` Leak Across Crossfade Swaps (`MusicService.kt`)
**Current Facts:**
- `MusicService.kt` (`lines 2880-2895`) attaches `countedPlayListener` to `val player = engine.masterPlayer`, then after Crossfade (`DualPlayerEngine.performOverlapTransition` swaps `playerA` and `playerB`), `stopCountedPlay()` calls `engine.masterPlayer.removeListener(it)` on the NEW player — leaking the listener on the old `playerA`.

**Requirements:**
1. Store `private var countedPlayAttachedPlayer: Player? = null` alongside `countedPlayListener` in `MusicService.kt`.
2. In `stopCountedPlay()`: remove `countedPlayListener` from `countedPlayAttachedPlayer` (and also defensively `engine.masterPlayer` if different) and clear `countedPlayAttachedPlayer = null`.
3. In `playerSwapListener` / `onPlayerAboutToBeReleasedListener` (`DualPlayerEngine` -> `MusicService`): when `masterPlayer` swaps during an active counted-play loop (`countedOriginalId != null`) or when a player is about to be released, migrate `countedPlayListener` cleanly from the old player to the new `masterPlayer` (or stop counted play safely without leaving orphaned listeners).

---

### Task 2.9 & 2.10 — Thread-Safe URI Cache, Single-Flight Coalescing & Cache-Miss Resolution (`DualPlayerEngine.kt`)
**Current Facts & Reality Check:**
- `DualPlayerEngine.kt` (`lines 11, 762`) currently uses `android.util.LruCache<String, Uri>(100)` (which is `synchronized` internally in Android SDK, unlike raw `LinkedHashMap`), NOT `androidx.collection.LruCache`.
- However:
  1. `resolveDataSpec` (`lines 1100-1112`) on ExoPlayer's loader thread only reads `resolvedUriCache.get(originalUri)`; on ANY cache miss (random seek, shuffle jump, or playing before the `600ms` delay in `triggerAdjacentPreResolution()` at `lines 745-746` finishes), it returns the custom scheme URI (`telegram://`, `netease://`, `qqmusic://`, `navidrome://`, `jellyfin://`, `gdrive://`) causing `DefaultDataSource` to fail with `PlaybackException`.
  2. There is no single-flight coalescing if `preResolutionJob` and `resolveDataSpec` resolve the same URI concurrently, and no per-entry expiration for time-limited cloud proxy URLs if cached upstream URLs rotate.

**Requirements:**
1. Upgrade `resolvedUriCache` to a thread-safe, bounded cache (keep `android.util.LruCache` / bounded `ConcurrentHashMap` with LRU eviction AND optional expiry or proxy-port invalidation check — e.g. if a cached `http://127.0.0.1:<port>/...` points to an old port after proxy restart, treat it as stale miss!).
2. Add per-URI single-flight `CompletableDeferred<Uri?>` in-flight map inside `resolveCloudUri(uri)` so concurrent callers (pre-resolution + player prepare) share a single async resolution call cleanly (`try/finally` cleanup).
3. In `ResolvingDataSource.Resolver.resolveDataSpec`:
   - Keep fast-path synchronous cache hit (`resolvedUriCache.get(...)`).
   - On cache MISS for `scheme in CLOUD_PROXY_SCHEMES`: instead of returning the unplayable `telegram://` / `netease://` / etc. custom URI directly, run `resolveCloudUri(uri)` synchronously on the ExoPlayer background loader thread via bounded `runBlocking(Dispatchers.IO) { withTimeoutOrNull(8_000L) { resolveCloudUri(uri) } }` (guard cleanly with `Looper.myLooper() != Looper.getMainLooper()` assertion/check so loader thread resolves cleanly on cache miss!).
   - Also keep `triggerAdjacentPreResolution()` (`line 745`): reduce or differentiate debounce so immediate upcoming track (`currentIndex + 1`) warms up immediately or quickly while rapid-scrub debounces only redundant neighbor warmups.

---

### Task 2.11 — Preserve Full Queue (>200 Songs) Across `rebuildPlayersPreservingMasterState` & Windowed Crossfades (`DualPlayerEngine.kt`)
**Current Facts & Reality Check:**
- `performOverlapTransition` (`lines 1446-1447`) already updates `activeWindowStartIndex = preparedWindowStartIndex` and `activePlayerUsesWindowedQueue = preparedPlayerUsesWindowedQueue`.
- **HOWEVER**, `rebuildPlayersPreservingMasterState` (`lines 943-978`, called by `setHiFiMode` & offload fallback/toggle) STILL reads `playerA.mediaItemCount` (`lines 946-948`) instead of `ensureQueueSnapshot()` / `getCurrentAbsoluteIndex()`, and resets/re-sets `playerA` with only the 200-item window (`MAX_AUXILIARY_TIMELINE_ITEMS = 200`)!
- Also verify `cancelNext()` (`line 1353`) and `MusicService.playerSwapListener`: ensure when `playerA` holds a windowed queue (`activePlayerUsesWindowedQueue == true`), `MediaSession` / queue accessors via `engine.getFullQueue()` & `getCurrentAbsoluteIndex()` stay intact and `rebuildPlayersPreservingMasterState` restores the **full** `queueSnapshot` at `getCurrentAbsoluteIndex()` (`activeWindowStartIndex = 0`, `activePlayerUsesWindowedQueue = false`) rather than silently permanently dropping all tracks outside the 200-item window!

**Requirements:**
1. In `rebuildPlayersPreservingMasterState` (`DualPlayerEngine.kt:940-987`):
   - Snapshot full items via `ensureQueueSnapshot()` BEFORE releasing `playerA` and compute `absoluteIndex = getCurrentAbsoluteIndex()`.
   - Restore the FULL `snapshot` onto the rebuilt `playerA` at `absoluteIndex`, `positionMs`, and set `activeWindowStartIndex = 0`, `activePlayerUsesWindowedQueue = false`, `resetPreparedWindowState()`.
2. Verify all callers of `setMediaItems`, `addMediaItems`, `removeMediaItem`, `moveMediaItem`, or `seekToDefaultPosition(windowIndex)` when `activePlayerUsesWindowedQueue == true` either map window indices consistently or expand cleanly back to full `queueSnapshot` when queue > 200.

---

### Task 2.12 — Eliminate Listener Registration Race in `DualPlayerEngine.awaitPlayerReady`
**Current Facts:**
- `DualPlayerEngine.kt` (`lines 1547-1563`) checks `player.playbackState` BEFORE entering `suspendCancellableCoroutine` (or registers `addListener` without re-checking `player.playbackState` immediately afterwards), so if `STATE_READY`/`STATE_ENDED` fires in the micro-window in between, `onPlaybackStateChanged` never fires and it stalls for the full `3000ms` timeout.

**Requirements:**
1. Re-write `awaitPlayerReady` using `withTimeoutOrNull(timeoutMs)` + `suspendCancellableCoroutine` executed on `Dispatchers.Main.immediate` (since ExoPlayer listeners run on Main Looper):
   - Register `listener` first via `player.addListener(listener)`.
   - Attach `continuation.invokeOnCancellation { player.removeListener(listener) }`.
   - **Immediately after** `player.addListener(listener)`, re-check `val state = player.playbackState` and resume right away (`if (state == Player.STATE_READY) resume(true)`, `if (state == Player.STATE_ENDED || state == Player.STATE_IDLE) resume(false)`) after removing `listener` and guarding double-resume (`continuation.isActive`).

---

### Task 2.13 — Fix Permanent `scope.cancel()` on `@Singleton TransitionController`
**Current Facts:**
- `TransitionController.kt` (`lines 35-41, 341-349`) injects `@Singleton`, allocates `private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())` once, and `release()` (`line 348`) calls `scope.cancel()`. When `MusicService` restarts after `onDestroy()`, all subsequent `scope.launch` calls inside `TransitionController.initialize()` silently no-op forever!

**Requirements:**
1. Replace `scope.cancel()` in `TransitionController.release()` (`line 348`) with `scope.coroutineContext.cancelChildren()` (cancelling active monitoring/transition jobs without killing the singleton `SupervisorJob`), AND defensively verify `ensureActiveScope()` inside `initialize()` that recreates `scope` if `!scope.isActive` (matching `DualPlayerEngine.initialize()`).
2. Cleanly detach the `Listener` stored on `player` inside `release()` and reset state flags (`transitionTriggeredForCurrentSong = false`, etc.).

---

### Verification (Wave 2)
- Add unit tests for:
  - `TransitionController` re-initialization after `release()` runs new transitions cleanly
  - `DualPlayerEngine` full-queue window preservation & `awaitPlayerReady` already-ready fast path
- Run:
  `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.theveloper.pixelplay.data.service.player.*" --tests "com.theveloper.pixelplay.utils.QueueUtilsTest"`
```

---

## Đợt 3 — Audio DSP, ReplayGain / Opus RFC 7845 & `EqualizerManager` (`2.14`, `2.15`, `2.16`, `2.17`, `2.18`, `2.25`)

```text
You are a Senior Android Audio DSP & MediaCodec/AudioEffect Engineer working on PixelPlayer (`c:\Users\lacdu\Downloads\PixelPlayer-master`).
Read `AGENTS.md` first, then implement **Wave 3 (`2.14`, `2.15`, `2.16`, `2.17`, `2.18`, `2.25`)**.
Read the exact target files completely before modifying them.

> [!CAUTION]
> **CODEBASE REALITY CHECK FOR `2.14` AND `2.17`:**
> - `ReplayGainProcessor.kt` (`lines 1-274`) currently operates on the **Main Thread adjusting `player.volume`** (there are NO `targetGainDb`, `currentGainDb`, or `currentGainLinear` audio-thread DSP fields). Do NOT invent those non-existent fields! Focus on fixing the real bugs present in `ReplayGainProcessor.kt` and `ReplayGainManager.kt` below.
> - `HiResSampleRateCapAudioProcessor.kt` (`lines 1-271`) currently implements **integer-factor decimation** (`downsampleFactor = ceil(sampleRate / 192000)`, `outputSampleRate = input / downsampleFactor`, passing all 4 tests in `HiResSampleRateCapAudioProcessorTest.kt`). `resampleLinear`, `targetSampleRateCapHz`, and `previousFrameSamples` do NOT exist. Fix the real remaining DSP defects described in Task `2.17` below while preserving existing test contracts!

### Target Files
- `app/src/main/java/com/theveloper/pixelplay/data/service/ReplayGainProcessor.kt`
- `app/src/main/java/com/theveloper/pixelplay/data/media/ReplayGainManager.kt`
- `app/src/main/java/com/theveloper/pixelplay/data/media/AudioMetadataReader.kt`
- `app/src/main/java/com/theveloper/pixelplay/data/service/player/HiResSampleRateCapAudioProcessor.kt`
- `app/src/main/java/com/theveloper/pixelplay/data/service/player/SurroundDownmixProcessor.kt`
- `app/src/main/java/com/theveloper/pixelplay/data/equalizer/EqualizerManager.kt`
- Tests:
  - `app/src/test/java/com/theveloper/pixelplay/data/service/player/HiResSampleRateCapAudioProcessorTest.kt`
  - `app/src/test/java/com/theveloper/pixelplay/data/service/player/SurroundDownmixProcessorTest.kt`
  - Add unit tests for `ReplayGainManager` (R128 Q7.8 + negative cache) and `AudioMetadataReader`

---

### Task 2.14 — Harden `ReplayGainProcessor` Volume Sync, Positive-Gain Headroom & Concurrency
**Current Facts (`ReplayGainProcessor.kt:1-274`, `ReplayGainManager.kt:113-117`):**
- `ReplayGainManager.gainDbToVolume(...)` returns `linear.coerceIn(0f, 2f)` (allowing up to +6dB for quiet tracks), whereas `ReplayGainProcessor.setPlayerVolume(...)` clamps `player.volume` to `[0f, 1f]` — silently clipping positive ReplayGain boosts, while rapid track transitions / `prepareForTransition` vs `onTransitionFinished` / external volume changes can leave stale `pendingVolume` or request-token races.

**Requirements:**
1. Audit `ReplayGainProcessor` concurrency & clamping contract carefully:
   - Synchronize `ReplayGainManager.gainDbToVolume(...)` / `ReplayGainProcessor` effective ceiling cleanly: Android `Player.volume` is `[0f, 1f]` (cannot amplify > 1.0 natively without an `AudioProcessor` gain stage or `LoudnessEnhancer`, and setting `player.volume` > 1.0 throws or is clamped by ExoPlayer!). Either explicitly normalize/document positive gain against `preAmpDb` / headroom or implement clean gain scaling in a lightweight thread-safe `AudioProcessor` / `LoudnessEnhancer` hook ONLY if desired — OR keep `player.volume` clamping consistent with `expectedVolume` so `expectedVolume` vs `player.volume` drift check never misinterprets an intentional clamp as a user volume change! Look closely at `ReplayGainProcessor`: when `applyReplayGainForTrack` sets `targetVolume = (userSelectedVolume * replayGainMultiplier).coerceIn(0f, 1f)`, verify `expectedVolume` matches `player.volume` identically AND handle stale `requestToken` / `pendingVolume` across `prepareForTransition()` -> `onTransitionFinished()`.
2. Dispatch all `applyReplayGain` / `pendingVolume` state mutations atomically on Main thread (`Dispatchers.Main.immediate` or `@MainThread` enforcement) so IO tag extraction never races with `player.volume` updates.

---

### Task 2.15 — Fix RFC 7845 Q7.8 Opus `R128_TRACK_GAIN` / `R128_ALBUM_GAIN` Parsing (`ReplayGainManager.kt`, `AudioMetadataReader.kt`)
**Current Facts:**
- `ReplayGainManager.kt` (`lines 34-44, 143-160`) and `AudioMetadataReader.kt` (`lines 104-111, 242-264`) pass `R128_TRACK_GAIN` and `R128_ALBUM_GAIN` strings into `parseGainString` / `parseReplayGainDb` directly as dB floats.
- Per **RFC 7845 Section 5.2**, `R128_*_GAIN` stores signed integer Q7.8 fixed-point values in `1/256 dB` units referenced to `-23 LUFS` (vs ReplayGain `-18 LUFS`). E.g. `"-2304"` means `-2304 / 256.0 = -9.0 dB` relative to `-23 LUFS` (`= -4.0 dB` relative to ReplayGain `-18 LUFS` when adding the standard `+5.0 dB` offset). Parsing `"-2304"` as `-2304.0f dB` yields `10^(-2304/20) ≈ 0f` (complete silence)!

**Requirements:**
1. Create ONE shared parser helper (reused by BOTH `ReplayGainManager.kt` and `AudioMetadataReader.kt` — do not duplicate!) that accepts `(tagKey: String, rawValue: String): Float?`:
   - For `R128_TRACK_GAIN` / `R128_ALBUM_GAIN` (case-insensitive `key.startsWith("R128_")`): parse integer/float Q7.8 value -> `rawQ78 / 256.0f` (+ apply `+5.0f` LUFS reference offset if standard ReplayGain alignment is desired; document explicitly in KDoc and tests!).
   - For `REPLAYGAIN_TRACK_GAIN` / `REPLAYGAIN_ALBUM_GAIN` (`*_DB`): parse standard dB float stripping optional `"dB"` suffix cleanly.
   - Ignore NaN/infinite values (`isFinite()`).
2. In `ReplayGainManager.extractGainValue` (`lines 143-149`): fix premature `return parseGainString(rawValue)` inside the loop so if a higher-priority tag key has an unparseable/empty value, it `continue`s to try subsequent keys instead of aborting early!

---

### Task 2.16 — Fix Non-Functional Negative Cache in `ReplayGainManager.kt`
**Current Facts:**
- `ReplayGainManager.kt` (`lines 26-28, 73, 85, 93`): `cache` is `LinkedHashMap<String, ReplayGainValues?>`, stores `cache[filePath] = null` on files without ReplayGain tags, then at line `73` writes `synchronized(cache) { cache[filePath] }?.let { return it }`. Because cached `null` skips `?.let`, every playback re-opens the file and invokes JNI `TagLib.getMetadata`!

**Requirements:**
1. Wrap cached entries in a private sentinel/wrapper class `private data class CacheEntry(val values: ReplayGainValues?, val lastModifiedMs: Long)` (so both positive & negative hits return a non-null `CacheEntry`, AND stale entries automatically invalidate if the file's `lastModified()` changes after a metadata edit!).
2. Provide an explicit `fun invalidateCache(filePath: String)` method (can also be called if metadata tags are updated) and verify both `getCachedReplayGain` and `readReplayGain` respect negative cache hits properly.

---

### Task 2.17 — Flush Trailing Frames & Improve Anti-Aliasing Filter in `HiResSampleRateCapAudioProcessor.kt`
**Current Facts (`HiResSampleRateCapAudioProcessor.kt:1-271`, `HiResSampleRateCapAudioProcessorTest.kt:1-113`):**
- Already uses integer decimation (`downsampleFactor`, `outputSampleRate = inputSampleRate / downsampleFactor`, avoiding pitch shift).
- Remaining real defects:
  1. `queueEndOfStream()` (`lines 251-254`) sets `pendingSize = 0` and resets accumulator, silently dropping up to `downsampleFactor - 1` trailing samples on EOS instead of outputting the final averaged partial frame.
  2. Boxcar averaging (`sum / downsampleFactor`) has poor stopband aliasing attenuation (~-13 dB).

**Requirements:**
1. Update `queueEndOfStream()` to flush a final averaged output frame when `pendingSize > 0` before closing EOS (`accumulator[ch] / pendingSize`), OR pad cleanly across the filter tap tail without losing the tail samples.
2. Upgrade decimation filtering (e.g. symmetric windowed-sinc / Hann/Blackman low-pass FIR kernel scaled to cutoff `0.45f / downsampleFactor`, keeping integer decimation factor `downsampleFactor` intact so exact sample rate ratio and zero pitch shift remain guaranteed!).
3. Update / expand `HiResSampleRateCapAudioProcessorTest.kt` to cover trailing-sample flush on `queueEndOfStream()`, high-frequency attenuation / stopband aliasing behavior, AND verify all existing 4 tests continue passing.

---

### Task 2.18 — Fix Partial-Frame Misalignment & Normalized Mix Gains in `SurroundDownmixProcessor.kt`
**Current Facts (`SurroundDownmixProcessor.kt:33, 36, 95, 113, 116, 134, 174-207`):**
- `frameCount = inputBuffer.remaining() / bytesPerFrame` leaves up to `bytesPerFrame - 1` trailing bytes unread, then `inputBuffer.position(inputBuffer.limit())` drops those partial-frame bytes completely — misaligning channels/samples on the next buffer (audible static/white noise bursts!).
- Unnormalized gains: 5.1 sum `1.0 + 0.707 + 0.707 + 0.707 = 3.121` (`COEFF_SURROUND = 0.707f`, `COEFF_LFE = 0.707f`) and 7.1 sum `3.828` before hard clamping (`coerceIn`), causing severe clipping distortion.

**Requirements:**
1. Fix byte-boundary consumption: consume ONLY `frameCount * bytesPerFrame` bytes (`inputBuffer.position(inputBuffer.position() + frameCount * bytesPerFrame)`) so Media3 `DefaultAudioSink` re-presents unconsumed trailing partial-frame bytes cleanly in the next `queueInput(inputBuffer)` call (or carry a tiny `remainderBuffer` across calls + flush/reset cleanly on `onFlush`/`onReset`).
2. Apply proper ITU-R BS.775-based normalized downmix coefficients (with headroom normalization dividing each output channel's coefficients by their L1 sum <= 1.0, e.g., optional/subdued LFE coefficient <= `0.5f` or `0.707f` divided by total gain `normFactor`, so full-scale multi-channel peaks never clip `[-1f, 1f]` / `[Short.MIN_VALUE, Short.MAX_VALUE]`), keeping 16-bit and float PCM paths using the shared coefficient table cleanly.
3. Update & expand `SurroundDownmixProcessorTest.kt` to assert:
   - Unaligned input buffer trailing bytes are NOT dropped (`inputBuffer.remaining()` preserves remainder OR yields exact total frames when split across odd byte chunks)
   - Full-scale 5.1 / 7.1 peaks do not clip/saturate unexpectedly.

---

### Task 2.25 — Serialize `EqualizerManager.attachToAudioSession` with `Mutex` & Make Failure Handling Session-Scoped
**Current Facts (`EqualizerManager.kt:86, 144-335`):**
- `attachToAudioSessionInternal()` is a `suspend fun` with `delay(300)` retry loops (`lines 263, 293`) and **no `Mutex`**, allowing overlapping `attachToAudioSession` calls to interleave, overwrite, and leak native `Equalizer`/`BassBoost`/`Virtualizer`/`LoudnessEnhancer` handles.
- If `Equalizer(0, audioSessionId)` throws once (`line 213`, e.g. audio offload session or transitional Bluetooth route), `effectsDisabledForProcess = true` permanently bricks the entire Equalizer stack for the lifetime of the app process!
- Retrying `BassBoost` + `Virtualizer` (`3 * 300ms` each) sequentially delays Equalizer band setup by up to `1200ms`.

**Requirements:**
1. Add `private val attachMutex = Mutex()` guarding all mutations/attach/release across `attachToAudioSession(...)` and `release()` (`suspend` attach with `attachMutex.withLock { ... }`), checking latest target `audioSessionId` after lock acquisition so superseded session requests skip cleanly.
2. Replace `effectsDisabledForProcess = true` permanent kill-switch with **session/route-scoped fallback** (e.g., track `failedSessionIds` / temporary backoff per session ID, or retry automatically whenever a NEW `audioSessionId != lastFailedSessionId` arrives, and restore user preference enabled flags cleanly rather than mutating `_isEnabled.value = false` permanently across route changes!).
3. Avoid holding up `Equalizer` / `LoudnessEnhancer` band setup behind blocking `delay(300)` `BassBoost`/`Virtualizer` retries (apply Equalizer bands first, or run optional effect probes cleanly without delaying primary EQ activation).

---

### Verification (Wave 3)
- Run:
  `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.theveloper.pixelplay.data.service.player.HiResSampleRateCapAudioProcessorTest" --tests "com.theveloper.pixelplay.data.service.player.SurroundDownmixProcessorTest" --tests "com.theveloper.pixelplay.data.media.*"`
```

---

## Đợt 4 — Google Cast Stack: `CastPlayer` & `MediaFileHttpServerService` (`2.19`, `2.20`)

```text
You are a Senior Android Media & Embedded Ktor Server Engineer working on PixelPlayer (`c:\Users\lacdu\Downloads\PixelPlayer-master`).
Read `AGENTS.md` first, then implement **Wave 4 (`2.19`, `2.20`)**.
Read the targeted sections of both large files carefully before making changes.

### Target Files
- `app/src/main/java/com/theveloper/pixelplay/data/service/player/CastPlayer.kt` (~1,130 lines)
- `app/src/main/java/com/theveloper/pixelplay/data/service/http/MediaFileHttpServerService.kt` (~2,939 lines)
- Tests: `CastSessionSecurityTest.kt`, `CastRemotePlaybackStateTest.kt`

---

### Task 2.19 — Replace Unmanaged Raw Threads & Unbounded Static Caches in `CastPlayer.kt`
**Current Facts & Verified Symbols (`CastPlayer.kt:43-45, 265-467`):**
- `CastPlayer.Companion` (`lines 43-45`) declares 3 static unbounded `ConcurrentHashMap<String, String>` caches (`extractorMimeCache`, `retrieverMimeCache`, `signatureMimeCache`) that grow indefinitely across the process lifetime.
- `loadQueue()` (`lines 265-467`) spawns a new raw unmanaged `Thread { ... }.start()` on every call with zero cancellation or generation tracking when rapid queue loads overlap — racing multiple threads into `client.queueLoad(...)`.

**Requirements:**
1. Replace raw `Thread { ... }.start()` in `loadQueue()` with a managed coroutine `CoroutineScope(SupervisorJob() + Dispatchers.IO)` (plus `release()` / `cancelActiveLoad()` on Cast session end!) and an `activeLoadJob: Job?` + monotonic `loadGeneration: AtomicLong`:
   - Calling `loadQueue()` increments `loadGeneration`, cancels the previous `activeLoadJob` + pending timeout runnable, and abandons any stale probe/callback cleanly if `generation != loadGeneration.get()`.
2. Replace the 3 unbounded static `ConcurrentHashMap` caches (`extractorMimeCache`, `retrieverMimeCache`, `signatureMimeCache`) with bounded thread-safe LRU caches (`android.util.LruCache<String, String>(512)` or `Collections.synchronizedMap` bounded `LinkedHashMap` — keep max size e.g. `500` entries), plus expose `clearProbeCaches()` if needed.

---

### Task 2.20 — Fix Cache Concurrency, Transcode Race, ADTS Channel Mismatch & CIO Event-Loop Blocking in `MediaFileHttpServerService.kt`
**Current Facts & Verified Bugs (`MediaFileHttpServerService.kt`):**
1. **Unsynchronized unbounded caches (`lines 108-110, 1443-1454, 1788-1862`):** `signatureMimeCache` (`mutableMapOf`) and `codecInfoCache` (`mutableMapOf`) are plain unsynchronized `LinkedHashMap`s mutated concurrently across Ktor CIO worker coroutines and without size bounds.
2. **Concurrent transcode overwrite race (`lines 1939-1945`):** `val entry = TranscodeEntry(tempFile = tempFile); transcodeCache[songId] = entry` uses check-then-put rather than atomic `putIfAbsent` / per-`songId` single-flight synchronization! Two concurrent Cast HTTP `GET` requests for the same uncached `songId` overwrite `transcodeCache[songId]` and both launch FFmpeg/MediaCodec writing simultaneously into the SAME `cast_transcode_${songId}.aac` file!
3. **ADTS header wrong channel count (`lines 2648, 2784`):** Inside `transcodeToAacAdtsViaFfmpeg` (which downmixes multichannel PCM to `encChannels = minOf(codecInfo.channelCount, 2)`), lines `2598` & `2615` pass `encChannels`, **HOWEVER** the final tail drain at `line 2648` AND `queueEncoderEndOfStream` (`line 2784`) erroneously pass `codecInfo.channelCount` (e.g. 6 channels for 5.1 FLAC/ALAC instead of `encChannels = 2`) into `drainEncoderToAdts` -> `writeAdtsHeader(...)`! Also verify `writeAdtsHeader` handling when `sampleRate > 96000` (if Hi-Res >96kHz track is transcoded, AAC-LC ADTS `freqIdx` falls back to `4` = 44100 Hz while audio plays at >96kHz unless encoder/resampler aligns!).
4. **Blocking I/O on Ktor CIO dispatcher (`lines 394-660, 1885-2042`):** Ktor route handlers (`GET /song/{songId}`, `HEAD /song/{songId}`, artwork route) execute `resolveSongForServing(...)`, `detectAudioCodecViaExtractor(...)`, `resolvePreferredAudioMimeType(...)`, `resolveAudioStreamSource(...)`, and `streamFromGrowingFile(...)` (`fis.read` + `delay`) synchronously on Ktor's `CIO` dispatcher unless explicitly wrapped in `withContext(Dispatchers.IO)`.

**Requirements:**
1. Replace `signatureMimeCache` and `codecInfoCache` with thread-safe bounded LRU caches (e.g., synchronized bounded `LinkedHashMap` with max capacity 512 + `CachedProbeValue` wrapper or `android.util.LruCache` with sentinel wrapper so `null` misses still negative-cache safely!).
2. Fix `respondTranscodedWithCache` concurrency:
   - Synchronize `transcodeCache` entry creation per `songId` (`transcodeCache.compute(...)` or `synchronized(transcodeStartLock)` / `putIfAbsent` loop) so at most **one** background transcode job writes `cast_transcode_${songId}.aac` per `songId`, and any concurrent request cleanly attaches to the winning `TranscodeEntry` via `streamFromGrowingFile` or `respondWhenTranscodeCompletes`.
   - Ensure `streamFromGrowingFile` runs its `FileInputStream.read` loop explicitly on `Dispatchers.IO` (`withContext(Dispatchers.IO)` / IO blocking reads).
3. Fix ADTS header bugs:
   - Pass `encChannels` (`minOf(codecInfo.channelCount, 2)`) into ALL calls to `drainEncoderToAdts` and `queueEncoderEndOfStream` (`lines 2648, 2784`!).
   - Cap or align target AAC encoder sample rate to supported ADTS sampling frequencies (<= 48000 / 96000 Hz or explicit resampling check if > 96kHz) so `encFormat` `KEY_SAMPLE_RATE` and `writeAdtsHeader` `freqIdx` NEVER mismatch!
4. Wrap blocking DB / ContentResolver / MediaExtractor / file operations inside `GET` and `HEAD` Ktor route handlers in `withContext(Dispatchers.IO)`.

---

### Verification (Wave 4)
- Run:
  `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.theveloper.pixelplay.data.service.cast.*" --tests "com.theveloper.pixelplay.data.service.http.*"`
```

---

## Đợt 5 — Stream Proxies, SSRF Hardening & Telegram Streaming/Cache (`2.21`, `2.22`)

```text
You are a Senior Android Network Security & Streaming Systems Engineer working on PixelPlayer (`c:\Users\lacdu\Downloads\PixelPlayer-master`).
Read `AGENTS.md` first, then implement **Wave 5 (`2.21`, `2.22`)**.
Read every file completely before modifying.

### Target Files
- `app/src/main/java/com/theveloper/pixelplay/data/stream/CloudStreamSecurity.kt`
- `app/src/main/java/com/theveloper/pixelplay/data/stream/CloudStreamProxy.kt`
- `app/src/main/java/com/theveloper/pixelplay/data/gdrive/GDriveStreamProxy.kt`
- `app/src/main/java/com/theveloper/pixelplay/data/telegram/TelegramStreamProxy.kt`
- `app/src/main/java/com/theveloper/pixelplay/data/telegram/TelegramCacheManager.kt`
- `app/src/main/java/com/theveloper/pixelplay/data/telegram/TelegramRepository.kt`
- `app/src/main/java/com/theveloper/pixelplay/di/AppModule.kt` (ONLY if providing dedicated no-redirect/SSRF-validated OkHttpClient or check how proxy OkHttpClient handles redirects)
- Test: `app/src/test/java/com/theveloper/pixelplay/data/stream/CloudStreamSecurityTest.kt`

---

### Task 2.21 — Thread-Safety, `HEAD` Support, Cache Invalidation & SSRF Redirect Defense in Stream Proxies
**Current Facts (`CloudStreamProxy.kt`, `GDriveStreamProxy.kt`, `TelegramStreamProxy.kt`, `CloudStreamSecurity.kt`):**
1. `server`, `actualPort`, and `startJob` across `CloudStreamProxy`, `GDriveStreamProxy`, and `TelegramStreamProxy` lack `@Volatile` and synchronization (`start()` blindly calls `startJob?.cancel()` creating race conditions & leaked Ktor CIO server instances/ports on rapid concurrent `ensureReady()`/`startIfNeeded()` calls).
2. All 3 proxies only define `get(...)` routes and reject/404 `head(...)` requests used by ExoPlayer/clients probing content length/ranges.
3. `CloudStreamProxy.kt` (`lines 163-167, 219`): when `upstream.code` returns `401` or `403`, `urlCache[id]` retains the expired/rejected URL until TTL (`15-30m`) expires instead of invalidating + optionally refreshing once!
4. `CloudStreamSecurity.kt` (`lines 128-156, 171-240`):
   - `isSafeRemoteStreamUrl` allows ANY host + private IPs + `.local` whenever URL path merely contains `"stream.view"` (`pathSegments.contains("stream.view")`) or `"Audio"` + `"universal"` — without verifying `allowedHostSuffixes` or checking `isLocalOrPrivateHost` cleanly, AND fails to call `isCgnatIpv4Literal` / `isPrivateIpv6Literal` on non-self-hosted URLs!
   - `OkHttpClient` (`AppModule.kt:460`) follows HTTP `301`/`302`/`307`/`308` redirects automatically (`followRedirects = true` by default), letting a remote HTTPS server open-redirect an internal proxy into `http://127.0.0.1:...` or RFC1918 LAN endpoints!
   - Extract shared server start/stop/HEAD helpers or extend `CloudStreamProxy` so `GDriveStreamProxy` / `TelegramStreamProxy` don't duplicate Ktor lifecycle boilerplate.

**Requirements:**
1. **Shared Single-Flight Server Lifecycle:** Add `@Volatile` on `server`, `actualPort` (and `startMutex: Mutex` or synchronized single-flight `CompletableDeferred`/`Job` reuse so `ensureReady()` awaits the *in-flight* start job cleanly rather than polling `delay(50L)` or cancelling another caller's `start()`). Clean up leaked server on cancellation.
2. **`HEAD` Route Support:** Support `head(...)` alongside `get(...)` in `CloudStreamProxy`, `GDriveStreamProxy`, and `TelegramStreamProxy` (returning `Content-Length`, `Accept-Ranges`, `Content-Range`, `Content-Type`, and proper status code without opening/streaming body bytes).
3. **`urlCache` 401/403 Invalidation (`CloudStreamProxy`):** On upstream `401` or `403`, remove `urlCache.remove(id)` and retry URL resolution + request **at most once** if a fresh URL is returned (different or re-signed) before returning `401`/`403`.
4. **Hardened SSRF Protection (`CloudStreamSecurity` + Proxy OkHttp Calls):**
   - Block `isPrivateIpv4Literal`, `isCgnatIpv4Literal`, `isPrivateIpv6Literal`, `FORBIDDEN_HOSTS`, `.local`/`.internal`/`.home.arpa`/single-label local hosts for public cloud proxies cleanly. Restrict self-hosted exceptions (`allowHttpForAllowedHosts` / private LAN IPs for Navidrome/Jellyfin) **strictly** to hosts matching the user's configured `allowedHostSuffixes` + exact path rules (never allow arbitrary private-IP SSRF just because `"stream.view"` appears in a query/path!).
   - Also validate DNS-resolved `InetAddress` IPs (`isSiteLocalAddress`, `isLoopbackAddress`, `isLinkLocalAddress`, `isAnyLocalAddress`, `isMulticastAddress`, IPv4 CGNAT `100.64.0.0/10`, IPv6 `fc00::/7`) AND disable automatic uncontrolled redirects on proxy requests (`client.newBuilder().followRedirects(false).followSslRedirects(false).build()` with manual bounded <=3 hop loop re-running `CloudStreamSecurity.isSafeRemoteStreamUrl` + DNS IP validation on every `Location` target!).
   - Expand `CloudStreamSecurityTest.kt` for IPv6 ULA/link-local (`fc00::/7`, `fe80::/10`), CGNAT (`100.64.0.0/10`), path-trick bypass (`https://evil.com/stream.view` vs configured server), and redirect target rejection.

---

### Task 2.22 — Fix Range-Seek Streaming, Infinite Stall Timeout, Active Playback Eviction & `downloadFileAwait` Event Race (Telegram)
**Current Facts (`TelegramStreamProxy.kt`, `TelegramCacheManager.kt`, `TelegramRepository.kt`):**
1. `TelegramStreamProxy.kt` (`line 67`): always calls `telegramRepository.downloadFile(fileId, 1)` (`offset = 0, limit = 0`), forcing download from byte 0 even when ExoPlayer requests `Range: bytes=<end_of_file>-`! (Verify Range header is parsed BEFORE `DownloadFile` so TDLib receives the requested `offset` when starting/prioritizing a seek! Also handle `offset = start` cleanly alongside `downloadedPrefixSize` / `TdApi.GetFileDownloadedPrefixSize(fileId, offset)` or offset-aware progress!)
2. `TelegramStreamProxy.kt` (`lines 204-252`): backoff exists (`50ms..400ms`), **HOWEVER** `while (true)` has NO cumulative stall timeout! If network drops or TDLib stalls silently while `canBeDownloaded == true`, the proxy coroutine hangs indefinitely! Add an explicit idle/stall timeout (e.g. `20_000L`–`30_000L` without byte progress -> break/abort cleanly so ExoPlayer receives an IO error/retry instead of hanging forever!).
3. `TelegramCacheManager.kt` (`lines 93-147`): protects only 1 single `activeFileId` + 5-item `audioFileHistory`. If the user rapidly skips through tracks or is mid-crossfade (playing `outgoingFileId` + `incomingFileId`), or if playback stops cleanly while paused, verify **all currently pinned/active/crossfading file IDs** are protected from `TdApi.DeleteFile` eviction (`activeFileIds: Set<Int>` / crossfade grace), and cancel eviction cleanly if that `fileId` becomes active again during the 2-second grace delay!
4. `TelegramRepository.kt` (`lines 631-725`):
   - `activeDownloads[fileId]` (`lines 637-640, 723`) has a non-atomic check-then-put TOCTOU race (`putIfAbsent` or lock needed; `try { ... } finally { activeDownloads.remove(fileId, ...) }`).
   - Line `678` sends `TdApi.DownloadFile(fileId, priority, 0, 0, false)` **BEFORE** subscribing to `clientManager.updates` (`line 685`, `SharedFlow` with `replay = 0`!), losing completion events for cached/instant completions until the 60-second `withTimeoutOrNull` expires! Subscribe/attach the collector (`updates.onSubscription { sendRequest(DownloadFile(...)) }` or collect in a coroutine started *before* `sendRequest`) and re-check `getFile(fileId)` right after subscription!

---

### Verification (Wave 5)
- Run:
  `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.theveloper.pixelplay.data.stream.*"`
```

---

## Đợt 6 — Netease/QQ Cloud URL Coalescing & `QQSignGenerator` WebView Hardening (`2.23`, `2.24`)

```text
You are a Senior Android Networking & WebView Security Engineer working on PixelPlayer (`c:\Users\lacdu\Downloads\PixelPlayer-master`).
Read `AGENTS.md` first, then implement **Wave 6 (`2.23`, `2.24`)**.
Read the referenced files completely before editing.

### Target Files
- `app/src/main/java/com/theveloper/pixelplay/data/netease/NeteaseRepository.kt` (`lines 475-545`)
- `app/src/main/java/com/theveloper/pixelplay/data/qqmusic/QqMusicRepository.kt` (`lines 162-225`)
- `app/src/main/java/com/theveloper/pixelplay/data/remote/qqmusic/QQSignGenerator.kt` (`lines 1-275`)
- Optional shared helper (e.g. `CloudSongUrlCoalescer` in `data/stream/` to avoid duplicating the identical cooldown + in-flight `CompletableDeferred` logic across `NeteaseRepository` and `QqMusicRepository`!)

---

### Task 2.23 — Fix Reversed Cooldown vs In-Flight Deduplication Order (`NeteaseRepository`, `QqMusicRepository`)
**Current Facts:**
- Both `NeteaseRepository.getSongUrl` (`lines 477-532`) and `QqMusicRepository.getSongUrl` (`lines 162-214`) check and overwrite `lastSongUrlAttemptAtMs[id] = now` (`songUrlRequestCooldownMs = 1500L`) **BEFORE** checking `inFlightSongUrlRequests[id]?.await()`.
- Consequently, when `warmUpStreamUrl` initiates URL resolution and ExoPlayer's proxy request arrives `<1500ms` later, the second caller immediately fails with `"request throttled"` (HTTP 404/502) instead of joining the active `CompletableDeferred`!
- Additionally, `inFlightSongUrlRequests.remove(id, requestDeferred)` is NOT wrapped in `try { ... } finally { ... }`, leaking stale entries permanently on coroutine `CancellationException`.

**Requirements:**
1. Extract a shared helper or implement identical corrected ordering in both repositories:
   - **Step 1:** Check `inFlightSongUrlRequests[key]` FIRST (`await()` if active) — coalescing concurrent callers regardless of cooldown!
   - **Step 2:** Enforce cooldown (`songUrlRequestCooldownMs`) cleanly ONLY for **new failed-retry attempts** when no in-flight request exists (note: successful resolution cached in `CloudStreamProxy.urlCache` won't hit this often, while rapid back-to-back *failures* respect cooldown without penalizing successful/in-flight joins). Update `lastAttemptAtMs` inside the winner path only!
   - **Step 3:** Atomic `putIfAbsent(key, requestDeferred)` race guard -> if `existing != null`, `return existing.await()`.
   - **Step 4:** Execute resolution inside `try { ... } finally { inFlightSongUrlRequests.remove(key, requestDeferred) }` (and complete `requestDeferred` with failure/cancellation in `finally` if still incomplete so waiting callers never hang forever!).

---

### Task 2.24 — Isolate `QQSignGenerator` WebView from `https://y.qq.com/`, Fix Concurrent Init Race & Lifecycle Leak
**Current Facts (`QQSignGenerator.kt:63-275`):**
- `ensureWebView()` calls `loadUrl("https://y.qq.com/")` with `domStorageEnabled = true` and `addJavascriptInterface(JsBridge(), "AndroidBridge")`, pulling arbitrary remote live web assets/ads/trackers into the privileged `AndroidBridge` origin and keeping the `WebView` allocated forever without `destroy()`.
- Calling `ensureWebView()` lacks mutual exclusion across threads/timeouts (`signLock` protects `generateSign` / `decryptResponseWithVm` / `encryptRequestWithVm`, yet `webView`, `webViewReady`, `encryptLatch`, `encryptResultRef` are fragile under timeouts/re-entry and leak Main-Thread `WebView` instances indefinitely).

**Requirements:**
1. **Eliminate live remote network load of `https://y.qq.com/`:**
   - Replace `loadUrl("https://y.qq.com/")` with `loadDataWithBaseURL("https://y.qq.com/", "<!DOCTYPE html><html><head></head><body></body></html>", "text/html", "UTF-8", null)` — preserving the `https://y.qq.com/` origin required by `vm_new.js` Web Crypto APIs cleanly with **zero external network traffic, zero ads, zero trackers**!
   - Disable unnecessary capabilities (`domStorageEnabled = false` if `qq_sign.js`/`vm_new.js` pass without DOM storage — verify JS files first! Keep `allowFileAccess = false`, `allowContentAccess = false`, block external URLs in `shouldOverrideUrlLoading` & `shouldInterceptRequest` returning empty `WebResourceResponse` for any external request!).
2. **Thread-Safe Single-Flight Init + Idle/Shutdown Cleanup:**
   - Guard WebView creation cleanly inside a single lock / Main-thread future so two callers/retries never instantiate leaked duplicate `WebView`s or overwrite each other's ready latch.
   - Add idle timeout auto-destroy (e.g. schedule runnable after 2–3 minutes of inactivity on Main handler, protected by `signLock`) AND a `destroy()` method (`webView.removeJavascriptInterface("AndroidBridge"); webView.destroy(); webView = null; webViewReady = false`) so the heavy Chromium renderer memory is reclaimed cleanly when idle!
   - Always reset/clear `encryptLatch` / `encryptResultRef` cleanly even on timeout.

---

### Verification (Wave 6)
- Add unit tests verifying:
  - Coalescing order in `NeteaseRepository` & `QqMusicRepository` (concurrent call 2 awaits call 1 inside 1500ms without failing with `"throttled"`; cancellation removes entry in `finally`).
- Run:
  `.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests "com.theveloper.pixelplay.data.netease.*" --tests "com.theveloper.pixelplay.data.qqmusic.*" --tests "com.theveloper.pixelplay.data.remote.*"`
```

---

## Đợt 7 — `SongMetadataEditor` DB Sync/FLAC Routing & Wear OS Transfer/Manifest/Migration (`2.26`, `2.27`)

```text
You are a Senior Android Storage, Room Database & Wear OS Engineer working on PixelPlayer (`c:\Users\lacdu\Downloads\PixelPlayer-master`).
Read `AGENTS.md` first, then implement **Wave 7 (`2.26`, `2.27`)**.
Read the targeted sections of every file before editing.

### Target Files
- `app/src/main/java/com/theveloper/pixelplay/data/media/SongMetadataEditor.kt`
- `app/src/main/java/com/theveloper/pixelplay/data/database/MusicDao.kt` (and `AlbumEntity` / `LyricsDao` as needed)
- `app/src/main/java/com/theveloper/pixelplay/data/database/TelegramSongEntity.kt` (read-only schema reference: `id = "chatId_messageId"`)
- `app/src/main/java/com/theveloper/pixelplay/data/service/wear/PhoneDirectWatchTransferCoordinator.kt`
- `wear/src/main/AndroidManifest.xml`
- `wear/src/main/java/com/theveloper/pixelplay/di/WearModule.kt`
- `wear/src/main/java/com/theveloper/pixelplay/data/local/WearMusicDatabase.kt`
- `wear/src/main/java/com/theveloper/pixelplay/data/WearTransferRepository.kt`

---

### Task 2.26 — Fix All 5 Bugs in `SongMetadataEditor.kt`
**Current Verified Facts (`SongMetadataEditor.kt`):**
1. **Telegram DAO wrong ID (`lines 494-507`):** Line `497` builds `val telegramId = "${songEntity.telegramChatId}_${songEntity.telegramFileId}"` (`chatId_fileId`), whereas `TelegramSongEntity.id` (`TelegramSongEntity.kt:23`) is **`"${chatId}_${messageId}"`**! (Parse `messageId` cleanly from `contentUriString` `telegram://{chatId}/{messageId}` or query `TelegramDao` by `chatId` + `fileId`!).
2. **Edited Lyrics not persisted to DB (`lines 525-535`, `MusicDao.kt:1729-1734`):** `editSongMetadata` writes `trimmedLyrics` to file tags (`TagLib`/`JAudioTagger`/`VorbisJava`) but NEVER updates Room (`songs.lyrics` via `musicDao.updateLyrics` / `resetLyrics`, AND sync/clear `LyricsDao` cache if `LyricsDao` caches per-song lyrics!).
3. **Album rename leaves `songs.album_id` & `albums` table stale (`MusicDao.kt:1658-1714`):** `updateSongMetadata` only updates the `album_name = :album` string column, leaving `songs.album_id` pointing at the old `AlbumEntity` and `albums` (`name`, `song_count`, orphaned albums) unsynchronized!
4. **High-Res FLAC with mismatched extension (`lines 310-343, 593-618`):** `isProblematicFlacFile(finalFilePath)` checks `filePath` extension (`if (extension != "flac") return NotFlac`) BEFORE routing by magic bytes (`detectedContainer == DetectedContainer.FLAC`), AND `val needsExtensionSwap` (`line 337`) is computed and unused! High-res FLAC disguised as `.mp3` bypasses `isHighResFlac` / `JAudioTagger` and hits TagLib (and when `needsExtensionSwap` occurs, writing raw FLAC bytes back into `.mp3` leaves broken extension or fails!).
5. **`CoverArtUpdate.equals/hashCode` omits `isDeletion` (`lines 1366-1388`):** `CoverArtUpdate(bytes = null, isDeletion = true) == CoverArtUpdate(bytes = null, isDeletion = false)` evaluates `true`!

**Requirements:**
1. Fix Telegram song lookup (`line 497`): resolve `TelegramSongEntity` via parsed `chatId_messageId` from `songEntity.contentUriString` (and fallback query on `TelegramDao` by `chatId` + `fileId` if URI lacks messageId), and update `TelegramDao` reliably.
2. Sync edited `trimmedLyrics` to Room inside the metadata update transaction (`if (trimmedLyrics.isNotEmpty()) musicDao.updateLyrics(songId, trimmedLyrics) else musicDao.resetLyrics(songId)` + inspect & update/invalidate `LyricsDao` if used for `songId`!).
3. Synchronize `albums` and `songs.album_id` atomically in `MusicDao`: resolve existing matching `AlbumEntity` (or create new album entry cleanly) when `album` changes, update `songs.album_id`, recompute/update affected `albums.song_count`, and prune orphaned empty albums cleanly (using existing `MusicDao` album helpers).
4. Route `isProblematicFlacFile(...)` by magic-byte `detectedContainer == DetectedContainer.FLAC` (removing the blind `extension != "flac"` early return when reading the `tempFile` or detected FLAC stream!), and ensure container-mismatch handling (`needsExtensionSwap`) either safely preserves/updates or prevents silently reporting `success = true` if writing failed.
5. Add `if (isDeletion != other.isDeletion) return false` to `CoverArtUpdate.equals` and include `isDeletion.hashCode()` in `CoverArtUpdate.hashCode`.

---

### Task 2.27 — Fix Wear OS OOM Cache, Missing Manifest Filter, Missing Room Migrations & Transfer Validation
**Current Verified Facts:**
1. **`PhoneDirectWatchTransferCoordinator.kt` (`lines 77-78, 495-536`):** `albumArtworkTransferCache = ConcurrentHashMap<Long, ByteArray>()` (up to 1.5MB/album) and `albumPaletteSeedCache = ConcurrentHashMap<Long, Int>()` are completely unbounded and never evicted.
2. **`wear/src/main/AndroidManifest.xml` (`lines 83-116`):** `WearDataListenerService` `MESSAGE_RECEIVED` `<intent-filter>` declares `/browse_response`, `/playback_result`, `/transfer_metadata`, `/transfer_progress`, `/transfer_request`, `/transfer_cancel`, `/watch_library_query`, `/volume_state`, **MISSING `<data android:scheme="wear" android:host="*" android:pathPrefix="/favorites_sync_state" />`** (even though `WearDataListenerService.kt:276` handles `WearDataPaths.FAVORITES_SYNC_STATE`)! Also verify if any other `WearDataPaths` path sent phone->watch (`/watch_library_state` vs phone paths) is missing.
3. **`wear/.../WearModule.kt` (`lines 44-49`):** `provideWearMusicDatabase` calls `Room.databaseBuilder(application, WearMusicDatabase::class.java, "wear_music.db").build()` WITHOUT registering `.addMigrations(WearMusicDatabase.MIGRATION_1_2, WearMusicDatabase.MIGRATION_2_3, WearMusicDatabase.MIGRATION_3_4, WearMusicDatabase.MIGRATION_4_5)` which already exist in `WearMusicDatabase.kt` (`lines 17-40`)!
4. **`wear/.../WearTransferRepository.kt` (`lines 91-104, 589-618`):**
   - `localSongs` (`lines 91-104`) deletes files and mutates `localSongDao.deleteById(...)` inside cold `.transform { ... }` on every Flow emission/collector (causing Room invalidation feedback loops and multi-collector races!).
   - Transfer completion check (`line 589-598`) only checks `if (actualSize == 0L)` — accepting truncated partial transfers where `actualSize < resolvedMetadata.fileSize` (when `resolvedMetadata.fileSize > 0L`) as valid complete transfers!

**Requirements:**
1. Replace `albumArtworkTransferCache` (and `albumPaletteSeedCache`) in `PhoneDirectWatchTransferCoordinator.kt` with bounded `android.util.LruCache` (e.g. byte-size-bounded `LruCache<Long, ByteArray>` capped at e.g. `12 * 1024 * 1024` bytes overriding `sizeOf`, plus bounded count LRU for `albumPaletteSeedCache`).
2. Add `<data android:scheme="wear" android:host="*" android:pathPrefix="/favorites_sync_state" />` (and verify any other phone->watch paths handled in `WearDataListenerService`) to `wear/src/main/AndroidManifest.xml`.
3. Update `WearModule.provideWearMusicDatabase` to register `.addMigrations(WearMusicDatabase.MIGRATION_1_2, WearMusicDatabase.MIGRATION_2_3, WearMusicDatabase.MIGRATION_3_4, WearMusicDatabase.MIGRATION_4_5)` + `.fallbackToDestructiveMigrationOnDowngrade()`.
4. Fix `WearTransferRepository.kt`:
   - Enforce `if (resolvedMetadata.fileSize > 0L && actualSize != resolvedMetadata.fileSize)` cleanly (delete `tempFile`, report `handleTransferError(..., "Incomplete transfer: expected ${resolvedMetadata.fileSize} bytes, got $actualSize bytes")`, and abort without committing a truncated `LocalSongEntity`!).
   - Move stale-entry cleanup OUT of `localSongs.transform` into a dedicated startup/one-shot synchronized `pruneStaleLocalSongs()` suspend function (plus optional `distinctUntilChanged` pure filter in `localSongs` without DB writes inside `transform`!).

---

### Verification (Wave 7)
- Add unit tests for:
  - `CoverArtUpdate.equals`/`hashCode` distinguishing `isDeletion`
  - `WearTransferRepository` partial file rejection (`actualSize < expectedSize`) & non-mutating `localSongs` flow
  - `WearMusicDatabase` migrations `1 -> 5` cleanly registered in `WearModule`
- Run BOTH app and wear targets:
  `.\gradlew.bat :app:compileDebugKotlin :wear:compileDebugKotlin :app:testDebugUnitTest --tests "com.theveloper.pixelplay.data.database.*" --tests "com.theveloper.pixelplay.data.media.*"`
  `.\gradlew.bat :wear:testDebugUnitTest`
```
