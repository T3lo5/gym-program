package com.treinos.app.ui

/**
 * H4-5: rampa de aquecimento antes da série de trabalho — informativa, nunca
 * registrada automaticamente. Reusa [plateBreakdown] para arredondar cada
 * degrau para a carga realmente carregável (sempre para baixo).
 */

/** Um degrau da rampa: `percent` da carga de trabalho, `reps` repetições. */
data class WarmupStep(
    val percent: Int,
    val weightKg: Double,
    val reps: Int,
)

/**
 * Degraus fixos: 40% × 8 reps, 60% × 5 reps, 80% × 3 reps.
 *
 * Cada `weightKg` é o `loadedKg` de [plateBreakdown] do degrau. Regra única
 * de descarte: o degrau cuja carga carregável não sai da barra
 * (`loadedKg <= barKg`). Também são descartados degraus >= à carga de
 * trabalho e pesos repetidos (mantém a primeira ocorrência). Vazio quando a
 * carga de trabalho não é finita ou `<= barKg`.
 */
fun warmupRamp(
    workingWeightKg: Double,
    inventory: PlateInventory = DEFAULT_PLATE_INVENTORY,
): List<WarmupStep> {
    if (!workingWeightKg.isFinite()) return emptyList()
    if (!inventory.barKg.isFinite() || inventory.barKg <= 0.0) return emptyList()
    if (workingWeightKg <= inventory.barKg) return emptyList()
    val steps = listOf(
        Triple(40, 8, 0.40),
        Triple(60, 5, 0.60),
        Triple(80, 3, 0.80),
    )
    val ramp = mutableListOf<WarmupStep>()
    val seen = mutableSetOf<Double>()
    for ((percent, reps, fraction) in steps) {
        val loaded = plateBreakdown(fraction * workingWeightKg, inventory)?.loadedKg ?: continue
        if (loaded <= inventory.barKg) continue
        if (loaded >= workingWeightKg) continue
        if (!seen.add(loaded)) continue // mantém a primeira ocorrência
        ramp.add(WarmupStep(percent = percent, weightKg = loaded, reps = reps))
    }
    return ramp
}
