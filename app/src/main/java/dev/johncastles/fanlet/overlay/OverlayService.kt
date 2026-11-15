package dev.johncastles.fanlet.overlay

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.johncastles.fanlet.MainActivity
import dev.johncastles.fanlet.R
import dev.johncastles.fanlet.appInfoCache
import dev.johncastles.fanlet.config.BarConfig
import dev.johncastles.fanlet.config.FanletConfig
import dev.johncastles.fanlet.configRepository
import dev.johncastles.fanlet.geometry.BarGeometry
import dev.johncastles.fanlet.geometry.PixelInsets
import dev.johncastles.fanlet.geometry.PixelRect
import dev.johncastles.fanlet.geometry.ScreenInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the overlay windows: the edge bars, and (through
 * [PieController]) the pie that a press on a bar opens.
 *
 * Start it with [android.content.Context.startForegroundService]; stop it with
 * [android.content.Context.stopService] or the "Stop" action in its notification.
 * It only runs while the user has switched it on. While running it follows the
 * config: every change, and every rotation, lays the bars out again.
 */
class OverlayService : Service() {

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Context used for overlay windows; see [createOverlayContext]. */
    private lateinit var overlayContext: Context
    private lateinit var windowManager: WindowManager
    private lateinit var haptics: Haptics
    private lateinit var pie: PieController

    /** Screen info from the last layout, reused when a pie opens. */
    private var screenInfo: ScreenInfo? = null

    /** The bar window whose touch gesture is driving the pie, if any. */
    private var activeBarView: EdgeBarView? = null

    /** Bar windows on screen, keyed by "<bar id>/<piece index>". */
    private val barViews = mutableMapOf<String, EdgeBarView>()

