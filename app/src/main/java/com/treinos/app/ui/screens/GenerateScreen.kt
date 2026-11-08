package com.treinos.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.treinos.app.data.remote.ModalityDto
import com.treinos.app.ui.ComplementaryValidator
import com.treinos.app.ui.GenerateConfig
import com.treinos.app.ui.ModalityDraft
import com.treinos.app.ui.ProfileValidator
import com.treinos.app.ui.UiError
import com.treinos.app.ui.components.ChipGroup
import com.treinos.app.ui.components.ErrorState
import com.treinos.app.ui.components.ScreenTopBar
import com.treinos.app.ui.components.SectionTitle
import kotlinx.coroutines.delay

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

/** Durações do modo complementar (H3-16): sessões curtas, 15–45 min. */
private val complementaryDurationOptions = listOf(15, 20, 30, 45)
private val reviewOptions = listOf(2, 4, 6, 8)

/** Rótulos pt-BR → valores de contrato (H3-7); o valor escolhido é o que viaja no request. */
private val sexOptions = listOf(
    "female" to "Feminino",
    "male" to "Masculino",
    "other" to "Outro",
    "undisclosed" to "Prefiro não informar",
)

private val environmentOptions = listOf(
    "gym" to "Academia",
    "home" to "Casa",
    "bodyweight" to "Peso corporal",
)

private val modalityTypes = listOf(
    "strength" to "Musculação",
    "run" to "Corrida",
    "mobility" to "Mobilidade",
)

/** Valor restaurado só é aceito se ainda for uma opção válida; senão, default. */
private fun <T> restored(value: T, allowed: List<T>, fallback: T): T =
    if (value in allowed) value else fallback

