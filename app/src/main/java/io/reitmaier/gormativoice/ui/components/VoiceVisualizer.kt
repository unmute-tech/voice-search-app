package io.reitmaier.gormativoice.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.tooling.preview.Preview
import io.reitmaier.gormativoice.ui.theme.GormatiVoiceTheme
import io.reitmaier.gormativoice.ui.voice.ViewState
import java.util.*
import kotlin.random.Random

private const val BAR_WIDTH = 30f
private const val GAP_WIDTH = 15f
private const val MAX_AMP = 3000
private const val MIN_HEIGHT = 10
private const val RADIUS = 3f

@Composable
internal fun VoiceVisualizer(
  modifier: Modifier = Modifier,
  streamingState: ViewState,
  color: Color = MaterialTheme.colorScheme.primary
) {
  Canvas(modifier = modifier) {
    val height = this.size.height / 2
    val canvasWidth = size.width

    when(streamingState) {
      is ViewState.Streaming -> {
        val count = (canvasWidth / (BAR_WIDTH)).toInt().coerceAtMost(streamingState.recordedAmplitudes.size)
        val animatedVolumeWidth = count * BAR_WIDTH
        val startOffset = (canvasWidth - animatedVolumeWidth) / 2
        translate(startOffset, height) {
          streamingState.recordedAmplitudes.takeLast(count).forEachIndexed { index, amplitude ->
            val boxHeight = MIN_HEIGHT + (height * (amplitude.toFloat() / MAX_AMP))
            drawRoundRect(
              color,
              topLeft = Offset(
                BAR_WIDTH * index,
                -boxHeight / 2
              ),
              size = Size(GAP_WIDTH, boxHeight),
              cornerRadius = CornerRadius(RADIUS, RADIUS)
            )
          }
        }
      }
      is ViewState.Idle,
      is ViewState.ImageResults,
      is ViewState.Processing -> {}

    }
  }
}

@Preview
@Composable
fun VoiceVisualiserPreview() {
  GormatiVoiceTheme {
    VoiceVisualizer(
      modifier = Modifier.fillMaxSize(),
      streamingState = ViewState.Streaming(
        List(20) { Random.nextInt(10, 90)},
        UUID.randomUUID(),
      ))
  }
}