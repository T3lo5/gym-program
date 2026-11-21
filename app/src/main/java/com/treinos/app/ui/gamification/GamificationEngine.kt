package com.treinos.app.ui.gamification

import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.ui.history.STATUS_COMPLETED
import com.treinos.app.ui.history.buildStreak
import com.treinos.app.ui.history.sessionDay
import com.treinos.app.ui.progression.PersonalRecord
import com.treinos.app.ui.progression.personalRecord
import java.time.LocalDate
import java.time.ZoneId

/**
 * H6-8: gamificação — nível, XP e badges derivados dos dados existentes
 * (sessões concluídas, streak e recordes pessoais). Leitura pura, sem Android,
 * sem Room, sem rede, sem persistência de estado.
 */

data class GamificationLevel(
    val level: Int,
    val name: String,
    val xpRequired: Int,
    val color: Int,
)

data class Badge(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val xpReward: Int,
    val color: Int,
    val unlocked: Boolean,
    val unlockedAt: Long?,
)

data class UserGamificationState(
    val level: Int,
    val xp: Int,
    val xpToNext: Int,
    val badges: List<Badge>,
    val totalSessions: Int,
    val currentStreak: Int,
    val totalPrs: Int,
)

private const val XP_PER_SESSION = 50

private const val LEVEL_1_NAME = "Iniciante"
private const val LEVEL_2_NAME = "Constante"
private const val LEVEL_3_NAME = "Dedicado"
private const val LEVEL_4_NAME = "Avançado"
private const val LEVEL_5_NAME = "Mestre"

/** Níveis com XP exigido acumulado (1º nível = 0 XP, 2º = 100, 3º = 300, …). */
private val LEVELS = listOf(
    GamificationLevel(1, LEVEL_1_NAME, 0, 0xFF00C853.toInt().toInt()),
    GamificationLevel(2, LEVEL_2_NAME, 100, 0xFF00BFA5.toInt().toInt()),
    GamificationLevel(3, LEVEL_3_NAME, 300, 0xFF3D5AFE.toInt().toInt()),
    GamificationLevel(4, LEVEL_4_NAME, 600, 0xFF7C4DFF.toInt().toInt()),
    GamificationLevel(5, LEVEL_5_NAME, 1000, 0xFFFF6F00.toInt().toInt()),
)

/** Catálogo de badges disponíveis (não conquistados). */
private val BADGE_CATALOG = listOf(
    Badge(
        id = "primeiro_treino",
        title = "Primeiro treino",
        description = "Complete sua primeira sessão.",
        icon = "fitness_center",
        xpReward = 50,
        color = 0xFF00C853.toInt(),
        unlocked = false,
        unlockedAt = null,
    ),
    Badge(
        id = "semana_de_ouro",
        title = "Semana de ouro",
        description = "Complete 7 sessões.",
        icon = "emoji_events",
        xpReward = 100,
        color = 0xFFFF6F00.toInt(),
        unlocked = false,
        unlockedAt = null,
    ),
    Badge(
        id = "marcador_cem",
        title = "Marcador cem",
        description = "Complete 100 sessões.",
        icon = "military_tech",
        xpReward = 500,
        color = 0xFF7C4DFF.toInt(),
        unlocked = false,
        unlockedAt = null,
    ),
    Badge(
        id = "estreia_de_streak",
        title = "Estreia de streak",
        description = "Mantenha uma sequência de 3 dias.",
        icon = "repeat",
        xpReward = 75,
        color = 0xFF00BFA5.toInt(),
        unlocked = false,
        unlockedAt = null,
    ),
    Badge(
        id = "semana_ininterrupta",
        title = "Semana ininterrupta",
        description = "Mantenha uma sequência de 7 dias.",
        icon = "today",
        xpReward = 150,
        color = 0xFFE65100.toInt(),
        unlocked = false,
        unlockedAt = null,
    ),
    Badge(
        id = "primeiro_recorde",
        title = "Primeiro recorde",
        description = "Bata seu primeiro recorde pessoal.",
        icon = "record_voice_over",
        xpReward = 100,
        color = 0xFF3D5AFE.toInt(),
        unlocked = false,
        unlockedAt = null,
    ),
    Badge(
        id = "colecionador_de_recordes",
        title = "Colecionador de recordes",
        description = "Bata 5 recordes pessoais.",
        icon = "star",
        xpReward = 200,
        color = 0xFFFF6F00.toInt(),
        unlocked = false,
        unlockedAt = null,
    ),
    Badge(
        id = "maratonista_modalidade",
        title = "Maratonista de modalidade",
        description = "Treine nas três modalidades (força, corrida, mobilidade).",
        icon = "sports",
        xpReward = 150,
        color = 0xFF00C853.toInt(),
        unlocked = false,
        unlockedAt = null,
    ),
)

