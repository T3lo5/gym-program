package com.treinos.app.data

import com.treinos.app.data.local.ExerciseUsageEvent
import com.treinos.app.data.local.MANUAL_SESSION_SOURCE
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
import com.treinos.app.data.media.ExerciseMedia
import com.treinos.app.data.prefs.AppPreferences
import com.treinos.app.data.remote.BackendApi
import com.treinos.app.data.remote.GenerateJobAck
import com.treinos.app.data.remote.GenerateJobStatus
import com.treinos.app.data.remote.GeneratePlanRequest
import com.treinos.app.data.remote.GeneratePlanResponse
import com.treinos.app.data.remote.ReviewPlanRequest
import com.treinos.app.data.remote.ReviewPlanResponse
import com.treinos.app.data.backup.BackupApp
import com.treinos.app.data.backup.BackupFile
import com.treinos.app.data.backup.BackupMapper
import com.treinos.app.data.backup.BackupProfile
import com.treinos.app.data.backup.BackupValidator
import com.treinos.app.data.backup.backupExportEncoder
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
import com.treinos.app.ui.progression.REVIEW_POSTPONE_WEEKS
import com.treinos.app.ui.progression.postponeReviewDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.time.Instant
import java.time.LocalDate

/**
 * Fonte única de dados do app.
 *
 * Tudo que o usuário anota durante o treino é gravado no Room (SQLite) na hora.
 * O backend só é populado quando [sync] é chamado explicitamente.
 */
/** Escopo de uma troca de exercício durante o treino em andamento. */
enum class SwapScope {
    /** Afeta apenas a sessão atual (o treino de hoje). */
    THIS_SESSION,
    /** Afeta o plano salvo, valendo para todos os treinos futuros. */
    ALL_FUTURE,
}

open class WorkoutRepository(
    private val dao: WorkoutDao,
    private val backendClient: BackendApi,
    private val preferences: AppPreferences,
) : SnapshotDownloader {

    fun observeActivePlans(): Flow<List<WorkoutPlanWithDetails>> = combine(
        dao.observeActivePlanEntities(),
        dao.observeAllDays(),
        dao.observeAllPlanExercises(),
    ) { plans, days, exercises ->
        plans.map { buildPlanDetails(it, days, exercises) }
    }

    suspend fun reviewPlan(request: ReviewPlanRequest): ReviewPlanResponse =
        backendClient.reviewPlan(request)

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

    // ---------------- Geração assíncrona sob demanda (H3-40) ----------------

    /** Submete a geração ao backend; só rede, não salva localmente. */
    suspend fun submitGeneration(request: GeneratePlanRequest): GenerateJobAck =
        backendClient.submitGeneration(request)

    /**
     * Consulta o status de um job pelo jobId.
     */
    suspend fun generationStatus(jobId: String): GenerateJobStatus =
        backendClient.generationStatus(jobId)

    /**
     * Cancela um job em andamento (idempotente).
     */
    suspend fun cancelGeneration(jobId: String): GenerateJobStatus =
        backendClient.cancelGeneration(jobId)

    /**
     * Conclui um job `done`: aplica a regra de nome do complementar e salva o
     * plano de forma atômica. Não toca em prefs de config/fallbackReason — isso
     * cabe ao ViewModel.
     */
    open suspend fun completeGeneration(status: GenerateJobStatus, kind: String = "main"): Long {
        val response = status.result ?: error("Job concluído sem resultado.")
        // O backend não conhece `kind` (H3-15): com foco exclusivo ele já nomeia
        // "Complementar — <foco>" (H3-16); no complementar só de ambiente, o
        // app aplica o nome localmente ao salvar.
        val finalResponse = if (
            kind == "extra" && !response.workout.name.startsWith("Complementar")
        ) {
            // O ambiente já veio no request do envelope; não está aqui. Só
            // renomeia quando o backend não nomeou (fallback local do
            // complementar só de ambiente).
            response.copy(workout = response.workout.copy(name = "Complementar — Academia"))
        } else {
            response
        }
        return saveGeneratedPlan(finalResponse, kind)
    }

    /**
     * Salva o plano gerado (genérico: geração ou revisão aceita). `main`
     * substitui o main anterior; `extra` só insere, sem desativar nada.
     */
    open suspend fun saveGeneratedPlan(response: GeneratePlanResponse, kind: String = "main"): Long {
        val now = System.currentTimeMillis()
            val dto = response.workout
            val sortedDays = dto.days.sortedBy { it.dayOrder }

            val days = sortedDays.map { dayDto ->
                WorkoutDayEntity(
                    planId = 0,
                    name = dayDto.name,
                    dayOrder = dayDto.dayOrder,
                    isRestDay = dayDto.isRestDay,
                    weekday = dayDto.weekday,
                    modality = dayDto.modality,
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
                            // H3-24: mídia que veio no plano (enrich_plan) é
                            // persistida — o detalhe funciona sem o catálogo.
                            instructions = exercise.instructions.joinToString("\n"),
                            images = exercise.images.joinToString(","),
                            exerciseOrder = exercise.exerciseOrder,
                            sets = exercise.sets,
                            targetReps = exercise.targetReps,
                            targetRpe = exercise.targetRpe,
                            restSeconds = exercise.restSeconds,
                            notes = exercise.notes,
                            isSupersetWithPrevious = exercise.isSupersetWithPrevious,
                            metricType = exercise.metricType,
                            targetDurationSeconds = exercise.targetDurationSeconds,
                            targetDistanceMeters = exercise.targetDistanceMeters,
                            targetPaceSecondsPerKm = exercise.targetPaceSecondsPerKm,
                            targetHrZone = exercise.targetHrZone,
                        )
                    }
            }

            val plan = WorkoutPlanEntity(
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
                kind = kind,
            )
            return if (kind == "main") {
                dao.replaceActivePlan(plan, days, exercisesByDay)
            } else {
                dao.insertPlanWithDays(plan, days, exercisesByDay)
            }
        }

    /** Exclui um plano complementar; o histórico (sem FK) é preservado. */
    suspend fun deleteExtraPlan(planId: Long) = withContext(Dispatchers.IO) {
        val plan = dao.getPlan(planId)
            ?: error("Plano não encontrado no aparelho.")
        require(plan.kind == "extra") { "Só planos complementares podem ser excluídos." }
        dao.deletePlan(planId)
    }

    // ---------------- Troca de exercício em plano salvo (H3-41) ----------------

    /**
     * Troca o exercício na [position] do dia [dayId] por [replacementExerciseId]
     * do catálogo local, preservando séries/reps/ordem e as demais regras da
     * spec (mesmo músculo, equipamento informado, sem duplicar, atômico).
     *
     * Recusa sessões em andamento: se o dia já tiver uma sessão `in_progress`,
     * a troca é rejeitada.
     *
     * `catalog` e `availableEquipment` vêm do chamador (ViewModel) para manter
     * esta função testável sem acoplar o repository ao ExerciseMediaRepository.
     */
    suspend fun swapExercise(
        dayId: Long,
        position: Int,
        replacementExerciseId: String,
        catalog: Map<String, ExerciseMedia>,
        availableEquipment: List<String>,
    ): SwapResult = withContext(Dispatchers.IO) {
        val day = dao.getDay(dayId)
            ?: return@withContext SwapResult.Error("Dia de treino não encontrado.")
        val dayExercises = dao.getExercisesForDay(dayId)
        if (position < 0 || position >= dayExercises.size) {
            return@withContext SwapResult.Error("Posição do exercício inválida.")
        }
        val original = dayExercises[position]

        // Não troca exercício de sessão já iniciada.
        val inProgress = dao.getInProgressSessionForDay(dayId)
        if (inProgress != null) {
            return@withContext SwapResult.Error("Não é possível trocar exercício de um treino já iniciado.")
        }

        val validation = validateSwap(
            original = original,
            dayExercises = dayExercises,
            replacementExerciseId = replacementExerciseId,
            catalog = catalog,
            availableEquipment = availableEquipment,
        )
        if (validation !is SwapResult.Ok) {
            return@withContext validation
        }

        val replacement = catalog[validation.exerciseId]!!
        val updated = original.copy(
            exerciseId = replacement.id,
            exerciseName = replacement.name,
            primaryMuscles = replacement.primaryMuscles.joinToString(", "),
            equipment = replacement.equipment,
        )
        dao.updatePlanExercise(updated)
        SwapResult.Ok(exerciseId = replacement.id, exerciseName = replacement.name)
    }

    // ---------------- Troca de exercício durante o treino em andamento --------

    /**
     * Troca o exercício de uma sessão em andamento.
     *
     * - [SwapScope.THIS_SESSION]: atualiza só o `SessionExerciseEntity` da
     *   sessão atual; não mexe no plano salvo.
     * - [SwapScope.ALL_FUTURE]: atualiza o `WorkoutPlanExerciseEntity` do
     *   plano (mesma regra do H3-41: mesmo músculo, equipamento informado,
     *   sem duplicar no dia), valendo para os próximos treinos.
     *
     * `catalog` e `availableEquipment` vêm do caller para manter a função
     * testável.
     */
    suspend fun swapSessionExercise(
        sessionExerciseId: Long,
        replacementExerciseId: String,
        scope: SwapScope,
        catalog: Map<String, ExerciseMedia>,
        availableEquipment: List<String>,
    ): SwapResult = withContext(Dispatchers.IO) {
        val sessionExercise = dao.getSessionExercise(sessionExerciseId)
            ?: return@withContext SwapResult.Error("Exercício da sessão não encontrado.")
        val session = dao.getSession(sessionExercise.sessionId)
            ?: return@withContext SwapResult.Error("Sessão não encontrada.")
        val dayId = session.dayId
        val dayExercises = dao.getExercisesForDay(dayId)

        val originalMedia = catalog[sessionExercise.exerciseId]
        val originalMuscle = originalMedia?.primaryMuscles?.firstOrNull()

        val replacement = catalog[replacementExerciseId]
            ?: return@withContext SwapResult.Error("Exercício não encontrado no catálogo.")
        if (replacement.id == sessionExercise.exerciseId) {
            return@withContext SwapResult.Error("Escolha um exercício diferente do atual.")
        }
        if (!availableEquipment.isEmpty() && !availableEquipment.contains(replacement.equipment)) {
            return@withContext SwapResult.Error("Equipamento não disponível no seu treino.")
        }
        if (originalMuscle == null ||
            !replacement.primaryMuscles.any { it.equals(originalMuscle, ignoreCase = true) }) {
            return@withContext SwapResult.Error("Músculo incompatível com o exercício original.")
        }
        // Não duplica: já existe um exercício com este ID no dia (excluindo o original da sessão)
        val takenIds = dayExercises.map { it.exerciseId }.toSet() - setOf(sessionExercise.exerciseId)
        if (takenIds.contains(replacementExerciseId)) {
            return@withContext SwapResult.Error("Este exercício já está neste dia.")
        }

        when (scope) {
            SwapScope.THIS_SESSION -> {
                dao.updateSessionExercise(
                    sessionExercise.copy(
                        exerciseId = replacement.id,
                        exerciseName = replacement.name,
                    )
                )
                SwapResult.Ok(exerciseId = replacement.id, exerciseName = replacement.name)
            }
            SwapScope.ALL_FUTURE -> {
                // Atualiza o plano salvo (futuro). Não recusa por sessão in_progress,
                // porque a sessão atual já começou com o exercício antigo.
                val originalPlanExercise = dayExercises.firstOrNull { it.exerciseId == sessionExercise.exerciseId }
                    ?: return@withContext SwapResult.Error("Exercício não encontrado no plano salvo.")
                val updated = originalPlanExercise.copy(
                    exerciseId = replacement.id,
                    exerciseName = replacement.name,
                    primaryMuscles = replacement.primaryMuscles.joinToString(", "),
                    equipment = replacement.equipment,
                )
                dao.updatePlanExercise(updated)
                SwapResult.Ok(exerciseId = replacement.id, exerciseName = replacement.name)
            }
        }
    }

    // ---------------- Uso de exercício para calibrar precisão (H3-42) ----------

    /**
     * Registra um evento de uso do exercício (concluído/pulado/trocado). Sinal
     * local-only: nada sai do aparelho, usado apenas para ordenar o catálogo da
     * próxima geração. Nunca falha o plano nem a geração se a gravação der
     * problema.
     */
    suspend fun recordExerciseUsage(
        exerciseId: String,
        outcome: String,
        planId: Long? = null,
        dayId: Long? = null,
        source: String = "generation",
    ) = withContext(Dispatchers.IO) {
        dao.insertUsageEvent(
            ExerciseUsageEvent(
                exerciseId = exerciseId,
                outcome = outcome,
                occurredAt = System.currentTimeMillis(),
                planId = planId,
                dayId = dayId,
                source = source,
            )
        )
    }

    /**
     * Peso de uso de cada exercício do catálogo, a partir dos eventos dos
     * últimos 90 dias. Usado pelo backend para ordenar o prompt (cobertura
     * total preservada: só reordena, nunca exclui).
     */
    suspend fun usageWeightsForCatalog(
        exerciseIds: List<String>,
    ): Map<String, Int> = withContext(Dispatchers.IO) {
        val since = System.currentTimeMillis() - USAGE_WINDOW_DAYS * 24 * 60 * 60 * 1000L
        val events = dao.getUsageEventsSince(since)
        usageWeightsForCatalog(exerciseIds, events, System.currentTimeMillis())
    }

    // ---------------- Revisão do plano (H3-5) ----------------

    /** Adia `nextReviewAt` sem nunca antecipar a data que já está salva. */
    suspend fun postponeReview(planId: Long, weeks: Int = REVIEW_POSTPONE_WEEKS) =
        withContext(Dispatchers.IO) {
            val plan = dao.getPlan(planId) ?: return@withContext
            val next = postponeReviewDate(plan.nextReviewAt, LocalDate.now(), weeks)
            dao.updatePlanReviewDate(planId, next.toString())
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
                title = day.name,
                modality = day.modality,
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
                        targetReps = exercise.targetReps,
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

    /** Finaliza uma sessão de corrida planejada: grava duração, distância e RPE. */
    suspend fun finishRunSession(
        sessionId: Long,
        durationSeconds: Long,
        distanceMeters: Double?,
        rpe: Double?,
    ) = withContext(Dispatchers.IO) {
        val session = dao.getSession(sessionId) ?: return@withContext
        val now = System.currentTimeMillis()
        dao.updateSession(
            session.copy(
                status = "completed",
                endedAt = now,
                totalDurationSeconds = durationSeconds.coerceAtLeast(0L),
                totalDistanceMeters = distanceMeters?.takeIf { it > 0.0 },
                sessionRpe = rpe,
            )
        )
    }

    /**
     * Atualiza uma série já registrada pelo `id` (H3-27). O vínculo com o
     * exercício da sessão e o número da série não mudam na edição — só
     * reps/carga/RPE.
     */
    suspend fun updateSet(set: SetLogEntity) = withContext(Dispatchers.IO) {
        val existing = dao.getSetLog(set.id)
            ?: error("Série não encontrada no aparelho.")
        dao.updateSetLog(
            set.copy(
                exerciseLogId = existing.exerciseLogId,
                setNumber = existing.setNumber,
            )
        )
    }

    /**
     * Exclui uma série pelo `id` (H3-27) e realinha o contador de séries
     * concluídas do exercício ao número real de séries gravadas.
     */
    suspend fun deleteSet(setId: Long) = withContext(Dispatchers.IO) {
        val existing = dao.getSetLog(setId) ?: return@withContext
        dao.deleteSetLog(setId)
        dao.recalculateCompletedSets(existing.exerciseLogId)
    }

    /**
     * Exclui a sessão inteira ("Abandonar treino", H3-27). Exercícios e séries
     * caem por CASCADE; o histórico das demais sessões fica intacto.
     */
    suspend fun deleteSession(sessionId: Long) = withContext(Dispatchers.IO) {
        dao.deleteSession(sessionId)
    }

    /**
     * Fecha a sessão de mobilidade planejada que já estava aberta (H3-27):
     * UPDATE da sessão `in_progress` para `completed` — nunca insere uma
     * segunda sessão, que ficaria como fantasma eterno na Home. O `title`
     * passa a apontar a rotina, como em [logPlannedMobility].
     */
    suspend fun finishPlannedMobilitySession(
        sessionId: Long,
        routineId: String,
        elapsedSeconds: Long,
    ) = withContext(Dispatchers.IO) {
        val session = dao.getSession(sessionId) ?: return@withContext
        val now = System.currentTimeMillis()
        dao.updateSession(
            session.copy(
                status = "completed",
                endedAt = now,
                totalDurationSeconds = elapsedSeconds.coerceAtLeast(0L),
                title = routineId,
            )
        )
    }

    /** Grava uma sessão de mobilidade planejada concluída, sem séries. */
    suspend fun logPlannedMobility(
        planId: Long,
        dayId: Long,
        routineId: String,
        elapsedSeconds: Long,
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val sessionId = dao.insertSession(
            WorkoutSessionEntity(
                planId = planId,
                dayId = dayId,
                planName = "",
                dayName = "Mobilidade",
                title = routineId,
                modality = "mobility",
                source = "plan",
                status = "completed",
                startedAt = now - (elapsedSeconds.coerceAtLeast(0L) * 1000L),
                endedAt = now,
                totalDurationSeconds = elapsedSeconds.coerceAtLeast(0L),
            )
        )
        sessionId
    }

    // ---------------- Sessão avulsa (registro manual) ----------------

    /**
     * Grava um registro que não veio de plano: nasce `completed`, com
     * `planId = dayId = 0` e nenhum exercício (a sessão manual tem zero filhos).
     */
    suspend fun logActivity(session: WorkoutSessionEntity): Long = withContext(Dispatchers.IO) {
        dao.insertSession(session)
    }

    /** Atualiza o mesmo `id`: editar não cria uma linha nova. */
    suspend fun updateActivity(session: WorkoutSessionEntity) = withContext(Dispatchers.IO) {
        val existing = dao.getSession(session.id)
            ?: error("Registro não encontrado no aparelho.")
        require(existing.source == MANUAL_SESSION_SOURCE) {
            "Só é possível editar um registro manual."
        }
        dao.updateSession(session)
    }

    suspend fun deleteActivity(sessionId: Long) = withContext(Dispatchers.IO) {
        val existing = dao.getSession(sessionId) ?: return@withContext
        require(existing.source == MANUAL_SESSION_SOURCE) {
            "Só é possível excluir um registro manual."
        }
        dao.deleteSession(sessionId)
    }

    suspend fun lastManualSession(modality: String): WorkoutSessionEntity? =
        withContext(Dispatchers.IO) {
            dao.getAllSessionEntities().firstOrNull {
                it.source == MANUAL_SESSION_SOURCE && it.modality == modality
            }
        }

    // ---------------- Sincronização manual ----------------

    suspend fun sync(): SyncResponseDto {
        val payload = buildSyncPayload()
        val response = backendClient.sync(payload)
        preferences.lastSyncAt = response.syncedAt
        return response
    }

    override suspend fun downloadSync(deviceId: String): SyncDownloadResult =
        withContext(Dispatchers.IO) {
            val data = backendClient.syncDownload(deviceId)
            if (data.plans.isEmpty() && data.sessions.isEmpty()) {
                return@withContext SyncDownloadResult.Empty
            }
            val snapshot = mapSnapshot(data)
            dao.replaceAllData(
                plans = snapshot.plans,
                days = snapshot.days,
                planExercises = snapshot.planExercises,
                sessions = snapshot.sessions,
                sessionExercises = snapshot.sessionExercises,
                setLogs = snapshot.setLogs,
            )
            preferences.lastSyncAt = Instant.now().toString()
            SyncDownloadResult.Restored(
                planCount = snapshot.plans.size,
                sessionCount = snapshot.sessions.size,
            )
        }

    /**
     * Converte o snapshot do backend em entidades do Room preservando os IDs
     * locais do aparelho (campos `localId`).
     *
     * Só leitura: nada é escrito aqui. ID repetido, ID não positivo ou referência
     * a um pai que não existe no snapshot derruba a restauração antes de o banco
     * local ser tocado.
     */
    private fun mapSnapshot(data: SyncDownloadDto): RestoredSnapshot {
        val usedPlanIds = mutableSetOf<Long>()
        val plans = data.plans.map { dto ->
            requirePositiveId(dto.localId, "plano '${dto.name}'")
            require(usedPlanIds.add(dto.localId)) { "IDs de plano duplicados no snapshot (${dto.localId})." }
            WorkoutPlanEntity(
                id = dto.localId,
                serverPlanId = null,
                name = dto.name,
                description = dto.description,
                goal = dto.goal,
                daysPerWeek = dto.daysPerWeek,
                isActive = dto.isActive,
                generatedByAi = dto.generatedByAi,
                createdAt = dto.createdAt,
                updatedAt = dto.updatedAt,
                nextReviewAt = dto.nextReviewAt,
                reviewAfterWeeks = dto.reviewAfterWeeks,
                source = dto.source,
                version = dto.version,
                kind = dto.kind,
            )
        }

        // H3-15: vários planos podem voltar ativos (1 main + N extras). Só quando
        // nenhum vem ativo o primeiro main é ativado, para a Home não ficar vazia.
        val normalizedPlans = if (plans.none { it.isActive }) {
            val firstMain = plans.indexOfFirst { it.kind == "main" }
            if (firstMain >= 0) {
                plans.mapIndexed { index, plan -> plan.copy(isActive = index == firstMain) }
            } else {
                plans
            }
        } else {
            plans
        }
        val planIds = normalizedPlans.mapTo(mutableSetOf()) { it.id }

        val usedDayIds = mutableSetOf<Long>()
        val days = data.days.map { dto ->
            requirePositiveId(dto.localId, "dia '${dto.name}'")
            require(usedDayIds.add(dto.localId)) { "IDs de dia duplicados no snapshot (${dto.localId})." }
            require(dto.planId in planIds) {
                "Dia '${dto.name}' referencia um plano que não está no snapshot (planId ${dto.planId})."
            }
            WorkoutDayEntity(
                id = dto.localId,
                planId = dto.planId,
                name = dto.name,
                dayOrder = dto.dayOrder,
                notes = dto.notes,
                isRestDay = dto.isRestDay,
                modality = dto.modality,
                weekday = dto.weekday,
            )
        }
        val dayIds = days.mapTo(mutableSetOf()) { it.id }

        val planExercises = data.planExercises.map { dto ->
            require(dto.dayId in dayIds) {
                "Exercício '${dto.exerciseName.ifBlank { dto.exerciseId }}' referencia um dia " +
                    "que não está no snapshot (dayId ${dto.dayId})."
            }
            WorkoutPlanExerciseEntity(
                dayId = dto.dayId,
                exerciseId = dto.exerciseId,
                exerciseName = dto.exerciseName,
                primaryMuscles = dto.primaryMuscles,
                equipment = dto.equipment,
                exerciseOrder = dto.exerciseOrder,
                sets = dto.sets,
                targetReps = dto.targetReps,
                targetRpe = dto.targetRpe,
                restSeconds = dto.restSeconds,
                notes = dto.notes,
                isSupersetWithPrevious = dto.isSupersetWithPrevious,
                metricType = dto.metricType,
                targetDurationSeconds = dto.targetDurationSeconds,
                targetDistanceMeters = dto.targetDistanceMeters,
                targetPaceSecondsPerKm = dto.targetPaceSecondsPerKm,
                targetHrZone = dto.targetHrZone,
            )
        }

        val usedSessionIds = mutableSetOf<Long>()
        val sessions = data.sessions.map { dto ->
            requirePositiveId(dto.localId, "sessão ${dto.localId}")
            require(usedSessionIds.add(dto.localId)) {
                "IDs de sessão duplicados no snapshot (${dto.localId})."
            }
            require(dto.planLocalId >= 0 && dto.dayLocalId >= 0) {
                "Sessão ${dto.localId} tem referência negativa (planId ${dto.planLocalId}, " +
                    "dayId ${dto.dayLocalId})."
            }
            require(dto.planLocalId == 0L || dto.planLocalId in planIds) {
                "Sessão ${dto.localId} referencia um plano que não está no snapshot " +
                    "(planId ${dto.planLocalId})."
            }
            require(dto.dayLocalId == 0L || dto.dayLocalId in dayIds) {
                "Sessão ${dto.localId} referencia um dia que não está no snapshot " +
                    "(dayId ${dto.dayLocalId})."
            }
            WorkoutSessionEntity(
                id = dto.localId,
                planId = dto.planLocalId,
                dayId = dto.dayLocalId,
                planName = dto.planName,
                dayName = dto.dayName,
                status = dto.status,
                startedAt = dto.startedAt,
                endedAt = dto.endedAt,
                totalDurationSeconds = dto.totalDurationSeconds,
                notes = dto.notes,
                modality = dto.modality,
                source = dto.source,
                title = dto.title,
                totalDistanceMeters = dto.totalDistanceMeters,
                poolLengthMeters = dto.poolLengthMeters,
                stroke = dto.stroke,
                sessionRpe = dto.sessionRpe,
            )
        }
        val sessionIds = sessions.mapTo(mutableSetOf()) { it.id }

        val usedSessionExerciseIds = mutableSetOf<Long>()
        val sessionExercises = data.sessionExercises.map { dto ->
            requirePositiveId(dto.localId, "exercício de sessão '${dto.exerciseName}'")
            require(usedSessionExerciseIds.add(dto.localId)) {
                "IDs de exercício de sessão duplicados no snapshot (${dto.localId})."
            }
            require(dto.sessionId in sessionIds) {
                "Exercício de sessão '${dto.exerciseName}' referencia uma sessão que não está " +
                    "no snapshot (sessionId ${dto.sessionId})."
            }
            SessionExerciseEntity(
                id = dto.localId,
                sessionId = dto.sessionId,
                exerciseId = dto.exerciseId,
                exerciseName = dto.exerciseName,
                exerciseOrder = dto.exerciseOrder,
                plannedSets = dto.plannedSets,
                completedSets = dto.completedSets,
                startedAt = dto.startedAt,
                endedAt = dto.endedAt,
            )
        }
        // As séries chegam referenciando o `id` que o backend deu ao exercício de
        // sessão; é essa chave que liga a série ao ID local preservado acima.
        val roomIdBySnapshotExerciseId = mutableMapOf<Long, Long>()
        data.sessionExercises.forEach { dto ->
            requirePositiveId(dto.id, "exercício de sessão '${dto.exerciseName}'")
            require(roomIdBySnapshotExerciseId.put(dto.id, dto.localId) == null) {
                "IDs de exercício de sessão duplicados no snapshot (${dto.id})."
            }
        }

        val usedSetLogIds = mutableSetOf<Long>()
        val setLogs = data.setLogs.map { dto ->
            requirePositiveId(dto.localId, "série ${dto.setNumber}")
            require(usedSetLogIds.add(dto.localId)) {
                "IDs de série duplicados no snapshot (${dto.localId})."
            }
            val exerciseLogId = roomIdBySnapshotExerciseId[dto.sessionExerciseId]
            require(exerciseLogId != null) {
                "Série ${dto.setNumber} referencia um exercício de sessão que não está no snapshot " +
                    "(exerciseLogId ${dto.sessionExerciseId})."
            }
            SetLogEntity(
                id = dto.localId,
                exerciseLogId = exerciseLogId,
                setNumber = dto.setNumber,
                reps = dto.reps,
                weightKg = dto.weightKg,
                rpe = dto.rpe,
                durationSeconds = dto.durationSeconds,
                distanceMeters = dto.distanceMeters,
                isCompleted = dto.isCompleted,
                isWarmup = dto.isWarmup,
                restTakenSeconds = dto.restTakenSeconds,
                completedAt = dto.completedAt,
            )
        }

        return RestoredSnapshot(
            plans = normalizedPlans,
            days = days,
            planExercises = planExercises,
            sessions = sessions,
            sessionExercises = sessionExercises,
            setLogs = setLogs,
        )
    }

    private fun requirePositiveId(id: Long, subject: String) {
        require(id > 0) { "$subject tem ID de aparelho inválido ($id); exporte um backup antes de restaurar." }
    }

    private data class RestoredSnapshot(
        val plans: List<WorkoutPlanEntity>,
        val days: List<WorkoutDayEntity>,
        val planExercises: List<WorkoutPlanExerciseEntity>,
        val sessions: List<WorkoutSessionEntity>,
        val sessionExercises: List<SessionExerciseEntity>,
        val setLogs: List<SetLogEntity>,
    )

    // ---------------- Backup JSON manual ----------------

    suspend fun exportBackup(appVersionName: String, appVersionCode: Long): String = withContext(Dispatchers.IO) {
        val plans = dao.getAllPlanEntities()
        val days = dao.getAllDayEntities()
        val planExercises = dao.getAllPlanExercises()
        val sessions = dao.getAllSessionEntities()
        val sessionExercises = dao.getAllSessionExercises()
        val setLogs = dao.getAllSetLogs()

        val daysByPlan = days.groupBy { it.planId }
        val exercisesByDay = planExercises.groupBy { it.dayId }
        val sessionExercisesBySession = sessionExercises.groupBy { it.sessionId }
        val setLogsByExercise = setLogs.groupBy { it.exerciseLogId }

        val backupDays = days.map { day ->
            BackupMapper.toDto(
                day = day,
                exercises = (exercisesByDay[day.id]?.map { ex ->
                    BackupMapper.toDto(exercise = ex, dayId = day.id)
                } ?: emptyList()),
            )
        }

        val backupPlans = plans.map { plan ->
            BackupMapper.toDto(
                plan = plan,
                days = backupDays.filter { it.planId == plan.id },
            )
        }

        val backupSessionExercises = sessionExercises.flatMap { ex ->
            val sets = (setLogsByExercise[ex.id]?.map { setLog ->
                BackupMapper.toDto(setLog = setLog, exerciseLogId = ex.id)
            } ?: emptyList())
            listOf(BackupMapper.toDto(exercise = ex, sessionId = ex.sessionId, sets = sets))
        }

        val backupSessions = sessions.map { session ->
            BackupMapper.toDto(
                session = session,
                exercises = backupSessionExercises.filter { it.sessionId == session.id },
            )
        }

        val file = BackupFile(
            schemaVersion = 3,
            exportedAt = Instant.now().toString(),
            app = BackupApp(
                packageName = "com.treinos.app",
                versionName = appVersionName,
                versionCode = appVersionCode,
            ),
            deviceId = preferences.deviceId,
            plans = backupPlans,
            sessions = backupSessions,
            profile = BackupProfile(
                bodyWeightKg = preferences.bodyWeightKg,
                heightCm = preferences.heightCm,
                sex = preferences.sex,
            ),
        )

        backupExportEncoder.encodeToString(BackupFile.serializer(), file)
    }

    /**
     * Substitui todo o banco local pelo backup, já validado, e só então aplica o
     * `deviceId` e o perfil do arquivo: se a validação ou a escrita falhar, nem os
     * dados nem a identidade do aparelho mudam.
     */
    suspend fun importBackup(
        payload: BackupFile,
        deviceIdStore: DeviceIdStore = preferences,
        profileStore: ProfileStore = preferences,
    ) = withContext(Dispatchers.IO) {
            val validation = BackupValidator.validate(payload)
            require(validation.valid) { validation.message ?: "Backup inválido" }

            val plans = payload.plans.map { BackupMapper.toEntity(it) }
            val days = payload.plans.flatMap { plan ->
                plan.days.map { BackupMapper.toEntity(it, planId = plan.id) }
            }
            val planExercises = payload.plans.flatMap { plan ->
                plan.days.flatMap { day ->
                    day.exercises.map { BackupMapper.toEntity(it, dayId = day.id) }
                }
            }
            val sessions = payload.sessions.map { BackupMapper.toEntity(it, planId = it.planId) }
            val sessionExercises = payload.sessions.flatMap { session ->
                session.exercises.map { BackupMapper.toEntity(it, sessionId = session.id) }
            }
            val setLogs = payload.sessions.flatMap { session ->
                session.exercises.flatMap { exercise ->
                    exercise.sets.map { BackupMapper.toEntity(it, exerciseLogId = exercise.id) }
                }
            }

            dao.replaceAllData(
                plans = plans,
                days = days,
                planExercises = planExercises,
                sessions = sessions,
                sessionExercises = sessionExercises,
                setLogs = setLogs,
            )

            if (payload.deviceId.isNotBlank()) {
                deviceIdStore.deviceId = payload.deviceId
            }

            // Backup v1/v2 não fala de perfil (profile == null): o perfil local
            // continua. Só um v3 que declara "não informado" limpa o campo.
            payload.profile?.let { profile ->
                profileStore.bodyWeightKg = profile.bodyWeightKg
                profileStore.heightCm = profile.heightCm
                profileStore.sex = profile.sex
            }
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
        kind = plan.kind,
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
        modality = day.modality,
        weekday = day.weekday,
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
                    metricType = exercise.metricType,
                    targetDurationSeconds = exercise.targetDurationSeconds,
                    targetDistanceMeters = exercise.targetDistanceMeters,
                    targetPaceSecondsPerKm = exercise.targetPaceSecondsPerKm,
                    targetHrZone = exercise.targetHrZone,
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
        modality = session.modality,
        source = session.source,
        title = session.title,
        totalDistanceMeters = session.totalDistanceMeters,
        poolLengthMeters = session.poolLengthMeters,
        stroke = session.stroke,
        sessionRpe = session.sessionRpe,
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
