package fr.bsodium.cron.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import fr.bsodium.cron.ui.screens.home.SmokeState

private sealed class Block {
    data class Paragraph(val text: String) : Block()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : Block()
    data class Blockquote(val text: String) : Block()
    data class Heading(val level: Int, val text: String) : Block()
    object HorizontalRule : Block()
}

@Composable
fun AiDebugCard(
    hasKey: Boolean,
    smokeState: SmokeState,
    onSaveKey: (String) -> Unit,
    onRunSmoke: () -> Unit,
    modifier: Modifier = Modifier,
    routesApiKey: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (!hasKey) {
            ApiKeyEntry(onSaveKey = onSaveKey)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Anthropic key: stored",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { onSaveKey("") }) { Text("Clear") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRunSmoke,
                enabled = smokeState !is SmokeState.Running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when (smokeState) {
                        is SmokeState.Running -> "Running…"
                        else -> "Run AI turn now"
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        SmokeResult(smokeState, routesApiKey)
    }
}

@Composable
private fun ApiKeyEntry(onSaveKey: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    OutlinedTextField(
        value = key,
        onValueChange = { key = it.trim() },
        label = { Text("Anthropic API key") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Button(
        onClick = { onSaveKey(key) },
        enabled = key.startsWith("sk-") && key.length > 20,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Save key")
    }
}

@Composable
private fun SmokeResult(state: SmokeState, routesApiKey: String?) {
    when (state) {
        SmokeState.Idle -> Unit
        SmokeState.Running -> Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp),
            )
        }
        is SmokeState.Success -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Round-trips: ${state.roundTrips}",
                style = MaterialTheme.typography.labelSmall,
            )
            for (block in parseBlocks(state.text)) {
                when (block) {
                    is Block.Paragraph -> Text(
                        text = parseBold(block.text.trim()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    is Block.Table -> TableBlock(block)
                    is Block.Blockquote -> BlockquoteBlock(block)
                    is Block.Heading -> Text(
                        text = parseBold(block.text),
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.titleMedium
                            2 -> MaterialTheme.typography.titleSmall
                            else -> MaterialTheme.typography.labelMedium
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                    is Block.HorizontalRule -> HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
            if (state.originLat != null && state.originLng != null &&
                state.destination != null && routesApiKey != null
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                RouteMapCard(
                    originLat = state.originLat,
                    originLng = state.originLng,
                    destination = state.destination,
                    apiKey = routesApiKey,
                )
            }
        }
        is SmokeState.Failure -> {
            Text(
                text = "Error: ${state.message}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun TableBlock(table: Block.Table) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (header in table.headers) {
                    Text(
                        text = parseBold(header),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            for (row in table.rows) {
                val padded = List(table.headers.size) { i -> row.getOrElse(i) { "" } }
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (cell in padded) {
                        Text(
                            text = parseBold(cell),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockquoteBlock(block: Block.Blockquote) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(2.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
        Text(
            text = parseBold(block.text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RouteMapCard(
    originLat: Double,
    originLng: Double,
    destination: String,
    apiKey: String,
) {
    val staticUrl = "https://maps.googleapis.com/maps/api/staticmap?" +
        "size=600x220" +
        "&markers=color:blue%7Clabel:H%7C$originLat,$originLng" +
        "&markers=color:red%7Clabel:D%7C${Uri.encode(destination)}" +
        "&key=$apiKey"
    val mapsUrl = "https://www.google.com/maps/dir/?api=1" +
        "&origin=$originLat,$originLng" +
        "&destination=${Uri.encode(destination)}" +
        "&travelmode=transit"

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AsyncImage(
            model = staticUrl,
            contentDescription = "Route map",
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
        val ctx = LocalContext.current
        TextButton(
            onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl))) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open transit route in Maps →")
        }
    }
}

private fun parseBlocks(text: String): List<Block> {
    val blocks = mutableListOf<Block>()
    var paraLines = mutableListOf<String>()
    var quoteLines = mutableListOf<String>()
    var tableHeaders: List<String>? = null
    var tableRows = mutableListOf<List<String>>()

    fun flushPara() {
        if (paraLines.isNotEmpty()) {
            blocks.add(Block.Paragraph(paraLines.joinToString("\n")))
            paraLines = mutableListOf()
        }
    }

    fun flushQuote() {
        if (quoteLines.isNotEmpty()) {
            blocks.add(Block.Blockquote(quoteLines.joinToString("\n")))
            quoteLines = mutableListOf()
        }
    }

    fun flushTable() {
        val h = tableHeaders ?: return
        blocks.add(Block.Table(h, tableRows.toList()))
        tableHeaders = null
        tableRows = mutableListOf()
    }

    fun parseCells(line: String): List<String> =
        line.split("|").map { it.trim() }.filter { it.isNotEmpty() }

    fun isSeparatorRow(line: String): Boolean =
        line.split("|").drop(1).dropLast(1).all { cell ->
            cell.trim().matches(Regex("^[-:]+$"))
        }

    val headingRegex = Regex("^(#{1,3}) (.+)$")
    val hRuleTrimmed = setOf("---", "***", "___")

    for (line in text.lines()) {
        val trimmed = line.trim()
        val isTableRow = line.startsWith("|") && line.trimEnd().endsWith("|")
        val isSep = isTableRow && isSeparatorRow(line)
        val isQuote = line.startsWith("> ")
        val headingMatch = headingRegex.matchEntire(trimmed)
        val isHRule = trimmed in hRuleTrimmed

        when {
            isSep -> Unit
            isTableRow -> {
                flushPara()
                flushQuote()
                val cells = parseCells(line)
                if (cells.isNotEmpty()) {
                    if (tableHeaders == null) tableHeaders = cells else tableRows.add(cells)
                }
            }
            isQuote -> {
                flushPara()
                flushTable()
                quoteLines.add(line.removePrefix("> "))
            }
            headingMatch != null -> {
                flushPara()
                flushTable()
                flushQuote()
                val level = headingMatch.groupValues[1].length
                blocks.add(Block.Heading(level, headingMatch.groupValues[2]))
            }
            isHRule -> {
                flushPara()
                flushTable()
                flushQuote()
                blocks.add(Block.HorizontalRule)
            }
            else -> {
                flushTable()
                flushQuote()
                paraLines.add(line)
            }
        }
    }

    flushPara()
    flushTable()
    flushQuote()
    return blocks
}

private fun parseBold(text: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    while (cursor < text.length) {
        val open = text.indexOf("**", cursor)
        if (open == -1) { append(text.substring(cursor)); break }
        append(text.substring(cursor, open))
        val close = text.indexOf("**", open + 2)
        if (close == -1) { append(text.substring(open)); break }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(text.substring(open + 2, close))
        }
        cursor = close + 2
    }
}
