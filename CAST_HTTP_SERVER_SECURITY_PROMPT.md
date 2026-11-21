# PROMPT FOR GEMINI 3.8 FLASH — HARDEN THE CAST LAN HTTP SERVER (`MediaFileHttpServerService`)

You are a senior Android/Kotlin engineer working on **PixelPlayer**. Your task has two parts:

- **Part A (Steps 1–8):** harden the local HTTP server that streams audio files from the phone to Cast receivers. Backend only.
- **Part B (Steps 9–11):** block cleartext HTTP app-wide in `network_security_config.xml`, and remove the Navidrome and Jellyfin sign-in screens (the only feature that needed cleartext to arbitrary hosts).

No new user-facing strings and no theme work in either part. The only UI edits are the removals in Step 11.

Read this whole file before editing. Apply the steps in order. Change only what each step names and keep all unrelated logic byte-for-byte.

---

## 1. BACKGROUND (how the feature works today)

- `MediaFileHttpServerService` (a foreground `Service`) runs a Ktor **CIO** server (Ktor `3.5.0`) on port `8080` (falls back to `8081..8100`).
- Routes: `GET|HEAD /song/{songId}`, `GET|HEAD /art/{songId}`, `GET|HEAD /health`.
- `CastTransferStateHolder` starts the service, calls `MediaFileHttpServerService.configureCastSessionAccess(...)` to build a `CastAccessPolicy` (random 128-bit token + allow-list of queued song IDs), and passes the token to `CastPlayer.loadQueue(...)`, which builds URLs such as `http://192.168.1.10:8080/song/42?v=<rev>&auth=<token>`.
- The Cast receiver downloads those URLs over **cleartext HTTP**. This cannot become HTTPS: Cast receivers reject self-signed certificates for LAN IPs. So the token travels in clear text on the Wi-Fi network and is visible to every Cast sender that joins the receiver session. The hardening below is therefore about **limiting who can reach the server, how long a token lives, and how much damage a token holder can do**.

### Files you will edit

| File | Purpose |
| :--- | :--- |
| `app/src/main/java/com/theveloper/pixelplay/data/service/http/CastSessionSecurity.kt` | Pure policy logic (unit-testable) |
| `app/src/main/java/com/theveloper/pixelplay/data/service/http/MediaFileHttpServerService.kt` | The server (2939 lines) |
| `app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/CastTransferStateHolder.kt` | Session lifecycle call sites |
| `app/src/test/java/com/theveloper/pixelplay/data/service/http/CastSessionSecurityTest.kt` | JUnit 5 tests |
| `app/src/main/java/com/theveloper/pixelplay/data/service/CastSyncCoordinator.kt` | Part B: Cast artwork URL read back by the app |
| `app/src/main/res/xml/network_security_config.xml` | Part B: cleartext policy |
| `app/src/main/AndroidManifest.xml` | Part B: remove two `<activity>` entries |
| `app/src/main/java/com/theveloper/pixelplay/presentation/components/StreamingProviderSheet.kt` | Part B: remove sign-in entry points |
| `app/src/main/java/com/theveloper/pixelplay/presentation/screens/AccountsScreen.kt` | Part B: remove sign-in entry points |
| `app/src/main/java/com/theveloper/pixelplay/presentation/viewmodel/AccountsViewModel.kt` | Part B: stop offering the two services |

Files you will **delete** (Part B): `presentation/navidrome/auth/NavidromeLoginActivity.kt`, `presentation/navidrome/auth/NavidromeLoginViewModel.kt`, `presentation/jellyfin/auth/JellyfinLoginActivity.kt`, `presentation/jellyfin/auth/JellyfinLoginViewModel.kt` (all under `app/src/main/java/com/theveloper/pixelplay/`).

Line numbers below are from the current working tree and are hints only. Always locate code by the quoted snippet, not by the number.

---

## 2. FINDINGS TO FIX

| ID | Severity | Problem | Location |
| :--- | :--- | :--- | :--- |
| F1 | High | Server binds `0.0.0.0`, so it also listens on mobile data, VPN, hotspot and USB-tether interfaces, not only the Wi-Fi network the Cast device is on. | `MediaFileHttpServerService.kt` `embeddedServer(CIO, port = serverPort, host = "0.0.0.0")` (~L384), `isPortAvailable` (~L867) |
| F2 | High | The client-address allow-list is computed but never enforced. `ensureAuthorizedCastMediaRequest` returns `true` for any remote IP that presents the token and only logs. `enforceClientAddressAllowlist` is dead. | ~L995–1031 |
| F3 | Medium | The token is never rotated while the service lives (`existingToken = castAccessPolicy.authToken`). When the user switches Cast device, `stopServerAndTransferBack` keeps the service running (`pendingCastRouteId != null`), so the next device and anyone who saw the old URL share one token. The policy is not cleared when a session ends. | `MediaFileHttpServerService.kt` ~L176–191, `CastTransferStateHolder.kt` ~L1105–1115 |
| F4 | Medium | Transcode cache is check-then-put, not atomic: two concurrent requests for the same song (Cast routinely sends an initial GET plus a Range GET) both start a transcode into the same temp file and corrupt it. | `respondTranscodedWithCache` ~L1890–1984 |
| F5 | Medium | No resource limits for a token holder: unlimited concurrent `MediaCodec` transcodes, unlimited disk use in `cacheDir` (`evictTranscodeCache` is never called; files are deleted only in `onDestroy` and are orphaned if the process is killed), and every waiting Range request blocks an IO thread for up to 10 minutes on `latch.await`. | ~L1953, ~L1991, ~L2057 |
| F6 | Medium | `signatureMimeCache` and `codecInfoCache` are plain `mutableMapOf()` mutated from concurrent request handlers (data race). | ~L108–110 |
| F7 | Medium | `START_STICKY` plus `ACTION_START_SERVER, null ->` means the system recreates the service after process death and reopens the port with no Cast session and nothing left to stop it. | `onStartCommand` ~L269–289 |
| F8 | Low | Token compared with `!=` (not constant-time), in two places. | `CastSessionSecurity.kt` ~L82, `MediaFileHttpServerService.kt` ~L1013 |
| F9 | Low | HTTP 500 body returns `e.message`; WARN/ERROR logs (which reach logcat in release builds through `ReleaseTree`) print file paths and content URIs. | ~L513–519, ~L546–566, ~L657–664 |
| F10 | Low | Temp file name is built from the raw song ID (`"cast_transcode_${songId}.aac"`). IDs are allow-listed, but an ID containing `/` or `..` would break or escape `cacheDir`. | ~L1940 |
| F11 | Low | Client address is read with `request.origin.remoteHost`, which may be a resolved host name. Policy decisions must use the IP literal. | ~L986, ~L996 |
| F12 | High | `network_security_config.xml` sets `<base-config cleartextTrafficPermitted="true">`, which overrides the manifest's `usesCleartextTraffic="false"`: the whole app may send cleartext HTTP to any host. | `res/xml/network_security_config.xml` |
| F13 | Medium | Navidrome/Jellyfin sign-in accepts `http://` server URLs and sends account credentials over them. It is the only feature that needs cleartext to arbitrary hosts. | `NavidromeLoginActivity`, `JellyfinLoginActivity` |
| F14 | Medium | Once F12 is fixed, the app can no longer fetch its own Cast artwork URL (`http://<lan-ip>:<port>/art/..`), which `MusicService` downloads for widgets during remote playback. LAN IPs are dynamic and cannot be listed in the config; the app must use loopback. | `CastSyncCoordinator.kt` ~L245–250 |

