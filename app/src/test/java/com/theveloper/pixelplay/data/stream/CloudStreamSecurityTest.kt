package com.theveloper.pixelplay.data.stream

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress

class CloudStreamSecurityTest {

    @Test
    fun `validateRangeHeader accepts standard ranges`() {
        val validation = CloudStreamSecurity.validateRangeHeader("bytes=0-1023")

        assertTrue(validation.isValid)
        assertTrue(validation.normalizedHeader == "bytes=0-1023")
        assertTrue(validation.startInclusive == 0L)
        assertTrue(validation.endInclusive == 1023L)
        assertFalse(validation.isSuffixRange)
    }

    @Test
    fun `validateRangeHeader accepts suffix ranges`() {
        val validation = CloudStreamSecurity.validateRangeHeader("bytes=-4096")

        assertTrue(validation.isValid)
        assertTrue(validation.isSuffixRange)
        assertTrue(validation.startInclusive == null)
        assertTrue(validation.endInclusive == 4096L)
    }

    @Test
    fun `validateRangeHeader rejects invalid formats`() {
        assertFalse(CloudStreamSecurity.validateRangeHeader("bytes=1-2,4-5").isValid)
        assertFalse(CloudStreamSecurity.validateRangeHeader("bytes=10-2").isValid)
        assertFalse(CloudStreamSecurity.validateRangeHeader("bytes=-").isValid)
    }

    @Test
    fun `isSafeRemoteStreamUrl blocks localhost`() {
        assertFalse(
            CloudStreamSecurity.isSafeRemoteStreamUrl(
                "http://127.0.0.1:8000/audio.mp3",
                allowedHostSuffixes = setOf("127.0.0.1"),
                allowHttpForAllowedHosts = true
            )
        )
    }

    @Test
    fun `isSafeRemoteStreamUrl enforces host allowlist and scheme rules`() {
        assertTrue(
            CloudStreamSecurity.isSafeRemoteStreamUrl(
                "https://m7.music.126.net/file.mp3",
                allowedHostSuffixes = setOf("music.126.net")
            )
        )
        assertTrue(
            CloudStreamSecurity.isSafeRemoteStreamUrl(
                "http://m7.music.126.net/file.mp3",
                allowedHostSuffixes = setOf("music.126.net"),
                allowHttpForAllowedHosts = true
            )
        )
        assertFalse(
            CloudStreamSecurity.isSafeRemoteStreamUrl(
                "http://evil.example.com/file.mp3",
                allowedHostSuffixes = setOf("music.126.net"),
                allowHttpForAllowedHosts = true
            )
        )
    }

    @Test
    fun `isLocalOrPrivateHost accepts local network hosts`() {
        assertTrue(CloudStreamSecurity.isLocalOrPrivateHost("localhost"))
        assertTrue(CloudStreamSecurity.isLocalOrPrivateHost("127.0.0.1"))
        assertTrue(CloudStreamSecurity.isLocalOrPrivateHost("192.168.1.20"))
        assertTrue(CloudStreamSecurity.isLocalOrPrivateHost("10.0.0.5"))
        assertTrue(CloudStreamSecurity.isLocalOrPrivateHost("172.20.0.3"))
        // Carrier-grade NAT (Tailscale and similar VPNs)
        assertTrue(CloudStreamSecurity.isLocalOrPrivateHost("100.101.102.103"))
        // Single-label LAN hostnames
        assertTrue(CloudStreamSecurity.isLocalOrPrivateHost("mynas"))
        // Local-only DNS suffixes
        assertTrue(CloudStreamSecurity.isLocalOrPrivateHost("nas.local"))
        assertTrue(CloudStreamSecurity.isLocalOrPrivateHost("nas.lan"))
        assertTrue(CloudStreamSecurity.isLocalOrPrivateHost("nas.home"))
        assertTrue(CloudStreamSecurity.isLocalOrPrivateHost("nas.internal"))
        assertTrue(CloudStreamSecurity.isLocalOrPrivateHost("nas.home.arpa"))
        // IPv6 loopback, unique-local, link-local
        assertTrue(CloudStreamSecurity.isLocalOrPrivateHost("::1"))
        assertTrue(CloudStreamSecurity.isLocalOrPrivateHost("[::1]"))
        assertTrue(CloudStreamSecurity.isLocalOrPrivateHost("fd00::1"))
        assertTrue(CloudStreamSecurity.isLocalOrPrivateHost("fe80::abcd"))
    }

