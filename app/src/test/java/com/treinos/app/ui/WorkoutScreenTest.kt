package com.treinos.app.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionExerciseWithSets
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.WorkoutSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkoutScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Sessão de força com um exercício e uma série 60 kg × 10 registrada. */
    private fun sessao(comSets: Boolean = true): SessionWithExercises {
        val sets = if (comSets) {
            listOf(
                SetLogEntity(
                    id = 1000,
                    exerciseLogId = 100,
                    setNumber = 1,
                    reps = 10,
                    weightKg = 60.0,
                    rpe = null,
                ),
            )
        } else {
            emptyList()
        }
        return SessionWithExercises(
            session = WorkoutSessionEntity(
                id = 1,
                planId = 1,
                dayId = 10,
                planName = "Plano Teste",
                dayName = "Treino A",
                status = "in_progress",
            ),
            exercises = listOf(
                SessionExerciseWithSets(
                    exercise = SessionExerciseEntity(
                        id = 100,
                        sessionId = 1,
                        exerciseId = "Barbell_Bench_Press",
                        exerciseName = "Supino reto",
                        exerciseOrder = 0,
                        plannedSets = 3,
                        completedSets = 1,
                        restSeconds = 90,
                    ),
                    sets = sets,
                ),
            ),
        )
    }

    private fun setContent(
        session: SessionWithExercises?,
        restRemaining: Int? = null,
        restTotal: Int? = null,
        restPaused: Boolean = false,
        onLoggedSet: (Int?, Double?, Double?) -> Unit = { _, _, _ -> },
    ) {
        composeRule.setContent {
            WorkoutScreen(
                session = session,
                restRemaining = restRemaining,
                restTotal = restTotal,
                onLeave = { },
                onLogSet = { _, reps, weight, rpe -> onLoggedSet(reps, weight, rpe) },
                onSkipRest = { },
                onFinish = { },
                hasInProgressSession = false,
                restPaused = restPaused,
            )
        }
    }

    @Test
    fun sem_serie_anterior_o_botao_repetir_nao_existe() {
        setContent(session = sessao(comSets = false))

        composeRule.onNodeWithText("Nenhuma série registrada ainda.").assertIsDisplayed()
        composeRule.onNodeWithText("Repetir última série", substring = true).assertDoesNotExist()
    }

    @Test
    fun descanso_zerado_entra_em_destaque_e_so_pular() {
        setContent(session = sessao(), restRemaining = 0, restTotal = 90)

        composeRule.onNodeWithText("Descanso concluído. Bora para a próxima série.").assertIsDisplayed()
        composeRule.onNodeWithText("Pular").assertIsDisplayed()
        // Zerado não faz sentido estender nem pausar.
        composeRule.onNodeWithText("+15 s").assertDoesNotExist()
        composeRule.onNodeWithText("Pausar").assertDoesNotExist()
    }

    @Test
    fun descanso_pausado_mostra_continuar() {
        setContent(session = sessao(), restRemaining = 45, restTotal = 90, restPaused = true)

        composeRule.onNodeWithText("Descanso pausado: 00:45").assertIsDisplayed()
        composeRule.onNodeWithText("Continuar").assertIsDisplayed()
    }

}
