package com.theveloper.pixelplay.data.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.annotation.VisibleForTesting
import com.theveloper.pixelplay.utils.AlbumArtUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

class SharedArtworkContentProvider : ContentProvider() {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val providerScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_IO_WORKERS)
    )

    private val inFlightResolves = ConcurrentHashMap<Long, Deferred<File?>>()

    override fun onCreate(): Boolean = true

    override fun shutdown() {
        inFlightResolves.clear()
        providerScope.cancel()
        super.shutdown()
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? {
        val appContext = context?.applicationContext ?: return null
        return resolveMimeType(uri, appContext.packageName)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val appContext = context?.applicationContext
            ?: throw FileNotFoundException("Provider context unavailable")
        val songId = validateOpenRequest(
            uri = uri,
            mode = mode,
            packageName = appContext.packageName,
            isCallerAuthorized = { isCallerAuthorized(appContext, uri) }
        )

        val pipe = ParcelFileDescriptor.createReliablePipe()
        val readSide = pipe[0]
        val writeSide = pipe[1]

        providerScope.launch {
            streamArtworkToPipe(
                appContext = appContext,
                songId = songId,
                writeSide = writeSide
            )
        }

        return readSide
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor {
        val fileDescriptor = openFile(uri, mode)
        return AssetFileDescriptor(fileDescriptor, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    private suspend fun streamArtworkToPipe(
        appContext: Context,
        songId: Long,
        writeSide: ParcelFileDescriptor
    ) {
        try {
            val file = resolveOrJoinArtworkFile(appContext, songId)
                ?.takeIf { it.exists() && it.isFile && it.canRead() }
            if (file == null) {
                closePipeWithError(writeSide, "No artwork found for songId=$songId")
                return
            }
            ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { output ->
                FileInputStream(file).use { input ->
                    input.copyTo(output, bufferSize = STREAM_BUFFER_BYTES)
                }
                output.flush()
            }
        } catch (ce: CancellationException) {
            closePipeWithError(writeSide, "Artwork request cancelled for songId=$songId")
            throw ce
        } catch (e: Exception) {
            Timber.w(e, "Failed streaming shared artwork for songId=%d", songId)
            closePipeWithError(writeSide, e.message ?: "Failed streaming artwork")
        }
    }

    private suspend fun resolveOrJoinArtworkFile(appContext: Context, songId: Long): File? {
        val deferred = inFlightResolves.computeIfAbsent(songId) {
            providerScope.async(start = CoroutineStart.LAZY) {
                withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                    AlbumArtUtils.ensureAlbumArtCachedFile(
                        appContext = appContext,
                        songId = songId
                    )?.takeIf { it.exists() && it.isFile && it.canRead() }
                }
            }
        }
        deferred.invokeOnCompletion {
            inFlightResolves.remove(songId, deferred)
        }
        deferred.start()
        return deferred.await()
    }

    private fun closePipeWithError(writeSide: ParcelFileDescriptor, message: String) {
        try {
            writeSide.closeWithError(message)
        } catch (_: Exception) {
            try {
                writeSide.close()
            } catch (_: Exception) {
                // Best-effort cleanup
            }
        }
    }

    companion object {
        private const val AUTHORITY_SUFFIX = ".artwork"
        private const val PATH_SONG = "song"
        internal const val DEFAULT_CONTENT_TYPE = "image/jpeg"
        private const val MAX_CONCURRENT_IO_WORKERS = 4
        private const val RESOLVE_TIMEOUT_MS = 8_000L
        private const val STREAM_BUFFER_BYTES = 8 * 1024

        fun authority(packageName: String): String = packageName + AUTHORITY_SUFFIX

        fun buildSongUri(
            context: Context,
            songId: Long,
            cacheBustToken: String? = null
        ): Uri = buildSongUri(context.packageName, songId, cacheBustToken)

        internal fun buildSongUri(
            packageName: String,
            songId: Long,
            cacheBustToken: String? = null
        ): Uri {
            return Uri.parse(buildSongUriString(packageName, songId, cacheBustToken))
        }

        internal fun buildSongUriString(
            packageName: String,
            songId: Long,
            cacheBustToken: String? = null
        ): String {
            val baseUri = "content://${authority(packageName)}/$PATH_SONG/$songId"
            return cacheBustToken
                ?.takeIf { it.isNotBlank() }
                ?.let { "$baseUri?t=$it" }
                ?: baseUri
        }

        internal fun resolveMimeType(uri: Uri, packageName: String): String? {
            return if (parseSongId(uri, packageName) != null) {
                DEFAULT_CONTENT_TYPE
            } else {
                null
            }
        }

        internal fun parseSongId(uri: Uri, packageName: String? = null): Long? {
            return parseSongId(uri.toString(), packageName)
        }

        internal fun parseSongId(uriString: String, packageName: String? = null): Long? {
            val expectedPrefix = packageName
                ?.let(::authority)
                ?.let { "content://$it/$PATH_SONG/" }

            if (expectedPrefix != null && !uriString.startsWith(expectedPrefix)) {
                return null
            }

            val basePrefix = expectedPrefix ?: run {
                val authoritySeparator = "://"
                val schemeSplit = uriString.indexOf(authoritySeparator)
                if (schemeSplit < 0) return null
                val scheme = uriString.substring(0, schemeSplit).lowercase()
                if (scheme != "content") return null
                val pathStart = uriString.indexOf('/', schemeSplit + authoritySeparator.length)
                if (pathStart < 0) return null
                val pathPrefix = uriString.substring(pathStart)
                if (!pathPrefix.startsWith("/$PATH_SONG/")) return null
                uriString.substring(0, pathStart) + "/$PATH_SONG/"
            }

            val remainder = uriString
                .removePrefix(basePrefix)
                .substringBefore('?')
                .substringBefore('#')

            if (remainder.isBlank() || '/' in remainder) {
                return null
            }
            return remainder.toLongOrNull()?.takeIf { it > 0L }
        }

        @VisibleForTesting
        internal fun isCallerAuthorized(
            appContext: Context,
            uri: Uri,
            callingUid: Int = Binder.getCallingUid(),
            myUid: Int = Process.myUid()
        ): Boolean {
            if (callingUid == myUid) {
                return true
            }
            return appContext.checkCallingUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED
        }

        @VisibleForTesting
        internal fun validateOpenRequest(
            uri: Uri,
            mode: String,
            packageName: String,
            isCallerAuthorized: () -> Boolean
        ): Long {
            if (mode != "r") {
                throw FileNotFoundException("Shared artwork provider is read-only")
            }
            if (!isCallerAuthorized()) {
                throw SecurityException("Caller does not hold read URI grant for $uri")
            }
            return parseSongId(uri, packageName)
                ?: throw FileNotFoundException("No valid songId found for uri=$uri")
        }
    }
}
