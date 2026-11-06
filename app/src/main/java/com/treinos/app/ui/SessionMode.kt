package com.treinos.app.ui

enum class SessionKind {
    STRENGTH, RUN, MOBILITY;

    companion object {
        fun fromModality(modality: String?): SessionKind = when (modality) {
            "run" -> RUN
            "mobility" -> MOBILITY
            else -> STRENGTH
        }
    }
}
