package com.treinos.app.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.WorkoutSessionEntity
import com.treinos.app.ui.RunLogInput
import com.treinos.app.ui.UiState
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.Assert.assertEquals
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

    /**
     * H3-47: duração fora do intervalo mostra o erro no campo (supportingText)
     * e o botão de finalizar fica desabilitado.
     */
    @Test
    fun duracao_fora_do_intervalo_mostra_erro_no_campo_e_bloqueia_botao() {
        composeRule.setContent {
            RunWorkoutScreen(
                state = UiState.Content(sessaoDeCorrida()),
                onLeave = {},
                onFinish = { _, _ -> },
            )
        }

        composeRule.onNodeWithTag("run_duration_field").performTextInput("700")

        composeRule
            .onNodeWithText("Duração entre 1 e 600 minutos.")
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("Finalizar corrida")
            .performScrollTo()
            .assertIsNotEnabled()
    }

    /**
     * H3-47: formulário válido finaliza com os valores digitados — duração em
     * minutos convertida para segundos e distância aceita com vírgula.
     */
    @Test
    fun formulario_valido_finaliza_com_valores_digitados() {
        val finished = mutableListOf<Pair<Long, RunLogInput>>()
        composeRule.setContent {
            RunWorkoutScreen(
                state = UiState.Content(sessaoDeCorrida()),
                onLeave = {},
                onFinish = { sessionId, input -> finished.add(sessionId to input) },
            )
        }

        composeRule.onNodeWithTag("run_duration_field").performTextInput("45")
        composeRule.onNodeWithTag("run_distance_field").performTextInput("5,2")
        composeRule
            .onNodeWithText("Finalizar corrida")
            .performScrollTo()
            .performClick()

        assertEquals(1, finished.size)
        assertEquals(7L, finished[0].first)
        assertEquals(2_700L, finished[0].second.durationSeconds)
        assertEquals(5.2, finished[0].second.distanceKm!!, 1e-9)
        assertEquals(null, finished[0].second.rpe)
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
