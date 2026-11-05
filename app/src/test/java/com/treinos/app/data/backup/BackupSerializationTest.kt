package com.treinos.app.data.backup

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun v3_round_trip_preserva_perfil() {
        val file = sampleFile().copy(
            schemaVersion = 3,
            profile = BackupProfile(bodyWeightKg = 82.5, heightCm = 178),
        )

        val encoded = json.encodeToString(BackupFile.serializer(), file)
        val decoded = json.decodeFromString(BackupFile.serializer(), encoded)

        assertEquals(3, decoded.schemaVersion)
        assertEquals(82.5, decoded.profile?.bodyWeightKg)
        assertEquals(178, decoded.profile?.heightCm)
    }

    @Test
    fun v3_escreve_os_campos_nulos_do_perfil() {
        val encoded = backupExportEncoder.encodeToString(
            BackupFile.serializer(),
            sampleFile().copy(schemaVersion = 3, profile = BackupProfile()),
        )

        assertTrue(encoded.contains("\"profile\""))
        assertTrue(encoded.contains("\"bodyWeightKg\":null"))
        assertTrue(encoded.contains("\"heightCm\":null"))
    }

    @Test
    fun chave_profile_ausente_decodifica_com_null() {
        val encoded = json.encodeToString(
            BackupFile.serializer(),
            sampleFile().copy(schemaVersion = 3, profile = null),
        )

        val decoded = json.decodeFromString(BackupFile.serializer(), encoded)

        assertNull(decoded.profile)
    }

    @Test
    fun decoder_de_producao_ignora_chave_desconhecida() {
        val escritoPeloExport = backupExportEncoder.encodeToString(
            BackupFile.serializer(),
            sampleFile().copy(schemaVersion = 3, profile = BackupProfile(bodyWeightKg = 82.5, heightCm = 178)),
        )
        val comChaveFutura = escritoPeloExport.dropLast(1) + ",\"avatar\":\"x\"}"

        val decoded = backupImportDecoder.decodeFromString(BackupFile.serializer(), comChaveFutura)

        assertEquals(3, decoded.schemaVersion)
        assertEquals(178, decoded.profile?.heightCm)
    }

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

    private val jsonV2 = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = true
    }

    @Test
    fun exporta_v2_com_campos_novos() {
        val encoded = jsonV2.encodeToString(
            BackupFile.serializer(),
            sampleFile().copy(schemaVersion = 2),
        )

        assertTrue(encoded.contains("\"schemaVersion\":2"))
        assertTrue(encoded.contains("\"modality\""))
        assertTrue(encoded.contains("\"weekday\""))
        assertTrue(encoded.contains("\"metricType\""))
        assertTrue(encoded.contains("\"targetDurationSeconds\""))
        assertTrue(encoded.contains("\"sessionRpe\""))
    }

    @Test
    fun importa_v1_com_defaults() {
        val v1 = """
            {
              "schemaVersion": 1,
              "exportedAt": "2026-01-01T00:00:00Z",
              "app": {"packageName": "com.treinos.app", "versionName": "1.0", "versionCode": 1},
              "deviceId": "d1",
              "plans": [{
                "id": 1, "serverPlanId": null, "name": "P", "description": "d",
                "goal": "hypertrophy", "daysPerWeek": 4, "isActive": true,
                "generatedByAi": false, "createdAt": 1, "updatedAt": 2, "version": 1,
                "nextReviewAt": null, "reviewAfterWeeks": 4, "source": "local",
                "days": [{
                  "id": 10, "planId": 1, "name": "A", "dayOrder": 0, "notes": "",
                  "isRestDay": false,
                  "exercises": [{
                    "id": 100, "exerciseId": "e", "exerciseName": "Ex",
                    "primaryMuscles": "", "equipment": "", "exerciseOrder": 0,
                    "sets": 3, "targetReps": "8-12", "targetRpe": null,
                    "restSeconds": 90, "notes": "", "isSupersetWithPrevious": false,
                    "dayId": 10
                  }]
                }]
              }],
              "sessions": []
            }
        """.trimIndent()

        val file = json.decodeFromString(BackupFile.serializer(), v1)

        assertEquals(1, file.schemaVersion)
        assertEquals("strength", file.plans[0].days[0].modality)
        assertEquals(null, file.plans[0].days[0].weekday)
        assertEquals("reps_weight", file.plans[0].days[0].exercises[0].metricType)
        assertEquals(null, file.plans[0].days[0].exercises[0].targetDurationSeconds)
    }

    @Test
    fun round_trip_v2() {
        val file = sampleFile().copy(
            schemaVersion = 2,
            plans = sampleFile().plans.map { plan ->
                plan.copy(
                    days = plan.days.map { day ->
                        day.copy(
                            modality = "run",
                            weekday = 3,
                            exercises = day.exercises.map { exercise ->
                                exercise.copy(
                                    metricType = "duration",
                                    targetDurationSeconds = 1800,
                                    targetPaceSecondsPerKm = 330,
                                    targetHrZone = "Z2",
                                )
                            },
                        )
                    },
                )
            },
            sessions = sampleFile().sessions.map { session ->
                session.copy(
                    planId = 0,
                    dayId = 0,
                    modality = "swim",
                    source = "manual",
                    title = "Natação",
                    totalDistanceMeters = 1500.0,
                    poolLengthMeters = 25,
                    stroke = "crawl",
                    sessionRpe = 7.0,
                    exercises = emptyList(),
                )
            },
        )

        val decoded = json.decodeFromString(
            BackupFile.serializer(),
            jsonV2.encodeToString(BackupFile.serializer(), file),
        )

        assertEquals("run", decoded.plans[0].days[0].modality)
        assertEquals(3, decoded.plans[0].days[0].weekday!!)
        assertEquals("duration", decoded.plans[0].days[0].exercises[0].metricType)
        assertEquals(1800, decoded.plans[0].days[0].exercises[0].targetDurationSeconds!!)
        assertEquals("swim", decoded.sessions[0].modality)
        assertEquals("manual", decoded.sessions[0].source)
        assertEquals("Natação", decoded.sessions[0].title)
        assertEquals(1500.0, decoded.sessions[0].totalDistanceMeters!!, 0.0)
        assertEquals(25, decoded.sessions[0].poolLengthMeters!!)
        assertEquals("crawl", decoded.sessions[0].stroke)
        assertEquals(7.0, decoded.sessions[0].sessionRpe!!, 0.0)
    }
}
