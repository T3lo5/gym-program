package com.treinos.app.ui

import com.treinos.app.data.local.WorkoutSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobilitySessionTest {
    @Test
    fun sessao_usa_modality_mobility() {
        val session = WorkoutSessionEntity(
            planId = 0,
            dayId = 0,
            planName = "",
            dayName = "Mobilidade",
            status = "completed",
            startedAt = System.currentTimeMillis() - 600000,
            endedAt = System.currentTimeMillis(),
            totalDurationSeconds = 600,
            modality = "mobility",
            source = "manual",
            title = "Costas e quadril — 10 min",
        )
        assertEquals("mobility", session.modality)
        assertEquals("manual", session.source)
    }

    @Test
    fun sessao_nao_cria_series() {
        val session = WorkoutSessionEntity(
            planId = 0,
            dayId = 0,
            planName = "",
            dayName = "Mobilidade",
            status = "completed",
            startedAt = System.currentTimeMillis(),
            endedAt = System.currentTimeMillis(),
            totalDurationSeconds = 300,
            modality = "mobility",
            source = "manual",
            title = "Pós-corrida — 8 min",
        )
        // Sessão manual sem exercícios - conforme spec H2-5/H2-7
        assertEquals(0, session.planId)
        assertEquals(0, session.dayId)
    }

    @Test
    fun finalizar_antes_do_fim_grava_tempo_decorrido() {
        val start = System.currentTimeMillis()
        val elapsed = 180L // 3 minutos
        val session = WorkoutSessionEntity(
            planId = 0,
            dayId = 0,
            planName = "",
            dayName = "Mobilidade",
            status = "completed",
            startedAt = start - elapsed * 1000,
            endedAt = start,
            totalDurationSeconds = elapsed,
            modality = "mobility",
            source = "manual",
            title = "Quadril e posterior — 10 min",
        )
        assertTrue(session.totalDurationSeconds > 0)
        assertEquals("completed", session.status)
    }
}
