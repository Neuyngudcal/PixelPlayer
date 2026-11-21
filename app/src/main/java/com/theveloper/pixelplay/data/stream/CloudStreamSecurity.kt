package com.theveloper.pixelplay.data.stream

import io.ktor.http.HttpStatusCode
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Lightweight validation utilities shared by cloud streaming proxies.
 *
 * The checks are intentionally cheap (string/number validation only) so they
 * can run on every request without adding measurable overhead.
 */
object CloudStreamSecurity {
    const val MAX_STREAM_CONTENT_LENGTH_BYTES: Long = 2L * 1024L * 1024L * 1024L

    private const val MAX_RANGE_HEADER_LENGTH = 64
    private const val MAX_RANGE_VALUE_BYTES = 8L * 1024L * 1024L * 1024L

    private val GDRIVE_FILE_ID_REGEX = Regex("^[A-Za-z0-9_-]{10,200}$")
    private val QQMUSIC_SONG_MID_REGEX = Regex("^[A-Za-z0-9_-]{6,50}$")
    private val NAVIDROME_SONG_ID_REGEX = Regex("^[A-Za-z0-9_-]{1,100}$")
    private val JELLYFIN_ITEM_ID_REGEX = Regex("^[A-Za-z0-9]{1,100}$")
    private val FORBIDDEN_HOSTS = setOf("localhost", "127.0.0.1", "0.0.0.0", "::1", "[::1]", "::", "[::]")
    private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
    const val MAX_VALIDATED_REDIRECT_HOPS = 3

    // DNS suffixes that only resolve on a local network: mDNS (.local), common
    // router defaults (.lan, .home), ICANN private-use (.internal), and the
    // RFC 8375 home network domain (.home.arpa).
    private val LOCAL_DNS_SUFFIXES = listOf(".local", ".lan", ".home", ".internal", ".home.arpa")
    private val EXTRA_ALLOWED_AUDIO_TYPES = setOf(
        "application/octet-stream",
        "binary/octet-stream",
        "application/mp4",
        "video/mp4"
    )

    data class RangeHeaderValidation(
        val isValid: Boolean,
        val normalizedHeader: String? = null,
        val startInclusive: Long? = null,
        val endInclusive: Long? = null,
        val isSuffixRange: Boolean = false
    )

    fun validateTelegramFileId(fileId: Int): Boolean = fileId > 0

    fun validateNeteaseSongId(songId: Long): Boolean = songId > 0L

    fun validateGDriveFileId(fileId: String): Boolean = GDRIVE_FILE_ID_REGEX.matches(fileId)

    fun validateQqMusicSongMid(songMid: String): Boolean = QQMUSIC_SONG_MID_REGEX.matches(songMid)

    fun validateNavidromeSongId(songId: String): Boolean = NAVIDROME_SONG_ID_REGEX.matches(songId)

    fun validateJellyfinItemId(itemId: String): Boolean = JELLYFIN_ITEM_ID_REGEX.matches(itemId)

    fun validateRangeHeader(rawHeader: String?): RangeHeaderValidation {
        if (rawHeader.isNullOrBlank()) {
            return RangeHeaderValidation(isValid = true)
        }

        val header = rawHeader.trim()
        if (header.length > MAX_RANGE_HEADER_LENGTH) {
            return RangeHeaderValidation(isValid = false)
        }
        if (!header.startsWith("bytes=") || header.contains(",")) {
            return RangeHeaderValidation(isValid = false)
        }

        val payload = header.removePrefix("bytes=")
        val dashIndex = payload.indexOf('-')
        if (dashIndex <= -1 || payload.indexOf('-', dashIndex + 1) != -1) {
            return RangeHeaderValidation(isValid = false)
        }

        val startPart = payload.substring(0, dashIndex).trim()
        val endPart = payload.substring(dashIndex + 1).trim()

        if (startPart.isEmpty() && endPart.isEmpty()) {
            return RangeHeaderValidation(isValid = false)
        }
        if (startPart.isNotEmpty() && !startPart.all(Char::isDigit)) {
            return RangeHeaderValidation(isValid = false)
        }
        if (endPart.isNotEmpty() && !endPart.all(Char::isDigit)) {
            return RangeHeaderValidation(isValid = false)
        }

        val start = startPart.toLongOrNull()
        val end = endPart.toLongOrNull()

        if (start != null && (start < 0 || start > MAX_RANGE_VALUE_BYTES)) {
            return RangeHeaderValidation(isValid = false)
        }
        if (end != null && (end < 0 || end > MAX_RANGE_VALUE_BYTES)) {
            return RangeHeaderValidation(isValid = false)
        }
        if (start != null && end != null && start > end) {
            return RangeHeaderValidation(isValid = false)
        }

        return RangeHeaderValidation(
            isValid = true,
            normalizedHeader = "bytes=$startPart-$endPart",
            startInclusive = start,
            endInclusive = end,
            isSuffixRange = start == null && end != null
        )
    }

