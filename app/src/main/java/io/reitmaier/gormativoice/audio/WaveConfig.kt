package io.reitmaier.gormativoice.audio

import android.media.AudioFormat

/**
 * Configuration for recording file.
 * @property [sampleRate] the number of samples that audio carried per second.
 * @property [channels] number and position of sound source when the sound is recording.
 * @property [audioEncoding] size of data per sample.
 */
data class WaveConfig(
    val sampleRate: Int = 16_000,
    val channels: Int = AudioFormat.CHANNEL_IN_MONO,
    val audioEncoding: Int = AudioFormat.ENCODING_PCM_16BIT
)

internal fun bitPerSample(audioEncoding: Int) = when (audioEncoding) {
    AudioFormat.ENCODING_PCM_8BIT -> 8
    AudioFormat.ENCODING_PCM_16BIT -> 16
    AudioFormat.ENCODING_PCM_32BIT -> 32
    else -> 16
}