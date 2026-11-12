package com.treinos.wear.ui

enum class WearSessionKind {
    Strength,
    Run,
    Mobility;

    companion object {
        fun from(modality: String): WearSessionKind = when (modality) {
            "run" -> Run
            "mobility" -> Mobility
            else -> Strength
        }
    }
}