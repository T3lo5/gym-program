package com.treinos.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val PATH_WORKOUT_STATE = "/treinos/workout-state"
const val PATH_COMMAND = "/treinos/command"

@Serializable
data class WearWorkoutState(
    @SerialName("protocolVersion") val protocolVersion: Int = 1,
    @SerialName("sessionId") val sessionId: Long,
    @SerialName("dayName") val dayName: String,
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
}
