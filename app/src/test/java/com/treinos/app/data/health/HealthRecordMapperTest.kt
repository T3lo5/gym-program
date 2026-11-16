package com.treinos.app.data.health

import com.treinos.app.data.local.WorkoutSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * H4-9: mapeamento puro sessão → rascunho do Health Connect e validação das
 * leituras de peso/passos. JVM pura: o mapper não importa o SDK.
 */
class HealthRecordMapperTest {

    private fun session(
        id: Long = 42L,
        status: String = "completed",
        startedAt: Long = 1_700_000_000_000L,
        endedAt: Long? = startedAt + 3_600_000L,
        modality: String = "strength",
        stroke: String? = null,
        title: String = "Treino A",
        notes: String = "boa sessão",
        sessionRpe: Double? = 8.0,
    ) = WorkoutSessionEntity(
        id = id,
        status = status,
        startedAt = startedAt,
        endedAt = endedAt,
        modality = modality,
        stroke = stroke,
        title = title,
        notes = notes,
        sessionRpe = sessionRpe,
    )

    @Test
    fun sessao_concluida_vira_draft() {
        val draft = toHealthSessionDraft(session())!!
        assertEquals(1_700_000_000_000L, draft.startedAtMillis)
        assertEquals(1_700_003_600_000L, draft.endedAtMillis)
        assertEquals("Treino A", draft.title)
        assertEquals("boa sessão", draft.notes)
        assertEquals(8.0f, draft.rpe)
        assertEquals(HC_EXERCISE_TYPE_STRENGTH_TRAINING, draft.exerciseType)
    }

    @Test
    fun sessao_em_andamento_nao_vira_draft() {
        assertNull(toHealthSessionDraft(session(status = "in_progress")))
    }

    @Test
    fun sessao_sem_ended_at_nao_vira_draft() {
        assertNull(toHealthSessionDraft(session(endedAt = null)))
    }

    @Test
    fun duracao_zero_nao_vira_draft() {
        val startedAt = 1_700_000_000_000L
        assertNull(toHealthSessionDraft(session(startedAt = startedAt, endedAt = startedAt)))
    }

    @Test
    fun client_record_id_e_estavel_por_sessao() {
        val primeiro = toHealthSessionDraft(session(id = 42L))!!
        val segundo = toHealthSessionDraft(session(id = 42L))!!
        assertEquals("treinos:session:42", primeiro.clientRecordId)
        assertEquals(primeiro.clientRecordId, segundo.clientRecordId)
    }

    @Test
    fun tipo_strength_e_strength_training() {
        assertEquals(70, healthExerciseTypeFor("strength", null))
    }

    @Test
    fun tipo_run_e_running() {
        assertEquals(56, healthExerciseTypeFor("run", null))
    }

    @Test
    fun tipo_swim_pool_e_open_water() {
        assertEquals(74, healthExerciseTypeFor("swim", "crawl"))
        assertEquals(73, healthExerciseTypeFor("swim", null))
    }

    @Test
    fun tipo_mobility_e_stretching() {
        assertEquals(71, healthExerciseTypeFor("mobility", null))
    }

    @Test
    fun tipo_desconhecido_e_other() {
        assertEquals(0, healthExerciseTypeFor("desconhecida", null))
    }

    @Test
    fun rpe_fora_da_faixa_vira_nulo() {
        assertNull(toHealthSessionDraft(session(sessionRpe = 0.5))!!.rpe)
        assertNull(toHealthSessionDraft(session(sessionRpe = 10.5))!!.rpe)
        assertNull(toHealthSessionDraft(session(sessionRpe = null))!!.rpe)
        assertEquals(10.0f, toHealthSessionDraft(session(sessionRpe = 10.0))!!.rpe)
        assertEquals(1.0f, toHealthSessionDraft(session(sessionRpe = 1.0))!!.rpe)
    }

    @Test
    fun peso_abaixo_de_30_e_descartado() {
        val samples = toWeightSamples(listOf(RawWeight(1_000L, 29.9)))
        assertEquals(emptyList<HealthWeightSample>(), samples)
    }

    @Test
    fun peso_acima_de_300_e_descartado() {
        val samples = toWeightSamples(listOf(RawWeight(1_000L, 300.1)))
        assertEquals(emptyList<HealthWeightSample>(), samples)
    }

    @Test
    fun passos_fora_da_faixa_descartados() {
        assertEquals(
            emptyList<HealthStepsSample>(),
            toStepsSamples(listOf(RawSteps(0L, 1_000L, 0L))),
        )
        assertEquals(
            emptyList<HealthStepsSample>(),
            toStepsSamples(listOf(RawSteps(0L, 1_000L, 1_000_001L))),
        )
        assertEquals(
            listOf(HealthStepsSample(0L, 1_000L, 1L)),
            toStepsSamples(listOf(RawSteps(0L, 1_000L, 1L))),
        )
    }

    @Test
    fun amostras_em_ordem_cronologica() {
        val pesos = toWeightSamples(
            listOf(
                RawWeight(3_000L, 80.0),
                RawWeight(1_000L, 81.0),
                RawWeight(2_000L, 20.0), // fora da faixa, sai da lista
            )
        )
        assertEquals(listOf(1_000L, 3_000L), pesos.map { it.timeMillis })

        val passos = toStepsSamples(
            listOf(
                RawSteps(3_000L, 4_000L, 100L),
                RawSteps(1_000L, 2_000L, 200L),
            )
        )
        assertEquals(listOf(1_000L, 3_000L), passos.map { it.startMillis })
    }
}
