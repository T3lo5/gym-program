package com.treinos.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LinearProgressIndicator
import com.treinos.app.data.backup.BackupPreview
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
    onBack: () -> Unit,
    onSaveUrl: (String) -> Unit,
    onTest: () -> Unit,
    onSync: () -> Unit,
    onDownload: () -> Unit,
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
) {
    var url by remember(backendUrl) { mutableStateOf(backendUrl) }

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
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = { onSaveUrl(url) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Salvar")
                        }
                        Button(
                            onClick = {
                                onSaveUrl(url)
                                onTest()
                            },
                            enabled = !isTesting,
                            modifier = Modifier.weight(1f),
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
                        text = "Sincronização manual (apenas envio)",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Os treinos e as séries ficam somente no SQLite do aparelho. " +
                            "Ao tocar no botão abaixo, o app envia um snapshot completo " +
                            "(planos, treinos e séries) para o banco do backend. " +
                            "Não é feito download de dados do servidor.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "Último envio: ${formatIsoDateTime(lastSyncAt) ?: "nunca"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = onSync,
                        enabled = !isSyncing,
                        modifier = Modifier.fillMaxWidth(),
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
                        onClick = onDownload,
                        enabled = !isDownloading,
                        modifier = Modifier.fillMaxWidth(),
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
                            ) {
                                Text(if (isUpdatingCatalog) "Atualizando..." else "Atualizar catálogo")
                            }
                            TextButton(onClick = onClearCatalogMessage) {
                                Text("Limpar")
                            }
                        }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {

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
                        text = "Exporte todos os seus planos, treinos e séries para um arquivo JSON " +
                            "no aparelho. Restaure depois, mesmo após desinstalar o app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onExportBackup,
                            enabled = backupImportState !is BackupImportState.Importing,
                            modifier = Modifier.fillMaxWidth(),
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
                            modifier = Modifier.fillMaxWidth(),
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
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Substituir tudo")
                                }
                                OutlinedButton(
                                    onClick = onCancelImport,
                                    modifier = Modifier.fillMaxWidth(),
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
                                modifier = Modifier.fillMaxWidth(),
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

}
