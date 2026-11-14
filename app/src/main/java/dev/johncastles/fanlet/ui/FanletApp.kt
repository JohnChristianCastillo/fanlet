package dev.johncastles.fanlet.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import dev.johncastles.fanlet.R
import dev.johncastles.fanlet.config.ConfigCodec
import dev.johncastles.fanlet.config.ConfigTransfer
import dev.johncastles.fanlet.config.FanletConfig
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The settings screens. Navigation is plain state: one current screen, back goes up one level. */
@Serializable
sealed interface Screen {
    @Serializable
    @SerialName("home")
    data object Home : Screen

    @Serializable
    @SerialName("apps")
    data object Apps : Screen

    @Serializable
    @SerialName("appearance")
    data object Appearance : Screen

    @Serializable
    @SerialName("bar")
    data class Bar(val barId: String) : Screen

    /** Choosing an app for the pie; [sliceIndex] null means a new slice. */
    @Serializable
    @SerialName("pick")
    data class PickApp(val sliceIndex: Int?, val secondary: Boolean) : Screen
}

/** Keeps the current screen across rotation and process death. */
private val screenSaver = Saver<Screen, String>(
    save = { Json.encodeToString(Screen.serializer(), it) },
    restore = { Json.decodeFromString(Screen.serializer(), it) },
)

/**
 * The whole settings UI. [home] carries the permission and service state from the
 * activity; every config change goes through [updateConfig].
 */
@Composable
fun FanletApp(
    home: HomeState,
    onOpenOverlaySettings: () -> Unit,
    onRequestNotifications: () -> Unit,
    onServiceToggled: (Boolean) -> Unit,
    updateConfig: ((FanletConfig) -> FanletConfig) -> Unit,
) {
    var screen by rememberSaveable(stateSaver = screenSaver) { mutableStateOf<Screen>(Screen.Home) }
    val config = home.config

    BackHandler(enabled = screen != Screen.Home) {
        screen = if (screen is Screen.PickApp) Screen.Apps else Screen.Home
    }

    when (val current = screen) {
        Screen.Home -> HomeWithBackup(
            home = home,
            onOpenOverlaySettings = onOpenOverlaySettings,
            onRequestNotifications = onRequestNotifications,
            onServiceToggled = onServiceToggled,
            onNavigate = { screen = it },
            updateConfig = updateConfig,
        )

        Screen.Apps -> AppsScreen(
            config = config,
            updateConfig = updateConfig,
            onPickApp = { target -> screen = Screen.PickApp(target.sliceIndex, target.secondary) },
            onBack = { screen = Screen.Home },
        )

        Screen.Appearance -> AppearanceScreen(
            appearance = config.pie,
            onAppearanceChanged = { pie -> updateConfig { it.copy(pie = pie) } },
            onBack = { screen = Screen.Home },
        )

        is Screen.Bar -> {
            val bar = config.bars.find { it.id == current.barId }
            if (bar == null) {
                // The bar disappeared, for example after an import.
                LaunchedEffect(current) { screen = Screen.Home }
                return
            }
            BarScreen(
                bar = bar,
                showBars = config.showBars,
                onShowBarsChanged = { show -> updateConfig { it.copy(showBars = show) } },
                onBarChanged = { changed -> updateConfig { it.withBar(changed) } },
                onBack = { screen = Screen.Home },
            )
        }

        is Screen.PickApp -> AppPickerScreen(
            title = stringResource(if (current.secondary) R.string.picker_title_secondary else R.string.picker_title),
            onPicked = { packageName ->
                updateConfig { cfg -> applyPick(cfg, current, packageName) }
                screen = Screen.Apps
            },
            onBack = { screen = Screen.Apps },
        )
    }
}

/** Puts the picked app where [target] says: a new slice, or a slice's main or second app. */
private fun applyPick(config: FanletConfig, target: Screen.PickApp, packageName: String): FanletConfig =
    when {
        target.sliceIndex == null -> config.withSliceAdded(packageName)
        target.secondary -> config.withSliceSecondary(target.sliceIndex, packageName)
        else -> config.withSlicePrimary(target.sliceIndex, packageName)
    }

/** The home screen plus export and import through the system file picker. */
@Composable
private fun HomeWithBackup(
    home: HomeState,
    onOpenOverlaySettings: () -> Unit,
    onRequestNotifications: () -> Unit,
    onServiceToggled: (Boolean) -> Unit,
    onNavigate: (Screen) -> Unit,
    updateConfig: ((FanletConfig) -> FanletConfig) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentConfig by rememberUpdatedState(home.config)

    /** A valid imported config waiting for the user to confirm the replacement. */
    var pendingImport by remember { mutableStateOf<FanletConfig?>(null) }

    fun showMessage(@StringRes message: Int) {
        scope.launch { snackbarHostState.showSnackbar(resources.getString(message)) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ConfigTransfer.MIME_TYPE)
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult // picker cancelled
        scope.launch {
            val saved = ConfigTransfer.export(context.contentResolver, uri, currentConfig)
            showMessage(if (saved) R.string.export_done else R.string.export_failed)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (val result = ConfigTransfer.import(context.contentResolver, uri)) {
                is ConfigTransfer.ImportResult.Success -> pendingImport = result.config
                else -> showMessage(importErrorMessage(result))
            }
        }
    }

    HomeScreen(
        state = home,
        snackbarHostState = snackbarHostState,
        onOpenOverlaySettings = onOpenOverlaySettings,
        onRequestNotifications = onRequestNotifications,
        onServiceToggled = onServiceToggled,
        onShowBarsChanged = { show -> updateConfig { it.copy(showBars = show) } },
        onOpenApps = { onNavigate(Screen.Apps) },
        onOpenAppearance = { onNavigate(Screen.Appearance) },
        onOpenBar = { barId -> onNavigate(Screen.Bar(barId)) },
        onExport = { exportLauncher.launch(ConfigTransfer.SUGGESTED_FILE_NAME) },
        // Some file managers label .json files as plain text or generic binary data.
        onImport = { importLauncher.launch(arrayOf(ConfigTransfer.MIME_TYPE, "text/*", "application/octet-stream")) },
    )

    pendingImport?.let { imported ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.import_confirm_title)) },
            text = { Text(stringResource(R.string.import_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    updateConfig { imported }
                    pendingImport = null
                    showMessage(R.string.import_done)
                }) { Text(stringResource(R.string.action_replace)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@StringRes
private fun importErrorMessage(result: ConfigTransfer.ImportResult): Int = when (result) {
    is ConfigTransfer.ImportResult.Success -> R.string.import_done
    ConfigTransfer.ImportResult.ReadFailed -> R.string.import_failed_read
    ConfigTransfer.ImportResult.TooLarge -> R.string.import_failed_too_large
    is ConfigTransfer.ImportResult.Invalid -> when (result.error) {
        ConfigCodec.ParseError.NOT_JSON, ConfigCodec.ParseError.NOT_A_FANLET_CONFIG -> R.string.import_failed_not_fanlet
        ConfigCodec.ParseError.NEWER_VERSION -> R.string.import_failed_newer
        ConfigCodec.ParseError.INVALID_CONTENT -> R.string.import_failed_invalid
    }
}
