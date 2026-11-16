package com.treinos.app.data.csv

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Parser dos CSVs exportados pelo Strong e pelo Hevy (H4-7).
 *
 * O formato é detectado **só pelo cabeçalho** (case-insensitive, sem acento,
 * espaços normalizados): um cabeçalho que não casa com nenhuma das listas de
 * colunas obrigatórias é rejeitado, nunca interpretado "no chute". Linhas
 * inválidas são ignoradas e contadas em `skippedRows`; valores fora de faixa
 * viram `null` e a linha continua válida.
 */
object StrongHevyCsvParser {

    /** Mensagem da regra 2: cabeçalho sem as colunas de Strong nem de Hevy. */
    const val UNKNOWN_FORMAT_MESSAGE =
        "Formato não reconhecido. Exporte do Strong ou do Hevy."

    private val STRONG_REQUIRED = setOf(
        "date",
        "workout name",
        "exercise name",
        "set order",
        "weight",
        "reps",
    )

    private val HEVY_REQUIRED = setOf(
        "title",
        "start_time",
        "exercise_title",
        "set_index",
        "weight_kg",
        "reps",
    )

    private val BR_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)

    fun detectFormat(header: List<String>): CsvImportFormat? {
        val columns = header.map { CsvCodec.normalizeHeaderField(it) }.toSet()
        return when {
            STRONG_REQUIRED.all { it in columns } -> CsvImportFormat.STRONG
            HEVY_REQUIRED.all { it in columns } -> CsvImportFormat.HEVY
            else -> null
        }
    }

    /**
     * Converte as linhas do arquivo (a primeira é o cabeçalho) em um
     * [CsvImportPlan]. Cabeçalho desconhecido lança
     * [IllegalArgumentException]; linhas de dados todas inválidas devolvem um
     * plano vazio com `skippedRows > 0` — é isso que a UI transforma em erro,
     * em vez de pré-visualização vazia.
     */
    fun parse(rows: List<List<String>>): CsvImportPlan {
        val header = rows.firstOrNull().orEmpty()
        val format = detectFormat(header)
            ?: throw IllegalArgumentException(UNKNOWN_FORMAT_MESSAGE)

        val columns = header.map { CsvCodec.normalizeHeaderField(it) }
        fun columnOf(vararg names: String): Int =
            columns.indexOfFirst { column -> names.any { it == column } }

        val zone = ZoneId.systemDefault()
        val sessionsByKey = LinkedHashMap<String, MutableSession>()
        var skippedRows = 0
        val warnings = mutableListOf<String>()

        rows.drop(1).forEachIndexed { offset, raw ->
            val row = CsvImportRow(lineNumber = offset + 2, raw = raw)
            fun cell(column: Int): String = raw.getOrNull(column)?.trim().orEmpty()

            val exerciseName = cell(
                when (format) {
                    CsvImportFormat.STRONG -> columnOf("exercise name")
                    CsvImportFormat.HEVY -> columnOf("exercise_title")
                },
            )
            val setNumber = cell(
                when (format) {
                    CsvImportFormat.STRONG -> columnOf("set order")
                    CsvImportFormat.HEVY -> columnOf("set_index")
                },
            ).toIntOrNull()

            if (exerciseName.isEmpty() || setNumber == null) {
                skippedRows++
                warnings += skippedRowWarning(row, "exercício vazio ou ordem de série inválida")
                return@forEachIndexed
            }

            val startedAt = when (format) {
                CsvImportFormat.STRONG -> parseStrongStartedAt(
                    dateCell = cell(columnOf("date")),
                    timeCell = cell(columnOf("time")),
                    zone = zone,
                )
                CsvImportFormat.HEVY -> parseHevyInstant(cell(columnOf("start_time")))?.toEpochMilli()
            }
            if (startedAt == null) {
                skippedRows++
                warnings += skippedRowWarning(row, "data ilegível")
                return@forEachIndexed
            }

            val title = cell(
                when (format) {
                    CsvImportFormat.STRONG -> columnOf("workout name")
                    CsvImportFormat.HEVY -> columnOf("title")
                },
            )
            val notes = cell(
                when (format) {
                    CsvImportFormat.STRONG -> columnOf("notes")
                    CsvImportFormat.HEVY -> columnOf("description")
                },
            )
            val rpe = parseNumber(cell(columnOf("rpe")))?.takeIf { it in 1.0..10.0 }
            val weightKg = parseNumber(
                cell(
                    when (format) {
                        CsvImportFormat.STRONG -> columnOf("weight")
                        CsvImportFormat.HEVY -> columnOf("weight_kg")
                    },
                ),
            )?.takeIf { it >= 0.0 }
            val reps = cell(columnOf("reps")).toIntOrNull()?.takeIf { it >= 0 }
            val durationSeconds = cell(
                when (format) {
                    CsvImportFormat.STRONG -> columnOf("seconds")
                    CsvImportFormat.HEVY -> columnOf("duration_seconds")
                },
            ).toLongOrNull()?.takeIf { it >= 0 }
            val distanceMeters = parseNumber(
                cell(
                    when (format) {
                        // Strong e Hevy trazem distância em km; o app guarda metros.
                        CsvImportFormat.STRONG -> columnOf("distance")
                        CsvImportFormat.HEVY -> columnOf("distance_km")
                    },
                ),
            )?.takeIf { it >= 0.0 }?.times(1000.0)

            val date = when (format) {
                CsvImportFormat.STRONG ->
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(startedAt), zone).toLocalDate()
                CsvImportFormat.HEVY ->
                    Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate()
            }
            val titleKey = csvImportRefTitle(title)
            val session = sessionsByKey.getOrPut("$date|$titleKey") {
                MutableSession(
                    date = date,
                    title = title,
                    startedAt = startedAt,
                )
            }
            if (session.notes.isEmpty() && notes.isNotEmpty()) session.notes = notes
            if (session.durationSeconds == null) {
                session.durationSeconds = when (format) {
                    CsvImportFormat.STRONG ->
                        parseDuration(cell(columnOf("workout duration")))
                    CsvImportFormat.HEVY -> parseHevyInstant(cell(columnOf("end_time")))
                        ?.takeIf { end -> end.toEpochMilli() > startedAt }
                        ?.let { end -> (end.toEpochMilli() - startedAt) / 1000L }
                }
            }
            val exercise = session.exercises.getOrPut(csvImportRefTitle(exerciseName)) {
                MutableExercise(name = exerciseName, order = session.exercises.size)
            }
            exercise.sets += CsvSetDraft(
                setNumber = setNumber,
                reps = reps,
                weightKg = weightKg,
                rpe = rpe,
                durationSeconds = durationSeconds,
                distanceMeters = distanceMeters,
                // Os formatos do concorrente não marcam aquecimento; a série
                // importada nasce como série de trabalho.
                isWarmup = false,
            )
        }

        val sessions = sessionsByKey.values.map { session ->
            CsvSessionDraft(
                importRef = "${format.name.lowercase()}:${session.date}:${
                    csvImportRefTitle(session.title)
                }",
                startedAt = session.startedAt,
                title = session.title,
                notes = session.notes,
                durationSeconds = session.durationSeconds,
                exercises = session.exercises.values.map { exercise ->
                    CsvExerciseDraft(
                        exerciseName = exercise.name,
                        exerciseOrder = exercise.order,
                        sets = exercise.sets.sortedBy { it.setNumber },
                    )
                },
            )
        }
        return CsvImportPlan(
            format = format,
            sessions = sessions,
            skippedRows = skippedRows,
            warnings = warnings,
        )
    }

    /** Strong: `yyyy-MM-dd` ou `dd/MM/yyyy`, com hora opcional na mesma coluna ou em `Time`. */
    private fun parseStrongStartedAt(dateCell: String, timeCell: String, zone: ZoneId): Long? {
        if (dateCell.isEmpty()) return null
        val parts = dateCell.split(Regex("\\s+"), limit = 2)
        val date = parseStrongDate(parts[0]) ?: return null
        val time = (parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: timeCell)
            .let { it.ifBlank { null } }
            ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            ?: LocalTime.MIDNIGHT
        return LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
    }

    private fun parseStrongDate(value: String): LocalDate? =
        runCatching { LocalDate.parse(value) }
            .recoverCatching { LocalDate.parse(value, BR_DATE_FORMAT) }
            .getOrNull()

    /** Hevy: ISO-8601 com offset (`Z` ou `+00:00`); sem offset, vira hora local. */
    private fun parseHevyInstant(value: String): Instant? {
        if (value.isEmpty()) return null
        return runCatching { OffsetDateTime.parse(value).toInstant() }
            .recoverCatching {
                LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant()
            }
            .getOrNull()
    }

    /**
     * Número com `.` ou `,` decimal. Com os dois presentes, o último é o
     * decimal (`1.234,5` e `1,234.5` → 1234.5).
     */
    private fun parseNumber(value: String): Double? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        val normalized = when {
            trimmed.contains(',') && trimmed.contains('.') ->
                if (trimmed.lastIndexOf(',') > trimmed.lastIndexOf('.')) {
                    trimmed.replace(".", "").replace(',', '.')
                } else {
                    trimmed.replace(",", "")
                }
            trimmed.contains(',') -> trimmed.replace(',', '.')
            else -> trimmed
        }
        return normalized.toDoubleOrNull()
    }

    /**
     * Duração da sessão: número puro em segundos, `H:mm:ss`/`mm:ss` ou o
     * `1h 2m 3s` do Strong. Ilegível vira `null`.
     */
    private fun parseDuration(value: String): Long? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        trimmed.toLongOrNull()?.let { return it }
        Regex("^(\\d{1,2}):(\\d{1,2}):(\\d{1,2})$").find(trimmed)?.let { match ->
            val (hours, minutes, seconds) = match.destructured
            return hours.toLong() * 3600 + minutes.toLong() * 60 + seconds.toLong()
        }
        Regex("^(\\d{1,2}):(\\d{1,2})$").find(trimmed)?.let { match ->
            val (minutes, seconds) = match.destructured
            return minutes.toLong() * 60 + seconds.toLong()
        }
        var total = 0L
        var found = false
        Regex("(\\d+)\\s*h").find(trimmed)?.let { total += it.groupValues[1].toLong() * 3600; found = true }
        Regex("(\\d+)\\s*m").find(trimmed)?.let { total += it.groupValues[1].toLong() * 60; found = true }
        Regex("(\\d+)\\s*s").find(trimmed)?.let { total += it.groupValues[1].toLong(); found = true }
        return if (found) total else null
    }

    private fun skippedRowWarning(row: CsvImportRow, reason: String): String =
        "Linha ${row.lineNumber} ignorada: $reason."

    private class MutableSession(
        val date: LocalDate,
        val title: String,
        val startedAt: Long,
    ) {
        var notes: String = ""
        var durationSeconds: Long? = null
        val exercises = LinkedHashMap<String, MutableExercise>()
    }

    private class MutableExercise(
        val name: String,
        val order: Int,
    ) {
        val sets = mutableListOf<CsvSetDraft>()
    }
}
