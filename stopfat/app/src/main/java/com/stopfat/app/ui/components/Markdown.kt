package com.stopfat.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.stopfat.app.ui.theme.Glass

/** Minimal Markdown renderer for AI answers: headings, bullet / numbered lists and **bold**. */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = Glass.Text) {
    Column(modifier) {
        text.lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.height(6.dp))
                line.startsWith("#") -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        inline(line.trimStart('#').trim()),
                        style = if (line.startsWith("###")) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        color = Glass.Teal,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") || line.trimStart().startsWith("• ") -> {
                    val indent = (line.length - line.trimStart().length).coerceAtMost(8)
                    Row(Modifier.padding(start = (indent * 4).dp, top = 2.dp, bottom = 2.dp)) {
                        Text("•", color = Glass.Teal, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(8.dp))
                        Text(inline(line.trimStart().drop(2)), color = color, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Regex("^\\s*\\d+[.)] ").containsMatchIn(line) -> {
                    val m = Regex("^\\s*(\\d+)[.)] (.*)$").find(line)
                    Row(Modifier.padding(top = 2.dp, bottom = 2.dp)) {
                        Text("${m?.groupValues?.get(1) ?: ""}.", color = Glass.Teal, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(8.dp))
                        Text(inline(m?.groupValues?.get(2) ?: line), color = color, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                line.startsWith("---") -> Spacer(Modifier.height(8.dp))
                else -> Text(inline(line), color = color, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

private fun inline(s: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        val start = s.indexOf("**", i)
        if (start < 0) {
            append(s.substring(i)); break
        }
        val end = s.indexOf("**", start + 2)
        if (end < 0) {
            append(s.substring(i)); break
        }
        append(s.substring(i, start))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) { append(s.substring(start + 2, end)) }
        i = end + 2
    }
}
