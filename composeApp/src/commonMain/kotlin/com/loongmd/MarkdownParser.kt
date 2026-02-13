package com.loongmd

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class UnorderedList(val items: List<MdListItem>) : MdBlock
    data class OrderedList(val items: List<MdListItem>) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class CodeFence(val language: String?, val text: String) : MdBlock
    data object HorizontalRule : MdBlock
}

data class MdListItem(
    val text: String,
    val indent: Int,
    val checked: Boolean?
)

data class MdInlineSpan(
    val text: String,
    val style: MdInlineStyle = MdInlineStyle()
)

data class MdInlineStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strikethrough: Boolean = false,
    val code: Boolean = false,
    val link: String? = null
)

private enum class ListKind {
    Unordered,
    Ordered
}

private data class MutableListItem(
    var text: String,
    val indent: Int,
    val checked: Boolean?
)

private data class LinkMatch(
    val label: String,
    val url: String,
    val endExclusive: Int
)

private val headingRegex = Regex("""^(#{1,6})\s+(.+?)\s*$""")
private val unorderedListRegex = Regex("""^(\s*)([-*+])\s+(.*)$""")
private val orderedListRegex = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""")
private val taskListRegex = Regex("""^\[( |x|X)\]\s+(.*)$""")
private val quoteRegex = Regex("""^\s{0,3}>\s?(.*)$""")
private val horizontalRuleRegex = Regex("""^\s{0,3}((\*\s*){3,}|(-\s*){3,}|(_\s*){3,})\s*$""")
private val codeFenceRegex = Regex("""^\s*```([^\s`]+)?\s*$""")
private val autoLinkRegex = Regex("""(?i)(https?://|www\.)[^\s<>\[\]{}]+""")

fun parseMarkdown(markdown: String): List<MdBlock> {
    if (markdown.isBlank()) return emptyList()

    val blocks = mutableListOf<MdBlock>()
    val lines = markdown.lines()

    val paragraphBuffer = mutableListOf<String>()
    val quoteBuffer = mutableListOf<String>()
    val codeBuffer = mutableListOf<String>()
    val listBuffer = mutableListOf<MutableListItem>()
    var currentListKind: ListKind? = null
    var inCodeBlock = false
    var codeLanguage: String? = null

    fun flushParagraph() {
        if (paragraphBuffer.isNotEmpty()) {
            blocks += MdBlock.Paragraph(paragraphBuffer.joinToString("\n").trim())
            paragraphBuffer.clear()
        }
    }

    fun flushQuote() {
        if (quoteBuffer.isNotEmpty()) {
            blocks += MdBlock.Quote(quoteBuffer.joinToString("\n").trim())
            quoteBuffer.clear()
        }
    }

    fun flushList() {
        if (listBuffer.isEmpty() || currentListKind == null) return

        val items = listBuffer.map {
            MdListItem(
                text = it.text.trim(),
                indent = it.indent,
                checked = it.checked
            )
        }

        blocks += when (currentListKind) {
            ListKind.Unordered -> MdBlock.UnorderedList(items)
            ListKind.Ordered -> MdBlock.OrderedList(items)
            null -> return
        }

        listBuffer.clear()
        currentListKind = null
    }

    fun flushCode() {
        if (codeBuffer.isNotEmpty()) {
            blocks += MdBlock.CodeFence(
                language = codeLanguage,
                text = codeBuffer.joinToString("\n")
            )
            codeBuffer.clear()
        }
        codeLanguage = null
    }

    for (line in lines) {
        val trimmed = line.trimEnd()
        val codeFenceMatch = codeFenceRegex.matchEntire(trimmed)

        if (codeFenceMatch != null) {
            if (inCodeBlock) {
                flushCode()
                inCodeBlock = false
            } else {
                flushParagraph()
                flushQuote()
                flushList()
                inCodeBlock = true
                codeLanguage = codeFenceMatch.groupValues[1].ifBlank { null }
            }
            continue
        }

        if (inCodeBlock) {
            codeBuffer += line
            continue
        }

        if (trimmed.isBlank()) {
            flushParagraph()
            flushQuote()
            flushList()
            continue
        }

        val quoteMatch = quoteRegex.matchEntire(trimmed)
        if (quoteMatch != null) {
            flushParagraph()
            flushList()
            quoteBuffer += quoteMatch.groupValues[1]
            continue
        } else {
            flushQuote()
        }

        if (horizontalRuleRegex.matches(trimmed)) {
            flushParagraph()
            flushList()
            blocks += MdBlock.HorizontalRule
            continue
        }

        val headingMatch = headingRegex.matchEntire(trimmed)
        if (headingMatch != null) {
            flushParagraph()
            flushList()
            blocks += MdBlock.Heading(
                level = headingMatch.groupValues[1].length,
                text = headingMatch.groupValues[2].trim()
            )
            continue
        }

        val unorderedMatch = unorderedListRegex.matchEntire(trimmed)
        if (unorderedMatch != null) {
            flushParagraph()
            flushQuote()
            if (currentListKind != ListKind.Unordered) {
                flushList()
                currentListKind = ListKind.Unordered
            }

            val rawText = unorderedMatch.groupValues[3]
            val (listText, checked) = parseTaskList(rawText)
            val indent = unorderedMatch.groupValues[1].length / 2
            listBuffer += MutableListItem(text = listText, indent = indent, checked = checked)
            continue
        }

        val orderedMatch = orderedListRegex.matchEntire(trimmed)
        if (orderedMatch != null) {
            flushParagraph()
            flushQuote()
            if (currentListKind != ListKind.Ordered) {
                flushList()
                currentListKind = ListKind.Ordered
            }

            val rawText = orderedMatch.groupValues[3]
            val (listText, checked) = parseTaskList(rawText)
            val indent = orderedMatch.groupValues[1].length / 2
            listBuffer += MutableListItem(text = listText, indent = indent, checked = checked)
            continue
        }

        if (currentListKind != null && line.startsWith("  ")) {
            val continuation = trimmed.trimStart()
            if (continuation.isNotBlank() && listBuffer.isNotEmpty()) {
                listBuffer[listBuffer.lastIndex].text += "\n$continuation"
                continue
            }
        }

        if (currentListKind != null) {
            flushList()
        }

        paragraphBuffer += trimmed
    }

    flushParagraph()
    flushQuote()
    flushList()
    if (inCodeBlock) flushCode()

    return blocks
}

fun parseInline(text: String): List<MdInlineSpan> {
    if (text.isEmpty()) return emptyList()
    return mergeAdjacentSpans(parseInlineInternal(text, MdInlineStyle()))
}

private fun parseInlineInternal(text: String, baseStyle: MdInlineStyle): List<MdInlineSpan> {
    val spans = mutableListOf<MdInlineSpan>()
    val plainBuffer = StringBuilder()
    var index = 0

    fun flushPlain() {
        if (plainBuffer.isNotEmpty()) {
            spans += MdInlineSpan(plainBuffer.toString(), baseStyle)
            plainBuffer.clear()
        }
    }

    while (index < text.length) {
        if (text[index] == '\\' && index + 1 < text.length) {
            plainBuffer.append(text[index + 1])
            index += 2
            continue
        }

        if (text[index] == '`') {
            val tickCount = countRepeated(text, index, '`')
            val delimiter = "`".repeat(tickCount)
            val closeIndex = findUnescapedDelimiter(text, delimiter, index + tickCount)
            if (closeIndex != -1) {
                flushPlain()
                val code = text.substring(index + tickCount, closeIndex)
                spans += MdInlineSpan(code, baseStyle.copy(code = true))
                index = closeIndex + tickCount
                continue
            }
        }

        val linkMatch = parseLinkAt(text, index)
        if (linkMatch != null) {
            flushPlain()
            val linkStyle = baseStyle.copy(link = linkMatch.url)
            spans += parseInlineInternal(linkMatch.label, linkStyle)
            index = linkMatch.endExclusive
            continue
        }

        if (baseStyle.link == null) {
            val autoLinkMatch = parseAutoLinkAt(text, index)
            if (autoLinkMatch != null) {
                flushPlain()
                spans += MdInlineSpan(
                    text = autoLinkMatch.label,
                    style = baseStyle.copy(link = autoLinkMatch.url)
                )
                index = autoLinkMatch.endExclusive
                continue
            }
        }

        val delimiter = when {
            text.startsWith("***", index) -> "***"
            text.startsWith("___", index) -> "___"
            text.startsWith("**", index) -> "**"
            text.startsWith("__", index) -> "__"
            text.startsWith("~~", index) -> "~~"
            text.startsWith("*", index) -> "*"
            text.startsWith("_", index) -> "_"
            else -> null
        }

        if (delimiter != null) {
            val closeIndex = findUnescapedDelimiter(text, delimiter, index + delimiter.length)
            if (closeIndex != -1) {
                flushPlain()
                val innerText = text.substring(index + delimiter.length, closeIndex)
                val style = when (delimiter) {
                    "***", "___" -> baseStyle.copy(bold = true, italic = true)
                    "**", "__" -> baseStyle.copy(bold = true)
                    "*", "_" -> baseStyle.copy(italic = true)
                    "~~" -> baseStyle.copy(strikethrough = true)
                    else -> baseStyle
                }
                spans += parseInlineInternal(innerText, style)
                index = closeIndex + delimiter.length
                continue
            }
        }

        plainBuffer.append(text[index])
        index += 1
    }

    flushPlain()
    return spans
}

private fun parseTaskList(text: String): Pair<String, Boolean?> {
    val task = taskListRegex.matchEntire(text.trim())
    if (task != null) {
        val checked = task.groupValues[1].equals("x", ignoreCase = true)
        return task.groupValues[2] to checked
    }
    return text to null
}

private fun countRepeated(text: String, start: Int, char: Char): Int {
    var count = 0
    var i = start
    while (i < text.length && text[i] == char) {
        count += 1
        i += 1
    }
    return count
}

private fun findUnescapedDelimiter(text: String, delimiter: String, fromIndex: Int): Int {
    var index = fromIndex
    while (index < text.length) {
        val found = text.indexOf(delimiter, startIndex = index)
        if (found == -1) return -1
        if (!isEscaped(text, found)) return found
        index = found + delimiter.length
    }
    return -1
}

private fun isEscaped(text: String, index: Int): Boolean {
    var slashCount = 0
    var i = index - 1
    while (i >= 0 && text[i] == '\\') {
        slashCount += 1
        i -= 1
    }
    return slashCount % 2 == 1
}

private fun parseLinkAt(text: String, start: Int): LinkMatch? {
    if (start >= text.length || text[start] != '[') return null
    val labelEnd = findUnescapedChar(text, ']', start + 1) ?: return null
    if (labelEnd + 1 >= text.length || text[labelEnd + 1] != '(') return null
    val urlEnd = findUnescapedChar(text, ')', labelEnd + 2) ?: return null

    val label = text.substring(start + 1, labelEnd)
    val url = text.substring(labelEnd + 2, urlEnd).trim()
    if (label.isBlank() || url.isBlank()) return null

    return LinkMatch(
        label = label,
        url = url,
        endExclusive = urlEnd + 1
    )
}

private fun parseAutoLinkAt(text: String, start: Int): LinkMatch? {
    if (start < 0 || start >= text.length) return null

    val match = autoLinkRegex.find(text, start) ?: return null
    if (match.range.first != start) return null

    if (start > 0) {
        val previous = text[start - 1]
        if (previous.isLetterOrDigit() || previous == '_' || previous == '/') {
            return null
        }
    }

    val raw = match.value
    val normalized = trimTrailingLinkPunctuation(raw)
    if (normalized.isBlank()) return null

    return LinkMatch(
        label = normalized,
        url = normalized,
        endExclusive = start + normalized.length
    )
}

private fun trimTrailingLinkPunctuation(url: String): String {
    var end = url.length
    while (end > 0) {
        val last = url[end - 1]
        val shouldTrim = when (last) {
            '.', ',', ';', ':', '!', '?' -> true
            ')' -> {
                val segment = url.substring(0, end)
                segment.count { it == ')' } > segment.count { it == '(' }
            }
            ']' -> {
                val segment = url.substring(0, end)
                segment.count { it == ']' } > segment.count { it == '[' }
            }
            '}' -> {
                val segment = url.substring(0, end)
                segment.count { it == '}' } > segment.count { it == '{' }
            }
            else -> false
        }
        if (!shouldTrim) break
        end -= 1
    }
    return url.substring(0, end)
}

private fun findUnescapedChar(text: String, char: Char, fromIndex: Int): Int? {
    var index = fromIndex
    while (index < text.length) {
        if (text[index] == char && !isEscaped(text, index)) {
            return index
        }
        index += 1
    }
    return null
}

private fun mergeAdjacentSpans(spans: List<MdInlineSpan>): List<MdInlineSpan> {
    if (spans.isEmpty()) return emptyList()

    val merged = mutableListOf<MdInlineSpan>()
    spans.forEach { span ->
        if (span.text.isEmpty()) return@forEach

        val last = merged.lastOrNull()
        if (last != null && last.style == span.style) {
            merged[merged.lastIndex] = last.copy(text = last.text + span.text)
        } else {
            merged += span
        }
    }

    return merged
}
