package com.treinos.app.ui

import com.treinos.app.notify.RestNotificationDecision
import com.treinos.app.notify.RestNotifierPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Política pura da notificação de descanso (H3-58): posta só em background com
 * descanso ativo, vira "concluído" ao zerar, cancela ao voltar/pular e não faz
 * nada sem permissão.
 */
class RestNotifierPolicyTest {

    private fun decide(
        remaining: Int?,
        paused: Boolean = false,
        background: Boolean = true,
        enabled: Boolean = true,
    ) = RestNotifierPolicy.decide(
        remainingSeconds = remaining,
        paused = paused,
        inBackground = background,
        notificationsEnabled = enabled,
    )

    @Test
    fun `posta_so_em_background_com_descanso`() {
        // Em primeiro plano: Cancel (no-op sem notificação viva) — nada é postado.
        assertEquals(RestNotificationDecision.Cancel, decide(remaining = 90, background = false))
        // Em segundo plano com descanso ativo: mostra o restante atual.
        assertEquals(RestNotificationDecision.ShowRemaining(90), decide(remaining = 90))
        // Em segundo plano sem descanso: nada para mostrar.
        assertEquals(RestNotificationDecision.Cancel, decide(remaining = null))
    }

    @Test
    fun `zera_para_concluido_uma_vez`() {
        // Timer zerou em background: a notificação vira "Descanso concluído".
        assertEquals(RestNotificationDecision.ShowFinished, decide(remaining = 0))
        // A política é sem estado; postar "exatamente uma vez" é garantido pelo
        // tick do descanso (que retorna ao zerar) e pela deduplicação do notifier.
        assertEquals(RestNotificationDecision.ShowFinished, decide(remaining = 0))
    }

    @Test
    fun `cancela_ao_voltar_ou_pular`() {
        // Voltar ao primeiro plano cancela imediatamente.
        assertEquals(RestNotificationDecision.Cancel, decide(remaining = 30, background = false))
        // Pular/limpar o descanso cancela.
        assertEquals(RestNotificationDecision.Cancel, decide(remaining = null))
        // Pausar congela a contagem: a notificação some.
        assertEquals(RestNotificationDecision.Cancel, decide(remaining = 30, paused = true))
    }

    @Test
    fun `sem_permissao_nada`() {
        assertEquals(RestNotificationDecision.Nothing, decide(remaining = 90, enabled = false))
        assertEquals(RestNotificationDecision.Nothing, decide(remaining = 0, enabled = false))
        assertEquals(
            RestNotificationDecision.Nothing,
            decide(remaining = null, background = false, enabled = false),
        )
    }
}
