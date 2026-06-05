package com.llmapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.datatransfer.StringSelection
import kotlin.time.Duration.Companion.seconds

@Composable
fun FormattedMessage(content: String) {
    val contentWithLineBreaks = content.replace(Regex("(?i)<br\\s*/?>"), "\n")
    val parsedElements = remember(contentWithLineBreaks) {
        parseMarkdownAndHtml(contentWithLineBreaks)
    }

    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            parsedElements.forEach { element ->
                when (element) {
                    is ParsedElement.Text -> {
                        val lines = element.content.split("\n")
                        lines.forEachIndexed { index, line ->
                            if (index > 0) Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                                        append(line)
                                    }
                                },
                                fontSize = 14.sp
                            )
                        }
                    }

                    is ParsedElement.Heading -> {
                        Text(
                            text = element.content,
                            fontSize = when (element.level) {
                                1 -> 24.sp
                                2 -> 20.sp
                                3 -> 18.sp
                                else -> 16.sp
                            },
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    is ParsedElement.Bold -> {
                        Text(
                            text = element.content,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    is ParsedElement.Italic -> {
                        Text(
                            text = element.content,
                            fontSize = 14.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }

                    is ParsedElement.Code -> {
                        CodeBlock(
                            code = element.code,
                            language = element.language
                        )
                    }

                    is ParsedElement.Link -> {
                        Text(
                            text = buildAnnotatedString {
                                pushStringAnnotation(tag = "URL", annotation = element.url)
                                withStyle(
                                    SpanStyle(
                                        color = MaterialTheme.colorScheme.primary,
                                        textDecoration = TextDecoration.Underline
                                    )
                                ) {
                                    append(element.text)
                                }
                                pop()
                            },
                            fontSize = 14.sp
                        )
                    }

                    is ParsedElement.ListElement -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            element.items.forEach { item: String ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("•", fontSize = 14.sp)
                                    Text(
                                        text = item,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    is ParsedElement.Blockquote -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = element.content,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is ParsedElement.HorizontalRule -> {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    is ParsedElement.Table -> {
                        TableBlock(table = element)
                    }
                }
            }
        }
    }
}

@Composable
fun TableBlock(table: ParsedElement.Table) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    table.headers.forEachIndexed { index, header ->
                        Text(
                            text = parseInlineMarkdownToAnnotatedString(header),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (index < table.headers.size - 1)
                                        Modifier.padding(end = 8.dp)
                                    else Modifier
                                )
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                thickness = 1.dp
            )

            table.rows.forEachIndexed { index, row ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (index % 2 == 0)
                        MaterialTheme.colorScheme.surface
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        row.forEachIndexed { cellIndex, cell ->
                            val cellWithLineBreaks = cell.replace(Regex("(?i)<br\\s*/?>"), "\n")
                            Text(
                                text = parseInlineMarkdownToAnnotatedString(cellWithLineBreaks),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .weight(1f)
                                    .then(
                                        if (cellIndex < row.size - 1)
                                            Modifier.padding(end = 8.dp)
                                        else Modifier
                                    )
                            )
                        }
                    }
                }

                if (index < table.rows.size - 1) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CodeBlock(code: String, language: String?) {
    var showCopied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2D2D2D))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language?.uppercase() ?: "CODE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6A9955),
                    fontFamily = FontFamily.Monospace
                )

                IconButton(
                    onClick = {
                        scope.launch {
                            val transferable = StringSelection(code)
                            val clipEntry = ClipEntry(transferable)
                            clipboard.setClipEntry(clipEntry)
                            showCopied = true
                            delay(2.seconds)
                            showCopied = false
                        }
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        if (showCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        modifier = Modifier.size(16.dp),
                        tint = if (showCopied) Color(0xFF4CAF50) else Color.White
                    )
                }
            }

            SelectionContainer {
                Text(
                    text = code,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFD4D4D4),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

sealed class ParsedElement {
    data class Text(val content: String) : ParsedElement()
    data class Heading(val content: String, val level: Int) : ParsedElement()
    data class Bold(val content: String) : ParsedElement()
    data class Italic(val content: String) : ParsedElement()
    data class Code(val code: String, val language: String?) : ParsedElement()
    data class Link(val text: String, val url: String) : ParsedElement()
    data class ListElement(val items: List<String>) : ParsedElement()
    data class Blockquote(val content: String) : ParsedElement()
    object HorizontalRule : ParsedElement()
    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>
    ) : ParsedElement()
}

