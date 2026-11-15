package com.treinos.app.ui.history

import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionExerciseWithSets
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.WorkoutSessionEntity
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H4-2: agregações puras dos gráficos de progresso — só sessões de
 * musculação concluídas, séries válidas (fora de aquecimento) e valores
 * finitos entram no desenho.
 */
class ProgressChartsTest {

    private val day = LocalDate.of(2026, 11, 10)
    private val chestOnly = mapOf("ex1" to "chest")

    @Test
    fun serie_por_exercicio_ordena_cronologico() {
        val sessions = listOf(
            session(id = 3, startedAt = millis(LocalDate.of(2026, 11, 20)), exercises = listOf(exercise("ex1", set(reps = 5, weight = 45.0)))),
            session(id = 1, startedAt = millis(LocalDate.of(2026, 11, 10)), exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0)))),
            session(id = 2, startedAt = millis(LocalDate.of(2026, 11, 15)), exercises = listOf(exercise("ex1", set(reps = 8, weight = 42.5)))),
        )

        val series = exerciseProgressSeries(sessions, "ex1", ExerciseChartMetric.BEST_SET_KG)

        assertEquals(listOf(1L, 2L, 3L), series.points.map { it.sessionId })
        assertEquals(listOf(40.0, 42.5, 45.0), series.points.map { it.value })
        assertEquals(listOf("10/11", "15/11", "20/11"), series.points.map { it.label })
        assertTrue(series.points.zipWithNext().all { (a, b) -> a.startedAtMillis <= b.startedAtMillis })
        assertEquals(ExerciseChartMetric.BEST_SET_KG, series.metric)
        assertEquals("ex1", series.exerciseId)
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

        val series = exerciseProgressSeries(sessions, "ex1", ExerciseChartMetric.BEST_SET_KG)

        assertEquals(listOf(1L), series.points.map { it.sessionId })
        assertEquals(40.0, series.points.single().value, EPSILON)
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

        val series = exerciseProgressSeries(sessions, "ex1", ExerciseChartMetric.BEST_SET_KG)

        assertEquals(listOf(3L), series.points.map { it.sessionId })
        // Só a sessão de força concluída alimenta as barras (as de corrida e
        // mobilidade são ignoradas, mesmo contendo o exercício).
        val bars = muscleVolumeBars(sessions, chestOnly)
        assertEquals(400.0, bars.single().volumeKg, EPSILON)
        assertEquals(1, bars.single().sets)
        assertTrue(
            exercisesWithHistory(sessions).single().let { it.exerciseId == "ex1" && it.sessionCount == 1 },
        )
    }

    @Test
    fun ignora_warmup() {
        val sessions = listOf(
            session(
                id = 1,
                exercises = listOf(
                    exercise(
                        "ex1",
                        set(reps = 12, weight = 50.0, isWarmup = true),
                        set(reps = 10, weight = 40.0),
                        set(reps = 8, weight = 30.0, completed = false),
                    ),
                ),
            ),
        )

        val best = exerciseProgressSeries(sessions, "ex1", ExerciseChartMetric.BEST_SET_KG)
        val volume = exerciseProgressSeries(sessions, "ex1", ExerciseChartMetric.VOLUME_KG)

        assertEquals(40.0, best.points.single().value, EPSILON)
        assertEquals(400.0, volume.points.single().value, EPSILON)
    }

    @Test
    fun melhor_serie_pega_maior_carga() {
        val sessions = listOf(
            session(
                id = 1,
                exercises = listOf(
                    exercise(
                        "ex1",
                        set(reps = 10, weight = 40.0),
                        set(reps = 8, weight = 42.5),
                        set(reps = 5, weight = 45.0),
                    ),
                ),
            ),
        )

        val series = exerciseProgressSeries(sessions, "ex1", ExerciseChartMetric.BEST_SET_KG)

        assertEquals(45.0, series.points.single().value, EPSILON)
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
                        set(reps = 8, weight = 40.0),
                    ),
                ),
            ),
        )

        val series = exerciseProgressSeries(sessions, "ex1", ExerciseChartMetric.VOLUME_KG)

        assertEquals(720.0, series.points.single().value, EPSILON)
    }

    @Test
    fun serie_sem_carga_fora_do_melhor() {
        val sessions = listOf(
            session(
                id = 1,
                exercises = listOf(
                    exercise(
                        "ex1",
                        set(reps = 15, weight = null),
                        set(reps = 12, weight = null),
                    ),
                ),
            ),
        )

        val best = exerciseProgressSeries(sessions, "ex1", ExerciseChartMetric.BEST_SET_KG)
        val volume = exerciseProgressSeries(sessions, "ex1", ExerciseChartMetric.VOLUME_KG)

        // Sem carga não há melhor série: a sessão não gera ponto na linha de carga.
        assertTrue(best.points.isEmpty())
        // No volume, a sessão existe (regra 5) e as séries sem carga somam 0 (regra 4).
        assertEquals(1, volume.points.size)
        assertEquals(0.0, volume.points.single().value, EPSILON)
    }

    @Test
    fun menos_de_dois_pontos_nao_desenha() {
        val sessions = listOf(
            session(id = 1, exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0)))),
        )

        val series = exerciseProgressSeries(sessions, "ex1", ExerciseChartMetric.BEST_SET_KG)

        // A agregação devolve o ponto único; a regra de não desenhar (mostrar
        // "Poucos dados para um gráfico") é da UI e está em ProgressChartTest.
        assertEquals(1, series.points.size)
    }

    @Test
    fun exercicios_com_historico_ordena_por_contagem() {
        val sessions = listOf(
            session(id = 1, exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0)))),
            session(id = 2, exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0)))),
            session(id = 3, exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0)))),
            session(
                id = 4,
                exercises = listOf(
                    exercise("alfa", set(reps = 10, weight = 40.0), name = "alfa"),
                    exercise("delta", set(reps = 10, weight = 40.0), name = "Delta"),
                ),
            ),
            session(
                id = 5,
                exercises = listOf(
                    exercise("alfa", set(reps = 10, weight = 40.0), name = "alfa"),
                    exercise("delta", set(reps = 10, weight = 40.0), name = "Delta"),
                ),
            ),
            session(id = 6, exercises = listOf(exercise("ex2", set(reps = 10, weight = 40.0)))),
        )

        val options = exercisesWithHistory(sessions)

        assertEquals(
            listOf("ex1", "alfa", "delta", "ex2"),
            options.map { it.exerciseId },
        )
        assertEquals(3, options.first().sessionCount)
    }

    @Test
    fun nome_vazio_cai_para_o_id() {
        val sessions = listOf(
            session(id = 1, exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0), name = ""))),
        )

        val options = exercisesWithHistory(sessions)

        assertEquals("ex1", options.single().name)
    }

    @Test
    fun barras_por_musculo_ordenadas() {
        val sessions = listOf(
            session(
                id = 1,
                exercises = listOf(
                    exercise("ex1", set(reps = 10, weight = 40.0)), // Peito: 400
                    exercise("ex2", set(reps = 10, weight = 20.0)), // Costas: 200
                ),
            ),
        )
        val muscles = mapOf("ex1" to "chest", "ex2" to "back")

        val bars = muscleVolumeBars(sessions, muscles)

        assertEquals(listOf("Peito", "Costas"), bars.map { it.muscle })
        assertEquals(400.0, bars.first().volumeKg, EPSILON)
        assertEquals(1, bars.first().sets)
    }

    @Test
    fun barras_empatadas_ordenam_por_rotulo() {
        val sessions = listOf(
            session(
                id = 1,
                exercises = listOf(
                    exercise("ex1", set(reps = 10, weight = 40.0)), // Peito: 400
                    exercise("ex2", set(reps = 10, weight = 40.0)), // Costas: 400
                ),
            ),
        )
        val muscles = mapOf("ex1" to "chest", "ex2" to "back")

        val bars = muscleVolumeBars(sessions, muscles)

        assertEquals(listOf("Costas", "Peito"), bars.map { it.muscle })
    }

    @Test
    fun volume_dividido_entre_musculos() {
        val sessions = listOf(
            session(id = 1, exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0)))),
        )

        val bars = muscleVolumeBars(sessions, mapOf("ex1" to "chest,back"))

        assertEquals(200.0, bars.first { it.muscle == "Peito" }.volumeKg, EPSILON)
        assertEquals(200.0, bars.first { it.muscle == "Costas" }.volumeKg, EPSILON)
    }

    @Test
    fun sets_contados_por_musculo() {
        val sessions = listOf(
            session(
                id = 1,
                exercises = listOf(
                    exercise(
                        "ex1",
                        set(reps = 10, weight = 40.0),
                        set(reps = 8, weight = 40.0),
                    ),
                ),
            ),
        )

        val bars = muscleVolumeBars(sessions, mapOf("ex1" to "chest,back"))

        // A série inteira conta em cada músculo (não divide).
        assertEquals(2, bars.first { it.muscle == "Peito" }.sets)
        assertEquals(2, bars.first { it.muscle == "Costas" }.sets)
    }

    @Test
    fun exercicio_sem_musculo_vai_para_outros() {
        val sessions = listOf(
            session(id = 1, exercises = listOf(exercise("desconhecido", set(reps = 10, weight = 40.0)))),
        )

        val bars = muscleVolumeBars(sessions, emptyMap())

        assertEquals(OTHERS_MUSCLE_LABEL, bars.single().muscle)
        assertEquals(400.0, bars.single().volumeKg, EPSILON)
    }

    @Test
    fun periodo_inclusivo() {
        val before = session(
            id = 1,
            startedAt = millis(LocalDate.of(2026, 11, 9)),
            exercises = listOf(exercise("ex1", set(reps = 10, weight = 40.0))),
        )
        val onFrom = session(
            id = 2,
            startedAt = millis(LocalDate.of(2026, 11, 10)),
            exercises = listOf(exercise("ex1", set(reps = 10, weight = 41.0))),
        )
        val onTo = session(
            id = 3,
            startedAt = millis(LocalDate.of(2026, 11, 20)),
            exercises = listOf(exercise("ex1", set(reps = 10, weight = 42.0))),
        )
        val after = session(
            id = 4,
            startedAt = millis(LocalDate.of(2026, 11, 21)),
            exercises = listOf(exercise("ex1", set(reps = 10, weight = 43.0))),
        )

        val series = exerciseProgressSeries(
            sessions = listOf(before, onFrom, onTo, after),
            exerciseId = "ex1",
            metric = ExerciseChartMetric.BEST_SET_KG,
            from = LocalDate.of(2026, 11, 10),
            to = LocalDate.of(2026, 11, 20),
        )
        val bars = muscleVolumeBars(
            sessions = listOf(before, onFrom, onTo, after),
            muscleByExerciseId = chestOnly,
            from = LocalDate.of(2026, 11, 10),
            to = LocalDate.of(2026, 11, 20),
        )

        assertEquals(listOf(2L, 3L), series.points.map { it.sessionId })
        // Apenas as sessões dentro do período somam: 410 + 420 no músculo único.
        assertEquals(830.0, bars.single().volumeKg, EPSILON)
        assertEquals(2, bars.single().sets)
    }

    @Test
    fun valores_nao_finitos_descartados() {
        val sessions = listOf(
            session(
                id = 1,
                exercises = listOf(
                    exercise(
                        "ex1",
                        set(reps = 10, weight = Double.NaN),
                        set(reps = 10, weight = Double.POSITIVE_INFINITY),
                        set(reps = 6, weight = 40.0),
                    ),
                ),
            ),
        )

        val best = exerciseProgressSeries(sessions, "ex1", ExerciseChartMetric.BEST_SET_KG)
        val volume = exerciseProgressSeries(sessions, "ex1", ExerciseChartMetric.VOLUME_KG)
        val bars = muscleVolumeBars(sessions, chestOnly)

        assertEquals(40.0, best.points.single().value, EPSILON)
        assertEquals(240.0, volume.points.single().value, EPSILON)
        assertEquals(240.0, bars.single().volumeKg, EPSILON)
    }

    @Test
    fun listas_vazias_sem_excecao() {
        assertTrue(exerciseProgressSeries(emptyList(), "ex1", ExerciseChartMetric.BEST_SET_KG).points.isEmpty())
        assertTrue(muscleVolumeBars(emptyList(), chestOnly).isEmpty())
        assertTrue(exercisesWithHistory(emptyList()).isEmpty())
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
