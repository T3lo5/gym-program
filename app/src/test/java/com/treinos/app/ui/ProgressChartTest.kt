package com.treinos.app.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.treinos.app.ui.components.ProgressLineChart
import com.treinos.app.ui.components.ProgressBarChart
import com.treinos.app.ui.history.MuscleBar
import com.treinos.app.ui.history.ProgressPoint
import java.util.Locale
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * H4-2: semântica de acessibilidade e estados vazios dos gráficos em
 * `Canvas` — o resumo textual é obrigatório (regra 14 da spec) e um ponto
 * isolado não desenha linha.
 */
@RunWith(RobolectricTestRunner::class)
class ProgressChartTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setPtLocale() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val conf = context.resources.configuration
        conf.setLocale(Locale("pt", "BR"))
        context.resources.updateConfiguration(conf, context.resources.displayMetrics)
    }

    private fun point(sessionId: Long, value: Double) = ProgressPoint(
        sessionId = sessionId,
        startedAtMillis = sessionId,
        label = "10/11",
        value = value,
    )

    @Test
    fun linha_tem_descricao_de_acessibilidade() {
        val description = "Supino reto — melhor série (3 treinos): 40,0 kg em 10/11 até 45,0 kg em 20/11"
        composeRule.setContent {
            ProgressLineChart(
                points = listOf(point(1, 40.0), point(2, 42.5), point(3, 45.0)),
                valueLabel = { formatKg(it) },
                contentDescription = description,
            )
        }

        composeRule.onNodeWithContentDescription(description).assertIsDisplayed()
        composeRule.onNodeWithText("Poucos dados para um gráfico").assertDoesNotExist()
    }

    @Test
    fun barras_tem_descricao_por_musculo() {
        val description = "Volume por músculo na semana: Peito: 400 kg • 2 série(s); Costas: 200 kg • 1 série(s)"
        composeRule.setContent {
            ProgressBarChart(
                bars = listOf(
                    MuscleBar(muscle = "Peito", volumeKg = 400.0, sets = 2),
                    MuscleBar(muscle = "Costas", volumeKg = 200.0, sets = 1),
                ),
                valueLabel = { formatVolumeKg(it) },
                contentDescription = description,
            )
        }

        composeRule.onNodeWithContentDescription(description).assertIsDisplayed()
    }

    @Test
    fun grafico_vazio_mostra_estado() {
        composeRule.setContent {
            // Um ponto isolado não é tendência: estado, sem linha.
            ProgressLineChart(
                points = listOf(point(1, 40.0)),
                valueLabel = { formatKg(it) },
                contentDescription = "linha",
            )
            ProgressBarChart(
                bars = emptyList(),
                valueLabel = { formatVolumeKg(it) },
                contentDescription = "barras",
            )
        }

        composeRule.onNodeWithText("Poucos dados para um gráfico").assertIsDisplayed()
        composeRule.onNodeWithText("Sem treinos de musculação na semana").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("linha").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("barras").assertDoesNotExist()
    }
}
