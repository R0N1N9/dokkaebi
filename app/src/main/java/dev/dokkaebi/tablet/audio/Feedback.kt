package dev.dokkaebi.tablet.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.PI
import kotlin.math.sin

/** Synthesized ring tone, UI blips and vibration. No audio assets required. */
class Feedback(context: Context) {
    private val sampleRate = 22050
    private val ringTrack: AudioTrack by lazy { buildRingTrack() }
    private var ringing = false
    private val tones = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 60) }.getOrNull()

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /** A digital "trill" ring: two 0.4 s bursts warbling between two tones, then silence. 2 s loop. */
    private fun buildRingTrack(): AudioTrack {
        val total = sampleRate * 2
        val pcm = ShortArray(total)
        for (i in 0 until total) {
            val t = i.toDouble() / sampleRate
            val inBurst = (t < 0.4) || (t in 0.5..0.9)
            if (!inBurst) continue
            val f = if (((t * 25).toInt() % 2) == 0) 1320.0 else 1650.0
            val env = minOf(1.0, (t % 0.5) * 60, (0.4 - (t % 0.5)).coerceAtLeast(0.0) * 60)
            pcm[i] = (sin(2 * PI * f * t) * env * 0.35 * Short.MAX_VALUE).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(total * 2)
            .build()
        track.write(pcm, 0, total)
        track.setLoopPoints(0, total, -1)
        return track
    }

    fun setRinging(on: Boolean, sound: Boolean, haptics: Boolean) {
        if (on == ringing) return
        ringing = on
        runCatching {
            if (on && sound) {
                ringTrack.reloadStaticData()
                ringTrack.setLoopPoints(0, sampleRate * 2, -1)
                ringTrack.play()
            } else if (!on && ringTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                ringTrack.pause()
                ringTrack.stop()
            }
        }
        if (on && haptics) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 380, 120, 380, 1120), 0))
        } else {
            vibrator?.cancel()
        }
    }

    fun blip(sound: Boolean, haptics: Boolean) {
        if (sound) tones?.startTone(ToneGenerator.TONE_PROP_BEEP, 40)
        if (haptics) vibrator?.vibrate(VibrationEffect.createOneShot(18, 120))
    }

    fun alarm(sound: Boolean, haptics: Boolean) {
        if (sound) tones?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 350)
        if (haptics) vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 40, 60, 40, 160), -1))
    }

    fun release() {
        vibrator?.cancel()
        runCatching { ringTrack.release() }
        tones?.release()
    }
}
