package com.treinos.app.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.aggregate.AggregationResultGroupedByDuration
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ChangesResponse
import androidx.health.connect.client.response.InsertRecordsResponse
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/**
 * H4-9: gateway do Health Connect com um cliente fake — disponibilidade,
 * permissões, escrita com `clientRecordId` e leituras de peso/passos.
 * A integração real exige emulador com Health Connect e fica na verificação
 * manual da spec.
 */
@RunWith(RobolectricTestRunner::class)
class HealthConnectGatewayTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun draft(sessionId: Long = 42L) = HealthSessionDraft(
        clientRecordId = healthClientRecordId(sessionId),
        startedAtMillis = 1_700_000_000_000L,
        endedAtMillis = 1_700_003_600_000L,
        exerciseType = HC_EXERCISE_TYPE_STRENGTH_TRAINING,
        title = "Treino A",
        notes = "boa sessão",
        rpe = 8.0f,
    )

    @Test
    fun availability_mapeia_sdk_status() {
        assertEquals(
            HealthConnectAvailability.AVAILABLE,
            availabilityFromSdkStatus(HealthConnectClient.SDK_AVAILABLE),
        )
        assertEquals(
            HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED,
            availabilityFromSdkStatus(HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED),
        )
        assertEquals(
            HealthConnectAvailability.UNAVAILABLE,
            availabilityFromSdkStatus(HealthConnectClient.SDK_UNAVAILABLE),
        )
    }

    @Test
    fun sem_permissao_nao_escreve() = runTest {
        val fake = FakeHealthConnectClient(granted = emptySet())
        val gateway = HealthConnectClientGateway(context) { fake }

        gateway.writeSession(draft())

        assertTrue(fake.insertedRecords.isEmpty())
    }

    @Test
    fun permissao_parcial_habilita_so_o_concedido() = runTest {
        val fake = FakeHealthConnectClient(granted = setOf(HEALTH_PERMISSION_WRITE_EXERCISE))
        val gateway = HealthConnectClientGateway(context) { fake }

        val state = healthPermissionState(fake.permissionController.getGrantedPermissions())
        assertTrue(state.canWriteExercise)
        assertFalse(state.canReadWeight)
        assertFalse(state.canReadSteps)
        assertTrue(state.hasAnyPermission)

        // Com só a escrita concedida, escrever funciona e ler peso volta vazio.
        gateway.writeSession(draft())
        assertEquals(1, fake.insertedRecords.size)
        assertTrue(gateway.readWeight(0L, Long.MAX_VALUE).isEmpty())
    }

    @Test
    fun escrita_usa_client_record_id() = runTest {
        val fake = FakeHealthConnectClient(granted = HEALTH_PERMISSIONS)
        val gateway = HealthConnectClientGateway(context) { fake }

        gateway.writeSession(draft(sessionId = 42L))

        val record = fake.insertedRecords.single() as ExerciseSessionRecord
        assertEquals("treinos:session:42", record.metadata.clientRecordId)
        assertEquals(HC_EXERCISE_TYPE_STRENGTH_TRAINING, record.exerciseType)
        assertEquals(Instant.ofEpochMilli(1_700_000_000_000L), record.startTime)
        assertEquals(Instant.ofEpochMilli(1_700_003_600_000L), record.endTime)
        assertEquals("Treino A", record.title)
    }

    @Test
    fun leitura_de_peso_mapeia_kilogramas() = runTest {
        val instant = Instant.ofEpochMilli(1_700_010_000_000L)
        val fake = FakeHealthConnectClient(
            granted = setOf(HEALTH_PERMISSION_READ_WEIGHT),
            weightRecords = listOf(
                WeightRecord(
                    time = instant,
                    zoneOffset = null,
                    weight = Mass.kilograms(82.4),
                    metadata = Metadata.manualEntry(),
                )
            ),
        )
        val gateway = HealthConnectClientGateway(context) { fake }

        val samples = gateway.readWeight(0L, Long.MAX_VALUE)

        assertEquals(listOf(HealthWeightSample(1_700_010_000_000L, 82.4)), samples)
    }

    @Test
    fun leitura_de_passos_usa_total() = runTest {
        val fake = FakeHealthConnectClient(
            granted = setOf(HEALTH_PERMISSION_READ_STEPS),
            stepsTotal = 12_345L,
        )
        val gateway = HealthConnectClientGateway(context) { fake }

        val samples = gateway.readSteps(1_000L, 2_000L)

        assertEquals(listOf(HealthStepsSample(1_000L, 2_000L, 12_345L)), samples)
    }

    @Test
    fun erro_de_permissao_nao_propaga_excecao() = runTest {
        val fake = FakeHealthConnectClient(
            granted = HEALTH_PERMISSIONS,
            failWith = SecurityException("permissão revogada"),
        )
        val gateway = HealthConnectClientGateway(context) { fake }

        // Regra 8: falha de permissão não propaga — escrita vira no-op e
        // leitura volta vazia; a UI mostra o estado de permissões.
        gateway.writeSession(draft())
        assertTrue(fake.insertedRecords.isEmpty())
        assertTrue(gateway.readWeight(0L, Long.MAX_VALUE).isEmpty())
        assertTrue(gateway.readSteps(0L, Long.MAX_VALUE).isEmpty())
    }
}

