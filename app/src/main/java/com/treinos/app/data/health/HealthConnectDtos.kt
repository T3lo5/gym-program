package com.treinos.app.data.health

/**
 * H4-9: tipos puros da integração com o Health Connect.
 *
 * Nada aqui importa o SDK (`androidx.health.connect.*`): o mapeamento fica
 * testável em JVM pura e o SDK fica confinado ao gateway
 * (`HealthConnectGateway`/`HealthConnectClientGateway`).
 */

/** Disponibilidade do Health Connect no aparelho. */
enum class HealthConnectAvailability {
    /** SDK e provedor prontos: a seção aparece e o app pode conectar. */
    AVAILABLE,

    /** O app do Health Connect existe, mas precisa ser atualizado. */
    PROVIDER_UPDATE_REQUIRED,

    /** Indisponível (API < 28 ou perfil de trabalho): a seção nem aparece. */
    UNAVAILABLE,
}

/**
 * Rascunho de uma sessão concluída pronta para virar `ExerciseSessionRecord`.
 * `exerciseType` já é a constante do Health Connect, resolvida pelo mapper;
 * `clientRecordId` é a chave de idempotência ("treinos:session:<id>").
 */
data class HealthSessionDraft(
    val clientRecordId: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val exerciseType: Int,
    val title: String?,
    val notes: String?,
    /** `sessionRpe` quando está em 1.0..10.0; fora disso `null`. */
    val rpe: Float?,
)

/** Uma leitura de peso já validada, pronta para `body_measurements`. */
data class HealthWeightSample(val timeMillis: Long, val weightKg: Double)

/** O total de passos de um período, já validado. */
data class HealthStepsSample(val startMillis: Long, val endMillis: Long, val count: Long)

/** O que o usuário concedeu; a UI habilita só o que dá para fazer (regra 3). */
data class HealthConnectPermissionState(
    val canWriteExercise: Boolean = false,
    val canReadWeight: Boolean = false,
    val canReadSteps: Boolean = false,
) {
    val hasAnyPermission: Boolean
        get() = canWriteExercise || canReadWeight || canReadSteps
}

/**
 * Registro "cru" vindo do SDK, antes da validação do mapper. Existe para o
 * `HealthRecordMapper` continuar puro: o gateway converte os `Record` do SDK
 * nestes tipos e o mapper aplica as regras de faixa/ordenação.
 */
data class RawWeight(val timeMillis: Long, val weightKg: Double)

/** Registro "cru" de passos agregados de um período, antes da validação. */
data class RawSteps(val startMillis: Long, val endMillis: Long, val count: Long)

/**
 * `source` gravado em `body_measurements` (H4-8) para peso vindo do Health
 * Connect. Mesmo valor esperado pela H4-8; sincronizar duas vezes não duplica
 * porque a deduplicação compara `measuredAt` dentro deste source (regra 9).
 */
const val HEALTH_CONNECT_MEASUREMENT_SOURCE = "health_connect"