---

## 3. HARD CONSTRAINTS (do NOT do these)

1. Do **not** switch to HTTPS and do not add any Ktor plugin or Gradle dependency.
2. Edit `network_security_config.xml` and `AndroidManifest.xml` **only** as Steps 10 and 11 specify. Do not touch any other manifest entry, permission or `res/xml` file.
3. Do **not** change URL shapes (`/song/{id}?v=..&auth=..`, `/art/{id}`), the `auth` query parameter name, or anything in `CastPlayer.kt`.
4. Do **not** touch the transcode pipelines themselves (`transcodeToAacAdts`, `transcodeToAacAdtsViaMediaCodec`, `transcodeToAacAdtsViaFfmpeg`, `writeAdtsHeader`), MIME detection, Range handling in `respondWithAudioStream`, or `selectIpAddress`.
5. Do **not** change the preferred port (`8080`) or the retry logic in `startServer`.
6. Keep every existing `CastSessionSecurityTest` test passing without editing its assertions.
7. Do **not** restrict clients to the single Cast device IP. Cast groups and receivers legitimately fetch from other addresses on the same subnet. The rule is "same subnet, or the hinted Cast IP, or loopback".
8. No Compose/UI edits other than the removals in Step 11. `AGENTS.md` M3E rules still apply to any file you touch, but nothing here should need them.
9. Part B removes **sign-in only**. Do **not** delete or modify the Navidrome/Jellyfin data layer: `data/navidrome/**`, `data/jellyfin/**`, `data/network/navidrome/**`, `data/network/jellyfin/**`, `NavidromeDao`, `JellyfinDao`, the `*Entity` classes, `PixelPlayDatabase` (no Room version bump, no migration), `NavidromeSyncWorker`, `SyncWorker`, `MusicService`, `DualPlayerEngine`, the Coil fetchers, the dashboard screens/view models, `Screen.kt`, `AppNavigation.kt`, `HomeScreen.kt`, `Song`/`SongEntity` fields, string resources, drawables, `backup_rules.xml`, `data_extraction_rules.xml`, or the two `*CredentialsTest` files. Accounts that are already signed in keep working over `https://` and can still be signed out from the Accounts screen.
10. Do **not** edit generated files under `app/src/release/generated/baselineProfiles/` or anything under `app/build/`.

---

## 4. STEP-BY-STEP IMPLEMENTATION

### STEP 1 — `CastSessionSecurity.kt`: policy model, subnet check, constant-time token, safe file name (F2, F3, F8, F10)

**1a.** Add the import:

```kotlin
import java.security.MessageDigest
```

**1b.** Replace the `CastAccessPolicy` data class header with (the two new fields have defaults so existing callers and tests still compile; `EMPTY` stays unchanged):

```kotlin
internal data class CastAccessPolicy(
    val authToken: String?,
    val allowedSongIds: Set<String>,
    val allowedClientAddresses: Set<String>,
    val enforceClientAddressAllowlist: Boolean,
    /** IPv4 literal the server is bound to; null when unknown. */
    val serverHostAddress: String? = null,
    /** Prefix length of the server's LAN subnet; -1 when unknown. */
    val serverPrefixLength: Int = -1
) {
```

**1c.** Inside `object CastSessionSecurity`, add a constant next to `AUTH_QUERY_PARAMETER`:

```kotlin
    const val TRANSCODE_FILE_PREFIX = "cast_transcode_"
```

**1d.** Replace `buildAccessPolicy` with:

```kotlin
    fun buildAccessPolicy(
        existingToken: String?,
        allowedSongIds: Collection<String>,
        castDeviceIpHint: String?,
        serverOwnIp: String? = null,
        serverPrefixLength: Int = -1,
        rotateToken: Boolean = false
    ): CastAccessPolicy {
        val normalizedSongIds = allowedSongIds
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
        val castAddressVariants = normalizeAddressVariants(castDeviceIpHint)
        val allowedAddresses = buildSet {
            loopbackCandidates.forEach { addAll(normalizeAddressVariants(it)) }
            addAll(castAddressVariants)
            // On-device components that connect through the LAN address.
            serverOwnIp?.let { addAll(normalizeAddressVariants(it)) }
        }
        return CastAccessPolicy(
            authToken = if (rotateToken) generateAuthToken() else existingToken ?: generateAuthToken(),
            allowedSongIds = normalizedSongIds,
            allowedClientAddresses = allowedAddresses,
            enforceClientAddressAllowlist = castAddressVariants.isNotEmpty(),
            serverHostAddress = serverOwnIp?.trim()?.takeIf { it.isNotEmpty() },
            serverPrefixLength = serverPrefixLength
        )
    }
```

**1e.** Replace `isAuthorizedClientAddress` with:

