// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.data.device

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.core.content.ContextCompat
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Generates tones on the ALARM stream with [AudioTrack]. Loudness depends on the device's
 * alarm volume and speaker; HAVEN never changes the user's volume settings.
 */
class TonePlayer(private val context: Context) {

    private val audio: AudioManager? = ContextCompat.getSystemService(context, AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private var track: AudioTrack? = null
    private var focus: AudioFocusRequest? = null
    private var onInterrupted: (() -> Unit)? = null

    /** Alarm-stream volume as a percentage, so the UI can tell the user if it is low. */
    fun alarmVolumePercent(): Int? {
        val a = audio ?: return null
        val max = a.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        if (max <= 0) return null
        return a.getStreamVolume(AudioManager.STREAM_ALARM) * 100 / max
    }

    /** Continuous Morse tone (700 Hz) prepared for fast on/off keying. */
    fun prepareMorseTone(): Boolean = prepare(sineLoop(700.0))

    /** Rising and falling two-tone siren loop. */
    fun prepareSiren(): Boolean = prepare(sirenLoop())

    fun play(onInterrupted: () -> Unit = {}) {
        val t = track ?: return
        this.onInterrupted = onInterrupted
        requestFocus()
        runCatching { t.play() }
    }

    fun pause() {
        val t = track ?: return
        runCatching { t.pause() }
    }

    fun release() {
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
        focus?.let { audio?.abandonAudioFocusRequest(it) }
        focus = null
        onInterrupted = null
    }

    private fun requestFocus() {
        val a = audio ?: return
        if (focus != null) return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS) {
                    // e.g. a phone call: stop rather than fight the call.
                    onInterrupted?.invoke()
                }
            }
            .build()
        focus = req
        a.requestAudioFocus(req)
    }

    private fun prepare(samples: ShortArray): Boolean {
        release()
        return try {
            val t = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
            t.write(samples, 0, samples.size)
            t.setLoopPoints(0, samples.size, -1)
            track = t
            true
        } catch (e: Exception) {
            track = null
            false
        }
    }

    private fun sineLoop(frequency: Double): ShortArray {
        // 700 Hz divides 44 100 Hz exactly (63 samples), so the loop is click-free.
        val period = (SAMPLE_RATE / frequency).roundToInt()
        val n = period * 100
        return ShortArray(n) { i -> (sin(2 * PI * i / period) * AMPLITUDE).toInt().toShort() }
    }

    private fun sirenLoop(): ShortArray {
        val seconds = 1.6
        val n = (SAMPLE_RATE * seconds).toInt()
        val out = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val f = if (t < 0.5) 650 + 900 * (t / 0.5) else 1550 - 900 * ((t - 0.5) / 0.5)
            phase += 2 * PI * f / SAMPLE_RATE
            out[i] = (sin(phase) * AMPLITUDE).toInt().toShort()
        }
        return out
    }

    companion object {
        const val SAMPLE_RATE = 44_100
        const val AMPLITUDE = 30_000.0
    }
}
