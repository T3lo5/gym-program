package com.treinos.app.data.discovery

/** Onde o app guarda o endereço do backend; implementada por [com.treinos.app.data.prefs.AppPreferences]. */
interface BackendUrlStore {
    var backendUrl: String
}

sealed interface HealthCheck {
    data class Reachable(val model: String, val catalogSize: Int) : HealthCheck
    data class Unreachable(val message: String) : HealthCheck
}

/** Sonda `GET <url>/health` de um endereço que ainda não foi salvo. */
interface BackendHealthChecker {
    suspend fun check(url: String): HealthCheck
}

sealed interface ConnectOutcome {
    data class Connected(val backend: DiscoveredBackend, val model: String, val catalogSize: Int) : ConnectOutcome
    data class Refused(val message: String) : ConnectOutcome
}

/**
 * Conecta a um backend descoberto na LAN sem trocar de backend por engano.
 *
 * A URL descoberta só substitui a URL salva depois que `GET /health` responde
 * 200; qualquer falha mantém exatamente o endereço anterior.
 */
class BackendConnector(
    private val checker: BackendHealthChecker,
    private val urls: BackendUrlStore,
) {
    suspend operator fun invoke(candidate: DiscoveredBackend): ConnectOutcome {
        val host = candidate.host.trim().trim('[', ']')
        if (host.isBlank() || candidate.port !in 1..65535) {
            return ConnectOutcome.Refused("Endereço encontrado é inválido: ${candidate.host}:${candidate.port}.")
        }
        val url = DiscoveredBackend.buildUrl(host, candidate.port)

        return when (val health = checker.check(url)) {
            is HealthCheck.Reachable -> {
                urls.backendUrl = url
                ConnectOutcome.Connected(
                    backend = candidate.copy(host = host, url = url),
                    model = health.model,
                    catalogSize = health.catalogSize,
                )
            }

            is HealthCheck.Unreachable -> ConnectOutcome.Refused(health.message)
        }
    }
}
