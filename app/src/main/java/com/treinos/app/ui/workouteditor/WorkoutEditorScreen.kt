package com.treinos.app.ui.workouteditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.treinos.app.R
import com.treinos.shared.data.WorkoutDayWithExercises
import com.treinos.shared.data.WorkoutPlanExercise
import com.treinos.shared.data.WorkoutPlanExerciseWithDetails
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutEditorScreen(
    navController: NavController,
    planId: String? = null,
    viewModel: WorkoutEditorViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val isNewPlan = planId == null || planId == "new"
    val scope = rememberCoroutineScope()

    // Load plan if editing
    LaunchedEffect(planId) {
        if (!isNewPlan) {
            planId?.toLongOrNull()?.let { viewModel.loadPlan(it) }
        } else {
            viewModel.createNewPlan()
        }
    }

    val plan by remember { mutableStateOf(viewModel.plan) }
    val days by remember { mutableStateOf(viewModel.days) }
    val isLoading by remember { mutableStateOf(viewModel.isLoading) }
    val error by remember { mutableStateOf(viewModel.error) }
    val aiState by remember { mutableStateOf(viewModel.aiGenerationState) }

    var showSaveDialog by remember { mutableStateOf(false) }
    var nameText by remember { mutableStateOf(plan?.name ?: "") }
    var descriptionText by remember { mutableStateOf(plan?.description ?: "") }
    var goalText by remember { mutableStateOf(plan?.goal ?: "hypertrophy") }
    var daysPerWeek by remember { mutableStateOf(plan?.daysPerWeek ?: 4) }
    var showAddDayDialog by remember { mutableStateOf(false) }
    var newDayName by remember { mutableStateOf("") }
    var newDayIsRest by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isNewPlan) stringResource(R.string.new_workout) else "Editar Treino", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (!isLoading) {
                        // AI Generate button
                        IconButton(
                            onClick = { viewModel.generateWithAi() },
                            enabled = aiState == WorkoutEditorViewModel.AiGenerationState.Idle && (plan?.id ?: 0L) > 0
                        ) {
                            Icon(Icons.Default.Psychology, contentDescription = "Gerar com IA", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { showSaveDialog = true }) {
                            Icon(Icons.Default.Save, contentDescription = "Salvar", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
        } else if (error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 16.sp)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Plan header fields
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = nameText,
                            onValueChange = { nameText = it },
                            label = { Text(stringResource(R.string.new_workout)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                        OutlinedTextField(
                            value = descriptionText,
                            onValueChange = { descriptionText = it },
                            label = { Text("Descrição (opcional)") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2,
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedTextField(
                                value = goalText,
                                onValueChange = { goalText = it },
                                label = { Text("Objetivo") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                            OutlinedTextField(
                                value = daysPerWeek.toString(),
                                onValueChange = { 
                                    it.toIntOrNull()?.let { daysPerWeek = it.coerceIn(2, 6) }
                                },
                                label = { Text("Dias/semana") },
                                modifier = Modifier.width(120.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                        }
                    }
                }

                // Days list
                Text("Dias do Treino", fontSize = 18.sp, fontWeight = FontWeight.Bold)

                if (days.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                            Text("Nenhum dia adicionado", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { showAddDayDialog = true }) {
                                Text("Adicionar Primeiro Dia")
                            }
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(days) { index, dayWithExercises ->
                            DayEditorCard(
                                dayIndex = index,
                                dayWithExercises = dayWithExercises,
                                onAddExercise = { 
                                    navController.navigate("exercise_picker/${dayWithExercises.day.id}") 
                                },
                                onDeleteExercise = { exerciseId ->
                                    scope.launch {
                                        viewModel.removeExerciseFromDay(dayWithExercises.day.id, exerciseId)
                                    }
                                },
                                onDeleteDay = { 
                                    scope.launch {
                                        viewModel.deleteDay(dayWithExercises.day.id)
                                    }
                                },
                                onToggleRestDay = { 
                                    val updated = dayWithExercises.day.copy(isRestDay = !dayWithExercises.day.isRestDay)
                                    scope.launch {
                                        viewModel.updateDay(updated)
                                    }
                                },
                                onReorder = { fromIndex, toIndex ->
                                    val orders = days.mapIndexed { i, d -> d.day.id to (if (i == fromIndex) toIndex else if (i == toIndex) fromIndex else i) }
                                    scope.launch {
                                        viewModel.reorderDays(orders)
                                    }
                                }
                            )
                        }
                    }
                }

                // Add day button
                FloatingActionButton(
                    onClick = { showAddDayDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Adicionar dia")
                }
            }
        }
    }

    // Save dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(if (isNewPlan) "Salvar Novo Treino" else "Salvar Alterações") },
            text = { Text("Nome: $nameText\nDias: ${days.size}") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val savedId = viewModel.savePlan(
                            name = nameText,
                            description = descriptionText,
                            goal = goalText,
                            daysPerWeek = daysPerWeek
                        )
                        showSaveDialog = false
                        snackbarHostState.showSnackbar("Treino salvo!")
                        navController.popBackStack()
                    }
                }) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                Button(onClick = { showSaveDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Add day dialog
    if (showAddDayDialog) {
        AlertDialog(
            onDismissRequest = { showAddDayDialog = false; newDayName = "" },
            title = { Text("Adicionar Dia") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newDayName,
                        onValueChange = { newDayName = it },
                        label = { Text("Nome do dia (ex: Peito/Tríceps)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = newDayIsRest,
                            onCheckedChange = { newDayIsRest = it }
                        )
                        Text("Dia de descanso")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newDayName.isNotBlank()) {
                        scope.launch {
                            viewModel.addDay(newDayName, newDayIsRest)
                            newDayName = ""
                            newDayIsRest = false
                            showAddDayDialog = false
                        }
                    }
                }, enabled = newDayName.isNotBlank()) {
                    Text("Adicionar")
                }
            },
            dismissButton = {
                Button(onClick = { showAddDayDialog = false; newDayName = "" }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // AI Generation Dialog
    if (aiState != WorkoutEditorViewModel.AiGenerationState.Idle) {
        AiGenerationDialog(
            onDismiss = { viewModel.resetAiGenerationState() },
            onConfirm = { /* dialog auto-dismisses on success */ },
            state = aiState,
            viewModel = viewModel
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayEditorCard(
    dayIndex: Int,
    dayWithExercises: WorkoutDayWithExercises,
    onAddExercise: () -> Unit,
    onDeleteExercise: (String) -> Unit,
    onDeleteDay: () -> Unit,
    onToggleRestDay: () -> Unit,
    onReorder: (Int, Int) -> Unit
) {
    val day = dayWithExercises.day
    val exercises = dayWithExercises.exercises
    val colors = MaterialTheme.colorScheme

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(0.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Day header with drag handle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drag handle
                Icon(
                    Icons.Default.DragIndicator,
                    contentDescription = "Arrastar para reordenar",
                    tint = colors.onSurfaceVariant
                )

                // Day name + rest day toggle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        day.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (day.isRestDay) {
                        androidx.compose.material3.Badge(
                            containerColor = colors.secondaryContainer,
                            contentColor = colors.onSecondaryContainer
                        ) {
                            Text("DESCANSO", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Actions
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onToggleRestDay) {
                        Icon(
                            if (day.isRestDay) Icons.Default.Check else Icons.Default.FitnessCenter,
                            contentDescription = if (day.isRestDay) "Marcar como treino" else "Marcar como descanso",
                            tint = colors.primary
                        )
                    }
                    IconButton(onClick = onAddExercise) {
                        Icon(Icons.Default.Add, contentDescription = "Adicionar exercício", tint = colors.primary)
                    }
                    IconButton(onClick = onDeleteDay) {
                        Icon(Icons.Default.Delete, contentDescription = "Excluir dia", tint = colors.error)
                    }
                }
            }

            // Exercises list
            if (exercises.isEmpty()) {
                if (!day.isRestDay) {
                    Text("Nenhum exercício. Toque em + para adicionar.", 
                        fontSize = 14.sp, color = colors.onSurfaceVariant, 
                        modifier = Modifier.padding(start = 48.dp, bottom = 8.dp))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    exercises.forEach { planExWithDetails ->
                        val exercise = planExWithDetails.exercise
                        val planEx = planExWithDetails.planExercise
                        
                        ExerciseInDayRow(
                            planExercise = planEx,
                            exerciseName = exercise?.name ?: planEx.exerciseId,
                            exerciseId = planEx.exerciseId,
                            onEdit = {
                                // TODO: Open exercise detail editor
                            },
                            onDelete = {
                                onDeleteExercise(planEx.exerciseId)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExerciseInDayRow(
    planExercise: WorkoutPlanExercise,
    exerciseName: String,
    exerciseId: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Exercise info
        Column(modifier = Modifier.weight(1f)) {
            Text(exerciseName, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${planExercise.sets}x ${planExercise.targetReps}", fontSize = 13.sp, color = colors.onSurfaceVariant)
                planExercise.targetRpe?.let { Text("RPE $it", fontSize = 13.sp, color = colors.primary) }
                Text("${planExercise.restSeconds}s", fontSize = 13.sp, color = colors.onSurfaceVariant)
            }
        }

        // Actions
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Editar série", tint = colors.primary, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Remover exercício", tint = colors.error, modifier = Modifier.size(20.dp))
            }
        }
    }
}
