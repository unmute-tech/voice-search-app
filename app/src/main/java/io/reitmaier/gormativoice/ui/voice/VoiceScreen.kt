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

import android.net.Uri
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VoiceOverOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import io.reitmaier.gormativoice.ui.components.ArcRotationAnimation
import io.reitmaier.gormativoice.ui.components.GormatiVoiceTopAppBar
import io.reitmaier.gormativoice.ui.components.MyBottomAppBar
import io.reitmaier.gormativoice.ui.components.VideoPlayer
import io.reitmaier.gormativoice.ui.components.VoiceVisualizer
import io.reitmaier.gormativoice.ui.theme.GormatiVoiceTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import java.io.File
import java.util.*
import kotlin.time.ExperimentalTime

@ExperimentalFoundationApi
@ExperimentalMaterial3Api
@ExperimentalCoroutinesApi
@FlowPreview
@ExperimentalLayoutApi
@ExperimentalTime
@ExperimentalPermissionsApi
@Composable
@UnstableApi
internal fun VoiceScreen(
  modifier: Modifier = Modifier,
  viewModel: VoiceViewModel,
) {
  val state = viewModel.container.stateFlow.collectAsState().value
  val processIntent = viewModel.processIntent
  val context = LocalContext.current
  DisposableEffect(key1 = viewModel) {
        onDispose { viewModel.onStop() }
    }

  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(viewModel, snackbarHostState) {
    launch {
      viewModel.container.sideEffectFlow.collect {
        when (it) {
          GormatiVoiceSideEffect.RecordingStarted -> {} // snackbarHostState.showSnackbar("Recording started")
          GormatiVoiceSideEffect.RecordingStopped -> {
            Toast.makeText(context, "Uploading Recording", Toast.LENGTH_SHORT).show()} // snackbarHostState.showSnackbar("Recording stopped.")
        }
      }
    }
  }
  VoiceScreen(state, snackbarHostState, processIntent)
}

