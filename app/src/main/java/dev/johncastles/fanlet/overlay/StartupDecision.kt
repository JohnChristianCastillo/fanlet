package dev.johncastles.fanlet.overlay

/**
 * Whether the edge bars should be started automatically (after a reboot, an app update,
 * or Android restarting the service). Pure Kotlin, covered by unit tests.
 */
object StartupDecision {

    /**
     * @param wanted the user switched the bars on and has not switched them off since.
     * @param overlayAllowed "Display over other apps" is still granted; without it the
     *   bars cannot be shown, so starting would only flash a notification.
     */
    fun shouldStart(wanted: Boolean, overlayAllowed: Boolean): Boolean = wanted && overlayAllowed
}
