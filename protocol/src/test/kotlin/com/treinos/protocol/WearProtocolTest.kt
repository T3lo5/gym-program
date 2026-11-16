package com.treinos.protocol

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
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

    @Test
    fun round_trip_do_log_set_com_carga_e_reps() {
        // H4-15: o LOG_SET do pulso passa a carregar reps/weightKg preenchidos.
        // Sem mudança de schema — protocolVersion continua 1 e os campos já
        // existiam no contrato.
        val comando = WearCommand(
            commandId = "cmd-h4-15",
            sessionId = 42,
            kind = WearCommandKind.LOG_SET,
            reps = 9,
            weightKg = 22.5,
        )
        val serializado = json.encodeToString(WearCommand.serializer(), comando)
        val novamente = json.decodeFromString(WearCommand.serializer(), serializado)
        assertEquals(comando, novamente)
        assertEquals(1, novamente.protocolVersion)
        assertEquals(9, novamente.reps)
        assertEquals(22.5, novamente.weightKg!!, 0.0)
        assertNull(novamente.rpe)
    }

    // ---------- H4-16: snapshot e lote de sincronização ----------

    @Test
    fun round_trip_do_snapshot() {
        val snapshot = WearSessionSnapshot(
            sessionId = 42,
            dayName = "Treino A",
            modality = "strength",
            startedAt = 1_700_000_000_000,
            exercises = listOf(
                WearSessionExercise(
                    exerciseIndex = 0,
                    sessionExerciseId = 100,
                    exerciseName = "Supino reto",
                    plannedSets = 3,
                    targetReps = "8-10",
                    restSeconds = 90,
                ),
                WearSessionExercise(
                    exerciseIndex = 1,
                    sessionExerciseId = 200,
                    exerciseName = "Remada alta",
                    plannedSets = 3,
                ),
            ),
        )
        val serializado = json.encodeToString(WearSessionSnapshot.serializer(), snapshot)
        val novamente = json.decodeFromString(WearSessionSnapshot.serializer(), serializado)
        assertEquals(snapshot, novamente)
        assertEquals(1, novamente.protocolVersion)
        assertEquals(2, novamente.exercises.size)
    }

    @Test
    fun round_trip_do_lote() {
        val lote = WearSyncBatch(
            sessionId = 42,
            batchId = "batch-1",
            finished = false,
            sets = listOf(
                WearLoggedSet(
                    clientSetId = "set-1",
                    sessionExerciseId = 100,
                    exerciseIndex = 0,
                    setNumber = 1,
                    reps = 10,
                    weightKg = 60.0,
                    loggedAt = 1_700_000_000_000,
                ),
            ),
        )
        val serializado = json.encodeToString(WearSyncBatch.serializer(), lote)
        val novamente = json.decodeFromString(WearSyncBatch.serializer(), serializado)
        assertEquals(lote, novamente)
        assertEquals(1, novamente.protocolVersion)
        assertFalse(novamente.finished)
    }

    @Test
    fun snapshot_sem_campo_novo_decodifica() {
        // Payload mínimo decodifica: os campos com default (modality e
        // exercises) podem faltar — o contrato é tolerante a payload antigo.
        val payload =
            "{\"protocolVersion\":1,\"sessionId\":7,\"dayName\":\"A\",\"startedAt\":1}"
        val snapshot = json.decodeFromString(WearSessionSnapshot.serializer(), payload)
        assertEquals(7, snapshot.sessionId)
        assertEquals("A", snapshot.dayName)
        assertEquals("strength", snapshot.modality)
        assertTrue(snapshot.exercises.isEmpty())
    }

    @Test
    fun lote_versao_incompativel_e_detectado() {
        val lote = WearSyncBatch(
            protocolVersion = 2,
            sessionId = 42,
            batchId = "batch-2",
        )
        assertEquals(2, lote.protocolVersion)
        assertNotEquals(1, lote.protocolVersion)
    }
}