@ExperimentalFoundationApi
@ExperimentalMaterial3Api
@FlowPreview
@ExperimentalCoroutinesApi
@ExperimentalTime
@ExperimentalLayoutApi
@Composable
internal fun VoiceScreen(
  state: ViewState,
  snackbarHostState: SnackbarHostState,
  processIntent: (ViewIntent) -> Unit
) {
  Scaffold(
    snackbarHost = {
      SnackbarHost(
        modifier = Modifier
          .navigationBarsPadding()
          .imePadding(),
        hostState = snackbarHostState,
      )
    },
    floatingActionButton = {
      GormatiVoiceFloatingActionButton(
        state = state,
        processIntent = processIntent
      )
    },
    topBar = {
      when(state) {
        is ViewState.Idle,
        is ViewState.Processing,
        is ViewState.Streaming -> Unit
        is ViewState.ImageResults -> GormatiVoiceTopAppBar(
          title = "",
          recording = state.recording,
          playing = state.playing,
          onRecordClick = {
            if(state.recording) {
              processIntent(ViewIntent.StopVoiceOver)
            } else {
              processIntent(ViewIntent.RecordVoiceOver)
            }
          } ,
          onPlayClick = {
            if(state.playing) {
              processIntent(ViewIntent.StopQuery)
            } else {
              processIntent(ViewIntent.PlayQuery)
            }

          }
        )
      }
    },

    bottomBar = {
      when(state) {
        ViewState.Idle -> {}
        is ViewState.Processing -> {}
        is ViewState.Streaming -> {}
        is ViewState.ImageResults -> {
          if(state.selectedIndex != null)  {
            MyBottomAppBar(
              containerColor = MaterialTheme.colorScheme.background,
            )
            {
              FloatingActionButton(
                onClick = { processIntent(ViewIntent.RateImagePositive)},
                modifier = Modifier.padding(10.dp, 0.dp),
                containerColor = Color.Green,
                elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation()
              ) {
                Icon(Icons.Filled.Check, "")
              }

              Text("${(state.selectedIndex + 1).toString().padStart(2,'0')} / ${state.speechResults.size}")

              FloatingActionButton(
                onClick = { processIntent(ViewIntent.RateImageNegative)},
                modifier = Modifier.padding(10.dp, 0.dp),
                containerColor = Color.Red,
                elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation()
              ) {
                Icon(Icons.Filled.Close, "")
              }
            }
          }
        }
      }
    },
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
  ) { scaffoldPadding ->
    val background = when(state) {
      is ViewState.Idle,
      is ViewState.Processing,
      is ViewState.ImageResults, -> MaterialTheme.colorScheme.background
      is ViewState.Streaming -> MaterialTheme.colorScheme.primaryContainer
    }
    Box(
      Modifier
        .fillMaxSize()
        .background(background)
        .padding(scaffoldPadding)
        .consumeWindowInsets(scaffoldPadding)
        .systemBarsPadding(),
    ) {
        Column(
    horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .fillMaxWidth()
          .align(Alignment.Center)
          .padding(bottom = 25.dp),
      ) {
          when(state) {
            is ViewState.Idle -> {
              Column {
                Text( "Say something for the UnMute system to find ...")
              }
            }
            is ViewState.Streaming -> {
              Column {
                VoiceVisualizer(
                  modifier = Modifier
                    .width(200.dp)
                    .height(200.dp),
                  streamingState = state
                )
              }
            }
            is ViewState.Processing -> {
              Column() {
                ArcRotationAnimation()
              }
            }

            is ViewState.ImageResults -> {
              PhotoGrid(state, processIntent)
              if(state.showNewQueryDialog) {

                AlertDialog(
                  onDismissRequest = {
                    // Dismiss the dialog when the user clicks outside the dialog or on the back
                    // button. If you want to disable that functionality, simply use an empty
                    // onCloseRequest.
                    processIntent(ViewIntent.NewQueryCancel)
                  },
                  title = {
                    Text(text = "Start New Query?")
                  },
                  text = {
                    Text("Are you sure you want to start a new query?")
                  },
                  confirmButton = {
                    Button(

                      onClick = {
                        processIntent(ViewIntent.NewQueryConfirm)
                      }) {
                      Text("Yes")
                    }
                  },
                  dismissButton = {
                    Button(
                      onClick = {
                        processIntent(ViewIntent.NewQueryCancel)
                      }) {
                      Text("No")
                    }
                  }
                )
              }
            }
          }
      }
    }
  }
}
@Composable
fun BackPressHandler(
  backPressedDispatcher: OnBackPressedDispatcher? =
        LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher,
  onBackPressed: () -> Unit
) {
    val currentOnBackPressed by rememberUpdatedState(newValue = onBackPressed)

    val backCallback = remember {
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                currentOnBackPressed()
            }
        }
    }

    DisposableEffect(key1 = backPressedDispatcher) {
        backPressedDispatcher?.addCallback(backCallback)

        onDispose {
            backCallback.remove()
        }
    }
}
@ExperimentalFoundationApi
@ExperimentalCoroutinesApi
@FlowPreview
@ExperimentalTime
@Composable
private fun PhotoGrid(
  imageResults: ViewState.ImageResults,
  processIntent: (ViewIntent) -> Unit,
) {
  val uriHandler = LocalUriHandler.current
  if(imageResults.selectedIndex != null) {
    val onBack = {
      processIntent(ViewIntent.DeSelectImageIndex)
    }
    BackPressHandler(onBackPressed = onBack)
    val zoomState = rememberZoomState()
    val borderWidth = when(imageResults.speechResults[imageResults.selectedIndex].rating) {
      Rating.POSITIVE -> 5.dp
      Rating.NEGATIVE -> 5.dp
      Rating.UNRATED -> 0.dp
    }
    val borderColor = when(imageResults.speechResults[imageResults.selectedIndex].rating) {
      Rating.POSITIVE -> Color.Green
      Rating.NEGATIVE -> Color.Red
      Rating.UNRATED -> Color.Black
    }
    val photo = imageResults.speechResults[imageResults.selectedIndex].photo
    val mediaUrl = if(VoiceViewModel.images.containsKey(photo)) {
      VoiceViewModel.images[photo]!!
    } else {
      if(photo.contains(",")) {
        photo.substringAfter(",")
      } else {
        photo
      }
    }
    if(mediaUrl.endsWith("mp4")) {
      VideoPlayer(uri = Uri.parse(mediaUrl))
    } else {
      AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
          .data(mediaUrl)
          .networkCachePolicy(CachePolicy.ENABLED)
          .diskCachePolicy(CachePolicy.ENABLED)
          .memoryCachePolicy(CachePolicy.ENABLED)
          .build(),
        contentDescription = "Banjara Image",
        contentScale = ContentScale.Fit,
        onSuccess = { state ->
          zoomState.setContentSize(state.painter.intrinsicSize)
        },
        modifier = Modifier
          .fillMaxSize()
          .zoomable(zoomState)
          .border(
            BorderStroke(borderWidth, borderColor)
          )
          .padding(borderWidth)
      )
    }
  } else {
    if(imageResults.speechResults.isEmpty()) {
      Text(text = "No images found for:\nPlease try again.")

    } else {
      LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 128.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
      ) {
        items(imageResults.speechResults.size) {
          val borderWidth = when(imageResults.speechResults[it].rating) {
            Rating.POSITIVE -> 4.dp
            Rating.NEGATIVE -> 4.dp
            Rating.UNRATED -> 0.dp
          }
          val borderColor = when(imageResults.speechResults[it].rating) {
            Rating.POSITIVE -> Color.Green
            Rating.NEGATIVE -> Color.Red
            Rating.UNRATED -> Color.Black
          }

          val photo = imageResults.speechResults[it].photo
          val imageUrl = if(VoiceViewModel.images.containsKey(photo)) {
            VoiceViewModel.images[photo]
          } else {
            photo.substringBefore(",")
          }
            AsyncImage(
              model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .networkCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .build(),
              contentDescription = "Banjara Image",
              modifier = Modifier
                .border(
                  BorderStroke(borderWidth, borderColor),
                )
                .padding(borderWidth)
                .combinedClickable(
                  onClick = {
                    processIntent(ViewIntent.SelectImageIndex(it))
                  },
                  onLongClick = {
                    processIntent(ViewIntent.RateImageToggle(it))
                  },
                )
            )
          }
      }
    }
  }
}

