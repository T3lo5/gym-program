package com.treinos.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun ExerciseImage(
    imageUrl: String?,
    exerciseName: String,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    contentDescription: String? = null,
) {
    if (imageUrl.isNullOrBlank()) {
        Box(
            modifier = modifier
                .size(size)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = exerciseName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    AsyncImage(
        model = imageUrl,
        contentDescription = contentDescription ?: "Imagem do exercício $exerciseName",
        modifier = modifier.size(size),
        contentScale = ContentScale.Crop,
    )
}
