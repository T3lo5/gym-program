package com.treinos.shared.data

class ExerciseRepository(
    private val exerciseDao: ExerciseDao,
    private val backendRepository: BackendRepository?
)
