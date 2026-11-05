package com.treinos.app.ui

enum class ScreenMotion { FORWARD, BACKWARD, NONE }

fun motionFor(from: Screen, to: Screen): ScreenMotion = when {
    from == to -> ScreenMotion.NONE
    from == Screen.Home -> ScreenMotion.FORWARD
    to == Screen.Home -> ScreenMotion.BACKWARD
    else -> ScreenMotion.NONE
}

fun motionDurationMillis(animatorScale: Float): Int = when {
    animatorScale <= 0f -> 0
    animatorScale < 1f -> 150
    else -> 250
}

fun shouldShowLoading(elapsedMs: Long): Boolean = elapsedMs >= 150L
