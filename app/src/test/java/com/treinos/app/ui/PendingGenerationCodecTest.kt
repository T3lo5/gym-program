package com.treinos.app.ui

import com.treinos.app.data.remote.GeneratePlanRequest
import com.treinos.app.data.remote.GeneratePlanResponse
import com.treinos.app.data.remote.ModalityDto
import com.treinos.app.data.remote.WorkoutDayDto
import com.treinos.app.data.remote.WorkoutExerciseDto
import com.treinos.app.data.remote.WorkoutPlanDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingGenerationCodecTest {

    private val request = GeneratePlanRequest(
        deviceId = "d1",
        goal = "hypertrophy",
        daysPerWeek = 3,
        availableEquipment = listOf("barbell", "dumbbell"),
        targetMuscles = listOf("chest"),
        currentLevel = "intermediate",
        sessionDurationMinutes = 60,
        reviewAfterWeeks = 4,
        bodyWeightKg = 80.0,
        heightCm = 180,
        sex = "M",
        modalities = listOf(
            ModalityDto(
                type = "strength",
                sessionsPerWeek = 3,
                goal = "hypertrophy",
                level = "intermediate",
                availableEquipment = listOf("barbell", "dumbbell"),
                targetMuscles = emptyList(),
                sessionDurationMinutes = 60,
            )
        ),
        environment = "gym",
        focusMuscles = null,
        language = "pt",
    )

    private val planDto = WorkoutPlanDto(
        name = "Semana híbrida",
        description = "",
        goal = "hypertrophy",
        daysPerWeek = 3,
        days = listOf(
            WorkoutDayDto(
                name = "Dia 1",
                dayOrder = 0,
                weekday = 1,
                modality = "strength",
                isRestDay = false,
                exercises = listOf(
                    WorkoutExerciseDto(
                        exerciseId = "Barbell_Bench_Press",
                        exerciseOrder = 0,
                        sets = 3,
                        targetReps = "8-12",
                        targetRpe = 8.0,
                        restSeconds = 90,
                        notes = "",
                        isSupersetWithPrevious = false,
                        metricType = "reps_weight",
                    )
                ),
            )
        ),
    )

    private val response = GeneratePlanResponse(
        workout = planDto,
        source = "ai",
        fallbackReason = "",
        nextReviewAt = "2026-11-17",
    )

    private val config = GenerateConfig(
        planKind = "main",
        goal = "hypertrophy",
        daysPerWeek = 3,
        level = "intermediate",
        equipment = listOf("barbell", "dumbbell"),
        muscles = listOf("chest"),
        sessionDurationMinutes = 60,
        reviewWeeks = 4,
        environment = "gym",
        modalities = listOf(
            ModalityDraft(type = "strength", included = true, sessionsPerWeek = 3),
            ModalityDraft(type = "run"),
            ModalityDraft(type = "mobility"),
        ),
    )

    @Test
    fun encode_decode_round_trip() {
        val envelope = PendingGeneration(
            jobId = "job-123",
            kind = "main",
            request = request,
            config = config,
        )
        val encoded = PendingGenerationCodec.encode(envelope)
        val decoded = PendingGenerationCodec.decode(encoded)

        assertEquals(envelope.jobId, decoded!!.jobId)
        assertEquals(envelope.kind, decoded!!.kind)
        assertEquals(envelope.request.deviceId, decoded!!.request.deviceId)
        assertEquals(envelope.request.goal, decoded!!.request.goal)
        assertEquals(envelope.request.daysPerWeek, decoded!!.request.daysPerWeek)
        assertEquals(envelope.config!!.planKind, decoded!!.config!!.planKind)
        assertEquals(envelope.config!!.goal, decoded!!.config!!.goal)
    }

    @Test
    fun entrada_vazia_devolve_null() {
        assertNull(PendingGenerationCodec.decode(""))
        assertNull(PendingGenerationCodec.decode("   "))
        assertNull(PendingGenerationCodec.decode(null))
    }

    @Test
    fun entrada_corrompida_devolve_null() {
        assertNull(PendingGenerationCodec.decode("{json quebrado"))
        assertNull(PendingGenerationCodec.decode("não é json"))
    }

    @Test
    fun config_null_permite_round_trip() {
        val envelope = PendingGeneration(
            jobId = "job-456",
            kind = "extra",
            request = request,
            config = null,
        )
        val encoded = PendingGenerationCodec.encode(envelope)
        val decoded = PendingGenerationCodec.decode(encoded)

        assertEquals(envelope.jobId, decoded!!.jobId)
        assertEquals(envelope.kind, decoded!!.kind)
        assertNull(decoded!!.config)
    }

    @Test
    fun ignore_unknown_keys_nao_quebra_leitura() {
        val envelope = PendingGeneration(
            jobId = "job-789",
            kind = "main",
            request = request,
            config = config,
        )
        val json = PendingGenerationCodec.encode(envelope)
        val comCampoExtra = json.substringBeforeLast("}") + ", \"campoNovo\": 123}"
        val decoded = PendingGenerationCodec.decode(comCampoExtra)

        assertEquals(envelope.jobId, decoded!!.jobId)
        assertEquals(envelope.kind, decoded!!.kind)
    }

    @Test
    fun retry_reutiliza_request_do_envelope() {
        val envelope = PendingGeneration(
            jobId = "job-retry",
            kind = "extra",
            request = request,
            config = config,
        )
        val decoded = PendingGenerationCodec.decode(PendingGenerationCodec.encode(envelope))!!

        assertEquals("d1", decoded.request.deviceId)
        assertEquals("hypertrophy", decoded.request.goal)
        assertEquals(3, decoded.request.daysPerWeek)
        assertEquals("gym", decoded.request.environment)
        assertEquals("extra", decoded.kind)
        assertEquals(config, decoded.config)
        assertTrue(decoded.config!!.modalities.size == 3)
    }
}
