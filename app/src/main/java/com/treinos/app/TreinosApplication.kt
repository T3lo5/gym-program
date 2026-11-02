package com.treinos.app

import android.app.Application
import android.content.Context
import com.treinos.app.data.WorkoutRepository
import com.treinos.app.data.local.AppDatabase
import com.treinos.app.data.prefs.AppPreferences
import com.treinos.app.data.remote.BackendClient

/**
 * Container manual de dependências (sem Hilt).
 *
 * Mantém uma única instância do banco Room, das preferências e do cliente HTTP
 * durante toda a vida do processo.
 */
class AppContainer(context: Context) {

    val preferences: AppPreferences = AppPreferences(context)

    private val database: AppDatabase = AppDatabase.get(context)

    private val backendClient: BackendClient = BackendClient(
        baseUrlProvider = { preferences.backendUrl }
    )

    val repository: WorkoutRepository = WorkoutRepository(
        dao = database.workoutDao(),
        backendClient = backendClient,
        preferences = preferences,
    )
}

class TreinosApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