private val MODALITY_STRENGTH = "strength"
private val MODALITY_RUN = "run"
private val MODALITY_MOBILITY = "mobility"

/** XP acumulado necessário para atingir o nível `n` (n >= 1): 0, 100, 300, 600, 1000. */
fun xpRequiredForLevel(level: Int): Int {
    if (level <= 1) return 0
    return 50 * (level - 1) * level
}

/** O nível correspondente a uma quantidade de XP acumulado. */
fun levelForXp(xp: Int): Int {
    var level = 1
    for (l in LEVELS) {
        if (xp >= l.xpRequired) level = l.level
    }
    return level.coerceAtMost(LEVELS.lastOrNull()?.level ?: level)
}

/** Nome do nível para um dado nível. */
fun levelName(level: Int): String = LEVELS.firstOrNull { it.level == level }?.name ?: LEVEL_1_NAME

/** Cor do nível. */
fun levelColor(level: Int): Int = LEVELS.firstOrNull { it.level == level }?.color ?: LEVELS.first().color

/**
 * Converte uma sessão em `LocalDate` no fuso do sistema (mesma regra de
 * [sessionDay] da H4-13).
 */
private fun sessionDate(session: SessionWithExercises): LocalDate =
    sessionDay(session)

/** Datas distintas com sessão concluída, descartando datas futuras. */
private fun completedDates(sessions: List<SessionWithExercises>, today: LocalDate): Set<LocalDate> =
    sessions
        .filter { it.session.status == STATUS_COMPLETED }
        .mapNotNull { sessionDate(it) }
        .filter { !it.isAfter(today) }
        .toSet()

/** Modalidades distintas em sessões concluídas. */
private fun completedModalities(sessions: List<SessionWithExercises>): Set<String> =
    sessions
        .filter { it.session.status == STATUS_COMPLETED }
        .map { it.session.modality }
        .toSet()

/**
 * Número de exercícios com pelo menos um recorde pessoal (reusa o motor
 * canônico da H4-1).
 */
private fun countPersonalRecords(sessions: List<SessionWithExercises>): Int {
    val ids = linkedSetOf<String>()
    sessions.forEach { session ->
        session.exercises.forEach { exercise -> ids.add(exercise.exercise.exerciseId) }
    }
    return ids.count { id -> personalRecord(sessions, id) != null }
}

/**
 * Calcula o estado de gamificação a partir das sessões, streak e hoje.
 * [today] injetável para testes determinísticos.
 */
fun computeGamificationState(
    sessions: List<SessionWithExercises>,
    streak: com.treinos.app.ui.history.StreakSummary,
    today: LocalDate = LocalDate.now(ZoneId.systemDefault()),
): UserGamificationState {
    val completed = sessions.filter { it.session.status == STATUS_COMPLETED }
    val totalSessions = completed.size
    val xp = totalSessions * XP_PER_SESSION
    val level = levelForXp(xp)
    val xpRequiredForCurrent = xpRequiredForLevel(level)
    val xpRequiredForNext = xpRequiredForLevel(level + 1)
    val xpToNext = if (xpRequiredForNext <= xpRequiredForCurrent) 0 else xpRequiredForNext - xp
    val modalities = completedModalities(completed)
    val totalPrs = countPersonalRecords(sessions)

    val badges = BADGE_CATALOG.map { badge ->
        val unlocked = when (badge.id) {
            "primeiro_treino" -> totalSessions >= 1
            "semana_de_ouro" -> totalSessions >= 7
            "marcador_cem" -> totalSessions >= 100
            "estreia_de_streak" -> streak.currentDays >= 3
            "semana_ininterrupta" -> streak.currentDays >= 7
            "primeiro_recorde" -> totalPrs >= 1
            "colecionador_de_recordes" -> totalPrs >= 5
            else -> false
        }
        val maratonista = badge.id == "maratonista_modalidade" &&
            modalities.contains(MODALITY_STRENGTH) &&
            modalities.contains(MODALITY_RUN) &&
            modalities.contains(MODALITY_MOBILITY)
        val unlockedFinal = if (badge.id == "maratonista_modalidade") maratonista else unlocked
        val unlockedAt = if (unlockedFinal) {
            // Primeira sessão concluída como aproximação da data de desbloqueio.
            completed.firstOrNull()?.session?.startedAt ?: null
        } else null
        badge.copy(unlocked = unlockedFinal, unlockedAt = unlockedAt)
    }

    return UserGamificationState(
        level = level,
        xp = xp,
        xpToNext = xpToNext,
        badges = badges.sortedBy { !it.unlocked },
        totalSessions = totalSessions,
        currentStreak = streak.currentDays,
        totalPrs = totalPrs,
    )
}
