package com.treinos.wear.data

import android.content.Context
import com.treinos.protocol.WearLoggedSet
import com.treinos.protocol.WearSessionExercise
import com.treinos.protocol.WearSessionSnapshot
import com.treinos.protocol.WearSyncBatch
import java.util.UUID

/**
 * H4-16: fachada do Room do relógio para o modo standalone — salvar o
 * snapshot da sessão do dia, ler a sessão ativa, gravar série local pendente,
 * listar pendentes e marcar como sincronizadas. Cap de segurança: acima de
 * 200 séries pendentes, a mais antiga sai (a fila da H3-20 continua
 * separada, com cap 20).
 */
class WearStandaloneStore(
    private val dao: WearStandaloneDao,
) {

    /** Snapshot da mesma `sessionId` substitui o anterior e preserva as séries locais ainda pendentes. */
    suspend fun saveSnapshot(snapshot: WearSessionSnapshot) {
        val pendentes = dao.pendingSets()
            .filter { it.sessionId == snapshot.sessionId }
        dao.deleteExercisesOfSession(snapshot.sessionId)
        dao.deleteSession(snapshot.sessionId)
        dao.insertSession(
            WearSessionEntity(
                id = snapshot.sessionId,
                dayName = snapshot.dayName,
                modality = snapshot.modality,
                startedAt = snapshot.startedAt,
                status = "in_progress",
            ),
        )
        dao.insertExercises(
            snapshot.exercises.map { ex ->
                WearSessionExerciseEntity(
                    sessionId = snapshot.sessionId,
                    sessionExerciseId = ex.sessionExerciseId,
                    exerciseIndex = ex.exerciseIndex,
                    exerciseName = ex.exerciseName,
                    plannedSets = ex.plannedSets,
                    targetReps = ex.targetReps,
                    restSeconds = ex.restSeconds,
                )
            },
        )
        // As séries pendentes desta sessão continuam guardadas (o snapshot
        // novo não apaga o que o usuário registrou offline).
        dao.insertSetLogs(pendentes)
    }

    suspend fun activeSession(): WearSessionEntity? = dao.latestSession()

    /**
     * Reconstrói o snapshot da sessão ativa a partir do Room (relógio
     * reiniciado no meio da sessão offline: exercício/série/restEndsAt vêm do
     * Room e o descanso é recalculado de `restEndsAt - now`).
     */
    suspend fun loadSnapshot(): WearSessionSnapshot? {
        val sessao = dao.latestSession() ?: return null
        val exercicios = dao.exercisesForSession(sessao.id)
        return WearSessionSnapshot(
            sessionId = sessao.id,
            dayName = sessao.dayName,
            modality = sessao.modality,
            startedAt = sessao.startedAt,
            exercises = exercicios.map {
                WearSessionExercise(
                    exerciseIndex = it.exerciseIndex,
                    sessionExerciseId = it.sessionExerciseId,
                    exerciseName = it.exerciseName,
                    plannedSets = it.plannedSets,
                    targetReps = it.targetReps,
                    restSeconds = it.restSeconds,
                )
            },
        )
    }

    /** Persiste o deadline do descanso (sobrevive a reinício do relógio). */
    suspend fun saveRestEndsAt(sessionId: Long, restEndsAt: Long?) {
        dao.updateRestEndsAt(sessionId, restEndsAt)
    }

    /** Finaliza a sessão no Room; o lote com `finished = true` vai ao reconectar. */
    suspend fun finishSession(sessionId: Long) {
        dao.updateStatus(sessionId, "finished")
    }

    suspend fun exercisesOfSession(sessionId: Long): List<WearSessionExerciseEntity> =
        dao.exercisesForSession(sessionId)

    suspend fun setsOfSession(sessionId: Long): List<WearSetLogEntity> =
        dao.setsForSession(sessionId)

    suspend fun pendingCount(): Int = dao.pendingCount()

    /** Grava a série local; devolve o `clientSetId` gerado (UUID do momento do log). */
    suspend fun logSetLocal(
        sessionId: Long,
        sessionExerciseId: Long,
        exerciseIndex: Int,
        setNumber: Int,
        reps: Int?,
        weightKg: Double?,
        rpe: Double?,
        loggedAt: Long,
    ): String {
        val clientSetId = UUID.randomUUID().toString()
        dao.insertSetLog(
            WearSetLogEntity(
                clientSetId = clientSetId,
                sessionId = sessionId,
                sessionExerciseId = sessionExerciseId,
                exerciseIndex = exerciseIndex,
                setNumber = setNumber,
                reps = reps,
                weightKg = weightKg,
                rpe = rpe,
                loggedAt = loggedAt,
                pending = true,
                originDeviceId = "wear",
                revisionId = "$loggedAt|$clientSetId",
                deleted = false,
            ),
        )
        aplicarCapDePendentes()
        return clientSetId
    }

    suspend fun pendingSets(): List<WearSetLogEntity> = dao.pendingSets()

    /** Lote de sincronização com tudo que está pendente (qualquer sessão). */
    suspend fun buildSyncBatch(batchId: String, sessionId: Long, finished: Boolean): WearSyncBatch =
        WearSyncBatch(
            sessionId = sessionId,
            batchId = batchId,
            finished = finished,
            cursor = batchId,
            sets = dao.pendingSets().map { it.toLoggedSet() },
        )

    /** Depois que o celular confirma o envio (≥ 1 node OK), `pending = false`. */
    suspend fun markSynced(clientSetIds: List<String>) {
        if (clientSetIds.isNotEmpty()) dao.markSynced(clientSetIds)
    }

    /** Lote de sessão já finalizada no celular: o relógio limpa as pendentes dela. */
    suspend fun clearPendingOfSession(sessionId: Long) {
        dao.deleteSetsOfSession(sessionId)
    }

    /** Cap de segurança: acima de 200 pendentes, a mais antiga sai. */
    private suspend fun aplicarCapDePendentes() {
        val pendentes = dao.pendingSets()
        if (pendentes.size <= MAX_PENDING_SETS) return
        pendentes.take(pendentes.size - MAX_PENDING_SETS).forEach { excedente ->
            dao.deleteSetLog(excedente.id)
        }
    }

    private suspend fun WearStandaloneDao.insertSetLogs(sets: List<WearSetLogEntity>) {
        sets.forEach { insertSetLog(it) }
    }

    private fun WearSetLogEntity.toLoggedSet(): WearLoggedSet =
        WearLoggedSet(
            clientSetId = clientSetId,
            sessionExerciseId = sessionExerciseId,
            exerciseIndex = exerciseIndex,
            setNumber = setNumber,
            reps = reps,
            weightKg = weightKg,
            rpe = rpe,
            loggedAt = loggedAt,
            originDeviceId = originDeviceId,
            revisionId = revisionId,
            deleted = deleted,
        )

    companion object {
        const val MAX_PENDING_SETS = 200

        fun from(context: Context): WearStandaloneStore =
            WearStandaloneStore(WearDatabase.build(context).standaloneDao())
    }
}
