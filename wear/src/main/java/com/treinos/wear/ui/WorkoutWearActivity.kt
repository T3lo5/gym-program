package com.treinos.wear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.treinos.wear.R

class WorkoutWearActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WorkoutWearScreen() }
    }
}

@Composable
fun WorkoutWearScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = androidx.compose.material3.MaterialTheme.colorScheme.background
    ) {
        Text(text = "Treinos Watch", fontSize = 24.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
    }
}

import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp