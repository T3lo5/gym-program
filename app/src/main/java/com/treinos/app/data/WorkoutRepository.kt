package com.treinos.app.data

import com.treinos.app.data.local.ExerciseUsageEvent
import com.treinos.app.data.local.BodyMeasurementEntity
import com.treinos.app.data.local.MANUAL_SESSION_SOURCE
import com.treinos.app.data.local.SET_TYPE_NORMAL
import com.treinos.app.data.local.SET_TYPE_WARMUP
import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SessionExerciseWithSets
import com.treinos.app.data.local.SessionWithExercises
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.SyncOutboxEntity
import com.treinos.app.data.local.WorkoutDao
import com.treinos.app.data.local.WorkoutDayEntity
import com.treinos.app.data.local.WorkoutDayWithExercises
import com.treinos.app.data.local.WorkoutPlanEntity
import com.treinos.app.data.local.WorkoutPlanExerciseEntity
import com.treinos.app.data.local.WorkoutPlanWithDetails
import com.treinos.app.data.local.WorkoutSessionEntity
import com.treinos.app.data.local.normalizeSetType
import com.treinos.app.data.local.resolveSetType
import com.treinos.app.data.media.ExerciseMedia
import com.treinos.app.data.media.rawImagePathFromUrl
import com.treinos.app.data.prefs.AppPreferences
import com.treinos.app.data.remote.BackendApi
import com.treinos.app.data.remote.BackendHttpException
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
import com.treinos.app.data.csv.CsvImportPlan
import com.treinos.app.data.csv.CsvImportResult
import com.treinos.app.data.csv.WorkoutCsvExporter
import com.treinos.app.data.csv.csvExerciseSlug
import com.treinos.app.data.health.HEALTH_CONNECT_MEASUREMENT_SOURCE
import com.treinos.app.data.health.HealthConnectGateway
import com.treinos.app.data.health.HealthWeightSample
import com.treinos.app.data.health.toHealthSessionDraft
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
import com.treinos.app.data.remote.SyncChangeDto
import com.treinos.app.data.remote.SyncChangesRequestDto
import com.treinos.app.data.remote.SyncChangesResponseDto
import com.treinos.app.data.remote.SyncMutationDto
import com.treinos.app.data.remote.BackendDiagnosticsDto
import com.treinos.app.data.remote.SyncSpaceCreateDto
import com.treinos.app.data.remote.SyncSpaceJoinDto
import com.treinos.app.data.remote.SyncSpaceRevokeDto
import com.treinos.app.data.remote.SyncSessionDto
import com.treinos.app.data.remote.SyncSessionExerciseDto
import com.treinos.app.data.remote.SyncSetLogDto
import com.treinos.app.ui.progression.REVIEW_POSTPONE_WEEKS
import com.treinos.app.ui.progression.ProgressionSuggestion
import com.treinos.app.ui.progression.applyProgressionToPlanExercise as applyProgressionSuggestionToEntity
import com.treinos.app.ui.progression.postponeReviewDate
import com.treinos.app.ui.normalizeNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom
import java.util.UUID
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

/** Resultado de aplicar a sugestão de progressão ao plano (H4-12). */
sealed interface ProgressionApplyResult {
    /** Alvo gravado no plano (a carga aplicada ou a faixa de reps nova). */
    data class Applied(val targetWeightKg: Double?, val targetReps: String) : ProgressionApplyResult
    /** Sugestão sem valor concreto (`NONE` ou ação sem carga/reps). */
    data object NothingToApply : ProgressionApplyResult
    /** Dia/ordem/exercício não casam: nada é gravado. */
    data object ExerciseNotInPlan : ProgressionApplyResult
}

