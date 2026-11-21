# PROMPT FOR GEMINI 3.8 FLASH — FULLY REMOVE NAVIDROME/SUBSONIC AND JELLYFIN FROM PIXELPLAYER

You are a senior Android/Kotlin engineer working on **PixelPlayer**. Your task is to remove the Navidrome/Subsonic and Jellyfin integrations completely: UI, playback hooks, sync, network layer, Room tables, resources, tests, and the data they left on users' devices.

This is a **removal**. Do not add features, do not refactor neighbouring code, and do not "improve" anything you pass through. Colour and string literals you see in files you edit are pre-existing; leave them alone.

Read this whole file before editing. Work phase by phase, in order. After each phase run the stated compile check and fix every error before moving on. Locate code by the quoted snippets; line numbers are hints only.

---

## 0. RELATION TO `CAST_HTTP_SERVER_SECURITY_PROMPT.md`

That prompt's Step 11 removes only the sign-in screens. This prompt supersedes it and works whether or not Step 11 has been applied:

- If a file this prompt tells you to delete is already gone, skip it.
- If a block is already in a reduced form (for example a `ProviderRow` wrapped in `if (isNavidromeLoggedIn) { ... }`, or a `NAVIDROME ->` branch that only calls the dashboard), delete that reduced form.

All paths below are relative to `app/src/main/java/com/theveloper/pixelplay/` unless they start with `app/` or `gradle/`.

---

## 1. HARD CONSTRAINTS

1. **Do not touch `gradle/libs.versions.toml`.** The line `androidx-media3-exoplayer-ffmpeg = { module = "org.jellyfin.media3:media3-ffmpeg-decoder", ... }` is the FFmpeg audio decoder used by the player and by Cast transcoding. It only shares a publisher name with Jellyfin. Removing it breaks playback.
2. **Do not edit any existing Room migration** (`MIGRATION_3_4` … `MIGRATION_41_42`, `recreateNavidromeSongsTable`, and their SQL). Users upgrading from old versions still run them. They are raw SQL and do not need the entity classes.
3. **Do not edit or delete existing schema files** `app/schemas/**/1.json` … `42.json`. Do not hand-write `43.json`; the build generates it.
4. Do not touch Telegram, Netease, QQ Music or Google Drive code, except the exact lines named below that sit next to Navidrome/Jellyfin lines.
5. Keep `CloudStreamSecurity.isLocalOrPrivateHost` and its tests in `CloudStreamSecurityTest.kt`.
6. Keep the `jellyfin_prefs*` / `navidrome_prefs*` `<exclude>` lines in `app/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml`. A user may upgrade and get a cloud backup before first launching the new version; those lines stop old credentials from being backed up.
7. Keep changelog text: `strings_changelogs.xml` in every locale, `values-ar/strings_presentation_batch_g.xml`, and `CHANGELOG.md` describe history and stay as they are.
8. Do not edit `app/src/release/generated/baselineProfiles/*`, anything under `app/build/`, the `:shared`, `:wear` or `:baselineprofile` modules.
9. Do not rename, reorder or renumber anything that stays (`SourceType` values `0..4`, remaining enum entries, DAO methods).
10. No new user-facing strings.

---

## 2. PHASE 1 — UI AND NAVIGATION

### 1.1 Delete these directories (every file in them)

```
presentation/navidrome/
presentation/jellyfin/
```

That is: `NavidromeLoginActivity`, `NavidromeLoginViewModel`, `NavidromeDashboardScreen`, `NavidromeDashboardViewModel`, `JellyfinLoginActivity`, `JellyfinLoginViewModel`, `JellyfinDashboardScreen`, `JellyfinDashboardViewModel`.

### 1.2 `app/src/main/AndroidManifest.xml`

Delete the two `<activity>` blocks whose `android:name` is `.presentation.navidrome.auth.NavidromeLoginActivity` and `.presentation.jellyfin.auth.JellyfinLoginActivity` (each is 6 lines, `<activity` through `/>`), plus the blank line after each.

### 1.3 `presentation/navigation/Screen.kt`

Delete:

```kotlin
    object NavidromeDashboard : Screen("navidrome_dashboard")
    object JellyfinDashboard : Screen("jellyfin_dashboard")
```

### 1.4 `presentation/navigation/AppNavigation.kt`

In the `AccountsScreen(...)` call, delete:

```kotlin
                        onOpenNavidromeDashboard = {
                            navController.navigateSafely(Screen.NavidromeDashboard.route)
                        },
                        onOpenJellyfinDashboard = {
                            navController.navigateSafely(Screen.JellyfinDashboard.route)
                        }
```

and remove the now-trailing comma after the `onOpenQqMusicDashboard = { ... }` argument.

Delete both `composable(...)` blocks for the dashboards:

