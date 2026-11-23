package com.treinos.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.treinos.app.R
import com.treinos.app.ui.history.MuscleBar
import com.treinos.app.ui.history.ProgressPoint
import kotlin.math.max

/** Limite de pontos desenhados na linha (últimos N) para não custar por frame. */
internal const val MAX_DRAWN_POINTS = 30

private val ChartHeight = 120.dp
private val LineStrokeWidth = 2.dp
private val PointRadius = 3.dp
private val BarGap = 8.dp
private val BarCornerRadius = 4.dp
private val BarLabelHeight = 20.dp

/**
 * H4-2: gráfico de linha da evolução de um exercício, desenhado em Compose
 * `Canvas` (sem dependência nova).
 *
 * - Menos de 2 pontos não é tendência: mostra o estado "Poucos dados para um
 *   gráfico" e não desenha linha.
 * - Eixo Y começa em 0 e vai até o maior valor (mínimo visual de 1.0); eixo X
 *   distribui os pontos uniformemente.
 * - [contentDescription] é o resumo textual montado pelo chamador (primeiro
 *   valor, último valor, quantidade de pontos) — o gráfico é apoio, nunca o
 *   único canal.
 * - [valueLabel] formata os valores no chamador; o desenho não escreve texto
 *   no canvas nem formata por conta própria.
 */
@Composable
fun ProgressLineChart(
    points: List<ProgressPoint>,
    valueLabel: (Double) -> String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) {
        Text(
            text = stringResource(R.string.chart_few_data),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }
    val lineColor = MaterialTheme.colorScheme.primary
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    // Local com nome próprio: dentro de `semantics { }` o nome
    // `contentDescription` colide com a propriedade do receiver.
    val chartDescription = contentDescription
    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = chartDescription },
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(ChartHeight)) {
            val drawn = points.takeLast(MAX_DRAWN_POINTS)
            val maxValue = max(drawn.maxOf { it.value }, 1.0)
            val stepX = size.width / (drawn.size - 1)
            val centers = drawn.mapIndexed { index, point ->
                Offset(
                    x = stepX * index,
                    y = size.height * (1f - (point.value / maxValue).toFloat()),
                )
            }
            // Linha de base (y = 0 do eixo).
            drawLine(
                color = baselineColor,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx(),
            )
            val path = Path().apply {
                moveTo(centers.first().x, centers.first().y)
                centers.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = LineStrokeWidth.toPx()),
            )
            centers.forEach { center ->
                drawCircle(color = lineColor, radius = PointRadius.toPx(), center = center)
            }
        }
    }
}

/**
 * H4-2: gráfico de barras do volume por músculo, desenhado em Compose
 * `Canvas` (sem dependência nova).
 *
 * - Sem barras mostra o estado vazio da semana e não desenha nada.
 * - Altura da barra proporcional ao volume; eixo começa em 0 (mínimo visual
 *   de 1.0 para não dividir por zero).
 * - [contentDescription] carrega a descrição por músculo montada pelo
 *   chamador; os números continuam no ranking textual da tela.
 * - Rótulos dos músculos centralizados abaixo de cada barra no eixo X.
 */
@Composable
fun ProgressBarChart(
    bars: List<MuscleBar>,
    valueLabel: (Double) -> String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    if (bars.isEmpty()) {
        Text(
            text = stringResource(R.string.chart_week_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }
    val barColor = MaterialTheme.colorScheme.primary
    val chartDescription = contentDescription
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = chartDescription },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(ChartHeight)) {
                val maxValue = max(bars.maxOf { it.volumeKg }, 1.0)
                val gap = BarGap.toPx()
                val barWidth = (size.width - gap * (bars.size - 1)) / bars.size
                bars.forEachIndexed { index, bar ->
                    val fraction = (bar.volumeKg / maxValue).toFloat().coerceIn(0f, 1f)
                    val barHeight = size.height * fraction
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x = index * (barWidth + gap), y = size.height - barHeight),
                        size = Size(width = barWidth, height = barHeight),
                        cornerRadius = CornerRadius(BarCornerRadius.toPx()),
                    )
                }
            }
            // Rótulos dos músculos centralizados abaixo das barras
            Row(
                modifier = Modifier.fillMaxWidth().height(BarLabelHeight),
                horizontalArrangement = Arrangement.spacedBy(BarGap),
            ) {
                bars.forEach { bar ->
                    Text(
                        text = bar.muscle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = labelColor,
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
