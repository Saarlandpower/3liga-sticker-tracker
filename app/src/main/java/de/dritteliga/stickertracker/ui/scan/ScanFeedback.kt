package de.dritteliga.stickertracker.ui.scan

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import de.dritteliga.stickertracker.data.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Kurzes Vibrieren + Ton nach jedem gezählten Sticker. */
@Singleton
class ScanFeedback @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
) {
    private var tone: ToneGenerator? = null

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun play(isNew: Boolean) {
        val s = settings.settings.value
        if (s.vibration) vibrate(isNew)
        if (s.sound) beep(isNew)
    }

    private fun vibrate(isNew: Boolean) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = if (isNew) {
            VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE)
        } else {
            VibrationEffect.createWaveform(longArrayOf(0, 35, 70, 35), -1)
        }
        runCatching { v.vibrate(effect) }
    }

    private fun beep(isNew: Boolean) {
        runCatching {
            val t = tone ?: ToneGenerator(AudioManager.STREAM_MUSIC, 70).also { tone = it }
            t.startTone(if (isNew) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP2, 150)
        }
    }

    fun release() {
        tone?.release()
        tone = null
    }
}
