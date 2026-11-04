package com.treinos.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

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
        } catch (error: IOException) {
            throw IOException(
                "Não foi possível falar com o backend (${baseUrlProvider()}). " +
                    "Verifique se ele está ligado e se o endereço está correto.",
                error,
            )
        }

        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val detail = runCatching {
                    json.decodeFromString(ApiErrorDto.serializer(), body).detail
                }.getOrNull()
                throw IOException("Backend respondeu ${it.code}: ${detail ?: body.take(300)}")
            }
            return try {
                json.decodeFromString<T>(body)
            } catch (error: Exception) {
                throw IOException("Resposta inesperada do backend: ${body.take(300)}", error)
            }
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // A geração com IA pode demorar; o fallback local responde rápido.
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
