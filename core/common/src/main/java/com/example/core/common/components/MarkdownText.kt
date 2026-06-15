package com.example.core.common.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.core.common.theme.ByokColorScheme
import org.json.JSONArray
import org.json.JSONObject

sealed class MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class BulletList(val items: List<String>) : MarkdownBlock()
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class ImageBlock(val alt: String, val url: String) : MarkdownBlock()
    data class SvgBlock(val svgCode: String) : MarkdownBlock()
    data class ChartBlock(val type: String, val title: String, val labels: List<String>, val values: List<Float>) : MarkdownBlock()
}

fun tryParseChartBlock(code: String, language: String): MarkdownBlock? {
    val trimmed = code.trim()
    val langLower = language.lowercase()
    val isJsonStart = trimmed.startsWith("{") && trimmed.endsWith("}")
    
    if (langLower == "csv") {
        val lines = trimmed.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size >= 2) {
            val labels = mutableListOf<String>()
            val values = mutableListOf<Float>()
            var title = "Chart Statistics"
            
            val firstLineParts = lines[0].split(",").map { it.trim() }
            val hasHeaders = firstLineParts.size >= 2 && firstLineParts[1].toFloatOrNull() == null
            val startIndex = if (hasHeaders) 1 else 0
            if (hasHeaders) {
                title = firstLineParts[0] + " vs " + firstLineParts[1]
            }
            
            for (i in startIndex until lines.size) {
                val parts = lines[i].split(",").map { it.trim() }
                if (parts.size >= 2) {
                    val label = parts[0]
                    val floatVal = parts[1].toFloatOrNull()
                    if (floatVal != null) {
                        labels.add(label)
                        values.add(floatVal)
                    }
                }
            }
            if (labels.isNotEmpty()) {
                return MarkdownBlock.ChartBlock("bar", title, labels, values)
            }
        }
    } else if (isJsonStart || langLower == "chart") {
        try {
            val json = JSONObject(trimmed)
            val type = json.optString("type", "bar").lowercase()
            val title = json.optString("title", "Statistics")
            val labelsArray = json.optJSONArray("labels")
            val valuesArray = json.optJSONArray("values")
            
            if (labelsArray != null && valuesArray != null && labelsArray.length() == valuesArray.length()) {
                val labels = List(labelsArray.length()) { labelsArray.getString(it) }
                val values = List(valuesArray.length()) { valuesArray.optDouble(it, 0.0).toFloat() }
                return MarkdownBlock.ChartBlock(type, title, labels, values)
            }
            
            val dataArray = json.optJSONArray("data")
            if (dataArray != null && dataArray.length() > 0) {
                val labels = mutableListOf<String>()
                val values = mutableListOf<Float>()
                for (i in 0 until dataArray.length()) {
                    val obj = dataArray.getJSONObject(i)
                    labels.add(obj.optString("label", obj.optString("name", "Item $i")))
                    values.add(obj.optDouble("value", obj.optDouble("count", 0.0)).toFloat())
                }
                return MarkdownBlock.ChartBlock(type, title, labels, values)
            }
        } catch (e: Exception) {
            // silent fail
        }
    }
    return null
}

fun tryParseSpecialBlock(code: String, language: String): MarkdownBlock? {
    val trimmed = code.trim()
    val langLower = language.lowercase()
    
    val containsSvgTag = trimmed.contains("<svg", ignoreCase = true) && trimmed.contains("</svg>", ignoreCase = true)
    if (langLower == "svg" || 
        langLower == "xml" || 
        langLower == "html" || 
        (langLower.isEmpty() && containsSvgTag) ||
        containsSvgTag
    ) {
        return MarkdownBlock.SvgBlock(code)
    }
    
    return tryParseChartBlock(code, language)
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
        
        if (trimmedLine.startsWith("```")) {
            if (inCodeBlock) {
                val codeContent = codeBuilder.toString().trimEnd()
                val specialBlock = tryParseSpecialBlock(codeContent, codeLanguage)
                if (specialBlock != null) {
                    blocks.add(specialBlock)
                } else {
                    blocks.add(MarkdownBlock.CodeBlock(codeLanguage, codeContent))
                }
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
        val codeContent = codeBuilder.toString().trimEnd()
        val specialBlock = tryParseSpecialBlock(codeContent, codeLanguage)
        if (specialBlock != null) {
            blocks.add(specialBlock)
        } else {
            blocks.add(MarkdownBlock.CodeBlock(codeLanguage, codeContent))
        }
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
fun SvgRenderer(svgCode: String, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient = WebViewClient()
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.javaScriptEnabled = true
                setBackgroundColor(0)
            }
        },
        update = { webView ->
            val html = """
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                    <style>
                        html, body {
                            margin: 0;
                            padding: 0;
                            width: 100%;
                            height: 100%;
                            display: flex;
                            justify-content: center;
                            align-items: center;
                            background-color: transparent;
                            overflow: hidden;
                        }
                        svg {
                            max-width: 100%;
                            max-height: 100%;
                            display: block;
                        }
                    </style>
                </head>
                <body>
                    $svgCode
                </body>
                </html>
            """.trimIndent()
            val encodedHtml = android.util.Base64.encodeToString(
                html.toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
            )
            webView.loadData(encodedHtml, "text/html", "base64")
        },
        modifier = modifier
    )
}