```kotlin
            composable(
                Screen.NavidromeDashboard.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                    com.theveloper.pixelplay.presentation.navidrome.dashboard.NavidromeDashboardScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(
                Screen.JellyfinDashboard.route,
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel, animatedVisibilityScope = this) {
                    com.theveloper.pixelplay.presentation.jellyfin.dashboard.JellyfinDashboardScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
```

### 1.5 `presentation/screens/HomeScreen.kt`

Delete the imports:

```kotlin
import com.theveloper.pixelplay.presentation.jellyfin.dashboard.JellyfinDashboardViewModel
import com.theveloper.pixelplay.presentation.navidrome.dashboard.NavidromeDashboardViewModel
```

Inside `if (showStreamingProviderSheet) { ... }` delete:

```kotlin
        val navidromeViewModel: NavidromeDashboardViewModel = hiltViewModel()
        val jellyfinViewModel: JellyfinDashboardViewModel = hiltViewModel()
```

```kotlin
        val isNavidromeLoggedIn by navidromeViewModel.isLoggedIn.collectAsStateWithLifecycle()
        val isJellyfinLoggedIn by jellyfinViewModel.isLoggedIn.collectAsStateWithLifecycle()
```

and, in the `StreamingProviderSheet(...)` call:

```kotlin
            isNavidromeLoggedIn = isNavidromeLoggedIn,
            onNavigateToNavidromeDashboard = {
                navController.navigateSafely(Screen.NavidromeDashboard.route)
            },
            isJellyfinLoggedIn = isJellyfinLoggedIn,
            onNavigateToJellyfinDashboard = {
                navController.navigateSafely(Screen.JellyfinDashboard.route)
            }
```

then remove the now-trailing comma after the `onNavigateToQqMusicDashboard = { ... }` argument.

### 1.6 `presentation/components/StreamingProviderSheet.kt`

- Delete the imports of `JellyfinLoginActivity` and `NavidromeLoginActivity` if present.
- Delete these four parameters from `fun StreamingProviderSheet(`:

```kotlin
    isNavidromeLoggedIn: Boolean = false,
    onNavigateToNavidromeDashboard: () -> Unit = {},
    isJellyfinLoggedIn: Boolean = false,
    onNavigateToJellyfinDashboard: () -> Unit = {},
```

- Delete the two `ProviderRow(...)` calls whose `title` is `"Subsonic"` and `"Jellyfin"` (between the `"Google Drive"` row and the `"Netease Music"` row), including any `if (isNavidromeLoggedIn) { ... }` / `if (isJellyfinLoggedIn) { ... }` wrapper and comment around them.

### 1.7 `presentation/viewmodel/AccountsViewModel.kt`

Delete, in this order:

1. Imports `com.theveloper.pixelplay.data.jellyfin.JellyfinRepository` and `com.theveloper.pixelplay.data.navidrome.NavidromeRepository`.
2. Enum entries `NAVIDROME` and `JELLYFIN` from `enum class ExternalServiceAccount`. The enum must end as:
   ```kotlin
   enum class ExternalServiceAccount {
       TELEGRAM,
       GOOGLE_DRIVE,
       NETEASE,
       QQ_MUSIC
   }
   ```
3. Constructor parameters `private val navidromeRepository: NavidromeRepository,` and `private val jellyfinRepository: JellyfinRepository`. The constructor must end with `private val qqMusicRepository: QqMusicRepository` (no trailing comma issue).
4. The whole `private val navidromeStateFlow = combine(...) { ... }` and `private val jellyfinStateFlow = combine(...) { ... }` declarations.
5. `navidromeStateFlow,` and `jellyfinStateFlow` from the `listOf(...)` inside `uiState`. The list must end with `qqMusicStateFlow`.
6. The two destructuring lines reading `states[4]` and `states[5]`.
7. The whole `if (navidromeConnected) { add(ExternalAccountUiModel(...)) }` and `if (jellyfinConnected) { add(ExternalAccountUiModel(...)) }` blocks.
8. `if (!navidromeConnected) add(ExternalServiceAccount.NAVIDROME)` and `if (!jellyfinConnected) add(ExternalServiceAccount.JELLYFIN)` if present.
9. In `fun logout`, the branches `ExternalServiceAccount.NAVIDROME -> navidromeRepository.logout()` and `ExternalServiceAccount.JELLYFIN -> jellyfinRepository.logout()`.

### 1.8 `presentation/screens/AccountsScreen.kt`

1. Delete the imports of `JellyfinLoginActivity` and `NavidromeLoginActivity` if present.
2. Delete the parameters `onOpenNavidromeDashboard: () -> Unit = {},` and `onOpenJellyfinDashboard: () -> Unit = {},` from `fun AccountsScreen(`.
3. In **both** `openService(...)` call sites, delete the arguments `onOpenNavidromeDashboard = onOpenNavidromeDashboard,` and `onOpenJellyfinDashboard = onOpenJellyfinDashboard,`.
4. In the `painter = if (...) ... else null` chain of the connected-account item, delete:
   ```kotlin
                        } else if (account.service == ExternalServiceAccount.JELLYFIN) {
                            painterResource(R.drawable.ic_jellyfin)
                        } else if (account.service == ExternalServiceAccount.NAVIDROME) {
                            painterResource(R.drawable.ic_navidrome_md3)
   ```
   so the chain ends `... painterResource(R.drawable.telegram)` followed by `} else null`.
