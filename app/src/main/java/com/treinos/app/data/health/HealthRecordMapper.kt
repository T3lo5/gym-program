package com.treinos.app.data.health

import com.treinos.app.data.local.WorkoutSessionEntity

/**
 * H4-9: mapeamento puro entre as sessões do Room e o formato do Health
 * Connect. **Não** importa o SDK — as constantes de tipo de exercício são
 * duplicadas aqui com os valores de `ExerciseSessionRecord` para o arquivo
 * continuar testável em JVM pura; o gateway usa exatamente estas constantes.
 */

// Constantes de `ExerciseSessionRecord` (Health Connect), sem importar o SDK.
const val HC_EXERCISE_TYPE_OTHER_WORKOUT = 0
const val HC_EXERCISE_TYPE_RUNNING = 56
const val HC_EXERCISE_TYPE_SWIMMING_OPEN_WATER = 73
const val HC_EXERCISE_TYPE_SWIMMING_POOL = 74
const val HC_EXERCISE_TYPE_STRETCHING = 71
const val HC_EXERCISE_TYPE_STRENGTH_TRAINING = 70

/** Faixa válida de peso, a mesma de `MeasurementValidator` (H4-8). */
const val HEALTH_WEIGHT_MIN_KG = 30.0
const val HEALTH_WEIGHT_MAX_KG = 300.0

/** Faixa válida de passos agregados (regra 11). */
const val HEALTH_STEPS_MIN = 1L
const val HEALTH_STEPS_MAX = 1_000_000L

/** Faixa de RPE do app; fora dela o RPE não vai ao Health Connect (regra 6). */
const val HEALTH_RPE_MIN = 1.0
const val HEALTH_RPE_MAX = 10.0

/**
 * Chave de idempotência no Health Connect (regras 7): reescrever a mesma
 * sessão com o mesmo `clientRecordId` atualiza o registro em vez de duplicar.
 */
fun healthClientRecordId(sessionId: Long): String = "treinos:session:$sessionId"

/**
 * Converte uma sessão do Room em rascunho do Health Connect. Devolve `null`
 * quando a sessão não pode ser escrita: em andamento (regra 4), sem
 * `endedAt` (regra 4) ou com duração zero/negativa (regra 5).
 */
fun toHealthSessionDraft(session: WorkoutSessionEntity): HealthSessionDraft? {
    if (session.status != "completed") return null
    val endedAt = session.endedAt ?: return null
    if (endedAt <= session.startedAt) return null
    return HealthSessionDraft(
        clientRecordId = healthClientRecordId(session.id),
        startedAtMillis = session.startedAt,
        endedAtMillis = endedAt,
        exerciseType = healthExerciseTypeFor(session.modality, session.stroke),
        title = session.title.takeIf { it.isNotBlank() },
        notes = session.notes.takeIf { it.isNotBlank() },
        rpe = session.sessionRpe?.takeIf { it in HEALTH_RPE_MIN..HEALTH_RPE_MAX }?.toFloat(),
    )
}

/**
 * Tipo de exercício do Health Connect para a modalidade do app. Na natação,
 * o `stroke` registrado decide piscina (estilo conhecido) ou água aberta
 * (sem estilo); o app não tem campo de ambiente separado.
 */
fun healthExerciseTypeFor(modality: String, stroke: String?): Int = when (modality) {
    "strength" -> HC_EXERCISE_TYPE_STRENGTH_TRAINING
    "run" -> HC_EXERCISE_TYPE_RUNNING
    "swim" -> if (stroke.isNullOrBlank()) {
        HC_EXERCISE_TYPE_SWIMMING_OPEN_WATER
    } else {
        HC_EXERCISE_TYPE_SWIMMING_POOL
    }
    "mobility" -> HC_EXERCISE_TYPE_STRETCHING
    else -> HC_EXERCISE_TYPE_OTHER_WORKOUT
}

/**
 * Valida e ordena leituras de peso: fora de 30..300 kg é descartado (regra
 * 10) e a saída fica em ordem cronológica.
 */
fun toWeightSamples(records: List<RawWeight>): List<HealthWeightSample> =
    records.mapNotNull { raw ->
        if (raw.weightKg.isNaN() || raw.weightKg !in HEALTH_WEIGHT_MIN_KG..HEALTH_WEIGHT_MAX_KG) {
            null
        } else {
            HealthWeightSample(timeMillis = raw.timeMillis, weightKg = raw.weightKg)
        }
    }.sortedBy { it.timeMillis }

/**
 * Valida e ordena leituras de passos: fora de 1..1.000.000 é descartado
 * (regra 11) e a saída fica em ordem cronológica.
 */
fun toStepsSamples(records: List<RawSteps>): List<HealthStepsSample> =
    records.mapNotNull { raw ->
        if (raw.count !in HEALTH_STEPS_MIN..HEALTH_STEPS_MAX) {
            null
        } else {
            HealthStepsSample(
                startMillis = raw.startMillis,
                endMillis = raw.endMillis,
                count = raw.count,
            )
        }
    }.sortedBy { it.startMillis }
