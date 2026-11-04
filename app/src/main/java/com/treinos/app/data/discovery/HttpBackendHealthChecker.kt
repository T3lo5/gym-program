package com.treinos.app.data.discovery

import com.treinos.app.data.remote.HealthResponse
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Sonda de health real, com timeout curto: um backend fora da rede local não
 * pode travar o toque em "Conectar" esperando o timeout longo do sync.
 */
class HttpBackendHealthChecker(
    private val client: OkHttpClient = defaultClient(),
) : BackendHealthChecker {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun check(url: String): HealthCheck = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$url/health").get().build()
        val response = try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            return@withContext HealthCheck.Unreachable(
                "Encontrado em ${url.removePrefix("http://")}, mas não respondeu.",
            )
        }

        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                return@use HealthCheck.Unreachable(
                    "Encontrado, mas respondeu ${it.code} em vez de ok.",
                )
            }
            val health = try {
                json.decodeFromString(HealthResponse.serializer(), body)
            } catch (error: Exception) {
                return@use HealthCheck.Unreachable("Encontrado, mas a resposta não é do Treinos backend.")
            }
            HealthCheck.Reachable(model = health.model, catalogSize = health.catalogSize)
        }
    }

    companion object {
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }
}
