package com.treinos.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.treinos.app.TreinosApplication

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Aplica o idioma por app persistido (per-app locale) antes de criar a
        // activity, para que ela já nasça no idioma escolhido pelo usuário.
        AppCompatDelegate.setApplicationLocales(AppCompatDelegate.getApplicationLocales())
        super.onCreate(savedInstanceState)
        val container = (application as TreinosApplication).container

        setContent {
            TreinosTheme {
                val viewModel: AppViewModel = viewModel(
                    factory = AppViewModel.factory(
                        context = this@MainActivity,
                        repository = container.repository,
                        preferences = container.preferences,
                        exerciseMediaRepository = container.exerciseMediaRepository,
                        discoveryManager = container.discoveryManager,
                        wearSyncRepository = container.wearSyncRepository,
                        restFeedback = container.restFeedback,
                    )
                )
                // H3-35: atalho "Retomar treino" abre direto a tela de treino.
                if (intent.getBooleanExtra("extra_resume_session", false)) {
                    viewModel.resumeWorkout()
                }
                TreinosApp(viewModel)
            }
        }
    }
}
