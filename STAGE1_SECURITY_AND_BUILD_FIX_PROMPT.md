# PixelPlayer — Stage 1 Security, Build & Architecture Remediation Prompt (for Gemini 3.8 Flash)

> **Target Model:** Gemini 3.8 Flash  
> **Scope:** Remediate all 8 Stage 1 findings (`1.1` – `1.8`) cleanly with zero functional regressions to Media3 playback, Android Auto / Wear artwork sharing, Glance widgets, Cloud streams, `.pxpl` backup/restore, or Compose UI reactivity.  
> **Project Conventions:** Strictly follow `AGENTS.md` (especially Compose phase isolation, modifier order, no hardcoded secrets/strings, no duplicated crypto/prefs logic).

---

## Global Rules & Execution Order for Gemini 3.8 Flash

1. **Read before editing:** Read every target file in full (or the exact surrounding functions) before modifying it. Do not invent imports, method names, or constructors.
2. **Reuse shared infrastructure (NO copy-paste crypto/prefs helpers):** Build shared security utilities once under `app/src/main/java/com/theveloper/pixelplay/data/security/` (or similar) and reuse them across `1.4`, `1.5`, and `1.6`.
3. **Preserve existing authenticated sessions safely where possible:**
   - Do **not** switch away from `EncryptedSharedPreferences` file names for Netease/QQMusic/Jellyfin/Navidrome/GDrive in a way that silently logs out existing users.
   - Migrate existing plaintext AI API keys (`1.4`) and legacy unencrypted TDLib databases (`1.5`) seamlessly before deleting legacy plaintext data.
4. **Never add silent plaintext or debug-signing fallbacks:** Security checks must **fail closed** (refuse to build release, clear/disable persistence, or require re-authentication), never downgrade silently.
5. **Recommended Execution Order:**
   1. Shared security primitives (`1.6` + `1.4` + `1.5` foundation)
   2. Task `1.7` (delete dead/empty files)
   3. Task `1.1` (network security config & HTTPS alignment)
   4. Task `1.2` (Gradle secrets, release signing guard, release lint)
   5. Task `1.3` (`SharedArtworkContentProvider` access control & async pipe)
   6. Task `1.4` (encrypted AI key store & `.pxpl` backup sanitization)
   7. Task `1.5` (TDLib DB encryption + migration + Android backup XML rules)
   8. Task `1.6` (fail-closed prefs across all 5 cloud repos, interceptor redaction, `AppModule` logging/DNS fix)
   9. Task `1.8` (`compose_stability.conf` & real `@Immutable` models)
   10. Final Build, Lint & Unit Test Verification

---

## Master Prompt (Copy-Paste to Gemini 3.8 Flash)

