package com.example.core.common.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.common.theme.ByokColorScheme

sealed class MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class BulletList(val items: List<String>) : MarkdownBlock()
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class ImageBlock(val alt: String, val url: String) : MarkdownBlock()
}

fun parseMarkdown(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = text.split("\n")
    var inCodeBlock = false
    var codeLanguage = ""
    val codeBuilder = java.lang.StringBuilder()
    val bulletBuilder = mutableListOf<String>()

    for (line in lines) {
        val trimmedLine = line.trim()
        
        // Code blocks delimiter check
        if (trimmedLine.startsWith("```")) {
            if (inCodeBlock) {
                blocks.add(MarkdownBlock.CodeBlock(codeLanguage, codeBuilder.toString().trimEnd()))
                codeBuilder.setLength(0)
                codeLanguage = ""
                inCodeBlock = false
            } else {
                inCodeBlock = true
                codeLanguage = trimmedLine.substring(3).trim()
            }
            continue
        }

        if (inCodeBlock) {
            codeBuilder.append(line).append("\n")
            continue
        }

        // Handle bullet lists
        if (trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ")) {
            val bulletText = trimmedLine.substring(2)
            bulletBuilder.add(bulletText)
            continue
        } else {
            if (bulletBuilder.isNotEmpty()) {
                blocks.add(MarkdownBlock.BulletList(bulletBuilder.toList()))
                bulletBuilder.clear()
            }
        }

        // Handle headers
        if (trimmedLine.startsWith("# ")) {
            blocks.add(MarkdownBlock.Header(1, trimmedLine.substring(2)))
        } else if (trimmedLine.startsWith("## ")) {
            blocks.add(MarkdownBlock.Header(2, trimmedLine.substring(3)))
        } else if (trimmedLine.startsWith("### ")) {
            blocks.add(MarkdownBlock.Header(3, trimmedLine.substring(4)))
        } else if (trimmedLine.startsWith("#### ")) {
            blocks.add(MarkdownBlock.Header(4, trimmedLine.substring(5)))
        } else if (trimmedLine.contains("![") && trimmedLine.contains("](") && trimmedLine.endsWith(")")) {
            val altStart = trimmedLine.indexOf("![") + 2
            val altEnd = trimmedLine.indexOf("](")
            val urlStart = altEnd + 2
            val urlEnd = trimmedLine.length - 1
            if (altEnd > altStart && urlEnd > urlStart) {
                blocks.add(MarkdownBlock.ImageBlock(
                    trimmedLine.substring(altStart, altEnd),
                    trimmedLine.substring(urlStart, urlEnd)
                ))
            } else {
                blocks.add(MarkdownBlock.Paragraph(line))
            }
        } else if (trimmedLine.startsWith("http") && (trimmedLine.endsWith(".png") || trimmedLine.endsWith(".jpg") || trimmedLine.endsWith(".jpeg") || trimmedLine.endsWith(".gif") || trimmedLine.endsWith(".webp") || trimmedLine.contains("/generations") || trimmedLine.contains("image_url"))) {
            blocks.add(MarkdownBlock.ImageBlock("Generated Artwork", trimmedLine))
        } else {
            if (line.isNotEmpty()) {
                blocks.add(MarkdownBlock.Paragraph(line))
            }
        }
    }

    if (inCodeBlock && codeBuilder.isNotEmpty()) {
        blocks.add(MarkdownBlock.CodeBlock(codeLanguage, codeBuilder.toString().trimEnd()))
    }
    if (bulletBuilder.isNotEmpty()) {
        blocks.add(MarkdownBlock.BulletList(bulletBuilder.toList()))
    }

    return blocks
}

@Composable
fun renderInlineMarkdown(text: String, colors: ByokColorScheme): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        while (cursor < text.length) {
            val boldStart = text.indexOf("**", cursor)
            val inlineCodeStart = text.indexOf("`", cursor)
            
            // Find closest trigger
            val triggers = listOf(
                boldStart to "**",
                inlineCodeStart to "`"
            ).filter { it.first >= cursor }.sortedBy { it.first }
            
            if (triggers.isEmpty()) {
                append(text.substring(cursor))
                break
            }
            
            val (nextTriggerIdx, triggerText) = triggers.first()
            if (nextTriggerIdx > cursor) {
                append(text.substring(cursor, nextTriggerIdx))
            }
            
            cursor = nextTriggerIdx + triggerText.length
            val triggerEnd = text.indexOf(triggerText, cursor)
            if (triggerEnd == -1) {
                // Unclosed tag helper
                append(triggerText)
                append(text.substring(cursor))
                break
            }
            
            val segmentText = text.substring(cursor, triggerEnd)
            if (triggerText == "**") {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.primaryAccent)) {
                    append(segmentText)
                }
            } else if (triggerText == "`") {
                withStyle(SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = colors.fieldBackground,
                    color = colors.primaryAccent,
                    fontSize = 13.sp
                )) {
                    append(" $segmentText ")
                }
            }
            cursor = triggerEnd + triggerText.length
        }
    }
}

@Composable
fun MarkdownText(
    text: String,
    colors: ByokColorScheme,
    modifier: Modifier = Modifier
) {
    val blocks = remember(text) { parseMarkdown(text) }
    val context = LocalContext.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Header -> {
                    val headerSize = when (block.level) {
                        1 -> 20.sp
                        2 -> 18.sp
                        3 -> 16.sp
                        else -> 14.sp
                    }
                    Text(
                        text = block.text,
                        fontSize = headerSize,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.primaryAccent,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = renderInlineMarkdown(block.text, colors),
                        fontSize = 15.sp,
                        color = colors.textPrimary,
                        lineHeight = 22.sp
                    )
                }
                is MarkdownBlock.BulletList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        block.items.forEach { itemText ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = "  •  ",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primaryAccent
                                )
                                Text(
                                    text = renderInlineMarkdown(itemText, colors),
                                    fontSize = 15.sp,
                                    color = colors.textPrimary,
                                    lineHeight = 22.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                is MarkdownBlock.CodeBlock -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.background),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.fieldBackground)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = block.language.ifEmpty { "code" }.uppercase(),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textSecondary
                                )
                                Text(
                                    text = "📋 Copy",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primaryAccent,
                                    modifier = Modifier
                                        .clickable {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Copied Code", block.code)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Code copied to clipboard!", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(4.dp)
                                )
                            }
                            HorizontalDivider(color = colors.border, thickness = 0.5.dp)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = block.code,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = colors.textPrimary,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
                is MarkdownBlock.ImageBlock -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.fieldBackground),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = block.alt.ifEmpty { "Generated Visual" },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primaryAccent,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            AsyncImage(
                                model = block.url,
                                contentDescription = block.alt,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 150.dp, max = 320.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.background),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }
    }
}
