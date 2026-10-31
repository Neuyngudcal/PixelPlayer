package com.theveloper.pixelplay.presentation.components.lyrics

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

const val LYRICS_SHARE_MAX_LINES = 5

enum class LyricsShareTemplate(val id: String, val label: String) {
    ARTWORK("artwork", "Ảnh bìa"),
    COLOR("color", "Bảng màu"),
    DARK("dark", "Tối"),
    LIGHT("light", "Sáng")
}

data class ShareableLyricLine(
    val index: Int,
    val primaryText: String,
    val translation: String? = null,
    val romanization: String? = null
)

fun extractShareableLyricLines(lyrics: Lyrics?): List<ShareableLyricLine> {
    if (lyrics == null) return emptyList()
    val synced = lyrics.synced
    if (!synced.isNullOrEmpty()) {
        return synced.mapIndexedNotNull { idx, line ->
            val trimmed = line.line.trim()
            if (trimmed.isEmpty()) null
            else ShareableLyricLine(
                index = idx,
                primaryText = trimmed,
                translation = line.translation?.trim()?.takeIf { it.isNotEmpty() },
                romanization = line.romanization?.trim()?.takeIf { it.isNotEmpty() }
            )
        }
    }
    val plain = lyrics.plain
    if (!plain.isNullOrEmpty()) {
        return plain.mapIndexedNotNull { idx, line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) null
            else ShareableLyricLine(index = idx, primaryText = trimmed)
        }
    }
    return emptyList()
}