5. In the account card `Row`, replace:
   ```kotlin
       if (account.service == ExternalServiceAccount.NAVIDROME) {
           ServiceIcon(
               service = account.service,
               tint = palette.iconTint,
               modifier = Modifier
                   .width(48.dp)
                   .height(40.dp)
           )
       } else {
           Surface(
   ```
   with just `Surface(`, and delete the matching closing `}` of that `else` block (the one directly before `Spacer(Modifier.size(12.dp))`). The `Surface(...) { ... }` body stays unchanged.
6. In the `disconnectedServices.forEach` `when (service)` that builds `painter`, delete the `ExternalServiceAccount.JELLYFIN ->` and `ExternalServiceAccount.NAVIDROME ->` branches.
7. In the function returning `ServicePalette`, delete the whole `ExternalServiceAccount.NAVIDROME -> ServicePalette(...)` and `ExternalServiceAccount.JELLYFIN -> ServicePalette(...)` branches.
8. In `private fun accountIcon`, delete the `NAVIDROME` and `JELLYFIN` branches.
9. In `private fun ServiceIcon`, delete the `if (service == ExternalServiceAccount.NAVIDROME) { ... } else if (service == ExternalServiceAccount.JELLYFIN) { ... } else {` structure and keep only the body of the final `else` (the `Icon(imageVector = accountIcon(service), ...)` call) as the function body.
10. In `private fun serviceDisplayName`, delete the `NAVIDROME` and `JELLYFIN` branches.
11. In `private fun openService`, delete the parameters `onOpenNavidromeDashboard: () -> Unit,` and `onOpenJellyfinDashboard: () -> Unit,` and the `ExternalServiceAccount.NAVIDROME -> { ... }` and `ExternalServiceAccount.JELLYFIN -> { ... }` branches.

Unused-import warnings left behind (for example `width`, `height`, `offset`, `vectorResource`) may be removed, but only if the symbol has zero remaining uses in the file.

### 1.9 `presentation/components/PlaylistContainer.kt`

Delete:

```kotlin
                    if (playlist.source == "NAVIDROME") {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_navidrome),
                            contentDescription = "Navidrome",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(18.dp)
                        )
                    }
```

### 1.10 `presentation/viewmodel/SongInfoBottomSheetViewModel.kt`

In `getCloudProviderLabel`, delete:

```kotlin
            normalized.startsWith("navidrome://") || normalized.startsWith("navidrome:") -> "Navidrome"
```

```kotlin
            normalized.startsWith("jellyfin://") || normalized.startsWith("jellyfin:") -> "Jellyfin"
```

**Phase 1 check:** `./gradlew :app:compileDebugKotlin` must succeed.

---

## 3. PHASE 2 — PLAYBACK, SYNC AND SERVICE HOOKS

### 2.1 `data/service/MusicService.kt`

1. Delete `import com.theveloper.pixelplay.data.navidrome.NavidromeRepository`.
2. Delete:
   ```kotlin
       @Inject
       lateinit var navidromeRepository: NavidromeRepository
   ```
3. Delete these members entirely: `getNavidromeId`, `isNavidromeMediaItem`, `reportNavidromePlayback`, `private var navidromePlaybackReportJob: Job? = null`, `startNavidromePlaybackReporting`, `stopNavidromePlaybackReporting`.
4. In `onIsPlayingChanged`, delete:
   ```kotlin
               if (isPlaying) {
                   reportNavidromePlayback("playing")
                   startNavidromePlaybackReporting()
               } else {
                   val state = if (player.playbackState == Player.STATE_ENDED) "stopped" else "paused"
                   reportNavidromePlayback(state)
                   stopNavidromePlaybackReporting()
               }
   ```
   Keep the later `if (isPlaying) { replayGainProcessor.reapplyLastAppliedVolume(player) }`.
5. In `onPlaybackStateChanged`, delete:
   ```kotlin
                   val mediaItem = (mediaSession?.player ?: engine.masterPlayer).currentMediaItem
                   getNavidromeId(mediaItem)?.let { navidromeId ->
                       appScope.launch(Dispatchers.IO) {
                           navidromeRepository.scrobble(navidromeId, submission = true)
                       }
                   }
   ```
   and the two lines `reportNavidromePlayback("stopped")` and `stopNavidromePlaybackReporting()`. Keep `endOfTrackTimerSongId = null`.
