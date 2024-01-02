/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.reitmaier.gormativoice.ui.voice

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import audiostream.CloudSpeech.StreamingRecognitionResult
import io.reitmaier.gormativoice.asr.CloudAsr
import io.reitmaier.gormativoice.audio.AudioEmitter
import io.reitmaier.gormativoice.audio.ExoAudioPlayer
import io.reitmaier.gormativoice.audio.PlaybackState
import io.reitmaier.gormativoice.audio.combine
import io.reitmaier.gormativoice.service.GormatiService
import io.reitmaier.gormativoice.util.chunked
import io.reitmaier.gormativoice.util.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import logcat.logcat
import org.orbitmvi.orbit.Container
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.syntax.simple.intent
import org.orbitmvi.orbit.syntax.simple.postSideEffect
import org.orbitmvi.orbit.syntax.simple.reduce
import org.orbitmvi.orbit.viewmodel.container
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime


@UnstableApi @FlowPreview
@ExperimentalCoroutinesApi
@ExperimentalTime
internal class VoiceViewModel(
  savedStateHandle: SavedStateHandle,
  private val audioEmitter: AudioEmitter,
  private val speech: CloudAsr,
  private val service: GormatiService,
  private val exoAudioPlayer: ExoAudioPlayer,
): ViewModel(), ContainerHost<ViewState, GormatiVoiceSideEffect> {
  override val container: Container<ViewState, GormatiVoiceSideEffect> =
    container(ViewState.Idle, savedStateHandle)
//    container(ViewState.ImageResults(UUID.randomUUID(), SpeechResult.previewList, File(""), recording = false, playing = false, selectedIndex = null), savedStateHandle)
  private val SHOW_GRID_FIRST: Boolean = false

  private val intentFlow = MutableSharedFlow<ViewIntent>(extraBufferCapacity = 64)
  internal val processIntent: IntentDispatcher<ViewIntent> = { intentFlow.tryEmit(it) }
  init {
    viewModelScope.launch {
      try {
        container.stateFlow.onEach {
          if(it !is ViewState.Streaming)
            logcat { it.toString() }
        }.last()
      }
      finally {
        Unit
      }
    }
    exoAudioPlayer.playbackState
      .shareIn(viewModelScope, SharingStarted.WhileSubscribed())
      .onEach { playbackState ->
        logcat { "Playbackstate: $playbackState" }
        when(playbackState) {
          PlaybackState.Loading -> {}
          PlaybackState.Paused -> intent {
            state.let { viewState ->
              when (viewState) {
                ViewState.Idle,
                is ViewState.Processing,
                is ViewState.Streaming -> Unit
                is ViewState.ImageResults -> {
                  reduce { viewState.copy(playing = false) }
                }
              }
            }
          }
          PlaybackState.Playing -> intent {
            state.let { viewState ->
              when (viewState) {
                ViewState.Idle,
                is ViewState.Processing,
                is ViewState.Streaming -> Unit
                is ViewState.ImageResults -> {
                  reduce { viewState.copy(playing = true) }
                }
              }
            }
          }
        }
      }
      .stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        0L,
      )

    intentFlow
      .shareIn(viewModelScope, SharingStarted.WhileSubscribed())
      .onEach { viewIntent ->
        logcat { "Processing: $viewIntent" }
        when (viewIntent) {
          is ViewIntent.Start -> intent {
            state.let { viewState ->
              when (viewState) {
                ViewState.Idle,
                is ViewState.Processing,
                is ViewState.Streaming -> Unit
                is ViewState.ImageResults -> audioEmitter.stop()
              }
              startStreaming()
            }
          }
          is ViewIntent.Stop -> {
            val wavFile = audioEmitter.stop()

            viewModelScope.launch(Dispatchers.Main) {
              exoAudioPlayer.prepare(wavFile)
            }
            intent {
              state.let { viewState ->
                when (viewState) {
                  is ViewState.Idle -> {}
                  is ViewState.Streaming -> {
                    viewModelScope.launch {
                      service.submitTask(viewState.requestId,wavFile)
                    }
                    reduce { ViewState.Processing(viewState.requestId, wavFile, viewState.speechResults) }
                  }
                  is ViewState.ImageResults -> {
                    viewModelScope.launch {
                      service.submitTask(viewState.requestId,wavFile)
                    }
                  }
                  is ViewState.Processing -> {
                    viewModelScope.launch {
                      service.submitTask(viewState.requestId,wavFile)
                    }
                  }
                }
              }
            }
            // Don't update ViewState
            // let audio processing pipeline complete
          }
          is ViewIntent.Cancel -> {
            intent {
              reduce {
                ViewState.Idle
              }
            }
          }

          is ViewIntent.DeSelectImageIndex -> {
            intent {
              state.let { viewState ->
                when (viewState) {
                  is ViewState.ImageResults -> reduce {
                    viewState.copy(selectedIndex = null)
                  }
                  is ViewState.Idle,
                  is ViewState.Processing,
                  is ViewState.Streaming, -> Unit
                }
              }
            }
          }
          is ViewIntent.SelectImageIndex -> {
            intent {
              state.let { viewState ->
                when (viewState) {
                  is ViewState.ImageResults -> reduce {
                    viewState.copy(selectedIndex = viewIntent.index)
                  }

                  ViewState.Idle,
                  is ViewState.Processing,
                  is ViewState.Streaming, -> Unit
                }
              }
            }
          }

          is ViewIntent.RateImagePositive -> {
            intent {
              state.let { viewState ->
                when (viewState) {
                  is ViewState.ImageResults ->  {
                    if(viewState.selectedIndex != null) {
                      val result = viewState.speechResults[viewState.selectedIndex].copy(rating = Rating.POSITIVE)
                      viewModelScope.launch {
                        service.rateResult(viewState.requestId, result)
                      }
                      val speechResults = viewState.speechResults.update(viewState.selectedIndex, result)
                      val nextIndex = calcNextIndex(viewState.selectedIndex, speechResults)
                      reduce { viewState.copy(selectedIndex = nextIndex, speechResults = speechResults) }
                    }
                  }
                  ViewState.Idle,
                  is ViewState.Processing,
                  is ViewState.Streaming, -> Unit
                }
              }
            }
          }
          is ViewIntent.RateImageNegative -> {
            intent {
              state.let { viewState ->
                when (viewState) {
                  is ViewState.ImageResults ->  {
                    if(viewState.selectedIndex != null) {
                      val result = viewState.speechResults[viewState.selectedIndex].copy(rating = Rating.NEGATIVE)
                      viewModelScope.launch {
                        service.rateResult(viewState.requestId, result)
                      }
                      val speechResults = viewState.speechResults.update(viewState.selectedIndex, result)
                      val nextIndex = calcNextIndex(viewState.selectedIndex, speechResults)
                      reduce { viewState.copy(selectedIndex = nextIndex, speechResults = speechResults) }
                    }
                  }

                  ViewState.Idle,
                  is ViewState.Processing,
                  is ViewState.Streaming, -> Unit
                }
              }
            }
          }

          is ViewIntent.RateImageToggle -> {
            intent {
              state.let { viewState ->
                when (viewState) {
                  is ViewState.ImageResults ->  {
                    val toggledRating = when(viewState.speechResults[viewIntent.index].rating) {
                      Rating.POSITIVE -> Rating.NEGATIVE
                      Rating.NEGATIVE -> Rating.UNRATED
                      Rating.UNRATED -> Rating.POSITIVE
                    }
                    val result = viewState.speechResults[viewIntent.index].copy(rating = toggledRating)
                    viewModelScope.launch {
                      service.rateResult(viewState.requestId, result)
                    }
                    val speechResults = viewState.speechResults.update(viewIntent.index, result)
                    reduce { viewState.copy(speechResults = speechResults) }
                  }
                  ViewState.Idle,
                  is ViewState.Processing,
                  is ViewState.Streaming, -> Unit
                }
              }
            }
          }

          ViewIntent.RecordVoiceOver -> intent {
            state.let { viewState ->
              when (viewState) {
                is ViewState.Idle,
                is ViewState.Processing,
                is ViewState.Streaming -> Unit
                is ViewState.ImageResults -> {
                  postSideEffect(GormatiVoiceSideEffect.RecordingStarted)
                  viewModelScope.launch {
                    val filename = "${viewState.requestId}-comment-${System.currentTimeMillis()}.wav"
                    audioEmitter.start(viewModelScope, viewState.requestId, "$filename")
                      .consumeAsFlow().lastOrNull()
                    logcat { "Finished Recording Audio Comment" }
                  }
                  reduce { viewState.copy(recording = true) }
                }
              }
            }
          }
          ViewIntent.StopVoiceOver -> intent {
            state.let { viewState ->
              when (viewState) {
                is ViewState.Idle,
                is ViewState.Processing,
                is ViewState.Streaming -> Unit
                is ViewState.ImageResults -> {
                  postSideEffect(GormatiVoiceSideEffect.RecordingStopped)
                  viewModelScope.launch {
                    val wavFile = audioEmitter.stop()
                    service.submitComment(viewState.requestId,wavFile)
                  }
                  reduce {
                    viewState.copy(recording = false)
                  }
                }
              }
            }
          }
          ViewIntent.PlayQuery -> intent {
            state.let { viewState ->
              when (viewState) {
                is ViewState.Idle,
                is ViewState.Processing,
                is ViewState.Streaming -> Unit
                is ViewState.ImageResults -> {
                  viewModelScope.launch(Dispatchers.Main) {
                    exoAudioPlayer.playPause()
                  }
                  // Let exoplayer reduce viewstate
                }
              }
            }
          }
          ViewIntent.StopQuery -> intent {
            state.let { viewState ->
              when (viewState) {
                is ViewState.Idle,
                is ViewState.Processing,
                is ViewState.Streaming -> Unit
                is ViewState.ImageResults ->
                  viewModelScope.launch(Dispatchers.Main) {
                    exoAudioPlayer.playPause()
                  }
                // Let exoplayer reduce viewstate
              }
            }
          }

          ViewIntent.NewQuery -> intent {
            state.let { viewState ->
              when (viewState) {
                is ViewState.Idle,
                is ViewState.Processing,
                is ViewState.Streaming -> Unit
                is ViewState.ImageResults ->
                  reduce { viewState.copy(showNewQueryDialog = true) }
                // Let exoplayer reduce viewstate
              }
            }
          }
          ViewIntent.NewQueryCancel -> intent {
            state.let { viewState ->
              when (viewState) {
                is ViewState.Idle,
                is ViewState.Processing,
                is ViewState.Streaming -> Unit
                is ViewState.ImageResults ->
                  reduce { viewState.copy(showNewQueryDialog = false) }
                // Let exoplayer reduce viewstate
              }
            }
          }
          ViewIntent.NewQueryConfirm -> intent {
            state.let { viewState ->
              when (viewState) {
                is ViewState.Idle,
                is ViewState.Processing,
                is ViewState.Streaming -> Unit
                is ViewState.ImageResults ->
                  reduce { ViewState.Idle }
                // Let exoplayer reduce viewstate
              }
            }
          }
        }
      }
      .stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        0L,
      )
  }

  private fun calcFirstIndex(ratings: List<SpeechResult>) : Int? {
    return if(ratings.isEmpty()) {
      null
    } else {
      0
    }
  }
  private fun calcNextIndex(selectedIndex: Int, ratings: List<SpeechResult>) : Int? {
    if(ratings.isEmpty()) {
      return null
    }
    val nextUnratedIndex = ratings.subList(selectedIndex,ratings.size).indexOfFirst { it.rating == Rating.UNRATED }
    if(nextUnratedIndex != -1) {
      return nextUnratedIndex + selectedIndex
    }
    val prevUnratedIndex = ratings.subList(0,selectedIndex).indexOfFirst { it.rating == Rating.UNRATED }
    if(prevUnratedIndex != -1) {
      return prevUnratedIndex
    }
    if(selectedIndex == ratings.size - 1) {
      return null
    }
    return null
  }

  suspend fun startStreaming() {
    val requestId = UUID.randomUUID()
    val stream = audioEmitter.start(viewModelScope, requestId, "$requestId.wav")
      .consumeAsFlow()
      .onEach { streamState ->
        intent {
          state.let { viewState ->
            when (viewState) {
              is ViewState.Idle,
              is ViewState.ImageResults, -> reduce { ViewState.Streaming.new(streamState.amplitude, requestId) }
              is ViewState.Processing -> Unit
              is ViewState.Streaming -> reduce { viewState.append(streamState.amplitude) }
            }
          }

        }
      }
      .onCompletion {
        logcat { "Audio Stream Completed" }
      }
      .catch {  e ->
        // rethrow all but CancellationException
        // CancellationException == Stop Recording/Streaming
        if (e !is CancellationException) throw e
      }
      .chunked(10,1.seconds)
      .map {
        it.combine().data
      }

    // Stop streaming after 10 seconds
    val maxLengthJob = viewModelScope.launch {
      delay(10.seconds)
      processIntent(ViewIntent.Stop)
    }

    viewModelScope.launch {
      intent {
        speech.stream(stream, requestId)
          .filter { it.resultsCount > 0 }
          .map { it.resultsList }
          .map { it.getOrNull(0) }
          .filterNotNull()
          .onEach {
            logcat { it.toString() }
            state.let { viewState ->
              when(viewState) {
                is ViewState.Idle,
                is ViewState.ImageResults,
                -> Unit
                is ViewState.Processing -> {
                  if(viewState.requestId == requestId) {
                    val speechResults = SpeechResult.from(it)
                    viewModelScope.launch {
                      service.submitResults(speechResults, viewState.requestId)
                    }
                    reduce {
                      viewState.copy(
                        speechResults = speechResults
                      )
                    }
                  }
                }
                is ViewState.Streaming ->
                  if(viewState.requestId == requestId) {
                    reduce {
                      viewState.copy(
                        speechResults = SpeechResult.from(it)
                      )
                    }
                  }
              }
            }
          }
          .timeout(20.seconds)
          .lastOrNull() // Wait for result stream to terminate
        logcat { "ASR Stream finished" }
        maxLengthJob.cancel()

        // Then update the state
        // Remember onEach clause already catches the final transcript
        state.let { viewState ->
          when(viewState) {
            is ViewState.Idle,
            is ViewState.ImageResults,
            is ViewState.Streaming,
              -> reduce {
              logcat { "Ignoring unexpected ViewState: $viewState" }
              viewState
              }
            is ViewState.Processing ->
              if(viewState.requestId == requestId) {
                reduce {
                  ViewState.ImageResults(
                    requestId = viewState.requestId,
                    speechResults = viewState.speechResults,
                    waveFile = viewState.waveFile,
                    recording = false,
                    playing = false,
                    selectedIndex = if(SHOW_GRID_FIRST) null else calcFirstIndex(viewState.speechResults)
                  )
                }
              }
              else {
                logcat { "Ignoring mismatched ${viewState.requestId} != $requestId" }
              }
          }
        }
      }
    }
//    microphone.streamingState
//      .shareIn(viewModelScope, SharingStarted.WhileSubscribed())
//      .onEach { streamingState ->
//        intent {
//          state.let {
//            when (it) {
//              is ViewState.Idle,
//              is ViewState.Processing -> reduce {
//                when (streamingState) {
//                  is StreamingState.Idle -> ViewState.Idle
//                  is StreamingState.Streaming -> ViewState.Streaming.new(streamingState.amplitude)
//                }
//              }
//              is ViewState.Streaming -> reduce {
//                when (streamingState) {
//                  is StreamingState.Idle -> ViewState.Processing
//                  is StreamingState.Streaming -> it.append(streamingState.amplitude)
//                }
//              }
//            }
//          }
//        }
//      }
//      .chunked(10,1.seconds)
//      .onEach {
//        logcat { "New streaming state" }
//      }
//      .stateIn(
//        viewModelScope,
//        SharingStarted.Eagerly,
//        0L,
//      )
  }

  fun onStop() {
    logcat { "onStop called" }
    viewModelScope.launch(NonCancellable) {
      audioEmitter.stop()
    }
    exoAudioPlayer.pause()
  }

  companion object {
    val images = mapOf(
      "barrel" to "https://banjara.reitmaier.xyz/barrel.jpg",
      "beans" to "https://banjara.reitmaier.xyz/beans.jpg",
      "branches" to "https://banjara.reitmaier.xyz/branches.jpg",
      "bush" to "https://banjara.reitmaier.xyz/bush.jpg",
      "chestnut" to "https://banjara.reitmaier.xyz/chestnut.jpg",
      "chilli" to "https://banjara.reitmaier.xyz/chilli.jpg",
      "corn" to "https://banjara.reitmaier.xyz/corn.jpg",
      "cotton" to "https://banjara.reitmaier.xyz/cotton.jpg",
      "cow" to "https://banjara.reitmaier.xyz/cow.jpg",
      "curry" to "https://banjara.reitmaier.xyz/curry.jpg",
      "eggplant" to "https://banjara.reitmaier.xyz/eggplant.jpg",
      "field" to "https://banjara.reitmaier.xyz/field.jpg",
      "field2" to "https://banjara.reitmaier.xyz/field2.jpg",
      "flour" to "https://banjara.reitmaier.xyz/flour.jpg",
      "guava" to "https://banjara.reitmaier.xyz/guava.jpg",
      "ilex" to "https://banjara.reitmaier.xyz/ilex.jpg",
      "lemon" to "https://banjara.reitmaier.xyz/lemon.jpg",
      "millet" to "https://banjara.reitmaier.xyz/millet.jpg",
      "naan" to "https://banjara.reitmaier.xyz/naan.jpg",
      "papaya" to "https://banjara.reitmaier.xyz/papaya.jpg",
      "parsley" to "https://banjara.reitmaier.xyz/parsley.jpg",
      "pea" to "https://banjara.reitmaier.xyz/pea.jpg",
      "peanut" to "https://banjara.reitmaier.xyz/peanut.jpg",
      "plum" to "https://banjara.reitmaier.xyz/plum.jpg",
      "radish" to "https://banjara.reitmaier.xyz/radish.jpg",
      "river" to "https://banjara.reitmaier.xyz/river.jpg",
      "rubbish" to "https://banjara.reitmaier.xyz/rubbish.jpg",
      "taro" to "https://banjara.reitmaier.xyz/taro.jpg",
      "vagon" to "https://banjara.reitmaier.xyz/vagon.jpg",
      "well" to "https://banjara.reitmaier.xyz/well.jpg",
      "wheat" to "https://banjara.reitmaier.xyz/wheat.jpg",
      "goat" to "https://banjara.reitmaier.xyz/goat.jpg",
      "sorghum" to "https://banjara.reitmaier.xyz/sorghum.jpg",
    )
  }

}



