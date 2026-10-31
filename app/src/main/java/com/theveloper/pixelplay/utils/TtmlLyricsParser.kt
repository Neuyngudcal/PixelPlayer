package com.theveloper.pixelplay.utils

import java.io.StringReader
import java.util.Base64
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource

internal object TtmlLyricsParser {
    private const val MAX_TTML_PARAGRAPHS = 5_000

    private data class SpanPiece(
        val text: String,
        val beginMs: Int?,
        val endMs: Int?,
        val isPart: Boolean
    )

    fun parseToEnhancedLrc(ttmlText: String, includeWordEndTags: Boolean = true): String? {
        return runCatching {
            val normalizedTtml = normalizeTtmlDocument(ttmlText)
            if (normalizedTtml.isBlank()) {
                return null
            }

            val builder = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isXIncludeAware = false
                setExpandEntityReferences(false)
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
                runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
            }.newDocumentBuilder().apply {
                setEntityResolver(EntityResolver { _, _ -> InputSource(StringReader("")) })
            }

            val document = builder.parse(InputSource(StringReader(normalizedTtml)))
            val paragraphNodes = document.getElementsByTagNameNS("*", "p")
            if (paragraphNodes.length == 0 || paragraphNodes.length > MAX_TTML_PARAGRAPHS) {
                return null
            }

            // Parse <ttm:agent xml:id="..." type="person|group"> in <head>
            val agentRoles = mutableMapOf<String, String>()
            var personCount = 0
            val agentNodes = document.getElementsByTagNameNS("*", "agent")
            for (i in 0 until agentNodes.length) {
                val agentEl = agentNodes.item(i) as? Element ?: continue
                val id = findAttributeByLocalName(agentEl, "id")?.trim().orEmpty()
                val type = findAttributeByLocalName(agentEl, "type")?.trim()?.lowercase().orEmpty()
                if (id.isEmpty()) continue
                val role = when {
                    id.equals("v1", ignoreCase = true) -> {
                        personCount = maxOf(personCount, 1)
                        "v1"
                    }
                    id.equals("v2", ignoreCase = true) -> {
                        personCount = maxOf(personCount, 2)
                        "v2"
                    }
                    id.equals("v3", ignoreCase = true) || type == "group" -> "v3"
                    type == "person" -> {
                        personCount++
                        if (personCount == 2) "v2" else "v1"
                    }
                    else -> "v1"
                }
                agentRoles[id] = role
            }

            val hasMultipleVoices = agentRoles.values.distinct().size > 1
            val keyToStartMs = mutableMapOf<String, Int>()
            val timedLines = mutableListOf<Pair<Int, String>>()

            for (index in 0 until paragraphNodes.length) {
                val paragraph = paragraphNodes.item(index) as? Element ?: continue
                val leadPieces = mutableListOf<SpanPiece>()
                val bgPieces = mutableListOf<SpanPiece>()
                collectParagraphPieces(paragraph, leadPieces, bgPieces, inBackground = false)

                val pBeginMs = parseTimeExpression(paragraph.getAttribute("begin"))
                    ?: leadPieces.firstNotNullOfOrNull { it.beginMs }
                    ?: bgPieces.firstNotNullOfOrNull { it.beginMs }
                    ?: continue
                val pEndMs = parseTimeExpression(paragraph.getAttribute("end"))
                    ?: leadPieces.asReversed().firstNotNullOfOrNull { it.endMs }

                val lineKey = findAttributeByLocalName(paragraph, "key")?.trim()
                if (!lineKey.isNullOrEmpty()) {
                    keyToStartMs[lineKey] = pBeginMs
                }

                val rawAgent = findAttributeByLocalName(paragraph, "agent")?.trim().orEmpty()
                val resolvedRole = when {
                    rawAgent.isEmpty() -> null
                    agentRoles.containsKey(rawAgent) -> agentRoles[rawAgent]
                    rawAgent.equals("v2", ignoreCase = true) -> "v2"
                    rawAgent.equals("v3", ignoreCase = true) -> "v3"
                    rawAgent.equals("v1", ignoreCase = true) -> "v1"
                    else -> null
                }
                val voicePrefix = when {
                    resolvedRole == "v2" || resolvedRole == "v3" -> "$resolvedRole:"
                    resolvedRole == "v1" && hasMultipleVoices -> "v1:"
                    else -> ""
                }

                val leadBody = formatPiecesToEnhancedLrc(leadPieces, pEndMs, includeWordEndTags)
                if (leadBody.isNotBlank()) {
                    timedLines += pBeginMs to "[${formatLrcTimestamp(pBeginMs)}]$voicePrefix$leadBody"
                }

                if (bgPieces.isNotEmpty()) {
                    val bgStartMs = bgPieces.firstNotNullOfOrNull { it.beginMs } ?: pBeginMs
                    val bgEndMs = bgPieces.asReversed().firstNotNullOfOrNull { it.endMs } ?: pEndMs
                    val bgBody = stripWrappingParens(formatPiecesToEnhancedLrc(bgPieces, bgEndMs, includeWordEndTags))
                    if (bgBody.isNotBlank()) {
                        timedLines += bgStartMs to "[${formatLrcTimestamp(bgStartMs)}][bg:$bgBody]"
                    }
                }
            }

            if (timedLines.isEmpty()) return null

            // Extract translations and transliterations from <head><metadata><iTunesMetadata>
            val deviceLang = java.util.Locale.getDefault().language.lowercase(java.util.Locale.ROOT)
            val translations = extractSupplements(document, keyToStartMs, "translation") { lang ->
                val normalized = lang.trim().lowercase(java.util.Locale.ROOT)
                when {
                    deviceLang.isNotEmpty() && normalized.startsWith(deviceLang) -> 2
                    normalized.startsWith("en") -> 1
                    else -> 0
                }
            }
            val transliterations = extractSupplements(document, keyToStartMs, "transliteration") { lang ->
                if (lang.contains("latn", ignoreCase = true)) 2 else 0
            }

            val headers = mutableListOf<String>()
            transliterations.forEach { (startMs, text) ->
                val encoded = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
                headers += "[x-romaji:$startMs:$encoded]"
            }
            translations.forEach { (startMs, text) ->
                val encoded = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
                headers += "[x-translation:$startMs:$encoded]"
            }

            val bodyText = timedLines
                .sortedBy { it.first }
                .joinToString("\n") { it.second }

            if (headers.isEmpty()) {
                bodyText
            } else {
                headers.joinToString("\n") + "\n" + bodyText
            }
        }.getOrNull()
    }

    private fun collectParagraphPieces(
        node: Node,
        lead: MutableList<SpanPiece>,
        background: MutableList<SpanPiece>,
        inBackground: Boolean
    ) {
        var child = node.firstChild
        while (child != null) {
            val target = if (inBackground) background else lead
            when (child.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                    pushPiece(target, child.nodeValue.orEmpty(), null, null)
                }
                Node.ELEMENT_NODE -> {
                    val el = child as Element
                    val localName = localNameOf(el)
                    val role = findAttributeByLocalName(el, "role")?.trim()?.lowercase()
                    when {
                        localName == "br" -> {
                            pushPiece(target, " ", null, null)
                        }
                        role == "x-bg" -> {
                            collectParagraphPieces(el, lead, background, inBackground = true)
                        }
                        role != null && role != "x-bg" -> {
                            // Skip non-sung inline metadata spans (e.g. x-roman, x-translation)
                        }
                        hasElementChildren(el) -> {
                            collectParagraphPieces(el, lead, background, inBackground = inBackground)
                        }
                        else -> {
                            val text = collectPlainText(el)
                            val beginMs = parseTimeExpression(el.getAttribute("begin"))
                            val endMs = parseTimeExpression(el.getAttribute("end"))
                            pushPiece(target, text, beginMs, endMs)
                        }
                    }
                }
            }
            child = child.nextSibling
        }
    }

    private fun pushPiece(
        target: MutableList<SpanPiece>,
        rawText: String,
        beginMs: Int?,
        endMs: Int?
    ) {
        val cleaned = sanitizeTextFragment(rawText)
        if (cleaned.isEmpty()) return

        if (cleaned.first().isWhitespace() && target.isNotEmpty()) {
            val lastIndex = target.lastIndex
            target[lastIndex] = target[lastIndex].copy(isPart = false)
        }

        val normalizedWords = cleaned.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
        if (normalizedWords.isEmpty()) return

        val endsWithSpace = cleaned.last().isWhitespace()
        target += SpanPiece(
            text = normalizedWords,
            beginMs = beginMs,
            endMs = endMs,
            isPart = !endsWithSpace
        )
    }

    private fun formatPiecesToEnhancedLrc(
        pieces: List<SpanPiece>,
        fallbackLineEndMs: Int?,
        includeWordEndTags: Boolean = true
    ): String {
        if (pieces.isEmpty()) return ""
        val hasWordTiming = pieces.any { it.beginMs != null }
        if (!hasWordTiming) {
            return buildString {
                pieces.forEachIndexed { idx, piece ->
                    append(piece.text)
                    if (!piece.isPart && idx < pieces.lastIndex) append(' ')
                }
            }.trim()
        }

        val sb = StringBuilder()
        var lastEmittedTimestamp: Int? = null
        pieces.forEachIndexed { index, piece ->
            val start = piece.beginMs
            if (start != null && start != lastEmittedTimestamp) {
                sb.append("<${formatLrcTimestamp(start)}>")
                lastEmittedTimestamp = start
            }
            sb.append(piece.text)
            if (!piece.isPart && index < pieces.lastIndex) {
                sb.append(' ')
            }
            if (includeWordEndTags) {
                val end = piece.endMs ?: if (index == pieces.lastIndex) fallbackLineEndMs else null
                if (end != null) {
                    sb.append("<${formatLrcTimestamp(end)}>")
                    lastEmittedTimestamp = end
                }
            }
        }
        return sb.toString().trim()
    }

    private fun stripWrappingParens(value: String): String {
        val trimmed = value.trim()
        val plain = trimmed.replace(Regex("<\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?>"), "").trim()
        if (plain.startsWith("(") && plain.endsWith(")")) {
            return trimmed
                .replaceFirst("(", "")
                .let { s ->
                    val lastParen = s.lastIndexOf(')')
                    if (lastParen >= 0) s.removeRange(lastParen, lastParen + 1) else s
                }
                .trim()
        }
        return trimmed
    }

    private fun extractSupplements(
        document: org.w3c.dom.Document,
        keyToStartMs: Map<String, Int>,
        tagName: String,
        languageRanker: (String) -> Int
    ): Map<Int, String> {
        if (keyToStartMs.isEmpty()) return emptyMap()
        val nodes = document.getElementsByTagNameNS("*", tagName)
        var bestMap = emptyMap<Int, String>()
        var bestRank = -1

        for (i in 0 until nodes.length) {
            val groupEl = nodes.item(i) as? Element ?: continue
            val lang = findAttributeByLocalName(groupEl, "lang").orEmpty()
            val rank = languageRanker(lang)
            val currentMap = mutableMapOf<Int, String>()

            val textNodes = groupEl.getElementsByTagNameNS("*", "text")
            for (j in 0 until textNodes.length) {
                val textEl = textNodes.item(j) as? Element ?: continue
                val forKey = findAttributeByLocalName(textEl, "for")?.trim() ?: continue
                val startMs = keyToStartMs[forKey] ?: continue
                val textVal = collectPlainText(textEl).trim().replace(Regex("\\s+"), " ")
                if (textVal.isNotEmpty()) {
                    currentMap[startMs] = textVal
                }
            }

            if (currentMap.isNotEmpty()) {
                val isBetter = bestMap.isEmpty() ||
                    (rank > bestRank) ||
                    (rank == bestRank && currentMap.size > bestMap.size)
                if (isBetter) {
                    bestMap = currentMap
                    bestRank = rank
                }
            }
        }
        return bestMap
    }

    private fun findAttributeByLocalName(element: Element, localName: String): String? {
        val attrs = element.attributes ?: return null
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i) ?: continue
            val name = attr.localName ?: attr.nodeName.substringAfterLast(':')
            if (name.equals(localName, ignoreCase = true)) {
                return attr.nodeValue
            }
        }
        return null
    }

    private fun hasElementChildren(element: Element): Boolean {
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) return true
            child = child.nextSibling
        }
        return false
    }

    private fun collectPlainText(node: Node): String {
        val sb = StringBuilder()
        var child = node.firstChild
        while (child != null) {
            when (child.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> sb.append(child.nodeValue.orEmpty())
                Node.ELEMENT_NODE -> {
                    val el = child as Element
                    val role = findAttributeByLocalName(el, "role")?.trim()?.lowercase()
                    if (role != "x-bg") {
                        sb.append(collectPlainText(el))
                    }
                }
            }
            child = child.nextSibling
        }
        return sb.toString()
    }

    private fun normalizeTtmlDocument(raw: String): String {
        val withoutLeadingNoise = raw.trimStart { char ->
            char.isWhitespace() ||
                char == '\uFEFF' ||
                Character.getType(char).toByte() == Character.FORMAT
        }

        return if (withoutLeadingNoise.startsWith("<?xml", ignoreCase = true)) {
            withoutLeadingNoise.substringAfter("?>", missingDelimiterValue = withoutLeadingNoise).trimStart()
        } else {
            withoutLeadingNoise
        }
    }

    private fun localNameOf(element: Element): String {
        return element.localName?.lowercase()
            ?: element.tagName.substringAfterLast(':').lowercase()
    }

    private fun sanitizeTextFragment(raw: String): String {
        if (raw.isEmpty()) return raw

        val cleaned = raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .filterNot { char ->
                Character.getType(char).toByte() == Character.FORMAT ||
                    (Character.isISOControl(char) && char != '\n' && char != '\t')
            }

        if (cleaned.isEmpty()) return ""
        if (cleaned.all { it.isWhitespace() }) {
            return if (cleaned.any { it == '\n' || it == '\t' }) "" else " "
        }

        return cleaned.replace(Regex("\\s+"), " ")
    }

    private fun parseTimeExpression(value: String?): Int? {
        val normalized = value?.trim().orEmpty()
        if (normalized.isEmpty()) return null

        if (normalized.endsWith("ms", ignoreCase = true)) {
            val ms = normalized.removeSuffix("ms").removeSuffix("MS").trim().toDoubleOrNull() ?: return null
            return ms.roundToInt()
        }

        if (normalized.endsWith("s", ignoreCase = true)) {
            val seconds = normalized.removeSuffix("s").removeSuffix("S").toDoubleOrNull() ?: return null
            return (seconds * 1000).roundToInt()
        }

        normalized.toDoubleOrNull()?.let { seconds ->
            return (seconds * 1000).roundToInt()
        }

        val parts = normalized.split(':')
        return when (parts.size) {
            2 -> {
                val minutes = parts[0].toLongOrNull() ?: return null
                val (seconds, millis) = parseSecondsWithFraction(parts[1]) ?: return null
                (minutes * 60_000L + seconds * 1_000L + millis).toInt()
            }
            3 -> {
                val hours = parts[0].toLongOrNull() ?: return null
                val minutes = parts[1].toLongOrNull() ?: return null
                val (seconds, millis) = parseSecondsWithFraction(parts[2]) ?: return null
                (hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + millis).toInt()
            }
            else -> null
        }
    }

    private fun parseSecondsWithFraction(value: String): Pair<Long, Long>? {
        val parts = value.split('.', limit = 2)
        val seconds = parts[0].toLongOrNull() ?: return null
        val fraction = parts.getOrNull(1).orEmpty()
        if (fraction.isEmpty()) return seconds to 0L

        val digits = fraction.take(3).padEnd(3, '0')
        val millis = digits.toLongOrNull() ?: return null
        return seconds to millis
    }

    private fun formatLrcTimestamp(timeMs: Int): String {
        val totalSeconds = timeMs / 1_000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hundredths = (timeMs % 1_000) / 10
        return String.format("%02d:%02d.%02d", minutes, seconds, hundredths)
    }
}