```kotlin
    /**
     * A client is allowed when it is loopback, an explicitly allowed address
     * (hinted Cast device, the server's own IP), or inside the server's LAN subnet.
     * When the subnet is unknown, the legacy allow-list flag decides.
     */
    fun isAuthorizedClientAddress(remoteAddress: String?, policy: CastAccessPolicy): Boolean {
        if (isLoopbackAddress(remoteAddress)) return true
        val normalizedRemote = normalizeAddressVariants(remoteAddress)
        if (normalizedRemote.any { candidate -> candidate in policy.allowedClientAddresses }) return true
        val serverHost = policy.serverHostAddress
        if (serverHost != null && policy.serverPrefixLength in 1..32) {
            return normalizedRemote.any { candidate ->
                isSameIpv4Subnet(candidate, serverHost, policy.serverPrefixLength)
            }
        }
        return !policy.enforceClientAddressAllowlist
    }
```

**1f.** Replace `isAuthorizedSongRequest` and add `isValidToken` above it:

```kotlin
    /** Constant-time token comparison. */
    fun isValidToken(providedToken: String?, policy: CastAccessPolicy): Boolean {
        val expected = policy.authToken?.takeIf { it.isNotBlank() } ?: return false
        val provided = providedToken ?: return false
        return MessageDigest.isEqual(
            provided.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8)
        )
    }

    fun isAuthorizedSongRequest(
        providedToken: String?,
        requestedSongId: String?,
        policy: CastAccessPolicy
    ): Boolean {
        val normalizedSongId = requestedSongId?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        if (!isValidToken(providedToken, policy)) return false
        return normalizedSongId in policy.allowedSongIds
    }
```

**1g.** Add these three functions inside the object (place them before `generateAuthToken`):

```kotlin
    fun isSameIpv4Subnet(addressA: String?, addressB: String?, prefixLength: Int): Boolean {
        if (prefixLength !in 1..32) return false
        val a = parseIpv4Literal(addressA) ?: return false
        val b = parseIpv4Literal(addressB) ?: return false
        val mask = if (prefixLength == 32) -1 else (-1 shl (32 - prefixLength))
        return (a and mask) == (b and mask)
    }

    /** Hashes the song id so the temp file name can never contain path separators. */
    fun transcodeCacheFileName(songId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(songId.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString(separator = "") { byte -> "%02x".format(byte) }.take(32)
        return "$TRANSCODE_FILE_PREFIX$hex.aac"
    }

    /** Strict dotted-quad parser. Never performs a DNS lookup. */
    private fun parseIpv4Literal(rawAddress: String?): Int? {
        val parts = rawAddress
            ?.trim()
            ?.lowercase()
            ?.removePrefix("::ffff:")
            ?.split('.')
            ?: return null
        if (parts.size != 4) return null
        var result = 0
        for (part in parts) {
            if (part.isEmpty() || part.length > 3 || !part.all(Char::isDigit)) return null
            val octet = part.toInt()
            if (octet > 255) return null
            result = (result shl 8) or octet
        }
        return result
    }
```

---

### STEP 2 — `MediaFileHttpServerService.kt`: policy plumbing (F3)

**2a.** In the `companion object`, add a lock and replace `configureCastSessionAccess` and `clearCastSessionAccess`:

```kotlin
        private val castAccessPolicyLock = Any()

        internal fun configureCastSessionAccess(
            allowedSongIds: Collection<String>,
            castDeviceIpHint: String?,
            rotateToken: Boolean = false
        ): CastAccessPolicy = synchronized(castAccessPolicyLock) {
            val updatedPolicy = CastSessionSecurity.buildAccessPolicy(
                existingToken = castAccessPolicy.authToken,
                allowedSongIds = allowedSongIds,
                castDeviceIpHint = castDeviceIpHint,
                serverOwnIp = serverHostAddress,
                serverPrefixLength = serverPrefixLength,
                rotateToken = rotateToken
            )
            castAccessPolicy = updatedPolicy
            updatedPolicy
        }

        internal fun currentCastAccessPolicy(): CastAccessPolicy = castAccessPolicy

        internal fun clearCastSessionAccess() = synchronized(castAccessPolicyLock) {
            castAccessPolicy = CastAccessPolicy.EMPTY
        }
```

**2b.** In the same `companion object`, add constants next to `TRANSCODE_STREAM_IDLE_TIMEOUT_MS`:

```kotlin
        private const val MAX_CONCURRENT_TRANSCODES = 2
        private const val MAX_TRANSCODE_CACHE_ENTRIES = 8
        private const val MAX_TRANSCODE_CACHE_BYTES = 512L * 1024L * 1024L
        private const val IDLE_CHECK_INTERVAL_MS = 30_000L
        private const val IDLE_MAX_TICKS = 4
```

---

### STEP 3 — `MediaFileHttpServerService.kt`: bind only to the LAN address and loopback (F1)

**3a.** In `startServer`, find:

```kotlin
                val serverPort = resolveServerPort(preferredPort = 8080)
```

Replace with:

```kotlin
                val serverPort = resolveServerPort(
                    preferredPort = 8080,
                    lanHost = addressSelection.hostAddress
                )
```

**3b.** In `startServer`, find:

```kotlin
                server = embeddedServer(CIO, port = serverPort, host = "0.0.0.0") {
                    routing {
```

Replace **only these two lines** with (the `routing { ... }` body and the closing `}.start(wait = false)` stay exactly as they are; `io.ktor.server.engine.connector` is already imported):

```kotlin
                deleteOrphanedTranscodeFiles()
                server = embeddedServer(
                    CIO,
                    configure = {
                        // LAN interface the Cast device can reach.
                        connector {
                            host = addressSelection.hostAddress
                            port = serverPort
                        }
                        // Loopback for the in-app preflight (CastSessionSecurity.buildLoopbackSongUrl).
                        connector {
                            host = "127.0.0.1"
                            port = serverPort
                        }
                    }
                ) {
                    routing {
```

**3c.** Directly after `isServerRunning = true` (the line that follows `}.start(wait = false)`), add:

```kotlin
                launchIdleWatchdog()
```

**3d.** Replace `resolveServerPort` and `isPortAvailable` with:

