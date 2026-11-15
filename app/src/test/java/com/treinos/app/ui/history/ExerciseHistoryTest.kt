package com.treinos.app.ui.history

import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionExerciseWithSets
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.WorkoutSessionEntity
import com.treinos.app.ui.progression.personalRecord
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H4-3: agregação pura do drill-down por exercício — só sessões de musculação
 * concluídas, séries válidas (fora de aquecimento, reps 1..100), lista da mais
 * recente para a mais antiga, recorde progressivo pela regra da H4-1 e série
 * de pontos idêntica à do gráfico da H4-2.
 */
class ExerciseHistoryTest {

    private val day = LocalDate.of(2026, 11, 10)

    @Test
    fun lista_sessoes_mais_recente_primeiro() {
        val sessions = listOf(
            session(id = 1, startedAt = millis(LocalDate.of(2026, 11, 10)), exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0)))),
            session(id = 3, startedAt = millis(LocalDate.of(2026, 11, 20)), exercises = listOf(exercise("ex1", set(reps = 5, weight = 45.0)))),
            session(id = 2, startedAt = millis(LocalDate.of(2026, 11, 15)), exercises = listOf(exercise("ex1", set(reps = 8, weight = 42.5)))),
        )

        val history = buildExerciseHistory(sessions, "ex1")

        assertEquals(listOf(3L, 2L, 1L), history.sessions.map { it.sessionId })
        assertTrue(
            history.sessions.zipWithNext().all { (a, b) -> a.startedAtMillis >= b.startedAtMillis },
        )
    }

    @Test
    fun ignora_sessao_em_andamento() {
        val sessions = listOf(
            session(id = 1, exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0)))),
            session(
                id = 2,
                status = STATUS_IN_PROGRESS,
                exercises = listOf(exercise("ex1", set(reps = 10, weight = 60.0))),
            ),
        )

        val history = buildExerciseHistory(sessions, "ex1")

        // A sessão em andamento não aparece na lista nem no recorde (critério 7).
        assertEquals(listOf(1L), history.sessions.map { it.sessionId })
        assertEquals(40.0, requireNotNull(requireNotNull(history.allTimeRecord).weightKg), EPSILON)
    }

    @Test
    fun ignora_corrida_e_mobilidade() {
        val sessions = listOf(
            session(
                id = 1,
                modality = "run",
                exercises = listOf(exercise("ex1", set(reps = 10, weight = 60.0))),
            ),
            session(
                id = 2,
                modality = "mobility",
                exercises = listOf(exercise("ex1", set(reps = 10, weight = 60.0))),
            ),
            session(id = 3, exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0)))),
        )

        val history = buildExerciseHistory(sessions, "ex1")

        assertEquals(listOf(3L), history.sessions.map { it.sessionId })
        assertEquals(40.0, requireNotNull(requireNotNull(history.allTimeRecord).weightKg), EPSILON)
    }

    @Test
    fun ignora_warmup_e_series_invalidas() {
        val sessions = listOf(
            session(
                id = 1,
                exercises = listOf(
                    exercise(
                        "ex1",
                        set(reps = 12, weight = 50.0, isWarmup = true),
                        set(reps = 10, weight = 60.0, completed = false),
                        set(reps = 0, weight = 60.0),
                        set(reps = 150, weight = 100.0),
                        set(reps = 10, weight = 40.0),
                    ),
                ),
            ),
        )

        val summary = buildExerciseHistory(sessions, "ex1").sessions.single()

        // Só a série válida entra: melhor série, volume e contagem (regras 2 e 5).
        assertEquals(40.0, requireNotNull(summary.bestSetWeightKg), EPSILON)
        assertEquals(10, summary.bestSetReps)
        assertEquals(400.0, summary.volumeKg, EPSILON)
        assertEquals(1, summary.sets)
    }

    @Test
    fun melhor_serie_por_carga_desempata_reps() {
        val porCarga = listOf(
            session(
                id = 1,
                exercises = listOf(
                    exercise(
                        "ex1",
                        set(reps = 10, weight = 40.0),
                        set(reps = 8, weight = 45.0),
                    ),
                ),
            ),
        )
        val empate = listOf(
            session(
                id = 2,
                exercises = listOf(
                    exercise(
                        "ex1",
                        set(reps = 8, weight = 40.0),
                        set(reps = 10, weight = 40.0),
                    ),
                ),
            ),
        )

        assertEquals(45.0, requireNotNull(buildExerciseHistory(porCarga, "ex1").sessions.single().bestSetWeightKg), EPSILON)
        val melhorEmpate = buildExerciseHistory(empate, "ex1").sessions.single()
        assertEquals(40.0, requireNotNull(melhorEmpate.bestSetWeightKg), EPSILON)
        assertEquals(10, melhorEmpate.bestSetReps)
    }

    @Test
    fun volume_soma_reps_vezes_carga() {
        val sessions = listOf(
            session(
                id = 1,
                exercises = listOf(
                    exercise(
                        "ex1",
                        set(reps = 10, weight = 40.0),
                        set(reps = 8, weight = 42.5),
                        set(reps = 15, weight = null),
                    ),
                ),
            ),
        )

        val summary = buildExerciseHistory(sessions, "ex1").sessions.single()

        // 10×40 + 8×42,5 + 15 sem carga (soma 0) = 740 kg, 3 séries válidas.
        assertEquals(740.0, summary.volumeKg, EPSILON)
        assertEquals(3, summary.sets)
        assertEquals(42.5, requireNotNull(summary.bestSetWeightKg), EPSILON)
    }

    @Test
    fun recorde_progressivo_marca_apenas_quem_superou() {
        val sessions = listOf(
            session(id = 1, startedAt = millis(LocalDate.of(2026, 11, 10)), exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0)))),
            session(id = 2, startedAt = millis(LocalDate.of(2026, 11, 15)), exercises = listOf(exercise("ex1", set(reps = 8, weight = 50.0)))),
            session(id = 3, startedAt = millis(LocalDate.of(2026, 11, 20)), exercises = listOf(exercise("ex1", set(reps = 9, weight = 45.0)))),
        )

        val history = buildExerciseHistory(sessions, "ex1")

        // Cronológico: 1ª é PR, 2ª supera (50×8 > 40×10), 3ª não supera.
        // Na lista (mais recente primeiro): [3ª não, 2ª PR, 1ª PR].
        assertEquals(listOf(false, true, true), history.sessions.map { it.isPersonalRecord })
    }

    @Test
    fun primeira_sessao_valida_e_recorde() {
        val sessions = listOf(
            session(id = 1, exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0)))),
        )

        val history = buildExerciseHistory(sessions, "ex1")

        assertTrue(history.sessions.single().isPersonalRecord)
    }

    @Test
    fun all_time_record_usa_h4_1() {
        val sessions = listOf(
            session(id = 1, startedAt = millis(LocalDate.of(2026, 11, 10)), exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0)))),
            session(id = 2, startedAt = millis(LocalDate.of(2026, 11, 15)), exercises = listOf(exercise("ex1", set(reps = 8, weight = 50.0)))),
        )

        val history = buildExerciseHistory(sessions, "ex1")

        assertEquals(personalRecord(sessions, "ex1"), history.allTimeRecord)
        assertEquals(50.0, requireNotNull(requireNotNull(history.allTimeRecord).weightKg), EPSILON)
    }

    @Test
    fun serie_de_pontos_igual_ao_grafico() {
        val sessions = listOf(
            session(id = 1, startedAt = millis(LocalDate.of(2026, 11, 10)), exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0)))),
            session(id = 2, startedAt = millis(LocalDate.of(2026, 11, 15)), exercises = listOf(exercise("ex1", set(reps = 8, weight = 50.0)))),
        )

        val history = buildExerciseHistory(sessions, "ex1")

        assertEquals(
            exerciseProgressSeries(sessions, "ex1", ExerciseChartMetric.BEST_SET_KG),
            history.series,
        )
        assertEquals(listOf(40.0, 50.0), history.series.points.map { it.value })
    }

    @Test
    fun exercicio_sem_sessoes_retorna_vazio() {
        val vazio = buildExerciseHistory(emptyList(), "ex1")
        assertEquals(0, vazio.sessions.size)
        assertNull(vazio.allTimeRecord)
        assertTrue(vazio.series.points.isEmpty())

        val outroExercicio = listOf(
            session(id = 1, exercises = listOf(exercise("ex2", set(reps = 10, weight = 40.0)))),
        )
        val ausente = buildExerciseHistory(outroExercicio, "ex1")
        assertEquals(0, ausente.sessions.size)
        assertNull(ausente.allTimeRecord)
        assertTrue(ausente.series.points.isEmpty())
    }

    @Test
    fun uma_sessao_nao_quebra_grafico() {
        val sessions = listOf(
            session(id = 1, exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0)))),
        )

        val history = buildExerciseHistory(sessions, "ex1")

        assertEquals(1, history.sessions.size)
        assertEquals(1, history.series.points.size)
        assertTrue(history.sessions.single().isPersonalRecord)
    }

    @Test
    fun sessao_sem_series_validas_nao_entra() {
        val sessions = listOf(
            session(
                id = 1,
                exercises = listOf(exercise("ex1", set(reps = 12, weight = 50.0, isWarmup = true))),
            ),
            session(id = 2, exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0)))),
        )

        val history = buildExerciseHistory(sessions, "ex1")

        // A sessão só de aquecimento não gera linha com zeros (regra 11).
        assertEquals(listOf(2L), history.sessions.map { it.sessionId })
        assertEquals(40.0, requireNotNull(requireNotNull(history.allTimeRecord).weightKg), EPSILON)
    }

    @Test
    fun nome_vazio_cai_para_exercise_id() {
        val semNome = listOf(
            session(
                id = 1,
                exercises = listOf(
                    exercise("ex1", set(reps = 10, weight = 40.0))
                        .copy(exercise = exercise("ex1").exercise.copy(exerciseName = "")),
                ),
            ),
        )

        // Param vazio e nome vazio no histórico: cai para o exerciseId.
        assertEquals("ex1", buildExerciseHistory(semNome, "ex1").exerciseName)
        // Param preenchido vence.
        assertEquals("Supino", buildExerciseHistory(semNome, "ex1", exerciseName = "Supino").exerciseName)
        // Param vazio, mas o histórico tem nome: usa o do histórico.
        val comNome = listOf(
            session(
                id = 1,
                exercises = listOf(
                    exercise("ex1", set(reps = 10, weight = 40.0))
                        .copy(exercise = exercise("ex1").exercise.copy(exerciseName = "Supino reto")),
                ),
            ),
        )
        assertEquals("Supino reto", buildExerciseHistory(comNome, "ex1").exerciseName)
    }

    @Test
    fun empate_de_data_ordena_por_id() {
        val mesmoInstante = millis(day)
        val sessions = listOf(
            session(id = 1, startedAt = mesmoInstante, exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0)))),
            session(id = 2, startedAt = mesmoInstante, exercises = listOf(exercise("ex1", set(reps = 10, weight = 42.5)))),
        )

        val history = buildExerciseHistory(sessions, "ex1")

        // Mesmo startedAt: ordem estável por sessionId decrescente (regra 12).
        assertEquals(listOf(2L, 1L), history.sessions.map { it.sessionId })
    }

    private fun session(
        id: Long,
        status: String = STATUS_COMPLETED,
        startedAt: Long = millis(day),
        modality: String = "strength",
        exercises: List<SessionExerciseWithSets> = emptyList(),
    ) = SessionWithExercises(
        session = WorkoutSessionEntity(
            id = id,
            status = status,
            startedAt = startedAt,
            totalDurationSeconds = 60L,
            modality = modality,
        ),
        exercises = exercises,
    )

    private fun exercise(
        exerciseId: String,
        vararg sets: SetLogEntity,
        name: String = exerciseId,
    ) = SessionExerciseWithSets(
        exercise = SessionExerciseEntity(
            id = 1,
            sessionId = 1,
            exerciseId = exerciseId,
            exerciseName = name,
        ),
        sets = sets.mapIndexed { index, set -> set.copy(setNumber = index + 1) },
    )

    private fun set(
        reps: Int?,
        weight: Double?,
        completed: Boolean = true,
        isWarmup: Boolean = false,
    ) = SetLogEntity(
        id = 0L,
        exerciseLogId = 1L,
        setNumber = 1,
        reps = reps,
        weightKg = weight,
        isCompleted = completed,
        isWarmup = isWarmup,
    )

    private fun millis(date: LocalDate): Long =
        date.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private companion object {
        const val EPSILON = 0.0001
    }
}
