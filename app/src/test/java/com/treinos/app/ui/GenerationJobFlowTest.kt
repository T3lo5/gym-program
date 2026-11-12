package com.treinos.app.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.treinos.app.data.WorkoutRepository
import com.treinos.app.data.local.AppDatabase
import com.treinos.app.data.local.WorkoutDao
import com.treinos.app.data.prefs.AppPreferences
import com.treinos.app.data.remote.BackendApi
import com.treinos.app.data.remote.CatalogResponseDto
import com.treinos.app.data.remote.GenerateJobAck
import com.treinos.app.data.remote.GenerateJobStatus
import com.treinos.app.data.remote.GeneratePlanRequest
import com.treinos.app.data.remote.GeneratePlanResponse
import com.treinos.app.data.remote.HealthResponse
import com.treinos.app.data.remote.ModalityDto
import com.treinos.app.data.remote.ReviewPlanRequest
import com.treinos.app.data.remote.ReviewPlanResponse
import com.treinos.app.data.remote.SyncDownloadDto
import com.treinos.app.data.remote.SyncRequestDto
import com.treinos.app.data.remote.SyncResponseDto
import com.treinos.app.data.remote.WorkoutDayDto
import com.treinos.app.data.remote.WorkoutExerciseDto
import com.treinos.app.data.remote.WorkoutPlanDto
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

class FakeJobBackendApi : BackendApi {
    var submitResponse: GenerateJobAck = GenerateJobAck(jobId = "job-1", status = "pending")
    var statusResponse: GenerateJobStatus = GenerateJobStatus(
        jobId = "job-1",
        status = "pending",
        createdAt = "2026-11-10T12:00:00+00:00",
    )
    var cancelResponse: GenerateJobStatus = GenerateJobStatus(
        jobId = "job-1",
        status = "cancelled",
        createdAt = "2026-11-10T12:00:00+00:00",
    )
    var submitThrows: Throwable? = null
    var statusThrows: Throwable? = null
    var lastSubmitRequest: GeneratePlanRequest? = null
    var lastStatusJobId: String? = null
    var lastCancelJobId: String? = null
    var submitCallCount = 0
    var statusCallCount = 0

    override suspend fun health(): HealthResponse = throw UnsupportedOperationException()

    override suspend fun submitGeneration(request: GeneratePlanRequest): GenerateJobAck {
        submitCallCount++
        lastSubmitRequest = request
        submitThrows?.let { throw it }
        return submitResponse
    }

    override suspend fun generationStatus(jobId: String): GenerateJobStatus {
        statusCallCount++
        lastStatusJobId = jobId
        statusThrows?.let { throw it }
        return statusResponse
    }

    override suspend fun cancelGeneration(jobId: String): GenerateJobStatus {
        lastCancelJobId = jobId
        return cancelResponse
    }

    override suspend fun sync(request: SyncRequestDto): SyncResponseDto =
        throw UnsupportedOperationException()
    override suspend fun syncDownload(deviceId: String): SyncDownloadDto =
        throw UnsupportedOperationException()
    override suspend fun catalog(): CatalogResponseDto = throw UnsupportedOperationException()
    override suspend fun reviewPlan(request: ReviewPlanRequest): ReviewPlanResponse =
        throw UnsupportedOperationException()
}

/** WorkoutRepository de teste que não grava no Room (evita Dispatchers.Default). */
class TestWorkoutRepository(
    dao: WorkoutDao,
    api: BackendApi,
    prefs: AppPreferences,
) : WorkoutRepository(dao, api, prefs) {
    override suspend fun completeGeneration(status: GenerateJobStatus, kind: String): Long {
        return 42L
    }
}

private fun newPrefs(context: Context): AppPreferences =
    AppPreferences(context).apply { deviceId = "device-1" }

private val validPlanDto = WorkoutPlanDto(
    name = "Semana híbrida",
    description = "",
    goal = "hypertrophy",
    daysPerWeek = 3,
    days = listOf(
        WorkoutDayDto(
            name = "Dia 1",
            dayOrder = 0,
            weekday = 1,
            modality = "strength",
            isRestDay = false,
            exercises = listOf(
                WorkoutExerciseDto(
                    exerciseId = "Barbell_Bench_Press",
                    exerciseOrder = 0,
                    sets = 3,
                    targetReps = "8-12",
                    targetRpe = 8.0,
                    restSeconds = 90,
                    notes = "",
                    isSupersetWithPrevious = false,
                    metricType = "reps_weight",
                )
            ),
        )
    ),
)

