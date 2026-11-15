package edu.git.gams.audio

object UltrasonicConfig {
    const val SAMPLE_RATE = 48000

    // Near-ultrasonic frequency band (18.0 kHz to 19.5 kHz)
    const val FREQ_PREAMBLE = 18000.0 // Sync preamble tone
    const val FREQ_BIT_0 = 18500.0    // Binary '0'
    const val FREQ_BIT_1 = 19000.0    // Binary '1'
    const val FREQ_STOP = 19500.0     // Frame termination

    const val BIT_DURATION_MS = 25       // Milliseconds per bit symbol
    const val PREAMBLE_DURATION_MS = 50  // Sync pulse duration
    const val PAUSE_BETWEEN_BURSTS_MS = 1000L // 1 second between transmissions
}
