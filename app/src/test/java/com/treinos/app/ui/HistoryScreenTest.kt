package com.treinos.app.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.treinos.app.data.local.MANUAL_SESSION_SOURCE
import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionExerciseWithSets
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.WorkoutSessionEntity
import com.treinos.app.ui.history.STATUS_COMPLETED
import com.treinos.app.ui.screens.HistoryScreen
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
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

    @Before
    fun setPtLocale() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val conf = context.resources.configuration
        conf.setLocale(Locale("pt", "BR"))
        context.resources.updateConfiguration(conf, context.resources.displayMetrics)
    }

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

    /**
     * Alterna expandir/recolher clicando no título do card (árvore não
     * mesclada): `performClick` injeta o toque no centro do nó, e o centro do
     * card expandido cai na linha clicável do exercício (H3-54), que consumiria
     * o toque sem recolher. O título fica na área exclusiva do card.
     */
    private fun alternarCardSessao() =
        composeRule.onNodeWithText("Treino A", useUnmergedTree = true).performClick()

    /** Atividade avulsa de outra modalidade, para o seletor de modalidades aparecer. */
    private val atividadeAvulsa = SessionWithExercises(
        session = WorkoutSessionEntity(
            id = 2,
            planName = "",
            dayName = "Caminhada",
            status = STATUS_COMPLETED,
            startedAt = millisOf(today),
            endedAt = millisOf(today) + 1_800_000L,
            totalDurationSeconds = 1800,
            modality = "run",
            source = MANUAL_SESSION_SOURCE,
            title = "Caminhada",
        ),
        exercises = emptyList(),
    )

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

        alternarCardSessao()
        composeRule.onNodeWithText("Supino reto", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Série 1", substring = true, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("10 reps • 40,0 kg • RPE 8,0", substring = true, useUnmergedTree = true)
            .assertExists()

        alternarCardSessao()
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

    @Test
    fun chips_antes_das_metricas() {
        // H3-28: os chips de modalidade vêm logo após o calendário, antes das métricas.
        composeRule.setContent {
            HistoryScreen(
                sessions = listOf(sessaoConcluida, atividadeAvulsa),
                onBack = { },
            )
        }

        val chipY = composeRule.onNodeWithText("Todas", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y
        val metricasY = composeRule.onNodeWithText("Treinos", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y
        assertTrue("chips devem vir antes das métricas", chipY < metricasY)
    }

    @Test
    fun dia_selecionado_lista_primeiro() {
        // H3-28: com um dia selecionado, a lista daquele dia vem antes das métricas.
        composeRule.setContent {
            HistoryScreen(sessions = listOf(sessaoConcluida), onBack = { })
        }

        val formato = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("pt-BR"))
        composeRule.onNodeWithContentDescription(
            "${today.format(formato)}, 1 treino",
            useUnmergedTree = true,
        ).performClick()

        val listaY = cardSessao().fetchSemanticsNode().positionInRoot.y
        val metricasY = composeRule.onNodeWithText("Treinos", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y
        assertTrue("lista do dia deve vir antes das métricas", listaY < metricasY)
    }

    @Test
    @Config(qualifiers = "w180dp-h1600dp")
    fun chips_em_flowrow_nao_transbordam() {
        // H3-32: em tela estreita os chips de modalidade quebram linha (FlowRow)
        // em vez de sumir para fora da viewport (Row sem scroll).
        val swim = SessionWithExercises(
            session = WorkoutSessionEntity(
                id = 3,
                planName = "",
                dayName = "Natação",
                status = STATUS_COMPLETED,
                startedAt = millisOf(today),
                endedAt = millisOf(today) + 1_800_000L,
                totalDurationSeconds = 1800,
                modality = "swim",
                source = MANUAL_SESSION_SOURCE,
                title = "Natação",
            ),
            exercises = emptyList(),
        )
        val mobility = SessionWithExercises(
            session = WorkoutSessionEntity(
                id = 4,
                planName = "",
                dayName = "Mobilidade",
                status = STATUS_COMPLETED,
                startedAt = millisOf(today),
                endedAt = millisOf(today) + 1_800_000L,
                totalDurationSeconds = 1800,
                modality = "mobility",
                source = MANUAL_SESSION_SOURCE,
                title = "Mobilidade",
            ),
            exercises = emptyList(),
        )
        composeRule.setContent {
            HistoryScreen(
                sessions = listOf(sessaoConcluida, atividadeAvulsa, swim, mobility),
                onBack = { },
            )
        }

        val rootWidth = composeRule.onRoot().fetchSemanticsNode().size.width
        val labels = listOf("Todas", "Musculação", "Corrida", "Natação", "Mobilidade")
        labels.forEach { label ->
            val node = composeRule.onNodeWithText(label, useUnmergedTree = true)
                .fetchSemanticsNode()
            val right = node.positionInRoot.x + node.size.width
            assertTrue("chip '$label' não pode transbordar a largura ($right > $rootWidth)", right <= rootWidth + 1)
        }
    }

    @Test
    fun excluirRegistro_exige_confirmacao() {
        // H3-47: a exclusão de um registro avulso só é disparada após
        // confirmação explícita; o toque em "Excluir" sozinho abre o diálogo.
        var excluido: Long? = null
        composeRule.setContent {
            HistoryScreen(
                sessions = listOf(atividadeAvulsa),
                onBack = { },
                onDeleteActivity = { excluido = it },
            )
        }
        // O botão "Excluir" do card abre o diálogo; a confirmação é outro toque.
        composeRule.onAllNodesWithText("Excluir", useUnmergedTree = true).onFirst().performClick()
        composeRule.onNodeWithText("Excluir o registro?").assertIsDisplayed()
        composeRule.onNodeWithText("Caminhada sai do histórico e do próximo backup. Não dá para desfazer.")
            .assertIsDisplayed()
        // Confirmar: o último nó "Excluir" é o botão do diálogo.
        composeRule.onAllNodesWithText("Excluir", useUnmergedTree = true).onLast().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Excluir o registro?").assertDoesNotExist()
        assertEquals(2L, excluido)
    }
}
