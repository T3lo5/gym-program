package com.treinos.app.ui.progression

import com.treinos.app.data.local.SET_TYPE_NORMAL
import com.treinos.app.data.local.SET_TYPE_WARMUP
import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionExerciseWithSets
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.WorkoutSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ProgressionEngineTest {

    private val zone = ZoneOffset.UTC
    private val nowMillis: Long = LocalDate.of(2026, 11, 3)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()

    private val dayMillis = 24L * 60L * 60L * 1000L

    private fun context(
        equipment: String,
        primaryMuscles: String = "chest",
        targetReps: String = "8-12",
        exerciseId: String = "barbell_bench_press",
    ) = ProgressionContext(
        exerciseId = exerciseId,
        targetReps = targetReps,
        equipment = equipment,
        primaryMuscles = primaryMuscles,
    )

    private fun set(
        sessionId: Long,
        daysAgo: Int,
        reps: Int?,
        weightKg: Double?,
        rpe: Double? = null,
        isCompleted: Boolean = true,
        isWarmup: Boolean = false,
        exerciseId: String = "barbell_bench_press",
    ) = ProgressionSet(
        sessionId = sessionId,
        sessionStartedAt = nowMillis - daysAgo * dayMillis,
        exerciseId = exerciseId,
        isCompleted = isCompleted,
        isWarmup = isWarmup,
        reps = reps,
        weightKg = weightKg,
        rpe = rpe,
    )

    private fun suggest(
        history: List<ProgressionSet>,
        equipment: String = "barbell",
        targetReps: String = "8-12",
    ): ProgressionSuggestion =
        suggestProgression(context(equipment = equipment, targetReps = targetReps), history, nowMillis)

    /** H4-4: sessão com séries gravadas para exercer `progressionHistory`. */
    private fun sessao(sets: List<SetLogEntity>): SessionWithExercises = SessionWithExercises(
        session = WorkoutSessionEntity(
            id = 1,
            planId = 1,
            dayId = 10,
            planName = "P",
            dayName = "A",
            status = "completed",
            startedAt = nowMillis - dayMillis,
        ),
        exercises = listOf(
            SessionExerciseWithSets(
                exercise = SessionExerciseEntity(
                    id = 100,
                    sessionId = 1,
                    exerciseId = "barbell_bench_press",
                    exerciseName = "Supino",
                    exerciseOrder = 0,
                    plannedSets = 3,
                ),
                sets = sets,
            ),
        ),
    )

    private fun setLog(
        reps: Int = 10,
        weightKg: Double = 40.0,
        rpe: Double? = null,
        rir: Int? = null,
        setType: String = SET_TYPE_NORMAL,
        isWarmup: Boolean = false,
    ) = SetLogEntity(
        id = 1,
        exerciseLogId = 100,
        setNumber = 1,
        reps = reps,
        weightKg = weightKg,
        rpe = rpe,
        setType = setType,
        rir = rir,
        isWarmup = isWarmup,
    )

    @Test
    fun historico_insuficiente_retorna_none() {
        val suggestion = suggest(listOf(set(sessionId = 1, daysAgo = 3, reps = 12, weightKg = 40.0, rpe = 8.0)))
        assertEquals(ProgressionAction.NONE, suggestion.action)
        assertNull(suggestion.suggestedWeightKg)
        assertEquals("Histórico insuficiente para sugerir progressão.", suggestion.reason)
    }

    @Test
    fun ignora_warmup_e_incompletas() {
        val suggestion = suggest(
            listOf(
                set(sessionId = 1, daysAgo = 6, reps = 12, weightKg = 40.0, rpe = 8.0, isCompleted = false),
                set(sessionId = 1, daysAgo = 6, reps = 12, weightKg = 40.0, rpe = 8.0, isWarmup = true),
                set(sessionId = 2, daysAgo = 3, reps = 12, weightKg = 40.0, rpe = 8.0),
            ),
        )
        assertEquals(ProgressionAction.NONE, suggestion.action)
    }

    @Test
    fun ignora_mais_de_42_dias() {
        val suggestion = suggest(
            listOf(
                set(sessionId = 1, daysAgo = 50, reps = 12, weightKg = 40.0, rpe = 8.0),
                set(sessionId = 2, daysAgo = 43, reps = 12, weightKg = 40.0, rpe = 8.0),
            ),
        )
        assertEquals(ProgressionAction.NONE, suggestion.action)
    }

    @Test
    fun aumenta_superior_barbell_em_2_5() {
        val suggestion = suggest(
            listOf(
                set(sessionId = 1, daysAgo = 6, reps = 12, weightKg = 40.0, rpe = 8.0),
                set(sessionId = 2, daysAgo = 3, reps = 12, weightKg = 40.0, rpe = 8.0),
            ),
        )
        assertEquals(ProgressionAction.INCREASE, suggestion.action)
        assertEquals(42.5, suggestion.suggestedWeightKg!!, 0.001)
        assertEquals("Duas sessões no topo da faixa com RPE confortável.", suggestion.reason)
    }

    @Test
    fun aumenta_inferior_barbell_em_5() {
        val suggestion = suggestProgression(
            context(equipment = "barbell", primaryMuscles = "quadriceps, hamstrings", exerciseId = "back_squat"),
            listOf(
                set(sessionId = 1, daysAgo = 6, reps = 12, weightKg = 40.0, rpe = 8.0, exerciseId = "back_squat"),
                set(sessionId = 2, daysAgo = 3, reps = 12, weightKg = 40.0, rpe = 8.0, exerciseId = "back_squat"),
            ),
            nowMillis,
        )
        assertEquals(ProgressionAction.INCREASE, suggestion.action)
        assertEquals(45.0, suggestion.suggestedWeightKg!!, 0.001)
    }

    @Test
    fun aumenta_halter_em_2_5() {
        val suggestion = suggest(
            listOf(
                set(sessionId = 1, daysAgo = 6, reps = 12, weightKg = 15.0, rpe = 8.0),
                set(sessionId = 2, daysAgo = 3, reps = 12, weightKg = 15.0, rpe = 8.0),
            ),
            equipment = "dumbbell",
        )
        assertEquals(ProgressionAction.INCREASE, suggestion.action)
        assertEquals(17.5, suggestion.suggestedWeightKg!!, 0.001)
    }

    @Test
    fun peso_corporal_aumenta_uma_rep() {
        val suggestion = suggest(
            listOf(
                set(sessionId = 1, daysAgo = 6, reps = 12, weightKg = null, rpe = 8.0),
                set(sessionId = 2, daysAgo = 3, reps = 12, weightKg = null, rpe = 8.0),
            ),
            equipment = "body only",
        )
        assertEquals(ProgressionAction.INCREASE, suggestion.action)
        assertNull(suggestion.suggestedWeightKg)
        assertEquals(13, suggestion.suggestedReps)
    }

    @Test
    fun rpe_alto_reduz_cinco_por_cento() {
        val suggestion = suggest(
            listOf(
                set(sessionId = 1, daysAgo = 6, reps = 12, weightKg = 40.0, rpe = 8.0),
                set(sessionId = 2, daysAgo = 3, reps = 12, weightKg = 40.0, rpe = 10.0),
            ),
        )
        assertEquals(ProgressionAction.DECREASE, suggestion.action)
        assertEquals(37.5, suggestion.suggestedWeightKg!!, 0.001)
        assertEquals("RPE alto ou repetições abaixo da faixa; reduza a carga.", suggestion.reason)
    }

    @Test
    fun reps_abaixo_da_faixa_reduz() {
        val suggestion = suggest(
            listOf(
                set(sessionId = 1, daysAgo = 6, reps = 12, weightKg = 40.0, rpe = 8.0),
                set(sessionId = 2, daysAgo = 3, reps = 6, weightKg = 40.0, rpe = 9.0),
            ),
        )
        assertEquals(ProgressionAction.DECREASE, suggestion.action)
        assertEquals(37.5, suggestion.suggestedWeightKg!!, 0.001)
    }

    @Test
    fun rpe_ausente_no_topo_aumenta() {
        val suggestion = suggest(
            listOf(
                set(sessionId = 1, daysAgo = 6, reps = 12, weightKg = 40.0),
                set(sessionId = 2, daysAgo = 3, reps = 12, weightKg = 40.0),
            ),
        )
        assertEquals(ProgressionAction.INCREASE, suggestion.action)
        assertEquals(42.5, suggestion.suggestedWeightKg!!, 0.001)
    }

    @Test
    fun rpe_86_mantem() {
        val suggestion = suggest(
            listOf(
                set(sessionId = 1, daysAgo = 6, reps = 12, weightKg = 40.0, rpe = 8.0),
                set(sessionId = 2, daysAgo = 3, reps = 12, weightKg = 40.0, rpe = 8.6),
            ),
        )
        assertEquals(ProgressionAction.MAINTAIN, suggestion.action)
        assertEquals(40.0, suggestion.suggestedWeightKg!!, 0.001)
        assertNull(suggestion.suggestedReps)
    }

    @Test
    fun limita_carga_minima() {
        val suggestion = suggest(
            listOf(
                set(sessionId = 1, daysAgo = 6, reps = 12, weightKg = 2.5, rpe = 8.0),
                set(sessionId = 2, daysAgo = 3, reps = 4, weightKg = 2.5, rpe = 8.0),
            ),
        )
        assertEquals(ProgressionAction.DECREASE, suggestion.action)
        assertEquals(2.5, suggestion.suggestedWeightKg!!, 0.001)
    }

    @Test
    fun limita_carga_maxima() {
        val suggestion = suggest(
            listOf(
                set(sessionId = 1, daysAgo = 6, reps = 12, weightKg = 500.0, rpe = 8.0),
                set(sessionId = 2, daysAgo = 3, reps = 12, weightKg = 500.0, rpe = 8.0),
            ),
        )
        assertEquals(ProgressionAction.INCREASE, suggestion.action)
        assertEquals(500.0, suggestion.suggestedWeightKg!!, 0.001)
    }

    @Test
    fun faixa_invertida_retorna_none() {
        val history = listOf(
            set(sessionId = 1, daysAgo = 6, reps = 12, weightKg = 40.0, rpe = 8.0),
            set(sessionId = 2, daysAgo = 3, reps = 12, weightKg = 40.0, rpe = 8.0),
        )
        assertEquals(ProgressionAction.NONE, suggest(history, targetReps = "12-8").action)
        assertEquals(ProgressionAction.INCREASE, suggest(history, targetReps = "8 - 12").action)
    }

    @Test
    fun alvo_amrap_retorna_none() {
        val suggestion = suggestProgression(
            context(equipment = "barbell", targetReps = "AMRAP"),
            listOf(
                set(sessionId = 1, daysAgo = 6, reps = 12, weightKg = 40.0, rpe = 8.0),
                set(sessionId = 2, daysAgo = 3, reps = 12, weightKg = 40.0, rpe = 8.0),
            ),
            nowMillis,
        )
        assertEquals(ProgressionAction.NONE, suggestion.action)
    }

    @Test
    fun arredonda_para_multiplo_de_2_5() {
        assertEquals(40.0, roundToIncrement(41.0), 0.001)
        assertEquals(42.5, roundToIncrement(43.0), 0.001)
        assertEquals(42.5, roundToIncrement(42.5), 0.001)
    }

    // ---------- H4-4: RIR ----------

    @Test
    fun rir_vira_rpe_na_historia() {
        // RIR 2 sem RPE: a progressão trata o esforço como RPE 8 (10 - RIR).
        val history = progressionHistory(
            listOf(sessao(listOf(setLog(rir = 2)))),
            "barbell_bench_press",
        )

        assertEquals(1, history.size)
        assertEquals(8.0, history.single().rpe!!, 0.0001)
    }

    @Test
    fun rpe_presente_vence_rir() {
        // Com os dois preenchidos, RPE é a fonte da verdade.
        val history = progressionHistory(
            listOf(sessao(listOf(setLog(rpe = 7.0, rir = 0)))),
            "barbell_bench_press",
        )

        assertEquals(7.0, history.single().rpe!!, 0.0001)
    }

    @Test
    fun warmup_com_rir_continua_ignorado() {
        // Aquecimento com RIR não entra na progressão: histórico insuficiente.
        val history = progressionHistory(
            listOf(sessao(listOf(setLog(setType = SET_TYPE_WARMUP, isWarmup = true, rir = 0)))),
            "barbell_bench_press",
        )
        assertEquals(1, history.size)

        val suggestion = suggestProgression(
            context(equipment = "barbell"),
            history,
            nowMillis,
        )
        assertEquals(ProgressionAction.NONE, suggestion.action)
    }
}
