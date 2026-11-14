package dev.johncastles.fanlet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.johncastles.fanlet.R
import dev.johncastles.fanlet.config.PieAppearance
import dev.johncastles.fanlet.ui.theme.FanletTheme
import kotlin.math.roundToInt

/**
 * Appearance of the pie. Every change is reported at once: the preview above it and the
 * next pie follow straight away.
 */
@Composable
fun PieSettingsCard(appearance: PieAppearance, onAppearanceChanged: (PieAppearance) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LabeledSlider(
                label = stringResource(R.string.pie_size),
                valueText = stringResource(R.string.value_percent, (appearance.sizeFraction * 100).roundToInt()),
                value = appearance.sizeFraction,
                range = PieAppearance.MIN_SIZE_FRACTION..PieAppearance.MAX_SIZE_FRACTION,
                onValueChange = { onAppearanceChanged(appearance.copy(sizeFraction = it.roundToHundredths())) },
            )
            Text(text = stringResource(R.string.pie_size_description), style = MaterialTheme.typography.bodyMedium)

            LabeledSlider(
                label = stringResource(R.string.icon_size),
                valueText = stringResource(R.string.value_percent, (appearance.iconSizeFraction * 100).roundToInt()),
                value = appearance.iconSizeFraction,
                range = PieAppearance.MIN_ICON_SIZE_FRACTION..PieAppearance.MAX_ICON_SIZE_FRACTION,
                onValueChange = { onAppearanceChanged(appearance.copy(iconSizeFraction = it.roundToHundredths())) },
            )
            Text(text = stringResource(R.string.icon_size_description), style = MaterialTheme.typography.bodyMedium)

            LabeledSlider(
                label = stringResource(R.string.secondary_icon_size),
                valueText = stringResource(R.string.value_percent, (appearance.secondaryIconScale * 100).roundToInt()),
                value = appearance.secondaryIconScale,
                range = PieAppearance.MIN_SECONDARY_ICON_SCALE..PieAppearance.MAX_SECONDARY_ICON_SCALE,
                onValueChange = { onAppearanceChanged(appearance.copy(secondaryIconScale = it.roundToHundredths())) },
            )
            Text(
                text = stringResource(R.string.secondary_icon_size_description),
                style = MaterialTheme.typography.bodyMedium,
            )

            LabeledSlider(
                label = stringResource(R.string.hold_time),
                valueText = stringResource(R.string.value_ms, appearance.holdMs),
                value = appearance.holdMs.toFloat(),
                range = PieAppearance.MIN_HOLD_MS.toFloat()..PieAppearance.MAX_HOLD_MS.toFloat(),
                // Steps of 50 ms are fine enough and easier to hit than single milliseconds.
                onValueChange = { onAppearanceChanged(appearance.copy(holdMs = (it / 50).roundToInt() * 50)) },
            )

            SwitchRow(
                title = stringResource(R.string.dim_background),
                description = stringResource(R.string.dim_background_description),
                checked = appearance.dimBackground,
                onCheckedChange = { onAppearanceChanged(appearance.copy(dimBackground = it)) },
            )
        }
    }
}

/** Keeps stored fractions tidy (0.35 rather than 0.34871). */
private fun Float.roundToHundredths(): Float = (this * 100).roundToInt() / 100f

@Preview(showBackground = true)
@Composable
private fun PieSettingsCardPreview() {
    FanletTheme {
        PieSettingsCard(appearance = PieAppearance(), onAppearanceChanged = {})
    }
}