    @Test
    fun `isLocalOrPrivateHost rejects public hosts`() {
        assertFalse(CloudStreamSecurity.isLocalOrPrivateHost("music.example.com"))
        assertFalse(CloudStreamSecurity.isLocalOrPrivateHost("8.8.8.8"))
        assertFalse(CloudStreamSecurity.isLocalOrPrivateHost("100.32.1.2"))
        assertFalse(CloudStreamSecurity.isLocalOrPrivateHost("2001:db8::1"))
        assertFalse(CloudStreamSecurity.isLocalOrPrivateHost("notlocal.example.org"))
        assertFalse(CloudStreamSecurity.isLocalOrPrivateHost(""))
    }

    @Test
    fun `isSupportedAudioContentType only accepts audio-safe types`() {
        assertTrue(CloudStreamSecurity.isSupportedAudioContentType("audio/mpeg"))
        assertTrue(CloudStreamSecurity.isSupportedAudioContentType("application/octet-stream"))
        assertFalse(CloudStreamSecurity.isSupportedAudioContentType("text/html"))
    }

    @Test
    fun `isSafeRemoteStreamUrl blocks IPv6 ULA link-local CGNAT and private hosts for non-self-hosted URLs`() {
        assertFalse(CloudStreamSecurity.isSafeRemoteStreamUrl("https://[fd00::1]/audio.mp3"))
        assertFalse(CloudStreamSecurity.isSafeRemoteStreamUrl("https://[fc12::5]/audio.mp3"))
        assertFalse(CloudStreamSecurity.isSafeRemoteStreamUrl("https://[fe80::1]/audio.mp3"))
        assertFalse(CloudStreamSecurity.isSafeRemoteStreamUrl("https://100.64.0.1/audio.mp3"))
        assertFalse(CloudStreamSecurity.isSafeRemoteStreamUrl("https://192.168.1.50/audio.mp3"))
        assertFalse(CloudStreamSecurity.isSafeRemoteStreamUrl("https://metadata.google.internal/computeMetadata/v1/"))
        assertFalse(CloudStreamSecurity.isSafeRemoteStreamUrl("https://169.254.169.254/latest/meta-data/"))
    }

    @Test
    fun `isSafeRemoteStreamUrl rejects path-trick SSRF bypasses to stream_view or universal`() {
        // An unconfigured arbitrary IP or evil domain must NOT bypass SSRF checks simply by using stream.view
        assertFalse(
            CloudStreamSecurity.isSafeRemoteStreamUrl(
                "https://192.168.1.10/rest/stream.view",
                allowedHostSuffixes = emptySet()
            )
        )
        assertFalse(
            CloudStreamSecurity.isSafeRemoteStreamUrl(
                "http://192.168.1.10/rest/stream.view",
                allowedHostSuffixes = setOf("my-other-server.lan"),
                allowHttpForAllowedHosts = true
            )
        )
        assertFalse(
            CloudStreamSecurity.isSafeRemoteStreamUrl(
                "https://evil.example.com/Audio/123/universal",
                allowedHostSuffixes = setOf("trusted-jellyfin.example.com")
            )
        )
        // Explicitly allowlisted self-hosted Navidrome / Jellyfin servers remain allowed
        assertTrue(
            CloudStreamSecurity.isSafeRemoteStreamUrl(
                "http://192.168.1.10:4533/rest/stream.view?id=42",
                allowedHostSuffixes = setOf("192.168.1.10"),
                allowHttpForAllowedHosts = true
            )
        )
        assertTrue(
            CloudStreamSecurity.isSafeRemoteStreamUrl(
                "http://jellyfin.lan:8096/Audio/item1/universal",
                allowedHostSuffixes = setOf("jellyfin.lan"),
                allowHttpForAllowedHosts = true
            )
        )
    }

