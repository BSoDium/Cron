@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.screens.home.AiIterationUi
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing

/**
 * Tab-like connected button group listing every planning iteration by time. Selecting a tab swaps the
 * thread shown below it, so the overnight history is navigable from the home screen; the selected
 * iteration's trigger ("Your schedule changed", …) reads as a muted caption above the group.
 */
@Composable
internal fun ReplanHistoryBar(
    iterations: List<AiIterationUi>,
    selectedTurn: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = iterations.firstOrNull { it.turnIndex == selectedTurn } ?: iterations.last()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = selected.systemMessage,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            iterations.forEachIndexed { index, iteration ->
                ToggleButton(
                    checked = iteration.turnIndex == selectedTurn,
                    onCheckedChange = { onSelect(iteration.turnIndex) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .semantics { role = Role.RadioButton },
                    elevation = null, // flat — depth comes from container tone, never a shadow
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        iterations.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                ) {
                    Text(iteration.timeLabel, maxLines = 1)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReplanHistoryBarPreview() {
    CronTheme {
        ReplanHistoryBar(
            iterations = listOf(
                PREVIEW_ITER(0, "21:30", "Planned"),
                PREVIEW_ITER(1, "23:10", "Your schedule changed"),
                PREVIEW_ITER(2, "02:10", "A good moment to wake"),
            ),
            selectedTurn = 1,
            onSelect = {},
        )
    }
}

@Suppress("FunctionName")
private fun PREVIEW_ITER(turn: Int, time: String, message: String) = AiIterationUi(
    turnIndex = turn,
    timeLabel = time,
    systemMessage = message,
    thread = AiThreadUi(turnIndex = turn, summary = message, process = emptyList(), response = null),
)
