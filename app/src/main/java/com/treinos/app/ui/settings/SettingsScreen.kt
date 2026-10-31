package com.treinos.app.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val backendUrl by remember { mutableStateOf(viewModel.backendUrl) }
    val backendStatus by remember { mutableStateOf(viewModel.backendStatus) }
    val availableEquipment by remember { mutableStateOf(viewModel.availableEquipment) }
    val targetMuscles by remember { mutableStateOf(viewModel.targetMuscles) }
    val currentLevel by remember { mutableStateOf(viewModel.currentLevel) }
    val sessionDuration by remember { mutableStateOf(viewModel.sessionDurationMinutes) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações", fontWeight = FontWeight.Bold) },
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
            // Backend Connection Section
            SettingsSection(title = "Conexão com Backend", icon = Icons.Default.Cloud) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = backendUrl,
                            onValueChange = { viewModel.updateBackendUrl(it) },
                            label = { Text("URL do Backend") },
                            placeholder = { Text("http://192.168.x.x:3000") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
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
                                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Cloud, contentDescription = null)
                                    Text("Testar Conexão")
                                }
                            }
                            
                            when (backendStatus) {
                                SettingsViewModel.BackendStatus.Connected -> {
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Conectado", tint = MaterialTheme.colorScheme.primary)
                                        Text("  Conectado", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                    }
                                }
                                SettingsViewModel.BackendStatus.Error -> {
                                    Text("Erro de conexão", color = MaterialTheme.colorScheme.error)
                                }
                                SettingsViewModel.BackendStatus.Connecting -> {
                                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                }
                                SettingsViewModel.BackendStatus.Unknown -> {
                                    Text("Não testado", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        
                        Text(
                            "Dica: Use 'http://10.0.2.2:3000' no emulador ou o IP da sua máquina na rede (ex: 192.168.1.42:3000).",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // AI Generation Preferences
            SettingsSection(title = "Geração de Treino (IA)", icon = Icons.Default.FitnessCenter) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Equipment
                        VStack(spacing = 8.dp) {
                            Text("Equipamentos disponíveis", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            EquipmentChips(
                                selected = availableEquipment,
                                onChange = { viewModel.availableEquipment = it },
                                allOptions = listOf(
                                    "body only", "dumbbell", "barbell", "cable", "bands",
                                    "kettlebells", "machine", "medicine ball", "foam roll", "e-z curl bar", "exercise ball"
                                )
                            )
                        }

                        // Target Muscles
                        VStack(spacing = 8.dp) {
                            Text("Foco muscular", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            EquipmentChips(
                                selected = targetMuscles,
                                onChange = { viewModel.targetMuscles = it },
                                allOptions = listOf(
                                    "chest", "back", "legs", "shoulders", "arms",
                                    "abdominals", "glutes", "quadriceps", "hamstrings", "calves",
                                    "lats", "traps", "lower back", "forearms", "biceps", "triceps"
                                )
                            )
                        }

                        // Level
                        VStack(spacing = 8.dp) {
                            Text("Nível", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf("beginner", "intermediate", "advanced").forEach { level ->
                                    val isSelected = currentLevel == level
                                    androidx.compose.material3.FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.currentLevel = level },
                                        label = { Text(level.capitalize()) }
                                    )
                                }
                            }
                        }

                        // Session Duration
                        VStack(spacing = 8.dp) {
                            Text("Duração da sessão: ${sessionDuration} min", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            androidx.compose.material3.Slider(
                                value = sessionDuration.toFloat(),
                                onValueChange = { viewModel.sessionDurationMinutes = it.roundToInt() },
                                valueRange = 20f..180f,
                                steps = 16
                            )
                        }
                    }
                }
            }

            // Backend Configuration
            SettingsSection(title = "Conexão com Backend", icon = Icons.Default.Cloud) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsRow(
                            title = "Configurar Backend",
                            subtitle = "Descoberta automática (mDNS) ou IP manual",
                            icon = Icons.Default.Cloud,
                            onClick = { navController.navigate("backend_config") }
                        )
                        SettingsRow(
                            title = "Testar conexão atual",
                            subtitle = "${if (backendStatus == SettingsViewModel.BackendStatus.Connected) "Conectado" else if (backendStatus == SettingsViewModel.BackendStatus.Error) "Erro" else "Não testado"}",
                            icon = if (backendStatus == SettingsViewModel.BackendStatus.Connected) Icons.Default.CheckCircle else Icons.Default.Cloud,
                            onClick = { viewModel.testBackendConnection() },
                            enabled = backendStatus != SettingsViewModel.BackendStatus.Connecting
                        )
                    }
                }
            }

            // Data Management
            SettingsSection(title = "Dados", icon = Icons.Default.Settings) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsRow(
                            title = "Exportar dados",
                            subtitle = "Backup completo (treinos + histórico + preferências)",
                            icon = Icons.Default.Download,
                            onClick = { /* TODO: Export JSON */ }
                        )
                        SettingsRow(
                            title = "Importar dados",
                            subtitle = "Restaurar backup JSON",
                            icon = Icons.Default.Upload,
                            onClick = { /* TODO: Import JSON */ }
                        )
                        SettingsRow(
                            title = "Limpar histórico",
                            subtitle = "Remove todas as sessões concluídas (mantém treinos)",
                            icon = Icons.Default.Delete,
                            destructive = true,
                            onClick = { /* TODO: Show confirmation */ }
                        )
                    }
                }
            }

            // About
            SettingsSection(title = "Sobre") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Treinos App", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Versão 1.0.0", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("App pessoal de musculação — Local-first + IA", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Catálogo: free-exercise-db (Unlicense)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("IA: SenseNova API (sensenova-6.8-flash-lite)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    icon: ImageVector? = null,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        content()
    }
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    androidx.compose.material3.ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() },
        headlineContent = { Text(title, color = if (destructive) colors.error else colors.onSurface) },
        supportingContent = { Text(subtitle, color = colors.onSurfaceVariant) },
        leadingContent = { Icon(icon, contentDescription = null, tint = if (destructive) colors.error else colors.primary) }
    )
}

