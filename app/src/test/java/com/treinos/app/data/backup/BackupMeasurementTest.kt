package com.treinos.app.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.treinos.app.data.WorkoutRepository
import com.treinos.app.data.local.AppDatabase
import com.treinos.app.data.local.BodyMeasurementEntity
import com.treinos.app.data.local.WorkoutDao
import com.treinos.app.data.prefs.AppPreferences
import com.treinos.app.data.remote.BackendApi
import com.treinos.app.data.remote.CatalogResponseDto
import com.treinos.app.data.remote.GenerateJobAck
import com.treinos.app.data.remote.GenerateJobStatus
import com.treinos.app.data.remote.GeneratePlanRequest
import com.treinos.app.data.remote.HealthResponse
import com.treinos.app.data.remote.ReviewPlanRequest
import com.treinos.app.data.remote.ReviewPlanResponse
import com.treinos.app.data.remote.SyncDownloadDto
import com.treinos.app.data.remote.SyncRequestDto
import com.treinos.app.data.remote.SyncResponseDto
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * H4-8: contrato do bloco `measurements` no backup JSON.
 *
 * Regras cobertas: `measurements` opcional (v1–v3 = medidas locais intactas),
 * `[]` explícito limpa (v4), `photoPath` relativo a `filesDir` no JSON e
 * remontado no import, validação de faixas e de IDs duplicados. Os casos que
 * mexem no banco usam Room em memória (mesmo padrão de `BackupImportTest`).
 */
