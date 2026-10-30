package com.treinos.app.ui.workouteditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.material3.icons.Icons
import androidx.material3.icons.filled.CheckCircle
import androidx.material3.icons.filled.Close
import androidx.material3.icons.filled.FitnessCenter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiGenerationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    state: AiGenerationState,
    viewModel: WorkoutEditorViewModel
) {
    AlertDialog(
        onDismissRequest = { if (state != AiGenerationState.Generating) onDismiss() },
        title = { Text(when (state) {
            AiGenerationState.Idle -> "Gerar treino com IA"
            AiGenerationState.Generating -> "Gerando treino..."
            AiGenerationState.Success -> "Treino gerado!"
            AiGenerationState.Error -> "Erro na geração"
        }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state) {
                    AiGenerationState.Idle -> {
                        Text("A IA vai criar um treino baseado nas suas preferências. Revise antes de salvar.")
                    }
                    AiGenerationState.Generating -> {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 4.dp
                            )
                        }
                        Text("Isso pode levar alguns segundos...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AiGenerationState.Success -> {
                        val plan = viewModel.plan
                        plan?.let { p ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                                    Icon(FitnessCenter, contentDescription = null, tint = MaterialTheme.colorScheme.primary, size = 32.dp)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(p.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text("${viewModel.days.size} dias • ${p.goal}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text("Revise no editor e salve se gostar.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    AiGenerationState.Error -> {
                        Text(viewModel.error ?: "Erro desconhecido. Tente novamente.", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                AiGenerationState.Idle -> Button(onClick = { viewModel.generateWithAi() }) { Text("Gerar") }
                AiGenerationState.Generating -> Button(onClick = {}, enabled = false) { Text("Gerando...") }
                AiGenerationState.Success -> Button(onClick = { onConfirm(); onDismiss() }) { Text("Ir para o editor") }
                AiGenerationState.Error -> Button(onClick = { viewModel.generateWithAi() }) { Text("Tentar novamente") }
            }
        },
        dismissButton = {
            if (state != AiGenerationState.Generating) {
                Button(onClick = onDismiss) { Text(state == AiGenerationState.Success ? "Fechar" : "Cancelar") }
            }
        }
    )
}

enum class AiGenerationState {
    Idle,
    Generating,
    Success,
    Error
}