/**
 * Port of Mornye's `LyricsShareScreen` (`lib/screens/lyrics_share_screen.dart`).
 * Lets the user pick up to 5 lyric lines, customize the card template (Artwork, Palette Color, Dark, Light),
 * optionally include translation/romanization lines, preview the 9:16 card live,
 * and share a high-DPI 1080x1920 PNG image via Android's share sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsShareCardSheet(
    lyrics: Lyrics?,
    songTitle: String,
    artistName: String,
    artworkUri: Any?,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    initialSelectedLineIndex: Int? = null,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val shareableLines = remember(lyrics) { extractShareableLyricLines(lyrics) }
    val selectedLineIndices = remember(shareableLines, initialSelectedLineIndex) {
        mutableStateListOf<Int>().apply {
            if (initialSelectedLineIndex != null && shareableLines.any { it.index == initialSelectedLineIndex }) {
                add(initialSelectedLineIndex)
            } else if (shareableLines.isNotEmpty()) {
                add(shareableLines.first().index)
            }
        }
    }

    var selectedTemplate by remember { mutableStateOf(LyricsShareTemplate.ARTWORK) }
    var includeSublines by remember { mutableStateOf(true) }
    var isSharing by remember { mutableStateOf(false) }
    var loadedArtworkBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(artworkUri) {
        if (artworkUri == null) {
            loadedArtworkBitmap = null
            return@LaunchedEffect
        }
        loadedArtworkBitmap = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(artworkUri)
                    .allowHardware(false)
                    .size(600)
                    .build()
                val result = context.imageLoader.execute(request)
                (result as? SuccessResult)?.drawable?.let { drawable ->
                    if (drawable is BitmapDrawable) {
                        drawable.bitmap
                    } else {
                        val bmp = Bitmap.createBitmap(
                            drawable.intrinsicWidth.coerceAtLeast(1),
                            drawable.intrinsicHeight.coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                        val c = Canvas(bmp)
                        drawable.setBounds(0, 0, c.width, c.height)
                        drawable.draw(c)
                        bmp
                    }
                }
            }.getOrNull()
        }
    }

    val selectedLines = remember(shareableLines, selectedLineIndices.toList()) {
        shareableLines.filter { it.index in selectedLineIndices }
    }
    val hasAnySublines = remember(shareableLines) {
        shareableLines.any { it.translation != null || it.romanization != null }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp)
                .padding(bottom = 18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close"
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "Chia sẻ thẻ lời bài hát",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Chọn tối đa $LYRICS_SHARE_MAX_LINES câu lời để tạo thẻ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = {
                        if (selectedLines.isEmpty() || isSharing) return@Button
                        isSharing = true
                        scope.launch {
                            try {
                                val file = withContext(Dispatchers.IO) {
                                    val bitmap = renderLyricsShareCardBitmap(
                                        songTitle = songTitle,
                                        artistName = artistName,
                                        selectedLines = selectedLines,
                                        includeSublines = includeSublines,
                                        template = selectedTemplate,
                                        accentColorArgb = accentColor.toArgb(),
                                        artworkBitmap = loadedArtworkBitmap
                                    )
                                    val outFile = File(context.cacheDir, "pixelplay_lyrics_card.png")
                                    FileOutputStream(outFile).use { fos ->
                                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                                        fos.flush()
                                    }
                                    outFile
                                }
                                shareLyricsCardFile(context, file, songTitle, artistName)
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "Không thể xuất thẻ lời bài hát: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } finally {
                                isSharing = false
                            }
                        }
                    },
                    enabled = selectedLines.isNotEmpty() && !isSharing,
                    shape = RoundedCornerShape(50)
                ) {
                    if (isSharing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chia sẻ")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Live 9:16 Card Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(270.dp),
                contentAlignment = Alignment.Center
            ) {
                LyricsCardLivePreview(
                    songTitle = songTitle,
                    artistName = artistName,
                    artworkUri = artworkUri,
                    selectedLines = selectedLines,
                    includeSublines = includeSublines,
                    template = selectedTemplate,
                    accentColor = accentColor,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(9f / 16f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Template selector + optional translation toggle
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(LyricsShareTemplate.entries) { template ->
                    FilterChip(
                        selected = selectedTemplate == template,
                        onClick = { selectedTemplate = template },
                        label = { Text(template.label) },
                        shape = RoundedCornerShape(50)
                    )
                }
                if (hasAnySublines) {
                    item {
                        FilterChip(
                            selected = includeSublines,
                            onClick = { includeSublines = !includeSublines },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Translate,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("Kèm dịch/phiên âm") },
                            shape = RoundedCornerShape(50)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Line counter + Clear button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Chọn câu lời",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (selectedLineIndices.size >= LYRICS_SHARE_MAX_LINES) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        Text(
                            text = "${selectedLineIndices.size}/$LYRICS_SHARE_MAX_LINES",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }

                if (selectedLineIndices.isNotEmpty()) {
                    TextButton(onClick = { selectedLineIndices.clear() }) {
                        Text("Bỏ chọn hết")
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Line picker list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                itemsIndexed(
                    items = shareableLines,
                    key = { _, item -> item.index }
                ) { _, item ->
                    val isSelected = item.index in selectedLineIndices
                    val canSelectMore = selectedLineIndices.size < LYRICS_SHARE_MAX_LINES
                    val rowBg by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        label = "ShareLineRowBg"
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = rowBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isSelected) {
                                    selectedLineIndices.remove(item.index)
                                } else if (canSelectMore) {
                                    selectedLineIndices.add(item.index)
                                } else {
                                    Toast
                                        .makeText(
                                            context,
                                            "Chỉ được chọn tối đa $LYRICS_SHARE_MAX_LINES câu lời",
                                            Toast.LENGTH_SHORT
                                        )
                                        .show()
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else Color.Transparent
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.primaryText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                val sub = item.translation ?: item.romanization
                                if (includeSublines && !sub.isNullOrBlank()) {
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsCardLivePreview(
    songTitle: String,
    artistName: String,
    artworkUri: Any?,
    selectedLines: List<ShareableLyricLine>,
    includeSublines: Boolean,
    template: LyricsShareTemplate,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val isLight = template == LyricsShareTemplate.LIGHT
    val fgColor = if (isLight) Color(0xFF141416) else Color.White
    val subColor = fgColor.copy(alpha = 0.66f)
    val innerCardBg = if (isLight) {
        Color.Black.copy(alpha = 0.05f)
    } else {
        Color.White.copy(alpha = 0.12f)
    }
    val innerCardBorder = if (isLight) {
        Color.Black.copy(alpha = 0.08f)
    } else {
        Color.White.copy(alpha = 0.16f)
    }

    val totalChars = remember(selectedLines) {
        selectedLines.sumOf { it.primaryText.length }
    }
    val previewFontSize = when {
        totalChars <= 40 -> 13.sp
        totalChars <= 90 -> 11.sp
        totalChars <= 160 -> 9.5.sp
        else -> 8.2.sp
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
    ) {
        // Template Background
        when (template) {
            LyricsShareTemplate.ARTWORK -> {
                if (artworkUri != null) {
                    SmartImage(
                        model = artworkUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.38f),
                                    Color.Black.copy(alpha = 0.68f)
                                )
                            )
                        )
                )
            }
            LyricsShareTemplate.COLOR -> {
                val darkAccent = Color(
                    red = (accentColor.red * 0.32f).coerceIn(0f, 1f),
                    green = (accentColor.green * 0.32f).coerceIn(0f, 1f),
                    blue = (accentColor.blue * 0.32f).coerceIn(0f, 1f),
                    alpha = 1f
                )
                val midAccent = Color(
                    red = (accentColor.red * 0.68f).coerceIn(0f, 1f),
                    green = (accentColor.green * 0.68f).coerceIn(0f, 1f),
                    blue = (accentColor.blue * 0.68f).coerceIn(0f, 1f),
                    alpha = 1f
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(midAccent, darkAccent)
                            )
                        )
                )
            }
            LyricsShareTemplate.DARK -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF111113))
                )
            }
            LyricsShareTemplate.LIGHT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF5F5F7))
                )
            }
        }

        // Inner Card + Footer
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(16.dp))
                    .background(innerCardBg)
                    .border(0.8.dp, innerCardBorder, RoundedCornerShape(16.dp))
                    .padding(11.dp)
            ) {
                // Song Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(fgColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (artworkUri != null) {
                            SmartImage(
                                model = artworkUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = fgColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = songTitle,
                            color = fgColor,
                            fontSize = 9.5.sp,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = artistName,
                            color = subColor,
                            fontSize = 7.5.sp,
                            fontFamily = GoogleSansRounded,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Selected Lyrics
                if (selectedLines.isEmpty()) {
                    Text(
                        text = "Chạm vào các câu lời bên dưới để thêm vào thẻ...",
                        color = subColor,
                        fontSize = 9.sp,
                        fontFamily = GoogleSansRounded
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        selectedLines.forEach { line ->
                            Column {
                                Text(
                                    text = line.primaryText,
                                    color = fgColor,
                                    fontSize = previewFontSize,
                                    lineHeight = previewFontSize * 1.25f,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val sub = line.translation ?: line.romanization
                                if (includeSublines && !sub.isNullOrBlank()) {
                                    Text(
                                        text = sub,
                                        color = subColor,
                                        fontSize = (previewFontSize.value * 0.76f).sp,
                                        fontFamily = GoogleSansRounded,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Brand Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(innerCardBg)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = fgColor.copy(alpha = 0.85f),
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "PixelPlay",
                    color = fgColor.copy(alpha = 0.85f),
                    fontSize = 7.5.sp,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Renders a crisp 1080x1920 (3x scale of Mornye's 360x640 card) offscreen Bitmap
 * for sharing to social apps or saving to device.
 */
