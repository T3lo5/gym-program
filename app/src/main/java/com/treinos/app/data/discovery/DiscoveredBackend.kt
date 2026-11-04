package com.treinos.app.data.discovery

/**
 * Backend encontrado na rede local via NSD/mDNS.
 *
 * O `url` é sempre montado a partir de `host` e `port`: IPv6 recebe colchetes
 * para continuar sendo uma URL válida.
 */
data class DiscoveredBackend(
    val name: String,
    val host: String,
    val port: Int,
    val version: String? = null,
    val url: String = buildUrl(host, port),
) {
    /** TXT `version` ausente: o backend não se identificou, então a UI avisa. */
    val isVersionKnown: Boolean get() = !version.isNullOrBlank()

    val endpoint: String get() = "$host:$port"

    companion object {
        fun buildUrl(host: String, port: Int): String =
            if (host.contains(":")) "http://[$host]:$port" else "http://$host:$port"

        fun from(host: String, port: Int, name: String = "", version: String? = null): DiscoveredBackend? {
            val h = host.trim().trim('[', ']')
            if (h.isBlank()) return null
            if (port !in 1..65535) return null
            val cleanVersion = version?.trim()?.takeIf { it.isNotEmpty() }
            return DiscoveredBackend(
                name = name.ifBlank { "TreinosBackend" },
                host = h,
                port = port,
                version = cleanVersion,
                url = buildUrl(h, port),
            )
        }
    }
}

fun dedupeBackends(list: List<DiscoveredBackend>): List<DiscoveredBackend> {
    val map = linkedMapOf<String, DiscoveredBackend>()
    for (b in list) {
        val key = b.endpoint
        val existing = map[key]
        map[key] = if (existing == null) {
            b
        } else {
            existing.copy(
                name = b.name.ifBlank { existing.name },
                version = b.version ?: existing.version,
            )
        }
    }
    return map.values.toList()
}
