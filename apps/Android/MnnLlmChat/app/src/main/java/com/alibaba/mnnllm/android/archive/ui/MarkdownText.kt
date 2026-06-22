package com.alibaba.mnnllm.android.archive.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Lightweight Markdown renderer for Compose — supports the subset the app produces:
 * `# / ## headings`, `- / * bullets`, `**bold**`, and plain paragraphs.
 * Keeps OCR/structured output readable without pulling in a TextView-based lib.
 */
@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier) {
        markdown.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd()
            when {
                line.isBlank() -> Text("", fontSize = 6.sp)
                line.startsWith("## ") ->
                    Text(line.removePrefix("## "), fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
                line.startsWith("# ") ->
                    Text(line.removePrefix("# "), fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                line.startsWith("- ") || line.startsWith("* ") ->
                    Row(Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)) {
                        Text("•  ", color = cs.primary, fontSize = 14.sp)
                        Text(parseInline(line.drop(2)), fontSize = 14.sp)
                    }
                else -> Text(parseInline(line), fontSize = 14.sp, modifier = Modifier.padding(vertical = 1.dp))
            }
        }
    }
}

/** Parses **bold** inline spans. */
private fun parseInline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val start = text.indexOf("**", i)
        if (start < 0) { append(text.substring(i)); break }
        append(text.substring(i, start))
        val end = text.indexOf("**", start + 2)
        if (end < 0) { append(text.substring(start)); break }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(text.substring(start + 2, end))
        }
        i = end + 2
    }
}