@Composable
fun GenerateScreen(
    isGenerating: Boolean,
    onBack: () -> Unit,
    savedBodyWeightKg: Double? = null,
    savedHeightCm: Int? = null,
    savedSex: String? = null,
    // H3-30: última configuração persistida, restaurada na abertura da tela.
    savedConfig: GenerateConfig? = null,
    // H3-29: erro persistente na tela de origem, com "Tentar de novo".
    error: UiError? = null,
    onRetry: () -> Unit = {},
    // H3-30: cancelar a geração em curso a partir do overlay.
    onCancel: () -> Unit = {},
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
        sexChoice: String?,
        environment: String?,
        kind: String,
        config: GenerateConfig,
    ) -> Unit,
) {
    val initial = savedConfig ?: GenerateConfig.DEFAULT
    var planKind by remember {
        mutableStateOf(if (initial.planKind == "extra") "extra" else "main")
    }
    var goal by remember {
        mutableStateOf(restored(initial.goal, goalOptions.map { it.first }, "hypertrophy"))
    }
    // Dias e duração válidos dependem do modo restaurado (H3-16).
    val validDays = if (planKind == "extra") 1..6 else 2..6
    val validDurations = if (planKind == "extra") complementaryDurationOptions else durationOptions
    var daysPerWeek by remember {
        mutableStateOf(initial.daysPerWeek.takeIf { it in validDays } ?: 4)
    }
    var level by remember {
        mutableStateOf(restored(initial.level, levelOptions.map { it.first }, "intermediate"))
    }
    var equipment by remember {
        mutableStateOf(
            initial.equipment.toSet().ifEmpty { GenerateConfig.DEFAULT_EQUIPMENT.toSet() }
        )
    }
    var muscles by remember { mutableStateOf(initial.muscles.toSet()) }
    var duration by remember {
        mutableStateOf(initial.sessionDurationMinutes.takeIf { it in validDurations } ?: 60)
    }
    var reviewWeeks by remember {
        mutableStateOf(initial.reviewWeeks.takeIf { it in reviewOptions } ?: 4)
    }
    var bodyWeightKgText by remember {
        mutableStateOf(initial.bodyWeightKgText.ifBlank { savedBodyWeightKg?.toString() ?: "" })
    }
    var heightCmText by remember {
        mutableStateOf(initial.heightCmText.ifBlank { savedHeightCm?.toString() ?: "" })
    }
    var sexChoice by remember { mutableStateOf(initial.sexChoice ?: savedSex) }
    var profileError by remember { mutableStateOf<String?>(null) }
    var environment by remember {
        mutableStateOf(restored(initial.environment, environmentOptions.map { it.first }, "gym"))
    }
    // H3-30: perfil/sexo/revisão/ambiente ficam em "Mais opções", colapsado por padrão.
    var showMoreOptions by remember { mutableStateOf(false) }
    val isComplementary = planKind == "extra"

    // Um draft por tipo de modalidade, restaurado da última configuração.
    var modalityDrafts by remember {
        mutableStateOf(
            modalityTypes.map { (type, _) ->
                type to (initial.draftsByType[type] ?: ModalityDraft(type = type))
            }.toMap()
        )
    }

    // H3-30: seções de força só aparecem quando a força está no plano — um
    // plano só de corrida/mobilidade não mostra objetivo/nível/equipamento.
    val strengthIncluded = isComplementary || (modalityDrafts["strength"]?.included == true)

    val selectedModalities = modalityDrafts.entries.filter { it.value.included }
    val hasModalities = selectedModalities.isNotEmpty()

    // Modo complementar (H3-16): a sessão de força é montada aqui —
    // uma sessão por dia selecionado, duração curta, sem corrida/mobilidade.
    val complementaryValidation = if (isComplementary) {
        ComplementaryValidator.validate(
            focusMuscles = muscles.toList().takeIf { it.isNotEmpty() },
            daysPerWeek = daysPerWeek,
            sessionDurationMinutes = duration,
            modalityTypes = listOf("strength"),
        )
    } else {
        null
    }
    val hasFocusOrAlternativeEnvironment = muscles.isNotEmpty() || environment != "gym"
    val complementaryReady = complementaryValidation?.valid == true && hasFocusOrAlternativeEnvironment
    val canGenerate = if (isComplementary) complementaryReady else hasModalities
    val generateHint = when {
        !isComplementary -> "Inclua pelo menos uma modalidade."
        !hasFocusOrAlternativeEnvironment ->
            "Escolha o foco exclusivo ou um ambiente diferente de academia."
        else -> complementaryValidation?.error ?: "Ajuste as opções do plano complementar."
    }

    fun modalityToDto(type: String, draft: ModalityDraft): ModalityDto? {
        if (!draft.included) return null
        return ModalityDto(
            type = type,
            sessionsPerWeek = draft.sessionsPerWeek.coerceAtLeast(1),
            // H3-30: a modalidade de força segue o objetivo do chip global —
            // antes ia sempre "hypertrophy", divergindo do objetivo escolhido.
            goal = when (type) {
                "run" -> "endurance"
                "mobility" -> "general"
                else -> goal
            },
            level = draft.level,
            availableEquipment = equipment.toList(),
            targetMuscles = if (type == "strength") muscles.toList() else emptyList(),
            focus = if (type == "mobility") draft.focus else null,
            recent5kSeconds = if (type == "run") draft.recent5kSeconds else null,
            sessionDurationMinutes = draft.sessionDurationMinutes.coerceAtLeast(5).coerceAtMost(120),
        )
    }

    /** Configuração atual do formulário, para persistir na geração (H3-30). */
    fun currentConfig(): GenerateConfig = GenerateConfig(
        planKind = planKind,
        goal = goal,
        daysPerWeek = daysPerWeek,
        level = level,
        equipment = equipment.toList(),
        muscles = muscles.toList(),
        sessionDurationMinutes = duration,
        reviewWeeks = reviewWeeks,
        environment = environment,
        bodyWeightKgText = bodyWeightKgText,
        heightCmText = heightCmText,
        sexChoice = sexChoice,
        modalities = modalityTypes.map { (type, _) ->
            modalityDrafts[type] ?: ModalityDraft(type = type)
        },
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = { ScreenTopBar(title = "Novo treino", onBack = onBack) },
            // H3-30: CTA fixo no fim da tela, sempre visível, com o hint de
            // validação junto — o usuário não precisa rolar para saber por que
            // não pode gerar.
            bottomBar = {
                Surface(tonalElevation = 3.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val ctaHint = when {
                            profileError != null -> profileError
                            !canGenerate -> generateHint
                            else -> null
                        }
                        ctaHint?.let { hint ->
                            Text(
                                text = hint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Button(
                            onClick = {
                                val validation = ProfileValidator.validate(
                                    bodyWeightKgText,
                                    heightCmText,
                                    sexChoice,
                                )
                                if (!validation.valid) {
                                    profileError = validation.error
                                    showMoreOptions = true
                                    return@Button
                                }
                                profileError = null
                                if (!canGenerate) {
                                    profileError = generateHint
                                    return@Button
                                }
                                val dtoList = if (isComplementary) {
                                    listOf(
                                        ModalityDto(
                                            type = "strength",
                                            sessionsPerWeek = daysPerWeek,
                                            goal = goal,
                                            level = level,
                                            availableEquipment = equipment.toList(),
                                            targetMuscles = emptyList(),
                                            sessionDurationMinutes = duration,
                                        )
                                    )
                                } else {
                                    selectedModalities.map { (type, draft) ->
                                        modalityToDto(type, draft)
                                    }.filterNotNull()
                                }
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
                                    sexChoice,
                                    environment,
                                    planKind,
                                    currentConfig(),
                                )
                            },
                            enabled = !isGenerating && canGenerate,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize(),
                        ) {
                            Text(
                                if (isComplementary) "Gerar plano complementar" else "Gerar semana híbrida"
                            )
                        }
                    }
                }
            },
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

                // H3-29: o erro fica visível até o usuário tentar de novo ou voltar —
                // não desaparece com o snackbar de ~4 s.
                error?.let { uiError ->
                    item {
                        ErrorState(
                            title = uiError.title,
                            message = uiError.message,
                            onRetry = if (uiError.retryLabel != null) onRetry else null,
                        )
                    }
                }

                item { SectionTitle("O que gerar") }
                item {
                    ChipGroup(
                        options = listOf(
                            "main" to "Substituir meu plano principal",
                            "extra" to "Adicionar plano complementar",
                        ),
                        selected = setOf(planKind),
                        onToggle = { value ->
                            planKind = value
                            // A duração selecionada pode não existir no outro modo.
                            val options = if (value == "extra") complementaryDurationOptions else durationOptions
                            if (duration !in options) {
                                duration = if (value == "extra") 30 else 60
                            }
                        },
                    )
                }
                item {
                    Text(
                        text = if (planKind == "extra") {
                            "O plano complementar aparece ao lado do principal na Home; nada é desativado. " +
                                "Complementar é só de força, com foco exclusivo e sessões curtas."
                        } else {
                            "O plano principal anterior é desativado; planos complementares continuam ativos."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (!isComplementary) {
                    item { SectionTitle("Modalidades da semana") }
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            modalityTypes.forEach { (type, label) ->
                                key(type) {
                                    val draft = modalityDrafts[type] ?: ModalityDraft(type = type)
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
                                                        modalityDrafts = modalityDrafts +
                                                            (type to draft.copy(included = it))
                                                    },
                                                    modifier = Modifier
                                                        .minimumInteractiveComponentSize()
                                                        .semantics {
                                                            stateDescription = if (included) "Ligado" else "Desligado"
                                                            contentDescription = "Incluir $label"
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
                                                        keyboardOptions = KeyboardOptions(
                                                            keyboardType = KeyboardType.Number,
                                                            imeAction = ImeAction.Done,
                                                        ),
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
                                                                val updated = draft.copy(
                                                                    included = true,
                                                                    recent5kSeconds = it.toIntOrNull(),
                                                                )
                                                                modalityDrafts = modalityDrafts + (type to updated)
                                                            },
                                                            label = { Text("Benchmark 5 km (s, opcional)") },
                                                            supportingText = { Text("Opcional. Tempo em segundos.") },
                                                            keyboardOptions = KeyboardOptions(
                                                                keyboardType = KeyboardType.Number,
                                                                imeAction = ImeAction.Done,
                                                            ),
                                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                                        )
                                                    }
                                                    "mobility" -> {
                                                        OutlinedTextField(
                                                            value = focusText,
                                                            onValueChange = {
                                                                focusText = it
                                                                val updated = draft.copy(
                                                                    included = true,
                                                                    focus = it.takeIf { it.isNotBlank() },
                                                                )
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
                            text = "Natação não entra no plano: registre-a como atividade avulsa no histórico.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }

                if (strengthIncluded) {
                    item { SectionTitle("Objetivo (musculação)") }
                    item {
                        ChipGroup(
                            options = goalOptions,
                            selected = setOf(goal),
                            onToggle = { goal = it },
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

                    item {
                        SectionTitle(
                            if (isComplementary) "Foco exclusivo (complementar)" else "Foco muscular (opcional, musculação)"
                        )
                    }
                    if (isComplementary) {
                        item {
                            Text(
                                text = "Todo exercício do plano será deste foco — nada de outros grupos entra junto.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item {
                        ChipGroup(
                            options = muscleOptions,
                            selected = muscles,
                            onToggle = { value ->
                                muscles = if (value in muscles) muscles - value else muscles + value
                            },
                        )
                    }
                    if (!isComplementary && muscles.isNotEmpty()) {
                        item {
                            Text(
                                text = "Os grupos escolhidos entram em todas as sessões de força. " +
                                    "Para um plano APENAS de um grupo (ex.: só abdômen), use " +
                                    "\"Adicionar plano complementar\".",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    item { SectionTitle("Duração da sessão") }
                    item {
                        ChipGroup(
                            options = (if (isComplementary) complementaryDurationOptions else durationOptions)
                                .map { it.toString() to "$it min" },
                            selected = setOf(duration.toString()),
                            onToggle = { duration = it.toInt() },
                        )
                    }
                }

                item { SectionTitle("Dias por semana") }
                item {
                    ChipGroup(
                        options = (if (isComplementary) 1..6 else 2..6).map { it.toString() to "${it}x" },
                        selected = setOf(daysPerWeek.toString()),
                        onToggle = { daysPerWeek = it.toInt() },
                    )
                }

                // H3-30: perfil/sexo/revisão/ambiente em "Mais opções", colapsado.
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                            ) { showMoreOptions = !showMoreOptions }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = if (showMoreOptions) "Menos opções" else "Mais opções",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Icon(
                            imageVector = if (showMoreOptions) {
                                Icons.Filled.KeyboardArrowUp
                            } else {
                                Icons.Filled.KeyboardArrowDown
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (showMoreOptions) {
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
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next,
                                ),
                                isError = profileError != null && bodyWeightKgText.isNotBlank(),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = heightCmText,
                                onValueChange = { heightCmText = it; profileError = null },
                                label = { Text("Altura (cm)") },
                                supportingText = { Text("Opcional") },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(onDone = { }),
                                isError = profileError != null && heightCmText.isNotBlank(),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "Sexo (opcional)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ChipGroup(
                                options = sexOptions,
                                selected = sexChoice?.let { setOf(it) } ?: emptySet(),
                                onToggle = { value -> sexChoice = if (value == sexChoice) null else value },
                            )
                            Text(
                                text = "Omitir é o padrão. Você pode limpar a seleção quando quiser.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item { SectionTitle("Revisar o plano em") }
                    item {
                        ChipGroup(
                            options = reviewOptions.map { it.toString() to "$it semanas" },
                            selected = setOf(reviewWeeks.toString()),
                            onToggle = { reviewWeeks = it.toInt() },
                        )
                    }
                    item { SectionTitle("Ambiente de treino") }
                    item {
                        ChipGroup(
                            options = environmentOptions,
                            selected = setOf(environment),
                            onToggle = { environment = it },
                        )
                    }
                }
                if (savedBodyWeightKg != null || savedHeightCm != null || savedSex != null) {
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
                                savedSex?.let { value ->
                                    val label = sexOptions.firstOrNull { it.first == value }?.second
                                    if (label != null) {
                                        Text(text = "Sexo: $label", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                if (savedBodyWeightKg != null && savedHeightCm != null) {
                                    val bmi = savedBodyWeightKg / ((savedHeightCm / 100.0) * (savedHeightCm / 100.0))
                                    Text(text = "IMC: ${"%.1f".format(bmi)}", style = MaterialTheme.typography.bodyMedium)
                                }
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

        // H3-30: overlay de geração — progresso honesto com tempo decorrido e
        // cancelar, no lugar do spinner de 18 dp escondido no botão.
        if (isGenerating) {
            GenerateOverlay(onCancel = onCancel)
        }
    }
}

/** Overlay modal da geração: tempo decorrido, expectativa honesta e Cancelar. */
@Composable
private fun GenerateOverlay(onCancel: () -> Unit) {
    var elapsedSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            elapsedSeconds++
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                // O scrim consome toques fora do cartão.
            },
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.padding(32.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text(
                    text = "Gerando semana…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Tempo decorrido: ${elapsedSeconds}s",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "A IA pode levar até 90 s.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text("Cancelar")
                }
            }
        }
    }
}
