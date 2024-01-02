
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

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.kapt)
  alias(libs.plugins.kotlin.parcelize)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.protobuf)
  alias(libs.plugins.secrets.plugin)
//  id("com.google.protobuf") version "0.8.18"
}

val javaVersion: JavaVersion by rootProject.extra

android {
  namespace = "io.reitmaier.gormativoice"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "io.reitmaier.gormativoice"
    minSdk = libs.versions.android.minSdk.get().toInt()
    targetSdk = libs.versions.android.targetSdk.get().toInt()
    versionCode = 3
    versionName = "1.0"

    testInstrumentationRunner = "io.reitmaier.gormativoice.HiltTestRunner"
    vectorDrawables {
      useSupportLibrary = true
    }

  }

  buildTypes {
    getByName("release") {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
  }

  kotlinOptions {
    jvmTarget = javaVersion.majorVersion
  }

  buildFeatures {
    compose = true
    aidl = false
    buildConfig = true
    renderScript = false
    shaders = false
  }

  composeOptions {
    kotlinCompilerExtensionVersion = libs.versions.androidxComposeCompiler.get()
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
      excludes += "META-INF/DEPENDENCIES"
    }
  }
}

dependencies {
  // DI
  implementation(libs.koin.core)
  implementation(libs.koin.androidx.compose)

  // logcat
  implementation(libs.logcat)

  // permissions
  implementation(libs.google.accompanist.permissions)

  // MVI
  implementation(libs.orbitmvi.core)
  implementation(libs.orbitmvi.viewmodel)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Icons
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)

  // Arch Components
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)

  // Coil
  implementation(libs.coil)
  implementation(libs.coil.compose)

  // media3
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui)

  // Zoomable
  implementation(libs.net.engawapg.lib.zoomable)

  // protobuf
  implementation(libs.grpc.protobuf)
  implementation(libs.grpc.stub)
  implementation(libs.grpc.kotlin.stub)
  implementation(libs.google.protobuf.kotlin)
  implementation(libs.grpc.okhttp)

  // monads
  implementation(libs.kotlin.result)
  implementation(libs.kotlin.result.coroutines)
  implementation(libs.kotlin.retry)

  // json
  implementation(libs.kotlinx.serialization.json)

  // ktor
  implementation(libs.ktor.client.auth)
  implementation(libs.ktor.client.content.negotiation)
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.ktor.serialization.kotlinx.json)

  // conscript + desugaring
  implementation(libs.conscript)
  coreLibraryDesugaring(libs.android.tools.desugar.jdk.libs)

  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
}


secrets {
  defaultPropertiesFileName = "secrets.defaults.properties"
  propertiesFileName = "secrets.properties"
}

protobuf {
  protoc {
    artifact = libs.google.protobuf.protoc.get().toString()
  }
  plugins {
    create("grpc") {
      artifact = libs.grpc.protoc.gen.java.get().toString()
    }
    create("grpckt") {
      artifact = "${libs.grpc.protoc.gen.kotlin.get()}:jdk8@jar"
    }
  }
  generateProtoTasks {
    all().forEach { task ->
      task.plugins {
        create("grpc") {
          option("lite")
        }
        create("grpckt") {
          option("lite")
        }
      }
      task.builtins {
        register("java") {
          option("lite")
        }
        register("kotlin") {
          option("lite")
        }
      }
    }
  }
}
