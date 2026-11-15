package dev.johncastles.fanlet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.johncastles.fanlet.R
import dev.johncastles.fanlet.config.FanletConfig
import dev.johncastles.fanlet.ui.theme.FanletTheme

/**
 * A short checklist that walks through everything Fanlet needs: the overlay permission,
 * notifications, battery, a first set of apps, and switching the bars on. Every step is
 * optional; finished steps show as done. Shown once on the first launch, and again from
 * "Setup guide" on the home screen.
 */
@Composable
fun SetupScreen(
    state: HomeState,
    onOpenOverlaySettings: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenApps: () -> Unit,
    onServiceToggled: (Boolean) -> Unit,
    onFinish: () -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = stringResource(R.string.setup_heading), style = MaterialTheme.typography.headlineSmall)
            Text(text = stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyMedium)

            StepCard(
                title = stringResource(R.string.overlay_permission_title),
                description = stringResource(R.string.overlay_permission_description),
                done = state.overlayAllowed,
                actionLabel = stringResource(R.string.action_open_settings),
                onAction = onOpenOverlaySettings,
            )
            if (state.showNotificationPermission) {
                StepCard(
                    title = stringResource(R.string.notification_permission_title),
                    description = stringResource(R.string.notification_permission_description),
                    done = state.notificationsAllowed,
                    // After a decline the system will not ask again; the step stays skipped.
                    actionLabel = if (state.notificationsDenied) null else stringResource(R.string.action_allow),
                    onAction = onRequestNotifications,
                )
            }
            StepCard(
                title = stringResource(R.string.battery_title),
                description = stringResource(R.string.battery_description),
                done = !state.batteryOptimized,
                actionLabel = stringResource(R.string.battery_action),
                onAction = onOpenBatterySettings,
            )
            StepCard(
                title = stringResource(R.string.apps_title),
                description = stringResource(R.string.setup_apps_description),
                done = state.config.slotCount > 0,
                actionLabel = stringResource(R.string.setup_apps_action),
                onAction = onOpenApps,
            )
            SwitchStepCard(
                title = stringResource(R.string.service_title),
                description = stringResource(
                    if (state.overlayAllowed) R.string.setup_switch_description else R.string.service_needs_overlay
                ),
                checked = state.serviceRunning,
                enabled = state.overlayAllowed,
                onCheckedChange = onServiceToggled,
            )

            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.setup_finish))
            }
        }
    }
}

/** One step: what it is, whether it is done, and the button that does it. */
@Composable
private fun StepCard(
    title: String,
    description: String,
    done: Boolean,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(if (done) R.string.step_done else R.string.step_todo),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
            if (!done && actionLabel != null) {
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun SwitchStepCard(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = description, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SetupScreenPreview() {
    FanletTheme {
        SetupScreen(
            state = HomeState(
                overlayAllowed = true,
                showNotificationPermission = true,
                notificationsAllowed = false,
                notificationsDenied = false,
                serviceRunning = false,
                servicePaused = false,
                batteryOptimized = true,
                config = FanletConfig.DEFAULT,
            ),
            onOpenOverlaySettings = {},
            onRequestNotifications = {},
            onOpenBatterySettings = {},
            onOpenApps = {},
            onServiceToggled = {},
            onFinish = {},
        )
    }
}
