package com.treinos.app.data.remote

import com.treinos.app.data.backup.BackupFile
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
}