@RunWith(RobolectricTestRunner::class)
class BackupMeasurementTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: WorkoutDao
    private lateinit var preferences: AppPreferences
    private lateinit var repository: WorkoutRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.workoutDao()
        preferences = AppPreferences(context)
        preferences.deviceId = "id-deste-app"
        repository = WorkoutRepository(
            dao = dao,
            backendClient = UnusedBackend(),
            preferences = preferences,
            filesDir = context.filesDir,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedMeasurements() {
        dao.insertMeasurement(
            BodyMeasurementEntity(
                id = 1,
                measuredAt = 1_760_000_000_000L,
                weightKg = 82.4,
                bodyFatPercent = 18.5,
                waistCm = 84.0,
                notes = "manhã",
            ),
        )
        dao.insertMeasurement(
            BodyMeasurementEntity(
                id = 2,
                measuredAt = 1_760_100_000_000L,
                weightKg = 82.0,
                photoPath = File(context.filesDir, "progress-photos/2026-11-10-0732.jpg").absolutePath,
            ),
        )
    }

    private fun backupFile(
        schemaVersion: Int,
        measurements: List<BackupMeasurement>?,
    ): BackupFile = BackupFile(
        schemaVersion = schemaVersion,
        exportedAt = "2026-11-10T12:00:00Z",
        app = BackupApp(packageName = "com.treinos.app", versionName = "1.5.0", versionCode = 42),
        deviceId = "celular-antigo",
        plans = emptyList(),
        sessions = emptyList(),
        profile = null,
        measurements = measurements,
    )

    @Test
    fun round_trip_preserva_medidas() = runTest {
        seedMeasurements()

        val json = repository.exportBackup(appVersionName = "1.5.0", appVersionCode = 42L)
        val decoded = backupImportDecoder.decodeFromString(BackupFile.serializer(), json)

        // v4 com o bloco presente; a foto viaja como caminho RELATIVO a filesDir.
        assertEquals(4, decoded.schemaVersion)
        val exported = decoded.measurements.orEmpty()
        assertEquals(2, exported.size)
        assertEquals("progress-photos/2026-11-10-0732.jpg", exported.first { it.id == 2L }.photoPath)
        assertEquals(82.4, exported.first { it.id == 1L }.weightKg!!, 0.0001)
        assertEquals(18.5, exported.first { it.id == 1L }.bodyFatPercent!!, 0.0001)
        assertEquals("manhã", exported.first { it.id == 1L }.notes)

        // Import remonta o caminho contra o filesDir deste aparelho — o arquivo
        // não existe aqui e mesmo assim o import não falha (regra 12).
        repository.importBackup(decoded)
        val restored = dao.getAllMeasurements()
        assertEquals(2, restored.size)
        val restoredWithPhoto = restored.first { it.id == 2L }
        assertEquals(
            File(context.filesDir, "progress-photos/2026-11-10-0732.jpg").absolutePath,
            restoredWithPhoto.photoPath,
        )
        assertTrue(!File(restoredWithPhoto.photoPath).exists())
    }

    @Test
    fun medida_com_campos_nulos_serializa() {
        val file = backupFile(
            schemaVersion = 4,
            measurements = listOf(BackupMeasurement(id = 1, measuredAt = 1_760_000_000_000L)),
        )

        val json = backupExportEncoder.encodeToString(BackupFile.serializer(), file)
        val decoded = backupImportDecoder.decodeFromString(BackupFile.serializer(), json)
        val measurement = decoded.measurements.orEmpty().single()

        assertNull(measurement.weightKg)
        assertNull(measurement.bodyFatPercent)
        assertNull(measurement.waistCm)
        assertNull(measurement.chestCm)
        assertNull(measurement.armCm)
        assertNull(measurement.thighCm)
        assertNull(measurement.hipCm)
        assertNull(measurement.photoPath)
        assertEquals("", measurement.notes)
        assertEquals("manual", measurement.source)
    }

    @Test
    fun backup_v3_sem_measurements_decodifica_como_nulo() {
        val json = """
            {
              "schemaVersion": 3,
              "exportedAt": "2026-11-10T12:00:00Z",
              "app": {"packageName": "com.treinos.app", "versionName": "1.4.0", "versionCode": 40},
              "deviceId": "celular-antigo",
              "plans": [],
              "sessions": []
            }
        """.trimIndent()

        val decoded = backupImportDecoder.decodeFromString(BackupFile.serializer(), json)

        assertNull(decoded.measurements)
    }

    @Test
    fun v4_com_lista_vazia_limpa_medidas() = runTest {
        seedMeasurements()

        // `[]` explícito: o arquivo fala que não há nenhuma medida (regra 10).
        repository.importBackup(backupFile(schemaVersion = 4, measurements = emptyList()))

        assertTrue(dao.getAllMeasurements().isEmpty())
    }

    @Test
    fun v3_nao_limpa_medidas_locais() = runTest {
        seedMeasurements()

        // v1/v2/v3: `measurements` ausente = "o arquivo não fala disso" (regra 11).
        repository.importBackup(backupFile(schemaVersion = 3, measurements = null))

        val measurements = dao.getAllMeasurements()
        assertEquals(2, measurements.size)
        assertEquals(82.4, measurements.first { it.id == 1L }.weightKg!!, 0.0001)
    }

    @Test
    fun rejeita_id_duplicado_de_medida() {
        val result = BackupValidator.validate(
            backupFile(
                schemaVersion = 4,
                measurements = listOf(
                    BackupMeasurement(id = 1, measuredAt = 1_760_000_000_000L, weightKg = 82.0),
                    BackupMeasurement(id = 1, measuredAt = 1_760_100_000_000L, weightKg = 82.4),
                ),
            ),
        )

        assertTrue(!result.valid)
        assertTrue(result.message!!.contains("duplicados"))
    }

    @Test
    fun rejeita_peso_de_medida_fora_da_faixa() {
        val result = BackupValidator.validate(
            backupFile(
                schemaVersion = 4,
                measurements = listOf(
                    BackupMeasurement(id = 1, measuredAt = 1_760_000_000_000L, weightKg = 500.0),
                ),
            ),
        )

        assertTrue(!result.valid)
        assertTrue(result.message!!.contains("30–300"))
    }

    @Test
    fun mapper_entity_para_dto_e_volta() {
        val entity = BodyMeasurementEntity(
            id = 7,
            measuredAt = 1_760_000_000_000L,
            weightKg = 82.4,
            bodyFatPercent = 18.5,
            waistCm = 84.0,
            chestCm = 100.0,
            armCm = 35.0,
            thighCm = 58.0,
            hipCm = 96.0,
            photoPath = "progress-photos/2026-11-10-0732.jpg",
            notes = "manhã",
            source = "manual",
        )

        assertEquals(entity, BackupMapper.toEntity(BackupMapper.toDto(entity)))
    }

    private class UnusedBackend : BackendApi {
        override suspend fun health(): HealthResponse = error("não usado")

        override suspend fun submitGeneration(request: GeneratePlanRequest): GenerateJobAck =
            error("não usado")

        override suspend fun generationStatus(jobId: String): GenerateJobStatus =
            error("não usado")

        override suspend fun cancelGeneration(jobId: String): GenerateJobStatus =
            error("não usado")

        override suspend fun sync(request: SyncRequestDto): SyncResponseDto = error("não usado")

        override suspend fun syncDownload(deviceId: String): SyncDownloadDto = error("não usado")

        override suspend fun catalog(): CatalogResponseDto = error("não usado")

        override suspend fun reviewPlan(request: ReviewPlanRequest): ReviewPlanResponse =
            error("não usado")
    }
}