typealias IntentDispatcher<I> = (I) -> Unit

@Parcelize
@Serializable
data class SpeechResult(
  val photo: String,
  val confidence: Double,
  val rating: Rating = Rating.UNRATED,
) : Parcelable {
  companion object {
    fun from(streamingRecognitionResult: StreamingRecognitionResult) : List<SpeechResult> =
      streamingRecognitionResult.alternativesList.map {
        SpeechResult(it.transcript, it.confidence.toDouble())
      }
    val previewList = listOf(
      SpeechResult("https://banjaraapi.reitmaier.xyz/data/photos/17d6c5b7394724d5308647ae467fcb69.jpg,https://banjara.reitmaier.xyz/vidtest.mp4", 0.66, Rating.UNRATED),
      SpeechResult("corn", 0.66, Rating.NEGATIVE),
      SpeechResult("vagon", 0.66),
      SpeechResult("millet", 0.66),
      SpeechResult("millet", 0.66),
      SpeechResult("corn", 0.66),
      SpeechResult("vagon", 0.66),
      SpeechResult("corn", 0.66),
      SpeechResult("vagon", 0.66),
      SpeechResult("corn", 0.66, Rating.NEGATIVE),
      SpeechResult("vagon", 0.66),
      SpeechResult("millet", 0.66),
      SpeechResult("millet", 0.66),
      SpeechResult("corn", 0.66),
      SpeechResult("vagon", 0.66),
      SpeechResult("corn", 0.66),
      SpeechResult("corn", 0.66),
      SpeechResult("corn", 0.66),
      SpeechResult("vagon", 0.66),
      SpeechResult("vagon", 0.66),
      SpeechResult("corn", 0.66, Rating.NEGATIVE),
      SpeechResult("vagon", 0.66),
      SpeechResult("millet", 0.66),
      SpeechResult("millet", 0.66),
      SpeechResult("corn", 0.66),
      SpeechResult("vagon", 0.66),
      SpeechResult("corn", 0.66),
      SpeechResult("corn", 0.66),
      SpeechResult("corn", 0.66),
      SpeechResult("vagon", 0.66),
    )
  }
}

