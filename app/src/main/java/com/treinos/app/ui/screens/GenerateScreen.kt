package com.treinos.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.treinos.app.data.remote.ModalityDto
import com.treinos.app.ui.ProfileValidator
import com.treinos.app.ui.components.ChipGroup
import com.treinos.app.ui.components.ScreenTopBar
import com.treinos.app.ui.components.SectionTitle

private val goalOptions = listOf(
    "hypertrophy" to "Hipertrofia",
    "strength" to "Força",
    "endurance" to "Resistência",
    "power" to "Potência",
    "fat_loss" to "Perda de gordura",
    "general" to "Condicionamento",
)

private val levelOptions = listOf(
    "beginner" to "Iniciante",
    "intermediate" to "Intermediário",
    "advanced" to "Avançado",
)

private val equipmentOptions = listOf(
    "body only" to "Peso corporal",
    "dumbbell" to "Halteres",
    "barbell" to "Barra",
    "cable" to "Polia",
    "machine" to "Máquina",
    "kettlebells" to "Kettlebell",
    "bands" to "Elásticos",
    "other" to "Outros",
)

private val muscleOptions = listOf(
    "chest" to "Peito",
    "back" to "Costas",
    "legs" to "Pernas",
    "shoulders" to "Ombros",
    "biceps" to "Bíceps",
    "triceps" to "Tríceps",
    "abs" to "Abdômen",
    "glutes" to "Glúteos",
)

private val durationOptions = listOf(30, 45, 60, 90)
private val reviewOptions = listOf(2, 4, 6, 8)

/** Rascunho de uma modalidade da semana híbrida antes de gerar. */
data class ModalityDraft(
    val included: Boolean = false,
    val sessionsPerWeek: Int = 1,
    val goal: String = "hypertrophy",
    val level: String = "intermediate",
    val focus: String? = null,
    val recent5kSeconds: Int? = null,
)

private val modalityTypes = listOf(
    "strength" to "Musculação",
    "run" to "Corrida",
    "mobility" to "Mobilidade",
)