```kotlin
    private fun resolveServerPort(preferredPort: Int, lanHost: String): Int {
        val startPort = preferredPort.coerceIn(1, 65535)
        val candidatePorts = sequence {
            yield(startPort)
            val upperBound = (startPort + 20).coerceAtMost(65535)
            for (port in (startPort + 1)..upperBound) {
                yield(port)
            }
        }

        candidatePorts.firstOrNull { isPortAvailable(it, lanHost) }?.let { return it }

        return runCatching {
            ServerSocket(0).use { socket ->
                max(socket.localPort, 1)
            }
        }.getOrDefault(startPort)
    }

    /** The port must be free on both interfaces the server binds to. */
    private fun isPortAvailable(port: Int, lanHost: String): Boolean {
        if (port !in 1..65535) return false
        return listOf(lanHost, "127.0.0.1").all { host ->
            runCatching {
                ServerSocket().use { socket ->
                    socket.reuseAddress = true
                    socket.bind(InetSocketAddress(host, port))
                    true
                }
            }.getOrDefault(false)
        }
    }
```

After this step the string `"0.0.0.0"` must no longer appear anywhere in this file.

---

### STEP 4 — `MediaFileHttpServerService.kt`: enforce client network and use the IP literal (F2, F8, F11)

Replace `ensureLoopbackHealthRequest` and `ensureAuthorizedCastMediaRequest` with:

```kotlin
    private suspend fun ApplicationCall.ensureLoopbackHealthRequest(): Boolean {
        val remoteAddress = request.origin.remoteAddress
        if (CastSessionSecurity.isLoopbackAddress(remoteAddress)) {
            return true
        }
        Timber.tag(castHttpLogTag).w("Rejected Cast health request from non-loopback client=%s", remoteAddress)
        respond(HttpStatusCode.Forbidden, "Forbidden")
        return false
    }

    private suspend fun ApplicationCall.ensureAuthorizedCastMediaRequest(songId: String): Boolean {
        // remoteAddress is the IP literal; remoteHost may be a resolved host name.
        val remoteAddress = request.origin.remoteAddress
        val policy = currentCastAccessPolicy()
        val providedToken = request.queryParameters[CastSessionSecurity.AUTH_QUERY_PARAMETER]

        // 1. Network boundary: loopback, hinted Cast device, or the server's own LAN subnet.
        if (!CastSessionSecurity.isAuthorizedClientAddress(remoteAddress, policy)) {
            Timber.tag(castHttpLogTag).w(
                "Rejected Cast media request from off-network client=%s songId=%s",
                remoteAddress,
                songId
            )
            respond(HttpStatusCode.Forbidden, "Forbidden")
            return false
        }

        // 2. Session token (constant-time compare).
        if (!CastSessionSecurity.isValidToken(providedToken, policy)) {
            Timber.tag(castHttpLogTag).w(
                "Rejected Cast media request with invalid token client=%s songId=%s",
                remoteAddress,
                songId
            )
            respond(HttpStatusCode.Unauthorized, "Unauthorized")
            return false
        }

        // 3. Song allow-list for the current queue.
        if (!CastSessionSecurity.isAuthorizedSongRequest(providedToken, songId, policy)) {
            Timber.tag(castHttpLogTag).w(
                "Rejected Cast media request for non-whitelisted song client=%s songId=%s",
                remoteAddress,
                songId
            )
            respond(HttpStatusCode.NotFound, "Song not found")
            return false
        }

        response.header("X-Content-Type-Options", "nosniff")
        return true
    }
```

If `request.origin.remoteAddress` does not compile against Ktor `3.5.0`, stop and report it. Do not silently fall back to `remoteHost`.

---

### STEP 5 — `MediaFileHttpServerService.kt`: transcode cache safety (F4, F5, F6, F10)

**5a.** Add imports:

```kotlin
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
```

**5b.** Replace the two cache fields:

```kotlin
    private val signatureMimeCache = mutableMapOf<String, String?>()
    // Cache for the actual codec info (codec MIME, sample rate, channels) to avoid re-probing.
    private val codecInfoCache = mutableMapOf<String, AudioCodecInfo?>()
```

with (synchronized wrappers keep `null` values legal, which `ConcurrentHashMap` does not):

```kotlin
    private val signatureMimeCache: MutableMap<String, String?> =
        java.util.Collections.synchronizedMap(HashMap())
    // Cache for the actual codec info (codec MIME, sample rate, channels) to avoid re-probing.
    private val codecInfoCache: MutableMap<String, AudioCodecInfo?> =
        java.util.Collections.synchronizedMap(HashMap())
```

**5c.** In `TranscodeEntry`, add one field after `failed`:

```kotlin
        /** Updated on every request; drives LRU eviction. */
        @Volatile var lastAccessAtMs: Long = System.currentTimeMillis(),
```

**5d.** Below `private val transcodeCache = ConcurrentHashMap<String, TranscodeEntry>()` add:

```kotlin
    private val transcodeSemaphore = Semaphore(MAX_CONCURRENT_TRANSCODES)
    private var idleWatchdogJob: Job? = null
```

**5e.** Replace the entire `respondTranscodedWithCache` function (keep its KDoc) with:

