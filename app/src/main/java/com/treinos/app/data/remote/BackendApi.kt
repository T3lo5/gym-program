package com.treinos.app.data.remote

/**
 * Contrato das chamadas de rede usadas pelo [com.treinos.app.data.WorkoutRepository].
 *
 * Existe para que a restauração seja testada com um fake, sem subir HTTP.
 */
interface BackendApi {
    suspend fun health(): HealthResponse

    /** H3-40: submete a geração como job assíncrono; 202 imediato com jobId. */
    suspend fun submitGeneration(request: GeneratePlanRequest): GenerateJobAck

    /** H3-40: consulta o status do job sob demanda (Atualizar / volta ao foreground). */
    suspend fun generationStatus(jobId: String): GenerateJobStatus

    /** H3-40: cancela um job em andamento (idempotente). */
    suspend fun cancelGeneration(jobId: String): GenerateJobStatus

    suspend fun sync(request: SyncRequestDto): SyncResponseDto

    suspend fun syncDownload(deviceId: String): SyncDownloadDto

    suspend fun catalog(): CatalogResponseDto

    suspend fun reviewPlan(request: ReviewPlanRequest): ReviewPlanResponse
}
