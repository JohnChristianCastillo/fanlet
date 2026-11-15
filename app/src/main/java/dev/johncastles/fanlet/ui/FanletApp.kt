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
import dev.johncastles.fanlet.config.BarConfig
import dev.johncastles.fanlet.config.ConfigCodec
import dev.johncastles.fanlet.config.Edge
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

    /** The app editor, showing the tab of [layer]. */
    @Serializable
    @SerialName("apps")
    data class Apps(val layer: Int = 0) : Screen

    @Serializable
    @SerialName("appearance")
    data object Appearance : Screen

    @Serializable
    @SerialName("bar")
    data class Bar(val barId: String) : Screen

    /** The checklist shown on the first launch, and from "Setup guide" on the home screen. */
    @Serializable
    @SerialName("setup")
    data object Setup : Screen

    @Serializable
    @SerialName("about")
    data object About : Screen

    /** Choosing an app for a slot of [layer]; [slotIndex] null means a new slot. */
    @Serializable
    @SerialName("pick")
    data class PickApp(val layer: Int, val slotIndex: Int?, val secondary: Boolean) : Screen
}

/** Keeps the current screen across rotation and process death. */
private val screenSaver = Saver<Screen, String>(
    save = { Json.encodeToString(Screen.serializer(), it) },
    restore = { Json.decodeFromString(Screen.serializer(), it) },
)

/**
 * The whole settings UI. [home] carries the permission and service state from the
 * activity; every config change goes through [updateConfig].
 *
 * @param showSetupFirst open the setup guide instead of the home screen (first launch).
 * @param onSetupFinished called when the user leaves the setup guide, so it is not shown
 *   automatically again.
 */
@Composable
fun FanletApp(
    home: HomeState,
    showSetupFirst: Boolean,
    onSetupFinished: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onServiceToggled: (Boolean) -> Unit,
    updateConfig: ((FanletConfig) -> FanletConfig) -> Unit,
) {
    var screen by rememberSaveable(stateSaver = screenSaver) {
        mutableStateOf<Screen>(if (showSetupFirst) Screen.Setup else Screen.Home)
    }
    // Set while the user is in the app editor on the way from the setup guide, so back
    // returns to the guide instead of the home screen.
    var returnToSetup by rememberSaveable { mutableStateOf(false) }
    val config = home.config

    fun finishSetup() {
        onSetupFinished()
        returnToSetup = false
        screen = Screen.Home
    }

    fun leaveApps() {
        if (returnToSetup) {
            returnToSetup = false
            screen = Screen.Setup
        } else {
            screen = Screen.Home
        }
    }

    BackHandler(enabled = screen != Screen.Home) {
        when (val current = screen) {
            is Screen.PickApp -> screen = Screen.Apps(current.layer)
            is Screen.Apps -> leaveApps()
            Screen.Setup -> finishSetup()
            else -> screen = Screen.Home
        }
    }

    when (val current = screen) {
        Screen.Home -> HomeWithBackup(
            home = home,
            onOpenOverlaySettings = onOpenOverlaySettings,
            onRequestNotifications = onRequestNotifications,
            onOpenBatterySettings = onOpenBatterySettings,
            onServiceToggled = onServiceToggled,
            onNavigate = { screen = it },
            updateConfig = updateConfig,
        )

        Screen.Setup -> SetupScreen(
            state = home,
            onOpenOverlaySettings = onOpenOverlaySettings,
            onRequestNotifications = onRequestNotifications,
            onOpenBatterySettings = onOpenBatterySettings,
            onOpenApps = {
                returnToSetup = true
                screen = Screen.Apps()
            },
            onServiceToggled = onServiceToggled,
            onFinish = ::finishSetup,
        )

        Screen.About -> AboutScreen(onBack = { screen = Screen.Home })

        is Screen.Apps -> AppsScreen(
            config = config,
            selectedLayer = current.layer,
            onSelectLayer = { layer -> screen = Screen.Apps(layer) },
            updateConfig = updateConfig,
            onPickApp = { target -> screen = Screen.PickApp(target.layer, target.slotIndex, target.secondary) },
            onBack = ::leaveApps,
        )

        Screen.Appearance -> AppearanceScreen(
            appearance = config.pie,
            layers = config.layers,
            barThicknessDp = config.bars.firstOrNull { it.edge == Edge.RIGHT }?.thicknessDp
                ?: BarConfig.DEFAULT_THICKNESS_DP,
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
            // Apps already in the pie, as main or second app in any layer, are not offered again.
            excludedPackages = remember(config.layers) {
                config.layers.flatMap { it.slices }.flatMap { listOfNotNull(it.primaryPackage, it.secondaryPackage) }.toSet()
            },
            onPicked = { packageName ->
                updateConfig { cfg -> applyPick(cfg, current, packageName) }
                screen = Screen.Apps(current.layer)
            },
            onBack = { screen = Screen.Apps(current.layer) },
        )
    }
}

/** Puts the picked app where [target] says: a new slice, or a slice's main or second app. */
private fun applyPick(config: FanletConfig, target: Screen.PickApp, packageName: String): FanletConfig =
    when {
        target.slotIndex == null -> config.withSlotAdded(target.layer, packageName)
        target.secondary -> config.withSlotSecondary(target.layer, target.slotIndex, packageName)
        else -> config.withSlotPrimary(target.layer, target.slotIndex, packageName)
    }

/** The home screen plus export and import through the system file picker. */
@Composable
private fun HomeWithBackup(
    home: HomeState,
    onOpenOverlaySettings: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenBatterySettings: () -> Unit,
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
        onOpenBatterySettings = onOpenBatterySettings,
        onServiceToggled = onServiceToggled,
        onShowBarsChanged = { show -> updateConfig { it.copy(showBars = show) } },
        onOpenApps = { onNavigate(Screen.Apps()) },
        onOpenAppearance = { onNavigate(Screen.Appearance) },
        onOpenBar = { barId -> onNavigate(Screen.Bar(barId)) },
        onOpenSetup = { onNavigate(Screen.Setup) },
        onOpenAbout = { onNavigate(Screen.About) },
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
