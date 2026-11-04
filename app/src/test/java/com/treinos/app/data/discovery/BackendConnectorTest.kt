package com.treinos.app.data.discovery

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendConnectorTest {

    private class FakeChecker(
        var result: HealthCheck = HealthCheck.Reachable(model = "gpt", catalogSize = 12),
    ) : BackendHealthChecker {
        val probed = mutableListOf<String>()

        override suspend fun check(url: String): HealthCheck {
            probed += url
            return result
        }
    }

    private class FakeUrlStore(override var backendUrl: String) : BackendUrlStore

    private val reachable = HealthCheck.Reachable(model = "SenseNova", catalogSize = 40)

    @Test
    fun salva_url_quando_health_responde() = runTest {
        val checker = FakeChecker(reachable)
        val store = FakeUrlStore("http://10.0.0.9:3000")
        val candidate = DiscoveredBackend.from("192.168.0.10", 3000, "TreinosBackend")!!

        val outcome = BackendConnector(checker, store)(candidate)

        assertTrue(outcome is ConnectOutcome.Connected)
        assertEquals("http://192.168.0.10:3000", store.backendUrl)
        assertEquals(listOf("http://192.168.0.10:3000"), checker.probed)
        val connected = outcome as ConnectOutcome.Connected
        assertEquals("SenseNova", connected.model)
        assertEquals(40, connected.catalogSize)
    }

    @Test
    fun nao_salva_quando_health_nao_responde() = runTest {
        val checker = FakeChecker(HealthCheck.Unreachable("Encontrado, mas não respondeu."))
        val store = FakeUrlStore("http://10.0.0.9:3000")
        val candidate = DiscoveredBackend.from("192.168.0.10", 3000)!!

        val outcome = BackendConnector(checker, store)(candidate)

        assertTrue(outcome is ConnectOutcome.Refused)
        assertEquals("Encontrado, mas não respondeu.", (outcome as ConnectOutcome.Refused).message)
        assertEquals("http://10.0.0.9:3000", store.backendUrl)
    }

    @Test
    fun mantem_url_anterior_quando_a_sonda_falha() = runTest {
        val antes = "http://10.0.2.2:3000"
        val store = FakeUrlStore(antes)
        val connector = BackendConnector(
            FakeChecker(HealthCheck.Unreachable("sem resposta")),
            store,
        )

        connector(DiscoveredBackend.from("192.168.0.99", 3000)!!)

        assertEquals(antes, store.backendUrl)
    }

    @Test
    fun recusa_endereco_invalido_sem_sondar_a_rede() = runTest {
        val checker = FakeChecker(reachable)
        val store = FakeUrlStore("http://10.0.0.9:3000")
        val broken = DiscoveredBackend(name = "X", host = "", port = 0, url = "http://:0")

        val outcome = BackendConnector(checker, store)(broken)

        assertTrue(outcome is ConnectOutcome.Refused)
        assertEquals("http://10.0.0.9:3000", store.backendUrl)
        assertTrue(checker.probed.isEmpty())
    }

    @Test
    fun normaliza_host_com_colchetes_antes_de_salvar() = runTest {
        val checker = FakeChecker(reachable)
        val store = FakeUrlStore("http://10.0.0.9:3000")
        val candidate = DiscoveredBackend.from("[::1]", 8080)!!

        BackendConnector(checker, store)(candidate)

        assertEquals("http://[::1]:8080", store.backendUrl)
        assertEquals(listOf("http://[::1]:8080"), checker.probed)
    }
}