    fun isSupportedAudioContentType(contentTypeHeader: String?): Boolean {
        if (contentTypeHeader.isNullOrBlank()) {
            return true
        }
        val normalized = contentTypeHeader.substringBefore(';').trim().lowercase()
        if (normalized.startsWith("audio/")) {
            return true
        }
        return normalized in EXTRA_ALLOWED_AUDIO_TYPES
    }

    fun isAcceptableContentLength(contentLengthHeader: String?): Boolean {
        if (contentLengthHeader.isNullOrBlank()) {
            return true
        }
        val parsed = contentLengthHeader.toLongOrNull() ?: return false
        return parsed in 0..MAX_STREAM_CONTENT_LENGTH_BYTES
    }

    fun isSafeRemoteStreamUrl(
        url: String,
        allowedHostSuffixes: Set<String> = emptySet(),
        allowHttpForAllowedHosts: Boolean = false
    ): Boolean {
        val httpUrl = url.toHttpUrlOrNull() ?: return false
        if (httpUrl.username.isNotEmpty() || httpUrl.password.isNotEmpty()) return false

        val host = httpUrl.host.lowercase().removePrefix("[").removeSuffix("]")
        if (isUnconditionallyForbiddenHost(host)) return false

        if (allowedHostSuffixes.isNotEmpty() && !hostMatchesAllowedSuffix(host, allowedHostSuffixes)) {
            return false
        }

        val allowSelfHostedLan = isSelfHostedStreamRequest(httpUrl) &&
            allowedHostSuffixes.isNotEmpty() &&
            hostMatchesAllowedSuffix(host, allowedHostSuffixes)

        if (isLocalOrPrivateHost(host) && !allowSelfHostedLan) {
            return false
        }

        return when (httpUrl.scheme.lowercase()) {
            "https" -> true
            "http" -> allowHttpForAllowedHosts &&
                allowedHostSuffixes.isNotEmpty() &&
                hostMatchesAllowedSuffix(host, allowedHostSuffixes)
            else -> false
        }
    }

    internal fun isSelfHostedStreamUrl(
        url: String,
        allowedHostSuffixes: Set<String>
    ): Boolean {
        val httpUrl = url.toHttpUrlOrNull() ?: return false
        val host = httpUrl.host.lowercase().removePrefix("[").removeSuffix("]")
        return isSelfHostedStreamRequest(httpUrl) &&
            allowedHostSuffixes.isNotEmpty() &&
            hostMatchesAllowedSuffix(host, allowedHostSuffixes)
    }

    private fun isSelfHostedStreamRequest(httpUrl: HttpUrl): Boolean {
        val segments = httpUrl.pathSegments.filter { it.isNotEmpty() }
        val isNavidromeStream = segments.lastOrNull() == "stream.view"
        val isJellyfinStream = segments.size >= 3 &&
            segments[segments.size - 3] == "Audio" &&
            segments.last() == "universal"
        return isNavidromeStream || isJellyfinStream
    }

