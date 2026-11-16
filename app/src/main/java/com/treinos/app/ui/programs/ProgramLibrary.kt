package com.treinos.app.ui.programs

import com.treinos.app.data.remote.GeneratePlanResponse
import com.treinos.app.data.remote.WorkoutDayDto
import com.treinos.app.data.remote.WorkoutExerciseDto
import com.treinos.app.data.remote.WorkoutPlanDto

/**
 * H4-10: biblioteca de programas prontos offline. Arquivo puro, sem Android:
 * o catálogo é determinístico e a única fonte de tempo é o parâmetro
 * [buildProgram.nextReviewAt], calculado pelo chamador.
 *
 * Os exercícios vêm do catálogo do app (`exercise_swap_catalog.json`, versão 1);
 * [ProgramCatalogAssetTest] confere que id/nome/músculos/equipamento batem com
 * o asset — um id removido de lá quebra o gate antes do release.
 */
data class ProgramTemplate(
    val id: String,              // "ppl" | "531" | "gzclp" | "phul"
    val name: String,            // "Push/Pull/Legs"
    val description: String,     // 1 frase
    val goal: String,            // "hypertrophy" | "strength"
    val level: String,           // "beginner" | "intermediate" | "advanced"
    val daysPerWeek: Int,        // == days.size
    val equipment: List<String>, // equipamento mínimo
    val progressionNote: String, // regra de progressão embutida, em PT
)

data class ProgramExercise(
    val exerciseId: String,
    val exerciseName: String,    // nome do catálogo (asset v1)
    val primaryMuscles: String,  // "chest" ou "chest, triceps"
    val equipment: String,       // "barbell", "dumbbell", "body only"...
    val sets: Int,
    val targetReps: String,      // "5", "3-5", "8-12" — sempre parseável pelo motor
    val targetRpe: Double?,      // 8.0 / 9.0 ou null
    val restSeconds: Int,
    val notes: String = "",      // progressão embutida daquele exercício
)

data class ProgramDay(
    val name: String,
    val dayOrder: Int,
    val exercises: List<ProgramExercise>,
)

/** Ordem fixa do catálogo: `ppl`, `531`, `gzclp`, `phul`. */
fun programCatalog(): List<ProgramTemplate> = listOf(
    template(
        id = "ppl",
        name = "Push/Pull/Legs",
        description = "Divisão clássica de 6 dias que treina cada grupo duas vezes por semana.",
        goal = "hypertrophy",
        level = "intermediate",
        progressionNote = "Adicione 2,5 kg quando fechar o topo da faixa nas duas últimas sessões; " +
            "o app sugere a carga no diálogo de série.",
        days = pplDays(),
    ),
    template(
        id = "531",
        name = "5/3/1",
        description = "Força com progressão por ciclos nos quatro levantamentos principais.",
        goal = "strength",
        level = "intermediate",
        progressionNote = "Suba 2,5 kg no principal a cada ciclo concluído; a assistência fica em 5×10 " +
            "e só sobe quando as 5 séries fecharem.",
        days = f531Days(),
    ),
    template(
        id = "gzclp",
        name = "GZCLP",
        description = "Linear progressivo em três camadas de exercício, ideal para voltar a progredir.",
        goal = "strength",
        level = "beginner",
        progressionNote = "T1 sobe 2,5 kg (5 kg no terra/agachamento) quando fechar 5×3; falhou, reinicia " +
            "em 5×2 e depois 6×1. T2/T3 sobem quando fecharem as 3 séries no topo.",
        days = gzclpDays(),
    ),
    template(
        id = "phul",
        name = "PHUL",
        description = "Quatro dias alternando blocos de potência e de hipertrofia.",
        goal = "hypertrophy",
        level = "intermediate",
        progressionNote = "Nos dias de potência, suba 2,5 kg ao fechar o topo da faixa (3-5 ou 6-10); " +
            "nos dias de hipertrofia, suba só quando fechar 8-12 em todas as séries.",
        days = phulDays(),
    ),
)

/**
 * Monta o [GeneratePlanResponse] do programa [id] — caminho 100% local:
 * `source = "local"`, sem motivo de fallback e com revisão em
 * [reviewAfterWeeks] semanas. Devolve `null` para id desconhecido.
 */
