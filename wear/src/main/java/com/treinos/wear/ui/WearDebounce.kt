package com.treinos.wear.ui

class WearDebounce(
    private val windowMillis: Long = WINDOW_MILLIS,
) {

    private var lastSentMillis: Long? = null

    fun canSend(nowMillis: Long): Boolean {
        val last = lastSentMillis ?: return true
        return nowMillis - last >= windowMillis
    }

    fun markSent(nowMillis: Long) {
        lastSentMillis = nowMillis
    }

    companion object {
        const val WINDOW_MILLIS = 2_000L
    }
}