    fun isSafeResolvedAddress(
        address: InetAddress,
        allowPrivateLanForSelfHosted: Boolean = false
    ): Boolean {
        if (address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isMulticastAddress
        ) {
            return false
        }

        val rawBytes = address.address
        val ipv4Bytes: ByteArray? = when {
            address is Inet4Address && rawBytes.size == 4 -> rawBytes
            address is Inet6Address && isIpv4MappedIpv6Bytes(rawBytes) -> rawBytes.copyOfRange(12, 16)
            else -> null
        }

        if (ipv4Bytes != null) {
            val b0 = ipv4Bytes[0].toInt() and 0xFF
            val b1 = ipv4Bytes[1].toInt() and 0xFF
            // Always reject 0.0.0.0/8, 127.0.0.0/8 (loopback), 169.254.0.0/16 (link-local / cloud metadata)
            if (b0 == 0 || b0 == 127 || (b0 == 169 && b1 == 254)) return false
            val isPrivateOrCgnat = b0 == 10 ||
                (b0 == 172 && b1 in 16..31) ||
                (b0 == 192 && b1 == 168) ||
                (b0 == 100 && b1 in 64..127)
            if (isPrivateOrCgnat && !allowPrivateLanForSelfHosted) return false
            return true
        }

        if (rawBytes.size == 16) {
            val b0 = rawBytes[0].toInt() and 0xFF
            val b1 = rawBytes[1].toInt() and 0xFF
            // Link-local fe80::/10
            if (b0 == 0xFE && (b1 and 0xC0) == 0x80) return false
            // Unique local fc00::/7 or Java site-local
            val isPrivateIpv6 = (b0 and 0xFE) == 0xFC || address.isSiteLocalAddress
            if (isPrivateIpv6 && !allowPrivateLanForSelfHosted) return false
            return true
        }

        return false
    }

    fun resolveAndValidateHost(
        url: String,
        allowPrivateLanForSelfHosted: Boolean = false,
        resolver: (String) -> Array<InetAddress> = { InetAddress.getAllByName(it) }
    ): Boolean {
        val httpUrl = url.toHttpUrlOrNull() ?: return false
        val host = httpUrl.host.lowercase().removePrefix("[").removeSuffix("]")
        if (isUnconditionallyForbiddenHost(host)) return false
        val addresses = runCatching { resolver(host) }.getOrNull() ?: return false
        if (addresses.isEmpty()) return false
        return addresses.all { isSafeResolvedAddress(it, allowPrivateLanForSelfHosted) }
    }

    sealed class ValidatedRequestResult {
        data class Success(val response: Response) : ValidatedRequestResult()
        data class Rejected(val reason: String) : ValidatedRequestResult()
    }

    fun executeWithValidatedRedirects(
        client: OkHttpClient,
        initialUrl: String,
        allowedHostSuffixes: Set<String>,
        allowHttpForAllowedHosts: Boolean = false,
        dnsResolver: (String) -> Array<InetAddress> = { InetAddress.getAllByName(it) },
        maxRedirects: Int = MAX_VALIDATED_REDIRECT_HOPS,
        requestBuilder: (url: String, isInitialHop: Boolean) -> Request
    ): ValidatedRequestResult {
        val noRedirectClient = if (client.followRedirects || client.followSslRedirects) {
            client.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        } else {
            client
        }

        var currentUrl = initialUrl
        for (hop in 0..maxRedirects) {
            val allowPrivateLan = isSelfHostedStreamUrl(currentUrl, allowedHostSuffixes)
            if (!isSafeRemoteStreamUrl(currentUrl, allowedHostSuffixes, allowHttpForAllowedHosts)) {
                return ValidatedRequestResult.Rejected("Unsafe stream URL on hop $hop")
            }
            if (!resolveAndValidateHost(currentUrl, allowPrivateLan, dnsResolver)) {
                return ValidatedRequestResult.Rejected("Unsafe DNS resolution on hop $hop")
            }

            val response = noRedirectClient.newCall(requestBuilder(currentUrl, hop == 0)).execute()
            if (response.code !in REDIRECT_STATUS_CODES) {
                return ValidatedRequestResult.Success(response)
            }

            val location = response.header("Location")
            val resolvedNextUrl = if (!location.isNullOrBlank()) {
                response.request.url.resolve(location)?.toString()
            } else {
                null
            }
            response.close()

            if (resolvedNextUrl.isNullOrBlank()) {
                return ValidatedRequestResult.Rejected("Missing or invalid redirect Location")
            }
            currentUrl = resolvedNextUrl
        }

        return ValidatedRequestResult.Rejected("Too many redirects (>$maxRedirects)")
    }

