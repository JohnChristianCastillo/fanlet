package dev.johncastles.fanlet.overlay

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.johncastles.fanlet.MainActivity
import dev.johncastles.fanlet.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * Foreground service that owns the overlay windows (the edge bars).
 *
 * Start it with [android.content.Context.startForegroundService]; stop it with
 * [android.content.Context.stopService] or the "Stop" action in its notification.
 * It only runs while the user has switched it on.
 */
class OverlayService : Service() {

    /** Context used for overlay windows; see [createOverlayContext]. */
    private lateinit var overlayContext: Context
    private lateinit var windowManager: WindowManager
    private lateinit var vibrator: Vibrator

    private var barView: EdgeBarView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        overlayContext = createOverlayContext()
        windowManager = overlayContext.getSystemService(WindowManager::class.java)
        vibrator = defaultVibrator()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Must happen within a few seconds of startForegroundService(), even if we
        // then stop again because the overlay permission is missing.
        startInForeground()

        if (!Settings.canDrawOverlays(this) || !showBar()) {
            stopSelf()
            return START_NOT_STICKY
        }

        runningState.value = true
        // Not sticky: restarting after a crash or reboot is handled in a later version.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        removeBar()
        runningState.value = false
        super.onDestroy()
    }

    // ---- Overlay window ---------------------------------------------------------

    /**
     * On Android 12+ overlay windows should be added through a window context for the
     * overlay window type, so the system can give it the right display configuration.
     * Older versions use the service itself.
     */
    private fun createOverlayContext(): Context {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
        val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        return createWindowContext(display, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    }

    /** Adds the test bar. Returns false if the system refused the window. */
    private fun showBar(): Boolean {
        if (barView != null) return true

        val view = EdgeBarView(overlayContext).apply {
            setBackgroundColor(TEST_BAR_COLOR)
            onTouchDown = ::vibrateTick
        }
        return try {
            windowManager.addView(view, testBarLayoutParams())
            barView = view
            true
        } catch (e: WindowManager.BadTokenException) {
            Log.w(TAG, "Overlay window refused", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Overlay window refused", e)
            false
        }
    }

    private fun removeBar() {
        barView?.let { windowManager.removeView(it) }
        barView = null
    }

    /**
     * A window that covers only the bar's own rectangle on the right edge.
     *
     * FLAG_NOT_FOCUSABLE keeps the window from taking key input or the keyboard focus,
     * and implies FLAG_NOT_TOUCH_MODAL: touches outside the rectangle go to whatever is
     * underneath. Overlay windows sit below the keyboard window, so the keyboard stays
     * usable where it overlaps the bar.
     */
    private fun testBarLayoutParams(): WindowManager.LayoutParams {
        val density = resources.displayMetrics.density
        val thicknessPx = (TEST_BAR_THICKNESS_DP * density).roundToInt()
        val lengthPx = (screenHeightPx() * TEST_BAR_LENGTH_FRACTION).roundToInt()

        return WindowManager.LayoutParams(
            thicknessPx,
            lengthPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            title = "Fanlet edge bar"
        }
    }

    private fun screenHeightPx(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.height()
        } else {
            overlayContext.resources.displayMetrics.heightPixels
        }

    // ---- Haptics ----------------------------------------------------------------

    private fun defaultVibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }

    private fun vibrateTick() {
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        } else {
            VibrationEffect.createOneShot(TICK_FALLBACK_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrator.vibrate(effect)
    }

    // ---- Foreground notification ------------------------------------------------

    private fun startInForeground() {
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(getString(R.string.notification_channel_name))
            .setDescription(getString(R.string.notification_channel_description))
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        // Behaves like the launcher icon: brings the existing settings screen to the front.
        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        val openApp = PendingIntent.getActivity(
            this, REQUEST_OPEN_APP, openAppIntent, PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.notification_action_stop), stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val TAG = "OverlayService"

        private const val ACTION_STOP = "dev.johncastles.fanlet.action.STOP_OVERLAY"
        private const val CHANNEL_ID = "overlay_status"
        private const val NOTIFICATION_ID = 1
        private const val REQUEST_OPEN_APP = 0
        private const val REQUEST_STOP = 1

        // Test bar for the overlay prototype; configurable bars replace these later.
        private const val TEST_BAR_THICKNESS_DP = 24
        private const val TEST_BAR_LENGTH_FRACTION = 0.30f
        private const val TEST_BAR_COLOR = 0x8000BCD4.toInt() // half-transparent cyan

        private const val TICK_FALLBACK_MS = 20L

        private val runningState = MutableStateFlow(false)

        /** True while the service is running and its bar is on screen. */
        val isRunning: StateFlow<Boolean> = runningState.asStateFlow()
    }
}