@Composable
fun EquipmentChips(
    selected: List<String>,
    onChange: (List<String>) -> Unit,
    allOptions: List<String>
) {
    var expanded by remember { mutableStateOf(false) }
    val displayOptions = if (expanded) allOptions else allOptions.take(8)
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            displayOptions.take(3).forEach { option ->
                val isSelected = option in selected
                androidx.compose.material3.FilterChip(
                    selected = isSelected,
                    onClick = {
                        val newList = if (isSelected) selected.filter { it != option } else selected + option
                        onChange(newList)
                    },
                    label = { Text(option.replace("_", " ").capitalize()) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        // Row 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            displayOptions.drop(3).take(3).forEach { option ->
                val isSelected = option in selected
                androidx.compose.material3.FilterChip(
                    selected = isSelected,
                    onClick = {
                        val newList = if (isSelected) selected.filter { it != option } else selected + option
                        onChange(newList)
                    },
                    label = { Text(option.replace("_", " ").capitalize()) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        // Row 3
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            displayOptions.drop(6).take(3).forEach { option ->
                val isSelected = option in selected
                androidx.compose.material3.FilterChip(
                    selected = isSelected,
                    onClick = {
                        val newList = if (isSelected) selected.filter { it != option } else selected + option
                        onChange(newList)
                    },
                    label = { Text(option.replace("_", " ").capitalize()) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (allOptions.size > 8) {
            androidx.compose.material3.FilterChip(
                selected = expanded,
                onClick = { expanded = !expanded },
                label = { Text(if (expanded) "Mostrar menos" else "Mais opções...") }
            )
        }
    }
}

@Composable
fun VStack(
    modifier: Modifier = Modifier,
    spacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing), content = content)
}
