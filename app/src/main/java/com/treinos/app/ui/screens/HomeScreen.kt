package com.treinos.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.treinos.app.data.local.MANUAL_SESSION_SOURCE
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.WorkoutDayWithExercises
import com.treinos.app.data.local.WorkoutPlanWithDetails
import com.treinos.app.data.media.ExerciseMedia
import com.treinos.app.ui.ActivityModality
import com.treinos.app.ui.ModalityTab
import com.treinos.app.ui.PlanCycle
import com.treinos.app.ui.PendingGeneration
import com.treinos.app.ui.SessionKind
import com.treinos.app.ui.TodaySession
import com.treinos.app.ui.defaultModalityTab
import com.treinos.app.ui.groupSessionsByModality
import com.treinos.app.ui.formatDateTime
import com.treinos.app.ui.formatDecimal
import com.treinos.app.ui.formatDistanceMeters
import com.treinos.app.ui.formatDuration
import com.treinos.app.ui.formatPacePerKm
import com.treinos.app.ui.formatReviewDate
import com.treinos.app.ui.formatRunDistanceKm
import com.treinos.app.ui.isToday
import com.treinos.app.ui.goalLabel
import com.treinos.app.ui.planVolumeLabel
import com.treinos.app.ui.progression.REVIEW_POSTPONE_WEEKS
import com.treinos.app.ui.reviewIsDue
import com.treinos.app.ui.reviewStateDescription
import com.treinos.app.ui.todaySessionSelector
import com.treinos.app.ui.UiState
import com.treinos.app.ui.components.BusyButton
import com.treinos.app.ui.components.ExerciseImage
import com.treinos.app.ui.components.LoadingState
import com.treinos.app.ui.components.ScreenTopBar
import com.treinos.app.ui.components.SectionTitle
import com.treinos.app.ui.components.StatusText
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.treinos.app.R

