package io.reitmaier.gormativoice.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@ExperimentalMaterial3Api
@Composable
fun GormatiVoiceTopAppBar(
  @StringRes titleRes: Int,
  navigationIcon: ImageVector,
  navigationIconContentDescription: String?,
  actionIcon: ImageVector,
  actionIconContentDescription: String?,
  modifier: Modifier = Modifier,
  colors: TopAppBarColors = TopAppBarDefaults.centerAlignedTopAppBarColors(),
  onNavigationClick: () -> Unit = {},
  onActionClick: () -> Unit = {},
) {
  CenterAlignedTopAppBar(
    title = { Text(text = stringResource(id = titleRes)) },
    navigationIcon = {
      IconButton(onClick = onNavigationClick) {
        Icon(
          imageVector = navigationIcon,
          contentDescription = navigationIconContentDescription,
          tint = MaterialTheme.colorScheme.onSurface,
        )
      }
    },
    actions = {
      IconButton(onClick = onActionClick) {
        Icon(
          imageVector = actionIcon,
          contentDescription = actionIconContentDescription,
          tint = MaterialTheme.colorScheme.onSurface,
        )
      }
    },
    colors = colors,
    modifier = modifier.testTag("transcriptToolTopAppBar"),
  )
}

/**
 * Top app bar with action, displayed on the right
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GormatiVoiceTopAppBar(
  title: String,
  recording: Boolean,
  playing: Boolean,
  modifier: Modifier = Modifier,
  colors: TopAppBarColors = TopAppBarDefaults.centerAlignedTopAppBarColors(),
  onRecordClick: () -> Unit = {},
  onPlayClick: () -> Unit = {},
) {
  CenterAlignedTopAppBar(
    title = { Text(text = title) },
    navigationIcon = {
      IconButton(onClick = onPlayClick) {
        Icon(
          imageVector = if(playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
          contentDescription = if(playing) "Stop Playing Query" else "Start Playing Query",
          tint = MaterialTheme.colorScheme.onSurface,
        )
      }
    },
    actions = {
      if(recording) {
         Box(
            modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.Red)
        )
      }
      IconButton(onClick = onRecordClick) {
        Icon(
          imageVector = if(recording) Icons.Filled.Stop else Icons.Filled.Mic,
          contentDescription = if(recording) "Stop Recording Voice Over" else "Start Recording Voice Over",
          tint = MaterialTheme.colorScheme.onSurface,
        )
      }
    },
    colors = colors,
    modifier = modifier.testTag("niaTopAppBar"),
  )
}

@ExperimentalMaterial3Api
@Preview("Top App Bar")
@Composable
private fun GormatiVoiceTopAppBarPreview() {
  GormatiVoiceTopAppBar(
    title = "",
    recording = false,
    playing = true,
  )
}
