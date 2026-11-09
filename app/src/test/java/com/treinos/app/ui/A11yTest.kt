package com.treinos.app.ui

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.treinos.app.ui.components.ScreenTopBar
import com.treinos.app.ui.components.statusIsSuccess
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import com.treinos.app.ui.screens.GenerateScreen
import com.treinos.app.ui.screens.MobilityScreen
import com.treinos.app.ui.screens.WorkoutScreen
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionExerciseWithSets
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.WorkoutSessionEntity
import com.treinos.app.ui.MobilityScreenState
import com.treinos.app.ui.MobilitySessionState
import com.treinos.app.ui.MobilityRoutine
import com.treinos.app.ui.MobilityMovement
import com.treinos.app.data.media.ExerciseMedia
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
class A11yTest {
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
    fun descricao_do_exercicio_inclui_nome() {
        assertEquals("Exercício Supino reto", exerciseContentDescription("Supino reto"))
    }

    @Test
    fun descricao_de_serie_completa() {
        assertEquals(
            "Série 2, 10 repetições, 40 quilos, RPE 8",
            setContentDescription(2, 10, 40.0, 8.0),
        )
    }

    @Test
    fun descricao_de_serie_sem_carga_nao_deixa_virgula_solta() {
        assertEquals("Série 1, 8 repetições", setContentDescription(1, 8, null, null))
        assertEquals("Série 3", setContentDescription(3, null, null, null))
    }

    @Test
    fun progresso_anuncia_concluidas_e_total() {
        assertEquals("2 de 4 séries concluídas", progressStateDescription(2, 4))
    }

    @Test
    fun descanso_anuncia_segundos() {
        assertEquals("Descanso: 45 segundos restantes de 90", restStateDescription(45, 90))
    }

    @Test
    fun timer_anuncia_apenas_marcos() {
        assertTrue(timerShouldAnnounce(60))
        assertTrue(timerShouldAnnounce(30))
        assertTrue(timerShouldAnnounce(10))
        assertTrue(timerShouldAnnounce(0))
        assertFalse(timerShouldAnnounce(59))
        assertFalse(timerShouldAnnounce(31))
        assertFalse(timerShouldAnnounce(11))
        assertFalse(timerShouldAnnounce(1))
    }

    @Test
    fun revisao_atrasada_muda_texto() {
        assertEquals(
            "Revisão atrasada desde 01/12/2026",
            reviewStateDescription("01/12/2026", true),
        )
        assertEquals(
            "Próxima revisão em 01/12/2026",
            reviewStateDescription("01/12/2026", false),
        )
    }

    @Test
    fun content_descriptions_localizados() {
        // H3-37: content descriptions seguem o locale do app.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val res = context.resources
        val originalLocale = res.configuration.locale
        try {
            res.configuration.locale = Locale("en", "US")
            composeRule.setContent {
                ScreenTopBar(title = "Tela", onBack = {})
            }
            composeRule.onNodeWithContentDescription("Voltar").assertExists()
        } finally {
            res.configuration.locale = originalLocale
        }
    }

    @Test
    fun icone_decorativo_tem_descricao_nula() {
        assertNull(decorativeContentDescription())
    }

    @Test
    fun exercicio_sem_nome_usa_fallback() {
        assertEquals("Exercício sem nome", exerciseContentDescription(""))
    }

    @Test
    fun status_text_erro_distinto() {
        // H3-32: só prefixos de sucesso são verdes; falha/validação/dado ausente
        // caem no visual de erro (vermelho + alerta), distinto do sucesso.
        assertTrue(statusIsSuccess("Conectado. Modelo: x • Catálogo: 1 exercícios."))
        assertTrue(statusIsSuccess("Enviado: 1 plano(s), 0 treino(s) e 0 série(s)."))
        assertTrue(statusIsSuccess("Restaurado: 2 plano(s) e 3 sessão(ões) do backend."))
        assertTrue(statusIsSuccess("Backup importado com sucesso."))
        assertTrue(statusIsSuccess("Catálogo atualizado em 08/11/2026 10:00"))
        assertFalse(statusIsSuccess("Falha ao conectar."))
        assertFalse(statusIsSuccess("Sem conexão com o servidor. Verifique o Wi-Fi e tente de novo."))
        assertFalse(statusIsSuccess("Informe um ID de aparelho válido."))
        assertFalse(statusIsSuccess("Nenhum dado encontrado; seus dados locais foram mantidos."))
    }

