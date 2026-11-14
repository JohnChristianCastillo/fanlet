package dev.johncastles.fanlet.ui

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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import dev.johncastles.fanlet.config.FanletConfig
import dev.johncastles.fanlet.config.PieAppearance
import dev.johncastles.fanlet.ui.theme.FanletTheme

/** Everything the setup screen shows. Built by the activity, which owns the real state. */
data class SetupState(
    val overlayAllowed: Boolean,
    /** False on Android 12 and older, where notifications need no permission. */
    val showNotificationPermission: Boolean,
    val notificationsAllowed: Boolean,
    /** The user denied the notification request; the system will not ask again. */
    val notificationsDenied: Boolean,
    val serviceRunning: Boolean,
    val servicePaused: Boolean,
    val config: FanletConfig,
)

@Composable
fun SetupScreen(
    state: SetupState,
    onOpenOverlaySettings: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onServiceToggled: (Boolean) -> Unit,
    onShowBarsChanged: (Boolean) -> Unit,
    onBarChanged: (BarConfig) -> Unit,
    onPieAppearanceChanged: (PieAppearance) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )

        PermissionCard(
            title = stringResource(R.string.overlay_permission_title),
            allowed = state.overlayAllowed,
            description = stringResource(R.string.overlay_permission_description),
            actionLabel = stringResource(R.string.action_open_settings),
            onAction = onOpenOverlaySettings,
        )

        if (state.showNotificationPermission) {
            val denied = state.notificationsDenied && !state.notificationsAllowed
            PermissionCard(
                title = stringResource(R.string.notification_permission_title),
                allowed = state.notificationsAllowed,
                description = stringResource(
                    if (denied) R.string.notification_permission_denied
                    else R.string.notification_permission_description
                ),
                actionLabel = stringResource(
                    if (denied) R.string.action_open_settings else R.string.action_allow
                ),
                onAction = if (denied) onOpenNotificationSettings else onRequestNotifications,
            )
        }

        ServiceCard(
            running = state.serviceRunning,
            paused = state.servicePaused,
            enabled = state.overlayAllowed,
            onToggled = onServiceToggled,
        )

        Text(
            text = stringResource(R.string.section_bars),
            style = MaterialTheme.typography.titleLarge,
        )
        ShowBarsCard(showBars = state.config.showBars, onShowBarsChanged = onShowBarsChanged)
        for (bar in state.config.bars) {
            BarSettingsCard(bar = bar, onBarChanged = onBarChanged)
        }

        Text(
            text = stringResource(R.string.section_pie),
            style = MaterialTheme.typography.titleLarge,
        )
        PieSettingsCard(appearance = state.config.pie, onAppearanceChanged = onPieAppearanceChanged)
    }
}

/** A permission with its status, why Fanlet needs it, and a button when it is missing. */
@Composable
private fun PermissionCard(
    title: String,
    allowed: Boolean,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(if (allowed) R.string.status_allowed else R.string.status_not_allowed),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (allowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
            if (!allowed) {
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun ServiceCard(running: Boolean, paused: Boolean, enabled: Boolean, onToggled: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.service_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(
                        when {
                            !enabled -> R.string.service_needs_overlay
                            running && paused -> R.string.service_paused
                            else -> R.string.service_description
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(checked = running, onCheckedChange = onToggled, enabled = enabled)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SetupScreenMissingPermissionsPreview() {
    FanletTheme {
        SetupScreen(
            state = SetupState(
                overlayAllowed = false,
                showNotificationPermission = true,
                notificationsAllowed = false,
                notificationsDenied = false,
                serviceRunning = false,
                servicePaused = false,
                config = FanletConfig.DEFAULT,
            ),
            onOpenOverlaySettings = {},
            onRequestNotifications = {},
            onOpenNotificationSettings = {},
            onServiceToggled = {},
            onShowBarsChanged = {},
            onBarChanged = {},
            onPieAppearanceChanged = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SetupScreenRunningPreview() {
    FanletTheme {
        SetupScreen(
            state = SetupState(
                overlayAllowed = true,
                showNotificationPermission = true,
                notificationsAllowed = true,
                notificationsDenied = false,
                serviceRunning = true,
                servicePaused = false,
                config = FanletConfig.DEFAULT.copy(showBars = true),
            ),
            onOpenOverlaySettings = {},
            onRequestNotifications = {},
            onOpenNotificationSettings = {},
            onServiceToggled = {},
            onShowBarsChanged = {},
            onBarChanged = {},
            onPieAppearanceChanged = {},
        )
    }
}
