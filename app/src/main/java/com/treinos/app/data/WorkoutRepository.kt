package com.treinos.app.data

import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionExerciseWithSets
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.WorkoutDao
import com.treinos.app.data.local.WorkoutDayEntity
import com.treinos.app.data.local.WorkoutDayWithExercises
import com.treinos.app.data.local.WorkoutPlanEntity
import com.treinos.app.data.local.WorkoutPlanExerciseEntity
import com.treinos.app.data.local.WorkoutPlanWithDetails
import com.treinos.app.data.local.WorkoutSessionEntity
import com.treinos.app.data.prefs.AppPreferences
import com.treinos.app.data.remote.BackendClient
import com.treinos.app.data.remote.GeneratePlanRequest
import com.treinos.app.data.remote.GeneratePlanResponse
import com.treinos.app.data.remote.SyncDayDto
import com.treinos.app.data.remote.SyncPlanDto
import com.treinos.app.data.remote.SyncPlanExerciseDto
import com.treinos.app.data.remote.SyncDownloadDto
import com.treinos.app.data.remote.SyncDownloadDayDto
import com.treinos.app.data.remote.SyncDownloadPlanDto
import com.treinos.app.data.remote.SyncDownloadPlanExerciseDto
import com.treinos.app.data.remote.SyncDownloadSetLogDto
import com.treinos.app.data.remote.SyncDownloadSessionDto
import com.treinos.app.data.remote.SyncDownloadSessionExerciseDto
import com.treinos.app.data.remote.SyncRequestDto
import com.treinos.app.data.remote.SyncResponseDto
import com.treinos.app.data.remote.SyncSessionDto
import com.treinos.app.data.remote.SyncSessionExerciseDto
import com.treinos.app.data.remote.SyncSetLogDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Fonte única de dados do app.
 *
 * Tudo que o usuário anota durante o treino é gravado no Room (SQLite) na hora.
 * O backend só é populado quando [sync] é chamado explicitamente.
 */
