package com.treinos.app.ui.workoutlist

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.compose.ui.input.keyboard.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.material3.icons.Icons
import androidx.material3.icons.filled.Add
import androidx.material3.icons.filled.Delete
import androidx.material3.icons.filled.Edit
import androidx.material3.icons.filled.FitnessCenter
import androidx.material3.icons.filled.Search
import androidx.navigation.compose.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.treinos.app.R
import com.treinos.shared.data.WorkoutGoal
import com.treinos.shared.data.WorkoutPlan
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutListScreen(
    navController: NavController,
    viewModel: WorkoutListViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val plans by viewModel.filteredPlans.collectAsStateWithLifecycle()
    var showSearch by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showSearch) {
                        TextField(
                            value = searchText,
                            onValueChange = { searchText = it; viewModel.searchQuery = it },
                            placeholder = { Text(stringResource(R.string.exercises)) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions.Default,
                            singleLine = true,
                            leadingIcon = { Icon(Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchText.isNotBlank()) {
                                    IconButton(onClick = { searchText = ""; viewModel.searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Limpar")
                                    }
                                }
                            },
                            colors = TextFieldDefaults.textFieldColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        )
                    } else {
                        Text(stringResource(R.string.my_workouts), fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    if (showSearch) {
                        IconButton(onClick = { showSearch = false; searchText = ""; viewModel.searchQuery = "" }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                },
                actions = {
                    if (!showSearch) {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Search, contentDescription = "Buscar")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("workout_editor/new") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Add, contentDescription = stringResource(R.string.new_workout))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (plans.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontal,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(FitnessCenter, contentDescription = null, tint = MaterialTheme.colorScheme.primary, size = 64.dp)
                        Text(
                            "Nenhum treino criado",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Toque no + para criar seu primeiro treino",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(plans) { plan ->
                        WorkoutPlanCard(
                            plan = plan,
                            onClick = { navController.navigate("workout_detail/${plan.id}") },
                            onEdit = { navController.navigate("workout_editor/${plan.id}") },
                            onDuplicate = { viewModel.duplicatePlan(plan.id, "${plan.name} (Cópia)") },
                            onDelete = {
                                viewModel.deletePlan(plan.id)
                                snackbarHostState.showSnackbar("Treino excluído")
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
fun WorkoutPlanCard(
    plan: WorkoutPlan,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val goalLabel = WorkoutGoal.values().firstOrNull { it.value == plan.goal }?.labelPt ?: plan.goal
    val colors = MaterialTheme.colorScheme

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(0.dp)
            .wrapContentSize(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
                Text(
                    plan.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.TextOverflow.Ellipsis
                )
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onEdit) {
                        Icon(Edit, contentDescription = "Editar", tint = colors.primary)
                    }
                    IconButton(onClick = onDuplicate) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicar", tint = colors.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Delete, contentDescription = "Excluir", tint = colors.error)
                    }
                }
            )

            // Meta
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (plan.description.isNotBlank()) {
                    Text(
                        plan.description,
                        fontSize = 14.sp,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.TextOverflow.Ellipsis
                    )
                }
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        goalLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.primary
                    )
                }
            }

            // Stats row
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatChip(icon = FitnessCenter, label = "${plan.daysPerWeek} dias/semana", colors = colors)
                if (plan.isActive) {
                    androidx.compose.material3.Badge(
                        badgeContent = { Text("ATIVO", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = androidx.material3.material3.BadgeDefaults.badgeColors(
                            containerColor = colors.primaryContainer,
                            contentColor = colors.onPrimaryContainer
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun StatChip(
    icon: androidx.compose.material.icons.filled.Icon,
    label: String,
    colors: androidx.compose.material3.ColorScheme
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant, size = 16.dp)
        Text(label, fontSize = 12.sp, color = colors.onSurfaceVariant)
    }
}