private fun renderLyricsShareCardBitmap(
    songTitle: String,
    artistName: String,
    selectedLines: List<ShareableLyricLine>,
    includeSublines: Boolean,
    template: LyricsShareTemplate,
    accentColorArgb: Int,
    artworkBitmap: Bitmap?
): Bitmap {
    val width = 1080
    val height = 1920
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    val isLight = template == LyricsShareTemplate.LIGHT
    val fgColor = if (isLight) 0xFF141416.toInt() else 0xFFFFFFFF.toInt()
    val subColor = if (isLight) 0xAA141416.toInt() else 0xB3FFFFFF.toInt()

    // 1. Draw Background
    when (template) {
        LyricsShareTemplate.ARTWORK -> {
            if (artworkBitmap != null) {
                val blurred = fastBlurBitmap(artworkBitmap, radius = 18)
                drawCenterCropBitmap(canvas, blurred, RectF(0f, 0f, width.toFloat(), height.toFloat()), paint)
            } else {
                paint.color = 0xFF18181C.toInt()
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }
            val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    intArrayOf(0x66000000, 0xB8000000.toInt()),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        }
        LyricsShareTemplate.COLOR -> {
            val r = (accentColorArgb shr 16) and 0xFF
            val g = (accentColorArgb shr 8) and 0xFF
            val b = accentColorArgb and 0xFF
            val topColor = (0xFF shl 24) or ((r * 70 / 100) shl 16) or ((g * 70 / 100) shl 8) or (b * 70 / 100)
            val bottomColor = (0xFF shl 24) or ((r * 28 / 100) shl 16) or ((g * 28 / 100) shl 8) or (b * 28 / 100)
            val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(topColor, bottomColor),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), gradPaint)
        }
        LyricsShareTemplate.DARK -> {
            paint.shader = null
            paint.color = 0xFF111113.toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
        LyricsShareTemplate.LIGHT -> {
            paint.shader = null
            paint.color = 0xFFF5F5F7.toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }

    // 2. Measure selected lyrics text layouts to size the inner card dynamically
    val outerPadding = 84f
    val cardPadding = 66f
    val innerContentWidth = (width - outerPadding * 2 - cardPadding * 2).toInt()

    val totalChars = selectedLines.sumOf { it.primaryText.length }
    val primaryTextSizePx = when {
        totalChars <= 40 -> 88f
        totalChars <= 90 -> 74f
        totalChars <= 160 -> 60f
        else -> 50f
    }
    val subTextSizePx = primaryTextSizePx * 0.62f

    val primaryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fgColor
        textSize = primaryTextSizePx
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = subColor
        textSize = subTextSizePx
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    data class LineLayouts(
        val primary: StaticLayout,
        val secondary: StaticLayout?
    )

    val lineLayouts = selectedLines.map { line ->
        val primaryLayout = StaticLayout.Builder
            .obtain(line.primaryText, 0, line.primaryText.length, primaryPaint, innerContentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.15f)
            .setIncludePad(false)
            .build()

        val subText = if (includeSublines) (line.translation ?: line.romanization) else null
        val secondaryLayout = if (!subText.isNullOrBlank()) {
            StaticLayout.Builder
                .obtain(subText, 0, subText.length, subPaint, innerContentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.12f)
                .setIncludePad(false)
                .build()
        } else null

        LineLayouts(primaryLayout, secondaryLayout)
    }

    val headerHeight = 148f
    val headerBottomGap = 64f
    val lineGap = 42f
    val subGap = 14f

    var totalLyricsHeight = 0f
    lineLayouts.forEachIndexed { idx, item ->
        totalLyricsHeight += item.primary.height
        if (item.secondary != null) {
            totalLyricsHeight += subGap + item.secondary.height
        }
        if (idx < lineLayouts.lastIndex) {
            totalLyricsHeight += lineGap
        }
    }

    val maxCardHeight = height - outerPadding * 2 - 140f
    val desiredCardHeight = (cardPadding * 2 + headerHeight + headerBottomGap + totalLyricsHeight)
        .coerceIn(520f, maxCardHeight)

    val cardTop = outerPadding + ((maxCardHeight - desiredCardHeight) * 0.35f).coerceAtLeast(0f)
    val cardRect = RectF(
        outerPadding,
        cardTop,
        width - outerPadding,
        cardTop + desiredCardHeight
    )

    // 3. Draw Inner Card
    val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isLight) 0x10000000 else 0x22FFFFFF
        style = Paint.Style.FILL
    }
    val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isLight) 0x1A000000 else 0x30FFFFFF
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawRoundRect(cardRect, 64f, 64f, cardBgPaint)
    canvas.drawRoundRect(cardRect, 64f, 64f, cardBorderPaint)

    // 4. Draw Header (Album Art + Title + Artist)
    val artLeft = cardRect.left + cardPadding
    val artTop = cardRect.top + cardPadding
    val artRect = RectF(artLeft, artTop, artLeft + headerHeight, artTop + headerHeight)

    if (artworkBitmap != null) {
        val save = canvas.save()
        val clipPath = Path().apply {
            addRoundRect(artRect, 36f, 36f, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)
        drawCenterCropBitmap(canvas, artworkBitmap, artRect, paint)
        canvas.restoreToCount(save)
    } else {
        val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isLight) 0x18000000 else 0x26FFFFFF
        }
        canvas.drawRoundRect(artRect, 36f, 36f, placeholderPaint)
    }

    val textStartX = artRect.right + 36f
    val maxHeaderTextWidth = (cardRect.right - cardPadding - textStartX).toInt().coerceAtLeast(100)

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fgColor
        textSize = 46f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = subColor
        textSize = 36f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    val ellipsizedTitle = android.text.TextUtils.ellipsize(
        songTitle,
        titlePaint,
        maxHeaderTextWidth.toFloat(),
        android.text.TextUtils.TruncateAt.END
    ).toString()
    val ellipsizedArtist = android.text.TextUtils.ellipsize(
        artistName,
        artistPaint,
        maxHeaderTextWidth.toFloat(),
        android.text.TextUtils.TruncateAt.END
    ).toString()

    canvas.drawText(ellipsizedTitle, textStartX, artTop + 62f, titlePaint)
    canvas.drawText(ellipsizedArtist, textStartX, artTop + 114f, artistPaint)

    // 5. Draw Selected Lyrics Lines (clipped to inner card bottom)
    val lyricsStartY = artRect.bottom + headerBottomGap
    val clipSave = canvas.save()
    canvas.clipRect(
        cardRect.left + cardPadding,
        lyricsStartY,
        cardRect.right - cardPadding,
        cardRect.bottom - cardPadding
    )

    var cursorY = lyricsStartY
    lineLayouts.forEach { item ->
        canvas.save()
        canvas.translate(cardRect.left + cardPadding, cursorY)
        item.primary.draw(canvas)
        canvas.restore()
        cursorY += item.primary.height

        if (item.secondary != null) {
            cursorY += subGap
            canvas.save()
            canvas.translate(cardRect.left + cardPadding, cursorY)
            item.secondary.draw(canvas)
            canvas.restore()
            cursorY += item.secondary.height
        }
        cursorY += lineGap
    }
    canvas.restoreToCount(clipSave)

    // 6. Draw Bottom Brand Pill ("♪ PixelPlay")
    val pillText = "♪  PixelPlay"
    val pillTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fgColor
        textSize = 34f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val pillTextWidth = pillTextPaint.measureText(pillText)
    val pillHeight = 76f
    val pillWidth = pillTextWidth + 76f
    val pillTop = (cardRect.bottom + 36f).coerceAtMost(height - outerPadding - pillHeight)
    val pillRect = RectF(
        outerPadding,
        pillTop,
        outerPadding + pillWidth,
        pillTop + pillHeight
    )
    canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, cardBgPaint)
    canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, cardBorderPaint)

    val fontMetrics = pillTextPaint.fontMetrics
    val pillTextBaseline = pillRect.centerY() - (fontMetrics.ascent + fontMetrics.descent) / 2f
    canvas.drawText(pillText, pillRect.left + 38f, pillTextBaseline, pillTextPaint)

    return output
}

