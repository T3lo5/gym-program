package com.treinos.app.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionExerciseWithSets
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.WorkoutSessionEntity
import com.treinos.app.ui.history.STATUS_COMPLETED
import com.treinos.app.ui.screens.HistoryScreen
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UI do histórico fora do emulador: a tela recebe uma sessão falsa montada aqui,
 * nunca o `AppViewModel` nem o banco do usuário.
 */
@RunWith(RobolectricTestRunner::class)
// O viewport padrão do Robolectric (320x470 dp) é menor que o LazyColumn: calendário e
// métricas ocupam tudo e o `LazyColumn` nem compõe o card do treino. Altura alta mantém
// todos os itens da lista compostos e visíveis, sem depender de scroll para clicar.
@Config(qualifiers = "w411dp-h1600dp")
class HistoryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.now()

    private fun millisOf(date: LocalDate): Long =
        date.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val sessaoConcluida = SessionWithExercises(
        session = WorkoutSessionEntity(
            id = 1,
            planId = 1,
            dayId = 10,
            planName = "Plano Teste",
            dayName = "Treino A",
            status = STATUS_COMPLETED,
            startedAt = millisOf(today),
            endedAt = millisOf(today) + 3_600_000L,
            totalDurationSeconds = 3600,
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
                sets = listOf(
                    SetLogEntity(
                        id = 1000,
                        exerciseLogId = 100,
                        setNumber = 1,
                        reps = 10,
                        weightKg = 40.0,
                        rpe = 8.0,
                    ),
                ),
            ),
        ),
    )

    /** Card expansível: único nó com a combinação "Treino A" + ação de clique. */
    private fun cardSessao() = composeRule.onNode(hasClickAction() and hasText("Treino A"))

    @Test
    fun mostra_estado_vazio() {
        var gerou = false
        composeRule.setContent {
            HistoryScreen(
                sessions = emptyList(),
                onBack = { },
                onGenerate = { gerou = true },
            )
        }

        composeRule.onNodeWithText("Nenhum treino registrado").assertExists()
        composeRule.onNodeWithText("Gerar treino").performClick()
        assertEquals(true, gerou)
    }

    @Test
    fun mostra_sessao_concluida() {
        composeRule.setContent {
            HistoryScreen(sessions = listOf(sessaoConcluida), onBack = { })
        }

        cardSessao().assertExists()
        composeRule.onNodeWithText("Concluído", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Plano Teste", useUnmergedTree = true).assertExists()
        // Recolhido: o detalhe das séries ainda não está na tela.
        composeRule.onNodeWithText("Supino reto", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun expande_e_recolhe_sessao() {
        composeRule.setContent {
            HistoryScreen(sessions = listOf(sessaoConcluida), onBack = { })
        }

        cardSessao().performClick()
        composeRule.onNodeWithText("Supino reto", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Série 1", substring = true, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("10 reps • 40 kg • RPE 8", substring = true, useUnmergedTree = true)
            .assertExists()

        cardSessao().performClick()
        composeRule.onNodeWithText("Supino reto", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun mostra_metricas_do_mes() {
        composeRule.setContent {
            HistoryScreen(sessions = listOf(sessaoConcluida), onBack = { })
        }

        composeRule.onNodeWithText(formatMonthYear(today.year, today.monthValue)).assertExists()
        composeRule.onNodeWithText("Treinos", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Séries", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Volume", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Duração", useUnmergedTree = true).assertExists()
        // 1 sessão concluída, 1 série de 10 reps com 40 kg = 400 kg, 1h de duração.
        composeRule.onNodeWithText("10 reps", useUnmergedTree = true).assertExists()
        // O mesmo "400 kg" aparece duas vezes na tela: no card "Volume" e na linha
        // "Outros" do ranking por músculo (a sessão falsa não tem músculo mapeado).
        composeRule.onNodeWithText("Outros", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithText("400 kg", useUnmergedTree = true).assertCountEquals(2)
        composeRule.onNodeWithText("1h 00min", useUnmergedTree = true).assertExists()
    }
}
