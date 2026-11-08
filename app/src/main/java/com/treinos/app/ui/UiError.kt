package com.treinos.app.ui

import com.treinos.app.data.remote.BackendConnectionException
import com.treinos.app.data.remote.BackendHttpException
import com.treinos.app.data.remote.BackendResponseException
import com.treinos.app.data.remote.BackendTimeoutException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.SerializationException

/**
 * Estado padrão de tela (H3-29): [Loading] até a **primeira** emissão da fonte
 * (Room/repositório), [Empty] só quando a fonte emitir vazio e [Content] com
 * dados. Mata o "vazio falso" — Home e Histórico mostravam o empty state nos
 * primeiros frames porque o valor inicial de `stateIn` já era a lista vazia.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data object Empty : UiState<Nothing>
    data class Content<T>(val data: T) : UiState<T>
}

/**
 * Erro persistente e acionável (H3-29): fica na tela de origem até o usuário
 * tentar de novo ou voltar — em vez de sumir com o snackbar de ~4 s. O título
 * diz qual ação falhou; a mensagem diz o porquê em linguagem de usuário.
 */
data class UiError(
    val title: String,
    val message: String,
    val retryLabel: String? = "Tentar de novo",
)

private const val OFFLINE_MESSAGE = "Sem conexão com o servidor. Verifique o Wi-Fi e tente de novo."
private const val TIMEOUT_MESSAGE =
    "O servidor demorou demais para responder. Verifique a conexão e tente de novo."
private const val PARSE_MESSAGE = "Resposta inesperada do servidor. Tente de novo."
private const val COMMS_MESSAGE = "Falha de comunicação com o servidor. Verifique a conexão e tente de novo."

/**
 * Traduz uma exceção para [UiError] com mensagem curta e acionável. O detalhe
 * técnico (corpo HTTP cru, stack) nunca volta aqui: quem captura registra o
 * throwable no Logcat, e o corpo cru do backend é logado no próprio
 * `BackendClient`.
 *
 * A ordem do `when` importa: as exceções de backend e `SocketTimeoutException`
 * são subclasses de [IOException] e precisam ser testadas antes.
 */
fun uiErrorFrom(throwable: Throwable, contexto: String): UiError = UiError(
    title = "Não foi possível $contexto",
    message = knownErrorMessage(throwable) ?: "Ocorreu um erro inesperado. Tente de novo.",
    retryLabel = "Tentar de novo",
)

/**
 * Mensagem curta para os caminhos que continuam de snackbar: erros de
 * rede/backend recebem o texto acionável; erros locais (Room, validação)
 * mantêm o texto específico da ação via [fallback].
 */
fun uiErrorMessage(throwable: Throwable, fallback: String): String =
    knownErrorMessage(throwable) ?: fallback

private fun knownErrorMessage(throwable: Throwable): String? = when (throwable) {
    is BackendHttpException ->
        "O servidor respondeu com erro (${throwable.code}). Tente de novo em instantes."
    is BackendTimeoutException, is SocketTimeoutException -> TIMEOUT_MESSAGE
    is BackendConnectionException, is UnknownHostException, is ConnectException -> OFFLINE_MESSAGE
    is BackendResponseException, is SerializationException -> PARSE_MESSAGE
    is IOException -> COMMS_MESSAGE
    else -> null
}