open class WorkoutRepository(
    private val dao: WorkoutDao,
    private val backendClient: BackendApi,
    private val preferences: AppPreferences,
    /**
     * H4-8: `filesDir` do aparelho, para converter o caminho da foto de
     * progresso entre o banco (absoluto) e o backup JSON (relativo a `filesDir`).
     * `null` (testes sem filesystem) mantém o caminho como está.
     */
    private val filesDir: File? = null,
    /**
     * H4-9: gateway do Health Connect, opcional — `null` quando o Health
     * Connect não está disponível no aparelho; todo o resto do app funciona
     * exatamente como antes.
     */
    private val healthGateway: HealthConnectGateway? = null,
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

    // ---------------- Medidas corporais (H4-8) ----------------

    /** Lista por `measuredAt` desc (ordem de exibição); o gráfico reordena asc. */
    fun observeMeasurements(): Flow<List<BodyMeasurementEntity>> = dao.observeMeasurements()

    /** Grava uma medida já validada ([MeasurementValidator]). */
    suspend fun addMeasurement(measurement: BodyMeasurementEntity): Long =
        withContext(Dispatchers.IO) { dao.insertMeasurement(measurement) }

    /** Exclui a medida pelo id; o arquivo da foto é apagado por quem chama. */
    suspend fun deleteMeasurement(id: Long): Unit = withContext(Dispatchers.IO) {
        dao.deleteMeasurement(id)
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
                            targetWeightKg = exercise.targetWeightKg,
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
                    // Mídia do substituto (bug relatado): sem isto, o slot do
                    // plano continuava com as instruções/imagem do exercício
                    // ANTIGO e o detalhe do substituto mostrava conteúdo errado.
                    // O plano guarda passos '\n' e paths crus separados por ','.
                    instructions = replacement.instructions.joinToString("\n"),
                    images = rawImagePathFromUrl(replacement.imageUrl).orEmpty(),
                    // A carga-alvo era do exercício antigo e não é transferível:
                    // 60 kg de supino não valem para o substituto.
                    targetWeightKg = null,
                )
                dao.updatePlanExercise(updated)
                SwapResult.Ok(exerciseId = replacement.id, exerciseName = replacement.name)
            }
        }
    }

    // ---------------- Superset (H4-6) ----------------

    /**
     * Grava a flag de superset do exercício no plano: sem coluna em
     * `session_exercises`, a alternância vale para a sessão atual e para os
     * próximos treinos do dia. Backup e sync já carregam o campo.
     */
    suspend fun setSupersetWithPrevious(planExerciseId: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        dao.updateSupersetFlag(planExerciseId, enabled)
    }

    // ---------------- Notas do treino (H4-11) ----------------

    /**
     * Grava a nota da sessão (`workout_sessions.notes`). O chamador envia o
     * texto já normalizado; o trim aqui é defesa em profundidade — string
     * vazia limpa a nota.
     */
    suspend fun updateSessionNotes(sessionId: Long, notes: String) = withContext(Dispatchers.IO) {
        val session = dao.getSession(sessionId)
            ?: error("Sessão não encontrada no aparelho.")
        dao.updateSession(session.copy(notes = normalizeNote(notes)))
    }

    /**
     * Grava a nota do exercício no PLANO do dia (`workout_plan_exercises.notes`),
     * casado por `exerciseOrder` — o `exerciseOrder` da sessão é copiado do
     * plano no startSession e não muda na troca de escopo THIS_SESSION (H3-41).
     * Por ser do plano, a nota reaparece nas próximas sessões do mesmo dia.
     * Dia/exercício não encontrado ⇒ erro e nada é gravado.
     */
    suspend fun updatePlanExerciseNotes(dayId: Long, exerciseOrder: Int, notes: String) =
        withContext(Dispatchers.IO) {
            val exercise = dao.getExercisesForDay(dayId)
                .firstOrNull { it.exerciseOrder == exerciseOrder }
                ?: error("Exercício não encontrado no plano do dia.")
            dao.updatePlanExercise(exercise.copy(notes = normalizeNote(notes)))
        }

    // ---------------- Progressão aplicada ao plano (H4-12) ----------------

    /**
     * Aplica a sugestão do `ProgressionEngine` ao exercício do plano com um
     * toque. O casamento é por `exerciseOrder` **e** `exerciseId` no dia —
     * um exercício trocado só nesta sessão (H3-41) não casa e nada é gravado.
     * Idempotente: a mesma sugestão grava o mesmo valor. Não mexe em `sets`,
     * `restSeconds`, `targetRpe` nem em `nextReviewAt`.
     */
    suspend fun applyProgressionToPlanExercise(
        dayId: Long,
        exerciseOrder: Int,
        exerciseId: String,
        suggestion: ProgressionSuggestion,
    ): ProgressionApplyResult = withContext(Dispatchers.IO) {
        val planExercise = dao.getExercisesForDay(dayId)
            .firstOrNull { it.exerciseOrder == exerciseOrder }
            ?: return@withContext ProgressionApplyResult.ExerciseNotInPlan
        if (planExercise.exerciseId != exerciseId) {
            return@withContext ProgressionApplyResult.ExerciseNotInPlan
        }
        val updated = applyProgressionSuggestionToEntity(planExercise, suggestion)
            ?: return@withContext ProgressionApplyResult.NothingToApply
        dao.updatePlanExercise(updated)
        ProgressionApplyResult.Applied(
            targetWeightKg = updated.targetWeightKg,
            targetReps = updated.targetReps,
        )
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

    /**
     * Registra uma série (H4-4): o tipo (`normal`/`warmup`/`drop`/`amrap`)
     * deriva `isWarmup` na escrita, e só série de trabalho incrementa
     * `completedSets` — aquecimento não "completa" o exercício.
     */
    suspend fun logSet(
        exerciseLogId: Long,
        reps: Int?,
        weightKg: Double?,
        rpe: Double?,
        restTakenSeconds: Int? = null,
        setType: String = SET_TYPE_NORMAL,
        rir: Int? = null,
    ): Int = withContext(Dispatchers.IO) {
        val type = normalizeSetType(setType)
        val isWarmup = type == SET_TYPE_WARMUP
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
                isWarmup = isWarmup,
                setType = type,
                rir = rir,
                restTakenSeconds = restTakenSeconds,
                completedAt = now,
            )
        )
        if (!isWarmup) {
            dao.incrementCompletedSets(exerciseLogId)
        }
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
     * reps/carga/RPE/tipo/RIR (H4-4). Mudar o tipo recalcula `completedSets`:
     * virar warm-up tira a série do progresso; voltar a ser de trabalho
     * devolve ela ao progresso.
     */
    suspend fun updateSet(set: SetLogEntity) = withContext(Dispatchers.IO) {
        val existing = dao.getSetLog(set.id)
            ?: error("Série não encontrada no aparelho.")
        val type = normalizeSetType(set.setType)
        val updated = set.copy(
            exerciseLogId = existing.exerciseLogId,
            setNumber = existing.setNumber,
            setType = type,
            isWarmup = type == SET_TYPE_WARMUP,
        )
        dao.updateSetLog(updated)
        if (existing.isWarmup != updated.isWarmup) {
            dao.recalculateCompletedSets(updated.exerciseLogId)
        }
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
     * H3-54: edita uma série de sessão já concluída, direto do Histórico.
     * Mesmo contrato do [updateSet] (H3-27) — vínculo com o exercício e
     * `setNumber` preservados — mas com guarda explícita: só sessão
     * `completed` é editável por esse caminho; a sessão em andamento continua
     * sendo editada pela tela de treino.
     */
    suspend fun updateHistorySet(set: SetLogEntity) = withContext(Dispatchers.IO) {
        val existing = dao.getSetLog(set.id)
            ?: error("Série não encontrada no aparelho.")
        requireCompletedSessionForHistory(existing.exerciseLogId)
        val type = normalizeSetType(set.setType)
        val updated = set.copy(
            exerciseLogId = existing.exerciseLogId,
            setNumber = existing.setNumber,
            setType = type,
            isWarmup = type == SET_TYPE_WARMUP,
        )
        dao.updateSetLog(updated)
        if (existing.isWarmup != updated.isWarmup) {
            dao.recalculateCompletedSets(updated.exerciseLogId)
        }
    }

    /**
     * H3-54: exclui uma série de sessão concluída pelo Histórico. Realinha o
     * contador de séries concluídas e, se o exercício ficar sem séries, a
     * entrada dele sai da sessão (o card passa a listar só o que tem série).
     */
    suspend fun deleteHistorySet(setId: Long) = withContext(Dispatchers.IO) {
        val existing = dao.getSetLog(setId) ?: return@withContext
        requireCompletedSessionForHistory(existing.exerciseLogId)
        dao.deleteSetLog(setId)
        dao.recalculateCompletedSets(existing.exerciseLogId)
        if (dao.maxSetNumber(existing.exerciseLogId) == 0) {
            dao.deleteSessionExercise(existing.exerciseLogId)
        }
    }

    /** H3-54: guarda do caminho do Histórico — apenas sessão `completed`. */
    private suspend fun requireCompletedSessionForHistory(exerciseLogId: Long) {
        val exercise = dao.getSessionExercise(exerciseLogId)
            ?: error("Exercício não encontrado na sessão.")
        val session = dao.getSession(exercise.sessionId)
            ?: error("Sessão não encontrada.")
        if (session.status != "completed") {
            error("Só sessões concluídas podem ser editadas pelo Histórico.")
        }
    }

    /**
     * Exclui a sessão inteira ("Abandonar treino", H3-27). Exercícios e séries
     * caem por CASCADE; o histórico das demais sessões fica intacto.
     */
    suspend fun deleteSession(sessionId: Long) = withContext(Dispatchers.IO) {
        dao.deleteSession(sessionId)
    }

    // ---------------- Health Connect (H4-9) ----------------

    /**
     * Envia uma sessão concluída ao Health Connect. Best-effort: sem gateway,
     * sessão inexistente, sessão que não pode ser escrita (em andamento, sem
     * `endedAt`, duração zero — regras 4 e 5) ou sem permissão (regra 2) o
     * método simplesmente não escreve; falhas de infraestrutura propagam para
     * o ViewModel, que nunca bloqueia o fim do treino por causa delas (regra
     * 8). Reescrever a mesma sessão atualiza o registro em vez de duplicar
     * (`clientRecordId` estável, regra 7).
     */
    suspend fun writeCompletedSessionToHealth(sessionId: Long) = withContext(Dispatchers.IO) {
        val gateway = healthGateway ?: return@withContext
        val session = dao.getSession(sessionId) ?: return@withContext
        val draft = toHealthSessionDraft(session) ?: return@withContext
        gateway.writeSession(draft)
    }

    /**
     * Lê o peso do Health Connect no período `[fromMillis, toMillis]` e grava
     * em `body_measurements` com `source = "health_connect"` (H4-8). Uma
     * leitura cujo `measuredAt` já existe neste source é ignorada — sincronizar
     * duas vezes não duplica (regra 9). Devolve as amostras válidas do período
     * (ordenadas), para a UI mostrar a mais recente.
     */
    suspend fun syncHealthWeight(fromMillis: Long, toMillis: Long): List<HealthWeightSample> =
        withContext(Dispatchers.IO) {
            val gateway = healthGateway ?: return@withContext emptyList()
            val samples = gateway.readWeight(fromMillis, toMillis)
            val existing = dao.getAllMeasurements()
                .filter { it.source == HEALTH_CONNECT_MEASUREMENT_SOURCE }
                .mapTo(mutableSetOf()) { it.measuredAt }
            for (sample in samples) {
                if (sample.timeMillis in existing) continue
                dao.insertMeasurement(
                    BodyMeasurementEntity(
                        measuredAt = sample.timeMillis,
                        weightKg = sample.weightKg,
                        source = HEALTH_CONNECT_MEASUREMENT_SOURCE,
                    )
                )
                existing.add(sample.timeMillis)
            }
            samples
        }

    /**
     * Lê o total de passos do Health Connect no período. Não cria entidade no
     * Room — o valor só é exibido (spec H4-9). `null` = sem dados no período.
     */
    suspend fun syncHealthSteps(fromMillis: Long, toMillis: Long): Long? =
        withContext(Dispatchers.IO) {
            val gateway = healthGateway ?: return@withContext null
            gateway.readSteps(fromMillis, toMillis)
                .sumOf { it.count }
                .takeIf { it > 0 }
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
        enqueueSyncOutbox(payload)
        val pending = dao.getSyncOutbox(500)
        val request = SyncChangesRequestDto(
            deviceId = preferences.deviceId,
            syncedAt = Instant.now().toString(),
            baseCursor = preferences.syncCursor,
            mutations = pending.map { item ->
                SyncMutationDto(
                    mutationId = item.mutationId,
                    entity = item.entity,
                    entityId = item.entityId,
                    operation = item.operation,
                    updatedAt = item.updatedAt,
                    payload = item.payloadJson,
                )
            },
        )
        val response = try {
            backendClient.syncChanges(request)
        } catch (error: BackendHttpException) {
            if (error.code in setOf(404, 405)) {
                val legacy = backendClient.sync(payload)
                dao.clearSyncOutbox()
                preferences.lastSyncAt = legacy.syncedAt
                preferences.lastSyncedPlanIds = payload.plans.map { it.localId.toString() }.toSet()
                preferences.lastSyncedSessionIds = payload.sessions.map { it.localId.toString() }.toSet()
                return legacy
            }
            throw error
        }

        val completed = (response.accepted + response.ignored).distinct()
        if (completed.isNotEmpty()) dao.deleteSyncOutbox(completed)
        preferences.syncCursor = response.cursor
        preferences.lastSyncAt = Instant.now().toString()
        preferences.lastSyncedPlanIds = payload.plans.map { it.localId.toString() }.toSet()
        preferences.lastSyncedSessionIds = payload.sessions.map { it.localId.toString() }.toSet()
        return SyncResponseDto(
            ok = true,
            plans = payload.plans.size,
            sessions = payload.sessions.size,
            setLogs = payload.sessions.sumOf { session ->
                session.exercises.sumOf { exercise -> exercise.sets.size }
            },
            syncedAt = preferences.lastSyncAt.orEmpty(),
        )
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
                // H4-8: o backend não conhece medidas corporais — `null` mantém
                // as medidas locais intactas na substituição do treino.
                measurements = null,
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
                targetWeightKg = dto.targetWeightKg,
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
            // H4-4: normaliza a dupla setType/isWarmup — DTO antigo sem setType
            // mas com isWarmup=true vira 'warmup'; nunca ficam divergentes.
            val setType = resolveSetType(dto.setType, dto.isWarmup)
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
                isWarmup = setType == SET_TYPE_WARMUP,
                setType = setType,
                rir = dto.rir,
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

    /**
     * H4-8: caminho da foto no backup JSON — relativo a `filesDir`
     * (ex.: `progress-photos/2026-11-10-0732.jpg`). Caminho fora de `filesDir`
     * ou `filesDir` ausente (testes) passa como está.
     */
    private fun photoPathForBackup(photoPath: String?): String? {
        if (photoPath.isNullOrBlank() || filesDir == null) return photoPath
        val prefix = filesDir.absolutePath.trimEnd('/') + "/"
        return if (photoPath.startsWith(prefix)) photoPath.removePrefix(prefix) else photoPath
    }

    /**
     * H4-8: remonta o caminho relativo do backup contra o `filesDir` deste
     * aparelho (regra 12). O arquivo pode não existir aqui — a medida continua
     * válida e a UI avisa; o import nunca falha por causa da foto.
     */
    private fun photoPathFromBackup(photoPath: String?): String? {
        if (photoPath.isNullOrBlank()) return null
        val dir = filesDir ?: return photoPath
        return File(dir, photoPath).absolutePath
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
            schemaVersion = 4,
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
            // H4-8: medidas corporais — o caminho da foto vai relativo a
            // `filesDir`; a foto em si nunca entra no backup (regra 8).
            measurements = dao.getAllMeasurements().map { measurement ->
                BackupMapper.toDto(measurement).copy(
                    photoPath = photoPathForBackup(measurement.photoPath),
                )
            },
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
            // H4-8: `measurements` decide o destino das medidas com a semântica
            // do `profile` — `null` (v1/v2/v3) = o arquivo não fala disso e as
            // medidas locais continuam (regra 11); lista presente (inclusive
            // vazia) substitui todas dentro da transação de `replaceAllData`
            // (regra 10). O caminho da foto é remontado contra o `filesDir`
            // deste aparelho; arquivo ausente não falha o import (regra 12).
            val measurements = payload.measurements?.map { dto ->
                BackupMapper.toEntity(dto).copy(
                    photoPath = photoPathFromBackup(dto.photoPath),
                )
            }

            dao.replaceAllData(
                plans = plans,
                days = days,
                planExercises = planExercises,
                sessions = sessions,
                sessionExercises = sessionExercises,
                setLogs = setLogs,
                measurements = measurements,
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

    // ---------------- CSV (H4-7): exportar "Treinos" e importar Strong/Hevy ----------------

    /**
     * Exporta todas as sessões com pelo menos uma série registrada no formato
     * "Treinos" ([WorkoutCsvExporter]); sem sessão, sai só o cabeçalho.
     */
    suspend fun exportSessionsCsv(): String = withContext(Dispatchers.IO) {
        WorkoutCsvExporter.exportSessionsCsv(
            buildSessionDetails(
                dao.getAllSessionEntities(),
                dao.getAllSessionExercises(),
                dao.getAllSetLogs(),
            ),
        )
    }

    /** Sessões do plano que já existem no banco, para a pré-visualização. */
    suspend fun csvDuplicates(plan: CsvImportPlan): Int = withContext(Dispatchers.IO) {
        plan.sessions.count { draft -> dao.getSessionByImportRef(draft.importRef) != null }
    }

    /**
     * Importa o plano de forma **aditiva** (nunca chama `deleteAllData()` /
     * `replaceAllData()`): sessões com `importRef` já presente no banco são
     * contadas em `duplicates` e não reinseridas; as novas entram em uma única
     * transação, então uma falha no meio não deixa sessão parcial nem
     * `importRef` órfão. O índice UNIQUE em `importRef` é a trava final.
     *
     * [deviceId] é a identidade do aparelho que fez o import (mesma convenção
     * do backup); não altera o conteúdo gravado.
     */
    suspend fun importSessionsCsv(plan: CsvImportPlan, deviceId: String): CsvImportResult =
        withContext(Dispatchers.IO) {
            val pending = plan.sessions.filter { draft ->
                dao.getSessionByImportRef(draft.importRef) == null
            }
            val duplicates = plan.sessions.size - pending.size
            var exercisesImported = 0
            var setsImported = 0
            val sessions = mutableListOf<WorkoutSessionEntity>()
            val exercisesBySession = mutableListOf<List<SessionExerciseEntity>>()
            val setsByExercise = mutableListOf<List<List<SetLogEntity>>>()

            pending.forEach { draft ->
                val endedAt = draft.durationSeconds?.let { duration ->
                    draft.startedAt + duration * 1000L
                }
                sessions += WorkoutSessionEntity(
                    planId = 0,
                    dayId = 0,
                    planName = "",
                    dayName = "",
                    status = "completed",
                    startedAt = draft.startedAt,
                    endedAt = endedAt,
                    totalDurationSeconds = draft.durationSeconds ?: 0L,
                    notes = draft.notes,
                    modality = "strength",
                    source = MANUAL_SESSION_SOURCE,
                    title = draft.title,
                    importRef = draft.importRef,
                )
                val exercises = mutableListOf<SessionExerciseEntity>()
                val setsOfExercise = mutableListOf<List<SetLogEntity>>()
                draft.exercises.forEach { exercise ->
                    // `sessionId` e `exerciseLogId` são remapeados dentro da
                    // transação (insertImportedCsvSessions).
                    exercises += SessionExerciseEntity(
                        sessionId = 0,
                        exerciseId = "csv_" + csvExerciseSlug(exercise.exerciseName),
                        exerciseName = exercise.exerciseName,
                        exerciseOrder = exercise.exerciseOrder,
                        plannedSets = exercise.sets.size,
                        completedSets = exercise.sets.count { !it.isWarmup },
                        startedAt = draft.startedAt,
                    )
                    setsOfExercise += exercise.sets.map { set ->
                        SetLogEntity(
                            exerciseLogId = 0,
                            setNumber = set.setNumber,
                            reps = set.reps,
                            weightKg = set.weightKg,
                            rpe = set.rpe,
                            durationSeconds = set.durationSeconds,
                            distanceMeters = set.distanceMeters,
                            isCompleted = true,
                            isWarmup = set.isWarmup,
                            setType = resolveSetType(SET_TYPE_NORMAL, set.isWarmup),
                            completedAt = draft.startedAt,
                        )
                    }
                    exercisesImported++
                    setsImported += exercise.sets.size
                }
                exercisesBySession += exercises
                setsByExercise += setsOfExercise
            }

            dao.insertImportedCsvSessions(
                sessions = sessions,
                exercisesBySession = exercisesBySession,
                setsByExercise = setsByExercise,
            )
            CsvImportResult(
                sessionsImported = sessions.size,
                exercisesImported = exercisesImported,
                setsImported = setsImported,
                duplicates = duplicates,
            )
        }

    suspend fun testConnection() = withContext(Dispatchers.IO) {
        backendClient.health()
    }

    suspend fun getDiagnostics(): BackendDiagnosticsDto =
        withContext(Dispatchers.IO) {
            backendClient.diagnostics()
        }

    suspend fun createSyncSpace(): Pair<String, String> = withContext(Dispatchers.IO) {
        val spaceId = UUID.randomUUID().toString()
        val secretBytes = ByteArray(16)
        SecureRandom().nextBytes(secretBytes)
        val secret = secretBytes.joinToString("") { "%02x".format(it) }
        val response = backendClient.createSyncSpace(
            SyncSpaceCreateDto(syncSpaceId = spaceId, syncSecret = secret),
        )
        if (!response.ok) error("Não foi possível criar o espaço de sync.")
        preferences.syncSpaceId = spaceId
        preferences.syncSecret = secret
        preferences.syncJoined = true
        preferences.lastSyncedPlanIds = emptySet()
        preferences.lastSyncedSessionIds = emptySet()
        preferences.syncCursor = 0
        spaceId to secret
    }

    suspend fun joinSyncSpace(syncSpaceId: String, syncSecret: String) =
        withContext(Dispatchers.IO) {
            val space = syncSpaceId.trim()
            val secret = syncSecret.trim()
            require(space.isNotBlank() && secret.length >= 16) {
                "Informe o código do espaço e um segredo válido."
            }
            val response = backendClient.joinSyncSpace(
                SyncSpaceJoinDto(
                    syncSpaceId = space,
                    syncSecret = secret,
                    deviceId = preferences.deviceId,
                ),
            )
            if (!response.ok) error("Não foi possível entrar no espaço.")
            preferences.syncSpaceId = space
            preferences.syncSecret = secret
            preferences.syncJoined = true
            preferences.syncCursor = 0
        }

    suspend fun revokeSyncSpace() = withContext(Dispatchers.IO) {
        val space = preferences.syncSpaceId ?: error("Nenhum espaço configurado.")
        val secret = preferences.syncSecret ?: error("Nenhum segredo configurado.")
        backendClient.revokeSyncSpace(
            SyncSpaceRevokeDto(syncSpaceId = space, syncSecret = secret),
        )
        preferences.syncSpaceId = null
        preferences.syncSecret = null
        preferences.syncJoined = false
        preferences.syncCursor = 0
    }

    private suspend fun enqueueSyncOutbox(payload: SyncRequestDto) {
        val now = System.currentTimeMillis()
        val encoder = Json { encodeDefaults = true }
        val items = mutableListOf<SyncOutboxEntity>()

        payload.plans.forEach { plan ->
            val payloadJson = encoder.encodeToString(SyncPlanDto.serializer(), plan)
            val mutationId = stableSyncMutationId(
                entity = "plan",
                entityId = plan.localId.toString(),
                operation = "upsert",
                payloadJson = payloadJson,
            )
            items += SyncOutboxEntity(
                mutationId = mutationId,
                entity = "plan",
                entityId = plan.localId.toString(),
                operation = "upsert",
                updatedAt = plan.updatedAt,
                payloadJson = payloadJson,
                createdAt = now,
            )
        }
        payload.sessions.forEach { session ->
            val payloadJson = encoder.encodeToString(SyncSessionDto.serializer(), session)
            val updatedAt = session.endedAt ?: session.startedAt
            val mutationId = stableSyncMutationId(
                entity = "session",
                entityId = session.localId.toString(),
                operation = "upsert",
                payloadJson = payloadJson,
            )
            items += SyncOutboxEntity(
                mutationId = mutationId,
                entity = "session",
                entityId = session.localId.toString(),
                operation = "upsert",
                updatedAt = updatedAt,
                payloadJson = payloadJson,
                createdAt = now,
            )
        }

        val currentPlanIds = payload.plans.map { it.localId.toString() }.toSet()
        (preferences.lastSyncedPlanIds - currentPlanIds).forEach { entityId ->
            val mutationId = stableSyncMutationId("plan", entityId, "delete", "{}")
            items += SyncOutboxEntity(
                mutationId = mutationId,
                entity = "plan",
                entityId = entityId,
                operation = "delete",
                updatedAt = now,
                payloadJson = "{}",
                createdAt = now,
            )
        }

        val currentSessionIds = payload.sessions.map { it.localId.toString() }.toSet()
        (preferences.lastSyncedSessionIds - currentSessionIds).forEach { entityId ->
            val mutationId = stableSyncMutationId("session", entityId, "delete", "{}")
            items += SyncOutboxEntity(
                mutationId = mutationId,
                entity = "session",
                entityId = entityId,
                operation = "delete",
                updatedAt = now,
                payloadJson = "{}",
                createdAt = now,
            )
        }

        if (items.isNotEmpty()) dao.insertSyncOutbox(items)
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
                    targetWeightKg = exercise.targetWeightKg,
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
                            setType = set.setType,
                            rir = set.rir,
                            restTakenSeconds = set.restTakenSeconds,
                            completedAt = set.completedAt,
                        )
                    },
                )
            },
    )
}
