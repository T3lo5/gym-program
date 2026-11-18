package com.treinos.app.data

import com.treinos.app.data.remote.BackendDiagnosticsDto
import com.treinos.app.data.remote.DiagnosticsGenerationDto
import com.treinos.app.data.remote.DiagnosticsSyncDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendDiagnosticsTest {
    @Test
    fun serializa_sem_segredos() {
        val report = BackendDiagnosticsDto(
            status = "healthy",
            catalogLoaded = true,
            databaseWritable = true,
            generation = DiagnosticsGenerationDto(pending = 1, running = 2, failed24h = 3),
            sync = DiagnosticsSyncDto(lastAcceptedAt = null, mutations24h = 4),
        )
        val text = buildString {
            append("status=").append(report.status).append('\n')
            append("generation.pending=").append(report.generation.pending)
        }

        assertTrue(text.contains("status=healthy"))
        assertFalse(text.contains("deviceId"))
        assertFalse(text.contains("apiToken"))
    }

    @Test
    fun estado_degradado_e_reconhecido() {
        val report = BackendDiagnosticsDto(status = "degraded", databaseWritable = false)

        assertFalse(report.databaseWritable)
        assertTrue(report.status == "degraded")
    }
}
