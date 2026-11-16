package com.treinos.app.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.treinos.app.data.WorkoutRepository
import com.treinos.app.data.local.AppDatabase
import com.treinos.app.data.local.WorkoutDao
import com.treinos.app.data.local.WorkoutDayEntity
import com.treinos.app.data.local.WorkoutPlanEntity
import com.treinos.app.data.local.WorkoutPlanExerciseEntity
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * H4-12 (débito fechado em 2026-11-16): a carga-alvo aplicada pelo usuário
 * (`targetWeightKg`) precisa sobreviver ao ciclo de revisão do plano —
 * `toReviewPlanDto` envia o campo e o backend o devolve; aceitar a revisão
 * regenera o plano COM os alvos, não sem eles.
 */
@RunWith(RobolectricTestRunner::class)
class ReviewPlanTargetWeightTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var dao: WorkoutDao
    private lateinit var preferences: AppPreferences
    private lateinit var viewModel: AppViewModel
    private lateinit var api: CapturingApi

    private val testDispatcher = UnconfinedTestDispatcher()

    /** API falsa que ecoa o plano recebido na revisão (comportamento do backend). */
    private class CapturingApi : BackendApi {
        var lastReview: ReviewPlanRequest? = null

        override suspend fun health(): HealthResponse = throw UnsupportedOperationException()
        override suspend fun submitGeneration(request: GeneratePlanRequest): GenerateJobAck =
            throw UnsupportedOperationException()
        override suspend fun generationStatus(jobId: String): GenerateJobStatus =
            throw UnsupportedOperationException()
        override suspend fun cancelGeneration(jobId: String): GenerateJobStatus =
            throw UnsupportedOperationException()
        override suspend fun sync(request: SyncRequestDto): SyncResponseDto =
            throw UnsupportedOperationException()
        override suspend fun syncDownload(deviceId: String): SyncDownloadDto =
            throw UnsupportedOperationException()
        override suspend fun catalog(): CatalogResponseDto = throw UnsupportedOperationException()

        override suspend fun reviewPlan(request: ReviewPlanRequest): ReviewPlanResponse {
            lastReview = request
            // O backend devolve o plano recebido (ajustes só nas séries).
            return ReviewPlanResponse(
                workout = request.plan,
                changes = listOf("ok"),
                source = "local",
            )
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.workoutDao()
        preferences = AppPreferences(context)
        preferences.deviceId = "device"
        api = CapturingApi()
        Dispatchers.setMain(testDispatcher)
        viewModel = AppViewModel.factory(
            context = context,
            repository = WorkoutRepository(dao, api, preferences),
            preferences = preferences,
        ).create(AppViewModel::class.java)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun revisao_preserva_carga_alvo() {
        // Plano principal com carga-alvo aplicada em um exercício.
        runBlocking {
            val planId = dao.insertPlan(WorkoutPlanEntity(name = "Principal", kind = "main"))
            val dayId = dao.insertDay(
                WorkoutDayEntity(
                    planId = planId,
                    name = "Dia Teste",
                    weekday = null,
                    isRestDay = false,
                ),
            )
            dao.insertPlanExercises(
                listOf(
                    WorkoutPlanExerciseEntity(
                        dayId = dayId,
                        exerciseId = "squat",
                        exerciseName = "Agachamento",
                        sets = 3,
                        targetWeightKg = 60.0,
                    ),
                ),
            )
            // Sanidade: o exercício está no banco antes de revisar.
            assertEquals(1, dao.observeAllPlanExercises().first().size)
            // Pré-aquece `activePlans`: `mainPlan()` lê `activePlans.value`
            // diretamente, então o StateFlow precisa de um assinante antes.
            viewModel.activePlans.first { it.isNotEmpty() }
        }

        viewModel.reviewPlan()
        runBlocking {
            withTimeoutOrNull(5_000) {
                viewModel.reviewChanges.first { it.isNotEmpty() }
            } ?: error(
                "Revisão não respondeu (message=${viewModel.message.value}, " +
                    "isReviewing=${viewModel.isReviewing.value}, " +
                    "activePlans=${viewModel.activePlans.value.size})",
            )
        }

        // O pedido enviado ao backend carrega a carga-alvo.
        val enviado = api.lastReview
        assertNotNull(enviado)
        val dias = enviado!!.plan.days
        assertEquals("dias do plano enviado: $enviado", 1, dias.size)
        val exercicios = dias.single().exercises
        assertEquals("exercicios do dia: $enviado", 1, exercicios.size)
        assertEquals(60.0, exercicios.single().targetWeightKg!!, 0.0)

        val aplicado = context.getString(com.treinos.app.R.string.msg_review_applied)
        viewModel.acceptReview()
        // Espera a aplicação assíncrona terminar (mensagem de sucesso).
        runBlocking {
            withTimeoutOrNull(5_000) {
                viewModel.message.first { it == aplicado }
            } ?: error("Revisão não foi aplicada (message=${viewModel.message.value})")
        }

        // O plano regenerado pela revisão mantém a carga-alvo; o `nextReviewAt`
        // preenchido prova que é o plano NOVO (o original tinha `null`) e que o
        // cálculo que usava ChronoUnit.WEEKS rodou sem lançar.
        val revisado = runBlocking {
            withTimeoutOrNull(5_000) {
                viewModel.activePlans.first { planos ->
                    planos.any { p ->
                        p.plan.nextReviewAt?.isNotBlank() == true &&
                            p.days.any { d ->
                                d.exercises.any { it.targetWeightKg == 60.0 }
                            }
                    }
                }
            } ?: error("Plano revisado completo não apareceu em activePlans")
        }.first { p ->
            p.plan.nextReviewAt?.isNotBlank() == true &&
                p.days.any { d -> d.exercises.any { it.targetWeightKg == 60.0 } }
        }
        assertEquals(60.0, revisado.days.single().exercises.single().targetWeightKg!!, 0.0)
    }
}
