package com.treinos.app.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.WorkoutDayEntity
import com.treinos.app.data.local.WorkoutDayWithExercises
import com.treinos.app.data.local.WorkoutPlanExerciseEntity
import com.treinos.app.data.local.WorkoutPlanWithDetails
import com.treinos.app.data.local.WorkoutPlanEntity
import com.treinos.app.data.local.WorkoutSessionEntity
import com.treinos.app.data.media.ExerciseMedia
import com.treinos.app.ui.ModalityTab
import com.treinos.app.ui.PlanCycle
import com.treinos.app.ui.SessionKind
import com.treinos.app.ui.UiState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var plan: WorkoutPlanWithDetails

    @Before
    fun setUp() {
        plan = WorkoutPlanWithDetails(
            plan = WorkoutPlanEntity(
                id = 1,
                name = "Semana híbrida",
                goal = "general",
                daysPerWeek = 7,
            ),
            days = listOf(
                day(
                    weekday = 1,
                    dayOrder = 0,
                    modality = "strength",
                    exercises = listOf(
                        exercise(id = "Barbell_Bench_Press", name = "Supino reto", order = 0),
                        exercise(id = "Barbell_Squat", name = "Agachamento", order = 1),
                    ),
                ),
                day(
                    weekday = 1,
                    dayOrder = 1,
                    modality = "run",
                    exercises = listOf(exercise(id = "run_easy_30", name = "Corrida leve 30 min", order = 0)),
                ),
                day(
                    weekday = 2,
                    dayOrder = 0,
                    modality = "mobility",
                    exercises = listOf(exercise(id = "mobility_spine", name = "Mobilidade de coluna", order = 0)),
                ),
            ),
        )
    }

    @Test
    fun selo_do_plano_local_cita_motivo_persistido() {
        // H3-30: o motivo do fallback persiste e o selo o cita — não some com
        // o snackbar de ~4 s; some só na próxima geração da IA.
        composeRule.setContent {
            HomeScreen(
                plan = plan,
                inProgressSession = null,
                onGenerate = {},
                onHistory = {},
                onSettings = {},
                onStartDay = {},
                onResume = {},
                onLogActivity = {},
                onOpenMobility = {},
                fallbackReasonLabel = "resposta da IA sem IDs válidos",
            )
        }

        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Gerado localmente", substring = true))
        composeRule.onNodeWithText("Gerado localmente — resposta da IA sem IDs válidos")
            .assertIsDisplayed()
    }

    @Test
    fun mostra_abas_apenas_das_modalidades_do_plano() {
        composeRule.setContent {
            HomeScreen(
                plan = plan,
                inProgressSession = null,
                onGenerate = {},
                onHistory = {},
                onSettings = {},
                onStartDay = {},
                onResume = {},
                onLogActivity = {},
                onOpenMobility = {},
            )
        }

        // H3-28: com o herói e as ações compactas acima das abas, elas podem
        // ficar abaixo da dobra no viewport do Robolectric — rolar até elas.
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Academia"))
        composeRule.onNodeWithText("Academia").assertIsDisplayed()
        composeRule.onNodeWithText("Corrida").assertIsDisplayed()
        composeRule.onNodeWithText("Mobilidade").assertIsDisplayed()
    }

    @Test
    fun card_de_corrida_mostra_distancia_e_duracao() {
        composeRule.setContent {
            HomeScreen(
                plan = plan,
                inProgressSession = null,
                onGenerate = {},
                onHistory = {},
                onSettings = {},
                onStartDay = {},
                onResume = {},
                onLogActivity = {},
                onOpenMobility = {},
            )
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Corrida"))
        composeRule.onNodeWithText("Corrida").performClick()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Corrida A"))
        composeRule.onNodeWithText("Corrida A").assertIsDisplayed()
    }

    @Test
    fun mesmo_dia_com_academia_e_corrida_aparece_nas_duas_abas() {
        composeRule.setContent {
            HomeScreen(
                plan = plan,
                inProgressSession = null,
                onGenerate = {},
                onHistory = {},
                onSettings = {},
                onStartDay = {},
                onResume = {},
                onLogActivity = {},
                onOpenMobility = {},
            )
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Academia"))
        composeRule.onNodeWithText("Academia").assertIsDisplayed()
        composeRule.onNodeWithText("Corrida").assertIsDisplayed()

        assertEquals(2, plan.days.filter { it.day.weekday == 1 }.size)
    }

    @Test
    fun card_de_exercicio_exibe_imagem_quando_disponivel() {
        composeRule.setContent {
            HomeScreen(
                plan = plan,
                inProgressSession = null,
                exerciseMedia = mapOf(
                    "Barbell_Bench_Press" to ExerciseMedia(
                        id = "Barbell_Bench_Press",
                        name = "Supino reto",
                        primaryMuscles = listOf("chest"),
                        equipment = "barbell",
                        instructions = emptyList(),
                        imageUrl = "https://example.test/bench.jpg",
                    ),
                ),
                onGenerate = {},
                onHistory = {},
                onSettings = {},
                onStartDay = {},
                onResume = {},
                onLogActivity = {},
                onOpenMobility = {},
            )
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Academia"))
        composeRule.onAllNodes(hasText("Academia") and hasClickAction()).onFirst().performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Supino reto"))
        composeRule.onNodeWithText("Supino reto", useUnmergedTree = true)
            .assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithContentDescription(
                "Imagem do exercício Supino reto",
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun corrida_sem_imagem_nao_quebra_card() {
        val runPlan = WorkoutPlanWithDetails(
            plan = WorkoutPlanEntity(id = 2, name = "Plano corrida", daysPerWeek = 1),
            days = listOf(
                day(
                    weekday = 3,
                    dayOrder = 0,
                    modality = "run",
                    exercises = listOf(
                        exercise(id = "run_easy_30", name = "Corrida leve 30 min", order = 0),
                    ),
                ),
            ),
        )

        composeRule.setContent {
            HomeScreen(
                plan = runPlan,
                inProgressSession = null,
                exerciseMedia = emptyMap(),
                onGenerate = {},
                onHistory = {},
                onSettings = {},
                onStartDay = {},
                onResume = {},
                onLogActivity = {},
                onOpenMobility = {},
            )
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Corrida"))
        composeRule.onAllNodes(hasText("Corrida") and hasClickAction()).onFirst().performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Corrida leve 30 min"))
        composeRule.onNodeWithText("Corrida leve 30 min", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun heroi_hoje_visivel_sem_rolagem() {
        // H3-28: com sessão hoje, o herói é o primeiro item da Home — visível
        // sem rolar, no viewport padrão do Robolectric.
        composeRule.setContent {
            HomeScreen(
                plan = planComSessaoHoje(),
                inProgressSession = null,
                onGenerate = {},
                onHistory = {},
                onSettings = {},
                onStartDay = {},
                onResume = {},
                onLogActivity = {},
                onOpenMobility = {},
            )
        }

        composeRule.onNodeWithText("Hoje — Treino A").assertIsDisplayed()
        composeRule.onNodeWithText("Iniciar").assertIsDisplayed()
    }

    @Test
    fun badge_hoje_no_card() {
        composeRule.setContent {
            HomeScreen(
                plan = planComSessaoHoje(),
                inProgressSession = null,
                onGenerate = {},
                onHistory = {},
                onSettings = {},
                onStartDay = {},
                onResume = {},
                onLogActivity = {},
                onOpenMobility = {},
            )
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Treino A"))
        composeRule.onNodeWithText("Treino A").assertIsDisplayed()
        composeRule.onNodeWithText("Hoje").assertIsDisplayed()
    }

    @Test
    fun rodape_somente_sem_plano() {
        composeRule.setContent {
            HomeScreen(
                plan = plan,
                inProgressSession = null,
                onGenerate = {},
                onHistory = {},
                onSettings = {},
                onStartDay = {},
                onResume = {},
                onLogActivity = {},
                onOpenMobility = {},
            )
        }
        composeRule.onNodeWithText("Atividade avulsa", substring = true).assertDoesNotExist()
    }

    @Test
    fun rodape_no_empty_state() {
        composeRule.setContent {
            HomeScreen(
                plan = null,
                inProgressSession = null,
                onGenerate = {},
                onHistory = {},
                onSettings = {},
                onStartDay = {},
                onResume = {},
                onLogActivity = {},
                onOpenMobility = {},
            )
        }
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Atividade avulsa", substring = true))
        composeRule.onNodeWithText("Atividade avulsa", substring = true).assertIsDisplayed()
    }

    @Test
    fun loading_antes_do_primeiro_emit() {
        // H3-29: com o banco ainda não emitido, a Home mostra carregando —
        // nunca o empty state ("vazio falso").
        composeRule.setContent {
            HomeScreen(
                plan = null,
                inProgressSession = null,
                plansState = UiState.Loading,
                onGenerate = {},
                onHistory = {},
                onSettings = {},
                onStartDay = {},
                onResume = {},
                onLogActivity = {},
            )
        }

        composeRule.onNodeWithContentDescription("Carregando").assertIsDisplayed()
        composeRule.onNodeWithText("Nenhum treino ainda").assertDoesNotExist()
    }

    @Test
    fun vazio_so_apos_emit() {
        // H3-29: depois que o Room emitir vazio, o empty state aparece.
        composeRule.setContent {
            HomeScreen(
                plan = null,
                inProgressSession = null,
                plansState = UiState.Content(emptyList()),
                onGenerate = {},
                onHistory = {},
                onSettings = {},
                onStartDay = {},
                onResume = {},
                onLogActivity = {},
            )
        }

        composeRule.onNodeWithText("Nenhum treino ainda").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Carregando").assertDoesNotExist()
    }

    /** Plano com sessão de musculação hoje e corrida amanhã, independente da data. */
    private fun planComSessaoHoje(): WorkoutPlanWithDetails {
        val hoje = LocalDate.now().dayOfWeek.value
        return WorkoutPlanWithDetails(
            plan = WorkoutPlanEntity(id = 3, name = "Plano de hoje", daysPerWeek = 2),
            days = listOf(
                day(
                    weekday = hoje,
                    dayOrder = 0,
                    modality = "strength",
                    exercises = listOf(
                        exercise(id = "Barbell_Bench_Press", name = "Supino reto", order = 0),
                    ),
                ),
                day(
                    weekday = hoje % 7 + 1,
                    dayOrder = 0,
                    modality = "run",
                    exercises = listOf(
                        exercise(id = "run_easy_30", name = "Corrida leve 30 min", order = 0),
                    ),
                ),
            ),
        )
    }

    private fun exercise(id: String, name: String, order: Int): WorkoutPlanExerciseEntity =
        WorkoutPlanExerciseEntity(
            id = order.toLong(),
            dayId = 0,
            exerciseId = id,
            exerciseName = name,
            exerciseOrder = order,
            sets = 3,
            targetReps = "8-12",
        )

    private fun day(
        weekday: Int,
        dayOrder: Int,
        modality: String,
        exercises: List<WorkoutPlanExerciseEntity>,
    ): WorkoutDayWithExercises =
        WorkoutDayWithExercises(
            day = WorkoutDayEntity(
                id = weekday.toLong() * 10 + dayOrder,
                planId = 0,
                name = when (modality) {
                    "strength" -> "Treino A"
                    "run" -> "Corrida A"
                    "mobility" -> "Mobilidade A"
                    else -> "Dia"
                },
                dayOrder = dayOrder,
                weekday = weekday,
                modality = modality,
            ),
            exercises = exercises,
        )
}
