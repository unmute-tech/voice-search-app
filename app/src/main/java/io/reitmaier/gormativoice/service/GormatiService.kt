package io.reitmaier.gormativoice.service

import android.os.Parcelable
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.runCatching
import com.github.michaelbull.retry.retry
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.reitmaier.gormativoice.BuildConfig
import io.reitmaier.gormativoice.ui.voice.SpeechResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.json.Json
import logcat.logcat
import org.conscrypt.Conscrypt
import java.io.File
import java.security.Security
import java.util.UUID

class GormatiService {
  init {
    // Fix SSL Handshake Error in 7.0 (API 24)
    // https://stackoverflow.com/a/70862408
    Security.insertProviderAt(Conscrypt.newProvider(), 1)
  }
  private val client by lazy {
    HttpClient(OkHttp) {
      install(ContentNegotiation) {
        json(
          Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
          },
        )
      }
      expectSuccess = false
    }
  }


  suspend fun rateResult(requestId: UUID, speechResult: SpeechResult)  =
    withContext(Dispatchers.IO) {
      logcat { "Rating $requestId as $speechResult" }
      retry {
        runCatching {
          client.post("$BASE_URL/query/$requestId/rating") {
            contentType(ContentType.Application.Json)
            setBody(speechResult)
          }
        }.mapError {
          NetworkError(it)
        }.andThen { response ->
          logcat { "Response response: $response" }
          when(response.status) {
            HttpStatusCode.OK -> {
              runCatching {
                val id = UUID.fromString(response.body())
                logcat { "Parsed response as $id" }
                id
              }.mapError {
                ServerError("Unexpected Response")
              }
            }
            else -> Err(ServerError(response.body()))
            }
          }
        }
      }

  suspend fun submitComment(requestId: UUID, file: File): ApiResult<UUID> =
    withContext(Dispatchers.IO) {
      logcat { "Submitting ${file.name} to API Server: $requestId" }
      retry {
        runCatching {
          // Ensures refresh token is current as form submission
          // fails if refresh token is out of date
          val audioData = file.readBytes()
          // Submit the Task
          client.submitFormWithBinaryData(
            url = "$BASE_URL/query/$requestId/comment",
            formData = formData {
              append(
                "file",
                audioData,
                Headers.build {
                  append(HttpHeaders.ContentDisposition, "filename=${file.name}")
                },
              )
            },
          ) {
            // Track progress
            onUpload { bytesSentTotal, contentLength ->
              logcat { "Sent $bytesSentTotal bytes from $contentLength" }
            }
          }
        }.mapError { NetworkError(it) } // Failure is likely due to network
      }
        .andThen { response ->
          logcat { "Upload response: $response" }
          when(response.status) {
            HttpStatusCode.Conflict -> Err(ServerError("Conflict"))
            HttpStatusCode.Created ->  {
              runCatching {
                val id = UUID.fromString(response.body())
                logcat { "Parsed response as $id" }
                id
              }.mapError {
                ServerError("Unexpected Response")
              }
            }
            else -> Err(ServerError(response.body()))
          }
        }
    }
  suspend fun submitTask(requestId: UUID, file: File): ApiResult<UUID> =
    withContext(Dispatchers.IO) {
      logcat { "Submitting ${file.name} to API Server: $requestId" }
      retry {
        runCatching {
          // Ensures refresh token is current as form submission
          // fails if refresh token is out of date
          val audioData = file.readBytes()
          // Submit the Task
          client.submitFormWithBinaryData(
            url = "$BASE_URL/query",
            formData = formData {
              append("id", requestId.toString())
              append(
                "file",
                audioData,
                Headers.build {
                  append(HttpHeaders.ContentDisposition, "filename=${file.name}")
                },
              )
            },
          ) {
            // Track progress
            onUpload { bytesSentTotal, contentLength ->
              logcat { "Sent $bytesSentTotal bytes from $contentLength" }
            }
          }
        }.mapError { NetworkError(it) } // Failure is likely due to network
      }
        .andThen { response ->
          logcat { "Upload response: $response" }
          when(response.status) {
            HttpStatusCode.Conflict -> Err(ServerError("Conflict"))
            HttpStatusCode.Created ->  {
              runCatching {
                val id = UUID.fromString(response.body())
                logcat { "Parsed response as $id" }
                id
              }.mapError {
                ServerError("Unexpected Response")
              }
            }
            else -> Err(ServerError(response.body()))
          }
        }
    }


  suspend fun submitResults(
    speechResults: List<SpeechResult>,
    requestId: UUID,
  ) =
      withContext(Dispatchers.IO) {
        logcat { "Submitting results $speechResults for query $requestId" }
        retry {
          runCatching {
            client.post("$BASE_URL/query/$requestId/results") {
              contentType(ContentType.Application.Json)
              setBody(speechResults)
            }
          }.mapError {
            NetworkError(it)
          }.andThen { response ->
            logcat { "Response response: $response" }
            when(response.status) {
              HttpStatusCode.OK -> {
                runCatching {
                  val id = UUID.fromString(response.body())
                  logcat { "Parsed response as $id" }
                  id
                }.mapError {
                  ServerError("Unexpected Response")
                }
              }
              else -> Err(ServerError(response.body()))
            }
          }
        }
      }


  companion object {
    const val BASE_URL: String = BuildConfig.API_SERVER_URL
//    const val BASE_URL: String = "https://banjaraapi.reitmaier.xyz/"
//    const val BASE_URL: String = "https://development.reitmaier.xyz/"
  }
}

sealed class ApiError : Parcelable

@Parcelize
data class NetworkError(val throwable: Throwable) : ApiError()

@Parcelize
data class ServerError(val message: String) : ApiError()



typealias ApiResult<T> = Result<T, ApiError>
