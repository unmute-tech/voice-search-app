package io.reitmaier.gormativoice.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

/** Produces audio from the device's mic. */
@ExperimentalCoroutinesApi
internal class AudioEmitter(private val context: Context) {
  companion object {
    private const val BUFFER_FACTOR = 2
  }
  private val wavConfig = WaveConfig()

  private var audioRecorder: AudioRecord? = null
  private var job: Job? = null
  private lateinit var wavFile: File

  private val format: AudioFormat =
    AudioFormat.Builder()
      .setEncoding(wavConfig.audioEncoding)
      .setSampleRate(wavConfig.sampleRate)
      .setChannelMask(wavConfig.channels)
      .build()

  private val bufferSize = AudioRecord.getMinBufferSize(
    wavConfig.sampleRate,
    wavConfig.channels,
    wavConfig.audioEncoding
  ) * BUFFER_FACTOR

  @Suppress("BlockingMethodInNonBlockingContext") // Already wrapped in Dispatchers.IO context
  @SuppressLint("MissingPermission") // Handing permission in navigation graph
  private suspend fun start(scope: CoroutineScope, requestId: UUID, file: File) : ReceiveChannel<StreamingData> {
    wavFile = file
    job = Job()
    return scope.produce(job!! + Dispatchers.IO) {

      logcat { "Writing to ${wavFile.absolutePath}" }

      // create and configure recorder
      // Note: ensure settings are match the speech recognition config
      audioRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize)
            .build()
        } else {
          AudioRecord(
            MediaRecorder.AudioSource.MIC,
            wavConfig.sampleRate,
            wavConfig.channels,
            wavConfig.audioEncoding,
            bufferSize
          )
        }
      // start!
      logcat { "Recording audio with buffer size of: $bufferSize bytes" }

      audioRecorder!!.startRecording()
      val outputStream = FileOutputStream(wavFile)

      // stream bytes as they become available in chunks equal to the buffer size
      launch {
        try {
          while (isActive) {
            val audioData = ByteArray(bufferSize)
            // read audio & and next chunk
            val read = audioRecorder!!.read(audioData, 0, audioData.size)
            if (read > 0) {
              val amplitude = audioData.calculateRMS()
              // Double check that co-routine is still active
              if(isActive) {
                send(StreamingData(audioData, amplitude))
                try {
                  outputStream.write(audioData, 0, read)
                } catch (_: IOException) {
                  logcat(priority = LogPriority.ERROR) { "Exception writing to audio file: ${wavFile.name}"  }
                }
              }
            }
          }
        } finally {
          withContext(NonCancellable) {
            logcat { "Cancelling Stream" }
            try {
              outputStream.flush()
              outputStream.close()
            } catch (_: IOException) {
              logcat(priority = LogPriority.ERROR) { "Exception closing audio output stream for: ${wavFile.name}"  }
            }
            try {
              logcat { "Writing Wav Header asynchronously" }
              WaveHeaderWriter(wavFile,wavConfig).writeHeader()
              logcat { "Finished Writing Wav Header" }
            } catch (_: IOException) {
              logcat(priority = LogPriority.ERROR) { "Exception writing header for: ${wavFile.name}"  }
            }
          }
        }
      }
    }
  }

  @Suppress("BlockingMethodInNonBlockingContext") // Already wrapped in Dispatchers.IO context
  @SuppressLint("MissingPermission") // Handing permission in navigation graph
  suspend fun start(scope: CoroutineScope, requestId: UUID, filename: String): ReceiveChannel<StreamingData> {
    return start(scope, requestId, context.filesDir.resolve(filename))
  }

  /** Stop Streaming  */
  suspend fun stop() : File {
    logcat { "Stop Stream Initiated" }
    // stop reading audio data
    job?.cancelAndJoin()
    job = null

    // stop recording
    audioRecorder?.stop()
    audioRecorder?.release()
    audioRecorder = null
    return wavFile
  }
}