package com.treinos.app.data.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveredBackendTest {

    @Test
    fun monta_url_http() {
        val b = DiscoveredBackend.from("192.168.0.10", 3000, "TreinosBackend")!!
        assertEquals("http://192.168.0.10:3000", b.url)
    }

    @Test
    fun ignora_porta_invalida() {
        assertNull(DiscoveredBackend.from("192.168.0.10", 0))
        assertNull(DiscoveredBackend.from("192.168.0.10", 70000))
    }

    @Test
    fun ignora_host_vazio() {
        assertNull(DiscoveredBackend.from("   ", 3000))
        assertNull(DiscoveredBackend.from("", 3000))
    }

    @Test
    fun deduplica_por_host_e_porta() {
        val list = listOf(
            DiscoveredBackend.from("192.168.0.10", 3000, "A")!!,
            DiscoveredBackend.from("192.168.0.10", 3000, "B")!!,
        )
        val deduped = dedupeBackends(list)
        assertEquals(1, deduped.size)
        assertEquals("B", deduped[0].name)
    }

    @Test
    fun aceita_ipv4_e_ipv6_entre_colchetes() {
        val v4 = DiscoveredBackend.from("10.0.0.1", 8080)!!
        assertEquals("http://10.0.0.1:8080", v4.url)
        val v6 = DiscoveredBackend.from("::1", 8080)!!
        assertEquals("http://[::1]:8080", v6.url)
    }

    @Test
    fun sem_txt_de_versao_marca_desconhecida() {
        val semVersion = DiscoveredBackend.from("192.168.0.10", 3000, version = "   ")!!
        assertFalse(semVersion.isVersionKnown)

        val comVersion = DiscoveredBackend.from("192.168.0.10", 3000, version = "1.0.0")!!
        assertTrue(comVersion.isVersionKnown)
        assertEquals("1.0.0", comVersion.version)
    }

    @Test
    fun deduplica_preservando_versao_e_nome() {
        val deduped = dedupeBackends(
            listOf(
                DiscoveredBackend.from("192.168.0.10", 3000, "A", "1.0.0")!!,
                DiscoveredBackend.from("192.168.0.10", 3000, "B")!!,
            ),
        )
        assertEquals(1, deduped.size)
        assertEquals("B", deduped[0].name)
        assertEquals("1.0.0", deduped[0].version)
        assertEquals("192.168.0.10:3000", deduped[0].endpoint)
    }
}