```text
You are a Senior Android Security, Build Systems, and Jetpack Compose Engineer working on PixelPlayer (`c:\Users\lacdu\Downloads\PixelPlayer-master`).
Read `AGENTS.md` first, then implement the 8 Stage 1 fixes (`1.1` through `1.8`) below in order.
For EVERY task:
1. Read the exact files listed before editing.
2. Avoid duplicating logic—extract shared helpers when multiple files need the same behavior.
3. Add or update JUnit unit tests for every security/data fix (`1.3`, `1.4`, `1.5`, `1.6`, `1.7`).
4. Run all verification commands at the end and report only directly observed results.

---

### TASK 1.1 — Disable Global Cleartext HTTP in Release (`network_security_config.xml`, `AndroidManifest.xml`, Cloud/Stream Audit)

**Target Files:**
- `app/src/main/res/xml/network_security_config.xml`
- `app/src/main/AndroidManifest.xml`
- `wear/src/main/AndroidManifest.xml`
- Check callers: `app/src/main/java/com/theveloper/pixelplay/data/stream/CloudStreamProxy.kt`, `app/src/main/java/com/theveloper/pixelplay/data/netease/NeteaseRepository.kt`, `app/src/main/java/com/theveloper/pixelplay/data/qqmusic/QqMusicRepository.kt`, `app/src/main/java/com/theveloper/pixelplay/data/jellyfin/model/JellyfinCredentials.kt`, `app/src/main/java/com/theveloper/pixelplay/data/navidrome/model/NavidromeCredentials.kt`

**Requirements:**
1. Update `app/src/main/res/xml/network_security_config.xml`:
   - Change `<base-config cleartextTrafficPermitted="true">` to `<base-config cleartextTrafficPermitted="false">` (keep trusting `system` CAs only; note `app/src/debug/res/xml/network_security_config.xml` already handles debug builds).
   - Keep the `<domain-config cleartextTrafficPermitted="true">` block restricted strictly to local loopback addresses (`127.0.0.1`, `localhost`, and optionally `[::1]`/`::1` if valid; verify Android XML domain-config syntax only accepts hostnames/IPs without brackets for domain elements) so `CloudStreamProxy`, `GDriveStreamProxy`, and `TelegramStreamProxy` local HTTP servers continue working.
2. Verify `app/src/main/AndroidManifest.xml` still declares both `android:usesCleartextTraffic="false"` and `android:networkSecurityConfig="@xml/network_security_config"`.
3. Add explicit `android:usesCleartextTraffic="false"` on `<application>` in `wear/src/main/AndroidManifest.xml` for defense-in-depth parity.
4. Check public CDN `http://` usage that would break once OS NetworkSecurityPolicy enforces `cleartextTrafficPermitted="false"`:
   - Inspect `NeteaseRepository.getSongUrl(...)`, `QqMusicRepository.getSongUrl(...)`, and `CloudStreamProxy.kt` (`line 202` passes `allowHttpForAllowedHosts = true`).
   - In `NeteaseRepository` and `QqMusicRepository`, upgrade resolved `http://` CDN URLs for Netease/QQ domains to `https://` when building playback URLs (or verify whether `https://` is already returned and tighten `CloudStreamProxy` `allowHttpForAllowedHosts = false` unless an upstream test specifically verifies `CloudStreamSecurity.isSafeRemoteStreamUrl`). Do NOT remove existing `CloudStreamSecurityTest` test coverage; keep `CloudStreamSecurity` utility intact and add/update tests if proxy default behavior changes.
   - Note explicitly in your summary any intentional impact on LAN `http://` Jellyfin/Navidrome (`isLocalOrPrivateHost`) or LAN Cast artwork (`CastSyncCoordinator`), because Android XML `network_security_config` cannot express RFC1918 IP CIDR wildcards when `<base-config cleartextTrafficPermitted="false">` is enforced.

---

### TASK 1.2 — Remove Hardcoded Secrets & Debug-Signing Fallback, Re-enable Release Lint (`app/build.gradle.kts`, `wear/build.gradle.kts`, CI Workflows)

**Target Files:**
- `app/build.gradle.kts`
- `wear/build.gradle.kts`
- `app/src/main/java/com/theveloper/pixelplay/data/telegram/TelegramClientManager.kt`
- `.github/workflows/phone-release.yml`
- `.github/workflows/nightly-apk.yml`

**Requirements:**
1. **Remove hardcoded Telegram fallback secrets (`app/build.gradle.kts`):**
   - Delete `"2040"` and `"b18441a1ff607e10a989891a5462e627"`.
   - Resolve `TELEGRAM_API_ID` and `TELEGRAM_API_HASH` from (in order): `localProperties`, `providers.gradleProperty(...)`, `providers.environmentVariable(...)`.
   - For debug/non-release compilation when unset: emit `buildConfigField("int", "TELEGRAM_API_ID", "0")` and `buildConfigField("String", "TELEGRAM_API_HASH", "\"\"")` so local debug builds and unit tests work out of the box without secrets.
   - Guard release tasks (check via `gradle.taskGraph.whenReady` or a registered `validateReleaseConfig` pre-task attached only to release bundle/assemble tasks, NEVER breaking `assembleDebug`, `test*`, `lintDebug`, or IDE Gradle sync): throw `GradleException` if `TELEGRAM_API_ID` parses to `<= 0` or `TELEGRAM_API_HASH` is blank.
   - In `TelegramClientManager.kt` (before sending `TdApi.SetTdlibParameters`), verify `BuildConfig.TELEGRAM_API_ID > 0 && BuildConfig.TELEGRAM_API_HASH.isNotBlank()`; if unconfigured, log a non-secret error and surface an explicit unconfigured/error state rather than initializing TDLib with `0` / `""`.