```kotlin
    private suspend fun ApplicationCall.respondTranscodedWithCache(
        song: Song,
        codecInfo: AudioCodecInfo,
        uri: Uri,
        rangeHeader: String?
    ) {
        val songId = song.id
        val aacContentType = ContentType.parse("audio/aac")

        // Atomic get-or-create: exactly one request becomes the owner and starts the transcode.
        var createdEntry: TranscodeEntry? = null
        val entry: TranscodeEntry = checkNotNull(
            transcodeCache.compute(songId) { _, current ->
                val reusable = current != null &&
                    !current.failed &&
                    (!current.done || current.tempFile.exists())
                if (reusable) {
                    current
                } else {
                    TranscodeEntry(
                        tempFile = File(cacheDir, CastSessionSecurity.transcodeCacheFileName(songId))
                    ).also { createdEntry = it }
                }
            }
        )
        entry.lastAccessAtMs = System.currentTimeMillis()

        if (createdEntry === entry) {
            Timber.tag(castHttpLogTag).d("transcode-cache MISS songId=%s, starting progressive stream", songId)
            Timber.tag("PX_CAST_HTTP").i("transcode_cache_start songId=$songId codec=${codecInfo.codecMime}")
            startBackgroundTranscode(entry = entry, song = song, codecInfo = codecInfo, uri = uri)
        }

        // Fast path: temp file already complete → serve with Range support.
        if (entry.done && !entry.failed && entry.tempFile.exists()) {
            Timber.tag(castHttpLogTag).d(
                "transcode-cache HIT songId=%s size=%d range=%s",
                songId, entry.tempFile.length(), rangeHeader
            )
            respondWithAudioStream(
                contentType = aacContentType,
                fileSize = entry.tempFile.length(),
                rangeHeader = rangeHeader
            ) { FileInputStream(entry.tempFile) }
            return
        }

        // Non-zero Range requests wait for the completed file so Cast never gets bytes from the wrong offset.
        if (isNonInitialRangeRequest(rangeHeader)) {
            respondWhenTranscodeCompletes(
                entry = entry,
                contentType = aacContentType,
                rangeHeader = rangeHeader
            )
            return
        }

        respondOutputStream(aacContentType) {
            streamFromGrowingFile(entry, this)
        }
    }

    /**
     * Transcodes to the entry's temp file in the background. Decoupled from the response stream,
     * so a Cast connection reset cannot fail the transcode. At most
     * [MAX_CONCURRENT_TRANSCODES] run at once so a token holder cannot exhaust MediaCodec instances.
     */
    private fun startBackgroundTranscode(
        entry: TranscodeEntry,
        song: Song,
        codecInfo: AudioCodecInfo,
        uri: Uri
    ) {
        val songId = song.id
        serviceScope.launch {
            runCatching {
                transcodeSemaphore.withPermit {
                    runCatching { if (entry.tempFile.exists()) entry.tempFile.delete() }
                    FileOutputStream(entry.tempFile).use { fos ->
                        transcodeToAacAdts(codecInfo, song, uri, fos)
                    }
                }
                entry.done = true
                Timber.tag("PX_CAST_HTTP").i("transcode_cache_done songId=$songId size=${entry.tempFile.length()}")
            }.onFailure { t ->
                entry.failed = true
                runCatching { entry.tempFile.delete() }
                if (!t.isClientAbortDuringResponse()) {
                    Timber.tag(castHttpLogTag).e(t, "transcode-cache bg transcode failed songId=%s", songId)
                    Timber.tag("PX_CAST_HTTP").e(t, "transcode_cache_error songId=$songId")
                }
            }.also {
                entry.latch.countDown()
            }
            trimTranscodeCache(keepSongId = songId)
        }
    }

    /** LRU eviction of completed transcodes so the cache dir cannot grow without bound. */
    private fun trimTranscodeCache(keepSongId: String) {
        val completed = transcodeCache.entries.filter { it.value.done && !it.value.failed }
        var count = completed.size
        var totalBytes = completed.sumOf { it.value.tempFile.length() }
        val evictionOrder = completed
            .filter { it.key != keepSongId }
            .sortedBy { it.value.lastAccessAtMs }
        for (candidate in evictionOrder) {
            if (count <= MAX_TRANSCODE_CACHE_ENTRIES && totalBytes <= MAX_TRANSCODE_CACHE_BYTES) break
            val size = candidate.value.tempFile.length()
            if (transcodeCache.remove(candidate.key, candidate.value)) {
                runCatching { candidate.value.tempFile.delete() }
                count -= 1
                totalBytes -= size
                Timber.tag(castHttpLogTag).d("transcode-cache LRU evicted songId=%s", candidate.key)
            }
        }
    }

    /** Removes temp files left behind when the process was killed before onDestroy ran. */
    private fun deleteOrphanedTranscodeFiles() {
        runCatching {
            cacheDir.listFiles { file ->
                file.isFile && file.name.startsWith(CastSessionSecurity.TRANSCODE_FILE_PREFIX)
            }?.forEach { file ->
                if (transcodeCache.values.none { it.tempFile == file }) {
                    file.delete()
                }
            }
        }
    }

    /** Stops the service when no Cast session has configured access for ~2 minutes. */
    private fun launchIdleWatchdog() {
        idleWatchdogJob?.cancel()
        idleWatchdogJob = serviceScope.launch {
            var idleTicks = 0
            while (isActive) {
                delay(IDLE_CHECK_INTERVAL_MS)
                idleTicks = if (currentCastAccessPolicy().authToken == null) idleTicks + 1 else 0
                if (idleTicks >= IDLE_MAX_TICKS) {
                    Timber.tag(castHttpLogTag).w("Cast HTTP server idle with no session; stopping.")
                    stopSelf()
                    return@launch
                }
            }
        }
    }
```

**5f.** Replace the body of `respondWhenTranscodeCompletes` so it suspends instead of blocking an IO thread:

```kotlin
    private suspend fun ApplicationCall.respondWhenTranscodeCompletes(
        entry: TranscodeEntry,
        contentType: ContentType,
        rangeHeader: String?
    ) {
        val completed = withTimeoutOrNull(TimeUnit.MINUTES.toMillis(TRANSCODE_RANGE_WAIT_TIMEOUT_MINUTES)) {
            while (!entry.done && !entry.failed) {
                delay(100)
            }
            true
        } ?: false
        if (completed && entry.done && !entry.failed && entry.tempFile.exists()) {
            respondWithAudioStream(
                contentType = contentType,
                fileSize = entry.tempFile.length(),
                rangeHeader = rangeHeader
            ) { FileInputStream(entry.tempFile) }
        } else {
            respond(HttpStatusCode.ServiceUnavailable, "Transcode not ready")
        }
    }
```

Leave `streamFromGrowingFile`, `isNonInitialRangeRequest`, `cleanupAllTranscodeTempFiles`, `evictTranscodeCache` and the `HEAD /song` handler's `transcodeCache[song.id]` lookups unchanged (the map is still keyed by song id; only the file name changed).

---

### STEP 6 — `MediaFileHttpServerService.kt`: service lifecycle and information leaks (F7, F9)

**6a.** In `onStartCommand`, replace:

```kotlin
            ACTION_START_SERVER, null -> {
```

with:

```kotlin
            null -> {
                // Recreated by the system without a Cast session: there is nothing to serve.
                castDeviceIpHint = null
                stopSelf()
            }
            ACTION_START_SERVER -> {
```

and change the final `return START_STICKY` of `onStartCommand` to `return START_NOT_STICKY`.

**6b.** In the `GET /song/{songId}` handler's `catch`, replace:

```kotlin
                                    call.respond(HttpStatusCode.InternalServerError, "Error serving file: ${e.message}")
```

with:

```kotlin
                                    call.respond(HttpStatusCode.InternalServerError, "Internal error")
```