@Composable
private fun GormatiVoiceFloatingActionButton(
  state: ViewState,
  processIntent: (ViewIntent) -> Unit,
) {
  when(state) {
    is ViewState.Idle, ->
      FloatingActionButton(
        onClick = { processIntent(ViewIntent.Start) },
        containerColor = MaterialTheme.colorScheme.primary
      ) {
        Icon(Icons.Filled.RecordVoiceOver, "Start Speaking")
      }
    is ViewState.ImageResults ->
      if(state.selectedIndex == null) {
        FloatingActionButton(
          onClick = { processIntent(ViewIntent.NewQuery) },
          containerColor = MaterialTheme.colorScheme.primary
        ) {
          Icon(Icons.Filled.RecordVoiceOver, "Start Speaking")
        }
      }
    is ViewState.Streaming ->
     FloatingActionButton(
       onClick = { processIntent(ViewIntent.Stop) },
       containerColor = MaterialTheme.colorScheme.background
    ) {
      Icon(Icons.Filled.VoiceOverOff, "Stop Speaking")
    }
    is ViewState.Processing -> {
      FloatingActionButton(
        onClick = { processIntent(ViewIntent.Cancel) },
        containerColor = MaterialTheme.colorScheme.primary
      ) {
        Icon(Icons.Filled.Cancel, "Cancel")
      }
    }

  }
}
// Previews

@ExperimentalFoundationApi
@ExperimentalMaterial3Api
@ExperimentalCoroutinesApi
@FlowPreview
@ExperimentalTime
@ExperimentalLayoutApi
@UnstableApi
@Preview(showBackground = true)
@Composable
private fun VoiceScreenPreview() {
  GormatiVoiceTheme {
    VoiceScreen(
      ViewState.ImageResults(
        UUID.randomUUID(),
          listOf(
            SpeechResult("millet", 0.75),
            SpeechResult("corn", 0.66),
          ),
        File(""),
        recording = false,
        playing = true,
        selectedIndex = 0,
      ),
      SnackbarHostState()
    )
      {

    }
  }
}