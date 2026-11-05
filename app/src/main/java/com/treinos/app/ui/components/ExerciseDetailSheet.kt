package com.treinos.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.treinos.app.data.media.ExerciseMedia

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailSheet(
    media: ExerciseMedia?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                    text = media?.name?.takeIf { it.isNotBlank() } ?: "Exercício",
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar")
                }
            }

            val imageUrl = media?.imageUrl
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Imagem do exercício ${media.name.ifBlank { "exercício" }}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .size(240.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                ExerciseImage(
                    imageUrl = null,
                    exerciseName = media?.name.orEmpty(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    size = 240.dp,
                )
            }

            val muscles = media?.primaryMuscles?.joinToString(", ").orEmpty()
            if (muscles.isNotBlank()) {
                Text(text = "Músculos principais: $muscles", style = MaterialTheme.typography.bodyMedium)
            }
            if (media != null && !media.equipment.isNullOrBlank()) {
                Text(text = "Equipamento: ${media.equipment}", style = MaterialTheme.typography.bodyMedium)
            }

            Text(text = "Instruções", style = MaterialTheme.typography.titleMedium)
            val instructions = media?.instructions.orEmpty()
            if (instructions.isEmpty()) {
                Text(text = "Sem instruções disponíveis.", style = MaterialTheme.typography.bodyMedium)
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
