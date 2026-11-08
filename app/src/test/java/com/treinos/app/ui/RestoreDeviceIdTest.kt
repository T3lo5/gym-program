package com.treinos.app.ui

import com.treinos.app.data.DeviceIdStore
import com.treinos.app.data.SnapshotDownloader
import com.treinos.app.data.SyncDownloadResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RestoreDeviceIdTest {

    private val devices = FakeDeviceIdStore(deviceId = "id-atual")
    private val downloader = FakeDownloader()
    private val restore by lazy { RestoreByDeviceId(downloader = downloader, devices = devices) }

    @Test
    fun `id vazio nao chama rede`() = runTest {
        val outcome = restore("")

        assertEquals(RestoreOutcome.InvalidDeviceId, outcome)
        assertEquals(emptyList<String>(), downloader.calls)
        assertEquals("id-atual", devices.deviceId)
    }

    @Test
    fun `sucesso persiste device id`() = runTest {
        downloader.result = SyncDownloadResult.Restored(planCount = 2, sessionCount = 5)

        val outcome = restore("  celular-da-esteira  ")

        assertEquals(RestoreOutcome.Success(planCount = 2, sessionCount = 5), outcome)
        assertEquals(listOf("celular-da-esteira"), downloader.calls)
        assertEquals("celular-da-esteira", devices.deviceId)
    }

    @Test
    fun `falha mantem device id anterior`() = runTest {
        downloader.error = IOException("backend offline")

        val outcome = restore("celular-da-esteira")

        // H3-29: a falha continua Failed e o device id é preservado, mas a
        // mensagem vira o texto acionável — o detalhe cru não chega à UI.
        val failed = requireNotNull(outcome as? RestoreOutcome.Failed)
        assertTrue(failed.message.contains("servidor"))
        assertTrue(!failed.message.contains("backend offline"))
        assertEquals(listOf("celular-da-esteira"), downloader.calls)
        assertEquals("id-atual", devices.deviceId)
    }

    @Test
    fun `snapshot vazio mantem device id anterior`() = runTest {
        downloader.result = SyncDownloadResult.Empty

        val outcome = restore("celular-da-esteira")

        assertEquals(RestoreOutcome.Empty, outcome)
        assertEquals(listOf("celular-da-esteira"), downloader.calls)
        assertEquals("id-atual", devices.deviceId)
    }

    private class FakeDownloader : SnapshotDownloader {
        var result: SyncDownloadResult = SyncDownloadResult.Empty
        var error: Exception? = null
        val calls = mutableListOf<String>()

        override suspend fun downloadSync(deviceId: String): SyncDownloadResult {
            calls.add(deviceId)
            error?.let { throw it }
            return result
        }
    }

    private class FakeDeviceIdStore(override var deviceId: String) : DeviceIdStore
}
