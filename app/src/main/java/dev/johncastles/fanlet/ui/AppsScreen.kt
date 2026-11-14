package dev.johncastles.fanlet.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
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
 * The pie's apps, shared by both bars: a tab per layer (layer 1 is the inner ring), plus
 * a tab to start the next layer while there are fewer than the maximum. Each tab lists
 * that layer's slots, top slot first.
 *
 * @param selectedLayer the open tab; may point at the "new layer" tab (index layers.size).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    config: FanletConfig,
    selectedLayer: Int,
    onSelectLayer: (Int) -> Unit,
    updateConfig: ((FanletConfig) -> FanletConfig) -> Unit,
    onPickApp: (AppPickTarget) -> Unit,
    onBack: () -> Unit,
) {
    val layerCount = config.layers.size
    val showNewLayerTab = layerCount < FanletConfig.MAX_LAYERS
    val tabCount = layerCount + if (showNewLayerTab) 1 else 0
    // Removing a layer's last app can remove the open tab; fall back to the nearest one.
    val layer = selectedLayer.coerceIn(0, tabCount - 1)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text(stringResource(R.string.apps_title)) }, navigationIcon = { BackButton(onBack) })
                PrimaryScrollableTabRow(selectedTabIndex = layer, edgePadding = 16.dp) {
                    for (tab in 0 until tabCount) {
                        val isNew = tab == layerCount
                        Tab(
                            selected = tab == layer,
                            onClick = { onSelectLayer(tab) },
                            text = {
                                Text(
                                    stringResource(
                                        if (isNew) R.string.layer_tab_new else R.string.layer_tab,
                                        tab + 1,
                                    )
                                )
                            },
                        )
                    }
                }
            }
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
            val slots = config.layers.getOrNull(layer)?.slices.orEmpty()
            Text(
                text = when {
                    layerCount == 0 -> stringResource(R.string.apps_empty_hint)
                    layer == layerCount -> stringResource(R.string.layer_new_hint, layer + 1)
                    else -> stringResource(R.string.apps_description)
                },
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
                .withSlotSecondary(0, 0, "org.example.chat")
                .withSlotAdded(1, "org.example.camera"),
            selectedLayer = 0,
            onSelectLayer = {},
            updateConfig = {},
            onPickApp = {},
            onBack = {},
        )
    }
}