fun buildProgram(
    id: String,
    nextReviewAt: String,
    reviewAfterWeeks: Int = 4,
): GeneratePlanResponse? {
    val template = programCatalog().firstOrNull { it.id == id } ?: return null
    val days = daysFor(id)
    return GeneratePlanResponse(
        workout = WorkoutPlanDto(
            name = template.name,
            description = template.progressionNote,
            goal = template.goal,
            daysPerWeek = template.daysPerWeek,
            days = days.map { day ->
                WorkoutDayDto(
                    name = day.name,
                    dayOrder = day.dayOrder,
                    exercises = day.exercises.mapIndexed { index, exercise ->
                        WorkoutExerciseDto(
                            exerciseId = exercise.exerciseId,
                            exerciseOrder = index,
                            sets = exercise.sets,
                            targetReps = exercise.targetReps,
                            targetRpe = exercise.targetRpe,
                            restSeconds = exercise.restSeconds,
                            notes = exercise.notes,
                            exerciseName = exercise.exerciseName,
                            primaryMuscles = exercise.primaryMuscles.split(",").map { it.trim() },
                            equipment = exercise.equipment,
                        )
                    },
                )
            },
        ),
        source = "local",
        nextReviewAt = nextReviewAt,
        reviewAfterWeeks = reviewAfterWeeks,
    )
}

private fun daysFor(id: String): List<ProgramDay> = when (id) {
    "ppl" -> pplDays()
    "531" -> f531Days()
    "gzclp" -> gzclpDays()
    "phul" -> phulDays()
    else -> emptyList()
}

private fun template(
    id: String,
    name: String,
    description: String,
    goal: String,
    level: String,
    progressionNote: String,
    days: List<ProgramDay>,
): ProgramTemplate = ProgramTemplate(
    id = id,
    name = name,
    description = description,
    goal = goal,
    level = level,
    daysPerWeek = days.size,
    equipment = days.flatMap { day -> day.exercises }.map { it.equipment }.distinct().sorted(),
    progressionNote = progressionNote,
)

/** Linha da tabela da spec: séries × reps, RPE alvo, descanso e nota opcional. */
private fun ex(
    exerciseId: String,
    sets: Int,
    targetReps: String,
    targetRpe: Double?,
    restSeconds: Int,
    notes: String = "",
): ProgramExercise {
    val entry = CATALOG.getValue(exerciseId)
    return ProgramExercise(
        exerciseId = exerciseId,
        exerciseName = entry.name,
        primaryMuscles = entry.primaryMuscles,
        equipment = entry.equipment,
        sets = sets,
        targetReps = targetReps,
        targetRpe = targetRpe,
        restSeconds = restSeconds,
        notes = notes,
    )
}

/** Nota do principal do 5/3/1, igual para os quatro levantamentos. */
private const val F531_MAIN_NOTE = "Principal 5/3/1: 3×5 na semana 1; +2,5 kg no próximo ciclo"