6. In `onPositionDiscontinuity`, delete both blocks:
   ```kotlin
               if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                   val state = if (engine.masterPlayer.isPlaying) "playing" else "paused"
                   reportNavidromePlayback(state)
               }

               if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                   val finishedItem = oldPosition.mediaItem
                   if (isNavidromeMediaItem(finishedItem)) {
                       val prevId = getNavidromeId(finishedItem)
                       reportNavidromePlayback("stopped", finishedItem)
                       if (prevId != null) {
                           appScope.launch(Dispatchers.IO) {
                               navidromeRepository.scrobble(prevId, submission = true)
                           }
                       }
                   }
               }
   ```
   Keep the following `if (reason == ...AUTO_TRANSITION || reason == ...SEEK) { ... replayGainProcessor ... }` block.
7. In `onMediaItemTransition`, delete:
   ```kotlin
               if (isNavidromeMediaItem(mediaItem)) {
                   reportNavidromePlayback("starting")
                   if (engine.masterPlayer.isPlaying) {
                       startNavidromePlaybackReporting()
                   }
               } else {
                   stopNavidromePlaybackReporting()
               }
   ```
8. In `onDestroy`, delete the lines `reportNavidromePlayback("stopped")` and `stopNavidromePlaybackReporting()`.

### 2.2 `data/service/player/DualPlayerEngine.kt`

1. Delete `import com.theveloper.pixelplay.data.navidrome.NavidromeStreamProxy`.
2. Delete constructor parameters:
   ```kotlin
       private val navidromeStreamProxy: NavidromeStreamProxy,
       private val jellyfinStreamProxy: com.theveloper.pixelplay.data.jellyfin.JellyfinStreamProxy,
   ```
3. Replace the two scheme sets with:
   ```kotlin
           private val REMOTE_MEDIA_SCHEMES = setOf("http", "https", "telegram", "netease", "qqmusic", "gdrive")
   ```
   ```kotlin
           private val CLOUD_PROXY_SCHEMES = setOf("telegram", "netease", "qqmusic", "gdrive")
   ```
4. In `resolveCloudUri`, delete the branches `"navidrome" -> resolveNavidromeUriAsync(uriString)` and `"jellyfin" -> resolveJellyfinUriAsync(uriString)`.
5. Delete the functions `resolveNavidromeUriAsync` and `resolveJellyfinUriAsync`.

### 2.3 `data/service/wear/PhoneDirectWatchTransferCoordinator.kt`

1. Delete `import com.theveloper.pixelplay.data.navidrome.NavidromeStreamProxy`.
2. Delete constructor parameters `private val navidromeStreamProxy: NavidromeStreamProxy,` and `private val jellyfinStreamProxy: com.theveloper.pixelplay.data.jellyfin.JellyfinStreamProxy,`.
3. In `resolveStreamUrl`, delete the whole `"navidrome" -> { ... }` and `"jellyfin" -> { ... }` branches.
4. In `ensureCloudProxyReady`, delete the `is NavidromeStreamProxy ->` and `is com.theveloper.pixelplay.data.jellyfin.JellyfinStreamProxy ->` branches.

### 2.4 `presentation/viewmodel/PlaybackDispatchStateHolder.kt`

In `buildResolvedPlaybackMediaItem`, delete the two conditions:

```kotlin
            scheme != "navidrome" &&
            scheme != "jellyfin" &&
```

### 2.5 `data/repository/LyricsRepositoryImpl.kt`

1. Delete constructor parameters:
   ```kotlin
       private val jellyfinRepository: dagger.Lazy<com.theveloper.pixelplay.data.jellyfin.JellyfinRepository>? = null,
       private val navidromeRepository: dagger.Lazy<com.theveloper.pixelplay.data.navidrome.NavidromeRepository>? = null
   ```
   and remove the trailing comma after the `neteaseRepository` parameter.
2. Delete both lyric-lookup blocks, from `val jellyfinId = song.jellyfinId?.takeIf { it.isNotBlank() }` through the closing `}` of `if (!navidromeId.isNullOrBlank()) { ... }`. The `null` that follows stays.
3. In `loadEmbeddedLyricsFromMetadata`, delete:
   ```kotlin
               song.contentUriString.startsWith("jellyfin://") ||
               song.contentUriString.startsWith("navidrome://") ||
   ```

### 2.6 `di/AppModule.kt` — lyrics provider

In `provideLyricsRepository`, delete the parameters:

```kotlin
        jellyfinRepository: Lazy<com.theveloper.pixelplay.data.jellyfin.JellyfinRepository>,
        navidromeRepository: Lazy<com.theveloper.pixelplay.data.navidrome.NavidromeRepository>
```

and the arguments:

```kotlin
            jellyfinRepository = jellyfinRepository,
            navidromeRepository = navidromeRepository
```

Remove the trailing commas left after `neteaseRepository` in both places.

### 2.7 `data/worker/SyncWorker.kt`

