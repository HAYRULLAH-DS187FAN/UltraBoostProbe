package com.ultraboost.probe.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

class ProbeTonePlayer {
    private var track: AudioTrack? = null
    private var worker: Thread? = null
    private var activeEffect: AudioEffect? = null
    @Volatile private var running = false

    var audioSessionId: Int = AudioEffect.ERROR_BAD_VALUE
        private set

    @Synchronized
    fun start(): Int {
        if (track != null) return audioSessionId

        val sampleRate = 44_100
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        check(minBuffer > 0) { "AudioTrack için geçerli tampon boyutu alınamadı ($minBuffer)." }

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val newTrack = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(maxOf(minBuffer, 4096))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        check(newTrack.state == AudioTrack.STATE_INITIALIZED) {
            newTrack.release()
            "Test ses çıkışı başlatılamadı."
        }

        newTrack.setVolume(0.55f)
        audioSessionId = newTrack.audioSessionId
        track = newTrack
        running = true
        newTrack.play()

        worker = Thread({
            val buffer = ShortArray(512)
            var sampleIndex = 0L
            while (running) {
                for (i in buffer.indices) {
                    val time = sampleIndex.toDouble() / sampleRate
                    // Very low-level two-tone signal for a cautious functional check.
                    val sample = (sin(2.0 * PI * 110.0 * time) * 0.035) +
                        (sin(2.0 * PI * 440.0 * time) * 0.025)
                    buffer[i] = (sample * Short.MAX_VALUE).roundToInt().toShort()
                    sampleIndex++
                }
                val written = try {
                    newTrack.write(buffer, 0, buffer.size)
                } catch (_: Exception) {
                    break
                }
                if (written < 0) break
            }
        }, "UltraBoost-ProbeTone").apply {
            isDaemon = true
            start()
        }

        return audioSessionId
    }

    @Synchronized
    fun apply(effect: ProbeEffect): String {
        val currentTrack = track ?: return "Önce test sesini başlat."
        releaseActiveEffect()

        return try {
            val session = currentTrack.audioSessionId
            val created: AudioEffect = when (effect) {
                ProbeEffect.LOUDNESS -> LoudnessEnhancer(session).apply {
                    setTargetGain(300) // +3 dB, deliberately conservative for this probe.
                }
                ProbeEffect.BASS -> BassBoost(0, session).apply {
                    setStrength(250.toShort())
                }
                ProbeEffect.EQUALIZER -> Equalizer(0, session)
                ProbeEffect.VIRTUALIZER -> Virtualizer(0, session).apply {
                    setStrength(200.toShort())
                }
            }

            if (!created.hasControl()) {
                created.release()
                return "Efekt oluşturuldu ancak kontrol bu uygulamaya verilmedi."
            }

            val status = created.setEnabled(true)
            if (status != AudioEffect.SUCCESS) {
                created.release()
                return "Efekt açma isteği başarısız oldu (kod: $status)."
            }

            activeEffect = created
            when (effect) {
                ProbeEffect.LOUDNESS -> "Loudness Enhancer test sesine bağlandı (+3 dB hedef kazanç)."
                ProbeEffect.BASS -> "Bass Boost test sesine bağlandı (%25 güç ayarı)."
                ProbeEffect.EQUALIZER -> "Equalizer test sesine bağlandı ve etkinleştirildi."
                ProbeEffect.VIRTUALIZER -> "Virtualizer test sesine bağlandı (%20 güç ayarı)."
            }
        } catch (error: Exception) {
            releaseActiveEffect()
            "Efekt test edilemedi: ${error.javaClass.simpleName}${error.message?.let { " — $it" } ?: ""}"
        }
    }

    @Synchronized
    fun stop() {
        running = false
        releaseActiveEffect()
        val oldTrack = track
        track = null
        try {
            oldTrack?.pause()
            oldTrack?.flush()
            oldTrack?.stop()
        } catch (_: Exception) {
            // The track may already be stopped by the audio system.
        }
        try {
            oldTrack?.release()
        } catch (_: Exception) {
            // Best-effort cleanup.
        }
        worker?.interrupt()
        worker = null
        audioSessionId = AudioEffect.ERROR_BAD_VALUE
    }

    private fun releaseActiveEffect() {
        try {
            activeEffect?.setEnabled(false)
            activeEffect?.release()
        } catch (_: Exception) {
            // Best-effort cleanup.
        } finally {
            activeEffect = null
        }
    }
}
