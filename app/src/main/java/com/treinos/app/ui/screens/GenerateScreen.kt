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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.treinos.app.ui.ProfileValidator.ProfileField
import com.treinos.app.ui.UiError
import com.treinos.app.ui.formatDecimal
import com.treinos.app.ui.parse5k
import com.treinos.app.ui.components.BusyButton
import com.treinos.app.ui.components.ChipGroup
import com.treinos.app.ui.components.ErrorState
import com.treinos.app.ui.components.ScreenTopBar
import com.treinos.app.ui.components.SectionTitle
import androidx.compose.ui.res.stringResource
import com.treinos.app.R
import kotlinx.coroutines.delay

// H3-52: as opções guardam o valor do contrato + o recurso do rótulo; o texto
// é resolvido na composição (`stringResource`) para seguir o idioma do app.
private val goalOptions = listOf(
    "hypertrophy" to R.string.generate_goal_hypertrophy,
    "strength" to R.string.generate_goal_strength,
    "endurance" to R.string.generate_goal_endurance,
    "power" to R.string.generate_goal_power,
    "fat_loss" to R.string.generate_goal_fat_loss,
    "general" to R.string.generate_goal_general,
)

private val levelOptions = listOf(
    "beginner" to R.string.generate_level_beginner,
    "intermediate" to R.string.generate_level_intermediate,
    "advanced" to R.string.generate_level_advanced,
)

private val equipmentOptions = listOf(
    "body only" to R.string.generate_equipment_body_only,
    "dumbbell" to R.string.generate_equipment_dumbbell,
    "barbell" to R.string.generate_equipment_barbell,
    "cable" to R.string.generate_equipment_cable,
    "machine" to R.string.generate_equipment_machine,
    "kettlebells" to R.string.generate_equipment_kettlebells,
    "bands" to R.string.generate_equipment_bands,
    "other" to R.string.generate_equipment_other,
)

private val muscleOptions = listOf(
    "chest" to R.string.generate_muscle_chest,
    "back" to R.string.generate_muscle_back,
    "legs" to R.string.generate_muscle_legs,
    "shoulders" to R.string.generate_muscle_shoulders,
    "biceps" to R.string.generate_muscle_biceps,
    "triceps" to R.string.generate_muscle_triceps,
    "abs" to R.string.generate_muscle_abs,
    "glutes" to R.string.generate_muscle_glutes,
)

private val durationOptions = listOf(30, 45, 60, 90)

/** Durações do modo complementar (H3-16): sessões curtas, 15–45 min. */
private val complementaryDurationOptions = listOf(15, 20, 30, 45)
private val reviewOptions = listOf(2, 4, 6, 8)

/** Rótulos localizados → valores de contrato (H3-7); o valor escolhido é o que viaja no request. */
private val sexOptions = listOf(
    "female" to R.string.generate_sex_female,
    "male" to R.string.generate_sex_male,
    "other" to R.string.generate_sex_other,
    "undisclosed" to R.string.generate_sex_undisclosed,
)

private val environmentOptions = listOf(
    "gym" to R.string.generate_environment_gym,
    "home" to R.string.generate_environment_home,
    "bodyweight" to R.string.generate_environment_bodyweight,
)

private val modalityTypes = listOf(
    "strength" to R.string.generate_modality_strength,
    "run" to R.string.generate_modality_run,
    "mobility" to R.string.generate_modality_mobility,
)

