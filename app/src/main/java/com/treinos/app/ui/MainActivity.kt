package com.treinos.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.treinos.app.TreinosApplication

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as TreinosApplication).container
        viewModel = ViewModelProvider(
            this,
            AppViewModel.factory(
                context = this@MainActivity,
                repository = container.repository,
                preferences = container.preferences,
                exerciseMediaRepository = container.exerciseMediaRepository,
                discoveryManager = container.discoveryManager,
                wearSyncRepository = container.wearSyncRepository,
                restFeedback = container.restFeedback,
            )
        )[AppViewModel::class.java]

        setContent {
            TreinosTheme {
                // H3-35: atalho "Retomar treino" abre direto a tela de treino.
                if (intent.getBooleanExtra("extra_resume_session", false)) {
                    viewModel.resumeWorkout()
                }
                TreinosApp(viewModel)
            }
        }
    }

    /** H3-40: poll sob demanda ao voltar ao foreground — consulta o job se houver envelope. */
    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
    }
}
