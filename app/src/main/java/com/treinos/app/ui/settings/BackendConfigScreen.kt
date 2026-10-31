package com.treinos.app.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.treinos.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackendConfigScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val backendUrl by remember { mutableStateOf(viewModel.backendUrl) }
    val backendStatus by remember { mutableStateOf(viewModel.backendStatus) }
    val savedUrl = remember { mutableStateOf("") }
    var showSavedUrl by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurar Backend", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Auto-discovery section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Descoberta Automática (mDNS)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Text(
                        "O app pode encontrar automaticamente o backend na rede local via mDNS. " +
                        "Certifique-se de que o backend está rodando e o celular está na mesma Wi-Fi.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { 
                                // TODO: Start NSD discovery
                                viewModel.testBackendConnection() 
                            },
                            enabled = backendStatus != SettingsViewModel.BackendStatus.Connecting
                        ) {
                            if (backendStatus == SettingsViewModel.BackendStatus.Connecting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null)
                                Text("Procurar na rede")
                            }
                        }
                        
                        when (backendStatus) {
                            SettingsViewModel.BackendStatus.Connected -> {
                                Box(contentAlignment = Alignment.CenterStart) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Encontrado", tint = MaterialTheme.colorScheme.primary)
                                    Text("  Backend encontrado!", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                }
                            }
                            SettingsViewModel.BackendStatus.Error -> {
                                Text("Não encontrado", color = MaterialTheme.colorScheme.error)
                            }
                            SettingsViewModel.BackendStatus.Connecting -> {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            }
                            SettingsViewModel.BackendStatus.Unknown -> {
                                Text("Aguardando...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Manual configuration section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Configuração Manual", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    OutlinedTextField(
                        value = backendUrl,
                        onValueChange = { viewModel.updateBackendUrl(it) },
                        label = { Text("URL do Backend") },
                        placeholder = { Text("http://192.168.x.x:3000") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null) }
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.testBackendConnection() },
                            enabled = backendStatus != SettingsViewModel.BackendStatus.Connecting
                        ) {
                            if (backendStatus == SettingsViewModel.BackendStatus.Connecting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Cloud, contentDescription = null)
                                Text("Testar Conexão")
                            }
                        }
                        
                        when (backendStatus) {
                            SettingsViewModel.BackendStatus.Connected -> {
                                Box(contentAlignment = Alignment.CenterStart) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Conectado", tint = MaterialTheme.colorScheme.primary)
                                    Text("  Conectado!", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                }
                            }
                            SettingsViewModel.BackendStatus.Error -> {
                                Text("Falha na conexão", color = MaterialTheme.colorScheme.error)
                            }
                            SettingsViewModel.BackendStatus.Connecting -> {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            }
                            SettingsViewModel.BackendStatus.Unknown -> {
                                Text("Não testado", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    
                    Button(
                        onClick = { 
                            viewModel.saveBackendUrl(backendUrl)
                            navController.popBackStack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Text("Salvar e Usar")
                    }
                    
                    Text(
                        "Exemplos:\n• Emulador: http://10.0.2.2:3000\n• Device físico: http://192.168.1.42:3000\n• Codespaces: https://xxx-3000.app.github.dev",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Saved URLs section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("URLs Salvas", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        androidx.compose.material3.IconButton(onClick = { showSavedUrl = !showSavedUrl }) {
                            Icon(if (showSavedUrl) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "Expandir")
                        }
                    }
                    
                    if (showSavedUrl) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // TODO: Load from DataStore
                            SavedUrlRow(
                                url = "http://10.0.2.2:3000",
                                label = "Emulador Android",
                                onUse = { viewModel.updateBackendUrl("http://10.0.2.2:3000"); showSavedUrl = false },
                                onDelete = { /* TODO */ }
                            )
                            SavedUrlRow(
                                url = "http://192.168.1.42:3000",
                                label = "PC da rede local",
                                onUse = { viewModel.updateBackendUrl("http://192.168.1.42:3000"); showSavedUrl = false },
                                onDelete = { /* TODO */ }
                            )
                        }
                    }
                }
            }

            // adb reverse helper
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Desenvolvimento (adb reverse)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text(
                        "Para testar no emulador sem configurar IP:\n" +
                        "1. Terminal: adb reverse tcp:3000 tcp:3000\n" +
                        "2. Use URL: http://10.0.2.2:3000\n" +
                        "3. O tráfego do emulador vai para o localhost do PC",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

@Composable
fun SavedUrlRow(
    url: String,
    label: String,
    onUse: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.onSurface)
            Text(url, fontSize = 11.sp, color = colors.onSurfaceVariant)
        }
        Button(onClick = onUse) {
            Text("Usar")
        }
        androidx.compose.material3.IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Remover", tint = colors.error)
        }
    }
}