    private var config: FanletConfig = FanletConfig.DEFAULT
    private var started = false
    private var paused = false
    private var windowConfigCallbacks: ComponentCallbacks? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        overlayContext = createOverlayContext()
        windowManager = overlayContext.getSystemService(WindowManager::class.java)
        haptics = Haptics(this)
        pie = PieController(overlayContext, windowManager, haptics, appInfoCache, ::launchApp)
        registerWindowConfigCallbacks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                BarsWanted.set(this, false)
                stopSelf()
            }
            ACTION_PAUSE -> setPaused(true)
            ACTION_RESUME -> setPaused(false)
            // No action: started by the app, the startup receiver, the restart
            // notification, or Android restarting the sticky service (null intent).
            else -> start()
        }
        // Sticky: if Android kills the process to free memory, it recreates the service
        // later and calls onStartCommand with a null intent, which starts the bars again.
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        onScreenConfigurationChanged()
    }

    override fun onDestroy() {
        mainScope.cancel()
        windowConfigCallbacks?.let { overlayContext.unregisterComponentCallbacks(it) }
        pie.dismiss()
        removeAllBars()
        runningState.value = false
        pausedState.value = false
        super.onDestroy()
    }

    private fun start() {
        // Must happen within a few seconds of startForegroundService(), even if we
        // then stop again because the overlay permission is missing.
        if (!startInForeground()) {
            // Android refused (possible when restarted from the background on newer
            // versions): offer a one-tap restart instead of failing silently.
            OverlayNotifications.showRestart(this)
            stopSelf()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        if (started) return
        started = true
        runningState.value = true
        OverlayNotifications.cancelRestart(this)

        mainScope.launch {
            configRepository.config.collect { latest ->
                config = latest
                prefetchIcons()
                layoutBars()
            }
        }
    }

    private fun onScreenConfigurationChanged() {
        if (!started) return
        // A rotation in the middle of a gesture would leave the pie in the wrong place.
        pie.cancel()
        activeBarView = null
        layoutBars()
    }

    private fun setPaused(value: Boolean) {
        if (!started) {
            // Only reachable if the service was not running; nothing to pause.
            stopSelf()
            return
        }
        paused = value
        pausedState.value = value
        if (value) {
            pie.dismiss()
            activeBarView = null
        }
        layoutBars()
        updateNotification()
    }

    // ---- Overlay windows --------------------------------------------------------

    /**
     * On Android 11+ overlay windows should be added through a window context for the
     * overlay window type, so the system gives it the right display metrics and
     * configuration. Older versions use the service itself.
     */
    private fun createOverlayContext(): Context {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return this
        val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        return createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    }

    /**
     * On Android 12+ the window context reports its own configuration changes, after its
     * metrics are up to date. The service's own [onConfigurationChanged] covers older
     * versions; laying out twice is harmless.
     */
    private fun registerWindowConfigCallbacks() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                onScreenConfigurationChanged()
            }

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = Unit
        }
        overlayContext.registerComponentCallbacks(callbacks)
        windowConfigCallbacks = callbacks
    }

    /** Brings the bar windows in line with [config], the screen and [paused]. */
    private fun layoutBars() {
        val wanted = if (paused) emptyMap() else wantedBarWindows()

        val iterator = barViews.entries.iterator()
        while (iterator.hasNext()) {
            val (key, view) = iterator.next()
            if (key !in wanted) {
                if (view === activeBarView) {
                    pie.cancel()
                    activeBarView = null
                }
                windowManager.removeView(view)
                iterator.remove()
            }
        }

        for ((key, window) in wanted) {
            val params = layoutParamsFor(window.rect)
            val existing = barViews[key]
            if (existing != null) {
                bind(existing, window)
                windowManager.updateViewLayout(existing, params)
                continue
            }
            val view = EdgeBarView(overlayContext)
            bind(view, window)
            try {
                windowManager.addView(view, params)
                barViews[key] = view
            } catch (e: WindowManager.BadTokenException) {
                Log.w(TAG, "Overlay window refused", e)
                stopSelf()
                return
            } catch (e: SecurityException) {
                Log.w(TAG, "Overlay window refused", e)
                stopSelf()
                return
            }
        }
    }

    private class BarWindow(val rect: PixelRect, val bar: BarConfig)

    private fun wantedBarWindows(): Map<String, BarWindow> {
        val screen = readScreenInfo().also { screenInfo = it }
        val windows = mutableMapOf<String, BarWindow>()
        for (bar in config.bars.filter { it.enabled }) {
            BarGeometry.barRects(screen, bar).forEachIndexed { index, rect ->
                windows["${bar.id}/$index"] = BarWindow(rect, bar)
            }
        }
        return windows
    }

    private fun bind(view: EdgeBarView, window: BarWindow) {
        // Transparent windows still receive touches; that is what makes the bars invisible.
        view.setBackgroundColor(if (config.showBars) VISIBLE_BAR_COLOR else Color.TRANSPARENT)
        view.onTouch = { event -> onBarTouch(window.bar, view, event) }
    }

    // ---- Pie gesture --------------------------------------------------------------

    /**
     * Every touch event of a gesture that started on a bar arrives here, including moves
     * far outside the bar: Android keeps delivering a gesture to the window it started in.
     * Raw coordinates are screen coordinates, the same space as the pie.
     */
    private fun onBarTouch(bar: BarConfig, view: EdgeBarView, event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (pie.isActive) return // one gesture at a time
                val screen = screenInfo ?: return
                activeBarView = view
                if (bar.hapticsEnabled) haptics.barTouched()
                pie.start(
                    bar = bar,
                    layers = config.layers,
                    appearance = config.pie,
                    screen = screen,
                    x = event.rawX,
                    y = event.rawY,
                    timeMs = event.eventTime,
                )
            }
            MotionEvent.ACTION_MOVE ->
                if (view === activeBarView) pie.move(event.rawX, event.rawY, event.eventTime)
            MotionEvent.ACTION_UP ->
                if (view === activeBarView) {
                    activeBarView = null
                    pie.release(event.rawX, event.rawY, event.eventTime)
                }
            MotionEvent.ACTION_CANCEL ->
                if (view === activeBarView) {
                    activeBarView = null
                    pie.cancel()
                }
        }
    }

    /** Loads icons for every app the pie can show, so it never waits for one. */
    private fun prefetchIcons() {
        val packages = config.layers
            .flatMap { it.slices }
            .flatMap { listOfNotNull(it.primaryPackage, it.secondaryPackage) }
        appInfoCache.prefetch(packages)
    }

    private fun launchApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Log.w(TAG, "No launch intent for $packageName")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Could not launch $packageName", e)
        }
    }

    /**
     * A window that covers exactly [rect], in absolute screen coordinates.
     *
     * FLAG_NOT_FOCUSABLE keeps the window from taking key input or the keyboard focus,
     * and implies FLAG_NOT_TOUCH_MODAL: touches outside the rectangle go to whatever is
     * underneath. Overlay windows sit below the keyboard window, so the keyboard stays
     * usable where it overlaps a bar.
     */
    @SuppressLint("RtlHardcoded") // Positions are absolute screen pixels, not layout-direction relative.
    private fun layoutParamsFor(rect: PixelRect): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            rect.width,
            rect.height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = rect.left
            y = rect.top
            title = "Fanlet edge bar"
            // The geometry already keeps bars clear of system bars and cutouts, so let the
            // window go exactly where it says instead of being pushed away from them.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

    private fun removeAllBars() {
        barViews.values.forEach { windowManager.removeView(it) }
        barViews.clear()
    }

    /** Screen size, system bars and cutouts for the current rotation. */
    private fun readScreenInfo(): ScreenInfo {
        val density = overlayContext.resources.displayMetrics.density

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets
            val systemBars = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            return ScreenInfo(
                widthPx = metrics.bounds.width(),
                heightPx = metrics.bounds.height(),
                density = density,
                systemBarInsets = PixelInsets(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom),
                cutouts = insets.displayCutout?.boundingRects.orEmpty().map { it.toPixelRect() },
            )
        }

        // Android 8 to 10: the real display size (including system bars). System bar
        // sizes are not available to a service there, so bars may extend under them.
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        val size = Point()
        @Suppress("DEPRECATION")
        display.getRealSize(size)
        val cutouts = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            display.cutout?.boundingRects.orEmpty().map { it.toPixelRect() }
        } else {
            emptyList()
        }
        return ScreenInfo(widthPx = size.x, heightPx = size.y, density = density, cutouts = cutouts)
    }

    private fun Rect.toPixelRect() = PixelRect(left, top, right, bottom)

    // ---- Foreground notification ------------------------------------------------

    /** Enters the foreground with the status notification. False if Android does not allow it now. */
    private fun startInForeground(): Boolean {
        OverlayNotifications.ensureChannel(this)
        val notification = buildNotification()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (Android 12+) is a subclass.
            Log.w(TAG, "Not allowed to start in the foreground", e)
            false
        }
    }

    /** Re-posts the notification so its text and Pause/Resume action match [paused]. */
    private fun updateNotification() {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (allowed) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        // Behaves like the launcher icon: brings the existing settings screen to the front.
        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        val openApp = PendingIntent.getActivity(
            this, REQUEST_OPEN_APP, openAppIntent, PendingIntent.FLAG_IMMUTABLE,
        )
        val pauseOrResume = if (paused) {
            serviceAction(ACTION_RESUME, REQUEST_RESUME, R.string.notification_action_resume)
        } else {
            serviceAction(ACTION_PAUSE, REQUEST_PAUSE, R.string.notification_action_pause)
        }
        val stop = serviceAction(ACTION_STOP, REQUEST_STOP, R.string.notification_action_stop)

        return NotificationCompat.Builder(this, OverlayNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(if (paused) R.string.notification_text_paused else R.string.notification_text))
            .setContentIntent(openApp)
            .addAction(pauseOrResume)
            .addAction(stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun serviceAction(action: String, requestCode: Int, labelRes: Int): NotificationCompat.Action {
        val intent = PendingIntent.getService(
            this,
            requestCode,
            Intent(this, OverlayService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(0, getString(labelRes), intent).build()
    }

    companion object {
        private const val TAG = "OverlayService"

        private const val ACTION_STOP = "dev.johncastles.fanlet.action.STOP_OVERLAY"
        private const val ACTION_PAUSE = "dev.johncastles.fanlet.action.PAUSE_OVERLAY"
        private const val ACTION_RESUME = "dev.johncastles.fanlet.action.RESUME_OVERLAY"
        private const val NOTIFICATION_ID = 1
        private const val REQUEST_OPEN_APP = 0
        private const val REQUEST_STOP = 1
        private const val REQUEST_PAUSE = 2
        private const val REQUEST_RESUME = 3

        /** Bar colour when "Show bars" is on: half-transparent cyan. */
        private const val VISIBLE_BAR_COLOR = 0x8000BCD4.toInt()


        private val runningState = MutableStateFlow(false)
        private val pausedState = MutableStateFlow(false)

        /** True while the service is running. */
        val isRunning: StateFlow<Boolean> = runningState.asStateFlow()

        /**
         * Starts the bars. From the background (boot, update) Android may refuse; then the
         * user gets a notification to start them with one tap.
         */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, OverlayService::class.java))
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Not allowed to start the service", e)
                OverlayNotifications.showRestart(context)
            }
        }

        /** The user switched the bars on in the app: remember it and start them. */
        fun switchOn(context: Context) {
            BarsWanted.set(context, true)
            start(context)
        }

        /** The user switched the bars off in the app: remember it and stop them. */
        fun switchOff(context: Context) {
            BarsWanted.set(context, false)
            context.stopService(Intent(context, OverlayService::class.java))
        }

        /** True while the bars are paused from the notification. */
        val isPaused: StateFlow<Boolean> = pausedState.asStateFlow()
    }
}
