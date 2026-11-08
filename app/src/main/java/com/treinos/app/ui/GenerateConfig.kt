package com.treinos.app.ui

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Rascunho editável de uma modalidade da semana híbrida (H3-30).
 *
 * É o modelo da tela "Gerar" e também o formato persistido em
 * `generate_config_v1`: a última configuração usada volta pré-preenchida na
 * próxima abertura. O `type` viaja no objeto para o JSON persistido ser
 * auto-descritivo (a ordem da lista não significa nada).
 */
@Serializable
data class ModalityDraft(
    val type: String,
    val included: Boolean = false,
    val sessionsPerWeek: Int = 1,
    val level: String = "intermediate",
    val focus: String? = null,
    val recent5kSeconds: Int? = null,
    val sessionDurationMinutes: Int = 60,
)

/**
 * Última configuração da tela "Gerar" (H3-30).
 *
 * Persistida a cada geração bem-sucedida (`generate_config_v1`) e restaurada
 * na abertura da tela, para que gerar de novo não exija refazer o formulário.
 * O perfil (peso/altura/sexo) viaja junto porque é o que o usuário preencheu
 * na tela; o `AppPreferences` continua sendo a fonte usada no request.
 *
 * Primeira abertura sem config salva: musculação 4x pré-incluída — o usuário
 * consegue gerar um plano só com o CTA.
 */
@Serializable
data class GenerateConfig(
    val planKind: String = "main",
    val goal: String = "hypertrophy",
    val daysPerWeek: Int = 4,
    val level: String = "intermediate",
    val equipment: List<String> = DEFAULT_EQUIPMENT,
    val muscles: List<String> = emptyList(),
    val sessionDurationMinutes: Int = 60,
    val reviewWeeks: Int = 4,
    val environment: String = "gym",
    val bodyWeightKgText: String = "",
    val heightCmText: String = "",
    val sexChoice: String? = null,
    val modalities: List<ModalityDraft> = DEFAULT_MODALITIES,
) {
    /** Drafts por tipo de modalidade; tipo ausente na config cai no draft padrão. */
    val draftsByType: Map<String, ModalityDraft>
        get() {
            val saved = modalities.associateBy { it.type }
            return MODALITY_TYPES.associateWith { type ->
                saved[type] ?: ModalityDraft(type = type)
            }
        }

    companion object {
        val MODALITY_TYPES = listOf("strength", "run", "mobility")

        val DEFAULT_EQUIPMENT = listOf("body only", "dumbbell", "barbell")

        /**
         * Defaults da primeira abertura: "Musculação 4x" já inclusa; corrida e
         * mobilidade aparecem desligadas com os valores padrão de draft.
         */
        val DEFAULT_MODALITIES = listOf(
            ModalityDraft(type = "strength", included = true, sessionsPerWeek = 4),
            ModalityDraft(type = "run"),
            ModalityDraft(type = "mobility"),
        )

        val DEFAULT = GenerateConfig()
    }
}

/**
 * Codec JSON puro de [GenerateConfig] (sem dependência de Android).
 *
 * `ignoreUnknownKeys` mantém a leitura tolerante a campos de versões novas;
 * a versão do formato vive no nome da chave de prefs (`generate_config_v1`).
 * Entrada vazia ou corrompida devolve null — o chamador cai no default.
 */
object GenerateConfigCodec {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(config: GenerateConfig): String = json.encodeToString(config)

    fun decode(raw: String?): GenerateConfig? {
        if (raw.isNullOrBlank()) return null
        return try {
            json.decodeFromString(GenerateConfig.serializer(), raw)
        } catch (_: Exception) {
            null
        }
    }
}
