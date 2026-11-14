package dev.johncastles.fanlet.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.johncastles.fanlet.R
import dev.johncastles.fanlet.apps.LaunchableApps
import dev.johncastles.fanlet.config.PieAppearance
import dev.johncastles.fanlet.ui.theme.FanletTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Appearance of the pie. Every change is reported at once and used by the next pie. */
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

            IconSizePreview(
                iconSizeFraction = appearance.iconSizeFraction,
                secondaryScale = appearance.secondaryIconScale,
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

/**
 * Two real app icons side by side in a sample slot, sized like in the pie: the main icon
 * fills [iconSizeFraction] of the slot, the second icon is [secondaryScale] of that. Real
 * slots differ per ring; the proportions are what this shows.
 */
@Composable
private fun IconSizePreview(iconSizeFraction: Float, secondaryScale: Float) {
    val primarySize = PREVIEW_SLOT_SIZE * iconSizeFraction
    val icons = rememberExampleIcons(count = 2, size = PREVIEW_SLOT_SIZE)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PREVIEW_SLOT_SIZE + 16.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ExampleIcon(icons.getOrNull(0), primarySize)
            ExampleIcon(icons.getOrNull(1), primarySize * secondaryScale)
        }
    }
}

/** Size of the sample slot in the preview. */
private val PREVIEW_SLOT_SIZE = 64.dp

/** Keeps stored fractions tidy (0.35 rather than 0.34871). */
private fun Float.roundToHundredths(): Float = (this * 100).roundToInt() / 100f

@Composable
private fun ExampleIcon(bitmap: ImageBitmap?, size: Dp) {
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .background(MaterialTheme.colorScheme.outline, CircleShape),
        )
    }
}

/** Icons of the first few launchable apps (not Fanlet), loaded off the main thread. */
@Composable
private fun rememberExampleIcons(count: Int, size: Dp): List<ImageBitmap> {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val icons by produceState(initialValue = emptyList<ImageBitmap>(), sizePx) {
        value = withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            LaunchableApps.query(packageManager)
                .filter { it.packageName != context.packageName }
                .take(count)
                .mapNotNull { app ->
                    try {
                        packageManager.getApplicationIcon(app.packageName).toBitmap(sizePx, sizePx).asImageBitmap()
                    } catch (e: PackageManager.NameNotFoundException) {
                        null // uninstalled in the meantime
                    }
                }
        }
    }
    return icons
}

@Preview(showBackground = true)
@Composable
private fun PieSettingsCardPreview() {
    FanletTheme {
        PieSettingsCard(appearance = PieAppearance(), onAppearanceChanged = {})
    }
}