2. **Remove `"dummyPassword"`, `"dummyAlias"`, and debug-signing fallback (`app/build.gradle.kts` & `wear/build.gradle.kts`):**
   - Load release keystore path (`keystore.properties` `"storeFile"` or `$rootDir/vz-pixelplay.jks`), `storePassword`, `keyAlias`, `keyPassword` from `keystore.properties` and/or environment variables (`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).
   - Do **NOT** supply dummy default strings and do **NOT** assign `signingConfigs.getByName("debug")` to `buildTypes.release`.
   - Only call `signingConfigs.create("release")` and assign `signingConfig = signingConfigs.getByName("release")` when `keystoreFile.exists()` and all 3 signing strings are non-blank.
   - In `gradle.taskGraph.whenReady` (when any release packaging task like `assembleRelease` or `bundleRelease` is requested for `:app`), throw `GradleException` if release `signingConfig` is missing/invalid. Check `wear/build.gradle.kts` `buildTypes.release` as well so it never falls back to debug signing when a release build is requested.
3. **Wire Telegram secrets in CI workflows (`.github/workflows/phone-release.yml`, `.github/workflows/nightly-apk.yml`):**
   - Pass `TELEGRAM_API_ID: ${{ secrets.TELEGRAM_API_ID }}` and `TELEGRAM_API_HASH: ${{ secrets.TELEGRAM_API_HASH }}` into the release build step `env:` so signed CI releases receive credentials from GitHub Secrets. (Check `nightly-apk.yml` if fork PRs run without secrets—release APKs should only run on push/tag/workflow_dispatch where secrets exist.)
4. **Re-enable Release Lint (`app/build.gradle.kts`, `wear/build.gradle.kts`):**
   - Remove `checkReleaseBuilds = false` from `app/build.gradle.kts` (`lint { ... }`) and remove `abortOnError = false` + `checkReleaseBuilds = false` from `wear/build.gradle.kts`.
   - Configure `lint { abortOnError = true; checkReleaseBuilds = true }` (or omit `checkReleaseBuilds` since `true` is default).
   - Run `:app:lintVitalRelease :wear:lintVitalRelease` (and/or `lintDebug`) and fix any genuine bugs/security findings reported by Lint rather than suppressing them.

---

### TASK 1.3 — Lock Down `SharedArtworkContentProvider` & Eliminate Binder Thread Blocking (`AndroidManifest.xml`, `SharedArtworkContentProvider.kt`, `MusicService.kt`)

**Target Files:**
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/theveloper/pixelplay/data/provider/SharedArtworkContentProvider.kt`
- `app/src/main/java/com/theveloper/pixelplay/data/service/MusicService.kt` (inspect `grantArtworkUriPermissions` and caller validation in `onConnect` / browse callbacks)
- `app/src/test/java/com/theveloper/pixelplay/data/provider/SharedArtworkContentProviderTest.kt`

**Requirements:**
1. **Close public export (`AndroidManifest.xml`):**
   - On `SharedArtworkContentProvider`, change `android:exported="true"` to `android:exported="false"` while keeping `android:grantUriPermissions="true"`.
   - Verify why this preserves legitimate sharing: `MusicService.grantArtworkUriPermissions(...)` already grants per-URI read access (`Intent.FLAG_GRANT_READ_URI_PERMISSION`) to connected external MediaSession controllers (Android Auto, System UI), and Glance widgets load artwork bytes directly inside `MusicService` (`loadArtworkBytesForWidget`), not via ungranted external ContentProvider queries.
   - Audit `MusicService.kt` callers of `grantArtworkUriPermissions` to confirm grants are only issued to connected/authorized MediaSession controllers (respecting `TrustedMediaItemsResolution.kt`).
2. **Defense-in-depth caller check & MIME type (`SharedArtworkContentProvider.kt`):**
   - In `openFile(uri, mode)`:
     - Keep `if (mode != "r") throw FileNotFoundException(...)`.
     - Parse & validate positive `songId` (`> 0L`) via `parseSongId(uri, appContext.packageName)`.
     - Verify caller authorization even in code: allow if `Binder.getCallingUid() == Process.myUid()` OR `appContext.checkCallingUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED` (throw `SecurityException` otherwise).
   - Implement `getType(uri)`: return `"image/jpeg"` when `parseSongId(...) != null`, otherwise `null`.
3. **Eliminate synchronous/blocking Binder work in `openFile()`:**
   - Currently `openFile()` invokes `resolveArtworkFile(uri)` -> `AlbumArtUtils.ensureAlbumArtCachedFile(...)` (MediaStore queries, TagLib/JAudioTagger metadata extraction, Bitmap decoding/JPEG recompression) directly on the Binder IPC thread.
   - Replace with **non-blocking pipe streaming** using `ParcelFileDescriptor.createReliablePipe()`:
     - Return `pipe[0]` (read end) **immediately** on the Binder thread without waiting on disk/TagLib/Bitmap I/O.
     - Dispatch the artwork resolution + streaming onto a **bounded background pool** (`CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(4))`), wrapped in a timeout (e.g., `withTimeoutOrNull(8_000L)`), with **single-flight deduplication per `songId`** (e.g., `ConcurrentHashMap<Long, Deferred<File?>>` or `Mutex` map) so burst requests (e.g., Android Auto fetching 30 items at once) do not trigger redundant concurrent TagLib decodes for the same song.
     - Copy bytes from the cached file into `ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])` and call `pipe[1].closeWithError("...")` if resolution returns null, times out, or throws, so callers fail cleanly instead of hanging.
     - Override `shutdown()` on `ContentProvider` to cancel the provider's `CoroutineScope`.
4. Add/update unit tests in `SharedArtworkContentProviderTest.kt` covering URI parsing, invalid/negative songId rejection, read-only mode validation, and unauthorized caller checks (using Robolectric or Mockito/mock Context if needed).

---

### TASK 1.4 — Encrypt AI API Keys & Exclude Secrets/Device Paths from `.pxpl` Backups (`AiPreferencesRepository.kt`, `UserPreferencesRepository.kt`, `GlobalSettingsModuleHandler.kt`)

**Target Files:**
- Create shared crypto helper: `app/src/main/java/com/theveloper/pixelplay/data/security/KeystoreSecretStore.kt` (or `SecretStorage` interface + `AndroidKeystoreSecretStore` `@Singleton`)
- `app/src/main/java/com/theveloper/pixelplay/data/preferences/AiPreferencesRepository.kt`
- `app/src/main/java/com/theveloper/pixelplay/data/preferences/UserPreferencesRepository.kt`
- `app/src/main/java/com/theveloper/pixelplay/data/backup/module/GlobalSettingsModuleHandler.kt`
- `app/src/main/java/com/theveloper/pixelplay/data/backup/BackupHistoryRepository.kt` (verify `backup_history_json` key constant)
- `app/src/main/res/xml/backup_rules.xml` & `app/src/main/res/xml/data_extraction_rules.xml`
- Tests: `app/src/test/java/com/theveloper/pixelplay/data/preferences/UserPreferencesRepositoryTest.kt` + new tests for `AiPreferencesRepository` and `GlobalSettingsModuleHandler`

**Requirements:**
1. **Build a shared Android Keystore AES-256-GCM secret store (`KeystoreSecretStore`):**
   - Uses `java.security.KeyStore.getInstance("AndroidKeyStore")` + `AES/GCM/NoPadding` (`SecretKey` generated with `KeyGenParameterSpec`, 256-bit key, randomized 12-byte IV prepended to ciphertext, persisted Base64-encoded inside a dedicated private `SharedPreferences` file `"pixelplay_ai_secrets_enc"` — **completely separate** from `"settings"` DataStore).
   - Never fall back to storing secrets unencrypted. If decryption fails permanently (e.g., Keystore invalidated after OS restore), log a warning without secret contents, clear only the un-decryptable entry, and return `""`.
   - Extract an interface (`SecretStore`) so unit tests can inject an in-memory fake cleanly.
2. **Isolate & migrate all 11+ AI API keys (`AiPreferencesRepository.kt`):**
   - Keep all public Flow/suspend method signatures in `AiPreferencesRepository` backward-compatible (`getApiKey(provider)`, `setApiKey(provider, apiKey)`, `geminiApiKey`, ..., `hasAnyApiKey()`, etc.) so ViewModels/clients require minimal or zero signature changes.
   - Store all `AiProvider.entries` API keys inside the encrypted `SecretStore` (with a `MutableStateFlow<Map<AiProvider, String>>` backing reactive flows).
   - Implement an idempotent suspend migration (`migratePlaintextApiKeysIfNeeded()` guarded by a `Mutex`), triggered automatically during repo initialization / before reads/writes:
     - For every `provider` in `AiProvider.entries`, check legacy key `Keys.getApiKey(provider)` (`"${provider.name.lowercase()}_api_key"`) in `context.dataStore` (`"settings"`).
     - If present and non-blank, persist into encrypted `SecretStore` (if not already set there).
     - Remove **every** `${provider.name.lowercase()}_api_key` key from `context.dataStore` via `dataStore.edit { ... }`.
3. **Harden `.pxpl` Export & Restore (`UserPreferencesRepository.kt` & `GlobalSettingsModuleHandler.kt`):**
   - Define a shared, canonical backup-exclusion check (e.g., `UserPreferencesRepository.isExcludedFromBackup(keyName: String): Boolean`) covering:
     - `PreferencesKeys.INITIAL_SETUP_DONE.name`
     - `"backup_history_json"` (from `BackupHistoryRepository`)
     - Any AI API key (`keyName.endsWith("_api_key")` + explicit `AiProvider.entries.map { "${it.name.lowercase()}_api_key" }`)
     - Local offline GGUF model state tied to device filesystem paths: `active_offline_model_path`, `active_offline_model_name`, `active_offline_model_id` (check `AiPreferencesRepository.Keys` for any other `offline_*` local path keys and exclude local path keys too!)
     - Any credential/secret key suffix (`_secret`, `_token`, `_password`, `_cookie`).
   - Update `exportPreferencesForBackup()` to skip every key matching `isExcludedFromBackup(key.name)`.
   - Update `restorePreferencesFromBackup(prefs)` to **reject/skip** any incoming key matching `isExcludedFromBackup(keyName)` (prevents malicious/legacy `.pxpl` files from injecting API keys or overwriting local backup history / model paths).
   - Update `GlobalSettingsModuleHandler.EXCLUDED_KEYS` and `UserPreferencesRepository.clearPreferencesExceptKeys(excludedKeys)`: ensure that restoring `GLOBAL_SETTINGS` preserves `PLAYLIST_KEYS + QUICK_FILL_KEYS + EQUALIZER_KEYS` **PLUS** `INITIAL_SETUP_DONE`, `"backup_history_json"`, all `*_api_key` keys, and `active_offline_model_*` keys (never wipe them during restore!).
4. **Exclude encrypted AI secret store from Android Backup:** Add `<exclude domain="sharedpref" path="pixelplay_ai_secrets_enc.xml"/>` to both `backup_rules.xml` and `data_extraction_rules.xml` (`cloud-backup` & `device-transfer`).
5. Add unit tests verifying:
   - Migration removes plaintext `*_api_key` entries from DataStore and stores them in `SecretStore`.
   - `exportPreferencesForBackup()` omits all 11 AI keys, `backup_history_json`, `active_offline_model_path`, and `initial_setup_done`.
   - `GlobalSettingsModuleHandler.restore(...)` preserves existing `initial_setup_done`, `backup_history_json`, `active_offline_model_path`, and does not import/overwrite API keys even if a legacy payload contains them.

---

### TASK 1.5 — Encrypt TDLib Database & Exclude TDLib State from Android Backups (`TelegramClientManager.kt`, `backup_rules.xml`, `data_extraction_rules.xml`)

**Target Files:**
- Create `@Singleton` `TelegramDatabaseKeyProvider` (reusing `KeystoreSecretStore` / `SecretStore` from Task `1.4`)
- `app/src/main/java/com/theveloper/pixelplay/data/telegram/TelegramClientManager.kt`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`
- Unit tests for `TelegramDatabaseKeyProvider` & key migration logic

**Requirements:**
1. **Generate & store a Keystore-protected 32-byte TDLib encryption key:**
   - Generate 32 cryptographically random bytes via `java.security.SecureRandom` once, persist via `SecretStore` (`AndroidKeyStore` AES-GCM backed `"telegram_db_key_enc"`), and zero out temporary copies when appropriate.
   - Track boolean marker `tdlib_db_encrypted` in the protected store.
   - Zero-fallback policy: NEVER pass `null` or an all-zero hardcoded key in production.
2. **Handle `TdApi.SetTdlibParameters` & Legacy Unencrypted DB Migration (`TelegramClientManager.kt`):**
   - Remember TDLib `com.github.tdlibx:td:1.8.56` signature: `TdApi.SetTdlibParameters(boolean useTestDc, String databaseDirectory, String filesDirectory, byte[] databaseEncryptionKey, ...)` and `TdApi.SetDatabaseEncryptionKey(byte[] newEncryptionKey)`.
   - Inside `AuthorizationStateWaitTdlibParameters`:
     - First check `BuildConfig.TELEGRAM_API_ID > 0 && BuildConfig.TELEGRAM_API_HASH.isNotBlank()` (from Task `1.2`).
     - Obtain `(encryptionKey, isDbAlreadyEncrypted)` from `TelegramDatabaseKeyProvider`.
     - Check whether legacy unencrypted `filesDir/tdlib` already exists and is non-empty while `!isDbAlreadyEncrypted`:
       - **Fresh DB (`tdlib` absent/empty) OR already encrypted (`isDbAlreadyEncrypted == true`):** mark encrypted (`markDatabaseEncrypted()`) when fresh, and pass `encryptionKey` directly to `TdApi.SetTdlibParameters(..., databaseEncryptionKey = encryptionKey, ...)`.
       - **Existing unencrypted legacy DB (`tdlib` non-empty and `!isDbAlreadyEncrypted`):** open with `byteArrayOf()` in `SetTdlibParameters`, and in the `SetTdlibParameters` completion handler (`TdApi.Ok`), **immediately send `TdApi.SetDatabaseEncryptionKey(encryptionKey)`** and call `markDatabaseEncrypted()` ONLY once `SetDatabaseEncryptionKey` returns `TdApi.Ok`! Next launch will pass `encryptionKey` directly in `SetTdlibParameters`.
3. **Exclude TDLib data & key store from Android Cloud Backup / Device Transfer (`backup_rules.xml` & `data_extraction_rules.xml`):**
   - Note `databaseDirectory = File(context.filesDir, "tdlib")` and `filesDirectory = File(context.filesDir, "tdlib_files")`.
   - In `backup_rules.xml` AND BOTH `<cloud-backup>` and `<device-transfer>` inside `data_extraction_rules.xml`, add exclusions for:
     - `domain="file"` `path="tdlib"` & `path="tdlib/"`
     - `domain="file"` `path="tdlib_files"` & `path="tdlib_files/"`
     - `domain="sharedpref"` for the encrypted key prefs file (e.g. `"pixelplay_ai_secrets_enc.xml"` / `"telegram_db_key_enc.xml"`).

---

### TASK 1.6 — Eliminate Plaintext Prefs Fallbacks Across All 5 Cloud Repos, Redact QQInterceptor Logs, & Fix `provideFastOkHttpClient` (`AppModule.kt`)

**Target Files:**
- Create shared factory: `app/src/main/java/com/theveloper/pixelplay/data/security/SecureSharedPreferencesFactory.kt`
- `app/src/main/java/com/theveloper/pixelplay/data/netease/NeteaseRepository.kt`
- `app/src/main/java/com/theveloper/pixelplay/data/qqmusic/QqMusicRepository.kt`
- `app/src/main/java/com/theveloper/pixelplay/data/jellyfin/JellyfinRepository.kt` *(sibling repo with exact same `*_prefs_plain` bug!)*
- `app/src/main/java/com/theveloper/pixelplay/data/navidrome/NavidromeRepository.kt` *(sibling repo with exact same `*_prefs_plain` bug!)*
- `app/src/main/java/com/theveloper/pixelplay/data/gdrive/GDriveRepository.kt` *(sibling repo with exact same `*_prefs_plain` bug!)*
- `app/src/main/java/com/theveloper/pixelplay/data/remote/qqmusic/QQMusicEncryptInterceptor.kt`
- `app/src/main/java/com/theveloper/pixelplay/di/AppModule.kt`

**Requirements:**
1. **Unify & Harden `EncryptedSharedPreferences` Creation (`SecureSharedPreferencesFactory.kt`):**
   - Replace the duplicated `try { EncryptedSharedPreferences.create(...) } catch { getSharedPreferences("*_prefs_plain") }` blocks in **all 5 repositories** (`NeteaseRepository`, `QqMusicRepository`, `JellyfinRepository`, `NavidromeRepository`, `GDriveRepository`) with a single shared `SecureSharedPreferencesFactory.create(context, prefsName)`.
   - Keep the existing `MasterKey` + `EncryptedSharedPreferences.create(context, prefsName, masterKey, AES256_SIV, AES256_GCM)` file names (`"netease_prefs"`, `"qqmusic_prefs"`, `"jellyfin_prefs"`, `"navidrome_prefs"`, `"gdrive_prefs"`) so existing valid encrypted sessions remain intact!
   - One-time cleanup on success: if legacy `"${prefsName}_plain"` exists on disk (`File(context.applicationInfo.dataDir, "shared_prefs/${prefsName}_plain.xml").exists()`), migrate any missing entries once into the encrypted prefs (if non-empty) and **immediately delete** `"${prefsName}_plain"` via `context.deleteSharedPreferences("${prefsName}_plain")`! (And remove `*_prefs_plain` creation completely.)
   - **Fail-closed error recovery (ZERO plaintext persistence):**
     - If `EncryptedSharedPreferences.create` throws due to corrupted prefs/Keystore AEAD mismatch: log a redacted warning, delete ONLY the corrupted `"$prefsName"` via `context.deleteSharedPreferences(prefsName)`, and retry `EncryptedSharedPreferences.create(...)` once.
     - If retry still throws (e.g., device Keystore unavailable/locked): return a **process-lifetime in-memory `SharedPreferences`** implementation (never writing anything to disk in plaintext!) or propagate safe logged-out state. NEVER open `*_prefs_plain`!
2. **Eliminate sensitive logs in `QQMusicEncryptInterceptor.kt` (and check `QqMusicRepository.kt`):**
   - In `QQMusicEncryptInterceptor.kt`:
     - **Delete** the log statements outputting `Plain request json: $plaintextJson`, `Generated sign: $sign`, and `Decrypted JSON content: $decryptedJson` (lines ~37, 41, 75) — never log full unencrypted payloads, cookies, or auth tokens even in debug builds!
     - Wrap remaining lightweight structural metadata logs (`Response code`, `Response body size`, `Decrypted JSON length`, `Parsed req_0 code=..., subcode=...`) strictly in `if (BuildConfig.DEBUG)` and log only sanitized URL path (no query string).
   - Check `QqMusicRepository.kt:142`: gate or trim raw API playlist JSON debug logs strictly behind `if (BuildConfig.DEBUG)` (no tokens/cookies logged).
3. **Fix `provideFastOkHttpClient()` in `AppModule.kt` (around lines 484–535):**
   - Extract a shared helper `buildLoggingInterceptor()` used by BOTH `provideOkHttpClient()` and `provideFastOkHttpClient()`:
     - Gate level strictly by `if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS else HttpLoggingInterceptor.Level.NONE` (even better: only `.addInterceptor(...)` inside `if (BuildConfig.DEBUG)` so Release incurs zero logging overhead!).
     - Always call `.redactHeader(...)` for `"Authorization"`, `"Cookie"`, `"Set-Cookie"`, `"X-Emby-Token"`, `"X-Emby-Authorization"`, `"X-API-Key"`, `"x-goog-api-key"`.
   - Remove the redundant `dns(object : Dns { ... InetAddress.getAllByName ... })` block (`lines 499-507`); use `Dns.SYSTEM` (or OkHttp default).
4. Add unit tests for `SecureSharedPreferencesFactory` (verifying fail-closed in-memory fallback / zero `*_prefs_plain` file creation when Keystore fails, plus cleanup migration of legacy `*_prefs_plain`) and `QQMusicEncryptInterceptor` (confirming decrypted JSON / sign / cookies are not logged).

---

### TASK 1.7 — Remove Dead Test File & Empty Stub From `src/main`

**Target Files (Delete):**
- `app/src/main/java/com/theveloper/pixelplay/data/model/SortOptionTest.kt`
- `app/src/main/java/com/theveloper/pixelplay/presentation/components/subcomps/PlayerProgressBarSection.kt`

**Requirements:**
1. Verify no source or resource references exist to `PlayerProgressBarSection.kt` or the `src/main` `SortOptionTest.kt` (the active tests are in `app/src/test/java/com/theveloper/pixelplay/data/model/SortOptionTest.kt` and the real composable is `private fun PlayerProgressBarSection` inside `FullPlayerContent.kt`).
2. Delete both files cleanly.

---

### TASK 1.8 — Fix Incorrect Compose Compiler Stability Configuration (`app/compose_stability.conf` & Model Annotations)

**Target Files:**
- `app/compose_stability.conf`
- `app/src/main/java/com/theveloper/pixelplay/MainActivity.kt` (`BottomNavItem`)
- `app/src/main/java/com/theveloper/pixelplay/data/model/Lyrics.kt`
- `app/src/main/java/com/theveloper/pixelplay/data/model/MusicFolder.kt`
- `app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/LyricsSearchUiState.kt`
- Inspect `SortOption.kt`, `FolderSource.kt`, `LyricsSourcePreference.kt`, `StorageFilter.kt`, `Transition.kt`, `PlaybackQueueSnapshot.kt`, `PlayerInfo.kt`

**Requirements:**
1. **Clean up `app/compose_stability.conf`:**
   - **REMOVE** `kotlinx.coroutines.flow.StateFlow`
   - **REMOVE** `kotlinx.coroutines.flow.MutableStateFlow`
   - **REMOVE** `com.google.common.util.concurrent.ListenableFuture`
   - **REMOVE** wildcard `com.theveloper.pixelplay.data.model.**`
   - **REMOVE** bogus/wrong-package entries (`com.theveloper.pixelplay.presentation.components.BottomNavItem`, `com.theveloper.pixelplay.presentation.viewmodel.PickResult`) and entries already annotated `@Immutable` directly in code (`PlayerUiState`, `StablePlayerState`, `Song`, `Album`, `Artist`, `Genre`). Keep `compose_stability.conf` empty (commented header explaining only verified third-party immutable types without `@Immutable` belong here) so Compose Compiler never receives false stability guarantees!
2. **Fix real Compose stability on the model & state classes themselves (without breaking `@Serializable` / `@Parcelize` / Room):**
   - `MainActivity.kt`: replace `import javax.annotation.concurrent.Immutable` with `import androidx.compose.runtime.Immutable` on `BottomNavItem` (`Screen` is already `@Immutable`).
   - `MusicFolder.kt`: annotate `MusicFolder` with `@Immutable` (all fields are `val` + `ImmutableList`; prefer plain getter properties `val totalSongCount: Int get() = ...` or keep thread-safe `@Immutable` only if verified with Compose compiler report—plain getter or eager `val` avoids `Lazy` delegate mutability!).
   - `LyricsSearchUiState.kt`: annotate `LyricsSearchUiState` and all subclasses with `@Immutable`, and change `PickResult.results` from `List<LyricsSearchResult>` to `ImmutableList<LyricsSearchResult>` (updating the `LyricsSheet` / `FetchLyricsDialog` / `LyricsStateHolder` callers to pass `.toImmutableList()`). Also check `LrcLibResponse` + `LyricsSearchResult` and annotate `@Immutable` if all fields become truly immutable.
   - `Lyrics.kt` (`Lyrics`, `SyncedLine`, `SyncedWord`) & `SortOption.kt` / `FolderSource.kt` / `LyricsSourcePreference.kt` / `StorageFilter.kt`:
     - Ensure `SortOption`, `FolderSource`, `LyricsSourcePreference`, `StorageFilter` (pure immutable enums / sealed objects with `val`) carry `@Immutable`.
     - For `Lyrics` (`@Serializable`): keep `val plain: List<String>?`, `val synced: List<SyncedLine>?`, `val words: List<SyncedWord>?` OR switch to `ImmutableList` cleanly ONLY if `@Serializable` still round-trips identically with all JSON caches/backups! If keeping `List` for `kotlinx.serialization` binary/JSON compatibility, either wrap lists with `.toImmutableList()` at parse/factory boundaries + annotate `@Immutable`, OR migrate `StablePlayerState.lyrics` / UI lyrics wrappers to `ImmutableList`. **Do NOT mark `PlayerInfo` (contains `ByteArray?` + `List<QueueItem>`) or `PlaybackQueueSnapshot` (Room/JSON snapshot) or `TransitionRule` as stable in `compose_stability.conf`.**
   - Verify Compose calls passing `StateFlow<Long>` / `StateFlow<StablePlayerState>` (`LyricsSheet.kt`, `MornyeLyricsMotion.kt`): confirm under Kotlin 2.4 / Compose Strong Skipping that `StateFlow` identity comparison still skips unnecessary outer recompositions (or keep `StateFlow` read strictly in leaf `collectAsStateWithLifecycle()` scopes), and ensure **no** Composable takes `MutableStateFlow` or `ListenableFuture` as a parameter.
```

---

## Final Verification Checklist (Run After Applying Fixes)

Execute these commands in order and confirm **zero errors/failures**:

```powershell
# 1. Verify debug build & all unit tests pass (including new security & stability tests)
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :wear:compileDebugKotlin

# 2. Verify specific new/updated unit tests
./gradlew :app:testDebugUnitTest --tests "com.theveloper.pixelplay.data.model.SortOptionTest" --tests "com.theveloper.pixelplay.data.provider.SharedArtworkContentProviderTest" --tests "com.theveloper.pixelplay.data.preferences.UserPreferencesRepositoryTest" --tests "com.theveloper.pixelplay.data.stream.CloudStreamSecurityTest"

# 3. Verify Release Lint (1.2) — must execute cleanly without checkReleaseBuilds = false
./gradlew :app:lintVitalRelease :wear:lintVitalRelease

# 4. Verify Release Signing & Telegram Guardrail fail-fast (1.2)
# Run WITHOUT keystore/TELEGRAM_API_ID and verify it FAILS with a clear GradleException (NOT silent debug signing!):
./gradlew :app:assembleRelease
```

> **Expected Behavior for Step 4:** Without `vz-pixelplay.jks` + `keystore.properties` (or env vars) and valid `TELEGRAM_API_ID`/`TELEGRAM_API_HASH`, `:app:assembleRelease` MUST fail with an explicit error—while `:app:assembleDebug` and `:app:testDebugUnitTest` MUST succeed cleanly.
