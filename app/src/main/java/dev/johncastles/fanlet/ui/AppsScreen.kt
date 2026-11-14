package dev.johncastles.fanlet.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.johncastles.fanlet.R
import dev.johncastles.fanlet.config.FanletConfig
import dev.johncastles.fanlet.config.SliceConfig
import dev.johncastles.fanlet.ui.theme.FanletTheme

/**
 * Which app the picker is choosing: for a new slot in [layer] ([slotIndex] null), or a
 * slot's main or second app.
 */
data class AppPickTarget(val layer: Int, val slotIndex: Int?, val secondary: Boolean)

/**
 * The pie's apps, shared by both bars, top slot first. Edits the innermost layer only;
 * tabs for the other layers come in a later milestone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    config: FanletConfig,
    updateConfig: ((FanletConfig) -> FanletConfig) -> Unit,
    onPickApp: (AppPickTarget) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.apps_title)) }, navigationIcon = { BackButton(onBack) })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val layer = 0
            val slots = config.layers.getOrNull(layer)?.slices.orEmpty()
            Text(
                text = stringResource(if (slots.isEmpty()) R.string.apps_empty_hint else R.string.apps_description),
                style = MaterialTheme.typography.bodyMedium,
            )
            slots.forEachIndexed { index, slot ->
                SliceCard(
                    slice = slot,
                    canMoveUp = index > 0,
                    canMoveDown = index < slots.lastIndex,
                    onChangePrimary = { onPickApp(AppPickTarget(layer, index, secondary = false)) },
                    onChooseSecondary = { onPickApp(AppPickTarget(layer, index, secondary = true)) },
                    onRemoveSecondary = { updateConfig { it.withSlotSecondary(layer, index, null) } },
                    onMoveUp = { updateConfig { it.withSlotMoved(layer, index, index - 1) } },
                    onMoveDown = { updateConfig { it.withSlotMoved(layer, index, index + 1) } },
                    onRemove = { updateConfig { it.withSlotRemoved(layer, index) } },
                )
            }
            if (config.canAddSlot(layer)) {
                Button(onClick = { onPickApp(AppPickTarget(layer, slotIndex = null, secondary = false)) }) {
                    Text(stringResource(R.string.action_add_app))
                }
            } else {
                Text(
                    text = pluralStringResource(
                        R.plurals.slices_full, FanletConfig.MAX_SLOTS_PER_LAYER, FanletConfig.MAX_SLOTS_PER_LAYER,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
    }
}

@Composable
private fun SliceCard(
    slice: SliceConfig,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onChangePrimary: () -> Unit,
    onChooseSecondary: () -> Unit,
    onRemoveSecondary: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppEntry(
                    packageName = slice.primaryPackage,
                    iconSize = 40.dp,
                    onClick = onChangePrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(painterResource(R.drawable.ic_arrow_up), stringResource(R.string.action_move_up))
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(painterResource(R.drawable.ic_arrow_down), stringResource(R.string.action_move_down))
                }
            }

            val secondary = slice.secondaryPackage
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (secondary != null) {
                    Text(
                        text = stringResource(R.string.secondary_label),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    AppEntry(
                        packageName = secondary,
                        iconSize = 28.dp,
                        onClick = onChooseSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRemoveSecondary) { Text(stringResource(R.string.action_remove)) }
                } else {
                    TextButton(onClick = onChooseSecondary) { Text(stringResource(R.string.action_add_secondary)) }
                    Spacer(modifier = Modifier.weight(1f))
                }
                TextButton(
                    onClick = onRemove,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.action_remove_slice))
                }
            }
        }
    }
}

/** An app's icon and name; marks apps that are no longer installed. Tapping changes the app. */
@Composable
private fun AppEntry(packageName: String, iconSize: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val lookup = rememberAppLookup(packageName)
    Row(
        modifier = modifier
            .clickable(onClickLabel = stringResource(R.string.action_change_app), onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(lookup = lookup, size = iconSize)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = appLabel(lookup, packageName), style = MaterialTheme.typography.bodyLarge)
            if (lookup == AppLookup.Missing) {
                Text(
                    text = stringResource(R.string.app_not_installed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppsScreenPreview() {
    FanletTheme {
        AppsScreen(
            config = FanletConfig.DEFAULT
                .withSlotAdded(0, "org.example.mail")
                .withSlotAdded(0, "org.example.maps")
                .withSlotSecondary(0, 0, "org.example.chat"),
            updateConfig = {},
            onPickApp = {},
            onBack = {},
        )
    }
}