1. Delete `import com.theveloper.pixelplay.data.navidrome.NavidromeRepository`.
2. Delete the constructor parameter `private val navidromeRepository: NavidromeRepository` and the trailing comma after `private val neteaseDao: NeteaseDao`.
3. Replace:
   ```kotlin
                       // For Navidrome, we only do network sync if SYNC_THRESHOLD_MS (24h) threshold has passed.
                       val navidromeNeedsNetworkSync = navidromeRepository.isLoggedIn && 
                           (System.currentTimeMillis() - navidromeRepository.lastFullSyncTime >= NavidromeRepository.SYNC_THRESHOLD_MS)
                       
                       val needsActiveCloudSync = hasTelegramChannels ||
                           neteaseCount > 0 ||
                           navidromeNeedsNetworkSync
   ```
   with:
   ```kotlin
                       val needsActiveCloudSync = hasTelegramChannels || neteaseCount > 0
   ```
4. Delete:
   ```kotlin
                       if (navidromeRepository.isLoggedIn) {
                           syncNavidromeData()
                       } else {
                           Log.d(TAG, "Skipping Navidrome sync — not logged in.")
                       }
   ```
5. Delete the whole `private suspend fun syncNavidromeData() { ... }` function.

### 2.8 `app/src/androidTest/java/com/theveloper/pixelplay/data/worker/SyncWorkerTest.kt`

Delete the argument `navidromeRepository = mockk(relaxed = true)` and the trailing comma after `neteaseDao = mockk(relaxed = true)`.

### 2.9 `PixelPlayApplication.kt` — Coil fetchers

Delete:

```kotlin
    @Inject
    lateinit var navidromeCoilFetcherFactory: dagger.Lazy<com.theveloper.pixelplay.data.image.NavidromeCoilFetcher.Factory>

    @Inject
    lateinit var jellyfinCoilFetcherFactory: dagger.Lazy<com.theveloper.pixelplay.data.image.JellyfinCoilFetcher.Factory>
```

and, in `newImageLoader()`:

```kotlin
                add(navidromeCoilFetcherFactory.get())
                add(jellyfinCoilFetcherFactory.get())
```

**Phase 2 check:** `./gradlew :app:compileDebugKotlin` must succeed.

---

## 4. PHASE 3 — DATA LAYER, MODEL AND ROOM

### 3.1 Delete these files and directories

```
data/navidrome/                      (NavidromeRepository, NavidromeStreamProxy, model/*)
data/jellyfin/                       (JellyfinRepository, JellyfinStreamProxy, model/*)
data/network/navidrome/              (NavidromeApiService, NavidromeResponseParser)
data/network/jellyfin/               (JellyfinApiService, JellyfinResponseParser)
data/image/NavidromeCoilFetcher.kt
data/image/JellyfinCoilFetcher.kt
data/worker/NavidromeSyncWorker.kt
data/database/NavidromeDao.kt
data/database/NavidromeSongEntity.kt
data/database/NavidromePlaylistEntity.kt
data/database/JellyfinDao.kt
data/database/JellyfinSongEntity.kt
data/database/JellyfinPlaylistEntity.kt
app/src/test/java/com/theveloper/pixelplay/data/navidrome/   (NavidromeCredentialsTest)
app/src/test/java/com/theveloper/pixelplay/data/jellyfin/    (JellyfinCredentialsTest)
```

### 3.2 `di/AppModule.kt` — DAO providers and migration list

Delete both providers:

```kotlin
    @Singleton
    @Provides
    fun provideNavidromeDao(database: PixelPlayDatabase): com.theveloper.pixelplay.data.database.NavidromeDao {
        return database.navidromeDao()
    }
```

```kotlin
    @Singleton
    @Provides
    fun provideJellyfinDao(database: PixelPlayDatabase): com.theveloper.pixelplay.data.database.JellyfinDao {
        return database.jellyfinDao()
    }
```

In `providePixelPlayDatabase`, replace:

```kotlin
            PixelPlayDatabase.MIGRATION_41_42
        )
```

with:

```kotlin
            PixelPlayDatabase.MIGRATION_41_42,
            PixelPlayDatabase.MIGRATION_42_43
        )
```

### 3.3 `data/database/PixelPlayDatabase.kt`

1. In the `entities = [ ... ]` array delete exactly these four lines:
   ```kotlin
           NavidromeSongEntity::class,
           NavidromePlaylistEntity::class,
   ```
   ```kotlin
           JellyfinSongEntity::class,
           JellyfinPlaylistEntity::class,
   ```
2. Change `version = 42,` to `version = 43,`.
3. Delete `abstract fun navidromeDao(): NavidromeDao` and `abstract fun jellyfinDao(): JellyfinDao`.
4. Directly after the closing `}` of `val MIGRATION_41_42 = object : Migration(41, 42) { ... }`, add:

   ```kotlin

           /**
            * Navidrome/Subsonic and Jellyfin support was removed: drop their cache tables.
            * Rows those sources synced into `songs` / `playlists` are purged at runtime by
            * RemovedCloudSourcesCleanup, which reuses the existing DAO delete paths.
            */
           val MIGRATION_42_43 = object : Migration(42, 43) {
               override fun migrate(db: SupportSQLiteDatabase) {
                   db.execSQL("DROP TABLE IF EXISTS navidrome_songs")
                   db.execSQL("DROP TABLE IF EXISTS navidrome_playlists")
                   db.execSQL("DROP TABLE IF EXISTS jellyfin_songs")
                   db.execSQL("DROP TABLE IF EXISTS jellyfin_playlists")
               }
           }
   ```