**6c.** In this file only, for every `Timber...w(...)` and `Timber...e(...)` call inside the `/song` and `/art` route handlers, remove `song.contentUriString`, `song.path` and `uri` from the message and its arguments. Keep `song.id` / `songId`, MIME type and the exception. Do not change `.d(...)` / `.i(...)` calls (they are dropped in release builds). Example — replace:

```kotlin
                                        Timber.tag(castHttpLogTag).w(
                                            "GET /song failed to resolve source. songId=%s uri=%s path=%s mime=%s",
                                            song.id,
                                            song.contentUriString,
                                            song.path,
                                            song.mimeType
                                        )
```

with:

```kotlin
                                        Timber.tag(castHttpLogTag).w(
                                            "GET /song failed to resolve source. songId=%s mime=%s",
                                            song.id,
                                            song.mimeType
                                        )
```

**6d.** In `onDestroy`, directly before `cleanupAllTranscodeTempFiles()`, add:

```kotlin
        idleWatchdogJob?.cancel()
        idleWatchdogJob = null
```

---

### STEP 7 — `CastTransferStateHolder.kt`: rotate and clear the token with the session (F3)

**7a.** In `transferPlayback`, find the first call (the one followed by `val preflightSong = currentQueue.getOrNull(safeStartIndex)`):

```kotlin
            val accessPolicy = MediaFileHttpServerService.configureCastSessionAccess(
                allowedSongIds = currentQueue.map(Song::id),
                castDeviceIpHint = castDeviceIpHint
            )
```

Add `rotateToken = true` so every new or resumed Cast session gets a fresh token:

```kotlin
            val accessPolicy = MediaFileHttpServerService.configureCastSessionAccess(
                allowedSongIds = currentQueue.map(Song::id),
                castDeviceIpHint = castDeviceIpHint,
                rotateToken = true
            )
```

Do **not** add `rotateToken` to the other two call sites (the buffering reload near `"reload_current"` and `playRemoteQueue`): they run inside a live session whose queued URLs must keep working.

**7b.** In `stopServerAndTransferBack`, find:

```kotlin
        castStateHolder.setCastPlayer(null)
        castStateHolder.setCastSession(null)
        castStateHolder.setRemotePlaybackActive(false)
```

Add directly below it:

```kotlin
        // The session is over: revoke its token even when the service stays up for a device switch.
        MediaFileHttpServerService.clearCastSessionAccess()
```

---

### STEP 8 — Tests: `CastSessionSecurityTest.kt`

Append these tests inside the class. Do not modify the existing five tests.

```kotlin
    @Test
    fun `client in server subnet is allowed and off-subnet client is rejected`() {
        val policy = CastSessionSecurity.buildAccessPolicy(
            existingToken = "t",
            allowedSongIds = listOf("1"),
            castDeviceIpHint = "192.168.1.50",
            serverOwnIp = "192.168.1.10",
            serverPrefixLength = 24
        )

        assertTrue(CastSessionSecurity.isAuthorizedClientAddress("192.168.1.50", policy))
        assertTrue(CastSessionSecurity.isAuthorizedClientAddress("192.168.1.77", policy))
        assertTrue(CastSessionSecurity.isAuthorizedClientAddress("::ffff:192.168.1.77", policy))
        assertTrue(CastSessionSecurity.isAuthorizedClientAddress("127.0.0.1", policy))
        assertFalse(CastSessionSecurity.isAuthorizedClientAddress("192.168.2.77", policy))
        assertFalse(CastSessionSecurity.isAuthorizedClientAddress("10.0.0.5", policy))
        assertFalse(CastSessionSecurity.isAuthorizedClientAddress(null, policy))
    }

    @Test
    fun `hinted cast device outside the server subnet is still allowed`() {
        val policy = CastSessionSecurity.buildAccessPolicy(
            existingToken = "t",
            allowedSongIds = listOf("1"),
            castDeviceIpHint = "10.0.5.20",
            serverOwnIp = "192.168.1.10",
            serverPrefixLength = 24
        )

        assertTrue(CastSessionSecurity.isAuthorizedClientAddress("10.0.5.20", policy))
        assertFalse(CastSessionSecurity.isAuthorizedClientAddress("10.0.5.21", policy))
    }

    @Test
    fun `rotateToken replaces the existing token`() {
        val policy = CastSessionSecurity.buildAccessPolicy(
            existingToken = "existing-token",
            allowedSongIds = listOf("1"),
            castDeviceIpHint = null,
            rotateToken = true
        )

        assertNotNull(policy.authToken)
        assertFalse(policy.authToken == "existing-token")
        assertTrue(policy.authToken!!.length >= 32)
    }

    @Test
    fun `isValidToken rejects missing blank and wrong tokens`() {
        val policy = CastAccessPolicy.EMPTY.copy(authToken = "token-123")

        assertTrue(CastSessionSecurity.isValidToken("token-123", policy))
        assertFalse(CastSessionSecurity.isValidToken("token-124", policy))
        assertFalse(CastSessionSecurity.isValidToken("", policy))
        assertFalse(CastSessionSecurity.isValidToken(null, policy))
        assertFalse(CastSessionSecurity.isValidToken("token-123", CastAccessPolicy.EMPTY))
    }

    @Test
    fun `isSameIpv4Subnet handles prefixes and malformed input`() {
        assertTrue(CastSessionSecurity.isSameIpv4Subnet("192.168.1.10", "192.168.1.200", 24))
        assertFalse(CastSessionSecurity.isSameIpv4Subnet("192.168.1.10", "192.168.2.10", 24))
        assertTrue(CastSessionSecurity.isSameIpv4Subnet("192.168.1.10", "192.168.2.10", 16))
        assertFalse(CastSessionSecurity.isSameIpv4Subnet("192.168.1.10", "192.168.1.10", 0))
        assertFalse(CastSessionSecurity.isSameIpv4Subnet("192.168.1.999", "192.168.1.10", 24))
        assertFalse(CastSessionSecurity.isSameIpv4Subnet("chromecast.lan", "192.168.1.10", 24))
    }

    @Test
    fun `transcodeCacheFileName never contains path separators`() {
        val name = CastSessionSecurity.transcodeCacheFileName("../../abc/123")

        assertTrue(name.startsWith(CastSessionSecurity.TRANSCODE_FILE_PREFIX))
        assertTrue(name.endsWith(".aac"))
        assertFalse(name.contains("/"))
        assertFalse(name.contains(".."))
        assertEquals(name, CastSessionSecurity.transcodeCacheFileName("../../abc/123"))
    }
```

