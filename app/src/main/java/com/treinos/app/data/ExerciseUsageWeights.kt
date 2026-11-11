package com.treinos.app.data

import com.treinos.app.data.local.ExerciseUsageEvent

/**
 * Peso de uso de um exercício para calibrar a próxima geração (H3-42).
 *
 * Regras:
 * - concluído  → +1 confiança
 * - pulado     → -1 (rejeição)
 * - trocado    → -1 (rejeição)
 * - piso 0, teto 5 (não domina para sempre)
 * - decaimento linear: eventos com mais de 90 dias valem menos
 *
 * Pura: testável em JVM sem Room nem Android.
 */
const val USAGE_WINDOW_DAYS = 90L
const val USAGE_WEIGHT_CAP = 5

/**
 * Peso líquido de [exerciseId] a partir dos eventos de uso.
 * Positivo = exercício confirmado (aparece mais cedo no catálogo do prompt);
 * zero = sem sinal ou sinal neutro; nunca negativo (piso 0).
 */
fun usageWeightFor(
    exerciseId: String,
    events: List<ExerciseUsageEvent>,
    nowMillis: Long = System.currentTimeMillis(),
): Int {
    val windowStart = nowMillis - USAGE_WINDOW_DAYS * 24 * 60 * 60 * 1000L
    var score = 0
    for (event in events) {
        if (event.exerciseId != exerciseId) continue
        if (event.occurredAt < windowStart) continue
        // Decaimento: eventos mais antigos dentro da janela contam menos.
        val ageDays = (nowMillis - event.occurredAt) / (24 * 60 * 60 * 1000L)
        val decay = when {
            ageDays <= 30 -> 1.0
            ageDays <= 60 -> 0.66
            else -> 0.33
        }
        val delta = when (event.outcome) {
            "completed" -> 1.0
            "skipped", "swapped" -> -1.0
            else -> 0.0
        }
        score += (delta * decay).toInt()
    }
    return maxOf(0, minOf(score, USAGE_WEIGHT_CAP))
}

/** Peso de todos os exercícios do catálogo, a partir dos eventos de uso. */
fun usageWeightsForCatalog(
    exerciseIds: List<String>,
    events: List<ExerciseUsageEvent>,
    nowMillis: Long = System.currentTimeMillis(),
): Map<String, Int> =
    exerciseIds.associateWith { id -> usageWeightFor(id, events, nowMillis) }
