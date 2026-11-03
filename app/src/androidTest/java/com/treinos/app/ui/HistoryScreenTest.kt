package com.treinos.app.ui

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.SessionExerciseWithSets
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.WorkoutSessionEntity
import com.treinos.app.ui.screens.HistoryScreen
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Testes de UI do HistoryScreen com Compose + Robolectric.
 *
 * Toda a tela recebe dados falsos determinísticos montados no teste,
 * nunca o AppViewModel real.
 */
@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sessaoConcluida = SessionWithExercises(
        session = WorkoutSessionEntity(
            id = 1,
            planId = 1,
            dayId = 10,
            planName = "Plano Teste",
            dayName = "Treino A",
            status = "completed",
            startedAt = 1760000000000L,
            endedAt = 1760003600000L,
            totalDurationSeconds = 3600,
        ),
        exercises = listOf(
            SessionExerciseWithSets(
                exercise = com.treinos.app.data.local.SessionExerciseEntity(
                    id = 100,
                    sessionId = 1,
                    exerciseId = "Barbell_Bench_Press",
                    exerciseName = "Supino reto",
                    exerciseOrder = 0,
                    plannedSets = 3,
                    completedSets = 1,
                ),
                sets = listOf(
                    SetLogEntity(
                        id = 1000,
                        exerciseLogId = 100,
                        setNumber = 1,
                        reps = 10,
                        weightKg = 40.0,
                        rpe = 8.0,
                    )
                ),
            ),
        ),
    )

    @Before
    fun setUp() {
        // Nenhuma inicialização necessária além do composeRule
    }

    @Test
    fun mostraEstadoVazio() {
        composeRule.setContent {
            HistoryScreen(sessions = emptyList(), onBack = { })
        }

        composeRule.onNodeWithText("Nenhum treino registrado").assertExists()
    }

    @Test
    fun mostraSessaoConcluida() {
        composeRule.setContent {
            HistoryScreen(sessions = listOf(sessaoConcluida), onBack = { })
        }

        composeRule.onNodeWithText("Treino A").assertExists()
        composeRule.onNodeWithText("Concluído").assertExists()
        composeRule.onNodeWithText("Supino reto").assertExists()
    }

    @Test
    fun expandeERecolheSessao() {
        composeRule.setContent {
            HistoryScreen(sessions = listOf(sessaoConcluida), onBack = { })
        }

        // Inicialmente recolhido: o texto da série não aparece
        composeRule.onNodeWithText("Série 1").assertDoesNotExist()

        // Clica no card para expandir
        composeRule.onNodeWithText("Treino A").performClick()
        composeRule.onNodeWithText("Série 1").assertExists()

        // Clica novamente para recolher
        composeRule.onNodeWithText("Treino A").performClick()
        composeRule.onNodeWithText("Série 1").assertDoesNotExist()
    }

    @Test
    fun mostraMetricasDoMes() {
        composeRule.setContent {
            HistoryScreen(sessions = listOf(sessaoConcluida), onBack = { })
        }

        composeRule.onNodeWithText("1 h 00 min").assertExists()
        composeRule.onNodeWithText("1 série(s)").assertExists()
    }
}
