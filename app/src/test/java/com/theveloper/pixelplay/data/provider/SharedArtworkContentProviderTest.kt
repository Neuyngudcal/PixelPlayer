package com.theveloper.pixelplay.data.provider

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.FileNotFoundException

class SharedArtworkContentProviderTest {

    @Test
    fun buildSongUri_usesDedicatedArtworkAuthority() {
        val uri = SharedArtworkContentProvider.buildSongUriString(
            packageName = "com.theveloper.pixelplay",
            songId = 42L
        )

        assertThat(uri).isEqualTo("content://com.theveloper.pixelplay.artwork/song/42")
    }

    @Test
    fun buildSongUri_preservesCacheBustToken() {
        val uri = SharedArtworkContentProvider.buildSongUriString(
            packageName = "com.theveloper.pixelplay",
            songId = 42L,
            cacheBustToken = "1234"
        )

        assertThat(uri)
            .isEqualTo("content://com.theveloper.pixelplay.artwork/song/42?t=1234")
    }

    @Test
    fun parseSongId_rejectsOtherAuthorities() {
        val songId = SharedArtworkContentProvider.parseSongId(
            uriString = "content://example.com.artwork/song/42",
            packageName = "com.theveloper.pixelplay"
        )

        assertThat(songId).isNull()
    }

    @Test
    fun parseSongId_readsSharedArtworkSongUri() {
        val songId = SharedArtworkContentProvider.parseSongId(
            uriString = "content://com.theveloper.pixelplay.artwork/song/42",
            packageName = "com.theveloper.pixelplay"
        )

        assertThat(songId).isEqualTo(42L)
    }

    @Test
    fun parseSongId_rejectsZeroNegativeAndTrailingSegments() {
        val pkg = "com.theveloper.pixelplay"
        assertThat(SharedArtworkContentProvider.parseSongId("content://$pkg.artwork/song/0", pkg)).isNull()
        assertThat(SharedArtworkContentProvider.parseSongId("content://$pkg.artwork/song/-5", pkg)).isNull()
        assertThat(SharedArtworkContentProvider.parseSongId("content://$pkg.artwork/song/42/extra", pkg)).isNull()
        assertThat(SharedArtworkContentProvider.parseSongId("content://$pkg.artwork/song/abc", pkg)).isNull()
    }

    @Test
    fun isCallerAuthorized_allowsSameUidOrGrantedUriPermission_andRejectsUngrantedExternalCaller() {
        val uri = mockk<Uri>()
        val ungrantedContext = mockk<Context>()
        every {
            ungrantedContext.checkCallingUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } returns PackageManager.PERMISSION_DENIED

        assertThat(
            SharedArtworkContentProvider.isCallerAuthorized(
                appContext = ungrantedContext,
                uri = uri,
                callingUid = 10042,
                myUid = 10042
            )
        ).isTrue()

        assertThat(
            SharedArtworkContentProvider.isCallerAuthorized(
                appContext = ungrantedContext,
                uri = uri,
                callingUid = 20001,
                myUid = 10042
            )
        ).isFalse()

        val grantedContext = mockk<Context>()
        every {
            grantedContext.checkCallingUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } returns PackageManager.PERMISSION_GRANTED

        assertThat(
            SharedArtworkContentProvider.isCallerAuthorized(
                appContext = grantedContext,
                uri = uri,
                callingUid = 20001,
                myUid = 10042
            )
        ).isTrue()
    }

    @Test
    fun validateOpenRequest_rejectsUnauthorizedOrWriteMode() {
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://com.theveloper.pixelplay.artwork/song/42"

        assertThrows(SecurityException::class.java) {
            SharedArtworkContentProvider.validateOpenRequest(
                uri = uri,
                mode = "r",
                packageName = "com.theveloper.pixelplay",
                isCallerAuthorized = { false }
            )
        }

        assertThrows(FileNotFoundException::class.java) {
            SharedArtworkContentProvider.validateOpenRequest(
                uri = uri,
                mode = "rw",
                packageName = "com.theveloper.pixelplay",
                isCallerAuthorized = { true }
            )
        }
    }

    @Test
    fun getType_returnsDefaultContentTypeForValidUriOnly() {
        val validUri = mockk<Uri>()
        every { validUri.toString() } returns "content://com.theveloper.pixelplay.artwork/song/42"
        val invalidUri = mockk<Uri>()
        every { invalidUri.toString() } returns "content://com.theveloper.pixelplay.artwork/song/0"

        assertThat(
            SharedArtworkContentProvider.resolveMimeType(validUri, "com.theveloper.pixelplay")
        ).isEqualTo(SharedArtworkContentProvider.DEFAULT_CONTENT_TYPE)
        assertThat(
            SharedArtworkContentProvider.resolveMimeType(invalidUri, "com.theveloper.pixelplay")
        ).isNull()
    }
}
