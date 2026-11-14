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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dev.johncastles.fanlet.overlay.OverlayService
import dev.johncastles.fanlet.ui.SetupScreen
import dev.johncastles.fanlet.ui.SetupState
import dev.johncastles.fanlet.ui.theme.FanletTheme

class MainActivity : ComponentActivity() {

    // Permission state is re-read in onResume, because the user changes it in the
    // system settings screens and then comes back.
    private var overlayAllowed by mutableStateOf(false)
    private var notificationsAllowed by mutableStateOf(false)
    private var notificationsDenied by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    notificationsDenied = !granted
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SetupScreen(
                        state = SetupState(
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
                        onOpenNotificationSettings = ::openNotificationSettings,
                        onServiceToggled = { on -> if (on) startOverlayService() else stopOverlayService() },
                        onShowBarsChanged = { show -> configRepository.update { it.copy(showBars = show) } },
                        onBarChanged = { bar -> configRepository.update { it.withBar(bar) } },
                        onPieAppearanceChanged = { pie -> configRepository.update { it.copy(pie = pie) } },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
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

    private fun openOverlaySettings() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
        )
    }

    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        )
    }

    private fun startOverlayService() {
        ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
    }

    private fun stopOverlayService() {
        stopService(Intent(this, OverlayService::class.java))
    }
}
