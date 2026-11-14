package dev.johncastles.fanlet.overlay

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** The vibrations Fanlet uses. Each has a distinct feel so they can be told apart. */
class Haptics(context: Context) {

    private enum class Kind(val fallbackMs: Long) { CLICK(20), TICK(10), DOUBLE_CLICK(50) }

    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    /** A finger touched a bar. */
    fun barTouched() = play(Kind.CLICK)

    /** The highlighted slice changed. */
    fun sliceChanged() = play(Kind.TICK)

    /** A slice switched to its secondary app. */
    fun switchedToSecondary() = play(Kind.DOUBLE_CLICK)

    private fun play(kind: Kind) {
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val predefined = when (kind) {
                Kind.CLICK -> VibrationEffect.EFFECT_CLICK
                Kind.TICK -> VibrationEffect.EFFECT_TICK
                Kind.DOUBLE_CLICK -> VibrationEffect.EFFECT_DOUBLE_CLICK
            }
            VibrationEffect.createPredefined(predefined)
        } else {
            VibrationEffect.createOneShot(kind.fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrator.vibrate(effect)
    }
}
