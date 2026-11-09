package org.dynamicisland.app

import android.content.Context
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings

class HapticEngine(private val context: Context) {
    enum class Event {
        EXPAND,
        CONTRACT,
        ARRIVAL,
        LONG_PRESS,
        SUCCESS,
        DISMISS,
    }

    private val vibrator = context.getSystemService(Vibrator::class.java)
    private var last = 0L

    fun play(event: Event, intensity: Int) {
        if (
            intensity == 0 ||
                !vibrator.hasVibrator() ||
                Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.HAPTIC_FEEDBACK_ENABLED,
                    1,
                ) == 0
        )
            return
        val now = SystemClock.uptimeMillis()
        if (now - last < 120) return
        last = now
        val effect =
            when (event) {
                Event.ARRIVAL,
                Event.SUCCESS -> VibrationEffect.EFFECT_CLICK
                Event.LONG_PRESS,
                Event.EXPAND -> VibrationEffect.EFFECT_HEAVY_CLICK
                else -> VibrationEffect.EFFECT_TICK
            }
        runCatching {
            if (
                intensity == 2 &&
                    android.os.Build.VERSION.SDK_INT >= 30 &&
                    vibrator.areEffectsSupported(effect)[0] == Vibrator.VIBRATION_EFFECT_SUPPORT_YES
            )
                vibrator.vibrate(VibrationEffect.createPredefined(effect))
            else
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        if (intensity == 1) 8L else 16L,
                        if (vibrator.hasAmplitudeControl()) if (intensity == 1) 35 else 80
                        else VibrationEffect.DEFAULT_AMPLITUDE,
                    )
                )
        }
    }
}
