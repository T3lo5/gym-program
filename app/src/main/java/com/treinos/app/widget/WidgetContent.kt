package com.treinos.app.widget

import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.WorkoutPlanWithDetails
import com.treinos.app.ui.TodaySession
import com.treinos.app.ui.todaySessionSelector

/**
 * Estado puro do widget de tela inicial (H4-14). Sem Android: quem renderiza
 * resolve os recursos de string; aqui só se decide O QUE mostrar.
 */
sealed interface WidgetContent {
    /** Treino em andamento: um toque retoma (mesmo extra do atalho H3-35). */
    data class Resume(val dayName: String) : WidgetContent

    /** Hoje tem sessão no plano: um toque inicia o treino do dia. */
    data class Start(val dayName: String, val modality: String) : WidgetContent

    /** Hoje é descanso, mas o plano tem próxima sessão: só abre o app. */
    data class Next(val dayName: String) : WidgetContent

    /** Plano sem sessão em dia fixo da semana (dias flexíveis): só abre o app. */
    data object Rest : WidgetContent

    /** Sem plano no aparelho: só abre o app. */
    data object NoPlan : WidgetContent
}

/**
 * Plano exibido pelo widget (H4-14): o plano principal (`kind == "main"`);
 * sem principal, o primeiro plano ativo. `_selectedPlanId` é só memória, então
 * o widget não tenta adivinhar a seleção da Home.
 */
fun widgetPlan(plans: List<WorkoutPlanWithDetails>): WorkoutPlanWithDetails? =
    plans.firstOrNull { it.plan.kind == "main" } ?: plans.firstOrNull()

/**
 * Decisão pura do conteúdo do widget (H4-14), a partir da sessão em
 * andamento, do plano ativo e do weekday de hoje (1=seg … 7=dom, o mesmo
 * esperado por `todaySessionSelector`, H3-28).
 *
 * Regras: sessão em andamento vence o treino de hoje; sem plano é `NoPlan`;
 * com plano, o seletor da H3-28 decide entre `Start`, `Next` e `Rest`.
 * `dayName` vazio é repassado como veio — o fallback de string é da UI.
 */
fun widgetContent(
    inProgress: SessionWithExercises?,
    plan: WorkoutPlanWithDetails?,
    todayWeekday: Int,
): WidgetContent {
    inProgress?.let { return WidgetContent.Resume(dayName = it.session.dayName) }
    if (plan == null) return WidgetContent.NoPlan
    return when (val today = todaySessionSelector(plan.days, todayWeekday)) {
        is TodaySession.Today ->
            WidgetContent.Start(dayName = today.day.day.name, modality = today.day.day.modality)
        is TodaySession.Next -> WidgetContent.Next(dayName = today.day.day.name)
        TodaySession.Rest -> WidgetContent.Rest
    }
}
