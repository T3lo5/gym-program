package com.treinos.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** `source` de uma sessão registrada avulsa, sem plano nem dia (H2-5). */
const val MANUAL_SESSION_SOURCE = "manual"

@Entity(tableName = "workout_plans")
data class WorkoutPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverPlanId: Long? = null,
    val name: String,
    val description: String = "",
    val goal: String = "hypertrophy",
    val daysPerWeek: Int = 4,
    val isActive: Boolean = true,
    val generatedByAi: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Int = 1,
    val nextReviewAt: String? = null,
    val reviewAfterWeeks: Int = 4,
    val source: String = "local",
    @ColumnInfo(defaultValue = "main")
    val kind: String = "main",
)

@Entity(
    tableName = "workout_days",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("planId")],
)
data class WorkoutDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val name: String,
    val dayOrder: Int = 0,
    val notes: String = "",
    val isRestDay: Boolean = false,
    @ColumnInfo(defaultValue = "strength")
    val modality: String = "strength",
    val weekday: Int? = null,
)

@Entity(
    tableName = "workout_plan_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["dayId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("dayId")],
)
data class WorkoutPlanExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val exerciseId: String,
    val exerciseName: String = "",
    val primaryMuscles: String = "",
    val equipment: String = "",
    val exerciseOrder: Int = 0,
    val sets: Int = 3,
    val targetReps: String = "8-12",
    val targetRpe: Double? = null,
    /**
     * H4-12: carga-alvo aplicada pelo usuário com um toque a partir da
     * sugestão do `ProgressionEngine`. `null` = sem alvo definido (o plano
     * não muda sozinho — aplicar é sempre opt-in e explícito).
     */
    val targetWeightKg: Double? = null,
    val restSeconds: Int = 90,
    val notes: String = "",
    val isSupersetWithPrevious: Boolean = false,
    @ColumnInfo(defaultValue = "reps_weight")
    val metricType: String = "reps_weight",
    val targetDurationSeconds: Int? = null,
    val targetDistanceMeters: Double? = null,
    val targetPaceSecondsPerKm: Int? = null,
    val targetHrZone: String? = null,
    // H3-24: mídia embutida no plano, persistida para sobreviver ao reinício.
    // `instructions` guarda os passos separados por '\n'; `images`, os paths
    // relativos do catálogo separados por ','.
    @ColumnInfo(defaultValue = "")
    val instructions: String = "",
    @ColumnInfo(defaultValue = "")
    val images: String = "",
)

@Entity(
    tableName = "workout_sessions",
    indices = [
        Index("startedAt"),
        // H4-7: chave de deduplicação de importação; UNIQUE com NULLs distintos
        // (SQLite), então as sessões criadas no app (todas `null`) não colidem.
        Index(value = ["importRef"], unique = true),
    ],
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long = 0,
    val dayId: Long = 0,
    val planName: String = "",
    val dayName: String = "",
    val status: String = "in_progress",
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val totalDurationSeconds: Long = 0,
    val notes: String = "",
    @ColumnInfo(defaultValue = "strength")
    val modality: String = "strength",
    @ColumnInfo(defaultValue = "plan")
    val source: String = "plan",
    @ColumnInfo(defaultValue = "")
    val title: String = "",
    val totalDistanceMeters: Double? = null,
    val poolLengthMeters: Int? = null,
    val stroke: String? = null,
    val sessionRpe: Double? = null,
    /**
     * H4-7: `"<formato>:<yyyy-MM-dd>:<título normalizado>"` para sessão vinda
     * de importação de CSV (Strong/Hevy); `null` para sessão criada no app.
     * Reimportar o mesmo arquivo não duplica sessão.
     */
    val importRef: String? = null,
    val routePolyline: String? = null,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
)

@Entity(
    tableName = "session_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("sessionId")],
)
data class SessionExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    val exerciseName: String = "",
    val exerciseOrder: Int = 0,
    val plannedSets: Int = 3,
    val completedSets: Int = 0,
    val restSeconds: Int = 90,
    @ColumnInfo(defaultValue = "")
    val targetReps: String = "",
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
)

/** Tipos de série (H4-4): a intenção é por série, não por exercício. */
const val SET_TYPE_NORMAL = "normal"
const val SET_TYPE_WARMUP = "warmup"
const val SET_TYPE_DROP = "drop"
const val SET_TYPE_AMRAP = "amrap"

/** Whitelist de tipos aceitos no formulário, no backup e no sync. */
val VALID_SET_TYPES = setOf(SET_TYPE_NORMAL, SET_TYPE_WARMUP, SET_TYPE_DROP, SET_TYPE_AMRAP)