---

# PART B — BLOCK CLEARTEXT APP-WIDE AND REMOVE NAVIDROME/JELLYFIN SIGN-IN

### STEP 9 — Make the app read its own Cast artwork through loopback (F14)

Do this step **before** Step 10. During remote playback `CastSyncCoordinator` reads the artwork URL back from the Cast media status. That URL is `http://<lan-ip>:<port>/art/<id>?v=..&auth=..`, and `MusicService` downloads it with `HttpURLConnection` for widgets. After Step 10, cleartext to a LAN IP is blocked, so the app must use `127.0.0.1` (the loopback connector added in Step 3).

**9a.** In `CastSessionSecurity.kt`, add inside the object (next to `redactAuthToken`):

```kotlin
    /**
     * Rewrites a URL that points at this device's own Cast server so the app reaches it
     * through loopback. Any other URL is returned unchanged.
     */
    fun toLoopbackUrlIfOwnServer(url: String, serverHostAddress: String?): String {
        val ownHost = serverHostAddress?.trim()?.takeIf { it.isNotEmpty() } ?: return url
        val parsed = url.toHttpUrlOrNull() ?: return url
        if (parsed.scheme != "http" || !parsed.host.equals(ownHost, ignoreCase = true)) return url
        return parsed.newBuilder().host("127.0.0.1").build().toString()
    }
```

**9b.** In `CastSyncCoordinator.kt`, add imports:

```kotlin
import com.theveloper.pixelplay.data.service.http.CastSessionSecurity
import com.theveloper.pixelplay.data.service.http.MediaFileHttpServerService
```

and replace:

```kotlin
        val imageUri = metadata
                ?.images
                ?.firstOrNull()
                ?.url
                ?.toString()
                ?.takeIf { it.isNotBlank() }?.toUri()
```

with:

```kotlin
        val imageUri = metadata
                ?.images
                ?.firstOrNull()
                ?.url
                ?.toString()
                ?.takeIf { it.isNotBlank() }
                // The app must reach its own Cast server through loopback: cleartext HTTP
                // to LAN addresses is blocked by network_security_config.xml.
                ?.let { url ->
                    CastSessionSecurity.toLoopbackUrlIfOwnServer(
                        url = url,
                        serverHostAddress = MediaFileHttpServerService.serverHostAddress
                    )
                }
                ?.toUri()
```

**9c.** Append this test to `CastSessionSecurityTest.kt`:

```kotlin
    @Test
    fun `toLoopbackUrlIfOwnServer rewrites only this device's cast server`() {
        val own = "http://192.168.1.10:8080/art/5?v=abc&auth=secret"
        val remote = "https://example.com/cover.jpg"

        assertEquals(
            "http://127.0.0.1:8080/art/5?v=abc&auth=secret",
            CastSessionSecurity.toLoopbackUrlIfOwnServer(own, "192.168.1.10")
        )
        assertEquals(own, CastSessionSecurity.toLoopbackUrlIfOwnServer(own, "192.168.1.11"))
        assertEquals(own, CastSessionSecurity.toLoopbackUrlIfOwnServer(own, null))
        assertEquals(remote, CastSessionSecurity.toLoopbackUrlIfOwnServer(remote, "192.168.1.10"))
        assertEquals(
            "content://media/1",
            CastSessionSecurity.toLoopbackUrlIfOwnServer("content://media/1", "192.168.1.10")
        )
    }
```

---

### STEP 10 — `network_security_config.xml`: deny cleartext by default (F12)

Replace the **entire** content of `app/src/main/res/xml/network_security_config.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- Cleartext HTTP is denied by default. -->
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>

    <!-- In-app loopback servers: Telegram / Google Drive / cloud stream proxies,
         and the Cast server preflight and artwork read-back. -->
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">127.0.0.1</domain>
        <domain includeSubdomains="false">localhost</domain>
    </domain-config>

    <!-- Netease and QQ Music CDNs still hand out http:// stream and image URLs.
         Mirrors allowedHostSuffixes in NeteaseStreamProxy and QqMusicStreamProxy. -->
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">126.net</domain>
        <domain includeSubdomains="true">163.com</domain>
        <domain includeSubdomains="true">qq.com</domain>
    </domain-config>
</network-security-config>
```

Do not change `android:usesCleartextTraffic="false"` or `android:networkSecurityConfig` in the manifest; they are already correct.

**Expected behaviour changes — do not try to "fix" these:**
- Navidrome/Jellyfin accounts whose server URL is `http://` stop syncing and streaming. `https://` servers are unaffected.
- A custom AI provider whose base URL is `http://` (for example a LAN Ollama server) stops working. `https://` base URLs are unaffected.
- The Cast receiver still downloads from the phone over HTTP. That is inbound traffic to the embedded server and is not governed by this file.

---

### STEP 11 — Remove Navidrome and Jellyfin sign-in (F13)

Sign-in only. Re-read hard constraint 9 before starting.

A separate prompt, `REMOVE_NAVIDROME_JELLYFIN_PROMPT.md`, removes the whole integration and is meant to run **after** this one. If it has already been applied, the files and blocks below no longer exist: mark Step 11 `SKIPPED (already removed)` and continue.

**11a. Delete these four files:**

```
app/src/main/java/com/theveloper/pixelplay/presentation/navidrome/auth/NavidromeLoginActivity.kt
app/src/main/java/com/theveloper/pixelplay/presentation/navidrome/auth/NavidromeLoginViewModel.kt
app/src/main/java/com/theveloper/pixelplay/presentation/jellyfin/auth/JellyfinLoginActivity.kt
app/src/main/java/com/theveloper/pixelplay/presentation/jellyfin/auth/JellyfinLoginViewModel.kt
```

**11b. `AndroidManifest.xml`** — delete exactly these two blocks (and the blank line after each):

```xml
        <activity
            android:name=".presentation.navidrome.auth.NavidromeLoginActivity"
            android:exported="false"
            android:label="Navidrome Login"
            android:theme="@style/Theme.PixelPlay"
            android:windowSoftInputMode="adjustResize" />

        <activity
            android:name=".presentation.jellyfin.auth.JellyfinLoginActivity"
            android:exported="false"
            android:label="Jellyfin Login"
            android:theme="@style/Theme.PixelPlay"
            android:windowSoftInputMode="adjustResize" />
```

