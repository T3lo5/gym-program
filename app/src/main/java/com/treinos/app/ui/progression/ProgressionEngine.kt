package com.treinos.app.ui.progression

import com.treinos.app.data.local.SessionWithExercises
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.max
import kotlin.math.roundToLong

enum class ProgressionAction {
    INCREASE,
    MAINTAIN,
    DECREASE,
    NONE,
}

data class ProgressionSet(
    val sessionId: Long,
    val sessionStartedAt: Long,
    val exerciseId: String,
    val isCompleted: Boolean,
    val isWarmup: Boolean,
    val reps: Int?,
    val weightKg: Double?,
    val rpe: Double?,
)

data class ProgressionContext(
    val exerciseId: String,
    val targetReps: String,
    val equipment: String,
    val primaryMuscles: String,
)

data class ProgressionSuggestion(
    val action: ProgressionAction,
    val suggestedWeightKg: Double?,
    val suggestedReps: Int?,
    val reason: String,
)

const val PROGRESSION_HISTORY_DAYS = 42

private const val WEIGHT_STEP_KG = 2.5
private const val MIN_WEIGHT_KG = 2.5
private const val MAX_WEIGHT_KG = 500.0
private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
private const val INCREASE_RPE_CEILING = 8.5
private const val DECREASE_RPE_FLOOR = 9.5
private const val DECREASE_FACTOR = 0.95
private const val DELOAD_CYCLE_WEEK = 6
private const val DELOAD_MIN_SESSIONS = 8

private const val REASON_INCREASE = "Duas sessões no topo da faixa com RPE confortável."
private const val REASON_MAINTAIN = "Última sessão perto do limite; mantenha a carga."
private const val REASON_DECREASE = "RPE alto ou repetições abaixo da faixa; reduza a carga."
private const val REASON_NONE = "Histórico insuficiente para sugerir progressão."

private val LOWER_BODY_MUSCLES = setOf("quadriceps", "hamstrings", "glutes", "calves")
private val STANDARD_STEP_EQUIPMENT = setOf("dumbbell", "machine", "cable", "e-z curl bar", "ez curl bar")

private val REPS_RANGE_PATTERN = Regex("""(\d+)\s*[-–]\s*(\d+)""")
private val SINGLE_REPS_PATTERN = Regex("""(\d+)""")

/**
 * Sugere a próxima carga ou repetição a partir do que o usuário realmente fez.
 * Nunca altera o plano: a saída é texto de apoio para o diálogo de série.
 * Equipamento não reconhecido trata-se como peso corporal — sugestão por reps,
 * nunca por quilos.
 */
fun suggestProgression(
    context: ProgressionContext,
    history: List<ProgressionSet>,
    nowMillis: Long,
): ProgressionSuggestion {
    val targetRange = parseRepRange(context.targetReps) ?: return noneSuggestion()
    val bestPerSession = bestSetPerSession(context, history, nowMillis)
    if (bestPerSession.size < 2) return noneSuggestion()

    val last = bestPerSession.last()
    val previous = bestPerSession[bestPerSession.size - 2]
    val increment = loadIncrementKg(context.equipment, context.primaryMuscles)
    val isLoaded = increment != null
    val lastWeight = last.weightKg?.takeIf { it.isFinite() }

    if (isAtTopWithComfortableEffort(last, targetRange) && isAtTopWithComfortableEffort(previous, targetRange)) {
        return when {
            !isLoaded -> ProgressionSuggestion(
                action = ProgressionAction.INCREASE,
                suggestedWeightKg = null,
                suggestedReps = targetRange.last + 1,
                reason = REASON_INCREASE,
            )
            lastWeight == null -> maintainSuggestion(lastWeight, isLoaded, targetRange)
            else -> ProgressionSuggestion(
                action = ProgressionAction.INCREASE,
                suggestedWeightKg = clampWeight(
                    roundToIncrement(lastWeight + requireNotNull(increment), WEIGHT_STEP_KG),
                ),
                suggestedReps = null,
                reason = REASON_INCREASE,
            )
        }
    }

    val lastRpe = last.rpe?.takeIf { it.isFinite() }
    val lastReps = last.reps ?: 0
    val overTheLimit = lastRpe != null && lastRpe >= DECREASE_RPE_FLOOR
    if (overTheLimit || lastReps < targetRange.first) {
        return when {
            !isLoaded -> ProgressionSuggestion(
                action = ProgressionAction.DECREASE,
                suggestedWeightKg = null,
                suggestedReps = max(targetRange.first - 1, 1),
                reason = REASON_DECREASE,
            )
            lastWeight == null -> maintainSuggestion(lastWeight, isLoaded, targetRange)
            else -> ProgressionSuggestion(
                action = ProgressionAction.DECREASE,
                suggestedWeightKg = max(
                    roundToIncrement(lastWeight * DECREASE_FACTOR, WEIGHT_STEP_KG),
                    MIN_WEIGHT_KG,
                ),
                suggestedReps = null,
                reason = REASON_DECREASE,
            )
        }
    }

    return maintainSuggestion(lastWeight, isLoaded, targetRange)
}

fun roundToIncrement(value: Double, increment: Double = WEIGHT_STEP_KG): Double {
    if (!value.isFinite() || !increment.isFinite() || increment <= 0.0) return value
    return (value / increment).roundToLong() * increment
}

