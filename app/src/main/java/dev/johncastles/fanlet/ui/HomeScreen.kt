package dev.johncastles.fanlet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.johncastles.fanlet.R
import dev.johncastles.fanlet.config.FanletConfig
import dev.johncastles.fanlet.ui.theme.FanletTheme
import kotlin.math.roundToInt

/** Everything the home screen shows. Built by the activity, which owns the real state. */
data class HomeState(
    val overlayAllowed: Boolean,
    /** False on Android 12 and older, where notifications need no permission. */
    val showNotificationPermission: Boolean,
    val notificationsAllowed: Boolean,
    /** The user declined the notification request; Fanlet does not ask again. */
    val notificationsDenied: Boolean,
    val serviceRunning: Boolean,
    val servicePaused: Boolean,
    /** Android may put Fanlet to sleep in the background (battery optimization is on). */
    val batteryOptimized: Boolean,
    val config: FanletConfig,
)

/**
 * The first screen: the on/off switch and short lists that open the other screens.
 * Permission cards only appear while something needs to be done.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    snackbarHostState: SnackbarHostState,
    onOpenOverlaySettings: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onServiceToggled: (Boolean) -> Unit,
    onShowBarsChanged: (Boolean) -> Unit,
    onOpenApps: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenBar: (barId: String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val config = state.config
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)

            if (!state.overlayAllowed) {
                ActionCard(
                    title = stringResource(R.string.overlay_permission_title),
                    description = stringResource(R.string.overlay_permission_description),
                    actionLabel = stringResource(R.string.action_open_settings),
                    onAction = onOpenOverlaySettings,
                )
            }
            if (state.showNotificationPermission && !state.notificationsAllowed && !state.notificationsDenied) {
                ActionCard(
                    title = stringResource(R.string.notification_permission_title),
                    description = stringResource(R.string.notification_permission_description),
                    actionLabel = stringResource(R.string.action_allow),
                    onAction = onRequestNotifications,
                )
            }
            if (state.batteryOptimized) {
                ActionCard(
                    title = stringResource(R.string.battery_title),
                    description = stringResource(R.string.battery_description),
                    actionLabel = stringResource(R.string.battery_action),
                    onAction = onOpenBatterySettings,
                )
            }

            SettingsGroup(title = null) {
                SwitchItem(
                    title = stringResource(R.string.service_title),
                    summary = stringResource(
                        when {
                            !state.overlayAllowed -> R.string.service_needs_overlay
                            state.serviceRunning && state.servicePaused -> R.string.service_paused
                            state.serviceRunning -> R.string.service_on
                            else -> R.string.service_off
                        }
                    ),
                    checked = state.serviceRunning,
                    onCheckedChange = onServiceToggled,
                    enabled = state.overlayAllowed,
                )
            }

            SettingsGroup(title = stringResource(R.string.section_pie)) {
                NavigationItem(
                    title = stringResource(R.string.apps_title),
                    summary = appsSummary(config),
                    onClick = onOpenApps,
                )
                NavigationItem(
                    title = stringResource(R.string.appearance_title),
                    summary = stringResource(
                        R.string.appearance_summary,
                        (config.pie.sizeFraction * 100).roundToInt(),
                        config.pie.holdMs,
                    ),
                    onClick = onOpenAppearance,
                )
            }

            SettingsGroup(title = stringResource(R.string.section_bars)) {
                SwitchItem(
                    title = stringResource(R.string.show_bars_title),
                    summary = stringResource(R.string.show_bars_summary),
                    checked = config.showBars,
                    onCheckedChange = onShowBarsChanged,
                )
                for (bar in config.bars) {
                    NavigationItem(
                        title = barTitle(bar),
                        summary = stringResource(if (bar.enabled) R.string.bar_on else R.string.bar_off),
                        onClick = { onOpenBar(bar.id) },
                    )
                }
            }

            SettingsGroup(title = stringResource(R.string.section_backup)) {
                NavigationItem(
                    title = stringResource(R.string.export_title),
                    summary = stringResource(R.string.export_summary),
                    onClick = onExport,
                    showArrow = false,
                )
                NavigationItem(
                    title = stringResource(R.string.import_title),
                    summary = stringResource(R.string.import_summary),
                    onClick = onImport,
                    showArrow = false,
                )
            }

            SettingsGroup(title = stringResource(R.string.app_name)) {
                NavigationItem(
                    title = stringResource(R.string.setup_title),
                    summary = stringResource(R.string.setup_summary),
                    onClick = onOpenSetup,
                )
                NavigationItem(
                    title = stringResource(R.string.about_title),
                    summary = stringResource(R.string.about_summary),
                    onClick = onOpenAbout,
                )
            }
        }
    }
}

/** "No apps yet", "5 apps" or, with several layers, "2 layers, 12 apps". */
@Composable
private fun appsSummary(config: FanletConfig): String {
    val apps = config.slotCount
    if (apps == 0) return stringResource(R.string.apps_none)
    val appText = pluralStringResource(R.plurals.app_count, apps, apps)
    val layers = config.layers.size
    if (layers <= 1) return appText
    return stringResource(R.string.apps_summary_layers, pluralStringResource(R.plurals.layer_count, layers, layers), appText)
}

/** Something the user still has to do, with the button that does it. */
@Composable
private fun ActionCard(title: String, description: String, actionLabel: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    FanletTheme {
        HomeScreen(
            state = HomeState(
                overlayAllowed = true,
                showNotificationPermission = true,
                notificationsAllowed = true,
                notificationsDenied = false,
                serviceRunning = true,
                servicePaused = false,
                batteryOptimized = true,
                config = FanletConfig.DEFAULT.withSlotAdded(0, "org.example.mail"),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onOpenOverlaySettings = {},
            onRequestNotifications = {},
            onOpenBatterySettings = {},
            onServiceToggled = {},
            onShowBarsChanged = {},
            onOpenApps = {},
            onOpenAppearance = {},
            onOpenBar = {},
            onExport = {},
            onImport = {},
            onOpenSetup = {},
            onOpenAbout = {},
        )
    }
}
