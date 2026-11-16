package com.treinos.app.data.csv

import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * H4-7: detecção de formato pelo cabeçalho, datas, números, agrupamento por
 * sessão e casos de borda do import Strong/Hevy.
 *
 * As linhas dos CSVs de teste têm sempre o mesmo número de campos do
 * cabeçalho (linhas malformadas são descartadas pelo [CsvCodec]).
 */
class StrongHevyCsvParserTest {

    private val originalTimeZone = TimeZone.getDefault()

    @Before
    fun fixTimeZone() {
        // Strong traz data/hora local; o import usa o fuso do aparelho.
        // Fuso fixo (UTC) para o teste ser determinístico.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    /** 8 colunas: Date, Workout Name, Exercise Name, Set Order, Weight, Reps, Notes, RPE. */
    private val strongHeader = "Date,Workout Name,Exercise Name,Set Order,Weight,Reps,Notes,RPE"

    /** 8 colunas: title, start_time, exercise_title, set_index, weight_kg, reps, distance_km, rpe. */
    private val hevyHeader =
        "title,start_time,exercise_title,set_index,weight_kg,reps,distance_km,rpe"

    @Test
    fun detecta_strong() {
        assertEquals(
            CsvImportFormat.STRONG,
            StrongHevyCsvParser.detectFormat(CsvCodec.parseCsv(strongHeader).first()),
        )
        val plan = StrongHevyCsvParser.parse(
            CsvCodec.parseCsv("$strongHeader\n2026-11-10,Treino A,Supino,1,40,10,,"),
        )
        assertEquals(CsvImportFormat.STRONG, plan.format)
        assertEquals(1, plan.sessions.size)
    }

    @Test
    fun detecta_hevy() {
        assertEquals(
            CsvImportFormat.HEVY,
            StrongHevyCsvParser.detectFormat(CsvCodec.parseCsv(hevyHeader).first()),
        )
        val plan = StrongHevyCsvParser.parse(
            CsvCodec.parseCsv("$hevyHeader\nTreino A,2026-11-10T07:32:00Z,Supino,1,40,10,,"),
        )
        assertEquals(CsvImportFormat.HEVY, plan.format)
        assertEquals(1, plan.sessions.size)
    }

    @Test
    fun rejeita_cabecalho_desconhecido() {
        assertNull(StrongHevyCsvParser.detectFormat(listOf("foo", "bar")))
        val failure = runCatching {
            StrongHevyCsvParser.parse(listOf(listOf("foo", "bar")))
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals(
            StrongHevyCsvParser.UNKNOWN_FORMAT_MESSAGE,
            failure?.message,
        )
    }

    @Test
    fun agrupa_linhas_na_mesma_sessao() {
        val plan = StrongHevyCsvParser.parse(
            CsvCodec.parseCsv(
                "$strongHeader\n" +
                    "2026-11-10,Treino A,Supino,1,40,10,,\n" +
                    "2026-11-10,Treino A,Supino,2,42.5,8,,\n" +
                    "2026-11-10,Treino A,Rosca direta,1,20,12,,\n" +
                    // Mesmo treino em outro dia NÃO agrupa com o de cima.
                    "2026-11-11,Treino A,Supino,1,40,10,,",
            ),
        )
        assertEquals(2, plan.sessions.size)
        val first = plan.sessions.first()
        assertEquals(2, first.exercises.size)
        assertEquals("Supino", first.exercises[0].exerciseName)
        assertEquals(2, first.exercises[0].sets.size)
        assertEquals(1, first.exercises[1].exerciseOrder)
        assertEquals("Rosca direta", first.exercises[1].exerciseName)
    }

    @Test
    fun ordena_series_por_set_number() {
        val plan = StrongHevyCsvParser.parse(
            CsvCodec.parseCsv(
                "$strongHeader\n" +
                    "2026-11-10,Treino A,Supino,3,45,8,,\n" +
                    "2026-11-10,Treino A,Supino,1,40,10,,\n" +
                    "2026-11-10,Treino A,Supino,2,42.5,9,,",
            ),
        )
        val sets = plan.sessions.single().exercises.single().sets
        assertEquals(listOf(1, 2, 3), sets.map { it.setNumber })
        assertEquals(40.0, sets[0].weightKg!!, 0.0001)
        assertEquals(45.0, sets[2].weightKg!!, 0.0001)
    }

    @Test
    fun data_strong_iso_e_brasileira() {
        val iso = StrongHevyCsvParser.parse(
            CsvCodec.parseCsv("$strongHeader\n2026-11-10,Treino A,Supino,1,40,10,,"),
        )
        val brasileira = StrongHevyCsvParser.parse(
            CsvCodec.parseCsv("$strongHeader\n10/11/2026,Treino A,Supino,1,40,10,,"),
        )
        val expected = Instant.parse("2026-11-10T00:00:00Z").toEpochMilli()
        assertEquals(expected, iso.sessions.single().startedAt)
        assertEquals(expected, brasileira.sessions.single().startedAt)

        // Com hora na mesma coluna; sem hora vira 00:00 local.
        val comHora = StrongHevyCsvParser.parse(
            CsvCodec.parseCsv("$strongHeader\n2026-11-10 07:32,Treino A,Supino,1,40,10,,"),
        )
        assertEquals(
            Instant.parse("2026-11-10T07:32:00Z").toEpochMilli(),
            comHora.sessions.single().startedAt,
        )
    }

    @Test
    fun start_time_hevy_com_offset() {
        val plan = StrongHevyCsvParser.parse(
            CsvCodec.parseCsv(
                "$hevyHeader\nTreino A,2026-11-10T07:32:00+00:00,Supino,1,40,10,,",
            ),
        )
        assertEquals(
            Instant.parse("2026-11-10T07:32:00Z").toEpochMilli(),
            plan.sessions.single().startedAt,
        )
    }

    @Test
    fun numeros_com_virgula_e_ponto() {
        // Delimitador `;` (o export do app) aceita decimal com vírgula solta;
        // o delimitador `,` aceita ponto.
        val plan = StrongHevyCsvParser.parse(
            CsvCodec.parseCsv(
                "Date;Workout Name;Exercise Name;Set Order;Weight;Reps;Notes;RPE\n" +
                    "2026-11-10;Treino A;Supino;1;42,5;10;;\n" +
                    "2026-11-10;Treino A;Supino;2;42.5;8;;",
            ),
        )
        assertEquals(CsvImportFormat.STRONG, plan.format)
        val sets = plan.sessions.single().exercises.single().sets
        assertEquals(42.5, sets[0].weightKg!!, 0.0001)
        assertEquals(42.5, sets[1].weightKg!!, 0.0001)
    }

    @Test
    fun linha_sem_exercicio_e_ignorada() {
        val plan = StrongHevyCsvParser.parse(
            CsvCodec.parseCsv(
                "$strongHeader\n" +
                    "2026-11-10,Treino A,,1,40,10,,\n" +
                    "2026-11-10,Treino A,Supino,1,40,10,,\n" +
                    "2026-11-10,Treino A,Supino,2,42.5,8,,",
            ),
        )
        assertEquals(1, plan.skippedRows)
        assertEquals(1, plan.sessions.size)
        assertEquals(2, plan.sessions.single().exercises.single().sets.size)
        assertTrue(plan.warnings.isNotEmpty())
    }

    @Test
    fun rpe_fora_da_faixa_vira_nulo() {
        val plan = StrongHevyCsvParser.parse(
            CsvCodec.parseCsv(
                "$strongHeader\n" +
                    "2026-11-10,Treino A,Supino,1,40,10,,11\n" +
                    "2026-11-10,Treino A,Supino,2,40,10,,0\n" +
                    "2026-11-10,Treino A,Supino,3,40,10,,8.5",
            ),
        )
        val sets = plan.sessions.single().exercises.single().sets
        assertNull(sets[0].rpe)
        assertNull(sets[1].rpe)
        assertEquals(8.5, sets[2].rpe!!, 0.0001)
    }

    @Test
    fun carga_zero_preservada() {
        val plan = StrongHevyCsvParser.parse(
            CsvCodec.parseCsv("$strongHeader\n2026-11-10,Treino A,Barra fixa,1,0,10,,"),
        )
        val set = plan.sessions.single().exercises.single().sets.single()
        assertEquals(0.0, set.weightKg!!, 0.0001)
    }

    @Test
    fun distance_km_vira_metros() {
        val plan = StrongHevyCsvParser.parse(
            CsvCodec.parseCsv(
                "$hevyHeader\nTreino A,2026-11-10T07:32:00Z,Corrida,1,0,0,1.5,",
            ),
        )
        val set = plan.sessions.single().exercises.single().sets.single()
        assertEquals(1500.0, set.distanceMeters!!, 0.0001)
    }

    @Test
    fun csv_vazio_gera_plano_vazio() {
        val plan = StrongHevyCsvParser.parse(CsvCodec.parseCsv(strongHeader))
        assertTrue(plan.sessions.isEmpty())
        assertEquals(0, plan.skippedRows)
    }

    @Test
    fun todas_invalidas_e_erro() {
        val plan = StrongHevyCsvParser.parse(
            CsvCodec.parseCsv(
                "$strongHeader\n" +
                    "2026-11-10,Treino A,,1,40,10,,\n" +
                    "2026-11-10,Treino A,Supino,abc,40,10,,",
            ),
        )
        // Sessões vazias com linhas ignoradas é a condição que a UI transforma
        // em erro (regra 4) — nunca pré-visualização vazia.
        assertTrue(plan.sessions.isEmpty())
        assertEquals(2, plan.skippedRows)
    }
}
