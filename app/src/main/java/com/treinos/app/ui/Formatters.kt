package com.treinos.app.ui

import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToLong

private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val shortDateFormatter = DateTimeFormatter.ofPattern("dd/MM")
private val ptBr = Locale.forLanguageTag("pt-BR")

fun formatDateTime(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(dateTimeFormatter)

fun formatIsoDateTime(isoDateTime: String?): String? {
    if (isoDateTime.isNullOrBlank()) return null
    return runCatching {
        Instant.parse(isoDateTime).atZone(ZoneId.systemDefault()).format(dateTimeFormatter)
    }.getOrNull()
}

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "—"
    val totalMinutes = seconds / 60
    return when {
        totalMinutes < 1 -> "${seconds}s"
        totalMinutes < 60 -> "${totalMinutes} min"
        else -> "${totalMinutes / 60}h ${(totalMinutes % 60).toString().padStart(2, '0')}min"
    }
}

fun formatReviewDate(isoDate: String?): String? {
    if (isoDate.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(isoDate).format(dateFormatter) }.getOrNull()
}

fun reviewIsDue(isoDate: String?): Boolean {
    if (isoDate.isNullOrBlank()) return false
    return runCatching { LocalDate.parse(isoDate).isBefore(LocalDate.now()) }.getOrDefault(false)
}

fun goalLabel(goal: String): String = when (goal) {
    "hypertrophy" -> "Hipertrofia"
    "strength" -> "Força"
    "endurance" -> "Resistência"
    "power" -> "Potência"
    "fat_loss" -> "Perda de gordura"
    "general" -> "Condicionamento geral"
    else -> goal
}

fun levelLabel(level: String): String = when (level) {
    "beginner" -> "Iniciante"
    "intermediate" -> "Intermediário"
    "advanced" -> "Avançado"
    else -> level
}

fun equipmentLabel(equipment: String): String = when (equipment) {
    "body only" -> "Peso corporal"
    "dumbbell" -> "Halteres"
    "barbell" -> "Barra"
    "cable" -> "Polia"
    "machine" -> "Máquina"
    "kettlebells" -> "Kettlebell"
    "bands" -> "Elásticos"
    "e-z curl bar" -> "Barra EZ"
    "exercise ball" -> "Bola"
    "medicine ball" -> "Bola medicinal"
    "foam roll" -> "Rolo"
    "other" -> "Outro"
    else -> equipment
}

fun muscleLabel(muscle: String): String = when (muscle) {
    "chest" -> "Peito"
    "back" -> "Costas"
    "lats" -> "Dorsais"
    "middle back" -> "Meio das costas"
    "lower back" -> "Lombar"
    "shoulders" -> "Ombros"
    "traps" -> "Trapézio"
    "biceps" -> "Bíceps"
    "triceps" -> "Tríceps"
    "forearms" -> "Antebraços"
    "abdominals" -> "Abdômen"
    "quadriceps" -> "Quadríceps"
    "hamstrings" -> "Posterior"
    "glutes" -> "Glúteos"
    "calves" -> "Panturrilha"
    "legs" -> "Pernas"
    "arms" -> "Braços"
    "abs" -> "Abdômen"
    else -> muscle
}

fun isToday(millis: Long): Boolean =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()

fun formatDistanceMeters(meters: Double?): String? {
    val value = meters?.takeIf { it > 0.0 && it.isFinite() } ?: return null
    return groupThousands(value.roundToLong()) + " m"
}

/** Pace de natação em `mm:ss/100m`; null quando não há distância registrada. */
fun formatPacePer100m(durationSeconds: Long, distanceMeters: Double?): String? =
    paceSecondsPer100m(durationSeconds, distanceMeters)?.let { "${formatMinuteSecond(it)}/100m" }

/** Pace de corrida em `mm:ss/km`; null quando não há distância registrada. */
fun formatPacePerKm(durationSeconds: Long, distanceMeters: Double?): String? =
    paceSecondsPerKm(durationSeconds, distanceMeters)?.let { "${formatMinuteSecond(it)}/km" }

private fun formatMinuteSecond(totalSeconds: Long): String =
    "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"

// Agrupamento fixo em ponto: o histórico é exibido em pt-BR independente do
// locale do aparelho, que usaria vírgula ou espaço.
private fun groupThousands(value: Long): String =
    value.toString().reversed().chunked(3).joinToString(".").reversed()

@Suppress("unused")
fun safeParseDate(isoDate: String?): LocalDate? = try {
    isoDate?.let { LocalDate.parse(it) }
} catch (_: DateTimeParseException) {
    null
}

fun formatDateShort(date: LocalDate): String = date.format(shortDateFormatter)

fun formatMonthYear(year: Int, month: Int): String {
    val name = Month.of(month).getDisplayName(TextStyle.FULL, ptBr)
    return name.replaceFirstChar { it.uppercaseChar() } + " de $year"
}

/**
 * Volume com carga: quilogramas até 999 e toneladas com uma casa a partir de 1000 kg.
 * O arredondamento acontece aqui, nunca no cálculo agregado.
 */
fun formatVolumeKg(volumeKg: Double): String {
    val kilograms = volumeKg.roundToLong()
    if (kilograms < 1000) return "${groupThousands(kilograms)} kg"
    val tonsTenths = (volumeKg / 100.0).roundToLong()
    return "${groupThousands(tonsTenths / 10)},${tonsTenths % 10} t"
}
