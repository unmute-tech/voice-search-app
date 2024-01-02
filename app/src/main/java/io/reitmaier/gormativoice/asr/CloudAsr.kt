package io.reitmaier.gormativoice.asr

import audiostream.CloudSpeech
import audiostream.SpeechGrpcKt
import com.google.protobuf.ByteString
import io.grpc.ManagedChannelBuilder
import io.reitmaier.gormativoice.BuildConfig
import io.reitmaier.gormativoice.audio.WaveConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import logcat.logcat
import java.util.UUID


class CloudAsr() {
  private val speechClient = SpeechGrpcKt.SpeechCoroutineStub(
    ManagedChannelBuilder
      .forTarget(BuildConfig.CLOUD_ASR_URL)
      .usePlaintext()
      .build()
  )


  suspend fun stream(stream: Flow<ByteArray>, requestId: UUID) : Flow<CloudSpeech.StreamingRecognizeResponse>  {
    val conf =
      CloudSpeech.StreamingRecognizeRequest.newBuilder()
        .clearAudioContent()
        .clearStreamingConfig()
        .setStreamingConfig(
          CloudSpeech.RecognitionConfig.newBuilder()
            .clearModel()
            .clearSampleRateHertz()
            .setModel(BuildConfig.MODEL_NAME)
            .setRequestId(requestId.toString())
            .setSampleRateHertz(WaveConfig().sampleRate).build()
        ).build()
    return withContext(Dispatchers.IO) {
      speechClient.streamingRecognize(
        stream
          .map {
            CloudSpeech.StreamingRecognizeRequest.newBuilder()
              .clear()
              .setAudioContent(ByteString.copyFrom(it))
              .build()
          }.onStart {
            logcat{  "ASR Request Streaming Started" }
            emit(conf)
          }.onEach {
            logcat { "Streaming data: ${it.audioContent.size()}" }
          }
      ).onStart { logcat { "Initialising ASR Client" } }
        .onEach { logcat { "Received ASR Response: $it" } }
        .onCompletion { logcat {  "Completed ASR Client" } }
    }
  }


}