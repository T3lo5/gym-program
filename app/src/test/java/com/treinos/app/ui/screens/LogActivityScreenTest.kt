package com.treinos.app.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.treinos.app.ui.ActivityLogDraft
import com.treinos.app.ui.ActivityLogField
import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * H3-47: erros inline nos campos do registro avulso e submit bloqueado enquanto
 * houver algum erro. A tela recebe os erros do ViewModel (já validados em
 * `validateActivityDraft` em `ActivityLog.kt`); este teste cobre a
 * apresentação, não a regra.
 */
@RunWith(RobolectricTestRunner::class)
class LogActivityScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setPtLocale() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val conf = context.resources.configuration
        conf.setLocale(Locale("pt", "BR"))
        context.resources.updateConfiguration(conf, context.resources.displayMetrics)
    }

    private fun screen(
        errors: Map<ActivityLogField, String> = emptyMap(),
        isSaving: Boolean = false,
        onSave: (ActivityLogDraft) -> Unit = {},
    ) = composeRule.setContent {
        LogActivityScreen(
            prefill = null,
            editingSessionId = null,
            isSaving = isSaving,
            errors = errors,
            canCopyLast = false,
            onBack = {},
            onModalityChanged = {},
            onCopyLast = {},
            onSave = onSave,
            onDelete = {},
        )
    }

    private fun saveButton() =
        composeRule.onNode(hasClickAction() and hasText("Salvar", substring = true, ignoreCase = true))

    @Test
    fun erro_inline_no_campo_de_duracao() {
        screen(
            errors = mapOf(ActivityLogField.Duration to "Duração entre 1 e 600 minutos."),
        )
        composeRule.onNodeWithText("Duração entre 1 e 600 minutos.").assertIsDisplayed()
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Salvar", substring = true))
        saveButton().assertIsNotEnabled()
    }

    @Test
    fun salvar_desabilitado_ou_bloqueado_enquanto_invalido() {
        var saved: ActivityLogDraft? = null
        screen(
            errors = mapOf(ActivityLogField.Duration to "Duração entre 1 e 600 minutos."),
            onSave = { saved = it },
        )
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Salvar", substring = true))
        saveButton().performClick()
        composeRule.waitForIdle()
        // O erro inline permanece e nada foi gravado. A tela é uma LazyColumn:
        // rolar para o erro dispensa o item do botão, então o botão é trazido
        // de volta antes do assert de estado.
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Duração entre 1 e 600 minutos."))
        composeRule.onNodeWithText("Duração entre 1 e 600 minutos.").assertIsDisplayed()
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Salvar", substring = true))
        saveButton().assertIsNotEnabled()
        assertNull(saved)
    }

    @Test
    fun sem_erros_salvar_habilitado() {
        screen(errors = emptyMap())
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Salvar", substring = true))
        saveButton().assertIsEnabled()
    }
}