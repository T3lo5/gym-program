package com.treinos.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.treinos.app.data.WorkoutRepository
import com.treinos.app.data.local.AppDatabase
import com.treinos.app.data.prefs.AppPreferences
import com.treinos.app.ui.TreinosTheme
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NavigationBottomBarTest {

    private lateinit var context: Context
    private lateinit var repository: WorkoutRepository
    private lateinit var viewModel: AppViewModel

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = database.workoutDao()
        val preferences = AppPreferences(context)
        preferences.deviceId = "device"
        repository = WorkoutRepository(dao, FakeBackendApiNav(), preferences)
        viewModel = AppViewModel(
            context = context,
            repository = repository,
            preferences = preferences,
        )
    }

    @After
    fun tearDown() = runTest {}

    @Test
    fun bottom_bar_navega_entre_hubs() = runTest {
        composeRule.setContent {
            TreinosTheme {
                Row {
                    Button(onClick = { viewModel.goHome() }) { Text("Treinar") }
                    Button(onClick = { viewModel.openHistory() }) { Text("Histórico") }
                    Button(onClick = { viewModel.openSettings() }) { Text("Ajustes") }
                }
            }
        }

        // Home -> Histórico
        composeRule.onNodeWithText("Histórico").performClick()
        assertEquals(Screen.History, viewModel.screen.value)
        assertEquals(Screen.Home, viewModel.backTarget.value)

        // Histórico -> Ajustes
        composeRule.onNodeWithText("Ajustes").performClick()
        assertEquals(Screen.Settings, viewModel.screen.value)
        assertEquals(Screen.History, viewModel.backTarget.value)

        // Ajustes -> Treinar (Home)
        composeRule.onNodeWithText("Treinar").performClick()
        assertEquals(Screen.Home, viewModel.screen.value)
    }

    @Test
    fun back_target_default_home() = runTest {
        assertEquals(null, viewModel.backTarget.value)
        viewModel.voltar()
        assertEquals(Screen.Home, viewModel.screen.value)
    }
}
