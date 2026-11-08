package com.treinos.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.treinos.app.ui.components.ScreenTopBar
import com.treinos.app.ui.components.statusIsSuccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

class A11yTest {
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
}

/** IconButton decorativo/acionável carrega contentDescription para o TalkBack. */
@RunWith(RobolectricTestRunner::class)
class A11yComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun iconbuttons_tem_content_description() {
        composeRule.setContent {
            ScreenTopBar(title = "Tela", onBack = {})
        }
        composeRule.onNodeWithContentDescription("Voltar").assertExists()
    }
}
