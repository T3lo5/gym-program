package com.treinos.app.ui

import com.treinos.app.data.local.MANUAL_SESSION_SOURCE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityLogTest {

    private val now = 1_760_000_000_000L

    private fun input(
        modality: ActivityModality = ActivityModality.SWIM,
        title: String = "",
        startedAt: Long = now,
        durationMinutes: Int = 45,
        distanceMeters: Double? = null,
        poolLengthMeters: Int? = null,
        stroke: String? = null,
        rpe: Double? = null,
        notes: String = "",
    ) = ActivityLogInput(
        modality = modality,
        title = title,
        startedAt = startedAt,
        durationMinutes = durationMinutes,
        distanceMeters = distanceMeters,
        poolLengthMeters = poolLengthMeters,
        stroke = stroke,
        rpe = rpe,
        notes = notes,
    )

    @Test
    fun aceita_so_duracao() {
        val result = validateActivityLog(input(durationMinutes = 30), nowMillis = now)

        assertTrue(result.valid)
        assertEquals(emptyMap<ActivityLogField, String>(), result.errors)
        assertEquals("Natação", result.input?.title)
        assertNull(result.input?.distanceMeters)
    }

    @Test
    fun calcula_pace_por_100m() {
        // 45 min para 1.500 m = 3:00 por 100 m
        assertEquals(180L, paceSecondsPer100m(2_700L, 1_500.0))
        assertEquals("3:00/100m", formatPacePer100m(2_700L, 1_500.0))
    }

    @Test
    fun nao_calcula_pace_sem_distancia() {
        assertNull(paceSecondsPer100m(2_700L, null))
        assertNull(paceSecondsPer100m(2_700L, 0.0))
        assertNull(paceSecondsPer100m(0L, 1_500.0))
        assertNull(formatPacePer100m(2_700L, null))
    }

    @Test
    fun rejeita_duracao_zero() {
        val result = validateActivityLog(input(durationMinutes = 0), nowMillis = now)

        assertFalse(result.valid)
        assertTrue(result.errors.containsKey(ActivityLogField.Duration))
        assertNull(result.input)
    }

    @Test
    fun rejeita_duracao_acima_de_600() {
        assertTrue(validateActivityLog(input(durationMinutes = 600), nowMillis = now).valid)

        val result = validateActivityLog(input(durationMinutes = 601), nowMillis = now)
        assertFalse(result.valid)
        assertTrue(result.errors.containsKey(ActivityLogField.Duration))
    }

    @Test
    fun rejeita_rpe_fora_da_faixa() {
        assertTrue(
            validateActivityLog(input(rpe = 11.0), nowMillis = now)
                .errors.containsKey(ActivityLogField.Rpe)
        )
        assertTrue(validateActivityLog(input(rpe = 0.5), nowMillis = now).errors.containsKey(ActivityLogField.Rpe))
        assertTrue(validateActivityLog(input(rpe = 7.0), nowMillis = now).valid)
        assertTrue(validateActivityLog(input(rpe = 6.5), nowMillis = now).valid)
    }

    @Test
    fun rejeita_passo_de_rpe_invalido() {
        val result = validateActivityLog(input(rpe = 7.3), nowMillis = now)

        assertFalse(result.valid)
        assertEquals("Use passos de 0,5 no RPE.", result.errors[ActivityLogField.Rpe])
    }

    @Test
    fun rejeita_data_futura() {
        val result = validateActivityLog(input(startedAt = now + 10 * 60_000L), nowMillis = now)

        assertFalse(result.valid)
        assertTrue(result.errors.containsKey(ActivityLogField.Date))
    }

    @Test
    fun aceita_data_retroativa() {
        val ontem = now - 24 * 60 * 60_000L

        val result = validateActivityLog(input(startedAt = ontem), nowMillis = now)

        assertTrue(result.valid)
        assertEquals(ontem, result.input?.startedAt)
    }

    @Test
    fun titulo_vazio_usa_default() {
        assertEquals("Natação", validateActivityLog(input(), nowMillis = now).input?.title)
        assertEquals(
            "Corrida",
            validateActivityLog(input(modality = ActivityModality.RUN), nowMillis = now).input?.title
        )
        assertEquals(
            "Mobilidade",
            validateActivityLog(input(modality = ActivityModality.MOBILITY), nowMillis = now).input?.title
        )
        assertEquals(
            "Aula de hidro",
            validateActivityLog(input(title = "  Aula de hidro  "), nowMillis = now).input?.title
        )
    }

    @Test
    fun outra_modalidade_usa_atividade() {
        assertEquals(
            "Atividade",
            validateActivityLog(input(modality = ActivityModality.OTHER), nowMillis = now).input?.title
        )
    }

    @Test
    fun rejeita_notas_longas() {
        assertTrue(validateActivityLog(input(notes = "n".repeat(2000)), nowMillis = now).valid)

        val result = validateActivityLog(input(notes = "n".repeat(2001)), nowMillis = now)
        assertFalse(result.valid)
        assertTrue(result.errors.containsKey(ActivityLogField.Notes))
    }

    @Test
    fun rejeita_piscina_invalida() {
        val result = validateActivityLog(input(poolLengthMeters = 30), nowMillis = now)

        assertFalse(result.valid)
        assertTrue(result.errors.containsKey(ActivityLogField.PoolLength))
        assertTrue(validateActivityLog(input(poolLengthMeters = 25), nowMillis = now).valid)
        assertTrue(validateActivityLog(input(poolLengthMeters = 50), nowMillis = now).valid)
    }

    @Test
    fun rejeita_stroke_fora_da_lista() {
        val result = validateActivityLog(input(stroke = "borboleta!"), nowMillis = now)

        assertFalse(result.valid)
        assertTrue(result.errors.containsKey(ActivityLogField.Stroke))
        assertTrue(validateActivityLog(input(stroke = "borboleta"), nowMillis = now).valid)
    }

    @Test
    fun piscina_e_stroke_so_valem_para_natacao() {
        val result = validateActivityLog(
            input(modality = ActivityModality.RUN, poolLengthMeters = 25, stroke = "crawl"),
            nowMillis = now,
        )

        assertTrue(result.valid)
        assertNull(result.input?.poolLengthMeters)
        assertNull(result.input?.stroke)
    }

    @Test
    fun distancia_zero_vira_null() {
        val result = validateActivityLog(input(distanceMeters = 0.0), nowMillis = now)

        assertTrue(result.valid)
        assertNull(result.input?.distanceMeters)
    }

    @Test
    fun rejeita_distancia_negativa_e_acima_de_100km() {
        assertFalse(validateActivityLog(input(distanceMeters = -1.0), nowMillis = now).valid)
        assertFalse(validateActivityLog(input(distanceMeters = 100_001.0), nowMillis = now).valid)
        assertTrue(validateActivityLog(input(distanceMeters = 100_000.0), nowMillis = now).valid)
    }

    @Test
    fun registro_nasce_concluido_e_sem_exercicios() {
        val session = requireNotNull(
            validateActivityLog(
                input(title = "Natação", durationMinutes = 45, distanceMeters = 1_500.0, rpe = 7.0),
                nowMillis = now,
            ).input
        ).toManualSession()

        assertEquals(0L, session.id)
        assertEquals(0L, session.planId)
        assertEquals(0L, session.dayId)
        assertEquals("", session.planName)
        assertEquals(MANUAL_SESSION_SOURCE, session.source)
        assertEquals("swim", session.modality)
        assertEquals("completed", session.status)
        assertEquals("Natação", session.title)
        assertEquals("Natação", session.dayName)
        assertEquals(2_700L, session.totalDurationSeconds)
        assertEquals(now + 2_700_000L, session.endedAt)
        assertEquals(1_500.0, session.totalDistanceMeters!!, 0.0)
        assertEquals(7.0, session.sessionRpe!!, 0.0)
    }

    @Test
    fun sessao_salva_reabre_no_formulario() {
        val session = input(
            title = "Natação longa",
            durationMinutes = 50,
            distanceMeters = 2_000.0,
            poolLengthMeters = 25,
            stroke = "peito",
            rpe = 8.0,
            notes = "8 x 250 m",
        ).toManualSession(id = 50L)

        val draft = session.toActivityLogInput()

        assertEquals(ActivityModality.SWIM, draft.modality)
        assertEquals("Natação longa", draft.title)
        assertEquals(50, draft.durationMinutes)
        assertEquals(2_000.0, draft.distanceMeters!!, 0.0)
        assertEquals(25, draft.poolLengthMeters)
        assertEquals("peito", draft.stroke)
        assertEquals(8.0, draft.rpe!!, 0.0)
        assertEquals("8 x 250 m", draft.notes)
        assertEquals(session.startedAt, draft.startedAt)
    }

    @Test
    fun aceita_numero_com_separador_brasileiro() {
        assertEquals(1500.0, parseDraftNumber("1.500")!!, 0.0)
        assertEquals(1500.0, parseDraftNumber("1500")!!, 0.0)
        assertEquals(1500.0, parseDraftNumber("1 500")!!, 0.0)
        assertEquals(1_500.0, parseDraftNumber("1.500,0")!!, 0.0)
        assertEquals(7.5, parseDraftNumber("7,5")!!, 0.0)
        assertEquals(7.5, parseDraftNumber("7.5")!!, 0.0)
        assertNull(parseDraftNumber(""))
        assertNull(parseDraftNumber("abc"))
    }

    @Test
    fun draft_valida_texto_digitado() {
        val valido = validateActivityDraft(
            ActivityLogDraft(durationText = "45", distanceText = "1.500", rpeText = "7"),
            nowMillis = now,
        )
        assertTrue(valido.valid)
        assertEquals(1_500.0, valido.input?.distanceMeters!!, 0.0)
        assertEquals(7.0, valido.input?.rpe!!, 0.0)

        val digitado = validateActivityDraft(
            ActivityLogDraft(durationText = "quarenta e cinco"),
            nowMillis = now,
        )
        assertFalse(digitado.valid)
        assertEquals("Informe a duração em minutos.", digitado.errors[ActivityLogField.Duration])
    }

    @Test
    fun draft_rejeita_data_fora_do_formato() {
        val result = validateActivityDraft(
            ActivityLogDraft(durationText = "45", dateText = "2026-11-04"),
            nowMillis = now,
        )

        assertFalse(result.valid)
        assertEquals("Use o formato dd/mm/aaaa hh:mm.", result.errors[ActivityLogField.Date])
    }
}
