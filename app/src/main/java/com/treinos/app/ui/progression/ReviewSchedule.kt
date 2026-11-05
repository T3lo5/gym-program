package com.treinos.app.ui.progression

import java.time.LocalDate

const val REVIEW_POSTPONE_WEEKS = 2

/**
 * Nova data de revisão a partir da atual. Data ausente, ilegível ou já vencida
 * conta a partir de `today`; uma data futura é sempre adiada, nunca antecipada.
 */
fun postponeReviewDate(
    currentIsoDate: String?,
    today: LocalDate,
    weeks: Int = REVIEW_POSTPONE_WEEKS,
): LocalDate {
    val current = currentIsoDate?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
        runCatching { LocalDate.parse(raw) }.getOrNull()
    }
    val base = if (current != null && current.isAfter(today)) current else today
    return base.plusWeeks(weeks.coerceAtLeast(0).toLong())
}