fun parseMarkdownAndHtml(content: String): List<ParsedElement> {
    val elements = mutableListOf<ParsedElement>()
    val lines = content.lines()

    var inCodeBlock = false
    val currentCodeBlock = StringBuilder()
    var currentLanguage: String? = null

    var inList = false
    val currentListItems = mutableListOf<String>()

    var inTable = false
    val tableHeaders = mutableListOf<String>()
    val tableRows = mutableListOf<List<String>>()
    var tableAlignmentRow = false

    var i = 0
    while (i < lines.size) {
        val line = lines[i]

        if (line.trim().startsWith("```")) {
            if (!inCodeBlock) {
                flushTableIfNeeded(elements, tableHeaders, tableRows, inTable)
                inTable = false
                tableAlignmentRow = false

                inCodeBlock = true
                currentCodeBlock.clear()
                currentLanguage = line.trim().drop(3).takeIf { it.isNotEmpty() }
            } else {
                inCodeBlock = false
                if (currentCodeBlock.isNotEmpty()) {
                    elements.add(
                        ParsedElement.Code(
                            currentCodeBlock.toString().trimEnd(),
                            currentLanguage
                        )
                    )
                    currentCodeBlock.clear()
                    currentLanguage = null
                }
            }
            i++
            continue
        }

        if (inCodeBlock) {
            currentCodeBlock.appendLine(line)
            i++
            continue
        }

        if (line.trim().startsWith("|") && line.trim().endsWith("|")) {
            val cells = line.trim()
                .removeSurrounding("|")
                .split("|")
                .map { it.trim() }

            if (cells.all { it.matches(Regex("^:?-{3,}:?$")) }) {
                tableAlignmentRow = true
                i++
                continue
            }

            if (!inTable) {
                flushListIfNeeded(elements, currentListItems, inList)
                inList = false

                inTable = true
                tableHeaders.clear()
                tableRows.clear()
                tableAlignmentRow = false
                tableHeaders.addAll(cells)
            } else {
                if (tableAlignmentRow || tableHeaders.isNotEmpty()) {
                    tableRows.add(cells)
                    tableAlignmentRow = false
                }
            }
            i++
            continue
        } else {
            flushTableIfNeeded(elements, tableHeaders, tableRows, inTable)
            inTable = false
            tableAlignmentRow = false
        }

        if (line.trim() == "---" || line.trim() == "***" || line.trim() == "___") {
            flushListIfNeeded(elements, currentListItems, inList)
            inList = false
            elements.add(ParsedElement.HorizontalRule)
            i++
            continue
        }

        val headingMatch = Regex("^(#{1,6})\\s+(.+)$").find(line.trim())
        if (headingMatch != null) {
            flushListIfNeeded(elements, currentListItems, inList)
            inList = false
            val level = headingMatch.groupValues[1].length
            val headingText = headingMatch.groupValues[2]
            elements.add(ParsedElement.Heading(parseInlineMarkdown(headingText), level))
            i++
            continue
        }

        if (line.trim().startsWith("> ")) {
            flushListIfNeeded(elements, currentListItems, inList)
            inList = false
            val quoteContent = line.trim().drop(2)
            elements.add(ParsedElement.Blockquote(parseInlineMarkdown(quoteContent)))
            i++
            continue
        }

        val listMatch = Regex("^\\s*[-*+]\\s+(.+)$").find(line)
        if (listMatch != null) {
            inList = true
            val itemContent = parseInlineMarkdown(listMatch.groupValues[1])
            currentListItems.add(itemContent)
            i++
            continue
        }

        if (inList) {
            flushListIfNeeded(elements, currentListItems, true)
            inList = false
        }

        if (line.isNotBlank()) {
            val processedLine = parseInlineMarkdown(line)
            elements.add(ParsedElement.Text(processedLine))
        } else if (line.isBlank() && elements.isNotEmpty() && elements.last() !is ParsedElement.Text) {
            elements.add(ParsedElement.Text(""))
        }

        i++
    }

    flushListIfNeeded(elements, currentListItems, inList)
    flushTableIfNeeded(elements, tableHeaders, tableRows, inTable)

    return elements
}

