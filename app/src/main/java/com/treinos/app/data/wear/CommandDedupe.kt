package com.treinos.app.data.wear

/**
 * Lógica pura de deduplicação de comandos do Wear OS.
 * Mantém os últimos N comandos já aplicados para não executá-los duas vezes.
 */
object CommandDedupe {

    private const val MAX_RECENT = 50

    fun isRecent(ids: Set<String>, commandId: String): Boolean {
        return commandId in ids
    }

    fun addRecent(ids: MutableSet<String>, commandId: String): MutableSet<String> {
        ids.add(commandId)
        if (ids.size > MAX_RECENT) {
            ids.remove(ids.first())
        }
        return ids
    }

    fun emptyIds(): MutableSet<String> = mutableSetOf()
}