    @Test
    fun `resolveAndValidateHost rejects unsafe DNS resolutions`() {
        val publicResolver: (String) -> Array<InetAddress> = {
            arrayOf(InetAddress.getByAddress(byteArrayOf(93, 184.toByte(), 216.toByte(), 34)))
        }
        val loopbackResolver: (String) -> Array<InetAddress> = {
            arrayOf(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
        }
        val metadataResolver: (String) -> Array<InetAddress> = {
            arrayOf(InetAddress.getByAddress(byteArrayOf(169.toByte(), 254.toByte(), 169.toByte(), 254.toByte())))
        }
        val cgnatResolver: (String) -> Array<InetAddress> = {
            arrayOf(InetAddress.getByAddress(byteArrayOf(100, 100, 10, 5)))
        }
        val ulaResolver: (String) -> Array<InetAddress> = {
            arrayOf(InetAddress.getByName("fd12:3456:789a::1"))
        }

        assertTrue(
            CloudStreamSecurity.resolveAndValidateHost(
                "https://cdn.example.com/track.flac",
                resolver = publicResolver
            )
        )
        assertFalse(
            CloudStreamSecurity.resolveAndValidateHost(
                "https://rebind.example.com/track.flac",
                resolver = loopbackResolver
            )
        )
        assertFalse(
            CloudStreamSecurity.resolveAndValidateHost(
                "https://rebind.example.com/track.flac",
                resolver = metadataResolver
            )
        )
        assertFalse(
            CloudStreamSecurity.resolveAndValidateHost(
                "https://rebind.example.com/track.flac",
                resolver = cgnatResolver
            )
        )
        assertFalse(
            CloudStreamSecurity.resolveAndValidateHost(
                "https://rebind.example.com/track.flac",
                resolver = ulaResolver
            )
        )
    }

    @Test
    fun `executeWithValidatedRedirects rejects redirects to loopback metadata and private DNS`() {
        val publicIpResolver: (String) -> Array<InetAddress> = { host ->
            when (host) {
                "evil-rebind.example.com" -> arrayOf(InetAddress.getByAddress(byteArrayOf(10, 0, 0, 5)))
                else -> arrayOf(InetAddress.getByAddress(byteArrayOf(93, 184.toByte(), 216.toByte(), 34)))
            }
        }

        fun clientReturningRedirect(location: String): OkHttpClient {
            val interceptor = Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(302)
                    .message("Found")
                    .header("Location", location)
                    .body(ByteArray(0).toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            return OkHttpClient.Builder().addInterceptor(interceptor).build()
        }

        // Redirect to loopback is blocked before connecting
        val loopbackRedirect = CloudStreamSecurity.executeWithValidatedRedirects(
            client = clientReturningRedirect("http://127.0.0.1:8080/internal"),
            initialUrl = "https://cdn.example.com/track.mp3",
            allowedHostSuffixes = setOf("example.com"),
            dnsResolver = publicIpResolver
        ) { url, _ -> Request.Builder().url(url).build() }
        assertTrue(loopbackRedirect is CloudStreamSecurity.ValidatedRequestResult.Rejected)

        // Redirect to cloud metadata is blocked
        val metadataRedirect = CloudStreamSecurity.executeWithValidatedRedirects(
            client = clientReturningRedirect("http://169.254.169.254/latest/meta-data"),
            initialUrl = "https://cdn.example.com/track.mp3",
            allowedHostSuffixes = setOf("example.com"),
            dnsResolver = publicIpResolver
        ) { url, _ -> Request.Builder().url(url).build() }
        assertTrue(metadataRedirect is CloudStreamSecurity.ValidatedRequestResult.Rejected)

        // Redirect to host resolving to RFC1918 private IP is blocked
        val dnsRebindRedirect = CloudStreamSecurity.executeWithValidatedRedirects(
            client = clientReturningRedirect("https://evil-rebind.example.com/audio.mp3"),
            initialUrl = "https://cdn.example.com/track.mp3",
            allowedHostSuffixes = setOf("example.com"),
            dnsResolver = publicIpResolver
        ) { url, _ -> Request.Builder().url(url).build() }
        assertTrue(dnsRebindRedirect is CloudStreamSecurity.ValidatedRequestResult.Rejected)
    }

    @Test
    fun `executeWithValidatedRedirects allows safe redirect and withholds auth on subsequent hops`() {
        val publicIpResolver: (String) -> Array<InetAddress> = {
            arrayOf(InetAddress.getByAddress(byteArrayOf(93, 184.toByte(), 216.toByte(), 34)))
        }
        val recordedAuthHeaders = mutableListOf<String?>()

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                recordedAuthHeaders.add(req.header("Authorization"))
                if (req.url.host == "api.example.com") {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(302)
                        .message("Found")
                        .header("Location", "https://cdn.example.com/media.flac")
                        .body(ByteArray(0).toResponseBody("text/plain".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Type", "audio/flac")
                        .body("audio".toResponseBody("audio/flac".toMediaType()))
                        .build()
                }
            }
            .build()

        val result = CloudStreamSecurity.executeWithValidatedRedirects(
            client = client,
            initialUrl = "https://api.example.com/files/1",
            allowedHostSuffixes = setOf("example.com"),
            dnsResolver = publicIpResolver
        ) { url, isInitialHop ->
            Request.Builder().url(url).apply {
                if (isInitialHop) header("Authorization", "Bearer secret-token")
            }.build()
        }

        assertTrue(result is CloudStreamSecurity.ValidatedRequestResult.Success)
        (result as CloudStreamSecurity.ValidatedRequestResult.Success).response.close()
        assertEquals(listOf("Bearer secret-token", null), recordedAuthHeaders)
    }
}