Change nothing else in this file (hard constraint 2).

### 3.4 `app/src/androidTest/java/com/theveloper/pixelplay/data/database/PixelPlayDatabaseMigrationTest.kt`

Change `const val LATEST = 42` to `const val LATEST = 43`, and in `ALL_MIGRATIONS` replace `PixelPlayDatabase.MIGRATION_41_42` with:

```kotlin
            PixelPlayDatabase.MIGRATION_41_42,
            PixelPlayDatabase.MIGRATION_42_43
```

### 3.5 `data/database/MusicDao.kt`

Delete:

```kotlin
    @Query("SELECT id FROM songs WHERE source_type = 5")
    suspend fun getAllNavidromeSongIds(): List<Long>

    @Query("SELECT id FROM songs WHERE source_type = 6")
    suspend fun getAllJellyfinSongIds(): List<Long>
```

and the two `@Transaction suspend fun clearAllNavidromeSongs() { ... }` / `clearAllJellyfinSongs() { ... }` functions.

In their place (directly after `getAllQqMusicSongIds`) add one generic query, used by Phase 4:

```kotlin
    @Query("SELECT id FROM songs WHERE source_type IN (:sourceTypes)")
    suspend fun getSongIdsBySourceTypes(sourceTypes: List<Int>): List<Long>
```

### 3.6 `data/database/SongEntity.kt`

In `object SourceType`, delete `const val NAVIDROME = 5`, `const val JELLYFIN = 6`, and the two branches `uri.startsWith("navidrome://") -> NAVIDROME` and `uri.startsWith("jellyfin://") -> JELLYFIN`. Do not reuse the numbers 5 and 6 for anything.

In the `SongEntity -> Song` mapper delete:

```kotlin
        navidromeId = if (this.contentUriString.startsWith("navidrome://")) {
            this.contentUriString.removePrefix("navidrome://")
        } else null,
        jellyfinId = if (this.contentUriString.startsWith("jellyfin://")) {
            this.contentUriString.removePrefix("jellyfin://")
        } else null,
```

### 3.7 `data/model/Song.kt`

Delete the two properties:

```kotlin
    val navidromeId: String? = null, // Navidrome song ID
    val jellyfinId: String? = null, // Jellyfin item ID
```

and, in `emptySong()`, the arguments `navidromeId = null,` and `jellyfinId = null` (remove the trailing comma after `qqMusicMid = null`).

### 3.8 `utils/MediaItemBuilder.kt`

Delete `const val EXTERNAL_EXTRA_NAVIDROME_ID = EXTERNAL_EXTRA_PREFIX + "NAVIDROME_ID"` and `song.navidromeId?.let { putString(EXTERNAL_EXTRA_NAVIDROME_ID, it) }`.

### 3.9 `presentation/components/SongPickerBottomSheet.kt`

Replace:

```kotlin
                                s.qqMusicMid == null && s.navidromeId == null && s.jellyfinId == null
```

with `s.qqMusicMid == null`, and:

```kotlin
                                s.qqMusicMid != null || s.navidromeId != null || s.jellyfinId != null
```

with `s.qqMusicMid != null`.

### 3.10 `utils/LocalArtworkUri.kt`

In `isLikelyLocalMedia`, delete:

```kotlin
            !normalized.startsWith("navidrome://") &&
            !normalized.startsWith("jellyfin://") &&
```

### 3.11 `data/stream/CloudStreamSecurity.kt`

1. Delete `NAVIDROME_SONG_ID_REGEX`, `JELLYFIN_ITEM_ID_REGEX`, `validateNavidromeSongId` and `validateJellyfinItemId`.
2. In `isSafeRemoteStreamUrl`, replace:
   ```kotlin
           // Allow private IPs and .local for Subsonic/Navidrome/Jellyfin servers which are often self-hosted
           val isNavidromeStream = httpUrl.pathSegments.contains("stream.view")
           val isJellyfinStream = httpUrl.pathSegments.contains("Audio") && httpUrl.pathSegments.contains("universal")
           
           if (!isNavidromeStream && !isJellyfinStream) {
               if (host in FORBIDDEN_HOSTS) return false
               if (host.endsWith(".local")) return false
               if (isPrivateIpv4Literal(host)) return false
           }
   ```
   with:
   ```kotlin
           if (host in FORBIDDEN_HOSTS) return false
           if (host.endsWith(".local")) return false
           if (isPrivateIpv4Literal(host)) return false
   ```
   (This also closes a hole: any URL whose path contained `stream.view` used to skip the private-address check.)
