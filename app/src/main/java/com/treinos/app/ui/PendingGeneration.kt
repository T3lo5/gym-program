package com.treinos.app.ui

import com.treinos.app.data.remote.GeneratePlanRequest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Envelope de uma geração em andamento (H3-40).
 *
 * Persistido em `pending_generation_v1` no momento do submit (202). Sobrevive
 * a processo morto: ao reabrir o app, o envelope é restaurado e o status do
 * job é consultado sem novo submit. O retry de job `failed` reenvia o
 * `request` do envelope — funciona mesmo quando `lastGenerateArgs` já não
 * existe mais.
 *
 * `config` é a última configuração do formulário (GenerateConfig); null quando
 * não houve config salva.
 */
@Serializable
data class PendingGeneration(
    val jobId: String,
    val kind: String,
    val request: GeneratePlanRequest,
    val config: GenerateConfig?,
)

/**
 * Codec JSON puro de [PendingGeneration] (sem dependência de Android).
 *
 * `ignoreUnknownKeys` mantém a leitura tolerante a campos de versões novas; a
 * versão do formato vive no nome da chave de prefs (`pending_generation_v1`).
 * Entrada vazia ou corrompida devolve null — o chamador limpa o envelope sem
 * crash.
 */
object PendingGenerationCodec {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(envelope: PendingGeneration): String = json.encodeToString(envelope)

    fun decode(raw: String?): PendingGeneration? {
        if (raw.isNullOrBlank()) return null
        return try {
            json.decodeFromString(PendingGeneration.serializer(), raw)
        } catch (_: Exception) {
            null
        }
    }
}