@Composable
fun GenerateScreen(
    isGenerating: Boolean,
    onBack: () -> Unit,
    savedBodyWeightKg: Double? = null,
    savedHeightCm: Int? = null,
    onGenerate: (
        modalities: List<ModalityDto>,
        goal: String,
        daysPerWeek: Int,
        level: String,
        equipment: List<String>,
        muscles: List<String>,
        sessionDuration: Int,
        reviewWeeks: Int,
        bodyWeightKgText: String,
        heightCmText: String,
    ) -> Unit,
) {
    var goal by remember { mutableStateOf("hypertrophy") }
    var daysPerWeek by remember { mutableStateOf(4) }
    var level by remember { mutableStateOf("intermediate") }
    var equipment by remember { mutableStateOf(setOf("body only", "dumbbell", "barbell")) }
    var muscles by remember { mutableStateOf(setOf<String>()) }
    var duration by remember { mutableStateOf(60) }
    var reviewWeeks by remember { mutableStateOf(4) }
    var bodyWeightKgText by remember { mutableStateOf(savedBodyWeightKg?.toString() ?: "") }
    var heightCmText by remember { mutableStateOf(savedHeightCm?.toString() ?: "") }
    var profileError by remember { mutableStateOf<String?>(null) }

    // Um draft por tipo de modalidade.
    var modalityDrafts by remember {
        mutableStateOf(
            modalityTypes.map { (type, _) -> type to ModalityDraft() }.toMap()
        )
    }

    fun modalityToDto(type: String, draft: ModalityDraft): ModalityDto? {
        if (!draft.included) return null
        return ModalityDto(
            type = type,
            sessionsPerWeek = draft.sessionsPerWeek.coerceAtLeast(1),
            goal = when (type) {
                "strength" -> draft.goal
                "run" -> "endurance"
                "mobility" -> "general"
                else -> draft.goal
            },
            level = draft.level,
            availableEquipment = equipment.toList(),
            targetMuscles = if (type == "strength") muscles.toList() else emptyList(),
            focus = if (type == "mobility") draft.focus else null,
            recent5kSeconds = if (type == "run") draft.recent5kSeconds else null,
        )
    }

    Scaffold(
        topBar = { ScreenTopBar(title = "Novo treino", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Text(
                        text = "O backend monta o plano na hora e devolve também a data em que ele " +
                            "deve ser revisto. O plano é salvo no aparelho; nada é enviado ao backend " +
                            "até você tocar em Sincronizar.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            item { SectionTitle("Modalidades da semana") }
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    modalityTypes.forEach { (type, label) ->
                        key(type) {
                            val draft = modalityDrafts[type] ?: ModalityDraft()
                            var included by remember(type) { mutableStateOf(draft.included) }
                            var sessions by remember(type) { mutableStateOf(draft.sessionsPerWeek) }
                            var recent5kText by remember(type) {
                                mutableStateOf(draft.recent5kSeconds?.toString() ?: "")
                            }
                            var focusText by remember(type) { mutableStateOf(draft.focus ?: "") }

                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            checked = included,
                                            onCheckedChange = {
                                                included = it
                                                modalityDrafts = modalityDrafts + (type to draft.copy(included = it))
                                            },
                                            modifier = Modifier.minimumInteractiveComponentSize().semantics {
                                                stateDescription = if (included) "Ligado" else "Desligado"
                                            },
                                        )
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = "${draft.sessionsPerWeek}x/semana",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (included) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            OutlinedTextField(
                                                value = sessions.toString(),
                                                onValueChange = {
                                                    val v = it.toIntOrNull() ?: 1
                                                    sessions = v
                                                    val updated = draft.copy(included = true, sessionsPerWeek = v)
                                                    modalityDrafts = modalityDrafts + (type to updated)
                                                },
                                                label = { Text("Sessões por semana") },
                                                supportingText = { Text("Número inteiro") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                                isError = sessions < 1,
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        when (type) {
                                            "run" -> {
                                                OutlinedTextField(
                                                    value = recent5kText,
                                                    onValueChange = {
                                                        recent5kText = it
                                                        val updated = draft.copy(included = true, recent5kSeconds = it.toIntOrNull())
                                                        modalityDrafts = modalityDrafts + (type to updated)
                                                    },
                                                    label = { Text("Benchmark 5 km (s, opcional)") },
                                                    supportingText = { Text("Opcional. Tempo em segundos.") },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                                )
                                            }
                                            "mobility" -> {
                                                OutlinedTextField(
                                                    value = focusText,
                                                    onValueChange = {
                                                        focusText = it
                                                        val updated = draft.copy(included = true, focus = it.takeIf { it.isNotBlank() })
                                                        modalityDrafts = modalityDrafts + (type to updated)
                                                    },
                                                    label = { Text("Foco (ex.: costas, quadril, opcional)") },
                                                    supportingText = { Text("Opcional") },
                                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Pelo menos uma modalidade deve estar incluída. Natação não entra no plano: " +
                        "registre-a como atividade avulsa no histórico.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            item { SectionTitle("Objetivo (musculação)") }
            item {
                ChipGroup(
                    options = goalOptions,
                    selected = setOf(goal),
                    onToggle = { goal = it },
                )
            }

            item { SectionTitle("Dias por semana") }
            item {
                ChipGroup(
                    options = (2..6).map { it.toString() to "${it}x" },
                    selected = setOf(daysPerWeek.toString()),
                    onToggle = { daysPerWeek = it.toInt() },
                )
            }

            item { SectionTitle("Seu nível (musculação)") }
            item {
                ChipGroup(
                    options = levelOptions,
                    selected = setOf(level),
                    onToggle = { level = it },
                )
            }

            item { SectionTitle("Equipamentos disponíveis") }
            item {
                ChipGroup(
                    options = equipmentOptions,
                    selected = equipment,
                    onToggle = { value ->
                        equipment = if (value in equipment) equipment - value else equipment + value
                    },
                )
            }

            item { SectionTitle("Foco muscular (opcional, musculação)") }
            item {
                ChipGroup(
                    options = muscleOptions,
                    selected = muscles,
                    onToggle = { value ->
                        muscles = if (value in muscles) muscles - value else muscles + value
                    },
                )
            }

            item { SectionTitle("Duração da sessão") }
            item {
                ChipGroup(
                    options = durationOptions.map { it.toString() to "$it min" },
                    selected = setOf(duration.toString()),
                    onToggle = { duration = it.toInt() },
                )
            }

            item { SectionTitle("Revisar o plano em") }
            item {
                ChipGroup(
                    options = reviewOptions.map { it.toString() to "$it semanas" },
                    selected = setOf(reviewWeeks.toString()),
                    onToggle = { reviewWeeks = it.toInt() },
                )
            }

            item { SectionTitle("Seu perfil (opcional)") }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = bodyWeightKgText,
                        onValueChange = { bodyWeightKgText = it; profileError = null },
                        label = { Text("Peso (kg)") },
                        supportingText = { Text("Opcional") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        isError = profileError != null && bodyWeightKgText.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = heightCmText,
                        onValueChange = { heightCmText = it; profileError = null },
                        label = { Text("Altura (cm)") },
                        supportingText = { Text("Opcional") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { }),
                        isError = profileError != null && heightCmText.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            profileError?.let { error ->
                item {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp).padding(top = 4.dp),
                    )
                }
            }
            if (savedBodyWeightKg != null || savedHeightCm != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "Perfil salvo",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            savedBodyWeightKg?.let {
                                Text(text = "Peso: $it kg", style = MaterialTheme.typography.bodyMedium)
                            }
                            savedHeightCm?.let {
                                Text(text = "Altura: $it cm", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (savedBodyWeightKg != null && savedHeightCm != null) {
                                val bmi = savedBodyWeightKg / ((savedHeightCm / 100.0) * (savedHeightCm / 100.0))
                                Text(text = "IMC: ${"%.1f".format(bmi)}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            val selectedModalities = modalityDrafts.entries.filter { it.value.included }
            val hasModalities = selectedModalities.isNotEmpty()

            item {
                Button(
                    onClick = {
                        val validation = ProfileValidator.validate(bodyWeightKgText, heightCmText)
                        if (!validation.valid) {
                            profileError = validation.error
                            return@Button
                        }
                        profileError = null
                        if (!hasModalities) {
                            profileError = "Inclua pelo menos uma modalidade."
                            return@Button
                        }
                        val dtoList = selectedModalities.map { (type, draft) ->
                            modalityToDto(type, draft)
                        }.filterNotNull()
                        onGenerate(
                            dtoList,
                            goal,
                            daysPerWeek,
                            level,
                            equipment.toList(),
                            muscles.toList(),
                            duration,
                            reviewWeeks,
                            bodyWeightKgText,
                            heightCmText,
                        )
                    },
                    enabled = !isGenerating && hasModalities,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .minimumInteractiveComponentSize(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Text("Gerando semana…")
                        } else {
                            Text("Gerar semana híbrida")
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Se a IA não estiver disponível, o backend monta uma semana híbrida válida " +
                        "com os templates locais de corrida e mobilidade. Você sempre recebe um plano utilizável.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        }
    }
}
