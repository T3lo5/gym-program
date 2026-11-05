package com.treinos.app.ui

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val numberSymbols = DecimalFormatSymbols(Locale.forLanguageTag("pt-BR"))
private val numberFormat = DecimalFormat("0.#", numberSymbols)

private fun formatNumber(value: Double): String = numberFormat.format(value)

fun exerciseContentDescription(name: String): String {
    val label = name.trim().ifBlank { "sem nome" }
    return "Exercício $label"
}

fun setContentDescription(setNumber: Int, reps: Int?, weightKg: Double?, rpe: Double?): String {
    val parts = mutableListOf("Série $setNumber")
    reps?.let { parts.add("$it repetições") }
    weightKg?.let { parts.add("${formatNumber(it)} quilos") }
    rpe?.let { parts.add("RPE ${formatNumber(it)}") }
    return parts.joinToString(", ")
}

fun progressStateDescription(completed: Int, planned: Int): String =
    "$completed de $planned séries concluídas"

fun restStateDescription(remaining: Int, total: Int): String =
    "Descanso: $remaining segundos restantes de $total"

fun reviewStateDescription(dateLabel: String, due: Boolean): String =
    if (due) "Revisão atrasada desde $dateLabel" else "Próxima revisão em $dateLabel"

fun timerShouldAnnounce(remaining: Int): Boolean = remaining in setOf(60, 30, 10, 0)

fun decorativeContentDescription(): String? = null
