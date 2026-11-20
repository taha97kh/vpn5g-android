package com.v2ray.ang.handler.vpn5g

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil

/**
 * Short connect/error sounds (v1.1.2), built on [ToneGenerator] so no
 * audio asset is needed. Each call creates a generator, plays one tone
 * and releases it after the tone ends. A failure to play (e.g. no audio
 * service) is logged, never thrown, and never blocks the connect flow.
 */
object Vpn5gSounds {
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Pleasant double-beep when the tunnel is verified (connected). */
    fun playConnected() = play(ToneGenerator.TONE_PROP_BEEP2, 600L)

    /** Distinct low error tone when a connect fails. */
    fun playError() = play(ToneGenerator.TONE_CDMA_ABORT, 400L)

    private fun play(tone: Int, durationMs: Long) {
        try {
            val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            generator.startTone(tone, durationMs.toInt())
            mainHandler.postDelayed(
                { runCatching { generator.release() } },
                durationMs + 600L,
            )
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gSounds: tone failed", e)
        }
    }
}
