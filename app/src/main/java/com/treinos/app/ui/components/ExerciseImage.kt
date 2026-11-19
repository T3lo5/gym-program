package com.treinos.app.ui.components

import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.treinos.app.ui.modalityAccentFor

/**
 * Imagem de exercício resiliente (H3-32): cantos do tema, crossfade e fallback
 * de letra tanto sem URL quanto enquanto carrega e quando a rede falha — nunca
 * um quadrado vazio. A letra escala com o tamanho do container.
 *
 * H3-39: `imageRes` é o drawable local de fallback (APK, offline) usado quando
 * não há `imageUrl`. A URL do catálogo remoto vence sobre o drawable local
 * (fonte preferencial).
 *
 * H6-7: fallback de letra com fundo tonal da modalidade em vez de letra cinza
 * sobre `surfaceVariant`.
 */
@Composable
fun ExerciseImage(
    imageUrl: String?,
    exerciseName: String,
    imageRes: Int? = null,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    contentDescription: String? = null,
    modalityKind: String = "strength",
) {
    val shape = MaterialTheme.shapes.small
    val accent = modalityAccentFor(modalityKind)
    if (imageUrl.isNullOrBlank() && imageRes == null) {
        ExerciseImageFallback(
            exerciseName = exerciseName,
            size = size,
            modifier = modifier.size(size).clip(shape),
            accent = accent,
        )
        return
    }

    if (imageUrl.isNullOrBlank()) {
        // H3-39: drawable local offline — sem rede, sem Coil.
        val description = contentDescription ?: "Imagem do exercício $exerciseName"
        Image(
            painter = painterResource(id = imageRes!!),
            contentDescription = description,
            modifier = modifier
                .size(size)
                .clip(shape)
                .semantics { this.contentDescription = description },
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
    val description = contentDescription ?: "Imagem do exercício $exerciseName"
    SubcomposeAsyncImage(
        model = request,
        contentDescription = null,
        modifier = modifier
            .size(size)
            .clip(shape)
            .semantics { this.contentDescription = description },
        contentScale = ContentScale.Crop,
        loading = { ExerciseImageFallback(exerciseName, size, accent = accent) },
        error = { ExerciseImageFallback(exerciseName, size, accent = accent) },
    )
}

/**
 * Fallback de letra do [ExerciseImage], compartilhado com o detalhe do
 * exercício: fundo tonal da modalidade e a inicial do nome, escalada pelo
 * tamanho (`titleMedium` até 48 dp, `headlineMedium` acima). Nunca um quadrado vazio.
 */
@Composable
fun ExerciseImageFallback(
    exerciseName: String,
    size: Dp,
    modifier: Modifier = Modifier,
    accent: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(accent.copy(alpha = 0.15f))
            .clip(MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = exerciseName.take(1).uppercase(),
            style = if (size <= 48.dp) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.headlineMedium
            },
            color = accent,
        )
    }
}
