package com.treinos.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val PATH_WORKOUT_STATE = "/treinos/workout-state"
const val PATH_COMMAND = "/treinos/command"

// H4-16: paths novos do modo standalone — aditivos; celular antigo ignora o
// path novo (o listener só trata PATH_COMMAND) e relógio antigo não tem
// listener do snapshot. protocolVersion continua 1.
const val PATH_SESSION_SNAPSHOT = "/treinos/session-snapshot"
const val PATH_SYNC_BATCH = "/treinos/sync-batch"

@Serializable
data class WearWorkoutState(
    @SerialName("protocolVersion") val protocolVersion: Int = 1,
    @SerialName("sessionId") val sessionId: Long,
    @SerialName("dayName") val dayName: String,
    @SerialName("modality") val modality: String = "strength",
    @SerialName("exerciseIndex") val exerciseIndex: Int,
    @SerialName("exerciseCount") val exerciseCount: Int,
    @SerialName("exerciseName") val exerciseName: String,
    @SerialName("setNumber") val setNumber: Int,
    @SerialName("plannedSets") val plannedSets: Int,
    @SerialName("targetReps") val targetReps: String,
    @SerialName("lastWeightKg") val lastWeightKg: Double? = null,
    @SerialName("lastReps") val lastReps: Int? = null,
    @SerialName("restSeconds") val restSeconds: Int = 90,
    @SerialName("restRemainingSeconds") val restRemainingSeconds: Int? = null,
    @SerialName("nextExerciseName") val nextExerciseName: String? = null,
    @SerialName("status") val status: String,
    @SerialName("updatedAt") val updatedAt: Long,
)

@Serializable
data class WearCommand(
    @SerialName("protocolVersion") val protocolVersion: Int = 1,
    @SerialName("commandId") val commandId: String,
    @SerialName("sessionId") val sessionId: Long,
    @SerialName("kind") val kind: WearCommandKind,
    @SerialName("reps") val reps: Int? = null,
    @SerialName("weightKg") val weightKg: Double? = null,
    @SerialName("rpe") val rpe: Double? = null,
)

@Serializable
enum class WearCommandKind {
    LOG_SET,
    SKIP_REST,
    FINISH_WORKOUT,
    REQUEST_STATE,
    START_TODAY,
}

/**
 * H4-16: snapshot da SESSÃO do dia (não do plano inteiro) publicado pelo
 * celular em `PATH_SESSION_SNAPSHOT` — o relógio guarda no Room próprio e
 * consegue treinar sem o celular.
 */
@Serializable
data class WearSessionSnapshot(
    @SerialName("protocolVersion") val protocolVersion: Int = 1,
    @SerialName("sessionId") val sessionId: Long,
    @SerialName("dayName") val dayName: String,
    @SerialName("modality") val modality: String = "strength",
    @SerialName("startedAt") val startedAt: Long,
    @SerialName("exercises") val exercises: List<WearSessionExercise> = emptyList(),
)

/** Exercício do snapshot, na ordem de execução do dia. */
@Serializable
data class WearSessionExercise(
    @SerialName("exerciseIndex") val exerciseIndex: Int,
    @SerialName("sessionExerciseId") val sessionExerciseId: Long,
    @SerialName("exerciseName") val exerciseName: String,
    @SerialName("plannedSets") val plannedSets: Int,
    @SerialName("targetReps") val targetReps: String = "",
    @SerialName("restSeconds") val restSeconds: Int = 90,
)

/**
 * H4-16: lote de séries registradas offline no relógio, enviado em
 * `PATH_SYNC_BATCH` quando os dois se reconectam. `clientSetId` é UUID gerado
 * no relógio — reenviar o lote não duplica (dedupe persistido no celular).
 */
@Serializable
data class WearSyncBatch(
    @SerialName("protocolVersion") val protocolVersion: Int = 1,
    @SerialName("sessionId") val sessionId: Long,
    @SerialName("batchId") val batchId: String,
    @SerialName("finished") val finished: Boolean = false,
    @SerialName("sets") val sets: List<WearLoggedSet> = emptyList(),
    @SerialName("cursor") val cursor: String = "",
)

/** Série registrada offline no relógio. */
@Serializable
data class WearLoggedSet(
    @SerialName("clientSetId") val clientSetId: String,
    @SerialName("sessionExerciseId") val sessionExerciseId: Long,
    @SerialName("exerciseIndex") val exerciseIndex: Int,
    @SerialName("setNumber") val setNumber: Int,
    @SerialName("reps") val reps: Int? = null,
    @SerialName("weightKg") val weightKg: Double? = null,
    @SerialName("rpe") val rpe: Double? = null,
    @SerialName("loggedAt") val loggedAt: Long,
    @SerialName("originDeviceId") val originDeviceId: String = "",
    @SerialName("revisionId") val revisionId: String = "",
    @SerialName("deleted") val deleted: Boolean = false,
)
