package io.reitmaier.gormativoice.util

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration

fun <T> Flow<T>.chunked(maxSize: Int, interval: Duration) = channelFlow {

  val buffer = mutableListOf<T>()
  var flushJob: Job? = null

  collect { value ->

    flushJob?.cancelAndJoin()
    buffer.add(value)

    if (buffer.size >= maxSize) {
      send(buffer.toList())
      buffer.clear()
    } else {
      flushJob = launch {
        delay(interval)
        if (buffer.isNotEmpty()) {
          send(buffer.toList())
          buffer.clear()
        }
      }
    }
  }

  flushJob?.cancelAndJoin()

  if (buffer.isNotEmpty()) {
    send(buffer.toList())
    buffer.clear()
  }
}