3. In the KDoc of `isLocalOrPrivateHost`, replace the sentence that names the media servers with `where cleartext HTTP may be acceptable.` Keep the function body and its tests.

### 3.12 Search for stragglers

Search `app/src` for these identifiers and fix every remaining use (there should be none outside files already edited): `navidromeId`, `jellyfinId`, `NavidromeRepository`, `JellyfinRepository`, `NavidromeStreamProxy`, `JellyfinStreamProxy`, `NavidromeDao`, `JellyfinDao`, `SourceType.NAVIDROME`, `SourceType.JELLYFIN`, `clearAllNavidromeSongs`, `clearAllJellyfinSongs`.

**Phase 3 check:** `./gradlew :app:compileDebugKotlin` must succeed. KSP must also write `app/schemas/com.theveloper.pixelplay.data.database.PixelPlayDatabase/43.json`; confirm the file exists and contains neither `navidrome` nor `jellyfin`.

---

## 5. PHASE 4 — ONE-TIME CLEANUP OF DATA LEFT ON DEVICES

Users who had these accounts still hold: stored server URLs and credentials in SharedPreferences, synced songs in `songs` (`source_type` 5 and 6), mirrored playlists in `playlists` (`source` `NAVIDROME` / `JELLYFIN`), cached cover files, and possibly queued WorkManager jobs for the deleted worker class.

### 4.1 Create `data/maintenance/RemovedCloudSourcesCleanup.kt`

```kotlin
package com.theveloper.pixelplay.data.maintenance

import android.content.Context
import androidx.work.WorkManager
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time purge of data left behind by the removed Navidrome/Subsonic and Jellyfin
 * integrations: stored credentials, synced songs, mirrored playlists, cover cache
 * and queued sync work. Safe to call on every launch; it does nothing once it has succeeded.
 */
@Singleton
class RemovedCloudSourcesCleanup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicDao: MusicDao,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val workManager: WorkManager
) {
    suspend fun runIfNeeded() {
        val state = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        if (state.getBoolean(KEY_DONE, false)) return

        runCatching {
            // 1. Server URLs, usernames, passwords and tokens.
            REMOVED_PREFS.forEach { name -> context.deleteSharedPreferences(name) }

            // 2. Work queued for the deleted worker class (WorkManager tags each request
            //    with its worker class name).
            workManager.cancelAllWorkByTag(REMOVED_WORKER_TAG)

            // 3. Songs these sources synced into the unified library.
            musicDao.deleteSongsAndRelatedData(
                musicDao.getSongIdsBySourceTypes(REMOVED_SOURCE_TYPES)
            )

            // 4. App playlists mirrored from these sources.
            playlistPreferencesRepository.userPlaylistsFlow.first()
                .filter { playlist -> playlist.source in REMOVED_PLAYLIST_SOURCES }
                .forEach { playlist -> playlistPreferencesRepository.deletePlaylist(playlist.id) }

            // 5. Cached cover art.
            context.cacheDir.listFiles { file ->
                file.isFile && REMOVED_COVER_CACHE_PREFIXES.any { prefix -> file.name.startsWith(prefix) }
            }?.forEach { file -> file.delete() }
        }.onSuccess {
            state.edit().putBoolean(KEY_DONE, true).apply()
        }.onFailure { throwable ->
            Timber.w(throwable, "Cleanup of removed cloud sources failed; retrying on next launch")
        }
    }

    private companion object {
        const val STATE_PREFS = "removed_cloud_sources_cleanup"
        const val KEY_DONE = "done_v1"

        val REMOVED_PREFS = listOf(
            "navidrome_prefs",
            "navidrome_prefs_plain",
            "jellyfin_prefs",
            "jellyfin_prefs_plain"
        )

        /** Former SourceType.NAVIDROME (5) and SourceType.JELLYFIN (6). */
        val REMOVED_SOURCE_TYPES = listOf(5, 6)
        val REMOVED_PLAYLIST_SOURCES = setOf("NAVIDROME", "JELLYFIN")
        val REMOVED_COVER_CACHE_PREFIXES = listOf("navidrome_cover_", "jellyfin_cover_")
        const val REMOVED_WORKER_TAG = "com.theveloper.pixelplay.data.worker.NavidromeSyncWorker"
    }
}
```

### 4.2 `PixelPlayApplication.kt` — run it at startup

Add next to the other injected fields:

```kotlin
    @Inject
    lateinit var removedCloudSourcesCleanup: dagger.Lazy<com.theveloper.pixelplay.data.maintenance.RemovedCloudSourcesCleanup>
```

In `onCreate()`, inside the existing `startupScope.launch { ... }` block, add as its **last** statements (after the `if (savedLimit != null) { ... }` block):

```kotlin
            // One-time purge of data left by the removed Navidrome/Jellyfin integrations.
            runCatching { removedCloudSourcesCleanup.get().runIfNeeded() }
```

**Phase 4 check:** `./gradlew :app:compileDebugKotlin` must succeed.

---

