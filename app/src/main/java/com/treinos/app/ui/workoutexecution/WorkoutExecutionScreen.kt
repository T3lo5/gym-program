package com.treinos.app.ui.workoutexecution

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateInt
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.treinos.app.R
import com.treinos.shared.data.SetLog
import com.treinos.shared.data.WorkoutPlanExercise
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutExecutionScreen(
    navController: NavController,
    planId: Long,
    dayId: Long,
    viewModel: WorkoutExecutionViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    // Start session on first composition
    LaunchedEffect(planId, dayId) {
        viewModel.startSession(planId, dayId)
    }

    val session by remember { mutableStateOf(viewModel.session) }
    val exerciseLogs by remember { mutableStateOf(viewModel.exerciseLogs) }
    val currentIndex by remember { mutableStateOf(viewModel.currentExerciseIndex) }
    val restTimer by remember { mutableStateOf(viewModel.restTimerSeconds) }
    val isRestRunning by remember { mutableStateOf(viewModel.isRestTimerRunning) }
    val elapsedSeconds by remember { mutableStateOf(viewModel.elapsedSessionSeconds) }
    val progress by remember { mutableStateOf(viewModel.progress) }
    val currentLog = exerciseLogs.getOrNull(currentIndex)
    val currentExercise = currentLog?.exercise
    val planExercise = currentLog?.planExercise
    val sets = currentLog?.sets ?: emptyList()

    val colors = MaterialTheme.colorScheme
    val isInRest = isRestRunning && restTimer > 0
    var showExitDialog by remember { mutableStateOf(false) }

    // Session timer format
    val sessionTime = formatDuration(elapsedSeconds)
    val restTime = formatDuration(restTimer.toLong())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            progress = progress,
                            modifier = Modifier.size(24.dp),
                            color = colors.primary,
                            strokeWidth = 3.dp
                        )
                        Column {
                            Text(currentExercise?.name ?: "Carregando...", fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("${currentIndex + 1} de ${exerciseLogs.size}", fontSize = 12.sp, color = colors.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showExitDialog = true }) {
                        Icon(Icons.Default.Close, contentDescription = "Sair")
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: Settings */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "Configurações")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isInRest) colors.secondaryContainer else colors.surfaceContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Session timer + Rest timer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Session timer
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)
                        .background(
                            color = if (isInRest) colors.secondaryContainer else colors.primaryContainer,
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (isInRest) "DESCANSO" else "TREINO", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isInRest) colors.onSecondaryContainer else colors.onPrimaryContainer)
                        Text(
                            if (isInRest) restTime else sessionTime,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isInRest) colors.onSecondaryContainer else colors.onPrimaryContainer
                        )
                    }
                }
            }

            // Current exercise info
            currentExercise?.let { exercise ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Exercise image
                            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = colors.primary, modifier = Modifier.size(40.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(exercise.namePt ?: exercise.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                if (planExercise != null) {
                                    Text(
                                        "${planExercise.sets} séries × ${planExercise.targetReps} reps  •  ${planExercise.restSeconds}s descanso",
                                        fontSize = 13.sp,
                                        color = colors.onSurfaceVariant
                                    )
                                    planExercise.targetRpe?.let { Text("RPE alvo: $it", fontSize = 13.sp, color = colors.primary) }
                                }
                            }
                        }

                        // Sets list
                        if (sets.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                sets.forEach { set ->
                                    SetRow(
                                        set = set,
                                        setNumber = set.setNumber,
                                        isLastSet = set.setNumber == sets.size,
                                        onComplete = { reps, weight, rpe ->
                                            viewModel.completeSet(
                                                exerciseLogId = currentLog!!.log.id,
                                                setNumber = set.setNumber,
                                                reps = reps,
                                                weightKg = weight,
                                                rpe = rpe,
                                                durationSeconds = null
                                            )
                                        },
                                        onUncomplete = {
                                            viewModel.uncompleteSet(currentLog!!.log.id, set.setNumber)
                                        }
                                    )
                                }
                            }
                        }

                        // Rest timer controls
                        if (isInRest || !sets.all { it.isCompleted }) {
                            RestTimerControls(
                                seconds = restTimer,
                                isRunning = isRestRunning,
                                onStart = { viewModel.startRestTimer(planExercise?.restSeconds ?: 90) },
                                onPause = { viewModel.pauseRestTimer() },
                                onSkip = { viewModel.skipRestTimer() }
                            )
                        }

                        // Navigation
                        if (sets.all { it.isCompleted }) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.previousExercise() },
                                    enabled = currentIndex > 0,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.SkipPrevious, contentDescription = "Anterior")
                                    Text("Anterior")
                                }
                                Button(
                                    onClick = { viewModel.nextExercise() },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (currentIndex == exerciseLogs.size - 1) "Finalizar" else "Próximo")
                                    Icon(Icons.Default.SkipNext, contentDescription = "Próximo")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Empty state
        if (currentExercise == null && session != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Sair do treino?") },
            text = { Text("O progresso será salvo. Deseja realmente sair?") },
            confirmButton = {
                Button(onClick = { viewModel.cancelSession(); navController.popBackStack() }) {
                    Text("Sair e salvar")
                }
            },
            dismissButton = {
                Button(onClick = { showExitDialog = false }) {
                    Text("Continuar")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetRow(
    set: SetLog,
    setNumber: Int,
    isLastSet: Boolean,
    onComplete: (Int, Double?, Double?) -> Unit,
    onUncomplete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val isCompleted = set.isCompleted

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) colors.primaryContainer.copy(alpha = 0.3f) else colors.surfaceContainerLow
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Set number
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Concluído", tint = colors.primary, modifier = Modifier.size(24.dp))
                } else {
                    Text("$setNumber", fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = colors.onSurfaceVariant)
                }
            }

            // Input fields
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Reps
                OutlinedTextField(
                    value = set.reps?.toString() ?: "",
                    onValueChange = { /* handled in completion */ },
                    label = { Text("Reps") },
                    modifier = Modifier.width(80.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = TextFieldDefaults.outlinedTextFieldColors(containerColor = colors.surface),
                    enabled = !isCompleted
                )
                // Weight
                OutlinedTextField(
                    value = set.weightKg?.toString() ?: "",
                    onValueChange = { /* handled in completion */ },
                    label = { Text("kg") },
                    modifier = Modifier.width(80.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = TextFieldDefaults.outlinedTextFieldColors(containerColor = colors.surface),
                    enabled = !isCompleted
                )
                // RPE
                OutlinedTextField(
                    value = set.rpe?.toString() ?: "",
                    onValueChange = { /* handled in completion */ },
                    label = { Text("RPE") },
                    modifier = Modifier.width(70.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = TextFieldDefaults.outlinedTextFieldColors(containerColor = colors.surface),
                    enabled = !isCompleted
                )
            }

            // Actions
            if (isCompleted) {
                IconButton(onClick = onUncomplete) {
                    Icon(Icons.Default.Undo, contentDescription = "Desfazer", tint = colors.onSurfaceVariant)
                }
            } else {
                Button(
                    onClick = {
                        // In a real app, read from the text fields
                        // For now, we'll use placeholder values
                        onComplete(0, null, null)
                    }
                ) {
                    Text("OK")
                }
            }
        }
    }
}

@Composable
fun RestTimerControls(
    seconds: Int,
    isRunning: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onSkip: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.secondaryContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Timer, contentDescription = null, tint = colors.onSecondaryContainer)
                Text("Timer de Descanso", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onSecondaryContainer)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = if (isRunning) onPause else onStart,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) colors.errorContainer else colors.primaryContainer,
                        contentColor = if (isRunning) colors.onErrorContainer else colors.onPrimaryContainer
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                    Text(if (isRunning) "Pausar" else "Iniciar")
                }
                Button(
                    onClick = onSkip,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceContainerHigh),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Pular")
                    Icon(Icons.Default.SkipNext, contentDescription = null)
                }
            }
        }
    }
}

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
