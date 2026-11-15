package com.treinos.app.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.treinos.app.data.media.ExerciseMedia
import com.treinos.app.ui.components.ExerciseDetailSheet
import com.treinos.app.ui.progression.PersonalRecord
import java.util.Locale
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * H4-1: seção "Melhor de todos os tempos" da folha de detalhes do exercício.
 */
@RunWith(RobolectricTestRunner::class)
class ExerciseDetailSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setPtLocale() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val conf = context.resources.configuration
        conf.setLocale(Locale("pt", "BR"))
        context.resources.updateConfiguration(conf, context.resources.displayMetrics)
    }

    private fun media() = ExerciseMedia(
        id = "Barbell_Bench_Press",
        name = "Supino reto",
        primaryMuscles = listOf("chest"),
        equipment = "barbell",
        instructions = emptyList(),
        imageUrl = null,
    )

    @Test
    fun detalhe_mostra_melhor_de_todos_os_tempos() {
        composeRule.setContent {
            ExerciseDetailSheet(
                media = media(),
                record = PersonalRecord(
                    exerciseId = "Barbell_Bench_Press",
                    weightKg = 60.0,
                    reps = 10,
                    estimated1RmKg = 80.0,
                    achievedAtMillis = 1_000L,
                ),
                onDismiss = {},
            )
        }

        // Na janela estreita do Robolectric a seção fica abaixo da dobra da
        // folha; o conteúdo está composto (Column com scroll), então a
        // presença na árvore é o que a spec pede aqui.
        composeRule.onNodeWithText("Melhor de todos os tempos").assertExists()
        composeRule.onNodeWithText("60,0 kg × 10 reps").assertExists()
        composeRule.onNodeWithText("1RM estimado: 80,0 kg").assertExists()
    }

    @Test
    fun detalhe_sem_registro_mostra_estado_vazio() {
        composeRule.setContent {
            ExerciseDetailSheet(
                media = media(),
                record = null,
                onDismiss = {},
            )
        }

        composeRule.onNodeWithText("Melhor de todos os tempos").assertExists()
        composeRule.onNodeWithText("Sem registro ainda.").assertExists()
    }
}
