package com.treinos.app.ui.measurements

import com.treinos.app.data.local.BodyMeasurementEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * H4-8: agregações puras da tendência de peso — reuso do tipo `ProgressPoint`
 * da H4-2; a lista da tela é desc, o gráfico é asc (regra 4) e menos de dois
 * pontos com peso não é tendência (regra 6).
 */
class MeasurementTrendTest {

    private val zone = ZoneId.systemDefault()

    private fun millis(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun medida(
        id: Long,
        date: LocalDate,
        weightKg: Double? = null,
        waistCm: Double? = null,
    ): BodyMeasurementEntity = BodyMeasurementEntity(
        id = id,
        measuredAt = millis(date),
        weightKg = weightKg,
        waistCm = waistCm,
    )

    @Test
    fun pontos_em_ordem_cronologica() {
        // A lista chega em desc (ordem de exibição); o gráfico pede asc.
        val measurements = listOf(
            medida(3, LocalDate.of(2026, 11, 10), weightKg = 81.5),
            medida(2, LocalDate.of(2026, 11, 5), weightKg = 82.0),
            medida(1, LocalDate.of(2026, 11, 1), weightKg = 82.4),
        )
        val points = weightTrendPoints(measurements)
        assertEquals(listOf(1L, 2L, 3L), points.map { it.sessionId })
        assertEquals(listOf(82.4, 82.0, 81.5), points.map { it.value })
    }

    @Test
    fun rotulo_data_curta() {
        val points = weightTrendPoints(listOf(medida(1, LocalDate.of(2026, 11, 10), weightKg = 82.4)))
        assertEquals("10/11", points.single().label)
    }

    @Test
    fun ignora_medida_sem_peso() {
        val measurements = listOf(
            medida(1, LocalDate.of(2026, 11, 1), weightKg = 82.4),
            // Só circunferência: não gera ponto no gráfico de peso.
            medida(2, LocalDate.of(2026, 11, 5), waistCm = 84.0),
            medida(3, LocalDate.of(2026, 11, 10), weightKg = 81.5),
        )
        val points = weightTrendPoints(measurements)
        assertEquals(listOf(1L, 3L), points.map { it.sessionId })
    }

    @Test
    fun periodo_inclusivo() {
        val measurements = listOf(
            medida(1, LocalDate.of(2026, 11, 1), weightKg = 82.4),
            medida(2, LocalDate.of(2026, 11, 5), weightKg = 82.0),
            medida(3, LocalDate.of(2026, 11, 10), weightKg = 81.5),
        )
        // Limites inclusivos nos dois extremos.
        assertEquals(
            listOf(2L),
            weightTrendPoints(measurements, from = LocalDate.of(2026, 11, 5), to = LocalDate.of(2026, 11, 5))
                .map { it.sessionId },
        )
        assertEquals(
            listOf(1L, 2L),
            weightTrendPoints(measurements, from = LocalDate.of(2026, 11, 1), to = LocalDate.of(2026, 11, 5))
                .map { it.sessionId },
        )
        // Sem período, tudo entra.
        assertEquals(3, weightTrendPoints(measurements).size)
    }

    @Test
    fun menos_de_dois_pontos_nao_gera_tendencia() {
        // Uma medida com peso: um ponto existe, mas não há tendência — o
        // gráfico da H4-2 mostra "Poucos dados" e o delta é nulo (regra 6).
        val single = listOf(medida(1, LocalDate.of(2026, 11, 10), weightKg = 82.4))
        assertEquals(1, weightTrendPoints(single).size)
        assertNull(trendDeltaKg(single))
    }

    @Test
    fun delta_ultimo_menos_primeiro() {
        val measurements = listOf(
            medida(1, LocalDate.of(2026, 11, 1), weightKg = 82.4),
            medida(2, LocalDate.of(2026, 11, 5), weightKg = 82.0),
            medida(3, LocalDate.of(2026, 11, 10), weightKg = 81.5),
        )
        assertEquals(-0.9, trendDeltaKg(measurements)!!, 0.0001)
        // A ordem de entrada não muda o delta (cronológica por dentro).
        assertEquals(-0.9, trendDeltaKg(measurements.reversed())!!, 0.0001)
    }

    @Test
    fun delta_nulo_com_menos_de_dois() {
        assertNull(trendDeltaKg(emptyList()))
        assertNull(trendDeltaKg(listOf(medida(1, LocalDate.of(2026, 11, 1), weightKg = 82.4))))
        // Duas medidas, mas só uma com peso: não é tendência de peso.
        assertNull(
            trendDeltaKg(
                listOf(
                    medida(1, LocalDate.of(2026, 11, 1), weightKg = 82.4),
                    medida(2, LocalDate.of(2026, 11, 5), waistCm = 84.0),
                ),
            ),
        )
    }

    @Test
    fun lista_vazia_sem_excecao() {
        assertTrue(weightTrendPoints(emptyList()).isEmpty())
        assertNull(trendDeltaKg(emptyList()))
        assertNull(latestMeasurement(emptyList()))
    }

    @Test
    fun latest_pega_a_mais_recente() {
        val measurements = listOf(
            medida(1, LocalDate.of(2026, 11, 1), weightKg = 82.4),
            medida(2, LocalDate.of(2026, 11, 10), weightKg = 81.5),
            medida(3, LocalDate.of(2026, 11, 5), weightKg = 82.0),
        )
        assertEquals(2L, latestMeasurement(measurements)?.id)
        // Empate de `measuredAt` (regra 5): qualquer uma das duas serve — a
        // função não lança e devolve uma delas.
        val tied = listOf(
            medida(1, LocalDate.of(2026, 11, 10), weightKg = 82.4),
            medida(2, LocalDate.of(2026, 11, 10), weightKg = 82.0),
        )
        assertTrue(latestMeasurement(tied)?.id in setOf(1L, 2L))
    }
}
