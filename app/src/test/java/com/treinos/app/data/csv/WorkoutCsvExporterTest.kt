package com.treinos.app.data.csv

import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionExerciseWithSets
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.WorkoutSessionEntity
import java.time.Instant
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * H4-7: export determinístico do formato "Treinos" — BOM, `;`, decimal com
 * vírgula, `\r\n`, uma linha por série.
 */
class WorkoutCsvExporterTest {

    private val originalTimeZone = TimeZone.getDefault()

    @Before
    fun fixTimeZone() {
        // Data/hora do export vêm do fuso do aparelho; fixa o fuso para o
        // teste ser determinístico em qualquer máquina.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    private fun session(
        startedAt: Long,
        id: Long = 1,
        title: String = "",
        dayName: String = "Treino A",
        planName: String = "Plano",
        notes: String = "",
    ) = WorkoutSessionEntity(
        id = id,
        planId = 1,
        dayId = 10,
        planName = planName,
        dayName = dayName,
        status = "completed",
        startedAt = startedAt,
        notes = notes,
        title = title,
    )

    private fun exercise(
        sessionId: Long,
        id: Long = 100,
        name: String = "Supino reto",
        order: Int = 0,
    ) = SessionExerciseEntity(
        id = id,
        sessionId = sessionId,
        exerciseId = "ex1",
        exerciseName = name,
        exerciseOrder = order,
    )

    private fun set(
        exerciseLogId: Long,
        id: Long = 1000,
        setNumber: Int = 1,
        reps: Int? = 10,
        weightKg: Double? = 40.0,
        rpe: Double? = null,
        isWarmup: Boolean = false,
        durationSeconds: Long? = null,
        distanceMeters: Double? = null,
    ) = SetLogEntity(
        id = id,
        exerciseLogId = exerciseLogId,
        setNumber = setNumber,
        reps = reps,
        weightKg = weightKg,
        rpe = rpe,
        isCompleted = true,
        isWarmup = isWarmup,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters,
    )

    private fun details(
        sessionEntity: WorkoutSessionEntity,
        vararg exercises: Pair<SessionExerciseEntity, List<SetLogEntity>>,
    ) = SessionWithExercises(
        session = sessionEntity,
        exercises = exercises.map { (exercise, sets) ->
            SessionExerciseWithSets(exercise = exercise, sets = sets)
        },
    )

    /** Instante do meio-dia UTC de 2026-11-10 (mesma data em UTC e em -03). */
    private val noonUtc = Instant.parse("2026-11-10T12:00:00Z").toEpochMilli()

    private fun dataLines(csv: String): List<String> =
        csv.removePrefix(CsvCodec.UTF8_BOM)
            .split("\r\n")
            .filter { it.isNotEmpty() }

    @Test
    fun cabecalho_exato() {
        val csv = WorkoutCsvExporter.exportSessionsCsv(emptyList())
        val lines = dataLines(csv)
        assertEquals(1, lines.size)
        assertEquals(
            "Data;Hora;Treino;Exercício;Série;Reps;Carga (kg);RPE;Aquecimento;Duração (s);Distância (m);Notas",
            lines.first(),
        )
        assertTrue("Export começa com BOM", csv.startsWith(CsvCodec.UTF8_BOM))
    }

    @Test
    fun uma_linha_por_serie() {
        val csv = WorkoutCsvExporter.exportSessionsCsv(
            listOf(
                details(
                    session(noonUtc),
                    exercise(sessionId = 1) to listOf(
                        set(exerciseLogId = 100, setNumber = 1),
                        set(exerciseLogId = 100, setNumber = 2, reps = 8, weightKg = 42.5),
                    ),
                ),
            ),
        )
        val lines = dataLines(csv)
        assertEquals(3, lines.size) // cabeçalho + 2 séries
        assertEquals(
            "2026-11-10;12:00;Treino A;Supino reto;1;10;40;;Não;;;",
            lines[1],
        )
        assertEquals(
            "2026-11-10;12:00;Treino A;Supino reto;2;8;42,5;;Não;;;",
            lines[2],
        )
    }