private fun pplDays(): List<ProgramDay> = listOf(
    ProgramDay(
        name = "Push A",
        dayOrder = 0,
        exercises = listOf(
            ex("Barbell_Bench_Press_-_Medium_Grip", 4, "6-8", 8.0, 150),
            ex("Standing_Military_Press", 3, "8-10", 8.0, 120),
            ex("Barbell_Incline_Bench_Press_-_Medium_Grip", 3, "8-10", 8.0, 120),
            ex("Side_Lateral_Raise", 3, "12-15", 9.0, 60),
            ex("Triceps_Pushdown", 3, "10-12", 9.0, 60),
        ),
    ),
    ProgramDay(
        name = "Pull A",
        dayOrder = 1,
        exercises = listOf(
            ex("Barbell_Deadlift", 3, "5", 8.0, 180),
            ex("Pullups", 3, "6-10", 9.0, 120),
            ex("Bent_Over_Barbell_Row", 3, "8-10", 8.0, 120),
            ex("Face_Pull", 3, "12-15", 9.0, 60),
            ex("Barbell_Curl", 3, "10-12", 9.0, 60),
        ),
    ),
    ProgramDay(
        name = "Legs A",
        dayOrder = 2,
        exercises = listOf(
            ex("Barbell_Squat", 4, "6-8", 8.0, 180),
            ex("Romanian_Deadlift", 3, "8-10", 8.0, 120),
            ex("Leg_Press", 3, "10-12", 9.0, 90),
            ex("Lying_Leg_Curls", 3, "10-12", 9.0, 60),
            ex("Standing_Calf_Raises", 4, "12-15", 9.0, 60),
        ),
    ),
    ProgramDay(
        name = "Push B",
        dayOrder = 3,
        exercises = listOf(
            ex("Standing_Military_Press", 4, "6-8", 8.0, 150),
            ex("Dumbbell_Bench_Press", 3, "8-10", 8.0, 120),
            ex("Cable_Crossover", 3, "12-15", 9.0, 60),
            ex("Side_Lateral_Raise", 3, "12-15", 9.0, 60),
            ex("Lying_Triceps_Press", 3, "10-12", 9.0, 60),
        ),
    ),
    ProgramDay(
        name = "Pull B",
        dayOrder = 4,
        exercises = listOf(
            ex("Chin-Up", 3, "6-10", 9.0, 120),
            ex("Seated_Cable_Rows", 3, "8-10", 8.0, 120),
            ex("Face_Pull", 3, "12-15", 9.0, 60),
            ex("Hammer_Curls", 3, "10-12", 9.0, 60),
            ex("Barbell_Shrug", 3, "12-15", 9.0, 60),
        ),
    ),
    ProgramDay(
        name = "Legs B",
        dayOrder = 5,
        exercises = listOf(
            ex("Front_Squat_Clean_Grip", 4, "6-8", 8.0, 180),
            ex("Barbell_Hip_Thrust", 3, "8-10", 8.0, 120),
            ex("Leg_Extensions", 3, "10-12", 9.0, 60),
            ex("Seated_Leg_Curl", 3, "10-12", 9.0, 60),
            ex("Seated_Calf_Raise", 4, "12-15", 9.0, 60),
        ),
    ),
)

private fun f531Days(): List<ProgramDay> = listOf(
    ProgramDay(
        name = "Agachamento",
        dayOrder = 0,
        exercises = listOf(
            ex("Barbell_Squat", 3, "5", 8.0, 180, F531_MAIN_NOTE),
            ex("Leg_Press", 5, "10", 9.0, 90),
            ex("Lying_Leg_Curls", 5, "10", 9.0, 60),
            ex("Hanging_Leg_Raise", 5, "10", 9.0, 60),
        ),
    ),
    ProgramDay(
        name = "Supino",
        dayOrder = 1,
        exercises = listOf(
            ex("Barbell_Bench_Press_-_Medium_Grip", 3, "5", 8.0, 150, F531_MAIN_NOTE),
            ex("Dumbbell_Bench_Press", 5, "10", 9.0, 90),
            ex("Seated_Cable_Rows", 5, "10", 9.0, 90),
            ex("Triceps_Pushdown", 5, "10", 9.0, 60),
        ),
    ),
    ProgramDay(
        name = "Levantamento terra",
        dayOrder = 2,
        exercises = listOf(
            ex("Barbell_Deadlift", 3, "5", 8.0, 180, F531_MAIN_NOTE),
            ex("Romanian_Deadlift", 5, "10", 9.0, 90),
            ex("Leg_Extensions", 5, "10", 9.0, 60),
            ex("Crunches", 5, "10", 9.0, 60),
        ),
    ),
    ProgramDay(
        name = "Desenvolvimento",
        dayOrder = 3,
        exercises = listOf(
            ex("Standing_Military_Press", 3, "5", 8.0, 150, F531_MAIN_NOTE),
            ex("Dumbbell_Shoulder_Press", 5, "10", 9.0, 90),
            ex("Pullups", 5, "10", 9.0, 90),
            ex("Barbell_Curl", 5, "10", 9.0, 60),
        ),
    ),
)

