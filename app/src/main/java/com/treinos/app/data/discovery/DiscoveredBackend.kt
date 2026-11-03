package com.treinos.app.data.discovery

data class DiscoveredBackend(
    val name: String,
    val host: String,
    val port: Int,
    val url: String = if (host.contains(":")) "http://[$host]:$port" else "http://$host:$port",
) {
    companion object {
        fun from(host: String, port: Int, name: String = ""): DiscoveredBackend? {
            val h = host.trim().trim('[', ']')
            if (h.isBlank()) return null
            if (port !in 1..65535) return null
            val displayName = name.ifBlank { "TreinosBackend" }
            val url = if (h.contains(":")) "http://[$h]:$port" else "http://$h:$port"
            return DiscoveredBackend(name = displayName, host = h, port = port, url = url)
        }
    }
}

fun dedupeBackends(list: List<DiscoveredBackend>): List<DiscoveredBackend> {
    val map = linkedMapOf<String, DiscoveredBackend>()
    for (b in list) {
        val key = "${b.host}:${b.port}"
        val existing = map[key]
        map[key] = if (existing == null) b else existing.copy(name = b.name.ifBlank { existing.name }, url = b.url)
    }
    return map.values.toList()
}