class WorkoutRepository(
    private val dao: WorkoutDao,
    private val backendClient: BackendClient,
    private val preferences: AppPreferences,
) {

    fun observeActivePlan(): Flow<WorkoutPlanWithDetails?> = combine(
        dao.observeActivePlanEntity(),
        dao.observeAllDays(),
        dao.observeAllPlanExercises(),
    ) { plan, days, exercises ->
        plan?.let { buildPlanDetails(it, days, exercises) }
    }

    fun observeSessions(): Flow<List<SessionWithExercises>> = combine(
        dao.observeSessionEntities(),
        dao.observeAllSessionExercises(),
        dao.observeAllSetLogs(),
    ) { sessions, exercises, sets ->
        buildSessionDetails(sessions, exercises, sets)
    }

    fun observeSession(sessionId: Long): Flow<SessionWithExercises?> =
        observeSessions().map { sessions ->
            sessions.firstOrNull { it.session.id == sessionId }
        }

    // ---------------- Geração sob demanda ----------------

    suspend fun generatePlan(request: GeneratePlanRequest): GeneratePlanResponse {
        val response = backendClient.generatePlan(request)
        saveGeneratedPlan(response)
        return response
    }

    private suspend fun saveGeneratedPlan(response: GeneratePlanResponse): Long =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val dto = response.workout
            val sortedDays = dto.days.sortedBy { it.dayOrder }

            val days = sortedDays.map { dayDto ->
                WorkoutDayEntity(
                    planId = 0,
                    name = dayDto.name,
                    dayOrder = dayDto.dayOrder,
                    isRestDay = dayDto.isRestDay,
                )
            }

            val exercisesByDay = sortedDays.map { dayDto ->
                dayDto.exercises
                    .sortedBy { it.exerciseOrder }
                    .map { exercise ->
                        WorkoutPlanExerciseEntity(
                            dayId = 0,
                            exerciseId = exercise.exerciseId,
                            exerciseName = exercise.exerciseName.ifBlank { exercise.exerciseId },
                            primaryMuscles = exercise.primaryMuscles.joinToString(", "),
                            equipment = exercise.equipment,
                            exerciseOrder = exercise.exerciseOrder,
                            sets = exercise.sets,
                            targetReps = exercise.targetReps,
                            targetRpe = exercise.targetRpe,
                            restSeconds = exercise.restSeconds,
                            notes = exercise.notes,
                            isSupersetWithPrevious = exercise.isSupersetWithPrevious,
                        )
                    }
            }

            dao.replaceActivePlan(
                plan = WorkoutPlanEntity(
                    serverPlanId = response.planId,
                    name = dto.name,
                    description = dto.description,
                    goal = dto.goal,
                    daysPerWeek = dto.daysPerWeek,
                    isActive = true,
                    generatedByAi = response.source == "ai",
                    createdAt = now,
                    updatedAt = now,
                    nextReviewAt = response.nextReviewAt,
                    reviewAfterWeeks = response.reviewAfterWeeks,
                    source = response.source,
                ),
                days = days,
                exercisesByDay = exercisesByDay,
            )
        }

    // ---------------- Execução do treino ----------------

    suspend fun startSession(planId: Long, dayId: Long): Long = withContext(Dispatchers.IO) {
        val plan = dao.getPlan(planId)
            ?: error("Plano não encontrado no aparelho")
        val day = dao.getDay(dayId)
            ?: error("Dia de treino não encontrado")
        val planExercises = dao.getExercisesForDay(dayId)

        val now = System.currentTimeMillis()
        val sessionId = dao.insertSession(
            WorkoutSessionEntity(
                planId = plan.id,
                dayId = day.id,
                planName = plan.name,
                dayName = day.name,
                status = "in_progress",
                startedAt = now,
            )
        )

        if (planExercises.isNotEmpty()) {
            dao.insertSessionExercises(
                planExercises.map { exercise ->
                    SessionExerciseEntity(
                        sessionId = sessionId,
                        exerciseId = exercise.exerciseId,
                        exerciseName = exercise.exerciseName.ifBlank { exercise.exerciseId },
                        exerciseOrder = exercise.exerciseOrder,
                        plannedSets = exercise.sets,
                        completedSets = 0,
                        restSeconds = exercise.restSeconds,
                        startedAt = now,
                    )
                }
            )
        }
        sessionId
    }

    suspend fun logSet(
        exerciseLogId: Long,
        reps: Int?,
        weightKg: Double?,
        rpe: Double?,
        restTakenSeconds: Int? = null,
    ): Int = withContext(Dispatchers.IO) {
        val setNumber = dao.maxSetNumber(exerciseLogId) + 1
        val now = System.currentTimeMillis()
        dao.insertSetLog(
            SetLogEntity(
                exerciseLogId = exerciseLogId,
                setNumber = setNumber,
                reps = reps,
                weightKg = weightKg,
                rpe = rpe,
                isCompleted = true,
                isWarmup = false,
                restTakenSeconds = restTakenSeconds,
                completedAt = now,
            )
        )
        dao.incrementCompletedSets(exerciseLogId)
        dao.setExerciseEndedAt(exerciseLogId, now)
        setNumber
    }

    suspend fun finishSession(sessionId: Long) = withContext(Dispatchers.IO) {
        val session = dao.getSession(sessionId) ?: return@withContext
        val now = System.currentTimeMillis()
        dao.updateSession(
            session.copy(
                status = "completed",
                endedAt = now,
                totalDurationSeconds = ((now - session.startedAt) / 1000L).coerceAtLeast(0L),
            )
        )
    }

    // ---------------- Sincronização manual ----------------

    suspend fun sync(): SyncResponseDto {
        val payload = buildSyncPayload()
        val response = backendClient.sync(payload)
        preferences.lastSyncAt = response.syncedAt
        return response
    }

    suspend fun downloadSync(): SyncDownloadDto {
        val data = backendClient.syncDownload(preferences.deviceId)
        withContext(Dispatchers.IO) {
            dao.deleteAllData()
        }

        // Mapeia localId do servidor -> novo ID do Room
        val planIdMap = mutableMapOf<Long, Long>()

        // Restaura planos, dias e exercícios
        for (planDto in data.plans) {
            val newPlanId = dao.insertPlan(
                WorkoutPlanEntity(
                    id = planDto.localId,
                    serverPlanId = null,
                    name = planDto.name,
                    description = planDto.description,
                    goal = planDto.goal,
                    daysPerWeek = planDto.daysPerWeek,
                    isActive = planDto.isActive,
                    generatedByAi = planDto.generatedByAi,
                    createdAt = planDto.createdAt,
                    updatedAt = planDto.updatedAt,
                    nextReviewAt = planDto.nextReviewAt,
                    reviewAfterWeeks = planDto.reviewAfterWeeks,
                    source = planDto.source,
                    version = planDto.version,
                )
            )
            planIdMap[planDto.localId] = newPlanId
            val dayDtos = data.days.filter { it.planId == planDto.localId }
            for (dayDto in dayDtos) {
                val dayId = dao.insertDay(
                    WorkoutDayEntity(
                        id = dayDto.id,
                        planId = newPlanId,
                        name = dayDto.name,
                        dayOrder = dayDto.dayOrder,
                        isRestDay = dayDto.isRestDay,
                    )
                )
                val exDtos = data.planExercises.filter { it.dayId == dayDto.id }
                dao.insertPlanExercises(
                    exDtos.map { ex ->
                        WorkoutPlanExerciseEntity(
                            id = ex.id,
                            dayId = dayId,
                            exerciseId = ex.exerciseId,
                            exerciseName = ex.exerciseName,
                            primaryMuscles = ex.primaryMuscles,
                            equipment = ex.equipment,
                            exerciseOrder = ex.exerciseOrder,
                            sets = ex.sets,
                            targetReps = ex.targetReps,
                            targetRpe = ex.targetRpe,
                            restSeconds = ex.restSeconds,
                            notes = ex.notes,
                            isSupersetWithPrevious = ex.isSupersetWithPrevious,
                        )
                    }
                )
            }
        }

        // Garante que exatamente um plano esteja ativo (o primeiro com isActive=true,
        // senão o primeiro da lista)
        val allPlans = dao.getAllPlanEntities()
        if (allPlans.isNotEmpty()) {
            dao.deactivateAll()
            val activePlan = allPlans.firstOrNull { it.isActive } ?: allPlans[0]
            dao.updatePlanActive(activePlan.id, true)
        }

        // Mapeia localId de sessão -> novo ID do Room
        val sessionIdMap = mutableMapOf<Long, Long>()

        // Restaura sessões, exercícios de sessão e set logs
        for (sessionDto in data.sessions) {
            val planRoomId = planIdMap[sessionDto.planLocalId] ?: sessionDto.planLocalId
            val newSessionId = dao.insertSession(
                WorkoutSessionEntity(
                    id = sessionDto.id,
                    planId = planRoomId,
                    dayId = sessionDto.dayLocalId,
                    planName = sessionDto.planName,
                    dayName = sessionDto.dayName,
                    status = sessionDto.status,
                    startedAt = sessionDto.startedAt,
                    endedAt = sessionDto.endedAt,
                    totalDurationSeconds = sessionDto.totalDurationSeconds,
                )
            )
            sessionIdMap[sessionDto.localId] = newSessionId
            val sessExDtos = data.sessionExercises.filter { it.sessionId == sessionDto.localId }
            val newExIds = dao.insertSessionExercises(
                sessExDtos.map { ex ->
                    SessionExerciseEntity(
                        id = ex.id,
                        sessionId = newSessionId,
                        exerciseId = ex.exerciseId,
                        exerciseName = ex.exerciseName,
                        exerciseOrder = ex.exerciseOrder,
                        plannedSets = ex.plannedSets,
                        completedSets = ex.completedSets,
                        startedAt = ex.startedAt,
                        endedAt = ex.endedAt,
                    )
                }
            )
            val exIdMap = sessExDtos.zip(newExIds).toMap()
            for ((exDto, newExId) in sessExDtos.zip(newExIds)) {
                val setDtos = data.setLogs.filter { it.sessionExerciseId == exDto.id }
                for (set in setDtos) {
                    dao.insertSetLog(
                        SetLogEntity(
                            id = set.id,
                            exerciseLogId = newExId,
                            setNumber = set.setNumber,
                            reps = set.reps,
                            weightKg = set.weightKg,
                            rpe = set.rpe,
                            isCompleted = set.isCompleted,
                            isWarmup = set.isWarmup,
                            restTakenSeconds = set.restTakenSeconds,
                            completedAt = set.completedAt,
                        )
                    )
                }
            }
        }

        preferences.lastSyncAt = Instant.now().toString()
        return data
    }

    suspend fun testConnection() = withContext(Dispatchers.IO) {
        backendClient.health()
    }

    private suspend fun buildSyncPayload(): SyncRequestDto = withContext(Dispatchers.IO) {
        val allPlans = dao.getAllPlanEntities()
        val allDays = dao.getAllDayEntities()
        val allPlanExercises = dao.getAllPlanExercises()
        val sessions = buildSessionDetails(
            sessionEntities = dao.getAllSessionEntities(),
            exerciseEntities = dao.getAllSessionExercises(),
            setEntities = dao.getAllSetLogs(),
        )

        SyncRequestDto(
            deviceId = preferences.deviceId,
            syncedAt = Instant.now().toString(),
            plans = allPlans.map { plan ->
                buildPlanDetails(plan, allDays, allPlanExercises).toSyncDto()
            },
            sessions = sessions.map { it.toSyncDto() },
        )
    }

    private fun buildPlanDetails(
        plan: WorkoutPlanEntity,
        dayEntities: List<WorkoutDayEntity>,
        exerciseEntities: List<WorkoutPlanExerciseEntity>,
    ): WorkoutPlanWithDetails {
        val exercisesByDay = exerciseEntities.groupBy { it.dayId }
        val days = dayEntities
            .filter { it.planId == plan.id }
            .sortedBy { it.dayOrder }
            .map { day ->
                WorkoutDayWithExercises(
                    day = day,
                    exercises = exercisesByDay[day.id]
                        .orEmpty()
                        .sortedBy { it.exerciseOrder },
                )
            }
        return WorkoutPlanWithDetails(plan = plan, days = days)
    }

    private fun buildSessionDetails(
        sessionEntities: List<WorkoutSessionEntity>,
        exerciseEntities: List<SessionExerciseEntity>,
        setEntities: List<SetLogEntity>,
    ): List<SessionWithExercises> {
        val setsByExercise = setEntities.groupBy { it.exerciseLogId }
        val exercisesBySession = exerciseEntities.groupBy { it.sessionId }
        return sessionEntities.map { session ->
            val exercises = exercisesBySession[session.id]
                .orEmpty()
                .sortedBy { it.exerciseOrder }
                .map { exercise ->
                    SessionExerciseWithSets(
                        exercise = exercise,
                        sets = setsByExercise[exercise.id]
                            .orEmpty()
                            .sortedBy { it.setNumber },
                    )
                }
            SessionWithExercises(session = session, exercises = exercises)
        }
    }

    private fun WorkoutPlanWithDetails.toSyncDto(): SyncPlanDto = SyncPlanDto(
        localId = plan.id,
        name = plan.name,
        description = plan.description,
        goal = plan.goal,
        daysPerWeek = plan.daysPerWeek,
        isActive = plan.isActive,
        generatedByAi = plan.generatedByAi,
        createdAt = plan.createdAt,
        updatedAt = plan.updatedAt,
        version = plan.version,
        nextReviewAt = plan.nextReviewAt,
        reviewAfterWeeks = plan.reviewAfterWeeks,
        source = plan.source,
        days = days
            .sortedBy { it.day.dayOrder }
            .map { it.toSyncDto() },
    )

    private fun WorkoutDayWithExercises.toSyncDto(): SyncDayDto = SyncDayDto(
        localId = day.id,
        name = day.name,
        dayOrder = day.dayOrder,
        notes = day.notes,
        isRestDay = day.isRestDay,
        exercises = exercises
            .sortedBy { it.exerciseOrder }
            .map { exercise ->
                SyncPlanExerciseDto(
                    exerciseId = exercise.exerciseId,
                    exerciseOrder = exercise.exerciseOrder,
                    sets = exercise.sets,
                    targetReps = exercise.targetReps,
                    targetRpe = exercise.targetRpe,
                    restSeconds = exercise.restSeconds,
                    notes = exercise.notes,
                    isSupersetWithPrevious = exercise.isSupersetWithPrevious,
                    exerciseName = exercise.exerciseName,
                    primaryMuscles = exercise.primaryMuscles
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() },
                    equipment = exercise.equipment,
                )
            },
    )

    private fun SessionWithExercises.toSyncDto(): SyncSessionDto = SyncSessionDto(
        localId = session.id,
        planLocalId = session.planId,
        dayLocalId = session.dayId,
        planName = session.planName,
        dayName = session.dayName,
        status = session.status,
        startedAt = session.startedAt,
        endedAt = session.endedAt,
        totalDurationSeconds = session.totalDurationSeconds,
        notes = session.notes,
        exercises = exercises
            .sortedBy { it.exercise.exerciseOrder }
            .map { exercise ->
                SyncSessionExerciseDto(
                    localId = exercise.exercise.id,
                    exerciseId = exercise.exercise.exerciseId,
                    exerciseName = exercise.exercise.exerciseName,
                    exerciseOrder = exercise.exercise.exerciseOrder,
                    plannedSets = exercise.exercise.plannedSets,
                    completedSets = exercise.exercise.completedSets,
                    startedAt = exercise.exercise.startedAt,
                    endedAt = exercise.exercise.endedAt,
                    sets = exercise.sets.sortedBy { it.setNumber }.map { set ->
                        SyncSetLogDto(
                            localId = set.id,
                            setNumber = set.setNumber,
                            reps = set.reps,
                            weightKg = set.weightKg,
                            rpe = set.rpe,
                            durationSeconds = set.durationSeconds,
                            distanceMeters = set.distanceMeters,
                            isCompleted = set.isCompleted,
                            isWarmup = set.isWarmup,
                            restTakenSeconds = set.restTakenSeconds,
                            completedAt = set.completedAt,
                        )
                    },
                )
            },
    )
}
