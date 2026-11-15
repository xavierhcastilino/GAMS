package edu.git.gams.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import kotlin.math.cos
import kotlin.math.PI

class UltrasonicReceiver {

    private var audioRecord: AudioRecord? = null
    private var isListening = false
    private var listenJob: Job? = null

    @SuppressLint("MissingPermission")
    fun startListening(
        scope: CoroutineScope,
        onTokenDecoded: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (isListening) return

        val minBufSize = AudioRecord.getMinBufferSize(
            UltrasonicConfig.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        // Try UNPROCESSED (raw acoustics) first, fallback to DEFAULT
        audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.UNPROCESSED,
                UltrasonicConfig.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufSize * 2
            )
        } catch (_: Exception) {
            AudioRecord(
                MediaRecorder.AudioSource.DEFAULT,
                UltrasonicConfig.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufSize * 2
            )
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            onError("Microphone could not be initialized.")
            return
        }

        audioRecord?.startRecording()
        isListening = true

        val windowSize = (UltrasonicConfig.SAMPLE_RATE * (UltrasonicConfig.BIT_DURATION_MS / 1000.0)).toInt()
        val readBuffer = ShortArray(windowSize)

        listenJob = scope.launch(Dispatchers.Default) {
            val decodedBits = mutableListOf<Int>()
            var state = 0 // 0: SEARCHING, 1: IN_PREAMBLE, 2: DECODING

            while (isActive && isListening) {
                val read = audioRecord?.read(readBuffer, 0, windowSize) ?: -1
                if (read <= 0) continue

                val energyPreamble = goertzelEnergy(readBuffer, read, UltrasonicConfig.FREQ_PREAMBLE)
                val energy0 = goertzelEnergy(readBuffer, read, UltrasonicConfig.FREQ_BIT_0)
                val energy1 = goertzelEnergy(readBuffer, read, UltrasonicConfig.FREQ_BIT_1)
                val energyStop = goertzelEnergy(readBuffer, read, UltrasonicConfig.FREQ_STOP)

                val noiseThreshold = 10000.0

                when (state) {
                    0 -> { // SEARCHING FOR PREAMBLE
                        if (energyPreamble > noiseThreshold && energyPreamble > energy0 * 2.0 && energyPreamble > energy1 * 2.0) {
                            state = 1 // Preamble detected! Wait for it to finish.
                        }
                    }
                    1 -> { // IN_PREAMBLE: Wait for falling edge
                        val preambleEnded = (energyPreamble < noiseThreshold) || (energy0 > energyPreamble) || (energy1 > energyPreamble)
                        if (preambleEnded) {
                            state = 2 // Data bits begin now!
                            decodedBits.clear()
                            val bit = if (energy1 > energy0) 1 else 0
                            decodedBits.add(bit)
                        }
                    }
                    2 -> { // DECODING BITS
                        if (energyStop > noiseThreshold && energyStop > energy0 * 2.0 && energyStop > energy1 * 2.0) {
                            // Stop tone reached
                            val token = parseBitsToToken(decodedBits)
                            if (token != null) {
                                withContext(Dispatchers.Main) {
                                    onTokenDecoded(token)
                                }
                                break
                            } else {
                                state = 0 // Reset search for next burst
                            }
                        } else if (energy0 > noiseThreshold || energy1 > noiseThreshold) {
                            val bit = if (energy1 > energy0) 1 else 0
                            decodedBits.add(bit)

                            if (decodedBits.size >= 72) {
                                val token = parseBitsToToken(decodedBits)
                                if (token != null) {
                                    withContext(Dispatchers.Main) {
                                        onTokenDecoded(token)
                                    }
                                    break
                                } else if (decodedBits.size > 80) {
                                    state = 0 // Overrun, reset search
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun stopListening() {
        isListening = false
        listenJob?.cancel()
        listenJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    private fun parseBitsToToken(bits: List<Int>): String? {
        if (bits.size < 72) return null
        val bytes = mutableListOf<Int>()

        for (i in 0 until 72 step 8) {
            var b = 0
            for (j in 0 until 8) {
                b = (b shl 1) or bits[i + j]
            }
            bytes.add(b)
        }

        // Validate checksum
        var calculatedChecksum = 0
        val tokenChars = StringBuilder()
        for (i in 0 until 8) {
            val byteVal = bytes[i]
            calculatedChecksum = (calculatedChecksum + byteVal) and 0xFF
            tokenChars.append(byteVal.toChar())
        }

        val receivedChecksum = bytes[8]
        if (calculatedChecksum == receivedChecksum) {
            return tokenChars.toString()
        }
        return null
    }

    /**
     * Highly efficient Goertzel filter for single frequency bin energy.
     */
    private fun goertzelEnergy(buffer: ShortArray, numSamples: Int, targetFreq: Double): Double {
        val sampleRate = UltrasonicConfig.SAMPLE_RATE.toDouble()
        val k = (0.5 + ((numSamples.toDouble() * targetFreq) / sampleRate)).toInt()
        val omega = (2.0 * PI * k) / numSamples.toDouble()
        val coeff = 2.0 * cos(omega)

        var q0 = 0.0
        var q1 = 0.0
        var q2 = 0.0

        for (i in 0 until numSamples) {
            q0 = coeff * q1 - q2 + buffer[i]
            q2 = q1
            q1 = q0
        }

        return (q1 * q1 + q2 * q2 - q1 * q2 * coeff) / numSamples
    }
}
