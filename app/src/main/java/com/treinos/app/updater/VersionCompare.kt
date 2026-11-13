package com.treinos.app.updater

/**
 * Comparação de versão do updater (H3-51): o card só aparece quando o
 * `versionCode` remoto é maior que o local. Texto inválido no local vale 0 —
 * melhor oferecer um update real do que escondê-lo por um parse falho.
 */
object VersionCompare {

    /** versionCode local a partir do texto; inválido ou nulo → 0. */
    fun localVersionCode(raw: String?): Long = raw?.trim()?.toLongOrNull() ?: 0L

    /** Card aparece somente com versionCode remoto estritamente maior. */
    fun shouldShowCard(remoteCode: Long?, localCode: Long): Boolean =
        remoteCode != null && remoteCode > localCode
}
