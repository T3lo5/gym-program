package com.treinos.app.widget

import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.WorkoutDayEntity
import com.treinos.app.data.local.WorkoutDayWithExercises
import com.treinos.app.data.local.WorkoutPlanEntity
import com.treinos.app.data.local.WorkoutPlanWithDetails
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Testes JVM puros do modelo do widget (H4-14): decisão de conteúdo e escolha
 * do plano, sem Android.
 */
class WidgetContentTest {

    private fun sessao(dayName: String, status: String = "in_progress") = SessionWithExercises(
        session = com.treinos.app.data.local.WorkoutSessionEntity(
            dayName = dayName,
            status = status,
        ),
        exercises = emptyList(),
    )

    private fun dia(
        name: String,
        weekday: Int?,
        isRestDay: Boolean = false,
        modality: String = "strength",
    ) = WorkoutDayWithExercises(
        day = WorkoutDayEntity(
            planId = 1,
            name = name,
            weekday = weekday,
            isRestDay = isRestDay,
            modality = modality,
        ),
        exercises = emptyList(),
    )

    private fun plano(vararg dias: WorkoutDayWithExercises, kind: String = "main") =
        WorkoutPlanWithDetails(
            plan = WorkoutPlanEntity(name = "Plano $kind", kind = kind),
            days = dias.toList(),
        )

    @Test
    fun sessao_em_andamento_vence_treino_de_hoje() {
        val plano = plano(dia("Push", weekday = 1))
        val resultado = widgetContent(
            inProgress = sessao("Push em andamento"),
            plan = plano,
            todayWeekday = 1,
        )
        assertEquals(WidgetContent.Resume("Push em andamento"), resultado)
    }

    @Test
    fun sem_sessao_com_treino_hoje_inicia() {
        val resultado = widgetContent(
            inProgress = null,
            plan = plano(dia("Push", weekday = 1, modality = "strength")),
            todayWeekday = 1,
        )
        assertEquals(WidgetContent.Start("Push", "strength"), resultado)
    }

    @Test
    fun sem_sessao_dia_de_descanso_mostra_proximo() {
        val plano = plano(
            dia("Descanso", weekday = 1, isRestDay = true),
            dia("Pull", weekday = 2),
        )
        val resultado = widgetContent(inProgress = null, plan = plano, todayWeekday = 1)
        assertEquals(WidgetContent.Next("Pull"), resultado)
    }

    @Test
    fun plano_sem_weekday_mostra_descanso() {
        val plano = plano(dia("Flexível", weekday = null))
        val resultado = widgetContent(inProgress = null, plan = plano, todayWeekday = 3)
        assertEquals(WidgetContent.Rest, resultado)
    }

    @Test
    fun sem_plano_mostra_abrir_app() {
        val resultado = widgetContent(inProgress = null, plan = null, todayWeekday = 1)
        assertEquals(WidgetContent.NoPlan, resultado)
    }

    @Test
    fun plano_principal_tem_prioridade() {
        val extra = plano(dia("Extra Dia", weekday = 1), kind = "extra")
        val main = plano(dia("Main Dia", weekday = 1), kind = "main")

        assertEquals(main, widgetPlan(listOf(extra, main)))
        assertEquals(main, widgetPlan(listOf(main, extra)))
        assertEquals(extra, widgetPlan(listOf(extra)))
        assertEquals(null, widgetPlan(emptyList()))

        // O conteúdo renderizado é o do plano escolhido.
        val resultado = widgetContent(null, widgetPlan(listOf(extra, main)), todayWeekday = 1)
        assertEquals(WidgetContent.Start("Main Dia", "strength"), resultado)
    }

    @Test
    fun day_name_vazio_preserva_modality() {
        // Regra 5: dayName vazio é repassado como veio; o fallback é da UI.
        val start = widgetContent(
            inProgress = null,
            plan = plano(dia("", weekday = 1, modality = "run")),
            todayWeekday = 1,
        )
        assertEquals(WidgetContent.Start("", "run"), start)

        val resume = widgetContent(
            inProgress = sessao(""),
            plan = plano(dia("Push", weekday = 1)),
            todayWeekday = 1,
        )
        assertEquals(WidgetContent.Resume(""), resume)
    }
}
