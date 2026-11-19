package com.treinos.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import com.treinos.app.data.health.HEALTH_PERMISSIONS
import com.treinos.app.data.health.HealthConnectAvailability
import androidx.health.connect.client.PermissionController
import com.treinos.app.ui.AppViewModel.PermissionRequest
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

    /**
     * H4-9: contract de permissões do Health Connect. O resultado é ignorado —
     * `refreshHealthConnect()` recolhe o estado real (o usuário pode conceder
     * só parte das permissões, regra 3).
     */
    private val healthPermissionLauncher =
        registerForActivityResult(
            PermissionController.createRequestPermissionResultContract(),
        ) {
            viewModel.refreshHealthConnect()
        }

    /** H4-9: último contador de pedido de saúde já tratado; `null` = primeira coleta. */
    private var lastSeenHealthPermissionRequest: Int? = null

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
                healthGateway = container.healthGateway,
            )
        )[AppViewModel::class.java]

        // H3-58: ação "Pular descanso" da notificação reusa o fluxo do botão da tela.
        if (intent.getBooleanExtra(EXTRA_SKIP_REST, false)) {
            viewModel.skipRest()
        }

        // H4-14: widget "Iniciar treino de hoje" inicia a sessão do dia direto.
        if (intent.getBooleanExtra(EXTRA_START_TODAY, false)) {
            viewModel.startTodayWorkout()
        }

        setContent {
            TreinosTheme(
                dynamicColorEnabled = viewModel.useDynamicColor,
            ) {
                // H3-35: atalho "Retomar treino" abre direto a tela de treino.
                // H4-14: o widget "Retomar treino" reusa o mesmo extra.
                if (intent.getBooleanExtra(EXTRA_RESUME_SESSION, false)) {
                    viewModel.resumeWorkout()
                }
                TreinosApp(viewModel)
            }
        }

        // Estado inicial das permissões para o card da Home/seção de Ajustes.
        viewModel.refreshPermissions()

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

        // H4-9: o VM sinaliza quando o usuário toca "Conectar"; a Activity
        // executa o contract de permissões do Health Connect.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.healthPermissionRequest.collect { count ->
                    val last = lastSeenHealthPermissionRequest
                    if (last == null) {
                        lastSeenHealthPermissionRequest = count
                    } else if (count > last) {
                        lastSeenHealthPermissionRequest = count
                        launchHealthPermissionRequestIfNeeded()
                    }
                }
            }
        }

        // Pedidos explícitos do card "Permissões" (Home) e da seção em Ajustes:
        // diálogo do sistema para notificações; tela de apps desconhecidos
        // para o instalador de update (não é diálogo runtime).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.permissionRequests.collect { request ->
                    when (request) {
                        PermissionRequest.Notifications -> requestNotificationPermissionIfNeeded()
                        PermissionRequest.InstallPackages -> openUnknownSourcesSettings()
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
        // H4-14: widget "Iniciar treino de hoje" com a Activity já viva.
        if (intent.getBooleanExtra(EXTRA_START_TODAY, false)) {
            viewModel.startTodayWorkout()
        }
    }

    /** H3-40: poll sob demanda ao voltar ao foreground — consulta o job se houver envelope. */
    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
        // Voltou do primeiro plano (ex.: tela de Configurações depois de
        // conceder algo): recolhe o estado real das permissões.
        viewModel.refreshPermissions()
        viewModel.refreshHealthConnect()
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

    /**
     * "Instalar atualizações" não tem diálogo runtime: o Android só oferece a
     * tela de apps desconhecidos. Abre direto na página do app (sem o usuário
     * adivinhar onde a permissão mora); sem painel específico, cai na página
     * do app em Configurações.
     */
    private fun openUnknownSourcesSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (packageManager.canRequestPackageInstalls()) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:$packageName"),
        )
        runCatching {
            startActivity(intent)
        }.onFailure {
            // Fabricante sem a tela específica: página do app em Configurações.
            runCatching {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName"),
                    )
                )
            }
        }
    }

    /**
     * H4-9: só pede as permissões de saúde com o Health Connect disponível —
     * fora disso o contract não tem para onde abrir (regra 1).
     */
    private fun launchHealthPermissionRequestIfNeeded() {
        if (viewModel.healthConnectState.value.availability != HealthConnectAvailability.AVAILABLE) {
            return
        }
        healthPermissionLauncher.launch(HEALTH_PERMISSIONS)
    }

    companion object {
        /** H3-58: extra do PendingIntent "Pular descanso" da notificação. */
        const val EXTRA_SKIP_REST = "extra_skip_rest"

        /** H3-35/H4-14: extra do atalho e do widget "Retomar treino". */
        const val EXTRA_RESUME_SESSION = "extra_resume_session"

        /** H4-14: extra do widget "Iniciar treino de hoje". */
        const val EXTRA_START_TODAY = "extra_start_today"
    }
}