@Serializable
@Parcelize
enum class Rating(val value: Int) : Parcelable {
  POSITIVE(1),
  NEGATIVE(-1),
  UNRATED(0),
}
interface WithRequest
{
  val requestId: UUID
}
internal sealed class ViewState : Parcelable {

  @Parcelize
  object Idle : ViewState(), Parcelable

  @Parcelize
  data class Streaming(
    val recordedAmplitudes: List<Int>,
    override val requestId: UUID,
    val speechResults: List<SpeechResult> = emptyList()
  ) : ViewState(), Parcelable, WithRequest {
    fun append(amp: Int) : Streaming {
      val newAmplitudes = if(recordedAmplitudes.size >= NUM_AMPLITUDES) {
        recordedAmplitudes.subList(1, NUM_AMPLITUDES)
      } else {
        recordedAmplitudes
      } + amp
      return this.copy(recordedAmplitudes = newAmplitudes)
    }
    companion object {
      private const val NUM_AMPLITUDES = 400
      fun new(amplitude: Int, requestId: UUID) =
        Streaming( List(NUM_AMPLITUDES) { 0 } + amplitude, requestId = requestId)
    }
  }

  @Parcelize
  data class Processing(
    override val requestId: UUID,
    val waveFile: File,
    val speechResults: List<SpeechResult> = emptyList()
  ) : ViewState(), Parcelable, WithRequest