@Composable
fun HomeScreen(
    plan: WorkoutPlanWithDetails?,
    inProgressSession: SessionWithExercises?,
    sessions: List<SessionWithExercises> = emptyList(),
    planCycle: PlanCycle? = null,
    showDeloadNotice: Boolean = false,
    exerciseMedia: Map<String, ExerciseMedia> = emptyMap(),
    onGenerate: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onStartDay: (Long) -> Unit,
    onResume: () -> Unit,
    onAbandonWorkout: () -> Unit = {},
    onLogActivity: () -> Unit,
    onOpenMobility: () -> Unit = {},
    onPostponeReview: (Long) -> Unit = {},
    onDismissDeloadNotice: () -> Unit = {},
    plans: List<WorkoutPlanWithDetails> = emptyList(),
    plansState: UiState<List<WorkoutPlanWithDetails>> = UiState.Content(emptyList()),
    onSelectPlan: (Long) -> Unit = {},
    onDeletePlan: (Long) -> Unit = {},
    // H3-30: motivo do último fallback local, já traduzido; o selo do plano
    // gerado localmente cita o motivo até a próxima geração da IA.
    fallbackReasonLabel: String? = null,
    // H3-40: envelope da geração em andamento e ações do card pendente.
    pendingGeneration: PendingGeneration? = null,
    pendingGenerationStatus: String? = null,
    onRefreshPending: () -> Unit = {},
    onCancelPending: () -> Unit = {},
    onRetryPending: () -> Unit = {},
    onDismissPending: () -> Unit = {},
) {
    // H3-29: Loading até o primeiro emit do Room — o empty state ("Nenhum
    // treino ainda") só aparece quando o banco realmente está vazio. Enquanto
    // `activePlan` (um combine) ainda não convergiu com planos no banco, segue
    // carregando em vez de piscar o empty state.
    val loadedPlans = (plansState as? UiState.Content)?.data.orEmpty()
    if (plansState is UiState.Loading || (plan == null && loadedPlans.isNotEmpty())) {
        LoadingState(modifier = Modifier.fillMaxSize())
        return
    }
    val tabsByModality = remember(plan) {
        plan?.let { groupSessionsByModality(it.days) } ?: emptyMap()
    }
    val presentTabs = tabsByModality.keys.filter { tab ->
        tabsByModality[tab]?.isNotEmpty() == true
    }
    val todayWeekday = java.time.LocalDate.now()
        .get(java.time.temporal.ChronoField.DAY_OF_WEEK)
    val defaultTab = remember(plan, todayWeekday) {
        plan?.let { defaultModalityTab(it.days, todayWeekday) }
    }
    var selectedTab by remember(plan) { mutableStateOf<ModalityTab?>(defaultTab) }
    var showDeletePlanDialog by remember { mutableStateOf(false) }
    var showPlanMenu by remember { mutableStateOf(false) }
    // H3-28: o treino de hoje vem de um seletor puro (testável em JVM).
    val todaySession = remember(plan, todayWeekday) {
        plan?.let { todaySessionSelector(it.days, todayWeekday) }
    }

    Scaffold(
        topBar = {
            ScreenTopBar(
                title = stringResource(R.string.screen_home),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // H3-40: card de geração pendente no topo — em andamento ou falhou.
            pendingGeneration?.let {
                item {
                    PendingGenerationCard(
                        envelope = it,
                        status = pendingGenerationStatus,
                        onRefresh = onRefreshPending,
                        onCancel = onCancelPending,
                        onRetry = onRetryPending,
                        onDismiss = onDismissPending,
                    )
                }
            }

            if (inProgressSession != null) {
                item {
                    InProgressCard(
                        session = inProgressSession,
                        onResume = onResume,
                        onAbandon = onAbandonWorkout,
                    )
                }
            }

            if (plan == null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.home_empty_title),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = stringResource(R.string.home_empty_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = onGenerate, modifier = Modifier.minimumInteractiveComponentSize()) {
                                Text(stringResource(R.string.home_empty_cta))
                            }
                        }
                    }
                }

                item { HomeQuickActions(onGenerate, onLogActivity, onOpenMobility) }

                // H3-28: os parágrafos de rodapé só fazem sentido no empty state.
                item {
                    Text(
                                text = stringResource(R.string.home_footnote_activity),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                item {
                    Text(
                                text = stringResource(R.string.home_footnote_mobility),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                item {
                    Text(
                                text = stringResource(R.string.home_footnote_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // H3-28: herói do dia logo após o InProgressCard — o treino de
                // hoje fica acima da dobra, com ação direta.
                if (todaySession != null) {
                    item { TodayHeroCard(todaySession, onStartDay) }
                }

                item { HomeQuickActions(onGenerate, onLogActivity, onOpenMobility) }

                if (plans.size > 1 || plan.plan.kind == "extra") {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            plans.forEach { option ->
                                FilterChip(
                                    selected = option.plan.id == plan.plan.id,
                                    onClick = { onSelectPlan(option.plan.id) },
                                    label = { Text(option.plan.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                )
                            }
                            // H3-28: ação destrutiva sai do corpo da lista e vai
                            // para o overflow do seletor (a confirmação se mantém).
                            if (plan.plan.kind == "extra") {
                                Box {
                                    IconButton(
                                        onClick = { showPlanMenu = true },
                                        modifier = Modifier.minimumInteractiveComponentSize(),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = stringResource(R.string.home_plan_more_options),
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showPlanMenu,
                                        onDismissRequest = { showPlanMenu = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    stringResource(R.string.home_plan_delete_menu),
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                            },
                                            onClick = {
                                                showPlanMenu = false
                                                showDeletePlanDialog = true
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item { ActivePlanCard(plan, fallbackReasonLabel) }

                if (reviewIsDue(plan.plan.nextReviewAt)) {
                    item {
                        ReviewPlanCard(
                            reviewDate = formatReviewDate(plan.plan.nextReviewAt).orEmpty(),
                            onGenerate = onGenerate,
                            onPostpone = { onPostponeReview(plan.plan.id) },
                        )
                    }
                }

                val cycle = planCycle
                if (showDeloadNotice && cycle != null) {
                    item {
                        DeloadNoticeCard(week = cycle.week, onDismiss = onDismissDeloadNotice)
                    }
                }

                if (presentTabs.isNotEmpty()) {
                    item {
                        TabRow(
                            selectedTabIndex = presentTabs.indexOf(selectedTab ?: presentTabs.first())
                                .coerceAtLeast(0),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            presentTabs.forEach { tab ->
                                Tab(
                                    selected = selectedTab == tab,
                                    onClick = { selectedTab = tab },
                                    text = {
                                        Text(when (tab) {
                                            ModalityTab.STRENGTH -> stringResource(R.string.home_tab_gym)
                                            ModalityTab.RUN -> stringResource(R.string.home_tab_run)
                                            ModalityTab.MOBILITY -> stringResource(R.string.home_tab_mobility)
                                        })
                                    },
                                )
                            }
                        }
                    }

                    val currentSessions = tabsByModality[selectedTab ?: presentTabs.first()].orEmpty()
                    val currentTab = selectedTab ?: presentTabs.first()
                    item {
                        val currentTabLabel = stringResource(tabLabel(currentTab))
                        SectionTitle(stringResource(R.string.home_sessions_of, currentTabLabel))
                    }
                    items(currentSessions, key = { it.day.id }) { day ->
                        ModalityDayCard(
                            day = day,
                            exerciseMedia = exerciseMedia,
                            isToday = day.day.weekday == todayWeekday,
                            onStart = { onStartDay(day.day.id) },
                        )
                    }
                } else {
                    item { SectionTitle(stringResource(R.string.home_workout_days)) }
                    items(plan.days.sortedBy { it.day.dayOrder }, key = { it.day.id }) { day ->
                        DayCard(
                            day = day,
                            exerciseMedia = exerciseMedia,
                            isToday = day.day.weekday == todayWeekday,
                            onStart = { onStartDay(day.day.id) },
                        )
                    }
                }

                val todayManual = sessions.filter {
                    it.session.source == MANUAL_SESSION_SOURCE && isToday(it.session.startedAt)
                }
                if (todayManual.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.home_today_activities)) }
                    items(todayManual, key = { it.session.id }) { session ->
                        ActivitySummaryCard(session)
                    }
                }

                // H3-28: com plano ativo, a Home termina na lista do dia —
                // sem botões empilhados e sem parágrafos de rodapé.
            }
        }
    }

    if (showDeletePlanDialog && plan != null) {
        AlertDialog(
            onDismissRequest = { showDeletePlanDialog = false },
            title = { Text(stringResource(R.string.home_plan_delete_title)) },
            text = {
                Text(
                    stringResource(R.string.home_plan_delete_body, plan.plan.name)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeletePlanDialog = false
                        onDeletePlan(plan.plan.id)
                    },
                ) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePlanDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/**
 * H3-40: card de geração pendente no topo da Home.
 * - `pending`/`running`: mostra "Atualizar" (consulta o status) e "Cancelar".
 * - `failed`: mostra "Tentar de novo" (reenvia o request do envelope) e "Descartar".
 */
@Composable
private fun PendingGenerationCard(
    envelope: PendingGeneration,
    status: String?,
    onRefresh: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isFailed = status == "failed"
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isFailed) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer) else CardDefaults.cardColors(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (isFailed) stringResource(R.string.home_pending_titulo_falhou) else stringResource(R.string.home_pending_titulo_andamento),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (!isFailed) {
                Text(
                    text = stringResource(R.string.home_pending_subtitulo_andamento),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (status == "pending" || status == "running") {
                    BusyButton(
                        busy = false,
                        idleText = stringResource(R.string.home_pending_btn_atualizar),
                        busyText = stringResource(R.string.common_loading),
                        onClick = onRefresh,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.home_pending_btn_cancelar))
                    }
                } else if (isFailed) {
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.home_pending_btn_tentar_de_novo))
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.home_pending_btn_descartar))
                    }
                } else {
                    StatusText(
                        text = stringResource(R.string.home_pending_msg_sem_plano),
                        isSuccess = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun tabLabel(tab: ModalityTab): Int = when (tab) {
    ModalityTab.STRENGTH -> R.string.home_tab_label_gym
    ModalityTab.RUN -> R.string.home_tab_label_run
    ModalityTab.MOBILITY -> R.string.home_tab_label_mobility
}

private fun modalityTabOf(kind: SessionKind): ModalityTab = when (kind) {
    SessionKind.STRENGTH -> ModalityTab.STRENGTH
    SessionKind.RUN -> ModalityTab.RUN
    SessionKind.MOBILITY -> ModalityTab.MOBILITY
}


/**
 * H3-28: card-herói do dia. Com sessão hoje, mostra o nome e "Iniciar" direto;
 * em dia de descanso, aponta a próxima sessão do plano.
 */
@Composable
private fun TodayHeroCard(today: TodaySession, onStartDay: (Long) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (today) {
                is TodaySession.Today -> {
                    Text(
                        text = stringResource(R.string.home_hero_today, today.day.day.name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = when (SessionKind.fromModality(today.day.day.modality)) {
                            SessionKind.RUN -> stringResource(R.string.home_hero_run_subtitle, today.day.exercises.size)
                            SessionKind.MOBILITY -> stringResource(R.string.home_hero_mobility_subtitle, today.day.exercises.size)
                            SessionKind.STRENGTH -> stringResource(
                                R.string.home_hero_strength_subtitle,
                                today.day.exercises.size,
                                if (today.day.exercises.size != 1) "s" else "",
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Button(
                        onClick = { onStartDay(today.day.day.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_start))
                    }
                }
                is TodaySession.Next -> {
                    Text(
                        text = stringResource(R.string.home_hero_rest),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.home_hero_next, today.day.day.name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                TodaySession.Rest -> Unit
            }
        }
    }
}

/** H3-28: ações secundárias em uma única linha compacta (ícone + rótulo curto). */
@Composable
private fun HomeQuickActions(
    onGenerate: () -> Unit,
    onLogActivity: () -> Unit,
    onOpenMobility: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HomeQuickAction(
            label = stringResource(R.string.home_quick_new_workout),
            icon = Icons.Default.Add,
            onClick = onGenerate,
            modifier = Modifier.weight(1f),
        )
        HomeQuickAction(
            label = stringResource(R.string.home_quick_activity),
            icon = Icons.Default.Edit,
            onClick = onLogActivity,
            modifier = Modifier.weight(1f),
        )
        HomeQuickAction(
            label = stringResource(R.string.home_quick_stretching),
            icon = Icons.Default.Favorite,
            onClick = onOpenMobility,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HomeQuickAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** H3-28: marca o card do dia de hoje na lista de sessões. */
@Composable
private fun TodayBadge() {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
                        text = stringResource(R.string.home_today),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun ModalityDayCard(
    day: WorkoutDayWithExercises,
    exerciseMedia: Map<String, ExerciseMedia>,
    isToday: Boolean = false,
    onStart: () -> Unit,
) {
    val kind = SessionKind.fromModality(day.day.modality)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = day.day.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (isToday) {
                        TodayBadge()
                    }
                    when (kind) {
                        SessionKind.RUN -> {
                            val distance = day.exercises.firstOrNull()?.targetDistanceMeters
                            val pace = day.exercises.firstOrNull()?.targetPaceSecondsPerKm
                            Text(
                                text = buildList {
                                    add(stringResource(R.string.home_blocks_count, day.exercises.size))
                                    distance?.let { add(formatDistanceMeters(it)) }
                                    pace?.let { add(com.treinos.app.ui.formatMinuteSecond(pace.toLong()) + "/km") }
                                }.joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        SessionKind.MOBILITY -> {
                            val duration = day.exercises.firstOrNull()?.targetDurationSeconds?.let { it / 60 }
                            Text(
                                text = stringResource(
                                    R.string.home_routines_count,
                                    day.exercises.size,
                                    duration ?: 0,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        SessionKind.STRENGTH -> {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.home_exercises_count,
                                    day.exercises.size,
                                    day.exercises.size,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (day.day.isRestDay) {
                Text(
                                text = stringResource(R.string.home_rest_day),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    day.exercises
                        .sortedBy { it.exerciseOrder }
                        .take(6)
                        .forEach { exercise ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                ExerciseImage(
                                    imageUrl = exerciseMedia[exercise.exerciseId]?.imageUrl,
                                    exerciseName = exercise.exerciseName.ifBlank { exercise.exerciseId },
                                    size = 36.dp,
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(1.dp),
                                ) {
                                    Text(
                                        text = exercise.exerciseName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (exercise.targetReps.isNotBlank()) {
                                        Text(
                                            text = stringResource(R.string.home_sets_by_reps, exercise.sets, exercise.targetReps),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                    } else if (exercise.sets > 0) {
                                        Text(
                                            text = pluralStringResource(
                                                R.plurals.home_sets_count,
                                                exercise.sets,
                                                exercise.sets,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                }
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).minimumInteractiveComponentSize(),
                ) {
                    Text(stringResource(R.string.home_start_modality, stringResource(tabLabel(modalityTabOf(kind)))))
                }
            }
        }
    }
}

@Composable
private fun InProgressCard(
    session: SessionWithExercises,
    onResume: () -> Unit,
    onAbandon: () -> Unit,
) {
    val done = session.exercises.sumOf { it.sets.size }
    var showAbandonConfirm by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
                            Text(
                                text = stringResource(R.string.home_in_progress),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(
                                    R.string.home_in_progress_detail,
                                    session.session.dayName,
                                    formatDateTime(session.session.startedAt),
                                    done,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
            Button(
                onClick = onResume,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_resume))
            }
            // H3-27: "Abandonar treino" também na Home, sempre com confirmação —
            // a sessão some do aparelho junto com as séries registradas.
            TextButton(
                onClick = { showAbandonConfirm = true },
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
            ) {
                Text(
                    text = stringResource(R.string.action_abandon),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
    if (showAbandonConfirm) {
        AlertDialog(
            onDismissRequest = { showAbandonConfirm = false },
            title = { Text(stringResource(R.string.home_abandon_title)) },
            text = {
                Text(
                    stringResource(R.string.home_abandon_body)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAbandonConfirm = false
                        onAbandon()
                    },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text(
                        stringResource(R.string.home_abandon_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showAbandonConfirm = false }, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun ActivePlanCard(
    plan: WorkoutPlanWithDetails,
    fallbackReasonLabel: String? = null,
) {
    val entity = plan.plan
    val review = formatReviewDate(entity.nextReviewAt)
    val due = reviewIsDue(entity.nextReviewAt)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                review?.let { stateDescription = reviewStateDescription(it, due) }
            },
        // H3-28: card neutro — o destaque da tela passa a ser o herói de hoje.
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = entity.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = planVolumeLabel(plan),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                // H3-30: quando o plano veio do fallback local, o selo cita o
                // motivo persistido — não some com o snackbar de ~4 s.
                text = when {
                    entity.source == "ai" -> stringResource(R.string.home_plan_ai)
                    fallbackReasonLabel != null -> stringResource(R.string.home_plan_local_reason, fallbackReasonLabel)
                    else -> stringResource(R.string.home_plan_local)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (review != null) {
                Text(
                                text = if (due) {
                                    stringResource(R.string.home_review_overdue, review)
                                } else {
                                    stringResource(R.string.home_review_next, review)
                                },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (due) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ReviewPlanCard(reviewDate: String, onGenerate: () -> Unit, onPostpone: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                if (reviewDate.isNotBlank()) stateDescription = reviewStateDescription(reviewDate, true)
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.home_review_card_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.home_review_card_body, reviewDate),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onGenerate,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text(stringResource(R.string.home_review_cta))
                }
                OutlinedButton(
                    onClick = onPostpone,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text(stringResource(R.string.home_review_postpone, REVIEW_POSTPONE_WEEKS))
                }
            }
        }
    }
}

@Composable
private fun DeloadNoticeCard(week: Int, onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.home_cycle_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.home_cycle_body, week),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.home_cycle_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Text(stringResource(R.string.common_got_it))
            }
        }
    }
}

@Composable
private fun DayCard(
    day: WorkoutDayWithExercises,
    exerciseMedia: Map<String, ExerciseMedia>,
    isToday: Boolean = false,
    onStart: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = day.day.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (isToday) {
                        TodayBadge()
                    }
                    Text(
                        text = pluralStringResource(
                            R.plurals.home_exercises_count,
                            day.exercises.size,
                            day.exercises.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (day.day.isRestDay) {
                Text(
                                text = stringResource(R.string.home_rest_day),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    day.exercises
                        .sortedBy { it.exerciseOrder }
                        .take(6)
                        .forEach { exercise ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                ExerciseImage(
                                    imageUrl = exerciseMedia[exercise.exerciseId]?.imageUrl,
                                    exerciseName = exercise.exerciseName.ifBlank { exercise.exerciseId },
                                    size = 36.dp,
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(1.dp),
                                ) {
                                    Text(
                                        text = exercise.exerciseName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (exercise.targetReps.isNotBlank()) {
                                        Text(
                                            text = stringResource(R.string.home_sets_by_reps, exercise.sets, exercise.targetReps),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                    } else if (exercise.sets > 0) {
                                        Text(
                                            text = pluralStringResource(
                                                R.plurals.home_sets_count,
                                                exercise.sets,
                                                exercise.sets,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                }
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).minimumInteractiveComponentSize(),
                ) {
                    Text(stringResource(R.string.action_start_workout))
                }
            }
        }
    }
}

@Composable
private fun ActivitySummaryCard(session: SessionWithExercises) {
    val modality = ActivityModality.fromStorageValue(session.session.modality)
    val title = session.session.title.ifBlank { session.session.dayName }.ifBlank { activityModalityLabel(modality) }
    // H3-31: corrida em km como o corredor pensa; demais modalidades em metros.
    val distance = if (modality == ActivityModality.RUN) {
        formatRunDistanceKm(session.session.totalDistanceMeters)
    } else {
        formatDistanceMeters(session.session.totalDistanceMeters)
    }
    val pace = when (modality) {
        ActivityModality.RUN ->
            formatPacePerKm(session.session.totalDurationSeconds, session.session.totalDistanceMeters)
        else -> null
    }
    val summary = buildList {
        add(formatDuration(session.session.totalDurationSeconds))
        distance?.let { add(it) }
        pace?.let { add(it) }
        session.session.sessionRpe?.let { add("RPE ${formatDecimal(it, 1)}") }
    }.joinToString(" • ")

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "$summary • ${formatDateTime(session.session.startedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
