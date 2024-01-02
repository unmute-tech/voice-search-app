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

package io.reitmaier.gormativoice.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import io.reitmaier.gormativoice.ui.permissions.PermissionsScreen
import io.reitmaier.gormativoice.ui.voice.VoiceScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import org.koin.androidx.compose.getViewModel
import kotlin.time.ExperimentalTime

@ExperimentalFoundationApi
@FlowPreview
@ExperimentalCoroutinesApi
@ExperimentalMaterial3Api
@ExperimentalLayoutApi
@ExperimentalTime
@ExperimentalPermissionsApi
@UnstableApi
@Composable
fun MainNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "permission") {
      composable("permission") { PermissionsScreen(modifier = Modifier.padding(16.dp)) {
        navController.navigate("main") {
          popUpTo(0)
        }

      } }
      composable("main") { VoiceScreen(
        modifier = Modifier.padding(16.dp),
        viewModel = getViewModel()
      ) }
        // TODO: Add more destinations
    }
}