**11c. `StreamingProviderSheet.kt`** — delete these two imports:

```kotlin
import com.theveloper.pixelplay.presentation.jellyfin.auth.JellyfinLoginActivity
import com.theveloper.pixelplay.presentation.navidrome.auth.NavidromeLoginActivity
```

Then replace the two `ProviderRow(...)` calls whose titles are `"Subsonic"` and `"Jellyfin"` (they sit between the `"Google Drive"` row and the `"Netease Music"` row) with the block below. Keep the function's parameters unchanged so `HomeScreen.kt` needs no edit. The colour and string literals are pre-existing; copy them verbatim and do not refactor them.

```kotlin
                    // Sign-in for Subsonic/Navidrome and Jellyfin was removed.
                    // These rows only appear for accounts that are already connected.
                    if (isNavidromeLoggedIn) {
                        ProviderRow(
                            iconPainter = painterResource(R.drawable.ic_navidrome_md3),
                            iconTint = Color(0xFFE8A54B),
                            title = "Subsonic",
                            subtitle = "Connected · Navidrome/Airsonic",
                            shape = providerSegmentItemShape,
                            isConnected = true,
                            onClick = {
                                onNavigateToNavidromeDashboard()
                                onDismissRequest()
                            }
                        )
                    }

                    if (isJellyfinLoggedIn) {
                        ProviderRow(
                            iconPainter = painterResource(R.drawable.ic_jellyfin),
                            iconTint = Color(0xFF00A4DC),
                            title = "Jellyfin",
                            subtitle = "Connected",
                            shape = providerSegmentItemShape,
                            isConnected = true,
                            onClick = {
                                onNavigateToJellyfinDashboard()
                                onDismissRequest()
                            }
                        )
                    }
```

**11d. `AccountsScreen.kt`** — delete these two imports:

```kotlin
import com.theveloper.pixelplay.presentation.jellyfin.auth.JellyfinLoginActivity
import com.theveloper.pixelplay.presentation.navidrome.auth.NavidromeLoginActivity
```

Then, in `private fun openService(...)`, replace the `NAVIDROME` and `JELLYFIN` branches:

```kotlin
        ExternalServiceAccount.NAVIDROME -> {
            if (preferNeteaseDashboard) {
                onOpenNavidromeDashboard()
            } else {
                safeStartActivity(
                    context = context,
                    intent = Intent(context, NavidromeLoginActivity::class.java)
                )
            }
        }
        ExternalServiceAccount.JELLYFIN -> {
            if (preferNeteaseDashboard) {
                onOpenJellyfinDashboard()
            } else {
                safeStartActivity(
                    context = context,
                    intent = Intent(context, JellyfinLoginActivity::class.java)
                )
            }
        }
```

with:

```kotlin
        // Sign-in was removed; only an already-connected account can be opened.
        ExternalServiceAccount.NAVIDROME -> {
            if (preferNeteaseDashboard) onOpenNavidromeDashboard()
        }
        ExternalServiceAccount.JELLYFIN -> {
            if (preferNeteaseDashboard) onOpenJellyfinDashboard()
        }
```

Leave every other `ExternalServiceAccount.NAVIDROME` / `JELLYFIN` branch in this file (icons, palette, title) unchanged: they still render accounts that are already connected.

**11e. `AccountsViewModel.kt`** — in the `disconnectedServices = buildList { ... }` block, delete these two lines so the services are no longer offered for connection:

```kotlin
            if (!navidromeConnected) add(ExternalServiceAccount.NAVIDROME)
            if (!jellyfinConnected) add(ExternalServiceAccount.JELLYFIN)
```

Keep the `ExternalServiceAccount` enum values, the `navidromeStateFlow` / `jellyfinStateFlow`, the connected-account rows and the `logout` branches.

---

## 5. VERIFICATION (run all of it, report real output)

1. Full debug build (also validates the manifest, resources and Hilt graph):
   ```
   ./gradlew :app:assembleDebug
   ```
2. Unit tests (all 12 must pass):
   ```
   ./gradlew :app:testDebugUnitTest --tests "com.theveloper.pixelplay.data.service.http.CastSessionSecurityTest"
   ```
3. Static checks on `MediaFileHttpServerService.kt`:
   - `"0.0.0.0"` appears **0** times.
   - `START_STICKY` appears **0** times.
   - `origin.remoteHost` appears **0** times.
   - `latch.await` appears **0** times.
   - `"Error serving file"` appears **0** times.
4. Static checks for Part B:
   - Searching `app/src/main` for `NavidromeLoginActivity`, `JellyfinLoginActivity`, `NavidromeLoginViewModel`, `JellyfinLoginViewModel` returns **0** matches.
   - In `network_security_config.xml`, `<base-config cleartextTrafficPermitted="false">` is present and `cleartextTrafficPermitted="true"` appears exactly **2** times (the two `domain-config` blocks).
   - `git status` shows no change under `data/navidrome`, `data/jellyfin`, `data/database`, or `app/schemas`.
5. Manual on-device test (state clearly if you could not run it):
   - Cast an MP3, then a FLAC (transcode path). Playback starts, seek works, skipping to the next track works.
   - While casting, the home-screen widget shows the album art of the casting track (Step 9).
   - From a second device on the same Wi-Fi, `curl -I "http://<phone-ip>:8080/song/1"` returns `401`.
   - With mobile data on, the phone's cellular IP on port `8080` refuses the connection.
   - Disconnect Cast: the "cast server" notification disappears.
   - If playback fails with `403` in logcat tag `CastHttpServer`, report the logged `client=` value. That means `remoteAddress` is not an IP literal on this engine and STEP 4 needs revisiting.
   - A Telegram or Netease track still plays locally (loopback proxies and the `126.net` exception).
   - The cloud-streaming sheet and the Accounts screen no longer offer Subsonic/Navidrome or Jellyfin when no such account is connected.

## 6. REPORT FORMAT

Reply with: (1) the list of files changed, (2) for each step `DONE` / `SKIPPED (reason)`, (3) the compile and test output, (4) anything you could not verify. Do not claim the manual test passed unless you ran it.
