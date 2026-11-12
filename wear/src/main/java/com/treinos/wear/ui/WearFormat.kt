package com.treinos.wear.ui

import java.math.BigDecimal
import java.math.RoundingMode

fun formatLastSet(weightKg: Double?, reps: Int?): String? {
    if (weightKg == null && reps == null) return null
    val peso = weightKg?.let { formatWeightKg(it) }
    val repeticoes = reps?.let { "× $it reps" }
    return when {
        peso != null && repeticoes != null -> "$peso $repeticoes"
        peso != null -> "$peso ×"
        else -> repeticoes
    }
}

private fun formatWeightKg(weightKg: Double): String {
    val scaled = BigDecimal.valueOf(weightKg).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros()
    val texto = if (scaled.scale() < 0) scaled.setScale(0, RoundingMode.HALF_UP).toPlainString() else scaled.toPlainString()
    return "${texto.replace('.', ',')} kg"
}