## 6. PHASE 5 — RESOURCES

### 5.1 Strings

In **every** `app/src/main/res/values*/` directory, delete every `<string ...>` element whose `name` contains `navidrome`, `jellyfin` or `subsonic`, together with the section comments that only label them (for example `<!-- Subsonic / Navidrome Auth -->`, `<!-- Jellyfin Auth -->`, `<!-- Subsonic/Navidrome dashboard -->`, `<!-- Jellyfin dashboard -->`).

Files that contain such names today:

```
values/strings_cloud_services.xml
values-ar/strings_auth.xml
values-ar/strings_screens.xml
values-{de,es,fr,in,it,ko,nb,ru,tr,zh-rCN}/strings_cloud_services.xml
```

Default-locale names (other locales may use the same or older names): `subsonic_dashboard_title`, `subsonic_dashboard_quick_actions_subtitle`, `jellyfin_dashboard_playlists_empty_hint`, `jellyfin_dashboard_quick_actions_subtitle`, `auth_navidrome_footer_msg`, `auth_navidrome_info_msg`, `auth_navidrome_password_hint`, `auth_navidrome_prefill_https_action`, `auth_navidrome_subtitle`, `auth_navidrome_title`, `auth_navidrome_url_hint`, `auth_navidrome_url_placeholder`, `auth_navidrome_username_hint`, `auth_subsonic_navidrome_title`, `auth_subsonic_title`, `auth_jellyfin_connect_subtitle`, `auth_jellyfin_footer_msg`, `auth_jellyfin_info_msg`, `auth_jellyfin_logo_cd`, `auth_jellyfin_password_hint`, `auth_jellyfin_subtitle`, `auth_jellyfin_title`, `auth_jellyfin_url_hint`, `auth_jellyfin_url_placeholder`, `auth_jellyfin_username_hint`.

Before deleting, confirm that searching `app/src/main/java` for `R.string.` followed by a name containing `navidrome`, `jellyfin` or `subsonic` returns nothing.

Do **not** delete strings whose *value* merely mentions these services (changelog entries; hard constraint 7).

### 5.2 Drawables

After confirming that `R.drawable.ic_navidrome`, `R.drawable.ic_navidrome_md3`, `R.drawable.ic_jellyfin` and `R.drawable.ic_subsonic` have zero remaining references, delete:

```
app/src/main/res/drawable/ic_navidrome.xml
app/src/main/res/drawable/ic_navidrome_md3.xml
app/src/main/res/drawable/ic_jellyfin.xml
app/src/main/res/drawable/ic_subsonic.xml
```

---

## 7. VERIFICATION (run all of it, report real output)

1. `./gradlew :app:assembleDebug`
2. `./gradlew :app:testDebugUnitTest`
3. `./gradlew :app:compileDebugAndroidTestKotlin` (compiles `SyncWorkerTest` and the migration test)
4. If a device or emulator is attached: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.theveloper.pixelplay.data.database.PixelPlayDatabaseMigrationTest`. If none is attached, say so; do not claim it passed.
5. Case-insensitive search of `app/src`, `gradle/` and `app/schemas` for `navidrome|jellyfin|subsonic`. The **only** files allowed to match are:
   - `app/src/main/java/com/theveloper/pixelplay/data/database/PixelPlayDatabase.kt` (historical migrations and `MIGRATION_42_43`)
   - `app/src/main/java/com/theveloper/pixelplay/data/maintenance/RemovedCloudSourcesCleanup.kt`
   - `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml`
   - `strings_changelogs.xml` (all locales) and `values-ar/strings_presentation_batch_g.xml`
   - `app/src/release/generated/baselineProfiles/*`
   - `app/schemas/**/29.json` … `42.json`
   - `gradle/libs.versions.toml` (the `org.jellyfin.media3` FFmpeg decoder)

   List any other match and fix it.
6. `git status` must show: no change to `gradle/libs.versions.toml`, no change to `app/schemas/**/1.json`…`42.json`, and a new `43.json`.
7. Manual test on a device (state clearly if you could not run it):
   - Fresh install: app starts, local playback works, the cloud-streaming sheet and Accounts screen list only Telegram, Google Drive, Netease Music and QQ Music.
   - Upgrade install over a build that had a Navidrome or Jellyfin account: app starts without a crash, those songs and playlists are gone after the first launch, other playlists and favourites are intact.
   - A Telegram or Netease track still plays; Cast still works.

## 8. KNOWN EDGE (do not try to fix)

A saved playback queue may still contain `navidrome://` or `jellyfin://` items on the first launch after upgrade. They can no longer be resolved and will fail to play; the player's normal error handling skips them. The cleanup in Phase 4 removes them from the library.

## 9. REPORT FORMAT

Reply with: (1) files deleted, (2) files edited, (3) for each phase `DONE` / `SKIPPED (reason)` and the compile-check result, (4) full output summary of verification steps 1–6, (5) anything you could not verify. Do not claim a step passed unless you ran it.