private val validRequest = GeneratePlanRequest(
    deviceId = "device-1",
    goal = "hypertrophy",
    daysPerWeek = 3,
    availableEquipment = listOf("barbell", "dumbbell"),
    targetMuscles = emptyList(),
    currentLevel = "intermediate",
    sessionDurationMinutes = 60,
    reviewAfterWeeks = 4,
    bodyWeightKg = null,
    heightCm = null,
    sex = null,
    modalities = listOf(
        ModalityDto(
            type = "strength",
            sessionsPerWeek = 3,
            goal = "hypertrophy",
            level = "intermediate",
            availableEquipment = listOf("barbell", "dumbbell"),
            targetMuscles = emptyList(),
            sessionDurationMinutes = 60,
        )
    ),
    environment = "gym",
    focusMuscles = null,
    language = "pt",
)

@RunWith(RobolectricTestRunner::class)
class GenerationJobFlowTest {

    private lateinit var database: AppDatabase
    private lateinit var context: Context
    private var viewModel: AppViewModel? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        // O AppViewModel coleta `inProgressSession` do Room em viewModelScope.
        // Sem cancelar, a coleta continua depois do close e pode travar o teste
        // (o worker do Gradle fica pendurado até o timeout da task).
        viewModel?.viewModelScope?.cancel()
        viewModel = null
        database.close()
    }

    private fun vmWith(api: FakeJobBackendApi, prefs: AppPreferences = newPrefs(context)): AppViewModel {
        val repo = TestWorkoutRepository(database.workoutDao(), api, prefs)
        return AppViewModel(context, repo, prefs).also { viewModel = it }
    }

    private fun withEnvelope(prefs: AppPreferences, jobId: String, kind: String = "main") {
        prefs.pendingGenerationV1 = PendingGenerationCodec.encode(
            PendingGeneration(jobId = jobId, kind = kind, request = validRequest, config = null)
        )
    }

    @Test
    fun submit_persiste_envelope_e_nao_salva_plano() = runTest {
        val api = FakeJobBackendApi()
        api.submitResponse = GenerateJobAck(jobId = "job-new", status = "pending")
        val prefs = newPrefs(context)
        val vm = vmWith(api, prefs)

        vm.submitGeneration(
            modalities = validRequest.modalities,
            goal = validRequest.goal,
            daysPerWeek = validRequest.daysPerWeek,
            level = validRequest.currentLevel,
            equipment = validRequest.availableEquipment,
            muscles = validRequest.targetMuscles,
            sessionDuration = validRequest.sessionDurationMinutes,
            reviewWeeks = validRequest.reviewAfterWeeks,
            bodyWeightKgText = "",
            heightCmText = "",
            sexChoice = null,
            environment = validRequest.environment,
            kind = "main",
            config = null,
        )

        assertEquals("job-new", vm.pendingGeneration.value!!.jobId)
        assertEquals("main", vm.pendingGeneration.value!!.kind)
        assertNull(vm.pendingGenerationStatus.value)
    }

    @Test
    fun refresh_done_salva_plano_limpa_envelope_e_persiste_fallback() = runTest {
        val api = FakeJobBackendApi()
        api.statusResponse = GenerateJobStatus(
            jobId = "job-done",
            status = "done",
            createdAt = "2026-11-10T12:00:00+00:00",
            result = GeneratePlanResponse(
                workout = validPlanDto,
                source = "ai",
                fallbackReason = "",
                nextReviewAt = "2026-11-17",
            ),
        )
        val prefs = newPrefs(context)
        withEnvelope(prefs, "job-done")
        val vm = vmWith(api, prefs)

        vm.refreshPendingGeneration()

        assertEquals("done", vm.pendingGenerationStatus.value)
        assertNull(vm.pendingGeneration.value)
        assertEquals("", prefs.lastFallbackReason)
    }

    @Test
    fun refresh_done_main_seleciona_novo_plano() = runTest {
        val api = FakeJobBackendApi()
        api.statusResponse = GenerateJobStatus(
            jobId = "job-done",
            status = "done",
            createdAt = "2026-11-10T12:00:00+00:00",
            result = GeneratePlanResponse(
                workout = validPlanDto,
                source = "ai",
                nextReviewAt = "2026-11-17",
            ),
        )
        val prefs = newPrefs(context)
        withEnvelope(prefs, "job-done", kind = "main")
        val vm = vmWith(api, prefs)

        vm.refreshPendingGeneration()

        assertEquals(42L, vm.selectedPlanId.value)
    }

    @Test
    fun refresh_done_extra_preserva_selecao() = runTest {
        val api = FakeJobBackendApi()
        api.statusResponse = GenerateJobStatus(
            jobId = "job-done",
            status = "done",
            createdAt = "2026-11-10T12:00:00+00:00",
            result = GeneratePlanResponse(
                workout = validPlanDto,
                source = "ai",
                nextReviewAt = "2026-11-17",
            ),
        )
        val prefs = newPrefs(context)
        withEnvelope(prefs, "job-done", kind = "extra")
        val vm = vmWith(api, prefs)
        vm.selectPlan(99L)

        vm.refreshPendingGeneration()

        assertEquals(99L, vm.selectedPlanId.value)
    }

    @Test
    fun refresh_done_com_fallback_persiste_motivo() = runTest {
        val api = FakeJobBackendApi()
        api.statusResponse = GenerateJobStatus(
            jobId = "job-done",
            status = "done",
            createdAt = "2026-11-10T12:00:00+00:00",
            result = GeneratePlanResponse(
                workout = validPlanDto,
                source = "local",
                fallbackReason = "no_api_key",
                nextReviewAt = "2026-11-17",
            ),
        )
        val prefs = newPrefs(context)
        withEnvelope(prefs, "job-done")
        val vm = vmWith(api, prefs)

        vm.refreshPendingGeneration()

        assertEquals("done", vm.pendingGenerationStatus.value)
        assertNull(vm.pendingGeneration.value)
        assertEquals("no_api_key", prefs.lastFallbackReason)
    }

    @Test
    fun refresh_failed_mantem_envelope_para_retry() = runTest {
        val api = FakeJobBackendApi()
        api.statusResponse = GenerateJobStatus(
            jobId = "job-failed",
            status = "failed",
            createdAt = "2026-11-10T12:00:00+00:00",
            result = null,
            error = "pool vazio para o foco calves",
        )
        val prefs = newPrefs(context)
        withEnvelope(prefs, "job-failed", kind = "extra")
        val vm = vmWith(api, prefs)

        vm.refreshPendingGeneration()

        assertEquals("failed", vm.pendingGenerationStatus.value)
        assertNotNull(vm.pendingGeneration.value)
        assertEquals("job-failed", vm.pendingGeneration.value!!.jobId)
        assertNotNull(vm.generateError.value)
    }

    @Test
    fun retry_reenvia_request_persistido() = runTest {
        val api = FakeJobBackendApi()
        api.submitResponse = GenerateJobAck(jobId = "job-retry", status = "pending")
        val prefs = newPrefs(context)
        withEnvelope(prefs, "job-old", kind = "extra")
        val vm = vmWith(api, prefs)

        vm.retryFromEnvelope()

        assertEquals("device-1", api.lastSubmitRequest!!.deviceId)
        assertEquals("hypertrophy", api.lastSubmitRequest!!.goal)
        assertEquals("job-retry", vm.pendingGeneration.value!!.jobId)
    }

    @Test
    fun cancel_limpa_envelope() = runTest {
        val api = FakeJobBackendApi()
        api.cancelResponse = GenerateJobStatus(
            jobId = "job-cancel",
            status = "cancelled",
            createdAt = "2026-11-10T12:00:00+00:00",
        )
        val prefs = newPrefs(context)
        withEnvelope(prefs, "job-cancel")
        val vm = vmWith(api, prefs)

        vm.cancelGeneration()

        assertNull(vm.pendingGeneration.value)
        assertEquals("job-cancel", api.lastCancelJobId)
    }

    @Test
    fun refresh_404_vira_falha_com_retry() = runTest {
        val api = FakeJobBackendApi()
        api.statusThrows = RuntimeException("404 not found")
        val prefs = newPrefs(context)
        withEnvelope(prefs, "job-missing")
        val vm = vmWith(api, prefs)

        vm.refreshPendingGeneration()

        assertNotNull(vm.generateError.value)
        assertNotNull(vm.pendingGeneration.value)
    }

    @Test
    fun polls_sobrepostos_sao_deduzidos() = runTest {
        val api = FakeJobBackendApi()
        api.statusResponse = GenerateJobStatus(
            jobId = "job-dedup",
            status = "done",
            createdAt = "2026-11-10T12:00:00+00:00",
            result = GeneratePlanResponse(
                workout = validPlanDto,
                source = "local",
                fallbackReason = "no_api_key",
                nextReviewAt = "2026-11-17",
            ),
        )
        val prefs = newPrefs(context)
        withEnvelope(prefs, "job-dedup")
        val vm = vmWith(api, prefs)

        vm.refreshPendingGeneration()
        vm.refreshPendingGeneration() // segundo é deduped (_isPolling = true)

        assertEquals(1, api.statusCallCount)
    }

    @Test
    fun on_app_resumed_consulta_status_do_envelope() = runTest {
        val api = FakeJobBackendApi()
        api.statusResponse = GenerateJobStatus(
            jobId = "job-init",
            status = "done",
            createdAt = "2026-11-10T12:00:00+00:00",
            result = GeneratePlanResponse(
                workout = validPlanDto,
                source = "ai",
                fallbackReason = "",
                nextReviewAt = "2026-11-17",
            ),
        )
        val prefs = newPrefs(context)
        withEnvelope(prefs, "job-init")
        val vm = vmWith(api, prefs)

        vm.refreshPendingGeneration()

        assertEquals("job-init", api.lastStatusJobId)
    }
}
