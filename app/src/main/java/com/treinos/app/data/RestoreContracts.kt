package com.treinos.app.data

/**
 * Resultado de uma restauração a partir do snapshot do backend.
 *
 * [Empty] significa "o servidor não tem nada para este aparelho": o banco local
 * não é tocado. A substituição só acontece em [Restored].
 */
sealed interface SyncDownloadResult {

    data class Restored(
        val planCount: Int,
        val sessionCount: Int,
    ) : SyncDownloadResult

    data object Empty : SyncDownloadResult
}

/** Download de um snapshot por aparelho; implementada por [WorkoutRepository]. */
interface SnapshotDownloader {
    suspend fun downloadSync(deviceId: String): SyncDownloadResult
}

/** Onde o app guarda o identificador do aparelho; implementada por [com.treinos.app.data.prefs.AppPreferences]. */
interface DeviceIdStore {
    var deviceId: String
}

/** Onde o app guarda o perfil (peso/altura); implementada por [com.treinos.app.data.prefs.AppPreferences]. */
interface ProfileStore {
    var bodyWeightKg: Double?
    var heightCm: Int?
}
