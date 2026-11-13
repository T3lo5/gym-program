package com.treinos.app.updater

/**
 * Estado da checagem/atualização dentro do app (H3-51). Nada roda em loop:
 * checagem sob demanda (abrir Ajustes + botão manual) e download só com
 * toque do usuário.
 */
sealed interface UpdateState {
    /** Sem checagem em andamento e sem versão nova. */
    data object Idle : UpdateState

    /** Consultando a API do GitHub. */
    data object Checking : UpdateState

    /** Versão nova disponível; [note] traz um aviso adicional (ex.: instalação iniciada). */
    data class Available(val release: ReleaseInfo, val note: String? = null) : UpdateState

    /** Baixando o APK; [progress] em porcento (0..100). */
    data class Downloading(val progress: Int) : UpdateState

    /** Já está na versão mais recente (resposta do botão manual). */
    data object UpToDate : UpdateState

    /** Erro discreto, exibido junto ao botão; nunca bloqueia o uso do app. */
    data class Failed(val message: String) : UpdateState
}
