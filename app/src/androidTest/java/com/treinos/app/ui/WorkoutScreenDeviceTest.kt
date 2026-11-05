package com.treinos.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionExerciseWithSets
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.WorkoutSessionEntity
import com.treinos.app.ui.screens.WorkoutScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Execução de treino no device/emulador — é aqui que o toque real, o LazyColumn e o
 * SQLite do aparelho aparecem, coisas que o gate (JVM, sem emulador) não alcança.
 *
 * Fora do gate `./scripts/verify.sh`.
 * Rode com: ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class WorkoutScreenDeviceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sessao = SessionWithExercises(
        session = WorkoutSessionEntity(
            id = 1,
            planId = 1,
            dayId = 10,
            planName = "Plano Teste",
            dayName = "Treino A",
            status = "in_progress",
            startedAt = 1_760_000_000_000L,
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
                    completedSets = 0,
                    restSeconds = 90,
                ),
                sets = emptyList(),
            ),
        ),
    )

    @Test
    fun registra_serie_no_dispositivo() {
        var registrada: Triple<Int?, Double?, Double?>? = null
        composeRule.setContent {
            WorkoutScreen(
                session = sessao,
                restRemaining = null,
                restTotal = null,
                onLeave = { },
                onLogSet = { _, reps, weight, rpe -> registrada = Triple(reps, weight, rpe) },
                onSkipRest = { },
                onFinish = { },
                hasInProgressSession = true,
            )
        }

        composeRule.onNodeWithText("Registrar série 1").performClick()
        composeRule.onNodeWithText("Série 1 — Supino reto").assertExists()
        composeRule.onNodeWithText("Repetições").performTextInput("12")
        composeRule.onNodeWithText("Carga (kg)").performTextInput("60")
        composeRule.onNodeWithText("Salvar série").performClick()

        assertEquals(Triple(12, 60.0, null), registrada)
    }

    @Test
    fun timer_de_descanso_aparece() {
        var pulou = false
        composeRule.setContent {
            WorkoutScreen(
                session = sessao,
                restRemaining = 45,
                restTotal = 90,
                onLeave = { },
                onLogSet = { _, _, _, _ -> },
                onSkipRest = { pulou = true },
                onFinish = { },
                hasInProgressSession = true,
            )
        }

        composeRule.onNodeWithText("Descanso: 00:45").assertExists()
        composeRule.onNodeWithText("Pular descanso").performClick()
        assertTrue(pulou)
    }
}