/** Rótulos localizados para as opções (`ChipGroup` recebe texto pronto). */
@Composable
private fun labeledOptions(options: List<Pair<String, Int>>): List<Pair<String, String>> =
    options.map { (value, resId) -> value to stringResource(resId) }

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
    var profileErrors by remember { mutableStateOf(ProfileValidator.validateFields(null, null, null)) }
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
        !isComplementary -> stringResource(R.string.generate_hint_no_modality)
        !hasFocusOrAlternativeEnvironment ->
            stringResource(R.string.generate_hint_focus_or_environment)
        else -> complementaryValidation?.error ?: stringResource(R.string.generate_hint_adjust)
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
            topBar = { ScreenTopBar(title = stringResource(R.string.screen_generate), onBack = onBack) },
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
                        BusyButton(
                            busy = isGenerating,
                            idleText = if (isComplementary) {
                                stringResource(R.string.generate_btn_generate_extra)
                            } else {
                                stringResource(R.string.generate_btn_generate_main)
                            },
                            busyText = stringResource(R.string.generate_btn_generating),
                            onClick = {
                                val validation = ProfileValidator.validate(
                                    bodyWeightKgText,
                                    heightCmText,
                                    sexChoice,
                                )
                                if (!validation.valid) {
                                    profileErrors = ProfileValidator.validateFields(bodyWeightKgText, heightCmText, sexChoice)
                                    showMoreOptions = true
                                    return@BusyButton
                                }
                                profileErrors = ProfileValidator.validateFields(bodyWeightKgText, heightCmText, sexChoice)
                                if (!canGenerate) {
                                    return@BusyButton
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
                            enabled = canGenerate && profileErrors.errors.isEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.generate_info),
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

                item { SectionTitle(stringResource(R.string.generate_section_what)) }
                item {
                    ChipGroup(
                        options = listOf(
                            "main" to stringResource(R.string.generate_plan_main),
                            "extra" to stringResource(R.string.generate_plan_extra),
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
                            stringResource(R.string.generate_plan_extra_desc)
                        } else {
                            stringResource(R.string.generate_plan_main_desc)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (!isComplementary) {
                    item { SectionTitle(stringResource(R.string.generate_modalities_section)) }
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            modalityTypes.forEach { (type, labelRes) ->
                                key(type) {
                                    val draft = modalityDrafts[type] ?: ModalityDraft(type = type)
                                    var included by remember(type) { mutableStateOf(draft.included) }
                                    var sessions by remember(type) { mutableStateOf(draft.sessionsPerWeek) }
                                    var sessionsText by remember(type) { mutableStateOf(draft.sessionsPerWeek.toString()) }
                                    var recent5kText by remember(type) {
                                        mutableStateOf(draft.recent5kSeconds?.toString() ?: "")
                                    }
                                    var focusText by remember(type) { mutableStateOf(draft.focus ?: "") }
                                    val label = stringResource(labelRes)
                                    val includeContentDesc = stringResource(R.string.generate_include_label, label)
                                    // H3-52: estados de acessibilidade resolvidos na
                                    // composição — o bloco de semântica não é composable.
                                    val stateOn = stringResource(R.string.common_state_on)
                                    val stateOff = stringResource(R.string.common_state_off)

                                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .minimumInteractiveComponentSize()
                                                    .toggleable(
                                                        value = included,
                                                        role = Role.Checkbox,
                                                        onValueChange = { novo ->
                                                            included = novo
                                                            modalityDrafts = modalityDrafts +
                                                                (type to draft.copy(included = novo))
                                                        },
                                                    )
                                                        .semantics {
                                                            stateDescription = if (included) stateOn else stateOff
                                                            contentDescription = includeContentDesc
                                                        },
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
                                                        .clearAndSetSemantics { }
                                                )
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                Text(
                                                    text = stringResource(R.string.generate_sessions_week_short, draft.sessionsPerWeek),
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
                                                        value = sessionsText,
                                                        onValueChange = {
                                                            sessionsText = it
                                                            val v = if (it.isBlank()) sessions else (it.toIntOrNull() ?: sessions)
                                                            sessions = v.coerceIn(1, 7)
                                                            val updated = draft.copy(included = true, sessionsPerWeek = sessions)
                                                            modalityDrafts = modalityDrafts + (type to updated)
                                                        },
                                                        label = { Text(stringResource(R.string.generate_sessions_week_label)) },
                                                        supportingText = { Text(stringResource(R.string.generate_sessions_week_support)) },
                                                        keyboardOptions = KeyboardOptions(
                                                            keyboardType = KeyboardType.Number,
                                                            imeAction = ImeAction.Done,
                                                        ),
                                                        isError = sessions < 1 || sessions > 7,
                                                        modifier = Modifier
                                                            .weight(1f),
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
                                                                    recent5kSeconds = parse5k(it),
                                                                )
                                                                modalityDrafts = modalityDrafts + (type to updated)
                                                            },
                                                            label = { Text(stringResource(R.string.generate_benchmark5k_label)) },
                                                            supportingText = { Text(stringResource(R.string.generate_benchmark5k_support)) },
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
                                                            label = { Text(stringResource(R.string.generate_focus_label)) },
                                                            supportingText = { Text(stringResource(R.string.common_optional)) },
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
                            text = stringResource(R.string.generate_swim_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }

                if (strengthIncluded) {
                    item { SectionTitle(stringResource(R.string.generate_goal_section)) }
                    item {
                        ChipGroup(
                            options = labeledOptions(goalOptions),
                            selected = setOf(goal),
                            onToggle = { goal = it },
                        )
                    }

                    item { SectionTitle(stringResource(R.string.generate_level_section)) }
                    item {
                        ChipGroup(
                            options = labeledOptions(levelOptions),
                            selected = setOf(level),
                            onToggle = { level = it },
                        )
                    }

                    item { SectionTitle(stringResource(R.string.generate_equipment_section)) }
                    item {
                        ChipGroup(
                            options = labeledOptions(equipmentOptions),
                            selected = equipment,
                            onToggle = { value ->
                                equipment = if (value in equipment) equipment - value else equipment + value
                            },
                        )
                    }

                    item {
                        SectionTitle(
                            if (isComplementary) stringResource(R.string.generate_focus_exclusive_section)
                            else stringResource(R.string.generate_muscle_section)
                        )
                    }
                    if (isComplementary) {
                        item {
                            Text(
                                text = stringResource(R.string.generate_focus_exclusive_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item {
                        ChipGroup(
                            options = labeledOptions(muscleOptions),
                            selected = muscles,
                            onToggle = { value ->
                                muscles = if (value in muscles) muscles - value else muscles + value
                            },
                        )
                    }
                    if (!isComplementary && muscles.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.generate_muscle_groups_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    item { SectionTitle(stringResource(R.string.generate_duration_section)) }
                    item {
                        ChipGroup(
                            options = (if (isComplementary) complementaryDurationOptions else durationOptions)
                                .map { it.toString() to stringResource(R.string.generate_duration_min, it) },
                            selected = setOf(duration.toString()),
                            onToggle = { duration = it.toInt() },
                        )
                    }
                }

                    item { SectionTitle(stringResource(R.string.generate_days_section)) }
                    item {
                        ChipGroup(
                            options = (if (isComplementary) 1..6 else 2..6)
                                .map { it.toString() to stringResource(R.string.generate_days_x, it) },
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
                            text = if (showMoreOptions) stringResource(R.string.generate_less_options) else stringResource(R.string.generate_more_options),
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
                    item { SectionTitle(stringResource(R.string.generate_profile_section)) }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = bodyWeightKgText,
                                onValueChange = { bodyWeightKgText = it; profileErrors = ProfileValidator.validateFields(it, heightCmText, sexChoice) },
                                label = { Text(stringResource(R.string.generate_weight_label)) },
                                supportingText = profileErrors.errors[ProfileField.Weight]?.let { { Text(it) } },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next,
                                ),
                                isError = profileErrors.errors.containsKey(ProfileField.Weight),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = heightCmText,
                                onValueChange = { heightCmText = it; profileErrors = ProfileValidator.validateFields(bodyWeightKgText, it, sexChoice) },
                                label = { Text(stringResource(R.string.generate_height_label)) },
                                supportingText = profileErrors.errors[ProfileField.Height]?.let { { Text(it) } },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(onDone = { }),
                                isError = profileErrors.errors.containsKey(ProfileField.Height),
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
                                text = stringResource(R.string.generate_sex_section),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ChipGroup(
                                options = labeledOptions(sexOptions),
                                selected = sexChoice?.let { setOf(it) } ?: emptySet(),
                                onToggle = { value -> sexChoice = if (value == sexChoice) null else value },
                            )
                            Text(
                                text = stringResource(R.string.generate_sex_omit),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item { SectionTitle(stringResource(R.string.generate_review_section)) }
                    item {
                        ChipGroup(
                            options = reviewOptions.map { it.toString() to stringResource(R.string.generate_review_weeks, it) },
                            selected = setOf(reviewWeeks.toString()),
                            onToggle = { reviewWeeks = it.toInt() },
                        )
                    }
                    item { SectionTitle(stringResource(R.string.generate_environment_section)) }
                    item {
                        ChipGroup(
                            options = labeledOptions(environmentOptions),
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
                                    text = stringResource(R.string.generate_profile_saved),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                savedBodyWeightKg?.let {
                                    Text(
                                        text = stringResource(R.string.generate_profile_weight, it.toString()),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                savedHeightCm?.let {
                                    Text(
                                        text = stringResource(R.string.generate_profile_height, it.toString()),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                savedSex?.let { value ->
                                    val label = sexOptions
                                        .firstOrNull { it.first == value }
                                        ?.second
                                        ?.let { stringResource(it) }
                                    if (label != null) {
                                        Text(
                                            text = stringResource(R.string.generate_profile_sex, label),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                                if (savedBodyWeightKg != null && savedHeightCm != null) {
                                    val bmi = savedBodyWeightKg / ((savedHeightCm / 100.0) * (savedHeightCm / 100.0))
                                    Text(
                                        text = stringResource(R.string.generate_profile_imc, formatDecimal(bmi, 1)),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.generate_ai_fallback),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}
