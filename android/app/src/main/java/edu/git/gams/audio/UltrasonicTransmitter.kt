package edu.git.gams.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.sin

class UltrasonicTransmitter {

    private var audioTrack: AudioTrack? = null
    private var isTransmitting = false
    private var transmitJob: Job? = null
    private var currentToken: String = ""

    fun updateToken(token: String) {
        this.currentToken = token.trim().uppercase()
    }

    fun start(token: String, scope: CoroutineScope) {
        if (isTransmitting) return
        this.currentToken = token.trim().uppercase()
        isTransmitting = true

        val bufferSize = AudioTrack.getMinBufferSize(
            UltrasonicConfig.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(UltrasonicConfig.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.setVolume(1.0f)
        audioTrack?.play()

        transmitJob = scope.launch(Dispatchers.Default) {
            while (isActive && isTransmitting) {
                val tokenToBroadcast = currentToken
                if (tokenToBroadcast.isNotEmpty()) {
                    val pcmData = synthesizeFskBurst(tokenToBroadcast)
                    audioTrack?.write(pcmData, 0, pcmData.size)
                }
                delay(UltrasonicConfig.PAUSE_BETWEEN_BURSTS_MS)
            }
        }
    }

    fun stop() {
        isTransmitting = false
        transmitJob?.cancel()
        transmitJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }

    private fun synthesizeFskBurst(token: String): ShortArray {
        val bits = mutableListOf<Int>()

        // Convert token chars to 8-bit ASCII bits
        var checksum = 0
        for (char in token) {
            val byteVal = char.code
            checksum = (checksum + byteVal) and 0xFF
            for (i in 7 downTo 0) {
                bits.add((byteVal shr i) and 1)
            }
        }

        // Append 8-bit checksum
        for (i in 7 downTo 0) {
            bits.add((checksum shr i) and 1)
        }

        val sampleRate = UltrasonicConfig.SAMPLE_RATE
        val preambleSamples = (sampleRate * (UltrasonicConfig.PREAMBLE_DURATION_MS / 1000.0)).toInt()
        val bitSamples = (sampleRate * (UltrasonicConfig.BIT_DURATION_MS / 1000.0)).toInt()
        val stopSamples = (sampleRate * (UltrasonicConfig.BIT_DURATION_MS / 1000.0)).toInt()

        val totalSamples = preambleSamples + (bits.size * bitSamples) + stopSamples
        val buffer = ShortArray(totalSamples)
        var writeIndex = 0

        // 1. Sync Preamble Tone (18.0 kHz)
        writeIndex = appendSineTone(buffer, writeIndex, UltrasonicConfig.FREQ_PREAMBLE, preambleSamples)

        // 2. Data Bits (18.5 kHz for '0', 19.0 kHz for '1')
        for (bit in bits) {
            val freq = if (bit == 0) UltrasonicConfig.FREQ_BIT_0 else UltrasonicConfig.FREQ_BIT_1
            writeIndex = appendSineTone(buffer, writeIndex, freq, bitSamples)
        }

        // 3. Stop Tone (19.5 kHz)
        appendSineTone(buffer, writeIndex, UltrasonicConfig.FREQ_STOP, stopSamples)

        return buffer
    }

    private fun appendSineTone(buffer: ShortArray, offset: Int, freq: Double, count: Int): Int {
        val sampleRate = UltrasonicConfig.SAMPLE_RATE.toDouble()
        val maxAmplitude = 26000.0 // Headroom below 32767 to avoid clipping distortion

        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            var sample = sin(2.0 * PI * freq * t)

            // Smooth cosine ramp at start and end to prevent audible high-frequency clicks
            val rampLength = (count * 0.1).coerceAtMost(48.0)
            if (i < rampLength) {
                sample *= (i / rampLength)
            } else if (i > count - rampLength) {
                sample *= ((count - i) / rampLength)
            }

            buffer[offset + i] = (sample * maxAmplitude).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return offset + count
    }
}
