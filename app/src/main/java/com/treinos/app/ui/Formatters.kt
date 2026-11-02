package com.treinos.app.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

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

@Suppress("unused")
fun safeParseDate(isoDate: String?): LocalDate? = try {
    isoDate?.let { LocalDate.parse(it) }
} catch (_: DateTimeParseException) {
    null
}
