package com.receiptscanner.data.camera

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Plays a quiet, procedurally generated shutter-click sound.
 *
 * Uses [AudioTrack] to synthesize a brief ~50ms sine-wave click at a controlled amplitude,
 * replacing [android.media.MediaActionSound] which has no volume control and plays at full
 * system media volume.
 */
class ShutterSoundPlayer {

    private val sampleRate = 44100
    private val durationMs = 50
    private val frequencyHz = 1200.0
    private val volume = 0.12f // 0.0–1.0; ~12% of max

    private var audioTrack: AudioTrack? = null
    private var pcmData: ByteArray? = null

    init {
        generateClick()
    }

    private fun generateClick() {
        val numSamples = sampleRate * durationMs / 1000
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            // Sine wave with quick fade-in/fade-out envelope to avoid click artifacts
            val envelope = when {
                i < numSamples / 10 -> i.toFloat() / (numSamples / 10) // fade in
                i > numSamples * 9 / 10 -> (numSamples - i).toFloat() / (numSamples / 10) // fade out
                else -> 1.0f
            }
            val sample = (sin(2.0 * PI * frequencyHz * t) * Short.MAX_VALUE * volume * envelope).toInt()
            samples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        pcmData = ByteArray(samples.size * 2).also { bytes ->
            for (i in samples.indices) {
                bytes[i * 2] = (samples[i].toInt() and 0xFF).toByte()
                bytes[i * 2 + 1] = (samples[i].toInt() shr 8 and 0xFF).toByte()
            }
        }
    }

    fun play() {
        val data = pcmData ?: return

        // Release any previous track
        audioTrack?.release()

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(data.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(data, 0, data.size)
        track.play()
        audioTrack = track
    }

    fun release() {
        audioTrack?.release()
        audioTrack = null
    }
}
