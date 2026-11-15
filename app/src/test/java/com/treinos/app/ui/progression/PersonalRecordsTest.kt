package com.treinos.app.ui.progression

import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionExerciseWithSets
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.WorkoutSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H4-1: regras puras de PR e 1RM estimado (docs/specs/H4-1-recordes-pessoais-1rm.md).
 * Cada caso cobre uma regra da spec; sem Robolectric, sem Room, sem Android.
 */
class PersonalRecordsTest {

    private fun sessao(
        id: Long,
        startedAt: Long,
        status: String = "completed",
        sets: List<SetLogEntity>,
        exerciseId: String = "Barbell_Bench_Press",
    ): SessionWithExercises = SessionWithExercises(
        session = WorkoutSessionEntity(
            id = id,
            planId = 1,
            dayId = 10,
            planName = "Plano Teste",
            dayName = "Treino A",
            status = status,
            startedAt = startedAt,
        ),
        exercises = listOf(
            SessionExerciseWithSets(
                exercise = SessionExerciseEntity(
                    id = id * 100,
                    sessionId = id,
                    exerciseId = exerciseId,
                    exerciseName = "Supino reto",
                    exerciseOrder = 0,
                ),
                sets = sets,
            ),
        ),
    )

    /** Série de banco (SetLogEntity) para montar sessões. */
    private fun serie(
        setNumber: Int = 1,
        reps: Int? = 10,
        weightKg: Double? = 60.0,
        rpe: Double? = null,
        isCompleted: Boolean = true,
        isWarmup: Boolean = false,
    ) = SetLogEntity(
        exerciseLogId = 100,
        setNumber = setNumber,
        reps = reps,
        weightKg = weightKg,
        rpe = rpe,
        isCompleted = isCompleted,
        isWarmup = isWarmup,
    )

    /** Candidato a PR (RecordSet, modelo puro) para [isPersonalRecord]. */
    private fun candidato(
        reps: Int? = 10,
        weightKg: Double? = 60.0,
        rpe: Double? = null,
        isCompleted: Boolean = true,
        isWarmup: Boolean = false,
    ) = RecordSet(
        sessionId = 1L,
        sessionStartedAt = 1_000L,
        reps = reps,
        weightKg = weightKg,
        rpe = rpe,
        isCompleted = isCompleted,
        isWarmup = isWarmup,
    )

    @Test
    fun epley_reps_um_igual_carga() {
        // Série de 1 rep É o 1RM real — as duas fórmulas devolvem a própria carga.
        assertEquals(60.0, estimateOneRepMax(60.0, 1, OneRepMaxFormula.EPLEY)!!, 0.0)
    }

    @Test
    fun epley_40x10_estima_53_33() {
        assertEquals(53.33, estimateOneRepMax(40.0, 10, OneRepMaxFormula.EPLEY)!!, 0.01)
    }

    @Test
    fun brzycki_reps_um_igual_carga() {
        assertEquals(60.0, estimateOneRepMax(60.0, 1, OneRepMaxFormula.BRZYCKI)!!, 0.0)
    }

    @Test
    fun brzycki_reps_37_retorna_null() {
        assertNull(estimateOneRepMax(60.0, 37, OneRepMaxFormula.BRZYCKI))
    }

    @Test
    fun carga_invalida_retorna_null() {
        assertNull(estimateOneRepMax(null, 10))
        assertNull(estimateOneRepMax(0.0, 10))
        assertNull(estimateOneRepMax(Double.NaN, 10))
        assertNull(estimateOneRepMax(Double.POSITIVE_INFINITY, 10))
        assertNull(estimateOneRepMax(60.0, null))
        assertNull(estimateOneRepMax(60.0, 0))
        assertNull(estimateOneRepMax(60.0, 101))
    }

    @Test
    fun primeiro_registro_valido_e_pr() {
        assertTrue(isPersonalRecord(candidato(reps = 10, weightKg = 40.0), null))
    }

    @Test
    fun empate_nao_e_pr() {
        val record = PersonalRecord(
            exerciseId = "Barbell_Bench_Press",
            weightKg = 40.0,
            reps = 10,
            estimated1RmKg = estimateOneRepMax(40.0, 10),
            achievedAtMillis = 1_000L,
        )
        assertFalse(isPersonalRecord(candidato(reps = 10, weightKg = 40.0), record))
    }

    @Test
    fun carga_maior_1rm_menor_nao_e_pr() {
        // 40 kg × 10 (Epley 53,33) continua recorde contra 30 kg × 20 (50,0):
        // a comparação é por 1RM estimado, nunca por carga ou reps isoladas.
        val record = PersonalRecord(
            exerciseId = "Barbell_Bench_Press",
            weightKg = 40.0,
            reps = 10,
            estimated1RmKg = estimateOneRepMax(40.0, 10),
            achievedAtMillis = 1_000L,
        )
        assertFalse(isPersonalRecord(candidato(reps = 20, weightKg = 30.0), record))
        assertTrue(isPersonalRecord(candidato(reps = 8, weightKg = 45.0), record))
    }

