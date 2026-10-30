package com.treinos.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.treinos.app.R
import com.treinos.app.di.DatabaseModule
import com.treinos.app.ui.navigation.AppNavHost
import com.treinos.shared.data.ExerciseRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var exerciseRepository: ExerciseRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            exerciseRepository.initializeIfNeeded()
        }
        setContent { TreinosApp() }
    }
}

@Composable
fun TreinosApp() {
    val navController = rememberNavController()
    var selectedTab by remember { mutableStateOf(0) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Treinos", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer)
            )
        },
        bottomBar = {
            NavigationBar {
                val items = listOf(
                    NavigationItem(0, "Treinos", androidx.material3.icons.filled.FitnessCenter),
                    NavigationItem(1, "Histórico", androidx.material3.icons.filled.History),
                    NavigationItem(2, "Exercícios", androidx.material3.icons.filled.FitnessCenter),
                    NavigationItem(3, "Config.", androidx.material3.icons.filled.Settings)
                )
                items.forEach { item ->
                    NavigationBarItem(
                        selected = selectedTab == item.index,
                        onClick = { selectedTab = item.index; navigateToTab(navController, item.index) },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { innerPadding ->
        AppNavHost(navController)
    }
}

data class NavigationItem(val index: Int, val label: String, val icon: androidx.compose.material.icons.filled.Icon)

fun navigateToTab(navController: androidx.navigation.NavController, tab: Int) {
    val route = when (tab) {
        0 -> "workouts"
        1 -> "history"
        2 -> "exercise_list"
        3 -> "settings"
        else -> "workouts"
    }
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        avoidMultipleCopies = true
        launchSingleTop = true
    }
}