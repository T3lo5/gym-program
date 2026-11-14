package com.treinos.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.treinos.app.R
import com.treinos.app.ui.MainActivity

/**
 * Decisão da política de notificação do descanso (H3-58), calculada a partir
 * de um snapshot do estado do descanso e do app.
 */
sealed interface RestNotificationDecision {
    /** Nada a fazer (sem permissão, por exemplo). */
    object Nothing : RestNotificationDecision

    /** Posta/atualiza a notificação com o restante informado. */
    data class ShowRemaining(val remainingSeconds: Int) : RestNotificationDecision

    /** Posta/atualiza a notificação como "Descanso concluído". */
    object ShowFinished : RestNotificationDecision

    /** Cancela a notificação; sem nada postado, é um no-op. */
    object Cancel : RestNotificationDecision
}

/**
 * Política pura da notificação de descanso (H3-58).
 *
 * Regras da spec:
 * - Posta/atualiza somente com descanso ativo (rodando, não pausado) + app em
 *   segundo plano + notificações habilitadas. O card "concluído" ainda conta
 *   como descanso em curso: entrar em background nesse estado posta "Descanso
 *   concluído" com o estado atual.
 * - Não há tick por segundo: o chamador avalia a política só em transições
 *   (ir/voltar do background, zerar, pular, pausar, retomar) — a deduplicação
 *   de postagens idênticas fica no [RestNotifier].
 * - Voltar ao primeiro plano, pausar ou limpar o descanso cancela.
 * - Sem permissão, nada acontece (nem aviso extra).
 */
object RestNotifierPolicy {

    fun decide(
        remainingSeconds: Int?,
        paused: Boolean,
        inBackground: Boolean,
        notificationsEnabled: Boolean,
    ): RestNotificationDecision = when {
        !notificationsEnabled -> RestNotificationDecision.Nothing
        !inBackground -> RestNotificationDecision.Cancel
        paused -> RestNotificationDecision.Cancel
        remainingSeconds == null -> RestNotificationDecision.Cancel
        remainingSeconds <= 0 -> RestNotificationDecision.ShowFinished
        else -> RestNotificationDecision.ShowRemaining(remainingSeconds)
    }
}

/** Id fixo da notificação de descanso: uma única notificação, atualizada in place. */
const val REST_NOTIFICATION_ID = 42

/**
 * Posta/atualiza/cancela a notificação do descanso (H3-58).
 *
 * Sem foreground service e sem AlarmManager: a notificação existe enquanto o
 * processo vivo mantém o descanso (mesma fronteira da H3-26). Se o processo
 * morre, a notificação postada some junto com o resto — nada novo é criado.
 */
class RestNotifier(context: Context) {

    private val appContext = context.applicationContext

    /** Última postagem, para deduplicar: `null` = nada postado. */
    private var postedRemainingSeconds: Int? = null
    private var postedFinished = false

    /** Cria o canal `rest_timer` (idempotente); chamado no startup do processo. */
    fun createChannel() {
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.rest_notification_title),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
    }

    /** Notificações habilitadas: permissão (API 33+) e toggle do usuário. */
    fun notificationsEnabled(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }

    /** Aplica a decisão da política. */
    fun apply(decision: RestNotificationDecision) {
        when (decision) {
            is RestNotificationDecision.ShowRemaining ->
                post(remainingSeconds = decision.remainingSeconds, finished = false)
            is RestNotificationDecision.ShowFinished ->
                post(remainingSeconds = 0, finished = true)
            is RestNotificationDecision.Cancel -> cancel()
            is RestNotificationDecision.Nothing -> Unit
        }
    }

    /** Cancela a notificação; sem nada postado, é um no-op (idempotente). */
    fun cancel() {
        if (postedRemainingSeconds == null && !postedFinished) return
        NotificationManagerCompat.from(appContext).cancel(REST_NOTIFICATION_ID)
        postedRemainingSeconds = null
        postedFinished = false
    }

    private fun post(remainingSeconds: Int, finished: Boolean) {
        if (finished) {
            if (postedFinished) return
        } else if (!postedFinished && postedRemainingSeconds == remainingSeconds) {
            return
        }
        if (!notificationsEnabled()) return

        val text = if (finished) {
            appContext.getString(R.string.rest_notification_finished)
        } else {
            appContext.getString(R.string.rest_notification_running, remainingSeconds)
        }
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_rest)
            .setContentTitle(appContext.getString(R.string.rest_notification_title))
            .setContentText(text)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
        if (!finished) {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    null,
                    appContext.getString(R.string.rest_notification_skip),
                    skipIntent(),
                ).build(),
            )
        }
        try {
            NotificationManagerCompat.from(appContext).notify(REST_NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Permissão revogada entre a checagem e a postagem: nada a fazer.
            return
        }
        postedFinished = finished
        postedRemainingSeconds = if (finished) null else remainingSeconds
    }

    /** Tocar na notificação abre o app; ao voltar, o VM cancela a notificação. */
    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        appContext,
        REQUEST_CODE_OPEN,
        Intent(appContext, MainActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            ),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** "Pular descanso": mesmo fluxo do botão da tela, via extra na MainActivity. */
    private fun skipIntent(): PendingIntent = PendingIntent.getActivity(
        appContext,
        REQUEST_CODE_SKIP,
        Intent(appContext, MainActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            .putExtra(MainActivity.EXTRA_SKIP_REST, true),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val CHANNEL_ID = "rest_timer"
        const val REQUEST_CODE_OPEN = 1
        const val REQUEST_CODE_SKIP = 2
    }
}