private class FakePermissionController(granted: Set<String>) : PermissionController {
    var granted: Set<String> = granted
    override suspend fun getGrantedPermissions(): Set<String> = granted
    override suspend fun revokeAllPermissions() {
        granted = emptySet()
    }
}

/**
 * Cliente fake: guarda o que o gateway gravou e devolve o que foi configurado.
 * `failWith` simula a permissão revogada no meio do caminho (regra 8).
 */
private class FakeHealthConnectClient(
    granted: Set<String>,
    weightRecords: List<WeightRecord> = emptyList(),
    stepsTotal: Long? = null,
    failWith: Exception? = null,
) : HealthConnectClient {

    val insertedRecords = mutableListOf<Record>()
    private val allRecords: List<Record> = weightRecords
    private val stepsTotal: Long? = stepsTotal
    private val failWith: Exception? = failWith
    override val permissionController: PermissionController = FakePermissionController(granted)

    override suspend fun insertRecords(records: List<Record>): InsertRecordsResponse {
        failWith?.let { throw it }
        insertedRecords.addAll(records)
        return InsertRecordsResponse(emptyList())
    }

    override suspend fun updateRecords(records: List<Record>) {
        failWith?.let { throw it }
    }

    override suspend fun deleteRecords(
        recordType: kotlin.reflect.KClass<out Record>,
        recordIds: List<String>,
        clientRecordIds: List<String>,
    ) = Unit

    override suspend fun deleteRecords(
        recordType: kotlin.reflect.KClass<out Record>,
        timeRangeFilter: TimeRangeFilter,
    ) = Unit

    override suspend fun <T : Record> readRecord(
        recordType: kotlin.reflect.KClass<T>,
        recordId: String,
    ): androidx.health.connect.client.response.ReadRecordResponse<T> =
        error("Leitura por id não é usada nos testes.")

    override suspend fun <T : Record> readRecords(
        request: ReadRecordsRequest<T>,
    ): ReadRecordsResponse<T> {
        failWith?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        val matched = allRecords.filter { it::class == request.recordType } as List<T>
        return ReadRecordsResponse(matched, null)
    }

    override suspend fun aggregate(request: AggregateRequest): AggregationResult {
        failWith?.let { throw it }
        return AggregationResult(
            longValues = stepsTotal
                ?.let { mapOf(StepsRecord.COUNT_TOTAL.metricKey to it) }
                ?: emptyMap(),
            doubleValues = emptyMap(),
            dataOrigins = emptySet(),
        )
    }

    override suspend fun aggregateGroupByDuration(
        request: AggregateGroupByDurationRequest,
    ): List<AggregationResultGroupedByDuration> = emptyList()

    override suspend fun aggregateGroupByPeriod(
        request: AggregateGroupByPeriodRequest,
    ): List<AggregationResultGroupedByPeriod> = emptyList()

    override suspend fun getChangesToken(request: ChangesTokenRequest): String = ""

    override suspend fun getChanges(changesToken: String): ChangesResponse =
        ChangesResponse(emptyList(), changesToken, false, false)
}
