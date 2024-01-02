package io.reitmaier.gormativoice.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

    fun ByteArray.calculateRMS(): Int {
      val shorts = toShortArray()
      val rms = sqrt(shorts.sumOf { it * it } / shorts.size.toDouble())
      return rms.toInt()
    }

    fun ByteArray.toShortArray(): ShortArray {
        val shorts = ShortArray(this.size / 2)
        ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return shorts
    }
