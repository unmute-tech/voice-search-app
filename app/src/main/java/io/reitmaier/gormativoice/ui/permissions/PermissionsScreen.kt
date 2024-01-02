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

package io.reitmaier.gormativoice.ui.permissions

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import io.reitmaier.gormativoice.ui.theme.GormatiVoiceTheme
import io.reitmaier.gormativoice.ui.voice.IntentDispatcher
import io.reitmaier.gormativoice.ui.voice.ViewIntent
import io.reitmaier.gormativoice.ui.voice.ViewState

@ExperimentalPermissionsApi
@Composable
internal fun PermissionsScreen(
  modifier: Modifier = Modifier,
  navigateToVoiceScreen: () -> Unit,
) {
  // io.reitmaier.gormativoice.Microphone permission state
  val microphonePermissionState = rememberPermissionState(
    android.Manifest.permission.RECORD_AUDIO
  )

  if (microphonePermissionState.status.isGranted) {
    LaunchedEffect(Unit) {
      navigateToVoiceScreen()
    }
  } else {

    Column {
      val textToShow = if (microphonePermissionState.status.shouldShowRationale) {
        // If the user has denied the permission but the rationale can be shown,
        // then gently explain why the app requires this permission
        "The microphone is important for this app and is needed to capture your voice query. Please grant the permission."
      } else {
        // If it's the first time the user lands on this feature, or the user.
        // doesn't want to be asked again for this permission, explain that the
        // permission is required
        "io.reitmaier.gormativoice.Microphone permission required for this feature to be available. " +
          "Please grant the permission"
      }
      Text(textToShow)
      Button(onClick = { microphonePermissionState.launchPermissionRequest() }) {
        Text("Request permission")
      }
    }
  }
}
@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun VoiceScreen(
  modifier: Modifier = Modifier,
  viewState: ViewState,
  processIntent: IntentDispatcher<ViewIntent>,
) {

}

// Previews

@ExperimentalPermissionsApi
@Preview(showBackground = true)
@Composable
private fun DefaultPreview() {
  GormatiVoiceTheme {
    PermissionsScreen(Modifier) {}
  }
}

@ExperimentalPermissionsApi
@Preview(showBackground = true, widthDp = 480)
@Composable
private fun PortraitPreview() {
  GormatiVoiceTheme {
    PermissionsScreen(Modifier) {}
  }
}
