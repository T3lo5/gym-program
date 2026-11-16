package com.treinos.app.data.csv

import com.treinos.app.data.local.SessionWithExercises
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Exportador do formato "Treinos" (H4-7): UTF-8 com BOM, delimitador `;`,
 * decimal com vírgula, quebra `\r\n` e uma linha por série.
 *
 * O texto é determinístico: mesma entrada → mesmo texto, sem `Locale` da
 * máquina (decimal sempre com vírgula, separador de milhar nunca).
 */
object WorkoutCsvExporter {

    /** Cabeçalho exato do formato; é ele que versiona o arquivo. */
    val HEADER = listOf(
        "Data",
        "Hora",
        "Treino",
        "Exercício",
        "Série",
        "Reps",
        "Carga (kg)",
        "RPE",
        "Aquecimento",
        "Duração (s)",
        "Distância (m)",
        "Notas",
    )

    private const val DELIMITER = ';'
    private const val LINE_BREAK = "\r\n"
    private const val FALLBACK_TITLE = "Treino"

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    /**
     * Exporta as sessões que têm pelo menos uma série registrada
     * (`isCompleted = true`), inclusive as em andamento, ordenadas por
     * `startedAt` e, dentro da sessão, por `exerciseOrder` e `setNumber`.
     * Sessão sem série não gera linha; sem sessão alguma, sai só o cabeçalho.
     */
    fun exportSessionsCsv(sessions: List<SessionWithExercises>): String {
        val zone = ZoneId.systemDefault()
        val rows = mutableListOf(HEADER)
        sessions
            .filter { details -> details.exercises.any { it.sets.any { set -> set.isCompleted } } }
            .sortedWith(compareBy({ it.session.startedAt }, { it.session.id }))
            .forEach { details ->
                val started = Instant.ofEpochMilli(details.session.startedAt).atZone(zone)
                val title = sessionTitle(details)
                val notes = details.session.notes
                details.exercises
                    .sortedBy { it.exercise.exerciseOrder }
                    .forEach { exercise ->
                        val name = exercise.exercise.exerciseName
                            .ifBlank { exercise.exercise.exerciseId }
                        exercise.sets
                            .sortedBy { it.setNumber }
                            .forEach { set ->
                                rows += listOf(
                                    started.format(DATE_FORMAT),
                                    started.format(TIME_FORMAT),
                                    title,
                                    name,
                                    set.setNumber.toString(),
                                    set.reps?.toString().orEmpty(),
                                    set.weightKg?.let(::formatDecimal).orEmpty(),
                                    set.rpe?.let(::formatDecimal).orEmpty(),
                                    if (set.isWarmup) "Sim" else "Não",
                                    set.durationSeconds?.toString().orEmpty(),
                                    set.distanceMeters?.let(::formatDecimal).orEmpty(),
                                    notes,
                                )
                            }
                    }
            }
        return CsvCodec.UTF8_BOM +
            rows.joinToString(LINE_BREAK) { CsvCodec.writeCsvRow(it, DELIMITER) } +
            LINE_BREAK
    }

    /** `title` → `dayName` → `planName` → "Treino". */
    private fun sessionTitle(details: SessionWithExercises): String =
        details.session.title
            .ifBlank { details.session.dayName }
            .ifBlank { details.session.planName }
            .ifBlank { FALLBACK_TITLE }

    /**
     * Decimal com vírgula e sem zeros à direita (`40.0` → `40`,
     * `42.5` → `42,5`), independente de `Locale`.
     */
    private fun formatDecimal(value: Double): String =
        String.format(Locale.ROOT, "%.10f", value)
            .trimEnd('0')
            .trimEnd('.')
            .replace('.', ',')
}
