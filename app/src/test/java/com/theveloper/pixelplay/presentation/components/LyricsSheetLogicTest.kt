package com.theveloper.pixelplay.presentation.components

import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsSheetLogicTest {

    @Test
    fun sanitizeSyncedWords_removesLeadingTags_preventsOverlap() {
        val words = listOf(
            SyncedWord(time = 0, word = "v1:"),
            SyncedWord(time = 120, word = "Hello"),
            SyncedWord(time = 240, word = "world")
        )

        val sanitized = sanitizeSyncedWords(words)

        assertEquals(listOf("Hello", "world"), sanitized.map { it.word })
        assertEquals(listOf(120, 240), sanitized.map { it.time })
        assertTrue(sanitized.all { it.startsNewWord })
    }

    @Test
    fun highlightSnapOffsetPx_alignsLineWithHighlightZone() {
        val viewportHeight = 960
        val itemSize = 120
        val highlightOffsetPx = 80f

        val offset = highlightSnapOffsetPx(viewportHeight, itemSize, highlightOffsetPx)

        val expectedCenter = viewportHeight / 2f - highlightOffsetPx
        assertEquals(expectedCenter - itemSize / 2f, offset.toFloat(), 0.5f)
    }

    @Test
    fun highlightSnapOffsetPx_clampsWithinViewportForEndOfList() {
        val viewportHeight = 420
        val itemSize = 320
        val highlightOffsetPx = -160f

        val offset = highlightSnapOffsetPx(viewportHeight, itemSize, highlightOffsetPx)
        val center = offset + itemSize / 2f

        assertTrue(center <= viewportHeight.toFloat())
        assertTrue(center >= itemSize / 2f)
    }

    @Test
    fun highlightSnapOffsetPx_handlesOversizedItems() {
        val viewportHeight = 200
        val itemSize = 260

        val offset = highlightSnapOffsetPx(viewportHeight, itemSize, highlightOffsetPx = 60f)

        assertEquals(0, offset)
    }

    @Test
    fun calculateHighlightMetrics_reservesBottomSpace() {
        val metrics = calculateHighlightMetrics(
            containerHeight = 480.dp,
            highlightZoneFraction = 0.22f,
            highlightOffset = 48.dp
        )

        assertTrue(metrics.bottomPadding > 0.dp)
        assertTrue(metrics.topPadding >= 0.dp)
        assertTrue(metrics.zoneHeight > 0.dp)
    }

    @Test
    fun sanitizeLyricLineText_stripsLrcTimestampTags() {
        val raw = "[00:26.42][01:12.34] Three in the morning, I ain't slept all weekend"

        val sanitized = sanitizeLyricLineText(raw)

        assertEquals("Three in the morning, I ain't slept all weekend", sanitized)
    }

    @Test
    fun normalizeWordEndTime_guaranteesForwardRangeWhenTimestampsMatch() {
        val endTime = normalizeWordEndTime(
            currentWordTimeMs = 2_000L,
            nextWordTimeMs = 2_000L,
            lineEndTimeMs = 2_800L
        )

        assertEquals(2_001L, endTime)
    }

    @Test
    fun normalizeWordEndTime_clampsToLineEnd() {
        val endTime = normalizeWordEndTime(
            currentWordTimeMs = 5_000L,
            nextWordTimeMs = 8_000L,
            lineEndTimeMs = 5_400L
        )

        assertEquals(5_400L, endTime)
    }

    @Test
    fun resolveLineEndTimeMs_extendsPastNextLineWhenLastWordStartsLater() {
        val line = SyncedLine(
            time = 1_000,
            line = "abc",
            words = listOf(
                SyncedWord(1_000, "a"),
                SyncedWord(1_600, "b"),
                SyncedWord(2_000, "c")
            )
        )

        val lineEnd = resolveLineEndTimeMs(line, nextLineStartMs = 2_000)

        assertEquals(2_001L, lineEnd)
    }

    @Test
    fun sanitizeSyncedWords_promotesFirstVisibleWordAfterLeadingMarker() {
        val words = listOf(
            SyncedWord(time = 1000, word = "v1:"),
            SyncedWord(time = 1200, word = "fall", startsNewWord = false)
        )

        val sanitized = sanitizeSyncedWords(words)

        assertEquals(1, sanitized.size)
        assertEquals("fall", sanitized[0].word)
        assertTrue(sanitized[0].startsNewWord)
    }

    @Test
    fun clusterSyncedWords_keepsSyllablesInsideSameWord() {
        val clusters = clusterSyncedWords(
            listOf(
                SyncedWord(time = 1000, word = "to", startsNewWord = true),
                SyncedWord(time = 1100, word = "geth", startsNewWord = false),
                SyncedWord(time = 1200, word = "er", startsNewWord = false),
                SyncedWord(time = 1500, word = "now", startsNewWord = true)
            )
        )

        assertEquals(2, clusters.size)
        assertEquals(listOf("to", "geth", "er"), clusters[0].words.map { it.word })
        assertEquals(listOf("now"), clusters[1].words.map { it.word })
        assertEquals(0, clusters[0].startIndex)
        assertEquals(3, clusters[1].startIndex)
    }

    @Test
    fun resolveHighlightedWordIndex_picksLastVisibleWordAtLineEnd() {
        val words = listOf(
            SyncedWord(time = 10_000, word = "fall"),
            SyncedWord(time = 10_250, word = "in"),
            SyncedWord(time = 10_500, word = "love")
        )

        val idx = resolveHighlightedWordIndex(
            words = words,
            positionMs = 10_900,
            lineStartTimeMs = 10_000,
            lineEndTimeMs = 11_000
        )

        assertEquals(2, idx)
    }

    @Test
    fun resolveSeekPositionMs_subtractsPositiveLyricsOffset() {
        val seekPosition = resolveSeekPositionMs(
            lineTimeMs = 12_000L,
            lyricsSyncOffsetMs = 750
        )

        assertEquals(11_250L, seekPosition)
    }

    @Test
    fun resolveSeekPositionMs_addsNegativeLyricsOffset() {
        val seekPosition = resolveSeekPositionMs(
            lineTimeMs = 12_000L,
            lyricsSyncOffsetMs = -750
        )

        assertEquals(12_750L, seekPosition)
    }

    @Test
    fun resolveSeekPositionMs_clampsToZeroWhenOffsetWouldGoNegative() {
        val seekPosition = resolveSeekPositionMs(
            lineTimeMs = 300L,
            lyricsSyncOffsetMs = 750
        )

        assertEquals(0L, seekPosition)
    }

    @Test
    fun resolveLineEndTimeMs_respectsExplicitEndDuringLongInstrumentalGap() {
        val line = SyncedLine(
            time = 5_000,
            endTime = 8_000,
            line = "Before solo"
        )

        val lineEnd = resolveLineEndTimeMs(line, nextLineStartMs = 25_000)

        assertEquals(8_000L, lineEnd)
    }

    @Test
    fun resolveCurrentLineIndex_prefersLeadVocalOverOverlappingBackgroundVocal() {
        val lines = listOf(
            SyncedLine(time = 10_000, endTime = 16_000, line = "Lead vocal", isBackground = false),
            SyncedLine(time = 12_000, endTime = 15_000, line = "Backing vocal", isBackground = true),
            SyncedLine(time = 16_000, endTime = 20_000, line = "Next lead", isBackground = false)
        )

        val activeIndex = resolveCurrentLineIndex(lines = lines, position = 13_000L)

        assertEquals(0, activeIndex)
    }

    @Test
    fun resolveNextLineStartTimes_skipsBackgroundLinesForLeadVocals() {
        val lines = listOf(
            SyncedLine(time = 1_000, line = "Lead A"),
            SyncedLine(time = 2_000, line = "Backing", isBackground = true),
            SyncedLine(time = 3_000, line = "Lead B"),
            SyncedLine(time = 4_000, line = "Trailing backing", isBackground = true)
        )

        val nextStarts = resolveNextLineStartTimes(lines)

        // Lead A ends at the next lead line, the backing line at whatever follows it,
        // and the last lead line (no following lead) falls back to the immediate next line.
        assertEquals(listOf(3_000, 3_000, 4_000, Int.MAX_VALUE), nextStarts.toList())
    }

    @Test
    fun resolveCurrentLineIndex_matchesAcrossWholeSong() {
        val lines = listOf(
            SyncedLine(time = 0, line = "One"),
            SyncedLine(time = 2_000, line = "Two"),
            SyncedLine(time = 2_500, line = "bg", isBackground = true),
            SyncedLine(time = 4_000, line = "Three")
        )

        assertEquals(0, resolveCurrentLineIndex(lines, 1_000L))
        assertEquals(1, resolveCurrentLineIndex(lines, 3_000L))
        assertEquals(3, resolveCurrentLineIndex(lines, 9_000L))
        assertEquals(-1, resolveCurrentLineIndex(emptyList(), 0L))
    }

    @Test
    fun splitPlainLyricLine_vietnameseTranslationIsNotRomanization() {
        val parts = splitPlainLyricLine("君が好きだ\nTôi thích em")

        assertEquals("君が好きだ", parts.primary)
        assertEquals("", parts.romanization)
        assertEquals("Tôi thích em", parts.translation)
    }

    @Test
    fun splitPlainLyricLine_detectsRomanizationThenTranslation() {
        val parts = splitPlainLyricLine("君が好きだ\nkimi ga suki da\nTôi thích em")

        assertEquals("kimi ga suki da", parts.romanization)
        assertEquals("Tôi thích em", parts.translation)
    }

    @Test
    fun splitPlainLyricLine_cjkTranslationWithSpacesStaysTranslation() {
        val parts = splitPlainLyricLine("사랑해요\n我 爱 你")

        assertEquals("", parts.romanization)
        assertEquals("我 爱 你", parts.translation)
    }

    @Test
    fun splitPlainLyricLine_latinOriginalHasOnlyTranslation() {
        val parts = splitPlainLyricLine("I love you\nAnh yêu em")

        assertEquals("I love you", parts.primary)
        assertEquals("", parts.romanization)
        assertEquals("Anh yêu em", parts.translation)
    }
}
