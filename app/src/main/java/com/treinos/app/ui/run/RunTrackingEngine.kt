package com.treinos.app.ui.run

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI

data class RunTrackingSample(
    val latitude: Double,
    val longitude: Double,
    val elapsedMillis: Long,
)

data class RunTrackingState(
    val route: List<RunTrackingSample> = emptyList(),
    val distanceMeters: Double = 0.0,
    val heartRate: Int? = null,
    val intervalPhase: String = "warmup",
    val intervalRemainingSeconds: Int = 300,
) {
    val paceSecondsPerKm: Int?
        get() = distanceMeters.takeIf { it >= 100.0 }?.let {
            val elapsedSeconds = route.lastOrNull()?.elapsedMillis?.div(1000L) ?: 0L
            if (elapsedSeconds > 0L) (elapsedSeconds * 1000.0 / it).roundToInt() else null
        }
}

fun haversineMeters(from: RunTrackingSample, to: RunTrackingSample): Double {
    val earthRadiusMeters = 6_371_000.0
    val lat1 = from.latitude * PI / 180.0
    val lat2 = to.latitude * PI / 180.0
    val deltaLat = lat2 - lat1
    val deltaLon = (to.longitude - from.longitude) * PI / 180.0
    val a = sin(deltaLat / 2.0) * sin(deltaLat / 2.0) +
        cos(lat1) * cos(lat2) * sin(deltaLon / 2.0) * sin(deltaLon / 2.0)
    val c = 2.0 * asin(sqrt(a))
    return earthRadiusMeters * c
}

fun addRunTrackingSample(state: RunTrackingState, sample: RunTrackingSample): RunTrackingState {
    if (!sample.latitude.isFinite() || !sample.longitude.isFinite()) return state
    if (sample.latitude !in -90.0..90.0 || sample.longitude !in -180.0..180.0) return state
    val previous = state.route.lastOrNull()
    val meters = if (previous == null) 0.0 else haversineMeters(previous, sample)
    if (previous != null && (meters < 2.0 || meters > 2_000.0)) return state
    return state.copy(
        route = state.route + sample,
        distanceMeters = state.distanceMeters + meters,
    )
}

fun intervalPhaseAt(elapsedSeconds: Long): Pair<String, Int> {
    val elapsed = elapsedSeconds.coerceAtLeast(0L)
    if (elapsed < 300L) return "warmup" to (300L - elapsed).toInt()
    val afterWarmup = elapsed - 300L
    val cycle = afterWarmup % 120L
    return if (cycle < 60L) {
        "work" to (60L - cycle).toInt()
    } else {
        "recover" to (120L - cycle).toInt()
    }
}
