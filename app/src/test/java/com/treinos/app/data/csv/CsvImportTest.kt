package com.treinos.app.data.csv

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.treinos.app.data.WorkoutRepository
import com.treinos.app.data.local.AppDatabase
import com.treinos.app.data.local.SessionExerciseEntity
import com.treinos.app.data.local.SetLogEntity
import com.treinos.app.data.local.WorkoutDao
import com.treinos.app.data.local.WorkoutDayEntity
import com.treinos.app.data.local.WorkoutPlanEntity
import com.treinos.app.data.local.WorkoutPlanExerciseEntity
import com.treinos.app.data.local.WorkoutSessionEntity
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
import java.time.Instant
import java.util.TimeZone
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * H4-7: import de CSV transacional, aditivo e idempotente por `importRef`
 * (Room em memória, como BackupImportTest).
 */
@RunWith(RobolectricTestRunner::class)
class CsvImportTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: WorkoutDao
    private lateinit var preferences: AppPreferences
    private lateinit var repository: WorkoutRepository

    private val originalTimeZone = TimeZone.getDefault()

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.workoutDao()
        preferences = AppPreferences(context)
        preferences.deviceId = "id-deste-app"
        repository = WorkoutRepository(dao = dao, backendClient = UnusedBackend(), preferences = preferences)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
        db.close()
    }

    private val startedAt = Instant.parse("2026-11-10T12:00:00Z").toEpochMilli()

    private fun draft(
        importRef: String,
        title: String = "Treino A",
    ) = CsvSessionDraft(
        importRef = importRef,
        startedAt = startedAt,
        title = title,
        notes = "Importado do Strong",
        durationSeconds = 3600L,
        exercises = listOf(
            CsvExerciseDraft(
                exerciseName = "Supino reto",
                exerciseOrder = 0,
                sets = listOf(
                    CsvSetDraft(
                        setNumber = 1,
                        reps = 10,
                        weightKg = 40.0,
                        rpe = 8.0,
                        durationSeconds = null,
                        distanceMeters = null,
                        isWarmup = false,
                    ),
                    CsvSetDraft(
                        setNumber = 2,
                        reps = 8,
                        weightKg = 42.5,
                        rpe = null,
                        durationSeconds = null,
                        distanceMeters = null,
                        isWarmup = false,
                    ),
                ),
            ),
        ),
    )

    private fun plan(vararg sessions: CsvSessionDraft) = CsvImportPlan(
        format = CsvImportFormat.STRONG,
        sessions = sessions.toList(),
        skippedRows = 0,
        warnings = emptyList(),
    )

    private suspend fun seedExistingWorkout() {
        dao.replaceActivePlan(
            plan = WorkoutPlanEntity(id = 1, name = "Plano antigo"),
            days = listOf(WorkoutDayEntity(id = 10, planId = 1, name = "Treino A")),
            exercisesByDay = listOf(
                listOf(
                    WorkoutPlanExerciseEntity(
                        id = 100,
                        dayId = 10,
                        exerciseId = "ex1",
                        exerciseName = "Supino",
                    ),
                ),
            ),
        )
        dao.insertSession(WorkoutSessionEntity(id = 50, planId = 1, dayId = 10, status = "completed"))
        dao.insertSessionExercises(
            listOf(
                SessionExerciseEntity(id = 500, sessionId = 50, exerciseId = "ex1", exerciseName = "Supino"),
            ),
        )
        dao.insertSetLogs(
            listOf(SetLogEntity(id = 5000, exerciseLogId = 500, setNumber = 1, reps = 10, weightKg = 40.0)),
        )
    }

    @Test
    fun import_insere_sessao_exercicio_e_series() = runTest {
        val result = repository.importSessionsCsv(
            plan(draft("strong:2026-11-10:treino a")),
            deviceId = "id-deste-app",
        )

        assertEquals(1, result.sessionsImported)
        assertEquals(1, result.exercisesImported)
        assertEquals(2, result.setsImported)
        assertEquals(0, result.duplicates)

        val session = dao.getAllSessionEntities().single()
        assertEquals("strong:2026-11-10:treino a", session.importRef)
        assertEquals("Treino A", session.title)
        assertEquals("completed", session.status)
        assertEquals(startedAt, session.startedAt)
        assertEquals(3600L, session.totalDurationSeconds)

        val exercise = dao.getAllSessionExercises().single()
        assertEquals("csv_supino_reto", exercise.exerciseId)
        assertEquals("Supino reto", exercise.exerciseName)
        assertEquals(2, exercise.plannedSets)
        assertEquals(2, exercise.completedSets)

        val sets = dao.getAllSetLogs()
        assertEquals(2, sets.size)
        assertEquals(10, sets.first { it.setNumber == 1 }.reps)
        assertEquals(40.0, sets.first { it.setNumber == 1 }.weightKg!!, 0.0001)
        assertEquals(8, sets.first { it.setNumber == 2 }.reps)
        assertEquals(42.5, sets.first { it.setNumber == 2 }.weightKg!!, 0.0001)
    }

    @Test
    fun import_e_aditivo_e_nao_apaga_existentes() = runTest {
        seedExistingWorkout()

        val result = repository.importSessionsCsv(
            plan(draft("strong:2026-11-10:treino a")),
            deviceId = "id-deste-app",
        )

        assertEquals(1, result.sessionsImported)
        // Nada existente é apagado nem alterado.
        assertEquals(2, dao.getAllSessionEntities().size)
        assertEquals("Plano antigo", dao.getAllPlanEntities().single().name)
        assertEquals(1, dao.getAllPlanEntities().size)
        assertEquals(1, dao.getAllSetLogs().count { it.exerciseLogId == 500L })
    }

    @Test
    fun reimportar_nao_duplica() = runTest {
        val samePlan = plan(draft("strong:2026-11-10:treino a"))

        val first = repository.importSessionsCsv(samePlan, deviceId = "id-deste-app")
        assertEquals(1, first.sessionsImported)

        val second = repository.importSessionsCsv(samePlan, deviceId = "id-deste-app")
        assertEquals(0, second.sessionsImported)
        assertEquals(1, second.duplicates)
        assertEquals(1, dao.getAllSessionEntities().size)
    }

    @Test
    fun import_ref_derivado_do_formato_e_data() = runTest {
        val csv = CsvCodec.parseCsv(
            "Date,Workout Name,Exercise Name,Set Order,Weight,Reps\n" +
                "2026-11-10,Treino A,Supino reto,1,40,10\n" +
                "2026-11-10,Treino A,Supino reto,2,42.5,8",
        )
        val parsed = StrongHevyCsvParser.parse(csv)

        repository.importSessionsCsv(parsed, deviceId = "id-deste-app")

        val session = dao.getAllSessionEntities().single()
        assertEquals("strong:2026-11-10:treino a", session.importRef)

        // Sessão criada no app nasce sem importRef e coexiste no índice único
        // (NULLs são distintos no SQLite).
        dao.insertSession(WorkoutSessionEntity(id = 60, status = "completed"))
        assertNull(dao.getAllSessionEntities().first { it.id == 60L }.importRef)
        assertEquals(2, dao.getAllSessionEntities().size)
    }

    @Test
    fun falha_no_meio_faz_rollback() = runTest {
        // Dois rascunhos com o mesmo importRef: o segundo bate no índice UNIQUE
        // no meio da transação e TODO o lote volta atrás.
        val repeated = draft("strong:2026-11-10:treino a")
        val brokenPlan = plan(repeated, repeated)

        val failure = runCatching {
            repository.importSessionsCsv(brokenPlan, deviceId = "id-deste-app")
        }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(dao.getAllSessionEntities().isEmpty())
        assertNull(dao.getSessionByImportRef("strong:2026-11-10:treino a"))
        assertTrue(dao.getAllSetLogs().isEmpty())
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
