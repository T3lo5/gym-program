package com.treinos.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
fun AppNavHost(navController: NavController = rememberNavController()) {
    NavHost(navController, startDestination = "workouts") {
        composable("workouts") {
            WorkoutListScreen(
                navController = navController,
                viewModel = hiltViewModel<WorkoutListViewModel>()
            )
        }
        composable(
            route = "workout_detail/{planId}",
            arguments = listOf(androidx.navigation.navArgument("planId") { type = androidx.navigation.NavType.LongType })
        ) { backStackEntry ->
            val planId = backStackEntry.getInt("planId")!!
            WorkoutDetailScreen(planId = planId, navController = navController)
        }
        composable(
            route = "workout_editor/{planId}",
            arguments = listOf(androidx.navigation.navArgument("planId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val planId = backStackEntry.getString("planId") ?: "new"
            WorkoutEditorScreen(
                navController = navController,
                planId = planId,
                viewModel = hiltViewModel<WorkoutEditorViewModel>()
            )
        }
        composable(
            route = "exercise_picker/{dayId}",
            arguments = listOf(androidx.navigation.navArgument("dayId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val dayId = backStackEntry.getString("dayId")!!
            ExercisePickerScreen(
                navController = navController,
                dayId = dayId,
                viewModel = hiltViewModel<ExercisePickerViewModel>()
            )
        }
        composable(
            route = "workout_execution/{planId}/{dayId}",
            arguments = listOf(
                androidx.navigation.navArgument("planId") { type = androidx.navigation.NavType.IntType },
                androidx.navigation.navArgument("dayId") { type = androidx.navigation.NavType.IntType }
            )
        ) { backStackEntry ->
            val planId = backStackEntry.getInt("planId")!!
            val dayId = backStackEntry.getInt("dayId")!!
            WorkoutExecutionScreen(
                navController = navController,
                planId = planId,
                dayId = dayId,
                viewModel = hiltViewModel<WorkoutExecutionViewModel>()
            )
        }
        composable("exercise_list") { ExerciseListScreen() }
        composable("history") { HistoryScreen() }
        composable("settings") { SettingsScreen() }
        composable("backend_config") { BackendConfigScreen(navController = navController, viewModel = hiltViewModel<SettingsViewModel>()) }
    }
}

@Composable
fun WorkoutDetailScreen(planId: Int, navController: NavController) {
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { androidx.compose.material3.Text("Detalhes do Treino") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = { navController.popBackStack() }) {
                        androidx.compose.material3.Icon(androidx.material3.icons.filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { innerPadding ->
        androidx.compose.foundation.layout.Column(
            modifier = androidx.compose.foundation.layout.Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            androidx.compose.material3.Text("Detalhes do Treino #$planId", fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            androidx.compose.material3.Button(onClick = { navController.navigate("workout_execution/$planId/0") }) {
                androidx.compose.material3.Text("Iniciar Treino")
            }
        }
    }
}

@Composable
fun ExerciseListScreen() {
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(title = { androidx.compose.material3.Text("Exercícios") })
        }
    ) { innerPadding ->
        androidx.compose.foundation.layout.Column(
            modifier = androidx.compose.foundation.layout.Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            androidx.compose.material3.Text("Catálogo de Exercícios", fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
    }
}

@Composable
fun HistoryScreen() {
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(title = { androidx.compose.material3.Text("Histórico") })
        }
    ) { innerPadding ->
        androidx.compose.foundation.layout.Column(
            modifier = androidx.compose.foundation.layout.Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            androidx.compose.material3.Text("Histórico de Treinos", fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
    }
}

@Composable
fun SettingsScreen() {
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(title = { androidx.compose.material3.Text("Configurações") })
        }
    ) { innerPadding ->
        androidx.compose.foundation.layout.Column(
            modifier = androidx.compose.foundation.layout.Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            androidx.compose.material3.Text("Configurações", fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
    }
}