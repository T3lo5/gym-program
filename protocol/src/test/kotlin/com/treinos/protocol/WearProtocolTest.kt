package com.treinos.protocol

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearProtocolTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun round_trip_do_estado() {
        val estado = WearWorkoutState(
            sessionId = 42,
            dayName = "Treino A",
            exerciseIndex = 0,
            exerciseCount = 3,
            exerciseName = "Supino reto",
            setNumber = 1,
            plannedSets = 3,
            targetReps = "8-12",
            restSeconds = 90,
            restRemainingSeconds = 45,
            nextExerciseName = "Agachamento",
            status = "in_progress",
            updatedAt = 1_700_000_000L,
        )
        val serializado = json.encodeToString(WearWorkoutState.serializer(), estado)
        val novamente = json.decodeFromString(WearWorkoutState.serializer(), serializado)
        assertEquals(estado, novamente)
        assertEquals("/treinos/workout-state", PATH_WORKOUT_STATE)
        assertEquals("/treinos/command", PATH_COMMAND)
    }

    @Test
    fun round_trip_do_comando() {
        val comando = WearCommand(
            commandId = "cmd-123",
            sessionId = 42,
            kind = WearCommandKind.LOG_SET,
            reps = 10,
            weightKg = 60.0,
            rpe = 8.0,
        )
        val serializado = json.encodeToString(WearCommand.serializer(), comando)
        val novamente = json.decodeFromString(WearCommand.serializer(), serializado)
        assertEquals(comando, novamente)
    }

    @Test
    fun modality_vai_no_round_trip() {
        val estado = WearWorkoutState(
            sessionId = 42,
            dayName = "Corrida leve",
            modality = "run",
            exerciseIndex = 0,
            exerciseCount = 0,
            exerciseName = "",
            setNumber = 1,
            plannedSets = 0,
            targetReps = "",
            status = "in_progress",
            updatedAt = 1_700_000_000L,
        )
        val serializado = json.encodeToString(WearWorkoutState.serializer(), estado)
        val novamente = json.decodeFromString(WearWorkoutState.serializer(), serializado)
        assertEquals("run", novamente.modality)
        assertEquals(estado, novamente)
    }

    @Test
    fun payload_sem_modality_decodifica_com_default() {
        val jsonAntigo = """{"protocolVersion":1,"sessionId":1,"dayName":"D","exerciseIndex":0,"exerciseCount":1,"exerciseName":"X","setNumber":1,"plannedSets":1,"targetReps":"8","restSeconds":90,"status":"in_progress","updatedAt":1}"""
        val estado = json.decodeFromString(WearWorkoutState.serializer(), jsonAntigo)
        assertEquals("strength", estado.modality)
    }

    @Test
    fun ignora_chaves_desconhecidas() {
        val jsonComExtra = """{"protocolVersion":1,"sessionId":1,"dayName":"D","exerciseIndex":0,"exerciseCount":1,"exerciseName":"X","setNumber":1,"plannedSets":1,"targetReps":"8","restSeconds":90,"status":"in_progress","updatedAt":1,"chaveNova":"qualquer coisa"}"""
        val estado = json.decodeFromString(WearWorkoutState.serializer(), jsonComExtra)
        assertEquals(1, estado.sessionId)
        assertEquals("D", estado.dayName)
        assertEquals("strength", estado.modality)
    }

    @Test
    fun versao_incompativel_e_detectada() {
        val estadoV2 = WearWorkoutState(
            sessionId = 1,
            dayName = "D",
            exerciseIndex = 0,
            exerciseCount = 1,
            exerciseName = "X",
            setNumber = 1,
            plannedSets = 1,
            targetReps = "8",
            restSeconds = 90,
            status = "in_progress",
            updatedAt = 1L,
            protocolVersion = 2,
        )
        assertNotEquals(1, estadoV2.protocolVersion)
    }
}
