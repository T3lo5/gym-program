package com.treinos.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.treinos.app.TreinosApplication

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as TreinosApplication).container

        setContent {
            TreinosTheme {
                val viewModel: AppViewModel = viewModel(
                    factory = AppViewModel.factory(
                        repository = container.repository,
                        preferences = container.preferences,
                    )
                )
                TreinosApp(viewModel)
            }
        }
    }
}