    fun mapUpstreamStatusToProxyStatus(code: Int): HttpStatusCode {
        return when (code) {
            401 -> HttpStatusCode.Unauthorized
            403 -> HttpStatusCode.Forbidden
            404 -> HttpStatusCode.NotFound
            408 -> HttpStatusCode.RequestTimeout
            416 -> HttpStatusCode(416, "Range Not Satisfiable")
            429 -> HttpStatusCode(429, "Too Many Requests")
            in 500..599 -> HttpStatusCode.BadGateway
            else -> HttpStatusCode.BadGateway
        }
    }

    private fun hostMatchesAllowedSuffix(host: String, allowedHostSuffixes: Set<String>): Boolean {
        if (allowedHostSuffixes.isEmpty()) return true
        return allowedHostSuffixes.any { suffix ->
            val normalized = suffix.lowercase()
            host == normalized || host.endsWith(".$normalized")
        }
    }

    /**
     * Returns true when [host] points at a local network or private address,
     * where cleartext HTTP is acceptable for self-hosted media servers
     * (Navidrome/Subsonic, Jellyfin).
     *
     * Covers loopback names, local-only DNS suffixes, single-label LAN
     * hostnames, private and carrier-grade-NAT IPv4 ranges (the latter used by
     * Tailscale-style VPNs), and loopback/link-local/unique-local IPv6 literals.
     */
    internal fun isLocalOrPrivateHost(host: String): Boolean {
        val normalized = host.lowercase().removePrefix("[").removeSuffix("]")
        if (normalized.isEmpty()) return false
        if (normalized == "localhost") return true
        if (LOCAL_DNS_SUFFIXES.any { normalized.endsWith(it) }) return true
        if (isPrivateIpv4Literal(normalized)) return true
        if (isCgnatIpv4Literal(normalized)) return true
        if (normalized.contains(':')) return isPrivateIpv6Literal(normalized)
        // Single-label hostnames have no public DNS meaning; they only resolve
        // via the local resolver (router DNS, hosts file, NetBIOS).
        return !normalized.contains('.')
    }

    internal fun isPrivateIpv4Literal(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false

        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false

        val first = octets[0]
        val second = octets[1]

        return first == 0 ||
            first == 10 ||
            first == 127 ||
            (first == 169 && second == 254) ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168)
    }

    private fun isUnconditionallyForbiddenHost(host: String): Boolean {
        if (host in FORBIDDEN_HOSTS || host == "localhost") return true
        val ipv4Parts = parseIpv4Octets(host)
        if (ipv4Parts != null) {
            val first = ipv4Parts[0]
            val second = ipv4Parts[1]
            // Loopback 127.0.0.0/8, unspecified 0.0.0.0/8, link-local/metadata 169.254.0.0/16
            return first == 0 || first == 127 || (first == 169 && second == 254)
        }
        if (host.contains(':')) {
            return host == "::1" || host == "0:0:0:0:0:0:0:1" || host == "::" ||
                host.startsWith("fe8") || host.startsWith("fe9") ||
                host.startsWith("fea") || host.startsWith("feb") ||
                host.startsWith("::ffff:127.") || host.startsWith("::ffff:0.") ||
                host.startsWith("::ffff:169.254.")
        }
        return false
    }

    private fun isIpv4MappedIpv6Bytes(bytes: ByteArray): Boolean {
        if (bytes.size != 16) return false
        for (i in 0 until 10) {
            if (bytes[i].toInt() != 0) return false
        }
        return bytes[10].toInt() == -1 && bytes[11].toInt() == -1
    }

    private fun parseIpv4Octets(host: String): List<Int>? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val octets = parts.map { it.toIntOrNull() ?: return null }
        if (octets.any { it !in 0..255 }) return null
        return octets
    }

    // 100.64.0.0/10 — carrier-grade NAT space (RFC 6598), assigned to clients
    // by Tailscale and similar overlay VPNs.
    private fun isCgnatIpv4Literal(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false

        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false

        return octets[0] == 100 && octets[1] in 64..127
    }

    // ::1 loopback, fe80::/10 link-local, fc00::/7 unique-local.
    private fun isPrivateIpv6Literal(host: String): Boolean {
        if (host == "::1" || host == "0:0:0:0:0:0:0:1") return true
        if (host.startsWith("fe8") || host.startsWith("fe9") ||
            host.startsWith("fea") || host.startsWith("feb")
        ) {
            return true
        }
        return host.startsWith("fc") || host.startsWith("fd")
    }
}
