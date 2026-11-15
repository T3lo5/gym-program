package com.treinos.app.ui

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.treinos.app.R
import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionExerciseWithSets
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.WorkoutSessionEntity
import com.treinos.app.ui.components.ExerciseHistorySheet
import com.treinos.app.ui.history.STATUS_COMPLETED
import com.treinos.app.ui.history.buildExerciseHistory
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * H4-3: conteúdo da folha do histórico por exercício — recorde de todos os
 * tempos (H4-1), gráfico de evolução (H4-2), lista de sessões com selo de
 * recorde progressivo e estado vazio do exercício nunca treinado.
 */
@RunWith(RobolectricTestRunner::class)
class ExerciseHistorySheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setPtLocale() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val conf = context.resources.configuration
        conf.setLocale(Locale("pt", "BR"))
        context.resources.updateConfiguration(conf, context.resources.displayMetrics)
    }

    private fun millis(date: LocalDate): Long =
        date.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun session(
        id: Long,
        date: LocalDate,
        reps: Int,
        weight: Double?,
    ) = SessionWithExercises(
        session = WorkoutSessionEntity(
            id = id,
            status = STATUS_COMPLETED,
            startedAt = millis(date),
            totalDurationSeconds = 60L,
            modality = "strength",
        ),
        exercises = listOf(
            SessionExerciseWithSets(
                exercise = SessionExerciseEntity(
                    id = id,
                    sessionId = id,
                    exerciseId = "ex1",
                    exerciseName = "Supino reto",
                ),
                sets = listOf(
                    SetLogEntity(
                        id = id,
                        exerciseLogId = id,
                        setNumber = 1,
                        reps = reps,
                        weightKg = weight,
                    ),
                ),
            ),
        ),
    )

    @Test
    fun folha_mostra_melhor_de_todos_os_tempos() {
        val history = buildExerciseHistory(
            listOf(session(id = 1, date = LocalDate.of(2026, 11, 10), reps = 10, weight = 60.0)),
            "ex1",
        )

        composeRule.setContent {
            ExerciseHistorySheet(history = history, onDismiss = {})
        }

        composeRule.onNodeWithText("Melhor de todos os tempos").assertExists()
        // 60 kg × 10 reps → 1RM Epley = 80 kg. A mesma melhor série também
        // aparece na linha da sessão (a única sessão é o recorde), então o
        // texto existe ao menos uma vez — no topo e na lista.
        composeRule.onAllNodesWithText("60,0 kg × 10 reps").onFirst().assertExists()
        composeRule.onNodeWithText("1RM estimado: 80,0 kg").assertExists()
    }

    @Test
    fun folha_lista_sessoes_com_recorde() {
        val history = buildExerciseHistory(
            listOf(
                session(id = 1, date = LocalDate.of(2026, 11, 10), reps = 10, weight = 40.0),
                session(id = 2, date = LocalDate.of(2026, 11, 15), reps = 8, weight = 50.0),
                session(id = 3, date = LocalDate.of(2026, 11, 20), reps = 9, weight = 45.0),
            ),
            "ex1",
        )

        composeRule.setContent {
            ExerciseHistorySheet(history = history, onDismiss = {})
        }

        // Mais recente primeiro: 20/11, 15/11, 10/11.
        composeRule.onNodeWithText("20/11/2026 10:00", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("15/11/2026 10:00", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("10/11/2026 10:00", useUnmergedTree = true).assertExists()
        // Recorde progressivo: 1ª (40 kg) e 2ª (50 kg) são PR; a 3ª (45 kg) não.
        composeRule.onAllNodesWithText("Recorde", useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun folha_sem_sessoes_mostra_estado_vazio() {
        val history = buildExerciseHistory(emptyList(), "ex1")

        composeRule.setContent {
            ExerciseHistorySheet(history = history, onDismiss = {})
        }

        composeRule.onNodeWithText("Nenhuma sessão registrada").assertExists()
        // Estado vazio não desenha gráfico nem lista (regra 9).
        composeRule.onNodeWithText("Poucos dados para um gráfico").assertDoesNotExist()
        composeRule.onNodeWithText("Sessões").assertDoesNotExist()
    }

    @Test
    fun folha_mostra_grafico_com_dois_pontos() {
        val history = buildExerciseHistory(
            listOf(
                session(id = 1, date = LocalDate.of(2026, 11, 10), reps = 10, weight = 40.0),
                session(id = 2, date = LocalDate.of(2026, 11, 15), reps = 8, weight = 50.0),
            ),
            "ex1",
        )

        composeRule.setContent {
            ExerciseHistorySheet(history = history, onDismiss = {})
        }

        // Mesma descrição do gráfico do Histórico (H4-2), métrica "Melhor série".
        composeRule.onNodeWithContentDescription(
            "Supino reto — Melhor série (2 treinos): 40,0 kg em 10/11 até 50,0 kg em 15/11",
        ).assertExists()
        composeRule.onNodeWithText("Poucos dados para um gráfico").assertDoesNotExist()
    }
}
