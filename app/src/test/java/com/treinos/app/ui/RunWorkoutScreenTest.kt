package com.treinos.app.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.WorkoutSessionEntity
import com.treinos.app.ui.UiState
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * H3-29: a corrida distingue carregando (antes do Room emitir), sessão
 * inexistente (empty state com volta) e o formulário — nunca um formulário
 * morto sem sessão.
 */
@RunWith(RobolectricTestRunner::class)
class RunWorkoutScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setPtLocale() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val conf = context.resources.configuration
        conf.setLocale(Locale("pt", "BR"))
        context.resources.updateConfiguration(conf, context.resources.displayMetrics)
    }

    @Test
    fun session_null_mostra_vazio() {
        composeRule.setContent {
            RunWorkoutScreen(
                state = UiState.Content(null),
                onLeave = {},
                onFinish = { _, _ -> },
            )
        }

        composeRule.onNodeWithText("Sessão não encontrada").assertIsDisplayed()
        composeRule.onNodeWithText("Voltar").assertIsDisplayed()
        composeRule.onNodeWithText("Finalizar corrida").assertDoesNotExist()
    }

    @Test
    fun loading_antes_da_sessao_chegar() {
        composeRule.setContent {
            RunWorkoutScreen(
                state = UiState.Loading,
                onLeave = {},
                onFinish = { _, _ -> },
            )
        }

        composeRule.onNodeWithContentDescription("Carregando").assertIsDisplayed()
        composeRule.onNodeWithText("Sessão não encontrada").assertDoesNotExist()
    }

    @Test
    fun sessao_valida_mostra_formulario() {
        composeRule.setContent {
            RunWorkoutScreen(
                state = UiState.Content(sessaoDeCorrida()),
                onLeave = {},
                onFinish = { _, _ -> },
            )
        }

        composeRule.onNodeWithText("Finalizar corrida").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sessão não encontrada").assertDoesNotExist()
    }

    private fun sessaoDeCorrida(): SessionWithExercises = SessionWithExercises(
        session = WorkoutSessionEntity(
            id = 7,
            planId = 1,
            dayId = 2,
            planName = "Plano Teste",
            dayName = "Corrida leve",
            status = "in_progress",
            startedAt = 1_700_000_000_000L,
            modality = "run",
        ),
        exercises = emptyList(),
    )
}
