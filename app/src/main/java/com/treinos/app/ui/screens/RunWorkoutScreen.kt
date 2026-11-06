package com.treinos.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.ui.RunLogInput
import com.treinos.app.ui.RunLogValidation
import com.treinos.app.ui.formatRunPace
import com.treinos.app.ui.parseDraftNumber
import com.treinos.app.ui.validateRunLog
import com.treinos.app.ui.components.ScreenTopBar
import com.treinos.app.ui.components.SectionTitle

@Composable
fun RunWorkoutScreen(
    session: SessionWithExercises?,
    onLeave: () -> Unit,
    onFinish: (RunLogInput) -> Unit,
) {
    var durationMinutes by remember { mutableStateOf("") }
    var distanceMeters by remember { mutableStateOf("") }
    var rpe by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var pace by remember { mutableStateOf<String?>(null) }

    fun compute() {
        val dur = durationMinutes.toIntOrNull() ?: 0
        val dist = parseDraftNumber(distanceMeters)
        val rpeVal = parseDraftNumber(rpe)
        val input = RunLogInput(
            durationSeconds = dur.toLong() * 60,
            distanceMeters = dist,
            rpe = rpeVal,
        )
        val result = validateRunLog(input)
        error = result.error
        pace = result.paceSecondsPerKm?.let { formatRunPace(it) }
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = session?.session?.dayName?.ifBlank { "Corrida" } ?: "Corrida",
                onBack = onLeave,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Registre a corrida planejada",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Duração, distância e RPE. Nada de reps ou carga nesta sessão.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionTitle("Sua corrida")

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = durationMinutes,
                    onValueChange = { durationMinutes = it; compute() },
                    label = { Text("Duração (min)") },
                    supportingText = { Text("Opcional; se não preencher, usa a duração do plano") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null,
                )
                OutlinedTextField(
                    value = distanceMeters,
                    onValueChange = { distanceMeters = it; compute() },
                    label = { Text("Distância (m)") },
                    supportingText = { Text("Opcional. Ex.: 5000 para 5 km") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null,
                )
                OutlinedTextField(
                    value = rpe,
                    onValueChange = { rpe = it; compute() },
                    label = { Text("RPE (opcional)") },
                    supportingText = { Text("De 1 a 10. Use vírgula ou ponto para decimais.") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null,
                )
            }

            pace?.let { p ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Pace estimado: $p",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            error?.let { e ->
                Text(
                    text = e,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Button(
                onClick = {
                    compute()
                    if (error == null) {
                        val dur = durationMinutes.toIntOrNull() ?: 0
                        val dist = parseDraftNumber(distanceMeters)
                        val rpeVal = parseDraftNumber(rpe)
                        onFinish(
                            RunLogInput(
                                durationSeconds = dur.toLong() * 60,
                                distanceMeters = dist,
                                rpe = rpeVal,
                            )
                        )
                    }
                },
                enabled = error == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .minimumInteractiveComponentSize(),
            ) {
                Text("Finalizar corrida")
            }

            TextButton(
                onClick = onLeave,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Text("Cancelar")
            }
        }
    }
}
