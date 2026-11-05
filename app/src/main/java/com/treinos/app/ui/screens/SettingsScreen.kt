package com.treinos.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LinearProgressIndicator
import com.treinos.app.data.backup.BackupPreview
import com.treinos.app.data.discovery.DiscoveredBackend
import com.treinos.app.ui.BackupImportState
import com.treinos.app.ui.components.ScreenTopBar
import com.treinos.app.ui.formatIsoDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    backendUrl: String,
    isTesting: Boolean,
    isSyncing: Boolean,
    isDownloading: Boolean,
    connectionMessage: String?,
    syncMessage: String?,
    downloadMessage: String?,
    lastSyncAt: String?,
    deviceId: String,
    backupImportState: BackupImportState,
    backupMessage: String?,
    isRestoring: Boolean,
    restoreDeviceId: String,
    restoreMessage: String?,
    onBack: () -> Unit,
    onSaveUrl: (String) -> Unit,
    onTest: () -> Unit,
    onSync: () -> Unit,
    onDownload: () -> Unit,
    onRestoreDeviceIdChanged: (String) -> Unit,
    onRestoreByDeviceId: (String) -> Unit,
    onExportBackup: () -> Unit,
    onImportFileSelected: () -> Unit,
    onPrepareImport: (String) -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onClearBackupMessage: () -> Unit,
    isUpdatingCatalog: Boolean = false,
    catalogMessage: String? = null,
    onUpdateCatalog: () -> Unit = {},
    onClearCatalogMessage: () -> Unit = {},
    discoveredBackends: List<DiscoveredBackend> = emptyList(),
    isDiscovering: Boolean = false,
    isConnectingBackend: Boolean = false,
    discoveryMessage: String? = null,
    onSearchBackends: () -> Unit = {},
    onStopSearch: () -> Unit = {},
    onConnectBackend: (DiscoveredBackend) -> Unit = {},
    onClearDiscoveryMessage: () -> Unit = {},
) {
    var url by remember(backendUrl) { mutableStateOf(backendUrl) }
    var showDownloadConfirm by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }

    if (showDownloadConfirm) {
        AlertDialog(
            onDismissRequest = { showDownloadConfirm = false },
            title = { Text("Substituir os dados locais?") },
            text = {
                Text(
                    "Restaurar do backend apaga os planos, treinos e séries deste " +
                        "aparelho e grava no lugar o snapshot deste deviceId. " +
                        "Exporte um backup JSON antes, se quiser voltar atrás.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDownloadConfirm = false
                        onDownload()
                    },
                ) {
                    Text("Restaurar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadConfirm = false }) {
                    Text("Cancelar")
                }
            },
        )
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("Assumir este aparelho?") },
            text = {
                Text(
                    "Os dados locais serão substituídos pelo snapshot de " +
                        "$restoreDeviceId e este app passará a usar esse deviceId. " +
                        "Se o deviceId estiver errado, nada muda.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirm = false
                        onRestoreByDeviceId(restoreDeviceId)
                    },
                ) {
                    Text("Restaurar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
                    Text("Cancelar")
                }
            },
        )
    }

    Scaffold(
        topBar = { ScreenTopBar(title = "Ajustes", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Backend",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Endereço usado para gerar treinos e sincronizar. " +
                            "No emulador, use http://10.0.2.2:3000. No celular, use o IP do " +
                            "computador na rede local (ex.: http://192.168.0.10:3000).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("URL do backend") },
                        supportingText = { Text("Ex.: http://192.168.0.10:3000") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onSaveUrl(url) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = { onSaveUrl(url) },
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
                        ) {
                            Text("Salvar")
                        }
                        Button(
                            onClick = {
                                onSaveUrl(url)
                                onTest()
                            },
                            enabled = !isTesting,
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text("Testar conexão")
                            }
                        }
                    }
                    connectionMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = "ID deste aparelho: $deviceId",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Backend na rede local",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "O app procura o serviço _treinos._tcp na mesma Wi-Fi e conecta " +
                            "com um toque. No Docker (rede bridge) o mDNS não atravessa: " +
                            "nesse caso use o endereço manual acima.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            if (isDiscovering) {
                                onStopSearch()
                            } else {
                                hasSearched = true
                                onClearDiscoveryMessage()
                                onSearchBackends()
                            }
                        },
                        enabled = !isConnectingBackend,
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (isDiscovering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text("Procurando… (toque para parar)")
                            } else {
                                Text("Buscar backends")
                            }
                        }
                    }

                    if (discoveredBackends.isEmpty() && hasSearched && !isDiscovering) {
                        Text(
                            text = "Nenhum backend encontrado. Ligue o servidor no computador " +
                                "e confirme que o celular está na mesma Wi-Fi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    discoveredBackends.forEach { backend ->
                        val isConnected = backendUrl == backend.url
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(backend.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = backend.endpoint,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (!backend.isVersionKnown) {
                                    Text(
                                        text = "versão não informada",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                            }
                            if (isConnected) {
                                Text(
                                    text = "Conectado",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Button(
                                    onClick = { onConnectBackend(backend) },
                                    enabled = !isConnectingBackend && !isDiscovering,
                                ) {
                                    if (isConnectingBackend) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    } else {
                                        Text("Conectar")
                                    }
                                }
                            }
                        }
                    }

                    discoveryMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Sincronização com o backend",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Os treinos e as séries ficam no SQLite do aparelho. " +
                            "Enviar ao backend grava um snapshot completo (planos, treinos e séries) " +
                            "no banco do servidor. Restaurar do backend baixa o snapshot deste " +
                            "deviceId e substitui os dados locais — exporte um backup JSON antes.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "Último envio: ${formatIsoDateTime(lastSyncAt) ?: "nunca"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = onSync,
                        enabled = !isSyncing,
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Text("Enviando…")
                            } else {
                                Text("Enviar ao backend")
                            }
                        }
                    }
                    syncMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Button(
                        onClick = { showDownloadConfirm = true },
                        enabled = !isDownloading,
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Text("Baixando…")
                            } else {
                                Text("Restaurar do backend")
                            }
                        }
                    }
                    downloadMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Restaurar de outro aparelho",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Informe o deviceId que aparece nos Ajustes do outro celular. " +
                            "O app baixa o snapshot daquele aparelho, substitui os dados locais " +
                            "e passa a usar esse deviceId.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    OutlinedTextField(
                        value = restoreDeviceId,
                        onValueChange = onRestoreDeviceIdChanged,
                        label = { Text("deviceId do outro aparelho") },
                        supportingText = { Text(if (restoreDeviceId.isBlank()) "Obrigatório para restaurar" else "Pronto para restaurar") },
                        isError = restoreDeviceId.isBlank() && restoreMessage != null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (restoreDeviceId.isNotBlank()) showRestoreConfirm = true
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { showRestoreConfirm = true },
                        enabled = restoreDeviceId.isNotBlank() && !isRestoring,
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (isRestoring) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Text("Restaurando…")
                            } else {
                                Text("Restaurar")
                            }
                        }
                    }
                    restoreMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Catálogo de exercícios",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Imagens e instruções via catálogo do backend (free-exercise-db).",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (isUpdatingCatalog) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        catalogMessage?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onUpdateCatalog,
                                enabled = !isUpdatingCatalog,
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Text(if (isUpdatingCatalog) "Atualizando..." else "Atualizar catálogo")
                            }
                            TextButton(onClick = onClearCatalogMessage, modifier = Modifier.minimumInteractiveComponentSize()) {
                                Text("Limpar")
                            }
                        }
                    }
                }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Backup e restauração (JSON)",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text(
                        text = "Exporte seus planos, treinos, séries e o perfil (peso e altura) para um " +
                            "arquivo JSON. Restaure depois, mesmo após desinstalar o app. Quem tiver o " +
                            "arquivo lê esses dados — inclua peso e altura.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onExportBackup,
                            enabled = backupImportState !is BackupImportState.Importing,
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
                        ) {
                            if (backupImportState is BackupImportState.Importing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text("Exportar backup")
                            }
                        }
                        OutlinedButton(
                            onClick = onImportFileSelected,
                            enabled = backupImportState !is BackupImportState.Importing,
                            modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
                        ) {
                            if (backupImportState is BackupImportState.Importing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text("Importar backup")
                            }
                        }
                    }

                    when (val state = backupImportState) {
                        is BackupImportState.Ready -> {
                            Text(
                                text = "Pré-visualização: ${state.preview.plans} plano(s), " +
                                    "${state.preview.days} dia(s), " +
                                    "${state.preview.planExercises} exercício(s) de plano, " +
                                    "${state.preview.sessions} sessão(ões), " +
                                    "${state.preview.sessionExercises} exercício(s) de sessão, " +
                                    "${state.preview.setLogs} série(s).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = onConfirmImport,
                                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
                                ) {
                                    Text("Substituir tudo")
                                }
                                OutlinedButton(
                                    onClick = onCancelImport,
                                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
                                ) {
                                    Text("Cancelar")
                                }
                            }
                        }
                        is BackupImportState.Error -> {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            OutlinedButton(
                                onClick = onClearBackupMessage,
                                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                            ) {
                                Text("Dispensar")
                            }
                        }
                        else -> { }
                    }

                    backupMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