private fun flushTableIfNeeded(
    elements: MutableList<ParsedElement>,
    headers: MutableList<String>,
    rows: MutableList<List<String>>,
    inTable: Boolean
) {
    if (inTable && headers.isNotEmpty()) {
        elements.add(
            ParsedElement.Table(
                headers = headers.toList(),
                rows = rows.toList()
            )
        )
        headers.clear()
        rows.clear()
    }
}

private fun flushListIfNeeded(
    elements: MutableList<ParsedElement>,
    currentListItems: MutableList<String>,
    inList: Boolean
) {
    if (inList && currentListItems.isNotEmpty()) {
        elements.add(ParsedElement.ListElement(currentListItems.toList()))
        currentListItems.clear()
    }
}

fun parseInlineMarkdown(text: String): String {
    var result = text

    // HTML line break
    result = result.replace(
        Regex("(?i)<br\\s*/?>"),
        "\n"
    )

    // HTML bold
    result = result.replace(Regex("(?i)<b>(.+?)</b>"), "**$1**")
        .replace(Regex("(?i)<strong>(.+?)</strong>"), "**$1**")

    // HTML italic
    result = result.replace(Regex("(?i)<i>(.+?)</i>"), "*$1*")
        .replace(Regex("(?i)<em>(.+?)</em>"), "*$1*")

    // HTML code
    result = result.replace(Regex("(?i)<code>(.+?)</code>"), "`$1`")

    // Markdown formatting
    result = result.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        .replace(Regex("__(.+?)__"), "$1")
        .replace(Regex("\\*(.+?)\\*"), "$1")
        .replace(Regex("_(.+?)_"), "$1")
        .replace(Regex("`(.+?)`"), "$1")

    return result
}

@Composable
fun parseInlineMarkdownToAnnotatedString(text: String): AnnotatedString {
    val processedText = text
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)<b>(.+?)</b>"), "**$1**")
        .replace(Regex("(?i)<strong>(.+?)</strong>"), "**$1**")
        .replace(Regex("(?i)<i>(.+?)</i>"), "*$1*")
        .replace(Regex("(?i)<em>(.+?)</em>"), "*$1*")
        .replace(Regex("(?i)<code>(.+?)</code>"), "`$1`")

    return buildAnnotatedString {
        val boldPattern = Regex("\\*\\*(.+?)\\*\\*")
        val italicPattern = Regex("\\*(.+?)\\*")
        val codePattern = Regex("`(.+?)`")

        val matches = mutableListOf<Pair<IntRange, Triple<String, String, Int>>>()

        boldPattern.findAll(processedText).forEach { match ->
            matches.add(match.range to Triple("bold", match.groupValues[1], 1))
        }
        italicPattern.findAll(processedText).forEach { match ->
            matches.add(match.range to Triple("italic", match.groupValues[1], 2))
        }
        codePattern.findAll(processedText).forEach { match ->
            matches.add(match.range to Triple("code", match.groupValues[1], 3))
        }

        matches.sortWith(compareBy({ it.first.first }, { it.second.third }))

        var position = 0
        val usedRanges = mutableSetOf<IntRange>()

        for ((range, triple) in matches) {
            if (range.first < position) continue

            var overlaps = false
            for (used in usedRanges) {
                if (range.intersect(used).isNotEmpty()) {
                    overlaps = true
                    break
                }
            }
            if (overlaps) continue

            if (position < range.first) {
                append(processedText.substring(position, range.first))
            }

            when (triple.first) {
                "bold" -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(triple.second)
                    }
                }

                "italic" -> {
                    withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                        append(triple.second)
                    }
                }

                "code" -> {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0xFFEEEEEE)
                        )
                    ) {
                        append(triple.second)
                    }
                }
            }

            position = range.last + 1
            usedRanges.add(range)
        }

        if (position < processedText.length) {
            append(processedText.substring(position))
        }
    }
}
