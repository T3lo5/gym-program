package com.treinos.app.data.remote

fun authHeader(token: String?): String? =
    token?.trim()?.takeIf { it.isNotEmpty() }?.let { "Bearer $it" }

fun validateRemoteUrl(url: String): String? {
    val trimmed = url.trim().trimEnd('/')
    if (trimmed.isEmpty()) return "Informe a URL remota"
    if (!trimmed.startsWith("https://", ignoreCase = true)) {
        return "Backend remoto precisa usar HTTPS"
    }
    return null
}

fun effectiveBackendUrl(
    useRemote: Boolean,
    remoteUrl: String,
    localUrl: String,
): String =
    if (useRemote && validateRemoteUrl(remoteUrl) == null) {
        remoteUrl.trim().trimEnd('/')
    } else {
        localUrl
    }
