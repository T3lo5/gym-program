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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.treinos.app.ui.components.ScreenTopBar
import com.treinos.app.ui.formatIsoDateTime

@Composable
fun SettingsScreen(
    backendUrl: String,
    isTesting: Boolean,
    isSyncing: Boolean,
    connectionMessage: String?,
    syncMessage: String?,
    lastSyncAt: String?,
    deviceId: String,
    onBack: () -> Unit,
    onSaveUrl: (String) -> Unit,
    onTest: () -> Unit,
    onSync: () -> Unit,
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
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Como os dados funcionam",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "• O treino é gravado no aparelho imediatamente.\n" +
                            "• O backend só é chamado ao gerar um treino novo ou sincronizar.\n" +
                            "• A data de revisão vem do backend e aparece na tela inicial.\n" +
                            "• Sem internet, você continua treinando e registrando as séries.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
