package io.reitmaier.gormativoice.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun MyBottomAppBar(
  modifier: Modifier = Modifier,
  containerColor: Color = BottomAppBarDefaults.containerColor,
  contentColor: Color = contentColorFor(containerColor),
  tonalElevation: Dp = BottomAppBarDefaults.ContainerElevation,
  contentPadding: PaddingValues = BottomAppBarDefaults.ContentPadding,
  windowInsets: WindowInsets = BottomAppBarDefaults.windowInsets,
  content: @Composable RowScope.() -> Unit
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        //shape = ShapeKeyTokens.CornerNone,
        modifier = modifier
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(windowInsets)
                .height(80.0.dp)
                .padding(contentPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}