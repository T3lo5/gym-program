package com.treinos.app.ui

import com.treinos.app.data.DeviceIdStore
import com.treinos.app.data.SnapshotDownloader
import com.treinos.app.data.SyncDownloadResult

sealed interface RestoreOutcome {
    data class Success(
        val planCount: Int,
        val sessionCount: Int,
    ) : RestoreOutcome

    data object Empty : RestoreOutcome
    data object InvalidDeviceId : RestoreOutcome
    data class Failed(val message: String) : RestoreOutcome
}

/**
 * Restaura o snapshot de outro aparelho sem assumir a identidade dele antes da
 * hora: o `deviceId` digitado vale apenas para a chamada de rede e só é
 * persistido depois que um snapshot válido foi aplicado.
 */
class RestoreByDeviceId(
    private val downloader: SnapshotDownloader,
    private val devices: DeviceIdStore,
) {
    suspend operator fun invoke(deviceId: String): RestoreOutcome {
        val requested = deviceId.trim()
        if (requested.isEmpty()) return RestoreOutcome.InvalidDeviceId

        val result = try {
            downloader.downloadSync(requested)
        } catch (error: Exception) {
            return RestoreOutcome.Failed(
                error.message ?: "Não foi possível restaurar o backup do dispositivo $requested.",
            )
        }

        return when (result) {
            SyncDownloadResult.Empty -> RestoreOutcome.Empty
            is SyncDownloadResult.Restored -> {
                devices.deviceId = requested
                RestoreOutcome.Success(
                    planCount = result.planCount,
                    sessionCount = result.sessionCount,
                )
            }
        }
    }
}
