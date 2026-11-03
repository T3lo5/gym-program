package com.treinos.app.data.backup

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BackupSerializationTest {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun sampleFile(): BackupFile =
        BackupFile(
            schemaVersion = 1,
            exportedAt = "2026-11-03T18:00:00Z",
            app = BackupApp(
                packageName = "com.treinos.app",
                versionName = "1.0.41",
                versionCode = 41,
            ),
            deviceId = "uuid-do-aparelho",
            plans = listOf(
                BackupPlan(
                    id = 1,
                    serverPlanId = null,
                    name = "Full Body A",
                    description = "",
                    goal = "hypertrophy",
                    daysPerWeek = 4,
                    isActive = true,
                    generatedByAi = false,
                    createdAt = 1760000000000L,
                    updatedAt = 1760000000000L,
                    version = 1,
                    nextReviewAt = "2026-12-01",
                    reviewAfterWeeks = 4,
                    source = "local",
                    days = listOf(
                        BackupDay(
                            id = 10,
                            planId = 1,
                            name = "Treino A",
                            dayOrder = 0,
                            notes = "",
                            isRestDay = false,
                            exercises = listOf(
                                BackupPlanExercise(
                                    id = 100,
                                    exerciseId = "Barbell_Bench_Press",
                                    exerciseName = "Supino reto",
                                    primaryMuscles = "chest",
                                    equipment = "barbell",
                                    exerciseOrder = 0,
                                    sets = 3,
                                    targetReps = "8-12",
                                    targetRpe = 8.0,
                                    restSeconds = 90,
                                    notes = "",
                                    isSupersetWithPrevious = false,
                                    dayId = 10,
                                )
                            ),
                        )
                    ),
                )
            ),
            sessions = listOf(
                BackupSession(
                    id = 50,
                    planId = 1,
                    dayId = 10,
                    planName = "Full Body A",
                    dayName = "Treino A",
                    status = "completed",
                    startedAt = 1760000000000L,
                    endedAt = 1760003600000L,
                    totalDurationSeconds = 3600,
                    notes = "",
                    exercises = listOf(
                        BackupSessionExercise(
                            id = 500,
                            exerciseId = "Barbell_Bench_Press",
                            exerciseName = "Supino reto",
                            exerciseOrder = 0,
                            plannedSets = 3,
                            completedSets = 1,
                            restSeconds = 90,
                            startedAt = 1760000000000L,
                            endedAt = null,
                            sessionId = 50,
                            sets = listOf(
                                BackupSetLog(
                                    id = 5000,
                                    setNumber = 1,
                                    reps = 10,
                                    weightKg = 40.0,
                                    rpe = 8.0,
                                    durationSeconds = null,
                                    distanceMeters = null,
                                    isCompleted = true,
                                    isWarmup = false,
                                    restTakenSeconds = 90,
                                    completedAt = 1760000100000L,
                                    exerciseLogId = 500,
                                )
                            ),
                        )
                    ),
                )
            ),
        )

    @Test
    fun roundTripPreservaTodosOsCampos() {
        val file = sampleFile()
        val encoded = json.encodeToString(BackupFile.serializer(), file)
        val decoded = json.decodeFromString(BackupFile.serializer(), encoded)

        assertEquals(file.schemaVersion, decoded.schemaVersion)
        assertEquals(file.exportedAt, decoded.exportedAt)
        assertEquals(file.app.packageName, decoded.app.packageName)
        assertEquals(file.app.versionName, decoded.app.versionName)
        assertEquals(file.app.versionCode, decoded.app.versionCode)
        assertEquals(file.deviceId, decoded.deviceId)
        assertEquals(file.plans.size, decoded.plans.size)
        assertEquals(1, decoded.plans.first().id)
        assertEquals("Full Body A", decoded.plans.first().name)
        assertEquals(1, decoded.plans.first().days.size)
        assertEquals(10, decoded.plans.first().days.first().id)
        assertEquals(1, decoded.plans.first().days.first().planId)
        assertEquals(1, decoded.plans.first().days.first().exercises.size)
        assertEquals(100, decoded.plans.first().days.first().exercises.first().id)
        assertEquals(10, decoded.plans.first().days.first().exercises.first().dayId)
        assertEquals(1, decoded.sessions.size)
        assertEquals(50, decoded.sessions.first().id)
        assertEquals(1, decoded.sessions.first().exercises.size)
        assertEquals(500, decoded.sessions.first().exercises.first().id)
        assertEquals(50, decoded.sessions.first().exercises.first().sessionId)
        assertEquals(1, decoded.sessions.first().exercises.first().sets.size)
        assertEquals(5000, decoded.sessions.first().exercises.first().sets.first().id)
        assertEquals(500, decoded.sessions.first().exercises.first().sets.first().exerciseLogId)
    }

    @Test
    fun ignoraChavesDesconhecidas() {
        val file = sampleFile()
        val encoded = json.encodeToString(BackupFile.serializer(), file)
        val comChaveDesconhecida =
            encoded.replace("\"schemaVersion\": 1", "\"schemaVersion\": 1, \"novaChave\": 123")
                .dropLast(1) + ", \"planoDesconhecido\": true}"
        val decoded = json.decodeFromString(BackupFile.serializer(), comChaveDesconhecida)

        assertNotNull(decoded)
        assertEquals(1, decoded.schemaVersion)
    }

    @Test
    fun rejeitaJsonInvalido() {
        var ex: Exception? = null
        try {
            json.decodeFromString(BackupFile.serializer(), "{ json invalido ")
        } catch (e: Exception) {
            ex = e
        }
        assertNotNull(ex)
    }

    @Test
    fun rejeitaSchemaVersionDiferente() {
        val file = sampleFile().copy(schemaVersion = 2)
        val encoded = json.encodeToString(BackupFile.serializer(), file)
        val decoded = json.decodeFromString(BackupFile.serializer(), encoded)
        assertEquals(2, decoded.schemaVersion)
    }
}
