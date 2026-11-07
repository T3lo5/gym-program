package com.treinos.app.ui

/**
 * Lógica pura do descanso (H3-26).
 *
 * A contagem é derivada de um **deadline em wall-clock**, não de ticks
 * acumulados: `delay(1_000)` por iteração atrasa em Doze/tela apagada, mas
 * `deadline - now` sempre reflete o tempo real. O tick de 1 s só atualiza a UI.
 */
object RestTimer {

    const val MILLIS_PER_SECOND = 1_000L

    /**
     * Deadline absoluto (epoch millis) para um descanso que começa agora.
     * `totalSeconds <= 0` não inicia descanso: devolve `null` e o chamador
     * não grava estado nem mostra card.
     */
    fun start(totalSeconds: Int, nowMillis: Long): Long? {
        if (totalSeconds <= 0) return null
        return nowMillis + totalSeconds.toLong() * MILLIS_PER_SECOND
    }

    /**
     * Segundos restantes, derivados do deadline. Arredonda para cima: um
     * descanso de 90 s mostra "01:30" durante o primeiro segundo inteiro e só
     * chega a 0 depois do deadline passar.
     */
    fun remainingSeconds(deadlineMillis: Long, nowMillis: Long): Int {
        val millisLeft = deadlineMillis - nowMillis
        if (millisLeft <= 0L) return 0
        return ((millisLeft + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()
    }

    /** "+15 s" sem teto: empurra o deadline. */
    fun extend(deadlineMillis: Long, extraSeconds: Int): Long =
        deadlineMillis + extraSeconds.toLong() * MILLIS_PER_SECOND

    /**
     * Pausa: congela os segundos restantes no momento da pausa (o chamador
     * guarda o valor; o deadline em disco é limpo para não sobreviver a
     * processo morto como descanso correndo).
     */
    fun pausedRemainingSeconds(deadlineMillis: Long, nowMillis: Long): Int =
        remainingSeconds(deadlineMillis, nowMillis)

    /** Retomada: recalcula o deadline a partir do restante congelado. */
    fun resume(remainingSeconds: Int, nowMillis: Long): Long? {
        if (remainingSeconds <= 0) return null
        return nowMillis + remainingSeconds.toLong() * MILLIS_PER_SECOND
    }
}