    @Test
    fun peso_corporal_maior_reps_e_pr() {
        val record = PersonalRecord(
            exerciseId = "Barbell_Bench_Press",
            weightKg = null,
            reps = 15,
            estimated1RmKg = null,
            achievedAtMillis = 1_000L,
        )
        assertTrue(isPersonalRecord(candidato(reps = 16, weightKg = null), record))
        assertFalse(isPersonalRecord(candidato(reps = 15, weightKg = null), record))
    }

    @Test
    fun peso_corporal_empate_nao_e_pr() {
        val record = PersonalRecord(
            exerciseId = "Barbell_Bench_Press",
            weightKg = null,
            reps = 15,
            estimated1RmKg = null,
            achievedAtMillis = 1_000L,
        )
        assertFalse(isPersonalRecord(candidato(reps = 15, weightKg = null), record))
    }

    @Test
    fun carga_vence_recorde_de_peso_corporal() {
        val record = PersonalRecord(
            exerciseId = "Barbell_Bench_Press",
            weightKg = null,
            reps = 15,
            estimated1RmKg = null,
            achievedAtMillis = 1_000L,
        )
        assertTrue(isPersonalRecord(candidato(reps = 5, weightKg = 40.0), record))
    }

    @Test
    fun peso_corporal_nao_vence_recorde_com_carga() {
        val record = PersonalRecord(
            exerciseId = "Barbell_Bench_Press",
            weightKg = 40.0,
            reps = 10,
            estimated1RmKg = estimateOneRepMax(40.0, 10),
            achievedAtMillis = 1_000L,
        )
        assertFalse(isPersonalRecord(candidato(reps = 30, weightKg = null), record))
    }

    @Test
    fun ignora_warmup_e_incompletas() {
        val sessions = listOf(
            sessao(
                id = 1,
                startedAt = 1_000L,
                sets = listOf(
                    // Aquecimento e série incompleta com carga maior: não contam.
                    serie(setNumber = 1, reps = 6, weightKg = 100.0, isWarmup = true),
                    serie(setNumber = 2, reps = 6, weightKg = 100.0, isCompleted = false),
                    serie(setNumber = 3, reps = 10, weightKg = 60.0),
                ),
            ),
        )
        val record = personalRecord(sessions, "Barbell_Bench_Press")
        assertEquals(60.0, record?.weightKg)
        assertEquals(10, record?.reps)
    }

    @Test
    fun reps_fora_da_faixa_ignorada() {
        val sessions = listOf(
            sessao(
                id = 1,
                startedAt = 1_000L,
                sets = listOf(
                    serie(setNumber = 1, reps = 0, weightKg = 100.0),
                    serie(setNumber = 2, reps = 101, weightKg = 100.0),
                    serie(setNumber = 3, reps = 10, weightKg = 60.0),
                ),
            ),
        )
        val record = personalRecord(sessions, "Barbell_Bench_Press")
        assertEquals(10, record?.reps)
        // Candidato inválido nunca é PR, nem contra recorde nulo.
        assertFalse(isPersonalRecord(candidato(reps = 0, weightKg = 100.0), null))
        assertFalse(isPersonalRecord(candidato(reps = 101, weightKg = 100.0), null))
        assertFalse(isPersonalRecord(candidato(reps = null, weightKg = 100.0), null))
        assertFalse(isPersonalRecord(candidato(reps = 10, weightKg = 60.0, isCompleted = false), null))
        assertFalse(isPersonalRecord(candidato(reps = 10, weightKg = 60.0, isWarmup = true), null))
    }

    @Test
    fun melhor_registro_desempata_por_carga_depois_reps() {
        // 45 × 10 e 50 × 6 têm o mesmo 1RM Epley (60,0): a carga maior vence.
        val sessions = listOf(
            sessao(id = 1, startedAt = 1_000L, sets = listOf(serie(setNumber = 1, reps = 10, weightKg = 45.0))),
            sessao(id = 2, startedAt = 2_000L, sets = listOf(serie(setNumber = 1, reps = 6, weightKg = 50.0))),
        )
        val record = personalRecord(sessions, "Barbell_Bench_Press")
        assertEquals(50.0, record?.weightKg)
        assertEquals(6, record?.reps)
        assertEquals(2_000L, record?.achievedAtMillis)
    }

    @Test
    fun historico_vazio_retorna_null() {
        assertNull(personalRecord(emptyList(), "Barbell_Bench_Press"))
    }

    @Test
    fun exercicio_ausente_retorna_null() {
        val sessions = listOf(
            sessao(id = 1, startedAt = 1_000L, sets = listOf(serie()), exerciseId = "Barbell_Bench_Press"),
        )
        assertNull(personalRecord(sessions, "Dumbbell_Bench_Press"))
    }

    @Test
    fun sessao_em_andamento_conta() {
        // Regra 11: a série registrada em sessão in_progress é dado real.
        val sessions = listOf(
            sessao(
                id = 1,
                startedAt = 1_000L,
                status = "in_progress",
                sets = listOf(serie(reps = 10, weightKg = 60.0)),
            ),
        )
        val record = personalRecord(sessions, "Barbell_Bench_Press")
        assertEquals(60.0, record?.weightKg)
        assertEquals(80.0, record?.estimated1RmKg!!, 0.01)
    }
}
