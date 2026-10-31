package com.treinos.app.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.treinos.app.ui.exercisepicker.ExercisePickerScreen
import com.treinos.app.ui.exercisepicker.ExercisePickerViewModel
import com.treinos.app.ui.history.HistoryScreen
import com.treinos.app.ui.settings.BackendConfigScreen
import com.treinos.app.ui.settings.SettingsScreen
import com.treinos.app.ui.settings.SettingsViewModel
import com.treinos.app.ui.workouteditor.WorkoutEditorScreen
import com.treinos.app.ui.workouteditor.WorkoutEditorViewModel
import com.treinos.app.ui.workoutexecution.WorkoutExecutionScreen
import com.treinos.app.ui.workoutexecution.WorkoutExecutionViewModel
import com.treinos.app.ui.workoutlist.WorkoutListScreen
import com.treinos.app.ui.workoutlist.WorkoutListViewModel

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController, startDestination = "workouts") {
        composable("workouts") {
            WorkoutListScreen(
                navController = navController,
                viewModel = hiltViewModel<WorkoutListViewModel>()
            )
        }
        composable(
            route = "workout_detail/{planId}",
            arguments = listOf(navArgument("planId") { type = NavType.LongType })
        ) { backStackEntry ->
            val planId = backStackEntry.arguments?.getLong("planId") ?: 0L
            WorkoutDetailScreen(planId = planId, navController = navController)
        }
        composable(
            route = "workout_editor/{planId}",
            arguments = listOf(navArgument("planId") { type = NavType.StringType })
        ) { backStackEntry ->
            val planId = backStackEntry.arguments?.getString("planId") ?: "new"
            WorkoutEditorScreen(
                navController = navController,
                planId = planId,
                viewModel = hiltViewModel<WorkoutEditorViewModel>()
            )
        }
        composable(
            route = "exercise_picker/{dayId}",
            arguments = listOf(navArgument("dayId") { type = NavType.LongType })
        ) { backStackEntry ->
            val dayId = backStackEntry.arguments?.getLong("dayId") ?: 0L
            ExercisePickerScreen(
                navController = navController,
                dayId = dayId.toString(),
                viewModel = hiltViewModel<ExercisePickerViewModel>()
            )
        }
        composable(
            route = "workout_execution/{planId}/{dayId}",
            arguments = listOf(
                navArgument("planId") { type = NavType.LongType },
                navArgument("dayId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val planId = backStackEntry.arguments?.getLong("planId") ?: 0L
            val dayId = backStackEntry.arguments?.getLong("dayId") ?: 0L
            WorkoutExecutionScreen(
                navController = navController,
                planId = planId,
                dayId = dayId,
                viewModel = hiltViewModel<WorkoutExecutionViewModel>()
            )
        }
        composable("exercise_list") { ExerciseListScreen() }
        composable("history") { HistoryScreen(navController = navController) }
        composable("settings") { SettingsScreen(navController = navController) }
        composable("backend_config") {
            BackendConfigScreen(
                navController = navController,
                viewModel = hiltViewModel<SettingsViewModel>()
            )
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun WorkoutDetailScreen(planId: Long, navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalhes do Treino") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                "Detalhes do Treino #$planId",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Button(onClick = { navController.navigate("workout_execution/$planId/0") }) {
                Text("Iniciar Treino")
            }
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun ExerciseListScreen() {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Exercícios") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                "Catálogo de Exercícios",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