private fun gzclpDays(): List<ProgramDay> = listOf(
    ProgramDay(
        name = "A1",
        dayOrder = 0,
        exercises = listOf(
            ex("Barbell_Squat", 5, "3", 8.0, 180, "T1"),
            ex("Barbell_Bench_Press_-_Medium_Grip", 3, "10", 8.0, 120, "T2"),
            ex("Wide-Grip_Lat_Pulldown", 3, "15", 9.0, 60, "T3"),
            ex("Seated_Cable_Rows", 3, "15", 9.0, 60, "T3"),
        ),
    ),
    ProgramDay(
        name = "B1",
        dayOrder = 1,
        exercises = listOf(
            ex("Standing_Military_Press", 5, "3", 8.0, 150, "T1"),
            ex("Barbell_Deadlift", 3, "10", 8.0, 150, "T2"),
            ex("Dumbbell_Flyes", 3, "15", 9.0, 60, "T3"),
            ex("Face_Pull", 3, "15", 9.0, 60, "T3"),
        ),
    ),
    ProgramDay(
        name = "A2",
        dayOrder = 2,
        exercises = listOf(
            ex("Barbell_Bench_Press_-_Medium_Grip", 5, "3", 8.0, 150, "T1"),
            ex("Barbell_Squat", 3, "10", 8.0, 150, "T2"),
            ex("Triceps_Pushdown", 3, "15", 9.0, 60, "T3"),
            ex("Side_Lateral_Raise", 3, "15", 9.0, 60, "T3"),
        ),
    ),
    ProgramDay(
        name = "B2",
        dayOrder = 3,
        exercises = listOf(
            ex("Barbell_Deadlift", 5, "3", 8.0, 180, "T1"),
            ex("Standing_Military_Press", 3, "10", 8.0, 120, "T2"),
            ex("Barbell_Curl", 3, "15", 9.0, 60, "T3"),
            ex("Hanging_Leg_Raise", 3, "15", 9.0, 60, "T3"),
        ),
    ),
)

private fun phulDays(): List<ProgramDay> = listOf(
    ProgramDay(
        name = "Upper Power",
        dayOrder = 0,
        exercises = listOf(
            ex("Barbell_Bench_Press_-_Medium_Grip", 4, "3-5", 8.0, 180),
            ex("Bent_Over_Barbell_Row", 4, "3-5", 8.0, 150),
            ex("Barbell_Incline_Bench_Press_-_Medium_Grip", 3, "6-10", 8.0, 120),
            ex("Seated_Cable_Rows", 3, "6-10", 8.0, 120),
            ex("Standing_Military_Press", 3, "6-10", 8.0, 120),
            ex("Barbell_Curl", 3, "6-10", 9.0, 60),
            ex("Lying_Triceps_Press", 3, "6-10", 9.0, 60),
        ),
    ),
    ProgramDay(
        name = "Lower Power",
        dayOrder = 1,
        exercises = listOf(
            ex("Barbell_Squat", 4, "3-5", 8.0, 180),
            ex("Barbell_Deadlift", 4, "3-5", 8.0, 180),
            ex("Leg_Press", 3, "6-10", 9.0, 120),
            ex("Lying_Leg_Curls", 3, "6-10", 9.0, 60),
            ex("Standing_Calf_Raises", 4, "6-10", 9.0, 60),
        ),
    ),
    ProgramDay(
        name = "Upper Hypertrophy",
        dayOrder = 2,
        exercises = listOf(
            ex("Barbell_Incline_Bench_Press_-_Medium_Grip", 4, "8-12", 9.0, 90),
            ex("Dumbbell_Flyes", 4, "8-12", 9.0, 60),
            ex("Seated_Cable_Rows", 4, "8-12", 9.0, 90),
            ex("Wide-Grip_Lat_Pulldown", 4, "8-12", 9.0, 90),
            ex("Side_Lateral_Raise", 3, "8-12", 9.0, 60),
            ex("Hammer_Curls", 3, "8-12", 9.0, 60),
            ex("Triceps_Pushdown", 3, "8-12", 9.0, 60),
        ),
    ),
    ProgramDay(
        name = "Lower Hypertrophy",
        dayOrder = 3,
        exercises = listOf(
            ex("Barbell_Full_Squat", 4, "8-12", 9.0, 120),
            ex("Romanian_Deadlift", 4, "8-12", 9.0, 90),
            ex("Leg_Extensions", 4, "8-12", 9.0, 60),
            ex("Seated_Leg_Curl", 4, "8-12", 9.0, 60),
            ex("Seated_Calf_Raise", 4, "8-12", 9.0, 60),
        ),
    ),
)

