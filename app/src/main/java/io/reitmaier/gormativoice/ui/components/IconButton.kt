package io.reitmaier.gormativoice.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun IconButton(
  imageVector: ImageVector,
  contentDescription: String,
  modifier: Modifier = Modifier,
  colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  IconButton(
    enabled = enabled,
    onClick = onClick,
    modifier = modifier,
    colors = colors
  ) {
    Icon(
      imageVector = imageVector,
      contentDescription = contentDescription
    )
  }
}


@Preview
@Composable
private fun IconButtonPreview() {
  IconButton(
    imageVector = Icons.Filled.Search,
    contentDescription = "Search",
  ) {}
}
