package com.treinos.shared.data

import com.treinos.shared.util.BackendRepository

class ExerciseRepository(
    private val exerciseDao: ExerciseDao,
    private val backendRepository: BackendRepository? = null
)
