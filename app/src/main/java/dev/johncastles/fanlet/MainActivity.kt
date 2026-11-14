package dev.johncastles.fanlet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import dev.johncastles.fanlet.overlay.OverlayService
import dev.johncastles.fanlet.ui.FanletApp
import dev.johncastles.fanlet.ui.HomeState
import dev.johncastles.fanlet.ui.theme.FanletTheme

class MainActivity : ComponentActivity() {

    // Permission state is re-read in onResume, because the user changes it in the
    // system settings screens and then comes back.
    private var overlayAllowed by mutableStateOf(false)
    private var notificationsAllowed by mutableStateOf(false)

    // Remembered across launches so the notification card does not come back after the
    // user declined. Lives in app-private preferences, not in the exported config.
    private var notificationsDenied by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationsDenied = uiPreferences().getBoolean(KEY_NOTIFICATIONS_DECLINED, false)
        enableEdgeToEdge()
        setContent {
            FanletTheme {
                val serviceRunning by OverlayService.isRunning.collectAsState()
                val servicePaused by OverlayService.isPaused.collectAsState()
                val config by configRepository.config.collectAsState()
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    notificationsAllowed = granted
                    if (!granted) {
                        notificationsDenied = true
                        uiPreferences().edit { putBoolean(KEY_NOTIFICATIONS_DECLINED, true) }
                    }
                }

                FanletApp(
                    home = HomeState(
                        overlayAllowed = overlayAllowed,
                        showNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                        notificationsAllowed = notificationsAllowed,
                        notificationsDenied = notificationsDenied,
                        serviceRunning = serviceRunning,
                        servicePaused = servicePaused,
                        config = config,
                    ),
                    onOpenOverlaySettings = ::openOverlaySettings,
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onServiceToggled = { on -> if (on) startOverlayService() else stopOverlayService() },
                    updateConfig = { transform -> configRepository.update(transform) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        overlayAllowed = Settings.canDrawOverlays(this)
        notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun uiPreferences() = getSharedPreferences(UI_PREFERENCES, MODE_PRIVATE)

    private fun openOverlaySettings() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
        )
    }

    private fun startOverlayService() {
        ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
    }

    private fun stopOverlayService() {
        stopService(Intent(this, OverlayService::class.java))
    }

    private companion object {
        const val UI_PREFERENCES = "ui"
        const val KEY_NOTIFICATIONS_DECLINED = "notifications_declined"
    }
}
