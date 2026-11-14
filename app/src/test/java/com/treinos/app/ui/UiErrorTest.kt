package com.treinos.app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.treinos.app.R
import com.treinos.app.data.remote.BackendHttpException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * H3-29: o mapeamento de exceção → mensagem acionável é puro no mapeamento e
 * resolve os textos via recursos (H3-52) — o teste roda com Robolectric em
 * pt-BR para ler exatamente os textos que o usuário vê em português.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-rBR")
class UiErrorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun io_sem_rede_mensagem_acionavel() {
        val error = uiErrorFrom(
            UnknownHostException("Unable to resolve host 'backend.local'"),
            context,
            R.string.verb_generate_workout,
        )

        assertEquals("Não foi possível gerar o treino", error.title)
        assertTrue(error.message.contains("Sem conexão com o servidor"))
        assertTrue(error.message.contains("Wi-Fi"))
        // O detalhe cru da exceção não vaza para a UI.
        assertTrue(!error.message.contains("Unable to resolve"))
        assertEquals("Tentar de novo", error.retryLabel)
    }

    @Test
    fun http_500_mensagem_curta_sem_corpo() {
        // BackendHttpException é construído como no BackendClient: o corpo cru
        // fica no Logcat e não entra na mensagem.
        val error = uiErrorFrom(
            BackendHttpException(500, "Backend HTTP 500: {\"detail\":\"boom\"}"),
            context,
            R.string.verb_generate_workout,
        )

        assertTrue(error.message.contains("500"))
        assertTrue(error.message.contains("servidor"))
        assertTrue(error.message.length < 120)
        assertTrue(!error.message.contains("{"))
        assertTrue(!error.message.contains("<html"))
        assertEquals("Não foi possível gerar o treino", error.title)
    }

    @Test
    fun timeout_distinto_de_sem_rede() {
        val timeout = uiErrorFrom(SocketTimeoutException("Read timed out"), context, R.string.verb_send_to_backend)
        val offline = uiErrorFrom(UnknownHostException("host"), context, R.string.verb_send_to_backend)

        assertEquals("Não foi possível enviar ao backend", timeout.title)
        assertTrue(timeout.message.contains("demorou demais"))
        assertTrue(offline.message.contains("Sem conexão"))
        assertNotEquals(timeout.message, offline.message)
    }

    @Test
    fun erro_local_mantem_o_texto_da_acao_no_snackbar() {
        // uiErrorMessage: erros locais (Room, validação) mantêm o texto
        // específico da ação; erros de rede recebem o texto acionável.
        val local = uiErrorMessage(IllegalStateException("corrupted"), context, R.string.msg_save_set_error)
        val rede = uiErrorMessage(UnknownHostException("host"), context, R.string.msg_save_set_error)

        assertEquals("Não foi possível salvar a série.", local)
        assertTrue(rede.contains("Sem conexão"))
    }
}
