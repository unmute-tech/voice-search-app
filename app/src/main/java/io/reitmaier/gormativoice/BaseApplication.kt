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

package io.reitmaier.gormativoice

import android.app.Application
import androidx.media3.common.util.UnstableApi
import io.reitmaier.gormativoice.di.appModule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import kotlin.time.ExperimentalTime


@FlowPreview
@ExperimentalCoroutinesApi
@ExperimentalTime
@UnstableApi
class BaseApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    AndroidLogcatLogger.installOnDebuggableApp(this, minPriority = LogPriority.VERBOSE)
    startKoin {
      // Log Koin into Android logger
      androidLogger()
      // Reference Android context
      androidContext(this@BaseApplication)

      modules(appModule)
    }
  }
}
