package com.treinos.app.ui

import android.content.Context
import com.treinos.app.R
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
 *
 * H3-52: os textos nascem de recursos — a construção resolve via [Context]
 * (`application.getString`), porque o erro é montado fora da composição.
 */
data class UiError(
    val title: String,
    val message: String,
    val retryLabel: String? = null,
)

/**
 * Erro de rede/backend conhecido, como par recurso + argumentos: a tradução
 * acontece onde há [Context], nunca aqui (função pura, testável em JVM).
 */
data class KnownError(val resId: Int, val args: List<Any> = emptyList())

fun KnownError.resolve(context: Context): String =
    context.getString(resId, *args.toTypedArray())

/**
 * Traduz uma exceção para [UiError] com mensagem curta e acionável. O detalhe
 * técnico (corpo HTTP cru, stack) nunca volta aqui: quem captura registra o
 * throwable no Logcat, e o corpo cru do backend é logado no próprio
 * `BackendClient`.
 *
 * A ordem do `when` importa: as exceções de backend e `SocketTimeoutException`
 * são subclasses de [IOException] e precisam ser testadas antes.
 */
fun uiErrorFrom(throwable: Throwable, context: Context, verbResId: Int): UiError = UiError(
    title = context.getString(R.string.error_could_not, context.getString(verbResId)),
    message = knownErrorResource(throwable)?.resolve(context)
        ?: context.getString(R.string.error_unexpected),
    retryLabel = context.getString(R.string.common_retry),
)

/**
 * Mensagem curta para os caminhos que continuam de snackbar: erros de
 * rede/backend recebem o texto acionável; erros locais (Room, validação)
 * mantêm o texto específico da ação via [fallbackRes].
 */
fun uiErrorMessage(throwable: Throwable, context: Context, fallbackRes: Int, vararg fallbackArgs: Any): String =
    knownErrorResource(throwable)?.resolve(context)
        ?: context.getString(fallbackRes, *fallbackArgs)

/** Mapeia a exceção para o recurso de mensagem acionável; `null` quando local. */
fun knownErrorResource(throwable: Throwable): KnownError? = when (throwable) {
    is BackendHttpException -> KnownError(R.string.error_http, listOf(throwable.code))
    is BackendTimeoutException, is SocketTimeoutException -> KnownError(R.string.error_timeout)
    is BackendConnectionException, is UnknownHostException, is ConnectException -> KnownError(R.string.error_offline)
    is BackendResponseException, is SerializationException -> KnownError(R.string.error_parse)
    is IOException -> KnownError(R.string.error_comms)
    else -> null
}
