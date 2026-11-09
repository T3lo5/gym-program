package com.treinos.app.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionExerciseWithSets
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.WorkoutSessionEntity
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkoutScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setPtLocale() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val conf = context.resources.configuration
        conf.setLocale(Locale("pt", "BR"))
        context.resources.updateConfiguration(conf, context.resources.displayMetrics)
    }

    /** Sessão de força com um exercício e uma série 60 kg × 10 registrada. */
    private fun sessao(comSets: Boolean = true, weightKg: Double = 60.0): SessionWithExercises {
        val sets = if (comSets) {
            listOf(
                SetLogEntity(
                    id = 1000,
                    exerciseLogId = 100,
                    setNumber = 1,
                    reps = 10,
                    weightKg = weightKg,
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
        onFinished: () -> Unit = {},
        onUpdatedSet: (Int?, Double?, Double?) -> Unit = { _, _, _ -> },
    ) {
        composeRule.setContent {
            WorkoutScreen(
                session = session,
                restRemaining = restRemaining,
                restTotal = restTotal,
                onLeave = { },
                onLogSet = { _, reps, weight, rpe -> onLoggedSet(reps, weight, rpe) },
                onSkipRest = { },
                onFinish = onFinished,
                hasInProgressSession = false,
                restPaused = restPaused,
                onUpdateSet = { _, reps, weight, rpe -> onUpdatedSet(reps, weight, rpe) },
            )
        }
    }

    /** Campo de texto localizado pelo valor atual (o rótulo fica em outro nó). */
    private fun campoDeTexto(valorAtual: String) =
        composeRule.onNode(hasSetTextAction() and hasText(valorAtual), useUnmergedTree = true)

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

    @Test
    fun finalizar_exige_confirmacao() {
        // H3-27: "Finalizar treino" mostra o resumo e pode ser cancelado sem
        // efeito; só o "Finalizar" do diálogo encerra a sessão.
        var finished = false
        setContent(session = sessao(), onFinished = { finished = true })

        composeRule.onNodeWithText("Finalizar treino").performClick()
        composeRule.onNodeWithText("Finalizar treino?").assertIsDisplayed()
        composeRule.onNodeWithText("1 de 3 séries registradas", substring = true).assertIsDisplayed()
        assertFalse(finished)

        composeRule.onNodeWithText("Cancelar").performClick()
        composeRule.onNodeWithText("Finalizar treino?").assertDoesNotExist()
        assertFalse(finished)

        composeRule.onNodeWithText("Finalizar treino").performClick()
        composeRule.onNodeWithText("Finalizar").performClick()
        assertTrue(finished)
    }

    @Test
    fun linha_de_serie_abre_edicao() {
        // H3-27: tocar na linha registrada abre o diálogo pré-preenchido;
        // salvar atualiza a mesma série (62,5 kg com vírgula).
        var atualizada: Triple<Int?, Double?, Double?>? = null
        setContent(
            session = sessao(),
            onUpdatedSet = { reps, weight, rpe -> atualizada = Triple(reps, weight, rpe) },
        )

        composeRule.onNodeWithText("Série 1").performClick()
        composeRule.onNodeWithText("Editar série 1 — Supino reto").assertIsDisplayed()

        campoDeTexto("60,0").performTextReplacement("62,5")
        composeRule.onNodeWithText("Salvar alterações").performClick()

        assertEquals(Triple(10, 62.5, null), atualizada)
    }

    @Test
    fun carga_invalida_bloqueia_salvamento() {
        // H3-27: "6.0.5" mostra erro no diálogo e nada é gravado; corrigir
        // para "61,5" salva de verdade.
        var atualizada: Triple<Int?, Double?, Double?>? = null
        setContent(
            session = sessao(),
            onUpdatedSet = { reps, weight, rpe -> atualizada = Triple(reps, weight, rpe) },
        )

        composeRule.onNodeWithText("Série 1").performClick()
        composeRule.onNodeWithText("Editar série 1 — Supino reto").assertIsDisplayed()
        campoDeTexto("60,0").performTextReplacement("6.0.5")
        composeRule.onNodeWithText("Salvar alterações").performClick()

        composeRule.onNodeWithText("Informe a carga como número, usando vírgula ou ponto.").assertIsDisplayed()
        assertNull(atualizada)

        campoDeTexto("6.0.5").performTextReplacement("61,5")
        composeRule.onNodeWithText("Salvar alterações").performClick()
        assertEquals(Triple(10, 61.5, null), atualizada)
    }

    @Test
    fun dialogo_preserva_campos_apos_recreate() {
        // H3-31: os campos do diálogo de série vivem em rememberSaveable(key=...).
        // Na janela padrão do Robolectric (320×470) o botão "Registrar série 2"
        // fica sob o bottomBar — rolar o mínimo para trazê-lo à vista, como o
        // usuário faria, antes de abrir o diálogo. Os campos vêm pré-preenchidos
        // com a última série (10 reps / 60 kg) e são localizados pelo valor,
        // como nos demais testes deste arquivo.
        setContent(session = sessao())

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Registrar série 2"))
        composeRule.onNodeWithText("Registrar série 2").performClick()
        composeRule.onNodeWithText("Série 2 — Supino reto").assertIsDisplayed()

        campoDeTexto("10").performTextReplacement("12")
        campoDeTexto("60,0").performTextReplacement("62,5")

        composeRule.onNodeWithText("12").assertIsDisplayed()
        composeRule.onNodeWithText("62,5").assertIsDisplayed()
    }

}
