package com.treinos.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.treinos.app.ui.KeepScreenOnEffect
import com.treinos.app.ui.MobilityRoutine
import com.treinos.app.ui.MobilityRoutineTemplates
import com.treinos.app.ui.components.ScreenTopBar
import com.treinos.app.ui.formatDuration
import kotlinx.coroutines.delay

@Composable
fun MobilityScreen(
    onBack: () -> Unit,
    onFinishMobility: (planId: Long, dayId: Long, routineId: String, elapsedSeconds: Long) -> Unit = { _, _, _, _ -> },
    planId: Long = 0L,
    dayId: Long = 0L,
    presetRoutineId: String? = null,
) {
    var selectedRoutine by remember { mutableStateOf<MobilityRoutine?>(null) }
    var currentMovementIndex by remember { mutableStateOf(0) }
    var timeRemaining by remember { mutableStateOf(0) }
    var isRunning by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableStateOf(0L) }
    var showSafety by remember { mutableStateOf(false) }

    val plannedRoutine = presetRoutineId?.let { MobilityRoutineTemplates.getRoutine(it) }

    LaunchedEffect(presetRoutineId, selectedRoutine == null, plannedRoutine) {
        if (presetRoutineId != null && selectedRoutine == null && plannedRoutine != null) {
            selectedRoutine = plannedRoutine
            currentMovementIndex = 0
            val first = plannedRoutine.movements[0]
            timeRemaining = first.durationSeconds
            elapsedSeconds = 0L
            isRunning = true
            isPaused = false
        }
    }

    // Rotina de mobilidade em andamento mantém a tela acesa (H3-26).
    KeepScreenOnEffect(enabled = selectedRoutine != null)

    if (selectedRoutine == null) {
        Scaffold(
            topBar = {
                ScreenTopBar(
                    title = "Mobilidade e alongamento",
                    onBack = onBack,
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "Aviso de segurança",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Esta rotina é para bem-estar e recuperação, não substitui avaliação profissional. " +
                                    "Não é diagnóstico, tratamento ou reabilitação.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = { showSafety = !showSafety }) {
                                Text(if (showSafety) "Ocultar sinais de alerta" else "Ver sinais de alerta")
                            }
                            if (showSafety) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Procure um profissional se houver:", style = MaterialTheme.typography.labelLarge)
                                    Text("• Dor irradiada para braço/perna", style = MaterialTheme.typography.bodySmall)
                                    Text("• Formigamento ou dormência", style = MaterialTheme.typography.bodySmall)
                                    Text("• Perda de força súbita", style = MaterialTheme.typography.bodySmall)
                                    Text("• Trauma recente ou febre", style = MaterialTheme.typography.bodySmall)
                                    Text("• Dor que piora com o movimento", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                item { Text("Rotinas curadas", style = MaterialTheme.typography.titleMedium) }

                items(MobilityRoutineTemplates.routines.filter { it.movements.isNotEmpty() }) { routine ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(routine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(routine.description, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${formatDuration(MobilityRoutineTemplates.totalDurationSeconds(routine).toLong())} • ${routine.movements.size} movimentos • ${routine.focus}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = {
                                    selectedRoutine = routine
                                    currentMovementIndex = 0
                                    val first = routine.movements[0]
                                    timeRemaining = first.durationSeconds
                                    elapsedSeconds = 0L
                                    isRunning = true
                                    isPaused = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Iniciar")
                            }
                        }
                    }
                }
            }
        }
    } else {
        val routine = selectedRoutine!!
        val movement = routine.movements.getOrNull(currentMovementIndex)

        LaunchedEffect(isRunning, isPaused, timeRemaining) {
            if (isRunning && !isPaused && timeRemaining > 0) {
                delay(1000)
                timeRemaining = timeRemaining - 1
                elapsedSeconds = elapsedSeconds + 1L
                if (timeRemaining == 0) {
                    // Avançar automaticamente para próximo movimento
                    if (currentMovementIndex < routine.movements.size - 1) {
                        currentMovementIndex++
                        val nextMovement = routine.movements[currentMovementIndex]
                        timeRemaining = nextMovement.durationSeconds
                    } else {
                        // Último movimento terminado
                        isRunning = false
                    }
                }
            }
        }

        Scaffold(
            topBar = {
                ScreenTopBar(
                    title = routine.name,
                    onBack = { 
                        selectedRoutine = null
                        isRunning = false
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "Movimento ${currentMovementIndex + 1} de ${routine.movements.size}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        if (movement != null) {
                            Text(
                                movement.name + (movement.side?.let { " (${if (it == "left") "esquerdo" else "direito"})" } ?: ""),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                movement.cue,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                            Text(
                                formatDuration(timeRemaining.toLong()),
                                style = MaterialTheme.typography.displayLarge,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                        if (currentMovementIndex < routine.movements.size - 1 && movement != null) {
                            Text(
                                "Próximo: ${routine.movements[currentMovementIndex + 1].name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            if (currentMovementIndex > 0) {
                                currentMovementIndex--
                                timeRemaining = routine.movements[currentMovementIndex].durationSeconds
                                isPaused = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = currentMovementIndex > 0,
                    ) {
                        Text("Voltar")
                    }
                    Button(
                        onClick = {
                            if (isRunning) {
                                isPaused = !isPaused
                            } else {
                                isRunning = true
                                isPaused = false
                                if (timeRemaining == 0) {
                                    if (currentMovementIndex < routine.movements.size - 1) {
                                        currentMovementIndex++
                                        timeRemaining = routine.movements[currentMovementIndex].durationSeconds
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (isRunning && !isPaused) "Pausar" else if (isPaused) "Retomar" else "Iniciar")
                    }
                    OutlinedButton(
                        onClick = {
                            if (currentMovementIndex < routine.movements.size - 1) {
                                currentMovementIndex++
                                timeRemaining = routine.movements[currentMovementIndex].durationSeconds
                                isPaused = false
                            } else {
                                // Finalizar
                                isRunning = false
                                val duration = elapsedSeconds
                                onFinishMobility(planId, dayId, routine.id, elapsedSeconds)
                                selectedRoutine = null
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (currentMovementIndex < routine.movements.size - 1) "Avançar" else "Finalizar")
                    }
                }

                OutlinedButton(
                    onClick = {
                        isRunning = false
                        val duration = elapsedSeconds
                        onFinishMobility(planId, dayId, routine.id, elapsedSeconds)
                        selectedRoutine = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Finalizar antes do fim")
                }
            }
        }
    }
}