    @Test
    fun checkbox_row_tem_role_e_rotulo() {
        composeRule.setContent {
            CheckboxRowDemo(included = true, onToggle = {})
        }
        composeRule.onNodeWithContentDescription("Incluir Musculação").assertExists()
    }

    @Test
    fun liveRegion_anuncia_inicio_do_descanso() {
        composeRule.setContent {
            WorkoutScreen(
                session = sessaoComRest(),
                restRemaining = 90,
                restTotal = 90,
                onLeave = {},
                onLogSet = { _, _, _, _ -> },
                onSkipRest = {},
                onFinish = {},
                hasInProgressSession = true,
            )
        }
        composeRule.onNodeWithText("Descanso de 01:30 s iniciado").assertExists()
    }

    @Test
    fun liveRegion_anuncia_marco_do_timer_mobilidade() {
        val rotina = MobilityRoutine(
            id = "r1",
            name = "Rotina",
            focus = "Costas",
            description = "d",
            movements = listOf(MobilityMovement("Gato-Camelo", 60, cue = "Faça")),
        )
        val sessaoMob = MobilitySessionState(
            routineId = "r1",
            movementIndex = 0,
            secondsRemaining = 60,
            running = true,
        )
        composeRule.setContent {
            MobilityScreen(
                state = MobilityScreenState(routine = rotina, session = sessaoMob),
                onBack = {},
            )
        }
        composeRule.onNodeWithText("Temporizador 1 min").assertExists()
    }

    @Test
    fun campo_sessoes_tem_supporting_text() {
        composeRule.setContent {
            GenerateScreen(
                isGenerating = false,
                onBack = {},
                onGenerate = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
            )
        }
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Sessões por semana"))
        composeRule.onNodeWithText("Inteiro de 1 a 7").assertIsDisplayed()
    }

    private fun sessaoComRest(): SessionWithExercises {
        return SessionWithExercises(
            session = WorkoutSessionEntity(
                id = 1,
                planId = 1,
                dayId = 10,
                planName = "Plano",
                dayName = "Treino",
                status = "in_progress",
            ),
            exercises = listOf(
                SessionExerciseWithSets(
                    exercise = SessionExerciseEntity(
                        id = 100,
                        sessionId = 1,
                        exerciseId = "x",
                        exerciseName = "Ex",
                        exerciseOrder = 0,
                        plannedSets = 3,
                        completedSets = 0,
                        restSeconds = 90,
                    ),
                    sets = emptyList(),
                ),
            ),
        )
    }

    @Composable
    private fun CheckboxRowDemo(
        included: Boolean,
        onToggle: () -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .toggleable(
                    value = included,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() },
                )
                .semantics {
                    stateDescription = if (included) "Ligado" else "Desligado"
                    contentDescription = "Incluir Musculação"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = included,
                onCheckedChange = { onToggle() },
                modifier = Modifier.clearAndSetSemantics { },
            )
            Text(text = "Musculação")
        }
    }
}

/** IconButton decorativo/acionável carrega contentDescription para o TalkBack. */
@RunWith(RobolectricTestRunner::class)
class A11yComposeTest {
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
    fun iconbuttons_tem_content_description() {
        composeRule.setContent {
            ScreenTopBar(title = "Tela", onBack = {})
        }
        composeRule.onNodeWithContentDescription("Voltar").assertExists()
    }
}
