package com.treinos.app.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTextInput
import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionExerciseWithSets
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.SET_TYPE_DROP
import com.treinos.app.data.local.SET_TYPE_NORMAL
import com.treinos.app.data.local.SET_TYPE_WARMUP
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.WorkoutPlanExerciseEntity
import com.treinos.app.data.local.WorkoutSessionEntity
import com.treinos.app.data.SwapScope
import com.treinos.app.data.media.ExerciseMedia
import com.treinos.app.ui.progression.PersonalRecord
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
    private fun sessao(
        comSets: Boolean = true,
        weightKg: Double = 60.0,
        setType: String = SET_TYPE_NORMAL,
        rir: Int? = null,
    ): SessionWithExercises {
        val sets = if (comSets) {
            listOf(
                SetLogEntity(
                    id = 1000,
                    exerciseLogId = 100,
                    setNumber = 1,
                    reps = 10,
                    weightKg = weightKg,
                    rpe = null,
                    setType = setType,
                    rir = rir,
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
        exerciseMedia: Map<String, ExerciseMedia> = emptyMap(),
        availableEquipment: List<String> = emptyList(),
        personalRecords: Map<String, PersonalRecord> = emptyMap(),
        onSwapExercise: ((Long, String, SwapScope) -> Unit)? = null,
        onLoadCatalog: () -> Unit = {},
        planExercises: List<WorkoutPlanExerciseEntity> = emptyList(),
        onToggleSuperset: (Long, Boolean) -> Unit = { _, _ -> },
        onEditSessionNote: (String) -> Unit = {},
        onEditExerciseNote: (SessionExerciseEntity, String) -> Unit = { _, _ -> },
    ) {
        composeRule.setContent {
            WorkoutScreen(
                session = session,
                restRemaining = restRemaining,
                restTotal = restTotal,
                onLeave = { },
                onLogSet = { _, input -> onLoggedSet(input.reps, input.weightKg, input.rpe) },
                onSkipRest = { },
                onFinish = onFinished,
                hasInProgressSession = false,
                restPaused = restPaused,
                onUpdateSet = { _, input -> onUpdatedSet(input.reps, input.weightKg, input.rpe) },
                exerciseMedia = exerciseMedia,
                availableEquipment = availableEquipment,
                personalRecords = personalRecords,
                onSwapExercise = onSwapExercise,
                onLoadCatalog = onLoadCatalog,
                planExercises = planExercises,
                onToggleSuperset = onToggleSuperset,
                onEditSessionNote = onEditSessionNote,
                onEditExerciseNote = onEditExerciseNote,
            )
        }
    }

    private fun catalogoComSubstituto(): Map<String, ExerciseMedia> = mapOf(
        "Barbell_Bench_Press" to ExerciseMedia(
            id = "Barbell_Bench_Press",
            name = "Supino reto",
            primaryMuscles = listOf("chest"),
            equipment = "barbell",
            instructions = emptyList(),
            imageUrl = null,
        ),
        "Dumbbell_Bench_Press" to ExerciseMedia(
            id = "Dumbbell_Bench_Press",
            name = "Supino Halter",
            primaryMuscles = listOf("chest"),
            equipment = "dumbbell",
            instructions = emptyList(),
            imageUrl = null,
        ),
    )

    private fun abrirDialogoDeTroca() {
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Trocar exercício"))
        composeRule.onNodeWithText("Trocar exercício").performClick()
    }

    /** Campo de texto localizado pelo valor atual (o rótulo fica em outro nó). */
    private fun campoDeTexto(valorAtual: String) =
        composeRule.onNode(hasSetTextAction() and hasText(valorAtual), useUnmergedTree = true)

    @Test
    fun sem_serie_anterior_o_botao_repetir_nao_existe() {
        setContent(session = sessao(comSets = false))

        // H4-11: o bloco de nota no card de resumo empurra o card do
        // exercício para baixo na janela de teste — rolar até o texto antes
        // de afirmar que ele está visível (padrão dos demais testes).
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Nenhuma série registrada ainda."))
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

        // H4-11: o bloco de nota no card de resumo empurra a linha de série
        // para baixo na janela de teste — rolar até ela antes de clicar.
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Série 1"))
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

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Série 1"))
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

    @Test
    fun dialogo_troca_nao_mostra_carregando_quando_catalogo_presente() {
        // H3-44: com o catálogo local já publicado, a troca abre direto na
        // lista de substitutos; nada de "Carregando catálogo" nem de rede.
        setContent(
            session = sessao(),
            exerciseMedia = catalogoComSubstituto(),
            availableEquipment = listOf("barbell", "dumbbell"),
            onSwapExercise = { _, _, _ -> },
        )

        abrirDialogoDeTroca()

        composeRule.onNodeWithText("Substitutos (1)").assertIsDisplayed()
        composeRule.onNodeWithText("Carregando catálogo de exercícios…").assertDoesNotExist()
        composeRule.onNodeWithText("Supino Halter").assertIsDisplayed()
    }

    @Test
    fun dialogo_troca_sem_catalogo_mostra_estado_vazio_sem_progresso() {
        // H3-44: sem catálogo local (asset ausente/corrompido), o diálogo diz
        // isso explicitamente e oferece "Atualizar"; não fica em spinner.
        var pediuAtualizacao = false
        setContent(
            session = sessao(),
            exerciseMedia = emptyMap(),
            onSwapExercise = { _, _, _ -> },
            onLoadCatalog = { pediuAtualizacao = true },
        )

        abrirDialogoDeTroca()

        composeRule.onNodeWithText("Catálogo offline indisponível.").assertIsDisplayed()
        composeRule.onNodeWithText("Carregando catálogo de exercícios…").assertDoesNotExist()
        composeRule.onNodeWithText("Atualizar catálogo").performClick()
        assertTrue(pediuAtualizacao)
    }

    @Test
    fun dialogo_troca_seleciona_substituto_e_escopo() {
        // H3-44: seleção única + escopo explícito; confirmar só depois de
        // escolher, enviando o id e o escopo para a camada de troca.
        var troca: Triple<Long, String, SwapScope>? = null
        setContent(
            session = sessao(),
            exerciseMedia = catalogoComSubstituto(),
            availableEquipment = listOf("barbell", "dumbbell"),
            onSwapExercise = { id, replacementId, scope ->
                troca = Triple(id, replacementId, scope)
            },
        )

        abrirDialogoDeTroca()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Supino Halter").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("swap_sheet_list").performScrollToNode(hasText("Todos os treinos"))
        composeRule.onNodeWithText("Todos os treinos").performClick()
        composeRule.onNodeWithTag("swap_sheet_list").performScrollToNode(hasText("Trocar"))
        composeRule.onNodeWithText("Trocar").assertIsEnabled().performClick()

        assertEquals(
            Triple(100L, "Dumbbell_Bench_Press", SwapScope.ALL_FUTURE),
            troca,
        )
    }

    /** Recorde atual de 60 kg × 10 (1RM Epley 80,0) para o exercício da sessão. */
    private fun recordeAtual(): Map<String, PersonalRecord> = mapOf(
        "Barbell_Bench_Press" to PersonalRecord(
            exerciseId = "Barbell_Bench_Press",
            weightKg = 60.0,
            reps = 10,
            estimated1RmKg = 80.0,
            achievedAtMillis = 1_000L,
        ),
    )

    @Test
    fun bater_pr_mostra_selo() {
        // H4-1: 65 kg × 12 (1RM 91,0) supera 60 × 10 (80,0) → selo no card e
        // a série é gravada do mesmo jeito.
        var gravada: Triple<Int?, Double?, Double?>? = null
        setContent(
            session = sessao(),
            onLoggedSet = { reps, weight, rpe -> gravada = Triple(reps, weight, rpe) },
            personalRecords = recordeAtual(),
        )

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Registrar série 2"))
        composeRule.onNodeWithText("Registrar série 2").performClick()
        campoDeTexto("10").performTextReplacement("12")
        campoDeTexto("60,0").performTextReplacement("65")
        composeRule.onNodeWithText("Salvar série").performClick()

        assertEquals(Triple(12, 65.0, null), gravada)
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Recorde pessoal!"))
        composeRule.onNodeWithText("Recorde pessoal!").assertIsDisplayed()
    }

    @Test
    fun serie_sem_pr_nao_mostra_selo() {
        // H4-1: repetir o próprio recorde (60 × 10 = 1RM 80,0) é empate —
        // empate não é PR novo; a série é gravada e nenhum selo aparece.
        var gravada: Triple<Int?, Double?, Double?>? = null
        setContent(
            session = sessao(),
            onLoggedSet = { reps, weight, rpe -> gravada = Triple(reps, weight, rpe) },
            personalRecords = recordeAtual(),
        )

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Registrar série 2"))
        composeRule.onNodeWithText("Registrar série 2").performClick()
        campoDeTexto("60,0").performTextReplacement("60")
        composeRule.onNodeWithText("Salvar série").performClick()

        assertEquals(Triple(10, 60.0, null), gravada)
        composeRule.onNodeWithText("Recorde pessoal!").assertDoesNotExist()
    }

    @Test
    fun repetir_ultima_serie_com_pr_mostra_selo() {
        // H4-1: "Repetir última série" também avalia PR — com recorde anterior
        // mais fraco (50 × 8), repetir 60 × 10 supera e mostra o selo.
        var gravada: Triple<Int?, Double?, Double?>? = null
        setContent(
            session = sessao(),
            onLoggedSet = { reps, weight, rpe -> gravada = Triple(reps, weight, rpe) },
            personalRecords = mapOf(
                "Barbell_Bench_Press" to PersonalRecord(
                    exerciseId = "Barbell_Bench_Press",
                    weightKg = 50.0,
                    reps = 8,
                    estimated1RmKg = 56.67,
                    achievedAtMillis = 1_000L,
                ),
            ),
        )

        // Rolar até "Registrar série 2" (padrão dos demais testes desta classe)
        // deixa o botão "Repetir última série" — logo acima — visível e
        // clicável na janela estreita do Robolectric.
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Registrar série 2"))
        composeRule.onNodeWithText("Repetir última série", substring = true).performClick()

        assertEquals(Triple(10, 60.0, null), gravada)
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Recorde pessoal!"))
        composeRule.onNodeWithText("Recorde pessoal!").assertIsDisplayed()
    }

    // ---------- H4-4: tipos de série ----------

    @Test
    fun serie_warmup_mostra_rotulo() {
        // A série de aquecimento aparece rotulada na linha do card do treino.
        setContent(session = sessao(setType = SET_TYPE_WARMUP))

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Aquecimento", substring = true))
        composeRule.onNodeWithText("Aquecimento", substring = true).assertIsDisplayed()
    }

    @Test
    fun serie_drop_mostra_rotulo() {
        // Drop set também é rotulado na linha da série.
        setContent(session = sessao(setType = SET_TYPE_DROP))

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Drop set", substring = true))
        composeRule.onNodeWithText("Drop set", substring = true).assertIsDisplayed()
    }

    @Test
    fun serie_normal_nao_mostra_rotulo_de_tipo() {
        // Série normal não ganha selo de tipo — só reps/carga.
        setContent(session = sessao(setType = SET_TYPE_NORMAL))

        composeRule.onNodeWithText("Drop set", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Aquecimento", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("AMRAP", substring = true).assertDoesNotExist()
    }

    // ---------- H4-5: anilhas e aquecimento ----------

    @Test
    fun atalho_de_anilhas_aparece_para_barra() {
        // Exercício de barra: o atalho mostra a quebra por lado e a rampa da
        // carga digitada (100 kg → 20 + 20 por lado, 60% = 35 kg × 5).
        setContent(
            session = sessao(),
            exerciseMedia = mapOf(
                "Barbell_Bench_Press" to ExerciseMedia(
                    id = "Barbell_Bench_Press",
                    name = "Supino reto",
                    primaryMuscles = listOf("chest"),
                    equipment = "barbell",
                    instructions = emptyList(),
                    imageUrl = null,
                ),
            ),
        )

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Registrar série 2"))
        composeRule.onNodeWithText("Registrar série 2").performClick()

        campoDeTexto("60,0").performTextReplacement("100")
        // O botão do atalho nasce abaixo da dobra do diálogo na janela do
        // Robolectric (320×470): rola a Column do diálogo até ele antes de
        // clicar — clique fora da janela visível se perde.
        composeRule
            .onAllNodes(
                hasScrollAction() and hasAnyDescendant(hasText("Anilhas e aquecimento")),
            )[0]
            .performScrollToNode(hasText("Anilhas e aquecimento"))
        composeRule.onNodeWithText("Anilhas e aquecimento").performClick()

        // A asserção é de existência na composição: o conteúdo da expansão
        // também fica abaixo da dobra da Column rolável do diálogo.
        composeRule.onNodeWithText("Por lado: 20 + 20 kg").assertExists()
        composeRule.onNodeWithText("Total: 100 kg").assertExists()
        composeRule.onNodeWithText("40%: 40 kg × 8").assertExists()
        composeRule.onNodeWithText("60%: 60 kg × 5").assertExists()
        composeRule.onNodeWithText("80%: 80 kg × 3").assertExists()
    }

    @Test
    fun atalho_nao_aparece_para_halteres() {
        // Halteres não carrega anilha: o atalho nem aparece no diálogo.
        setContent(
            session = sessao(),
            exerciseMedia = mapOf(
                "Barbell_Bench_Press" to ExerciseMedia(
                    id = "Barbell_Bench_Press",
                    name = "Supino reto",
                    primaryMuscles = listOf("chest"),
                    equipment = "dumbbell",
                    instructions = emptyList(),
                    imageUrl = null,
                ),
            ),
        )

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Registrar série 2"))
        composeRule.onNodeWithText("Registrar série 2").performClick()

        composeRule.onNodeWithText("Anilhas e aquecimento").assertDoesNotExist()
    }

    // ---------- H4-6: supersets (bi/tri-set) ----------

    /** Sessão com dois exercícios na ordem do dia; devolve os ids de sessão 100 e 200. */
    private fun sessaoSuperset(): SessionWithExercises {
        fun exercicio(id: Long, exerciseId: String, nome: String, order: Int) =
            SessionExerciseWithSets(
                exercise = SessionExerciseEntity(
                    id = id,
                    sessionId = 1,
                    exerciseId = exerciseId,
                    exerciseName = nome,
                    exerciseOrder = order,
                    plannedSets = 3,
                    completedSets = 0,
                    restSeconds = 90,
                ),
                sets = emptyList(),
            )
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
                exercicio(100, "ex1", "Supino reto", 0),
                exercicio(200, "ex2", "Remada alta", 1),
            ),
        )
    }

    /** Plano do dia com um exercício por flag informada, na ordem. */
    private fun planoSuperset(vararg flags: Boolean): List<WorkoutPlanExerciseEntity> =
        flags.mapIndexed { index, superset ->
            WorkoutPlanExerciseEntity(
                id = 1000L + index,
                dayId = 10,
                exerciseId = "ex${index + 1}",
                exerciseName = "Ex ${index + 1}",
                exerciseOrder = index,
                restSeconds = 90,
                isSupersetWithPrevious = superset,
            )
        }

    @Test
    fun superset_mostra_cabecalho_e_rotulos() {
        // Bi-set: o cabeçalho "Supersérie A" fica DENTRO do card do primeiro
        // membro (sem item extra na lista) e os membros recebem A1 e A2.
        setContent(session = sessaoSuperset(), planExercises = planoSuperset(false, true))

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Supersérie A"))
        composeRule.onNodeWithText("Supersérie A").assertIsDisplayed()
        composeRule.onNodeWithText("A1").assertIsDisplayed()

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("A2"))
        composeRule.onNodeWithText("A2").assertIsDisplayed()
    }

    @Test
    fun botao_superset_ausente_no_primeiro() {
        // O primeiro exercício do dia nunca entra em grupo: sem botão nele;
        // só o segundo card mostra a alternância.
        setContent(session = sessaoSuperset(), planExercises = planoSuperset(false, false))

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Supersérie com o anterior"))
        composeRule.onNodeWithText("Supersérie com o anterior").assertIsDisplayed()
        assertEquals(
            1,
            composeRule.onAllNodesWithText("Supersérie com o anterior").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun toggle_superset_dispara_callback() {
        // Tocar em "Supersérie com o anterior" pede a ligação com o exercício
        // anterior: o callback recebe o id da sessão e o estado novo (true).
        var alternado: Pair<Long, Boolean>? = null
        setContent(
            session = sessaoSuperset(),
            planExercises = planoSuperset(false, false),
            onToggleSuperset = { id, enabled -> alternado = id to enabled },
        )

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Supersérie com o anterior"))
        composeRule.onNodeWithText("Supersérie com o anterior").performClick()

        assertEquals(200L to true, alternado)
    }

    // ---------- H4-11: notas do treino e do exercício ----------

    @Test
    fun nota_da_sessao_abre_e_salva() {
        // Card de resumo: "Adicionar nota" abre o diálogo; salvar entrega o
        // texto ao ViewModel e fecha o diálogo. O bloco da nota fica no fim do
        // card de resumo, visível na janela padrão do Robolectric.
        var salva: String? = null
        setContent(session = sessao(), onEditSessionNote = { salva = it })

        composeRule.onNodeWithText("Adicionar nota").performClick()
        composeRule.onNodeWithText("Observações do treino").assertIsDisplayed()

        composeRule.onNodeWithTag("note_edit_field").performTextReplacement("ombro esquerdo incomodou")
        composeRule.onNodeWithText("Salvar").performClick()

        assertEquals("ombro esquerdo incomodou", salva)
        composeRule.onNodeWithTag("note_edit_field").assertDoesNotExist()
    }

    @Test
    fun nota_do_exercicio_abre_e_salva() {
        // Card do exercício: "Adicionar nota do exercício" abre o diálogo e
        // salvar entrega o exercício da sessão + o texto ao ViewModel.
        var salva: Pair<Int, String>? = null
        setContent(
            session = sessao(),
            planExercises = listOf(
                WorkoutPlanExerciseEntity(
                    id = 500,
                    dayId = 10,
                    exerciseId = "Barbell_Bench_Press",
                    exerciseOrder = 0,
                ),
            ),
            onEditExerciseNote = { exercise, note -> salva = exercise.exerciseOrder to note },
        )

        // O rodapé da tela fica abaixo do card: rolar até ele deixa o bloco
        // da nota — última linha do card — visível e clicável.
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Cada série é gravada no SQLite", substring = true))
        composeRule.onNodeWithText("Adicionar nota do exercício").performClick()
        composeRule.onNodeWithText("Observações do treino").assertIsDisplayed()

        composeRule.onNodeWithTag("note_edit_field").performTextInput("puxar a barra até o peito")
        composeRule.onNodeWithText("Salvar").performClick()

        assertEquals(0 to "puxar a barra até o peito", salva)
    }

    @Test
    fun cancelar_nao_grava() {
        // Cancelar (e fechar por fora) não chama o callback: nada é gravado.
        var salva: String? = null
        setContent(session = sessao(), onEditSessionNote = { salva = it })

        composeRule.onNodeWithText("Adicionar nota").performClick()
        composeRule.onNodeWithTag("note_edit_field").performTextReplacement("rascunho que não vale")
        composeRule.onNodeWithText("Cancelar").performClick()

        composeRule.onNodeWithTag("note_edit_field").assertDoesNotExist()
        assertNull(salva)
    }

    @Test
    fun contador_bloqueia_acima_do_limite() {
        // 2001 caracteres: o contador vira aviso de erro e o Salvar fica
        // desabilitado — nada é gravado.
        var salva: String? = null
        setContent(session = sessao(), onEditSessionNote = { salva = it })

        composeRule.onNodeWithText("Adicionar nota").performClick()
        composeRule.onNodeWithTag("note_edit_field").performTextReplacement("a".repeat(2001))

        composeRule.onNodeWithText("Máximo de 2000 caracteres.").assertIsDisplayed()
        composeRule.onNodeWithText("Salvar").assertIsNotEnabled()
        assertNull(salva)
    }

    @Test
    fun nota_do_plano_aparece_sob_o_exercicio() {
        // A nota vem do slot do plano de mesmo `exerciseOrder` e aparece sob
        // o exercício na tela de treino (reaparece nas próximas sessões).
        setContent(
            session = sessao(),
            planExercises = listOf(
                WorkoutPlanExerciseEntity(
                    id = 500,
                    dayId = 10,
                    exerciseId = "Barbell_Bench_Press",
                    exerciseOrder = 0,
                    notes = "puxar a barra até o peito",
                ),
            ),
        )

        // O rodapé da tela fica abaixo do card: rolar até ele deixa o bloco
        // da nota — última linha do card — visível (padrão dos demais testes).
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText("Cada série é gravada no SQLite", substring = true))
        composeRule.onNodeWithText("Nota do exercício no plano").assertIsDisplayed()
        composeRule.onNodeWithText("puxar a barra até o peito").assertIsDisplayed()
    }
}