  @Parcelize
  data class ImageResults(
    override val requestId: UUID,
    val speechResults: List<SpeechResult>,
    val waveFile: File,
    val recording: Boolean,
    val playing: Boolean,
    val selectedIndex: Int? = null,
    val showNewQueryDialog: Boolean = false,
  ) : ViewState(), Parcelable, WithRequest
}

internal sealed class ViewIntent {
  data class SelectImageIndex(val index: Int) : ViewIntent()
  data object RateImagePositive : ViewIntent()
  data object RateImageNegative : ViewIntent()
  data class RateImageToggle(val index: Int) : ViewIntent()
  data object DeSelectImageIndex : ViewIntent()
  data object RecordVoiceOver : ViewIntent()
  data object StopVoiceOver : ViewIntent()
  data object PlayQuery : ViewIntent()
  data object StopQuery : ViewIntent()
  data object Start : ViewIntent()
  data object Stop : ViewIntent()
  data object Cancel : ViewIntent()
  data object NewQuery: ViewIntent()
  data object NewQueryConfirm: ViewIntent()
  data object NewQueryCancel: ViewIntent()
}
sealed class GormatiVoiceSideEffect {
  data object RecordingStarted : GormatiVoiceSideEffect()
  data object RecordingStopped : GormatiVoiceSideEffect()
}
