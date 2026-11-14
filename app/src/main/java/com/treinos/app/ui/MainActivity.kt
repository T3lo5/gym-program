package com.treinos.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.treinos.app.TreinosApplication
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: AppViewModel

    /**
     * H3-58: pedido de `POST_NOTIFICATIONS` (API 33+). O resultado é ignorado:
     * sem permissão, a notificação do descanso simplesmente não aparece.
     */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** H3-58: último contador de pedido já tratado; `null` = primeira coleta. */
    private var lastSeenPermissionRequest: Int? = null

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
                restNotifier = container.restNotifier,
            )
        )[AppViewModel::class.java]

        // H3-58: ação "Pular descanso" da notificação reusa o fluxo do botão da tela.
        if (intent.getBooleanExtra(EXTRA_SKIP_REST, false)) {
            viewModel.skipRest()
        }

        setContent {
            TreinosTheme {
                // H3-35: atalho "Retomar treino" abre direto a tela de treino.
                if (intent.getBooleanExtra("extra_resume_session", false)) {
                    viewModel.resumeWorkout()
                }
                TreinosApp(viewModel)
            }
        }

        // H3-58: o VM sinaliza quando um descanso começa; a Activity executa o
        // pedido de permissão (contexto de treino, não no startup).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.notificationPermissionRequest.collect { count ->
                    val last = lastSeenPermissionRequest
                    if (last == null) {
                        lastSeenPermissionRequest = count
                    } else if (count > last) {
                        lastSeenPermissionRequest = count
                        requestNotificationPermissionIfNeeded()
                    }
                }
            }
        }
    }

    /** H3-58: ação "Pular descanso" com a Activity já viva (CLEAR_TOP | SINGLE_TOP). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_SKIP_REST, false)) {
            viewModel.skipRest()
        }
    }

    /** H3-40: poll sob demanda ao voltar ao foreground — consulta o job se houver envelope. */
    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
    }

    /** H3-58: ao sair do primeiro plano, o VM posta a notificação do descanso. */
    override fun onStop() {
        super.onStop()
        viewModel.onAppBackgrounded()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        /** H3-58: extra do PendingIntent "Pular descanso" da notificação. */
        const val EXTRA_SKIP_REST = "extra_skip_rest"
    }
}
