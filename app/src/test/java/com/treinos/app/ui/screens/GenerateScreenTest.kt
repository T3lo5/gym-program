package com.treinos.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.treinos.app.data.remote.ModalityDto
import com.treinos.app.ui.GenerateConfig
import com.treinos.app.ui.ModalityDraft
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * H3-30: geração sem atrito — defaults, CTA fixo com hint, seções de força
 * condicionais e overlay de progresso com cancelar.
 *
 * H3-47: erros de perfil vêm inline no campo (supportingText/isError) e o
 * submit é bloqueado enquanto houver algum erro.
 */
@RunWith(RobolectricTestRunner::class)
class GenerateScreenTest {

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
    fun cta_fixo_e_hint_juntos() {
        var captured: GenerateConfig? = null
        composeRule.setContent {
            GenerateScreen(
                isGenerating = false,
                onBack = {},
                onGenerate = { _, _, _, _, _, _, _, _, _, _, _, _, _, config ->
                    captured = config
                },
            )
        }

        // Primeira abertura sem config salva: musculação 4x pré-inclusa e o
        // CTA nasce habilitado, visível sem rolar (barra fixa no fim).
        composeRule.onNodeWithText("Gerar semana híbrida")
            .assertIsDisplayed()
            .assertIsEnabled()

        // Desligar a única modalidade: o hint de validação aparece JUNTO do
        // CTA fixo — sem rolar até o fim do formulário para saber o porquê.
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasContentDescription("Incluir Musculação"))
        composeRule.onNodeWithContentDescription("Incluir Musculação").performClick()
        composeRule.onNodeWithText("Inclua pelo menos uma modalidade.").assertIsDisplayed()
        composeRule.onNodeWithText("Gerar semana híbrida")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun secoes_forca_ocultas_sem_forca() {
        composeRule.setContent {
            GenerateScreen(isGenerating = false, onBack = {}, onGenerate = ::onGenerateNoop)
        }

        // Com musculação inclusa (default), as seções de força existem.
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Objetivo (musculação)"))
        composeRule.onNodeWithText("Objetivo (musculação)").assertIsDisplayed()

        // Só corrida + mobilidade: as seções de força somem.
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasContentDescription("Incluir Musculação"))
        composeRule.onNodeWithContentDescription("Incluir Musculação").performClick()
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasContentDescription("Incluir Corrida"))
        composeRule.onNodeWithContentDescription("Incluir Corrida").performClick()
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasContentDescription("Incluir Mobilidade"))
        composeRule.onNodeWithContentDescription("Incluir Mobilidade").performClick()
        composeRule.waitForIdle()

        // Borda de cima do bloco condicional (logo após os cards).
        composeRule.onNodeWithText("Objetivo (musculação)").assertDoesNotExist()
        // Borda de baixo: rolar até "Dias por semana" — a seção de duração,
        // quando existe, é composta logo acima dela.
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Dias por semana"))
        composeRule.onNodeWithText("Duração da sessão").assertDoesNotExist()
        composeRule.onNodeWithText("Equipamentos disponíveis").assertDoesNotExist()
    }

    @Test
    fun goal_do_chip_vai_na_modalidade_de_forca() {
        var capturedGoal: String? = null
        var capturedStrengthGoal: String? = null
        composeRule.setContent {
            GenerateScreen(
                isGenerating = false,
                onBack = {},
                onGenerate = { modalities, goal, _, _, _, _, _, _, _, _, _, _, _, _ ->
                    capturedGoal = goal
                    capturedStrengthGoal = modalities.first { it.type == "strength" }.goal
                },
            )
        }

        // O usuário escolhe "Força" no chip de objetivo e gera.
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Força"))
        composeRule.onNodeWithText("Força").performClick()
        composeRule.onNodeWithText("Gerar semana híbrida").performClick()
        composeRule.waitForIdle()

        assertEquals("strength", capturedGoal)
        // H3-30: a modalidade de força segue o chip — não mais "hypertrophy".
        assertEquals("strength", capturedStrengthGoal)
    }

    @Test
    fun config_salva_restaurada_no_formulario() {
        val saved = GenerateConfig(
            planKind = "main",
            goal = "strength",
            daysPerWeek = 5,
            level = "advanced",
            equipment = listOf("barbell"),
            muscles = listOf("chest"),
            sessionDurationMinutes = 90,
            reviewWeeks = 6,
            environment = "home",
            modalities = listOf(
                ModalityDraft(type = "strength", included = false, sessionsPerWeek = 2),
                ModalityDraft(type = "run", included = true, sessionsPerWeek = 3, recent5kSeconds = 1500),
                ModalityDraft(type = "mobility"),
            ),
        )
        composeRule.setContent {
            GenerateScreen(
                isGenerating = false,
                onBack = {},
                savedConfig = saved,
                onGenerate = ::onGenerateNoop,
            )
        }

        // Musculação desligada na config salva: seções de força ocultas.
        composeRule.onNodeWithText("Objetivo (musculação)").assertDoesNotExist()

        // Valores restaurados: corrida inclusa com benchmark, musculação off.
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasContentDescription("Incluir Corrida"))
        composeRule.onNodeWithContentDescription("Incluir Corrida").assertIsOn()
        composeRule.onNodeWithContentDescription("Incluir Musculação").assertIsOff()
        composeRule.onNodeWithText("1500").assertIsDisplayed()

        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Dias por semana"))
        composeRule.onNodeWithText("5x").assertIsDisplayed()

        // Há modalidade inclusa: o CTA restaurado nasce habilitado.
        composeRule.onNodeWithText("Gerar semana híbrida").assertIsEnabled()
    }

    @Test
    fun campo_peso_invalido_mostra_erro_inline_e_bloqueia() {
        var gerou = false
        composeRule.setContent {
            GenerateScreen(
                isGenerating = false,
                onBack = {},
                onGenerate = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> gerou = true },
            )
        }

        // Expande "Mais opções" para ver o perfil.
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Mais opções"))
        composeRule.onNodeWithText("Mais opções").performClick()
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Peso (kg)"))
        // Peso inválido: o erro aparece junto ao campo e o CTA é desabilitado.
        composeRule.onNodeWithText("Peso (kg)").performTextReplacement("abc")
        composeRule.onNodeWithText("Peso inválido.").assertIsDisplayed()
        composeRule.onNodeWithText("Gerar semana híbrida").assertIsNotEnabled()

        // Corrige o peso: o erro some e o CTA volta a ficar habilitado.
        composeRule.onNodeWithText("Peso (kg)").performTextReplacement("75")
        composeRule.onNodeWithText("Peso inválido.").assertDoesNotExist()
        composeRule.onNodeWithText("Gerar semana híbrida").assertIsEnabled()

        // Mesmo com o perfil válido, o submit não é acionado com dados inválidos.
        composeRule.onNodeWithText("Peso (kg)").performTextReplacement("abc")
        composeRule.onNodeWithText("Gerar semana híbrida").performClick()
        composeRule.waitForIdle()
        assertFalse(gerou)
    }

    @Test
    fun submit_bloqueado_com_erro_de_perfil() {
        var gerou = false
        composeRule.setContent {
            GenerateScreen(
                isGenerating = false,
                onBack = {},
                onGenerate = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> gerou = true },
            )
        }
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Mais opções"))
        composeRule.onNodeWithText("Mais opções").performClick()
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Altura (cm)"))
        composeRule.onNodeWithText("Altura (cm)").performTextReplacement("9999")
        composeRule.onNodeWithText("Altura fora da faixa permitida (120–250 cm).")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Gerar semana híbrida").assertIsNotEnabled()
        composeRule.onNodeWithText("Gerar semana híbrida").performClick()
        composeRule.waitForIdle()
        assertFalse(gerou)
    }

    private fun onGenerateNoop(
        @Suppress("UNUSED_PARAMETER") modalities: List<ModalityDto>,
        @Suppress("UNUSED_PARAMETER") goal: String,
        @Suppress("UNUSED_PARAMETER") daysPerWeek: Int,
        @Suppress("UNUSED_PARAMETER") level: String,
        @Suppress("UNUSED_PARAMETER") equipment: List<String>,
        @Suppress("UNUSED_PARAMETER") muscles: List<String>,
        @Suppress("UNUSED_PARAMETER") sessionDuration: Int,
        @Suppress("UNUSED_PARAMETER") reviewWeeks: Int,
        @Suppress("UNUSED_PARAMETER") bodyWeightKgText: String,
        @Suppress("UNUSED_PARAMETER") heightCmText: String,
        @Suppress("UNUSED_PARAMETER") sexChoice: String?,
        @Suppress("UNUSED_PARAMETER") environment: String?,
        @Suppress("UNUSED_PARAMETER") kind: String,
        @Suppress("UNUSED_PARAMETER") config: GenerateConfig,
    ) {
        // no-op
    }
}