    @Test
    fun decimal_com_virgula() {
        val csv = WorkoutCsvExporter.exportSessionsCsv(
            listOf(
                details(
                    session(noonUtc),
                    exercise(sessionId = 1) to listOf(
                        set(exerciseLogId = 100, reps = 8, weightKg = 42.5, rpe = 8.5),
                    ),
                ),
            ),
        )
        val line = dataLines(csv)[1]
        assertTrue("Carga com vírgula", line.contains(";42,5;"))
        assertTrue("RPE com vírgula", line.contains(";8,5;"))
        assertTrue("Inteiro sem casa decimal", line.contains(";42,5;") && !line.contains("42,50"))
    }

    @Test
    fun campos_nulos_ficam_vazios() {
        val csv = WorkoutCsvExporter.exportSessionsCsv(
            listOf(
                details(
                    session(noonUtc),
                    exercise(sessionId = 1) to listOf(
                        set(exerciseLogId = 100, reps = null, weightKg = null, rpe = null),
                    ),
                ),
            ),
        )
        assertEquals(
            "2026-11-10;12:00;Treino A;Supino reto;1;;;;Não;;;",
            dataLines(csv)[1],
        )
    }

    @Test
    fun aquecimento_sim_nao() {
        val csv = WorkoutCsvExporter.exportSessionsCsv(
            listOf(
                details(
                    session(noonUtc),
                    exercise(sessionId = 1) to listOf(
                        set(exerciseLogId = 100, setNumber = 1, isWarmup = true),
                        set(exerciseLogId = 100, setNumber = 2, isWarmup = false),
                    ),
                ),
            ),
        )
        val lines = dataLines(csv)
        assertTrue(lines[1].endsWith(";Sim;;;"))
        assertTrue(lines[2].endsWith(";Não;;;"))
    }

    @Test
    fun ordena_por_data_e_serie() {
        val csv = WorkoutCsvExporter.exportSessionsCsv(
            listOf(
                // Sessão mais recente primeiro na entrada; e séries fora de ordem.
                details(
                    session(Instant.parse("2026-11-12T12:00:00Z").toEpochMilli(), id = 2),
                    exercise(sessionId = 2, id = 200) to listOf(
                        set(exerciseLogId = 200, id = 2002, setNumber = 2),
                        set(exerciseLogId = 200, id = 2001, setNumber = 1),
                    ),
                ),
                details(
                    session(Instant.parse("2026-11-10T12:00:00Z").toEpochMilli(), id = 1),
                    exercise(sessionId = 1, id = 100) to listOf(
                        set(exerciseLogId = 100, id = 1001, setNumber = 1),
                    ),
                ),
            ),
        )
        val lines = dataLines(csv)
        assertEquals("2026-11-10", lines[1].substringBefore(';'))
        assertEquals("2026-11-12", lines[2].substringBefore(';'))
        assertTrue("Série 1 antes da 2", lines[2].contains(";1;") && lines[3].contains(";2;"))
    }

    @Test
    fun sessao_sem_serie_nao_gera_linha() {
        val csv = WorkoutCsvExporter.exportSessionsCsv(
            listOf(
                details(session(noonUtc), exercise(sessionId = 1) to emptyList()),
            ),
        )
        assertEquals(1, dataLines(csv).size) // só o cabeçalho
    }

    @Test
    fun export_vazio_so_cabecalho() {
        val csv = WorkoutCsvExporter.exportSessionsCsv(emptyList())
        assertEquals(1, dataLines(csv).size)
        assertTrue(csv.startsWith(CsvCodec.UTF8_BOM))
    }

    @Test
    fun determinismo_mesma_entrada_mesmo_texto() {
        val input = listOf(
            details(
                session(noonUtc, notes = "Primeira série"),
                exercise(sessionId = 1) to listOf(
                    set(exerciseLogId = 100, setNumber = 1),
                    set(exerciseLogId = 100, setNumber = 2, reps = 8, weightKg = 42.5),
                ),
            ),
        )
        assertEquals(
            WorkoutCsvExporter.exportSessionsCsv(input),
            WorkoutCsvExporter.exportSessionsCsv(input),
        )
    }
}
