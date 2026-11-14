package com.treinos.app.ui

import android.content.Context
import com.treinos.app.R
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
 *
 * H3-52: as mensagens nascem de recursos e são resolvidas com o [Context]
 * recebido — a classe continua sem saber de rede além do downloader.
 */
class RestoreByDeviceId(
    private val downloader: SnapshotDownloader,
    private val devices: DeviceIdStore,
    private val context: Context,
) {
    suspend operator fun invoke(deviceId: String): RestoreOutcome {
        val requested = deviceId.trim()
        if (requested.isEmpty()) return RestoreOutcome.InvalidDeviceId

        val result = try {
            downloader.downloadSync(requested)
        } catch (error: Exception) {
            return RestoreOutcome.Failed(
                // H3-29: mensagem acionável; o detalhe técnico fica no Logcat.
                knownErrorResource(error)?.resolve(context)
                    ?: context.getString(R.string.msg_restore_device_error, requested),
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
