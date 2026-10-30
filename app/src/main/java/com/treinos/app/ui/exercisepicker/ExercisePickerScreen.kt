package com.treinos.app.ui.exercisepicker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.compose.material3.Chip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.getString
import androidx.navigation.compose.rememberNavController
import com.treinos.app.R
import com.treinos.shared.data.Exercise
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerScreen(
    navController: NavController,
    dayId: String,
    viewModel: ExercisePickerViewModel = hiltViewModel()
) {
    val exercises by viewModel.filteredExercises.collectAsStateWithLifecycle()
    val muscleGroups by viewModel.allMuscleGroups.collectAsStateWithLifecycle(initialValue = emptyList())
    val equipments by viewModel.allEquipments.collectAsStateWithLifecycle(initialValue = emptyList())

    var showSearch by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showSearch) {
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = { searchText = it; viewModel.searchQuery = it },
                            placeholder = { Text(stringResource(R.string.exercises)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchText.isNotBlank()) {
                                    IconButton(onClick = { searchText = ""; viewModel.searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Limpar")
                                    }
                                }
                            },
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        )
                    } else {
                        Text("Selecionar Exercício", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    if (showSearch) {
                        IconButton(onClick = { showSearch = false; searchText = ""; viewModel.searchQuery = "" }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                        }
                    } else {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                },
                actions = {
                    if (!showSearch) {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar")
                        }
                    }
                    if (viewModel.selectedMuscle != null || viewModel.selectedEquipment != null) {
                        IconButton(onClick = { viewModel.clearFilters() }) {
                            Icon(Icons.Default.FilterListOff, contentDescription = "Limpar filtros")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Filter chips
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Muscle group filter
                if (muscleGroups.isNotEmpty()) {
                    ScrollableTabRow(
                        selectedTabIndex = muscleGroups.indexOfFirst { it == viewModel.selectedMuscle }.coerceAtLeast(0),
                        modifier = Modifier.fillMaxWidth(),
                        edgePadding = 16.dp,
                        indicator = { tabPositions ->
                            // Custom indicator handled by Chip selection
                        }
                    ) {
                        muscleGroups.forEachIndexed { index, muscle ->
                            val isSelected = viewModel.selectedMuscle == muscle
                            Chip(
                                onClick = { viewModel.selectedMuscle = if (isSelected) null else muscle },
                                selected = isSelected,
                                label = { Text(muscle.replace("_", " ").capitalize(), maxLines = 1) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }

                // Equipment filter
                if (equipments.isNotEmpty()) {
                    ScrollableTabRow(
                        selectedTabIndex = equipments.indexOfFirst { it == viewModel.selectedEquipment }.coerceAtLeast(0),
                        modifier = Modifier.fillMaxWidth(),
                        edgePadding = 16.dp,
                        indicator = { tabPositions -> }
                    ) {
                        equipments.forEachIndexed { index, equip ->
                            val isSelected = viewModel.selectedEquipment == equip
                            Chip(
                                onClick = { viewModel.selectedEquipment = if (isSelected) null else equip },
                                selected = isSelected,
                                label = { Text(equip.replace("_", " ").capitalize(), maxLines = 1) },
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }
            }

            // Exercise list
            if (exercises.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontal, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Default.SearchOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary, size = 64.dp)
                        Text("Nenhum exercício encontrado", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Tente ajustar os filtros ou a busca", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(exercises) { exercise ->
                        ExercisePickerCard(
                            exercise = exercise,
                            onSelect = {
                                // Return selected exercise to editor
                                navController.getPreviousBackStackEntry()?.toRoute()?.let { route ->
                                    // We'll pass the exercise ID via navigation argument or shared state
                                    // For now, navigate back and the editor can read from a shared repository
                                }
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerCard(
    exercise: Exercise,
    onSelect: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val primaryMuscle = exercise.primaryMuscles.firstOrNull() ?: ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(0.dp),
        onClick = onSelect,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        shape = RoundedCornerShape(12.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Exercise image placeholder or first image
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                if (exercise.images.isNotEmpty()) {
                    // TODO: Load image with Coil
                    Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = colors.primary, size = 32.dp)
                } else {
                    Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = colors.onSurfaceVariant, size = 32.dp)
                }
            }

            // Exercise info
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    exercise.namePt?.takeIf { it.isNotBlank() } ?: exercise.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.TextOverflow.Ellipsis
                )
                if (exercise.namePt != null && exercise.namePt != exercise.name) {
                    Text(exercise.name, fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (primaryMuscle.isNotBlank()) {
                        Chip(
                            modifier = Modifier.height(24.dp),
                            onClick = { /* noop */ },
                            label = { Text(primaryMuscle.replace("_", " ").capitalize(), fontSize = 11.sp) },
                            colors = ChipDefaults.chipColors(containerColor = colors.primaryContainer)
                        )
                    }
                    exercise.equipment?.let { equip ->
                        Chip(
                            modifier = Modifier.height(24.dp),
                            onClick = { /* noop */ },
                            label = { Text(equip.replace("_", " ").capitalize(), fontSize = 11.sp) },
                            colors = ChipDefaults.chipColors(containerColor = colors.secondaryContainer)
                        )
                    }
                    Chip(
                        modifier = Modifier.height(24.dp),
                        onClick = { /* noop */ },
                        label = { Text(exercise.level.capitalize(), fontSize = 11.sp) },
                        colors = ChipDefaults.chipColors(containerColor = colors.tertiaryContainer)
                    )
                }
            }

            // Add button
            IconButton(onClick = onSelect) {
                Icon(Icons.Default.AddCircle, contentDescription = "Adicionar ao treino", tint = colors.primary, size = 28.dp)
            }
        }
    }
}

import androidx.material3.icons.Icons
import androidx.material3.icons.filled.AddCircle
import androidx.material3.icons.filled.FilterListOff
import androidx.material3.icons.filled.FitnessCenter
import androidx.material3.icons.filled.Search