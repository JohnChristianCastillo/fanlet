package dev.johncastles.fanlet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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

    // Swiping between layers: the pager and the selected tab follow each other.
    val pagerState = rememberPagerState(initialPage = layer) { tabCount }
    val currentLayer by rememberUpdatedState(layer)
    val currentOnSelectLayer by rememberUpdatedState(onSelectLayer)
    LaunchedEffect(layer) {
        if (pagerState.currentPage != layer) pagerState.animateScrollToPage(layer)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != currentLayer) currentOnSelectLayer(page)
        }
    }
    val shownTab = pagerState.currentPage.coerceIn(0, tabCount - 1)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text(stringResource(R.string.apps_title)) }, navigationIcon = { BackButton(onBack) })
                PrimaryScrollableTabRow(selectedTabIndex = shownTab, edgePadding = 16.dp) {
                    for (tab in 0 until tabCount) {
                        val isNew = tab == layerCount
                        Tab(
                            selected = tab == shownTab,
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
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            verticalAlignment = Alignment.Top,
        ) { page ->
            LayerPage(
                config = config,
                layer = page,
                updateConfig = updateConfig,
                onPickApp = onPickApp,
            )
        }
    }
}

/** One layer's slots (or the empty "new layer" page), with the controls to edit them. */
@Composable
private fun LayerPage(
    config: FanletConfig,
    layer: Int,
    updateConfig: ((FanletConfig) -> FanletConfig) -> Unit,
    onPickApp: (AppPickTarget) -> Unit,
) {
    val layerCount = config.layers.size
    Column(
        modifier = Modifier
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
    // One compact row per entry: | main icon | second icon + X | ... reorder / remove.
    // Icons only; the app name is the tap label for screen readers.
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SlotIcon(packageName = slice.primaryPackage, size = 48.dp, onClick = onChangePrimary)
            Spacer(modifier = Modifier.width(8.dp))
            SlotIcon(packageName = slice.secondaryPackage, size = 40.dp, onClick = onChooseSecondary)
            if (slice.secondaryPackage != null) {
                IconButton(onClick = onRemoveSecondary, modifier = Modifier.size(32.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.action_clear),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(painterResource(R.drawable.ic_arrow_up), stringResource(R.string.action_move_up))
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(painterResource(R.drawable.ic_arrow_down), stringResource(R.string.action_move_down))
            }
            IconButton(onClick = onRemove) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.action_remove_slice),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * One app slot as a tappable icon: the app's icon, a "+" in an empty slot, or the icon
 * in a red frame when the app is no longer installed. Tapping picks or changes the app.
 */
@Composable
private fun SlotIcon(packageName: String?, size: Dp, onClick: () -> Unit) {
    val lookup = if (packageName != null) rememberAppLookup(packageName) else null
    val description = when {
        packageName == null -> stringResource(R.string.action_add_secondary)
        lookup == AppLookup.Missing -> stringResource(R.string.app_not_installed)
        lookup != null -> appLabel(lookup, packageName)
        else -> packageName
    }
    val frame = when {
        lookup == AppLookup.Missing -> BorderStroke(2.dp, MaterialTheme.colorScheme.error)
        packageName == null -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        else -> null
    }
    Box(
        modifier = Modifier
            .size(size + 8.dp)
            .clip(CircleShape)
            .then(if (frame != null) Modifier.border(frame, CircleShape) else Modifier)
            .clickable(onClickLabel = stringResource(R.string.action_change_app), onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (lookup != null) {
            AppIcon(lookup = lookup, size = size)
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(size / 2),
            )
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
