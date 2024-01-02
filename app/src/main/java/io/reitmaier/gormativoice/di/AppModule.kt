package io.reitmaier.gormativoice.di

import androidx.media3.common.util.UnstableApi
import io.reitmaier.gormativoice.asr.CloudAsr
import io.reitmaier.gormativoice.audio.AudioEmitter
import io.reitmaier.gormativoice.audio.ExoAudioPlayer
import io.reitmaier.gormativoice.service.GormatiService
import io.reitmaier.gormativoice.ui.voice.VoiceViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module
import kotlin.time.ExperimentalTime

@FlowPreview
@ExperimentalCoroutinesApi
@ExperimentalTime
@UnstableApi
internal val appModule = module {
  viewModelOf(::VoiceViewModel)
  single { AudioEmitter(get()) }
  single { ExoAudioPlayer(get()) }
  single { CloudAsr() }
  single { GormatiService() }
}