fun completedSessionsSincePlan(
    sessions: List<SessionWithExercises>,
    planCreatedAtMillis: Long,
): Int = sessions.count { it.session.status == "completed" && it.session.startedAt >= planCreatedAtMillis }

/** Semana do ciclo do plano: segunda-feira abre a semana e o mínimo é 1. */
fun cycleWeek(
    planCreatedAtMillis: Long,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Int {
    val created = toDate(planCreatedAtMillis, zoneId) ?: return 1
    val today = toDate(max(nowMillis, planCreatedAtMillis), zoneId) ?: return 1
    val weekStart = created.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val days = ChronoUnit.DAYS.between(weekStart, maxOf(today, weekStart))
    return max((days / 7L).toInt() + 1, 1)
}

fun deloadSuggested(
    planCreatedAtMillis: Long,
    nowMillis: Long,
    completedSessionsSincePlan: Int,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean =
    cycleWeek(planCreatedAtMillis, nowMillis, zoneId) >= DELOAD_CYCLE_WEEK &&
        completedSessionsSincePlan >= DELOAD_MIN_SESSIONS

fun progressionHistory(
    sessions: List<SessionWithExercises>,
    exerciseId: String,
): List<ProgressionSet> = sessions.flatMap { session ->
    session.exercises
        .filter { it.exercise.exerciseId == exerciseId }
        .flatMap { exercise ->
            exercise.sets.map { set ->
                ProgressionSet(
                    sessionId = session.session.id,
                    sessionStartedAt = session.session.startedAt,
                    exerciseId = exerciseId,
                    isCompleted = set.isCompleted,
                    isWarmup = set.isWarmup,
                    reps = set.reps,
                    weightKg = set.weightKg,
                    rpe = set.rpe,
                )
            }
        }
}

private fun maintainSuggestion(
    lastWeight: Double?,
    isLoaded: Boolean,
    targetRange: IntRange,
): ProgressionSuggestion = ProgressionSuggestion(
    action = ProgressionAction.MAINTAIN,
    suggestedWeightKg = lastWeight,
    suggestedReps = if (isLoaded) null else targetRange.last,
    reason = REASON_MAINTAIN,
)

private fun noneSuggestion(): ProgressionSuggestion = ProgressionSuggestion(
    action = ProgressionAction.NONE,
    suggestedWeightKg = null,
    suggestedReps = null,
    reason = REASON_NONE,
)

private fun bestSetPerSession(
    context: ProgressionContext,
    history: List<ProgressionSet>,
    nowMillis: Long,
): List<ProgressionSet> {
    val cutoff = nowMillis - PROGRESSION_HISTORY_DAYS * MILLIS_PER_DAY
    return history
        .filter { it.exerciseId == context.exerciseId }
        .filter { it.isCompleted && !it.isWarmup }
        .filter { it.reps != null }
        .filter { it.sessionStartedAt >= cutoff }
        .groupBy { it.sessionId }
        .values
        .map { sets -> sets.maxWith(SET_RANK) }
        .sortedBy { it.sessionStartedAt }
}

/** Maior carga; empate de carga — ou duas sem carga — vence quem fez mais repetições. */
private val SET_RANK: Comparator<ProgressionSet> = Comparator { a, b ->
    val weightOrder = a.weightKg.orUnknown().compareTo(b.weightKg.orUnknown())
    if (weightOrder != 0) weightOrder else (a.reps ?: 0).compareTo(b.reps ?: 0)
}

private fun Double?.orUnknown(): Double = this ?: Double.NEGATIVE_INFINITY

private fun isAtTopWithComfortableEffort(set: ProgressionSet, targetRange: IntRange): Boolean {
    val rpe = set.rpe?.takeIf { it.isFinite() }
    return (set.reps ?: 0) >= targetRange.last && (rpe == null || rpe <= INCREASE_RPE_CEILING)
}

private fun clampWeight(weightKg: Double): Double = weightKg.coerceIn(MIN_WEIGHT_KG, MAX_WEIGHT_KG)

private fun loadIncrementKg(equipment: String, primaryMuscles: String): Double? = when (equipment.normalized()) {
    "barbell" -> if (hasLowerBodyMuscle(primaryMuscles)) 5.0 else 2.5
    in STANDARD_STEP_EQUIPMENT -> 2.5
    else -> null
}

private fun hasLowerBodyMuscle(primaryMuscles: String): Boolean =
    primaryMuscles.split(',').any { it.normalized() in LOWER_BODY_MUSCLES }

private fun parseRepRange(targetReps: String): IntRange? {
    val text = targetReps.trim()
    if (text.isEmpty()) return null
    REPS_RANGE_PATTERN.matchEntire(text)?.let { match ->
        val first = match.groupValues[1].toIntOrNull() ?: return null
        val last = match.groupValues[2].toIntOrNull() ?: return null
        return if (first < 1 || last < first) null else first..last
    }
    SINGLE_REPS_PATTERN.matchEntire(text)?.let { match ->
        val reps = match.groupValues[1].toIntOrNull() ?: return null
        return if (reps < 1) null else reps..reps
    }
    return null
}

private fun toDate(epochMillis: Long, zoneId: ZoneId): LocalDate? =
    runCatching { Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate() }.getOrNull()

private fun String.normalized(): String = trim().lowercase()
