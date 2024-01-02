package io.reitmaier.gormativoice.audio

import android.media.AudioRecord
import android.os.Parcelable
import kotlinx.parcelize.Parcelize



  @Parcelize
  data class StreamingData(val data: ByteArray, val amplitude: Int) : Parcelable {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as StreamingData

      if (!data.contentEquals(other.data)) return false
      if (amplitude != other.amplitude) return false

      return true
    }

    override fun hashCode(): Int {
      var result = data.contentHashCode()
      result = 31 * result + amplitude
      return result
    }
  }
fun List<StreamingData>.combine() : StreamingData =
  reduce { acc, streamingState ->
    StreamingData(acc.data + streamingState.data, 0)
  }

fun AudioRecord.isRecording() : Boolean {
  return recordingState == AudioRecord.RECORDSTATE_RECORDING
}
