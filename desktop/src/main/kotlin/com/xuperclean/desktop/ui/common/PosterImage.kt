package com.xuperclean.desktop.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.xuperclean.core.model.Poster
import com.xuperclean.core.model.VodAsset

/** Degradado inferior para leer títulos sobre la imagen. */
private val Scrim = Brush.verticalGradient(
    0f to Color.Transparent,
    0.55f to Color.Black.copy(alpha = 0.10f),
    1f to Color.Black.copy(alpha = 0.65f),
)

private fun VodAsset.posterFor(vararg preferTypes: String): Poster? {
    val byType = preferTypes.firstNotNullOfOrNull { tipo ->
        posterList.firstOrNull { it.fileType == tipo }
    }
    return byType ?: posterList.firstOrNull()
}

@Composable
fun PosterImage(
    asset: VodAsset,
    modifier: Modifier = Modifier,
    showTitle: Boolean = false,
    light: Boolean = true,
    subtitle: String? = null,
) {
    val poster = if (light) asset.posterFor("icon", "poster", "stage")
                 else asset.posterFor("stage", "poster", "icon")
    var failed by remember(asset.contentId) { mutableStateOf(false) }

    Box(modifier.clip(RoundedCornerShape(8.dp))) {
        if (poster?.fileUrl != null && !failed) {
            AsyncImage(
                model = poster.fileUrl,
                contentDescription = asset.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                onError = { failed = true },
            )
            if (showTitle) {
                Box(Modifier.matchParentSize().background(Scrim))
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                ) {
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.80f),
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = asset.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 2,
                    )
                }
            }
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
            Text(
                text = asset.name.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(8.dp),
            )
        }
    }
}
