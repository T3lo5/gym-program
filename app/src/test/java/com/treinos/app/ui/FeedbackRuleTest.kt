package com.treinos.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedbackRuleTest {

    @Test
    fun `ringer_silent_sem_som`() {
        val action = restFeedbackForRinger(RINGER_MODE_SILENT)
        assertEquals(RestFeedbackAction(vibrate = true, sound = false), action)
    }

    @Test
    fun `ringer_vibrate_so_vibracao`() {
        val action = restFeedbackForRinger(RINGER_MODE_VIBRATE)
        assertEquals(RestFeedbackAction(vibrate = true, sound = false), action)
    }

    @Test
    fun `ringer_normal_som_e_vibracao`() {
        val action = restFeedbackForRinger(RINGER_MODE_NORMAL)
        assertEquals(RestFeedbackAction(vibrate = true, sound = true), action)
    }

    @Test
    fun `ringer_desconhecido_tratado_como_normal`() {
        val action = restFeedbackForRinger(99)
        assertEquals(RestFeedbackAction(vibrate = true, sound = true), action)
    }
}