private fun drawCenterCropBitmap(
    canvas: Canvas,
    bitmap: Bitmap,
    dst: RectF,
    paint: Paint
) {
    val bw = bitmap.width.toFloat()
    val bh = bitmap.height.toFloat()
    val dw = dst.width()
    val dh = dst.height()
    if (bw <= 0f || bh <= 0f || dw <= 0f || dh <= 0f) return

    val scale = maxOf(dw / bw, dh / bh)
    val scaledW = bw * scale
    val scaledH = bh * scale
    val left = dst.left + (dw - scaledW) * 0.5f
    val top = dst.top + (dh - scaledH) * 0.5f
    val targetRect = RectF(left, top, left + scaledW, top + scaledH)
    canvas.drawBitmap(bitmap, null, targetRect, paint)
}

private fun fastBlurBitmap(source: Bitmap, radius: Int): Bitmap {
    // Downscale + multi-pass box blur for smooth, fast backdrop blur on all Android API levels
    val scaleWidth = (source.width / 4).coerceAtLeast(32)
    val scaleHeight = (source.height / 4).coerceAtLeast(32)
    val small = Bitmap.createScaledBitmap(source, scaleWidth, scaleHeight, true)
    val working = small.copy(Bitmap.Config.ARGB_8888, true)

    val w = working.width
    val h = working.height
    val pixels = IntArray(w * h)
    working.getPixels(pixels, 0, w, 0, 0, w, h)
    val temp = IntArray(w * h)
    val r = radius.coerceIn(2, 24)

    repeat(2) {
        // Horizontal pass
        for (y in 0 until h) {
            val rowOffset = y * w
            for (x in 0 until w) {
                var aSum = 0
                var rSum = 0
                var gSum = 0
                var bSum = 0
                var count = 0
                for (dx in -r..r) {
                    val nx = (x + dx).coerceIn(0, w - 1)
                    val c = pixels[rowOffset + nx]
                    aSum += (c ushr 24) and 0xFF
                    rSum += (c ushr 16) and 0xFF
                    gSum += (c ushr 8) and 0xFF
                    bSum += c and 0xFF
                    count++
                }
                temp[rowOffset + x] =
                    ((aSum / count) shl 24) or ((rSum / count) shl 16) or ((gSum / count) shl 8) or (bSum / count)
            }
        }
        // Vertical pass
        for (x in 0 until w) {
            for (y in 0 until h) {
                var aSum = 0
                var rSum = 0
                var gSum = 0
                var bSum = 0
                var count = 0
                for (dy in -r..r) {
                    val ny = (y + dy).coerceIn(0, h - 1)
                    val c = temp[ny * w + x]
                    aSum += (c ushr 24) and 0xFF
                    rSum += (c ushr 16) and 0xFF
                    gSum += (c ushr 8) and 0xFF
                    bSum += c and 0xFF
                    count++
                }
                pixels[y * w + x] =
                    ((aSum / count) shl 24) or ((rSum / count) shl 16) or ((gSum / count) shl 8) or (bSum / count)
            }
        }
    }

    working.setPixels(pixels, 0, w, 0, 0, w, h)
    return working
}

private fun shareLyricsCardFile(
    context: Context,
    file: File,
    songTitle: String,
    artistName: String
) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "$songTitle — $artistName")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(shareIntent, "Chia sẻ thẻ lời bài hát")
    )
}
