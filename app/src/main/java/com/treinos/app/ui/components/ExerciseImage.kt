package com.treinos.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

/**
 * Imagem de exercício resiliente (H3-32): cantos do tema, crossfade e fallback
 * de letra tanto sem URL quanto enquanto carrega e quando a rede falha — nunca
 * um quadrado vazio. A letra escala com o tamanho do container.
 */
@Composable
fun ExerciseImage(
    imageUrl: String?,
    exerciseName: String,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    contentDescription: String? = null,
) {
    val shape = MaterialTheme.shapes.small
    if (imageUrl.isNullOrBlank()) {
        ExerciseImageFallback(
            exerciseName = exerciseName,
            size = size,
            modifier = modifier.size(size).clip(shape),
        )
        return
    }

    val context = LocalContext.current
    val request = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .build()
    }
    // A descrição fica no container: os slots loading/error do SubcomposeAsyncImage
    // não recebem o contentDescription do parâmetro, e a imagem precisa anunciá-la
    // também enquanto carrega ou quando cai no fallback.
    val description = contentDescription ?: "Imagem do exercício $exerciseName"
    SubcomposeAsyncImage(
        model = request,
        contentDescription = null,
        modifier = modifier
            .size(size)
            .clip(shape)
            .semantics { this.contentDescription = description },
        contentScale = ContentScale.Crop,
        loading = { ExerciseImageFallback(exerciseName, size) },
        error = { ExerciseImageFallback(exerciseName, size) },
    )
}

/**
 * Fallback de letra do [ExerciseImage], compartilhado com o detalhe do
 * exercício: fundo `surfaceVariant` e a inicial do nome, escalada pelo tamanho
 * (`titleMedium` até 48 dp, `headlineMedium` acima). Nunca um quadrado vazio.
 */
@Composable
fun ExerciseImageFallback(
    exerciseName: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = exerciseName.take(1).uppercase(),
            style = if (size <= 48.dp) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.headlineMedium
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}