package com.treinos.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.treinos.app.R
import com.treinos.app.TreinosApplication
import com.treinos.app.ui.MainActivity
import java.time.LocalDate
import java.time.temporal.ChronoField
import kotlinx.coroutines.flow.first

/**
 * Widget "treino de hoje" (H4-14): um toque inicia o treino do dia ou retoma
 * o treino em andamento, direto da tela inicial.
 *
 * `provideGlance` lê o Room local (funciona em modo avião) e nunca lança:
 * sem plano, sem sessão ou falha de leitura caem em [WidgetContent.NoPlan]
 * (regra 6). A atualização por mudança de dados é feita pelo `AppViewModel`
 * (`inProgressSession`/`activePlans`); o refresh do launcher vem do receiver.
 */
object TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        val content = runCatching {
            val repository = (appContext as TreinosApplication).container.repository
            val sessions = repository.observeSessions().first()
            val plans = repository.observeActivePlans().first()
            val inProgress = sessions.firstOrNull { it.session.status == "in_progress" }
            val todayWeekday = LocalDate.now().get(ChronoField.DAY_OF_WEEK)
            widgetContent(inProgress, widgetPlan(plans), todayWeekday)
        }.getOrDefault(WidgetContent.NoPlan)
        val labels = resolveLabels(appContext, content)
        provideContent { WidgetBody(labels) }
    }

    /**
     * Rótulos resolvidos fora da composição (o texto acompanha o idioma
     * escolhido) e o intent de cada estado. `dayName` vazio cai no fallback
     * aqui — o modelo puro repassa como veio (regra 5) e o widget nunca
     * renderiza vazio.
     */
    private fun resolveLabels(context: Context, content: WidgetContent): WidgetLabels {
        val fallback = context.getString(R.string.widget_workout_fallback)
        return when (content) {
            is WidgetContent.Resume -> WidgetLabels(
                title = context.getString(R.string.widget_resume_label),
                subtitle = content.dayName.ifBlank { fallback },
                intent = Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_RESUME_SESSION, true),
            )
            is WidgetContent.Start -> WidgetLabels(
                title = context.getString(R.string.widget_start_today_label),
                subtitle = content.dayName.ifBlank { fallback },
                intent = Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_START_TODAY, true),
            )
            is WidgetContent.Next -> WidgetLabels(
                title = context.getString(R.string.widget_next_label, content.dayName.ifBlank { fallback }),
                subtitle = null,
                intent = Intent(context, MainActivity::class.java),
            )
            WidgetContent.Rest -> WidgetLabels(
                title = context.getString(R.string.widget_rest_label),
                subtitle = null,
                intent = Intent(context, MainActivity::class.java),
            )
            WidgetContent.NoPlan -> WidgetLabels(
                title = context.getString(R.string.widget_open_app_label),
                subtitle = null,
                intent = Intent(context, MainActivity::class.java),
            )
        }
    }

    private data class WidgetLabels(
        val title: String,
        val subtitle: String?,
        val intent: Intent,
    )

    @Composable
    private fun WidgetBody(labels: WidgetLabels) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(WidgetBackground))
                .padding(all = 12.dp)
                .clickable(actionStartActivity(labels.intent)),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = labels.title,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 2,
            )
            labels.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = TextStyle(color = ColorProvider(Color.White)),
                    maxLines = 1,
                )
            }
        }
    }

    private val WidgetBackground = Color(0xFF141414)
}

/** Receiver do launcher: `onUpdate` cobre o refresh pedido pelo sistema (regra 7). */
class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget
}
