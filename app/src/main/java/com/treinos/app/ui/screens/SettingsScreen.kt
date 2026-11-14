package com.treinos.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LinearProgressIndicator
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.treinos.app.R
import com.treinos.app.data.backup.BackupPreview
import com.treinos.app.data.discovery.DiscoveredBackend
import com.treinos.app.ui.BackupImportState
import com.treinos.app.ui.UiError
import com.treinos.app.ui.components.BusyButton
import com.treinos.app.ui.components.ConfirmationDialog
import com.treinos.app.ui.components.ErrorState
import com.treinos.app.ui.components.ScreenTopBar
import com.treinos.app.ui.components.StatusText
import com.treinos.app.ui.components.statusIsSuccess
import com.treinos.app.ui.formatIsoDateTime
import com.treinos.app.updater.UpdateState

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
    // H3-29: erro persistente com "Tentar de novo" para envio e download.
    syncError: UiError? = null,
    onRetrySync: () -> Unit = {},
    downloadError: UiError? = null,
    onRetryDownload: () -> Unit = {},
    // H3-51: atualização in-app via GitHub Releases (checagem sob demanda).
    updateState: UpdateState = UpdateState.Idle,
    onCheckUpdateOnOpen: () -> Unit = {},
    onCheckForUpdate: () -> Unit = {},
    onDownloadUpdate: () -> Unit = {},
    onClearUpdateMessage: () -> Unit = {},
) {
    var url by remember(backendUrl) { mutableStateOf(backendUrl) }
    var showDownloadConfirm by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showReplaceConfirm by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    // H3-35: seções colapsáveis — Sincronização inicia colapsada.
    var treinoExpanded by remember { mutableStateOf(true) }
    var syncExpanded by remember { mutableStateOf(false) }
    var backupExpanded by remember { mutableStateOf(true) }
    // H3-51: seção de atualização inicia colapsada; expande sozinha quando a
    // checagem automática encontra uma versão nova.
    var updateExpanded by remember { mutableStateOf(false) }

    // H3-51: checagem sob demanda — uma vez por processo ao abrir Ajustes;
    // nunca polling. O botão manual repete a checagem quando o usuário quiser.
    LaunchedEffect(Unit) { onCheckUpdateOnOpen() }
    LaunchedEffect(updateState) {
        if (updateState is UpdateState.Available) updateExpanded = true
    }

    if (showDownloadConfirm) {
        ConfirmationDialog(
            visible = true,
            onDismiss = { showDownloadConfirm = false },
            onConfirm = onDownload,
            title = stringResource(R.string.settings_dialog_replace_title),
            message = stringResource(R.string.settings_dialog_replace_body),
            confirmLabel = stringResource(R.string.settings_btn_restore_action),
            isDestructive = true,
        )
    }

    if (showRestoreConfirm) {
        ConfirmationDialog(
            visible = true,
            onDismiss = { showRestoreConfirm = false },
            onConfirm = { onRestoreByDeviceId(restoreDeviceId) },
            title = stringResource(R.string.settings_dialog_restore_title),
            message = stringResource(R.string.settings_dialog_restore_body, restoreDeviceId),
            confirmLabel = stringResource(R.string.settings_btn_restore_action),
            isDestructive = true,
        )
    }

    // H3-35: confirmação destrutiva antes de qualquer escrita no import.
    if (showReplaceConfirm) {
        ConfirmationDialog(
            visible = true,
            onDismiss = { showReplaceConfirm = false },
            onConfirm = onConfirmImport,
            title = stringResource(R.string.settings_dialog_replace_all_title),
            message = stringResource(R.string.settings_dialog_replace_all_body),
            confirmLabel = stringResource(R.string.settings_btn_replace_all),
            isDestructive = true,
        )
    }

    Scaffold(
        topBar = { ScreenTopBar(title = stringResource(R.string.screen_settings), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // H3-35: seção Treino (catálogo de exercícios).
            CollapsibleSection(
                title = stringResource(R.string.settings_section_training),
                expanded = treinoExpanded,
                onToggle = { treinoExpanded = it },
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_catalog),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.settings_catalog_desc),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (isUpdatingCatalog) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        catalogMessage?.let {
                            StatusText(text = it, isSuccess = statusIsSuccess(it), announce = true)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onUpdateCatalog,
                                enabled = !isUpdatingCatalog,
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Text(
                                    if (isUpdatingCatalog) stringResource(R.string.settings_catalog_updating)
                                    else stringResource(R.string.settings_btn_update_catalog)
                                )
                            }
                            TextButton(onClick = onClearCatalogMessage, modifier = Modifier.minimumInteractiveComponentSize()) {
                                Text(stringResource(R.string.settings_btn_clear))
                            }
                        }
                    }
                }
            }

            // H3-35: seção Sincronização — colapsada por padrão.
            CollapsibleSection(
                title = stringResource(R.string.settings_section_sync),
                expanded = syncExpanded,
                onToggle = { syncExpanded = it },
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_server_desc),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.settings_server_url_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text(stringResource(R.string.settings_label_server_url)) },
                            supportingText = { Text(stringResource(R.string.settings_server_url_ex)) },
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
                                Text(stringResource(R.string.settings_btn_save))
                            }
                            BusyButton(
                                busy = isTesting,
                                idleText = stringResource(R.string.settings_btn_test),
                                busyText = stringResource(R.string.settings_btn_testing),
                                onClick = {
                                    onSaveUrl(url)
                                    onTest()
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        connectionMessage?.let {
                            StatusText(text = it, isSuccess = statusIsSuccess(it), announce = true)
                        }
                        Text(
                            text = stringResource(R.string.settings_device_id, deviceId),
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
                            text = stringResource(R.string.settings_section_local),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.settings_local_desc),
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
                                    Text(stringResource(R.string.settings_btn_searching))
                                } else {
                                    Text(stringResource(R.string.settings_btn_search))
                                }
                            }
                        }

                        if (discoveredBackends.isEmpty() && hasSearched && !isDiscovering) {
                            Text(
                                text = stringResource(R.string.settings_no_server),
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
                                            text = stringResource(R.string.settings_server_version_unknown),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                        )
                                    }
                                }
                                if (isConnected) {
                                    Text(
                                            text = stringResource(R.string.settings_server_connected),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    BusyButton(
                                        busy = isConnectingBackend,
                                        idleText = stringResource(R.string.settings_btn_connect),
                                        busyText = stringResource(R.string.settings_btn_connecting),
                                        onClick = { onConnectBackend(backend) },
                                        enabled = !isDiscovering,
                                    )
                                }
                            }
                        }

                        discoveryMessage?.let {
                            StatusText(text = it, isSuccess = statusIsSuccess(it), announce = true)
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
                            text = stringResource(R.string.settings_btn_sync),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.settings_sync_body),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = stringResource(
                                R.string.settings_last_upload,
                                formatIsoDateTime(lastSyncAt) ?: stringResource(R.string.settings_upload_never),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        BusyButton(
                            busy = isSyncing,
                            idleText = stringResource(R.string.settings_btn_send),
                            busyText = stringResource(R.string.settings_btn_sending),
                            onClick = onSync,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        syncMessage?.let {
                            StatusText(text = it, isSuccess = statusIsSuccess(it), announce = true)
                        }
                        // H3-29: erro persiste com "Tentar de novo" — não é snackbar.
                        syncError?.let { uiError ->
                            ErrorState(
                                title = uiError.title,
                                message = uiError.message,
                                onRetry = if (uiError.retryLabel != null) onRetrySync else null,
                            )
                        }
                        BusyButton(
                            busy = isDownloading,
                            idleText = stringResource(R.string.settings_btn_restore),
                            busyText = stringResource(R.string.settings_btn_restoring),
                            onClick = { showDownloadConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        downloadMessage?.let {
                            StatusText(text = it, isSuccess = statusIsSuccess(it), announce = true)
                        }
                        // H3-29: erro persiste com "Tentar de novo" — não é snackbar.
                        downloadError?.let { uiError ->
                            ErrorState(
                                title = uiError.title,
                                message = uiError.message,
                                onRetry = if (uiError.retryLabel != null) onRetryDownload else null,
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
                            text = stringResource(R.string.settings_section_restore_device),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.settings_restore_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        OutlinedTextField(
                            value = restoreDeviceId,
                            onValueChange = onRestoreDeviceIdChanged,
                            label = { Text(stringResource(R.string.settings_label_other_code)) },
                            supportingText = {
                                Text(
                                    if (restoreDeviceId.isBlank()) {
                                        stringResource(R.string.settings_support_required)
                                    } else {
                                        stringResource(R.string.settings_support_ready)
                                    }
                                )
                            },
                            isError = restoreDeviceId.isBlank() && restoreMessage != null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (restoreDeviceId.isNotBlank()) showRestoreConfirm = true
                            }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        BusyButton(
                            busy = isRestoring,
                            idleText = stringResource(R.string.settings_btn_restore_device),
                            busyText = stringResource(R.string.settings_btn_restoring_device),
                            onClick = { showRestoreConfirm = true },
                            enabled = restoreDeviceId.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        restoreMessage?.let {
                            StatusText(text = it, isSuccess = statusIsSuccess(it), announce = true)
                        }
                    }
                }
            }

            // H3-35: seção Backup (exportar/importar/restaurar por deviceId).
            CollapsibleSection(
                title = stringResource(R.string.settings_section_backup),
                expanded = backupExpanded,
                onToggle = { backupExpanded = it },
            ) {
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
                                text = stringResource(R.string.settings_backup_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(
                            text = stringResource(R.string.settings_backup_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            BusyButton(
                                busy = backupImportState is BackupImportState.Importing,
                                idleText = stringResource(R.string.settings_btn_export),
                                busyText = stringResource(R.string.settings_btn_exporting),
                                onClick = onExportBackup,
                                modifier = Modifier.weight(1f),
                            )
                            BusyButton(
                                busy = backupImportState is BackupImportState.Importing,
                                idleText = stringResource(R.string.settings_btn_import),
                                busyText = stringResource(R.string.settings_btn_importing),
                                onClick = onImportFileSelected,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        when (val state = backupImportState) {
                            is BackupImportState.Ready -> {
                                Text(
                                    text = stringResource(
                                        R.string.settings_backup_preview,
                                        state.preview.plans,
                                        state.preview.days,
                                        state.preview.planExercises,
                                        state.preview.sessions,
                                        state.preview.sessionExercises,
                                        state.preview.setLogs,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        onClick = { showReplaceConfirm = true },
                                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
                                    ) {
                                        Text(stringResource(R.string.settings_btn_replace_all))
                                    }
                                    OutlinedButton(
                                        onClick = onCancelImport,
                                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
                                    ) {
                                        Text(stringResource(R.string.common_cancel))
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
                                    Text(stringResource(R.string.settings_btn_dismiss))
                                }
                            }
                            else -> { }
                        }

                        backupMessage?.let {
                            StatusText(text = it, isSuccess = statusIsSuccess(it), announce = true)
                        }
                    }
                }
            }

            // H3-51: atualização in-app — checagem sob demanda (uma vez por
            // processo ao abrir Ajustes + botão manual), download só com toque
            // e instalação sempre pelo instalador do sistema.
            CollapsibleSection(
                title = stringResource(R.string.settings_update_title),
                expanded = updateExpanded,
                onToggle = { updateExpanded = it },
            ) {
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
                        when (val state = updateState) {
                            is UpdateState.Available -> {
                                Text(
                                    text = stringResource(R.string.settings_update_available_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = stringResource(
                                        R.string.settings_update_available_body,
                                        state.release.versionName ?: state.release.tagName,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                state.note?.let {
                                    StatusText(text = it, isSuccess = true, announce = true)
                                }
                                BusyButton(
                                    busy = false,
                                    idleText = stringResource(R.string.settings_update_btn_download),
                                    busyText = stringResource(R.string.settings_update_btn_download),
                                    onClick = onDownloadUpdate,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            is UpdateState.Downloading -> {
                                Text(
                                    text = stringResource(
                                        R.string.settings_update_downloading,
                                        state.progress,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                LinearProgressIndicator(
                                    progress = { state.progress / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            else -> {
                                Text(
                                    text = stringResource(R.string.settings_update_idle_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                when (state) {
                                    is UpdateState.Checking -> { }
                                    is UpdateState.UpToDate -> StatusText(
                                        text = stringResource(R.string.settings_update_uptodate),
                                        isSuccess = true,
                                        announce = true,
                                    )
                                    is UpdateState.Failed -> Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    else -> { }
                                }
                                BusyButton(
                                    busy = state is UpdateState.Checking,
                                    idleText = stringResource(R.string.settings_update_btn_check),
                                    busyText = stringResource(R.string.settings_update_checking),
                                    onClick = onCheckForUpdate,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (state is UpdateState.Failed || state is UpdateState.UpToDate) {
                                    OutlinedButton(
                                        onClick = onClearUpdateMessage,
                                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                                    ) {
                                        Text(stringResource(R.string.settings_btn_dismiss))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // H3-37: seletor de idioma do app (per-app locale).
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_language_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.settings_language_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}







/**
 * H3-35: seção colapsável com título e ícone de expansão.
 */
@Composable
private fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    var localExpanded by remember { mutableStateOf(expanded) }
    LaunchedEffect(expanded) {
        localExpanded = expanded
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextButton(
            onClick = { onToggle(!localExpanded) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    imageVector = if (localExpanded) {
                        Icons.Filled.KeyboardArrowUp
                    } else {
                        Icons.Filled.KeyboardArrowDown
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (localExpanded) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
            ) {
                content()
            }
        }
    }
}

/** Idioma da interface do app, conforme a escolha do seletor ou o sistema. */
private enum class AppLanguage {
    System,
    Portuguese,
    English,
}

/** Idioma efetivo do app, lido do per-app locale (vazio = padrão do sistema). */
private fun currentAppLanguage(): AppLanguage {
    val locales = AppCompatDelegate.getApplicationLocales()
    val tag = if (locales.size() > 0) locales.get(0)?.toLanguageTag() else null
    return when (tag) {
        "pt-BR" -> AppLanguage.Portuguese
        "en" -> AppLanguage.English
        else -> AppLanguage.System
    }
}

/** Aplica o idioma escolhido, recriando a activity via per-app locale. */
private fun applyAppLanguage(language: AppLanguage) {
    val locales = when (language) {
        AppLanguage.System -> LocaleListCompat.getEmptyLocaleList()
        AppLanguage.Portuguese -> LocaleListCompat.forLanguageTags("pt-BR")
        AppLanguage.English -> LocaleListCompat.forLanguageTags("en")
    }
    AppCompatDelegate.setApplicationLocales(locales)
}

@Composable
private fun LanguageSelector(
    current: AppLanguage,
    onChange: (AppLanguage) -> Unit,
) {
    val options = listOf(
        AppLanguage.System to R.string.settings_language_system,
        AppLanguage.Portuguese to R.string.settings_language_pt,
        AppLanguage.English to R.string.settings_language_en,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (lang, resId) ->
            val selected = current == lang
            if (selected) {
                Button(
                    onClick = {
                        onChange(lang)
                        applyAppLanguage(lang)
                    },
                    enabled = true,
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
                ) {
                    Text(stringResource(resId))
                }
            } else {
                OutlinedButton(
                    onClick = {
                        onChange(lang)
                        applyAppLanguage(lang)
                    },
                    enabled = true,
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
                ) {
                    Text(stringResource(resId))
                }
            }
        }
    }
}
