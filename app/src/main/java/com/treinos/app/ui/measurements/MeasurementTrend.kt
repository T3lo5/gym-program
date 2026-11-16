package com.treinos.app.ui.measurements

import com.treinos.app.data.local.BodyMeasurementEntity
import com.treinos.app.ui.formatDateShort
import com.treinos.app.ui.history.ProgressPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * H4-8: tendência do peso corporal, derivada das medidas já gravadas no Room.
 * Funções puras — sem I/O, sem rede, sem Android — que reusam o tipo
 * [ProgressPoint] e o componente `ProgressLineChart` da H4-2; esta spec não
 * cria outro gráfico.
 */

private fun BodyMeasurementEntity.localDate(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(measuredAt).atZone(zone).toLocalDate()

/**
 * Pontos do gráfico de peso, em ordem cronológica crescente por `measuredAt`
 * (regra 4 — a lista da tela é que é exibida em desc).
 *
 * - Medidas sem `weightKg` (só circunferência/gordura) não geram ponto.
 * - `from`/`to` filtram por data local, inclusivos nos dois extremos; `null`
 *   não limita o período.
 * - O rótulo é a data curta "dd/MM" do dia da medição.
 */
fun weightTrendPoints(
    measurements: List<BodyMeasurementEntity>,
    from: LocalDate? = null,
    to: LocalDate? = null,
): List<ProgressPoint> {
    val zone = ZoneId.systemDefault()
    return measurements
        .asSequence()
        .filter { it.weightKg?.isFinite() == true }
        .filter { measurement ->
            val date = measurement.localDate(zone)
            (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to))
        }
        .sortedBy { it.measuredAt }
        .map { measurement ->
            ProgressPoint(
                sessionId = measurement.id,
                startedAtMillis = measurement.measuredAt,
                label = formatDateShort(measurement.localDate(zone)),
                value = measurement.weightKg!!,
            )
        }
        .toList()
}

/** Medição mais recente; `null` quando não há nenhuma. */
fun latestMeasurement(measurements: List<BodyMeasurementEntity>): BodyMeasurementEntity? =
    measurements.maxByOrNull { it.measuredAt }

/**
 * Delta do peso no período: último − primeiro entre as medidas com peso, em
 * ordem cronológica. Menos de duas medidas com peso não é tendência → `null`
 * (mesma régua da regra 6 do gráfico).
 */
fun trendDeltaKg(measurements: List<BodyMeasurementEntity>): Double? {
    val weights = measurements
        .filter { it.weightKg?.isFinite() == true }
        .sortedBy { it.measuredAt }
    if (weights.size < 2) return null
    return weights.last().weightKg!! - weights.first().weightKg!!
}
