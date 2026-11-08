package com.treinos.app.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Exceções tipadas do backend (H3-29): a camada de dados diz **o que** falhou
 * e a UI traduz para mensagem acionável (`uiErrorFrom`). O corpo HTTP cru
 * nunca entra na mensagem — vai para o Logcat via `Log.w`.
 */
/** Sem conexão com o backend (DNS, rota, recusa de conexão). */
class BackendConnectionException(message: String, cause: Throwable?) : IOException(message, cause)

/** Tempo limite: o backend não respondeu dentro do timeout configurado. */
class BackendTimeoutException(message: String, cause: Throwable?) : IOException(message, cause)

/** Backend respondeu, mas com status de erro; [code] alimenta a mensagem da UI. */
class BackendHttpException(val code: Int, message: String) : IOException(message)

/** Backend respondeu 2xx, mas o corpo não é o JSON esperado. */
class BackendResponseException(message: String, cause: Throwable?) : IOException(message, cause)

/**
 * Cliente HTTP do backend de treinos.
 *
 * O backend só é chamado em dois momentos:
 *  - quando o usuário pede um treino novo (geração sob demanda);
 *  - quando o usuário toca em "Sincronizar" nas configurações.
 */
class BackendClient(
    private val baseUrlProvider: () -> String,
    private val client: OkHttpClient = defaultClient(),
) : BackendApi {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun health(): HealthResponse = get("/health")

    override suspend fun generatePlan(request: GeneratePlanRequest): GeneratePlanResponse =
        post("/v1/generate-workout", json.encodeToString(GeneratePlanRequest.serializer(), request))

    override suspend fun sync(request: SyncRequestDto): SyncResponseDto =
        post("/v1/sync", json.encodeToString(SyncRequestDto.serializer(), request))

    override suspend fun syncDownload(deviceId: String): SyncDownloadDto =
        get("/v1/sync/download?deviceId=$deviceId")

    override suspend fun catalog(): CatalogResponseDto = get("/v1/catalog")

    override suspend fun reviewPlan(request: ReviewPlanRequest): ReviewPlanResponse =
        post("/v1/review-plan", json.encodeToString(ReviewPlanRequest.serializer(), request))

    private suspend inline fun <reified T> get(path: String): T = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrlProvider() + path)
            .get()
            .build()
        execute(request)
    }

    private suspend inline fun <reified T> post(path: String, body: String): T =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(baseUrlProvider() + path)
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            execute(request)
        }

    private inline fun <reified T> execute(request: Request): T {
        val response = try {
            client.newCall(request).execute()
        } catch (error: SocketTimeoutException) {
            Log.w(TAG, "Tempo limite ao chamar ${request.url.encodedPath}", error)
            throw BackendTimeoutException("Tempo limite ao chamar o backend", error)
        } catch (error: IOException) {
            Log.w(TAG, "Falha de rede ao chamar ${request.url.encodedPath}", error)
            throw BackendConnectionException("Falha de rede ao chamar o backend", error)
        }

        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val detail = runCatching {
                    json.decodeFromString(ApiErrorDto.serializer(), body).detail
                }.getOrNull()
                // H3-29: o corpo cru (JSON/HTML) fica só no Logcat; a UI recebe
                // mensagem curta e acionável via `uiErrorFrom`.
                Log.w(TAG, "Backend HTTP ${it.code}: ${body.take(300)}")
                throw BackendHttpException(
                    code = it.code,
                    message = detail?.takeIf { d -> d.isNotBlank() }
                        ?.let { d -> "Backend HTTP ${it.code}: $d" }
                        ?: "Backend HTTP ${it.code}",
                )
            }
            return try {
                json.decodeFromString<T>(body)
            } catch (error: Exception) {
                Log.w(TAG, "Resposta sem o JSON esperado: ${body.take(300)}", error)
                throw BackendResponseException("Resposta sem o JSON esperado", error)
            }
        }
    }

    companion object {
        private const val TAG = "BackendClient"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // A geração com IA pode demorar; o fallback local responde rápido.
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