/** Tipo desconhecido vira "normal" (H4-4): formulário e sync nunca gravam lixo. */
fun normalizeSetType(value: String): String =
    if (value in VALID_SET_TYPES) value else SET_TYPE_NORMAL

@Entity(
    tableName = "sync_outbox",
    indices = [Index(value = ["createdAt"])],
)
data class SyncOutboxEntity(
    @PrimaryKey val mutationId: String,
    val entity: String,
    val entityId: String,
    val operation: String,
    val updatedAt: Long,
    val payloadJson: String,
    val createdAt: Long,
)

/**
 * Resolve a dupla legada (H4-4): `setType` vence; sem `setType` útil,
 * `isWarmup` marca aquecimento. Nunca deixa os dois divergentes no banco.
 */
fun resolveSetType(setType: String, isWarmup: Boolean): String =
    when {
        setType != SET_TYPE_NORMAL -> normalizeSetType(setType)
        isWarmup -> SET_TYPE_WARMUP
        else -> SET_TYPE_NORMAL
    }

@Entity(
    tableName = "set_logs",
    foreignKeys = [
        ForeignKey(
            entity = SessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseLogId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("exerciseLogId")],
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseLogId: Long,
    val setNumber: Int,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val rpe: Double? = null,
    val durationSeconds: Long? = null,
    val distanceMeters: Double? = null,
    val isCompleted: Boolean = true,
    val isWarmup: Boolean = false,
    /** H4-4: intenção da série; `isWarmup` é derivado na escrita. */
    @ColumnInfo(defaultValue = "normal")
    val setType: String = SET_TYPE_NORMAL,
    /** H4-4: repetições em reserva (0..10); `null` = esforço não informado. */
    val rir: Int? = null,
    val restTakenSeconds: Int? = null,
    val completedAt: Long? = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "")
    val clientSetId: String? = null,
    val revisionAt: Long? = null,
    @ColumnInfo(defaultValue = "")
    val revisionOrigin: String = "",
    @ColumnInfo(defaultValue = "")
    val revisionId: String = "",
)

/** Fonte de uma medida corporal: registrada no app (H4-8) ou importada do Health Connect (H4-9). */
const val MEASUREMENT_SOURCE_MANUAL = "manual"
const val MEASUREMENT_SOURCE_HEALTH_CONNECT = "health_connect"

/**
 * H4-8: medida corporal pontual (peso, gordura, circunferências) com foto de
 * progresso opcional. Tabela aditiva; `measuredAt` é informado pelo usuário
 * (permite registrar ontem) e é indexado para a tendência cronológica.
 *
 * `photoPath` é o caminho **absoluto** do arquivo em `filesDir/progress-photos/`;
 * o backup JSON grava o caminho **relativo** a `filesDir` (a foto nunca sai do
 * aparelho — ver `MeasurementPhotoStore` e as rules de backup).
 */
@Entity(tableName = "body_measurements", indices = [Index("measuredAt")])
data class BodyMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch millis da medição; informado pelo usuário (permite registrar ontem). */
    val measuredAt: Long,
    val weightKg: Double? = null,
    val bodyFatPercent: Double? = null,
    val waistCm: Double? = null,
    val chestCm: Double? = null,
    val armCm: Double? = null,
    val thighCm: Double? = null,
    val hipCm: Double? = null,
    /** Caminho absoluto do arquivo em filesDir/progress-photos/; null = sem foto. */
    val photoPath: String? = null,
    val notes: String = "",
    /** "manual" (esta spec) | "health_connect" (H4-9). */
    val source: String = MEASUREMENT_SOURCE_MANUAL,
)

data class WorkoutPlanWithDetails(
    val plan: WorkoutPlanEntity,
    val days: List<WorkoutDayWithExercises>,
)

data class WorkoutDayWithExercises(
    val day: WorkoutDayEntity,
    val exercises: List<WorkoutPlanExerciseEntity>,
)

data class SessionWithExercises(
    val session: WorkoutSessionEntity,
    val exercises: List<SessionExerciseWithSets>,
)

data class SessionExerciseWithSets(
    val exercise: SessionExerciseEntity,
    val sets: List<SetLogEntity>,
)

/** Evento de uso de um exercício (H3-42): concluído/pulado/trocado pelo usuário. */
@Entity(tableName = "exercise_usage_events")
data class ExerciseUsageEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: String,
    val outcome: String, // "completed" | "skipped" | "swapped"
    val occurredAt: Long,
    val planId: Long?,
    val dayId: Long?,
    val source: String, // "generation" | "manual" | "swap"
)