/** Entrada do catálogo do asset (v1): nome, músculos primários e equipamento. */
private data class CatalogEntry(
    val name: String,
    val primaryMuscles: String,
    val equipment: String,
)

/**
 * Espelho dos exercícios usados pelos templates, na grafia exata do asset
 * `exercise_swap_catalog.json` (v1). [ProgramCatalogAssetTest] confere a
 * paridade — aqui não há Android para ler o asset diretamente.
 */
private val CATALOG: Map<String, CatalogEntry> = mapOf(
    "Barbell_Bench_Press_-_Medium_Grip" to CatalogEntry("Barbell Bench Press - Medium Grip", "chest", "barbell"),
    "Barbell_Curl" to CatalogEntry("Barbell Curl", "biceps", "barbell"),
    "Barbell_Deadlift" to CatalogEntry("Barbell Deadlift", "lower back", "barbell"),
    "Barbell_Full_Squat" to CatalogEntry("Barbell Full Squat", "quadriceps", "barbell"),
    "Barbell_Hip_Thrust" to CatalogEntry("Barbell Hip Thrust", "glutes", "barbell"),
    "Barbell_Incline_Bench_Press_-_Medium_Grip" to CatalogEntry("Barbell Incline Bench Press - Medium Grip", "chest", "barbell"),
    "Barbell_Shrug" to CatalogEntry("Barbell Shrug", "traps", "barbell"),
    "Barbell_Squat" to CatalogEntry("Barbell Squat", "quadriceps", "barbell"),
    "Bent_Over_Barbell_Row" to CatalogEntry("Bent Over Barbell Row", "middle back", "barbell"),
    "Cable_Crossover" to CatalogEntry("Cable Crossover", "chest", "cable"),
    "Chin-Up" to CatalogEntry("Chin-Up", "lats", "body only"),
    "Crunches" to CatalogEntry("Crunches", "abdominals", "body only"),
    "Dumbbell_Bench_Press" to CatalogEntry("Dumbbell Bench Press", "chest", "dumbbell"),
    "Dumbbell_Flyes" to CatalogEntry("Dumbbell Flyes", "chest", "dumbbell"),
    "Dumbbell_Shoulder_Press" to CatalogEntry("Dumbbell Shoulder Press", "shoulders", "dumbbell"),
    "Face_Pull" to CatalogEntry("Face Pull", "shoulders", "cable"),
    "Front_Squat_Clean_Grip" to CatalogEntry("Front Squat (Clean Grip)", "quadriceps", "barbell"),
    "Hammer_Curls" to CatalogEntry("Hammer Curls", "biceps", "dumbbell"),
    "Hanging_Leg_Raise" to CatalogEntry("Hanging Leg Raise", "abdominals", "body only"),
    "Leg_Extensions" to CatalogEntry("Leg Extensions", "quadriceps", "machine"),
    "Leg_Press" to CatalogEntry("Leg Press", "quadriceps", "machine"),
    "Lying_Leg_Curls" to CatalogEntry("Lying Leg Curls", "hamstrings", "machine"),
    "Lying_Triceps_Press" to CatalogEntry("Lying Triceps Press", "triceps", "e-z curl bar"),
    "Pullups" to CatalogEntry("Pullups", "lats", "body only"),
    "Romanian_Deadlift" to CatalogEntry("Romanian Deadlift", "hamstrings", "barbell"),
    "Seated_Cable_Rows" to CatalogEntry("Seated Cable Rows", "middle back", "cable"),
    "Seated_Calf_Raise" to CatalogEntry("Seated Calf Raise", "calves", "machine"),
    "Seated_Leg_Curl" to CatalogEntry("Seated Leg Curl", "hamstrings", "machine"),
    "Side_Lateral_Raise" to CatalogEntry("Side Lateral Raise", "shoulders", "dumbbell"),
    "Standing_Calf_Raises" to CatalogEntry("Standing Calf Raises", "calves", "machine"),
    "Standing_Military_Press" to CatalogEntry("Standing Military Press", "shoulders", "barbell"),
    "Triceps_Pushdown" to CatalogEntry("Triceps Pushdown", "triceps", "cable"),
    "Wide-Grip_Lat_Pulldown" to CatalogEntry("Wide-Grip Lat Pulldown", "lats", "cable"),
)