@Composable
fun BeautifulChartRenderer(
    type: String,
    title: String,
    labels: List<String>,
    values: List<Float>,
    colors: ByokColorScheme,
    modifier: Modifier = Modifier
) {
    if (labels.isEmpty()) return
    val maxVal = remember(values) { (values.maxOrNull() ?: 1.0f).coerceAtLeast(1.0f) }

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.fieldBackground),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primaryAccent,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            when (type) {
                "bar" -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        labels.forEachIndexed { index, label ->
                            val value = values.getOrElse(index) { 0f }
                            val animatedHeightFraction by animateFloatAsState(
                                targetValue = value / maxVal,
                                animationSpec = androidx.compose.animation.core.tween(800),
                                label = "bar_height"
                            )

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.Bottom,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = value.toInt().toString(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(animatedHeightFraction.coerceAtLeast(0.05f))
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(
                                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                                colors = listOf(colors.primaryAccent, colors.primaryAccent.copy(alpha = 0.5f))
                                            )
                                        )
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                "line" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .padding(vertical = 8.dp)
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp, vertical = 12.dp)
                        ) {
                            val w = size.width
                            val h = size.height
                            val spacingX = w / (labels.size - 1).coerceAtLeast(1)
                            
                            val strokeColor = colors.primaryAccent
                            val fillColor = colors.primaryAccent.copy(alpha = 0.15f)
                            
                            val path = androidx.compose.ui.graphics.Path()
                            val fillPath = androidx.compose.ui.graphics.Path()
                            
                            labels.forEachIndexed { index, _ ->
                                val valNormalized = (values.getOrElse(index) { 0f } / maxVal)
                                val x = index * spacingX
                                val y = h - (valNormalized * h)
                                
                                if (index == 0) {
                                    path.moveTo(x, y)
                                    fillPath.moveTo(x, h)
                                    fillPath.lineTo(x, y)
                                } else {
                                    path.lineTo(x, y)
                                    fillPath.lineTo(x, y)
                                }
                                
                                if (index == labels.size - 1) {
                                    fillPath.lineTo(x, h)
                                    fillPath.close()
                                }
                            }
                            
                            if (labels.size > 1) {
                                drawPath(fillPath, brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(fillColor, Color.Transparent)))
                                drawPath(path, color = strokeColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
                            }
                            
                            labels.forEachIndexed { index, _ ->
                                val valNormalized = (values.getOrElse(index) { 0f } / maxVal)
                                val x = index * spacingX
                                val y = h - (valNormalized * h)
                                drawCircle(color = colors.background, radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
                                drawCircle(color = strokeColor, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
                            }
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        labels.forEachIndexed { index, label ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(label, fontSize = 10.sp, color = colors.textSecondary)
                                Text(values.getOrElse(index) { 0f }.toInt().toString(), fontSize = 9.sp, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                "pie" -> {
                    val total = values.sum().coerceAtLeast(1.0f)
                    val chartSlicesColors = listOf(
                        colors.primaryAccent,
                        Color(0xFF10B981),
                        Color(0xFF3B82F6),
                        Color(0xFFF59E0B),
                        Color(0xFF8B5CF6),
                        Color(0xFFEC4899)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                var currentAngle = -90f
                                values.forEachIndexed { index, value ->
                                    val sweep = (value / total) * 360f
                                    val color = chartSlicesColors[index % chartSlicesColors.size]
                                    drawArc(
                                        color = color,
                                        startAngle = currentAngle,
                                        sweepAngle = sweep,
                                        useCenter = true
                                    )
                                    currentAngle += sweep
                                }
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            labels.forEachIndexed { index, label ->
                                val value = values.getOrElse(index) { 0f }
                                val percentage = ((value / total) * 100).toInt()
                                val color = chartSlicesColors[index % chartSlicesColors.size]

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
                                    Text(
                                        text = "$label: $value ($percentage%)",
                                        fontSize = 11.sp,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Medium
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
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
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
                is MarkdownBlock.SvgBlock -> {
                    var showRawCode by remember { mutableStateOf(false) }
                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.fieldBackground),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("🎨 Rendered SVG Visual", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.primaryAccent)
                                }
                                Text(
                                    text = if (showRawCode) "⚡ View visual" else "⚡ View XML code",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primaryAccent,
                                    modifier = Modifier
                                        .clickable { showRawCode = !showRawCode }
                                        .padding(4.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            AnimatedContent(targetState = showRawCode, label = "svg_raw_toggle") { raw ->
                                if (raw) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 200.dp)
                                            .horizontalScroll(rememberScrollState())
                                            .background(colors.background, RoundedCornerShape(8.dp))
                                            .border(0.5.dp, colors.border, RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = block.svgCode,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = colors.textPrimary,
                                            lineHeight = 16.sp
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(260.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.background)
                                            .border(0.5.dp, colors.border, RoundedCornerShape(8.dp))
                                            .padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        SvgRenderer(
                                            svgCode = block.svgCode,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                is MarkdownBlock.ChartBlock -> {
                    BeautifulChartRenderer(
                        type = block.type,
                        title = block.title,
                        labels = block.labels,
                        values = block.values,
                        colors = colors,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
