package dev.johncastles.fanlet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.johncastles.fanlet.R
import dev.johncastles.fanlet.config.BarConfig
import dev.johncastles.fanlet.config.Edge
import dev.johncastles.fanlet.ui.theme.FanletTheme
import kotlin.math.roundToInt

/** "Left bar" or "Right bar". */
@Composable
fun barTitle(bar: BarConfig): String = stringResource(
    when (bar.edge) {
        Edge.LEFT -> R.string.bar_left_title
        Edge.RIGHT -> R.string.bar_right_title
    }
)

/** Position and feel of one bar. Every change is reported at once, so the bar moves while dragging. */
@Composable
fun BarSettingsCard(bar: BarConfig, onBarChanged: (BarConfig) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SwitchRow(
                title = stringResource(R.string.bar_enabled),
                description = null,
                checked = bar.enabled,
                onCheckedChange = { onBarChanged(bar.copy(enabled = it)) },
            )
            if (bar.enabled) {
                LabeledSlider(
                    label = stringResource(R.string.bar_thickness),
                    valueText = stringResource(R.string.value_dp, bar.thicknessDp),
                    value = bar.thicknessDp.toFloat(),
                    range = BarConfig.MIN_THICKNESS_DP.toFloat()..BarConfig.MAX_THICKNESS_DP.toFloat(),
                    onValueChange = { onBarChanged(bar.copy(thicknessDp = it.roundToInt())) },
                )
                LabeledSlider(
                    label = stringResource(R.string.bar_length),
                    valueText = stringResource(R.string.value_percent, bar.lengthFraction.toPercent()),
                    value = bar.lengthFraction,
                    range = BarConfig.MIN_LENGTH_FRACTION..BarConfig.MAX_LENGTH_FRACTION,
                    onValueChange = { onBarChanged(bar.copy(lengthFraction = it.roundToHundredths())) },
                )
                LabeledSlider(
                    label = stringResource(R.string.bar_position),
                    valueText = stringResource(R.string.value_percent, bar.centerFraction.toPercent()),
                    value = bar.centerFraction,
                    range = 0f..1f,
                    onValueChange = { onBarChanged(bar.copy(centerFraction = it.roundToHundredths())) },
                )
                SwitchRow(
                    title = stringResource(R.string.bar_haptics),
                    description = null,
                    checked = bar.hapticsEnabled,
                    onCheckedChange = { onBarChanged(bar.copy(hapticsEnabled = it)) },
                )
            }
        }
    }
}

@Composable
internal fun SwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (description != null) {
                Text(text = description, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(text = valueText, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

private fun Float.toPercent(): Int = (this * 100).roundToInt()

/** Keeps stored fractions tidy (0.35 rather than 0.34871). */
private fun Float.roundToHundredths(): Float = (this * 100).roundToInt() / 100f

@Preview(showBackground = true)
@Composable
private fun BarSettingsCardPreview() {
    FanletTheme {
        BarSettingsCard(bar = BarConfig(id = "right", edge = Edge.RIGHT), onBarChanged = {})
    }
}
