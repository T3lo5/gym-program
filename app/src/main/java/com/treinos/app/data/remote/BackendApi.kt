package com.treinos.app.data.remote

/**
 * Contrato das chamadas de rede usadas pelo [com.treinos.app.data.WorkoutRepository].
 *
 * Existe para que a restauração seja testada com um fake, sem subir HTTP.
 */
interface BackendApi {
    suspend fun health(): HealthResponse

    suspend fun generatePlan(request: GeneratePlanRequest): GeneratePlanResponse

    suspend fun sync(request: SyncRequestDto): SyncResponseDto

    suspend fun syncDownload(deviceId: String): SyncDownloadDto

    suspend fun catalog(): CatalogResponseDto
}
