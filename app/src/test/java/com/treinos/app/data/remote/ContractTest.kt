package com.treinos.app.data.remote

import com.treinos.app.data.backup.BackupFile
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Teste de contrato: valida que o fixture `contracts/generate_response.json`
 * decodifica corretamente em `GeneratePlanResponse` do app, com `ignoreUnknownKeys`.
 *
 * O mesmo arquivo é validado pelo backend em `back/tests/test_contract.py`.
 */
class ContractTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun loadFixture(): String {
        // Caminho relativo ao módulo app: ../contracts/generate_response.json
        val file = File("../contracts/generate_response.json")
        assertTrue("Fixture de contrato não encontrado em ${file.absolutePath}", file.exists())
        return file.readText()
    }

    @Test
    fun decodificaFixtureDeContrato() {
        val jsonText = loadFixture()
        val response = json.decodeFromString(GeneratePlanResponse.serializer(), jsonText)

        assertNotNull(response)
        assertEquals(42, response.planId)
        assertEquals("contrato", response.modelUsed)
        assertEquals("local", response.source)
        assertEquals(1200, response.generationTimeMs)
        assertEquals("Full Body A", response.workout.name)
        assertEquals("hypertrophy", response.workout.goal)
        assertEquals(2, response.workout.daysPerWeek)
        assertEquals(1, response.workout.days.size)
        assertEquals("Treino A", response.workout.days.first().name)
        assertEquals(1, response.workout.days.first().exercises.size)
        assertEquals("Barbell_Bench_Press", response.workout.days.first().exercises.first().exerciseId)
        assertEquals(90, response.workout.days.first().exercises.first().restSeconds)
    }

    @Test
    fun fixtureTemCamposObrigatorios() {
        val jsonText = loadFixture()
        val response = json.decodeFromString(GeneratePlanResponse.serializer(), jsonText)

        // Campos obrigatórios de GeneratePlanResponse
        assertNotNull(response.workout)
        assertFalse(response.nextReviewAt.isBlank())
        assertTrue(response.reviewAfterWeeks > 0)

        // Campos obrigatórios do workout
        assertFalse(response.workout.name.isBlank())
        assertFalse(response.workout.goal.isBlank())
        assertTrue(response.workout.daysPerWeek > 0)
        assertFalse(response.workout.days.isEmpty())

        // Campos obrigatórios do dia
        val day = response.workout.days.first()
        assertFalse(day.name.isBlank())
        assertFalse(day.exercises.isEmpty())

        // Campos obrigatórios do exercício
        val exercise = day.exercises.first()
        assertFalse(exercise.exerciseId.isBlank())
        assertTrue(exercise.sets > 0)
        assertFalse(exercise.targetReps.isBlank())
        assertTrue(exercise.restSeconds > 0)
    }

    @Test
    fun ignoraCampoNovoDesconhecido() {
        val jsonText = loadFixture()
        val comChaveNova = jsonText.replace(
            "\"source\": \"local\"",
            "\"source\": \"local\", \"campoNovoDesconhecido\": 123"
        )
        val response = json.decodeFromString(GeneratePlanResponse.serializer(), comChaveNova)

        assertNotNull(response)
        assertEquals("local", response.source)
    }

    @Test
    fun request_sem_sexo_decode_ok() {
        val request = json.decodeFromString(
            GeneratePlanRequest.serializer(),
            """{"deviceId":"d1","goal":"hypertrophy","bodyWeightKg":82.5,"heightCm":178}""",
        )

        assertNull(request.sex)
        assertEquals(82.5, request.bodyWeightKg!!, 0.001)
        assertEquals(178, request.heightCm)

        // Request antigo (H1-5): sem perfil e sem sexo continua serializando sem a chave nova.
        val antigo = json.decodeFromString(GeneratePlanRequest.serializer(), """{"deviceId":"d1"}""")
        assertFalse(json.encodeToString(GeneratePlanRequest.serializer(), antigo).contains("\"sex\""))
    }

    @Test
    fun request_com_sexo_serializa() {
        val request = GeneratePlanRequest(
            deviceId = "d1",
            bodyWeightKg = 82.5,
            heightCm = 178,
            sex = "female",
        )

        val encoded = json.encodeToString(GeneratePlanRequest.serializer(), request)

        assertTrue(encoded.contains("\"sex\":\"female\""))
        assertEquals("female", json.decodeFromString(GeneratePlanRequest.serializer(), encoded).sex)

        // Os quatro valores do enum viajam e voltam intactos.
        for (value in listOf("male", "female", "other", "undisclosed")) {
            val text = json.encodeToString(GeneratePlanRequest.serializer(), request.copy(sex = value))
            assertTrue(text.contains("\"sex\":\"$value\""))
            assertEquals(value, json.decodeFromString(GeneratePlanRequest.serializer(), text).sex)
        }

        // Limpar a seleção volta a ser "campo ausente", não texto vazio.
        assertFalse(json.encodeToString(GeneratePlanRequest.serializer(), request.copy(sex = null)).contains("\"sex\""))
    }

    @Test
    fun request_com_environment_e_duracao_por_modalidade() {
        val request = GeneratePlanRequest(
            deviceId = "d1",
            environment = "gym",
            modalities = listOf(
                ModalityDto(
                    type = "strength",
                    sessionsPerWeek = 3,
                    sessionDurationMinutes = 60,
                ),
                ModalityDto(
                    type = "run",
                    sessionsPerWeek = 2,
                    sessionDurationMinutes = 40,
                ),
                ModalityDto(
                    type = "mobility",
                    sessionsPerWeek = 2,
                    sessionDurationMinutes = 10,
                ),
            ),
        )

        val encoded = json.encodeToString(GeneratePlanRequest.serializer(), request)

        assertTrue(encoded.contains("\"environment\":\"gym\""))
        assertTrue(encoded.contains("\"sessionDurationMinutes\":60"))
        assertTrue(encoded.contains("\"sessionDurationMinutes\":40"))
        assertTrue(encoded.contains("\"sessionDurationMinutes\":10"))

        val decoded = json.decodeFromString(GeneratePlanRequest.serializer(), encoded)
        assertEquals("gym", decoded.environment)
        assertEquals(3, decoded.modalities.size)
        assertEquals(60, decoded.modalities.first { it.type == "strength" }.sessionDurationMinutes)
        assertEquals(40, decoded.modalities.first { it.type == "run" }.sessionDurationMinutes)
        assertEquals(10, decoded.modalities.first { it.type == "mobility" }.sessionDurationMinutes)
    }

    @Test
    fun request_sem_environment_valor_padrao_nulo() {
        val request = GeneratePlanRequest(deviceId = "d1")
        val encoded = json.encodeToString(GeneratePlanRequest.serializer(), request)
        assertFalse(encoded.contains("environment"))

        val decoded = json.decodeFromString(GeneratePlanRequest.serializer(), encoded)
        assertNull(decoded.environment)
    }

    @Test
    fun modality_sem_duracao_valor_padrao_60() {
        val modality = ModalityDto(type = "strength", sessionsPerWeek = 3)
        val encoded = json.encodeToString(ModalityDto.serializer(), modality)
        assertTrue(encoded.contains("\"sessionDurationMinutes\":60"))

        val decoded = json.decodeFromString(ModalityDto.serializer(), encoded)
        assertEquals(60, decoded.sessionDurationMinutes)
    }

    @Test
    fun request_com_focus_muscles_round_trip() {
        // Fixture compartilhado com o backend (test_contract_v2.py): o mesmo
        // arquivo valida os dois lados do contrato (H3-16).
        val file = File("../contracts/generate_request_complementary.json")
        assertTrue("Fixture de contrato não encontrado em ${file.absolutePath}", file.exists())
        val request = json.decodeFromString(GeneratePlanRequest.serializer(), file.readText())

        assertEquals(listOf("abs"), request.focusMuscles)
        assertEquals(15, request.sessionDurationMinutes)
        assertEquals(2, request.daysPerWeek)
        assertEquals("bodyweight", request.environment)
        assertEquals(listOf("strength"), request.modalities.map { it.type })

        // Round-trip: codifica e decodifica de volta sem perder o foco.
        val encoded = json.encodeToString(GeneratePlanRequest.serializer(), request)
        assertTrue(encoded.contains("\"focusMuscles\":[\"abs\"]"))
        val decoded = json.decodeFromString(GeneratePlanRequest.serializer(), encoded)
        assertEquals(listOf("abs"), decoded.focusMuscles)
    }

    @Test
    fun request_sem_focus_muscles_omite_campo() {
        // Plano principal: sem foco o campo nem viaja (explicitNulls = false).
        val request = GeneratePlanRequest(deviceId = "d1")
        val encoded = json.encodeToString(GeneratePlanRequest.serializer(), request)
        assertFalse(encoded.contains("focusMuscles"))

        val decoded = json.decodeFromString(GeneratePlanRequest.serializer(), encoded)
        assertNull(decoded.focusMuscles)
    }
}
