package com.treinos.app.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.util.Locale

/**
 * Permissões de saúde pedidas em runtime (spec H4-9): escrever treino e ler
 * peso e passos. O manifest declara as três; o conjunto pedido ao usuário é
 * este — o diálogo do sistema só pergunta o que ainda falta.
 */
val HEALTH_PERMISSION_WRITE_EXERCISE: String =
    HealthPermission.getWritePermission(ExerciseSessionRecord::class)
val HEALTH_PERMISSION_READ_WEIGHT: String =
    HealthPermission.getReadPermission(WeightRecord::class)
val HEALTH_PERMISSION_READ_STEPS: String =
    HealthPermission.getReadPermission(StepsRecord::class)

val HEALTH_PERMISSIONS: Set<String> = setOf(
    HEALTH_PERMISSION_READ_WEIGHT,
    HEALTH_PERMISSION_READ_STEPS,
    HEALTH_PERMISSION_WRITE_EXERCISE,
)

/** Estado de permissões derivado do conjunto concedido (regra 3). */
fun healthPermissionState(granted: Set<String>): HealthConnectPermissionState =
    HealthConnectPermissionState(
        canWriteExercise = HEALTH_PERMISSION_WRITE_EXERCISE in granted,
        canReadWeight = HEALTH_PERMISSION_READ_WEIGHT in granted,
        canReadSteps = HEALTH_PERMISSION_READ_STEPS in granted,
    )

/** Mapeia o status do SDK para a disponibilidade da UI (regra 1). */
fun availabilityFromSdkStatus(status: Int): HealthConnectAvailability = when (status) {
    HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
        HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED
    else -> HealthConnectAvailability.UNAVAILABLE
}

/**
 * Constrói o gateway só quando o Health Connect está de fato disponível
 * (`SDK_AVAILABLE`); fora disso o container fica sem gateway e o app funciona
 * como antes (regra 1: nunca crasha, seção escondida).
 */
fun createHealthConnectGatewayIfAvailable(context: Context): HealthConnectGateway? =
    if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
        HealthConnectClientGateway(context)
    } else {
        null
    }

/** Disponibilidade efetiva para a UI, com ou sem gateway construído. */
fun healthConnectAvailability(
    context: Context,
    gateway: HealthConnectGateway?,
): HealthConnectAvailability = runCatching {
    gateway?.availability()
        ?: availabilityFromSdkStatus(HealthConnectClient.getSdkStatus(context))
}.getOrDefault(HealthConnectAvailability.UNAVAILABLE)

/**
 * Interface fina entre o app e o Health Connect. Quem implementa toca o SDK;
 * todo o resto do app (mapper, repositório, ViewModel) depende só desta
 * interface e dos tipos puros de `HealthConnectDtos.kt`.
 */
interface HealthConnectGateway {
    fun availability(): HealthConnectAvailability
    suspend fun grantedPermissions(): Set<String>
    suspend fun writeSession(draft: HealthSessionDraft)
    suspend fun readWeight(fromMillis: Long, toMillis: Long): List<HealthWeightSample>
    suspend fun readSteps(fromMillis: Long, toMillis: Long): List<HealthStepsSample>
}

/**
 * Implementação sobre `HealthConnectClient`.
 *
 * Regras de borda implementadas aqui:
 * - sem permissão, nada é tentado (regra 2) — escrita e leitura guardam a
 *   permissão própria antes de falar com o SDK;
 * - `SecurityException` (permissão revogada no meio do caminho) não propaga:
 *   a escrita vira no-op e a leitura volta vazia; o estado de permissões da
 *   UI é quem mostra "Conecte para sincronizar" (regra 8);
 * - os demais erros propagam para o repositório/ViewModel, que traduzem em
 *   mensagem com "Tentar de novo".
 *
 * O `clientProvider` existe para os testes injetarem um cliente fake.
 */
class HealthConnectClientGateway(
    private val context: Context,
    private val clientProvider: (Context) -> HealthConnectClient =
        { HealthConnectClient.getOrCreate(it) },
) : HealthConnectGateway {

    private val client: HealthConnectClient by lazy { clientProvider(context) }

    override fun availability(): HealthConnectAvailability =
        availabilityFromSdkStatus(HealthConnectClient.getSdkStatus(context))

    override suspend fun grantedPermissions(): Set<String> =
        runCatching { client.permissionController.getGrantedPermissions() }
            .getOrDefault(emptySet())

    override suspend fun writeSession(draft: HealthSessionDraft) {
        if (HEALTH_PERMISSION_WRITE_EXERCISE !in grantedPermissions()) return
        val record = ExerciseSessionRecord(
            startTime = Instant.ofEpochMilli(draft.startedAtMillis),
            startZoneOffset = null,
            endTime = Instant.ofEpochMilli(draft.endedAtMillis),
            endZoneOffset = null,
            metadata = Metadata.manualEntry(clientRecordId = draft.clientRecordId),
            exerciseType = draft.exerciseType,
            title = draft.title,
            // O Health Connect não tem campo de RPE em ExerciseSessionRecord;
            // ele viaja junto das notas para aparecer no registro.
            notes = withRpe(draft.notes, draft.rpe),
        )
        try {
            client.insertRecords(listOf(record))
        } catch (error: SecurityException) {
            // Permissão revogada no meio do caminho: não propaga (regra 8).
        }
    }

    override suspend fun readWeight(fromMillis: Long, toMillis: Long): List<HealthWeightSample> {
        if (HEALTH_PERMISSION_READ_WEIGHT !in grantedPermissions()) return emptyList()
        val response = try {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        Instant.ofEpochMilli(fromMillis),
                        Instant.ofEpochMilli(toMillis),
                    ),
                )
            )
        } catch (error: SecurityException) {
            return emptyList()
        }
        return toWeightSamples(
            response.records.map { record ->
                RawWeight(timeMillis = record.time.toEpochMilli(), weightKg = record.weight.inKilograms)
            }
        )
    }

    override suspend fun readSteps(fromMillis: Long, toMillis: Long): List<HealthStepsSample> {
        if (HEALTH_PERMISSION_READ_STEPS !in grantedPermissions()) return emptyList()
        val response = try {
            client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        Instant.ofEpochMilli(fromMillis),
                        Instant.ofEpochMilli(toMillis),
                    ),
                )
            )
        } catch (error: SecurityException) {
            return emptyList()
        }
        val total = response[StepsRecord.COUNT_TOTAL] ?: return emptyList()
        return toStepsSamples(listOf(RawSteps(fromMillis, toMillis, total)))
    }

    /** Junta as notas do usuário com o RPE, quando houver (regra 6). */
    private fun withRpe(notes: String?, rpe: Float?): String? {
        val rpeText = rpe?.let { "RPE " + String.format(Locale.US, "%.1f", it) }
        return listOfNotNull(notes, rpeText)
            .joinToString(" · ")
            .takeIf { it.isNotBlank() }
    }
}
