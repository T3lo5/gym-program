package com.treinos.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.treinos.app.data.media.ExerciseMedia
import com.treinos.app.R
import com.treinos.app.ui.equipmentLabel
import com.treinos.app.ui.muscleLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailSheet(
    media: ExerciseMedia?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val fallbackName = stringResource(R.string.exercise_detail_fallback_name)
    val imageDescription = stringResource(
        R.string.exercise_detail_image_desc,
        media?.name.orEmpty().ifBlank { fallbackName.lowercase() },
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = media?.name?.takeIf { it.isNotBlank() } ?: fallbackName,
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
                }
            }

            val imageUrl = media?.imageUrl
            val imageModifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.small)
            if (imageUrl.isNullOrBlank()) {
                ExerciseImageFallback(
                    exerciseName = media?.name.orEmpty(),
                    size = 240.dp,
                    modifier = imageModifier,
                )
            } else {
                val context = LocalContext.current
                val request = remember(imageUrl) {
                    ImageRequest.Builder(context)
                        .data(imageUrl)
                        .crossfade(true)
                        .build()
                }
                SubcomposeAsyncImage(
                    model = request,
                    contentDescription = null,
                    modifier = imageModifier.semantics {
                        contentDescription = imageDescription
                    },
                    contentScale = ContentScale.Crop,
                    loading = { ExerciseImageFallback(media?.name.orEmpty(), 240.dp) },
                    error = { ExerciseImageFallback(media?.name.orEmpty(), 240.dp) },
                )
            }

            val muscles = media?.primaryMuscles?.joinToString(", ") { muscleLabel(it) }.orEmpty()
            if (muscles.isNotBlank()) {
                Text(
                    text = stringResource(R.string.exercise_detail_muscles, muscles),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (media != null && !media.equipment.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.exercise_detail_equipment, equipmentLabel(media.equipment)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Text(
                text = stringResource(R.string.exercise_detail_instructions),
                style = MaterialTheme.typography.titleMedium,
            )
            val instructions = media?.instructions.orEmpty()
            if (instructions.isEmpty()) {
                Text(
                    text = stringResource(R.string.exercise_detail_no_instructions),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                instructions.forEachIndexed { index, instruction ->
                    Text(
                        text = "${index + 1}. $instruction",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
