package com.treinos.app.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.treinos.app.data.local.WorkoutDayEntity
import com.treinos.app.data.local.WorkoutDayWithExercises
import com.treinos.app.data.local.WorkoutPlanExerciseEntity
import com.treinos.app.data.local.WorkoutPlanWithDetails
import com.treinos.app.data.local.WorkoutPlanEntity
import com.treinos.app.ui.ModalityTab
import com.treinos.app.ui.PlanCycle
import com.treinos.app.ui.SessionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var plan: WorkoutPlanWithDetails

    @Before
    fun setUp() {
        plan = WorkoutPlanWithDetails(
            plan = WorkoutPlanEntity(
                id = 1,
                name = "Semana híbrida",
                goal = "general",
                daysPerWeek = 7,
            ),
            days = listOf(
                day(weekday = 1, dayOrder = 0, modality = "strength", exercises = 5),
                day(weekday = 1, dayOrder = 1, modality = "run"),
                day(weekday = 2, dayOrder = 0, modality = "mobility"),
            ),
        )
    }

    @Test
    fun mostra_abas_apenas_das_modalidades_do_plano() {
        composeRule.setContent {
            HomeScreen(
                plan = plan,
                inProgressSession = null,
                onGenerate = {},
                onHistory = {},
                onSettings = {},
                onStartDay = {},
                onResume = {},
                onLogActivity = {},
                onOpenMobility = {},
            )
        }

        composeRule.onNodeWithText("Academia").assertIsDisplayed()
        composeRule.onNodeWithText("Corrida").assertIsDisplayed()
        composeRule.onNodeWithText("Mobilidade").assertIsDisplayed()
    }

    @Test
    fun card_de_corrida_mostra_distancia_e_duracao() {
        composeRule.setContent {
            HomeScreen(
                plan = plan,
                inProgressSession = null,
                onGenerate = {},
                onHistory = {},
                onSettings = {},
                onStartDay = {},
                onResume = {},
                onLogActivity = {},
                onOpenMobility = {},
            )
        }

        composeRule.onNodeWithText("Corrida").performClick()
        composeRule.onNodeWithText("Corrida A").assertIsDisplayed()
    }

    @Test
    fun mesmo_dia_com_academia_e_corrida_aparece_nas_duas_abas() {
        composeRule.setContent {
            HomeScreen(
                plan = plan,
                inProgressSession = null,
                onGenerate = {},
                onHistory = {},
                onSettings = {},
                onStartDay = {},
                onResume = {},
                onLogActivity = {},
                onOpenMobility = {},
            )
        }

        composeRule.onNodeWithText("Academia").assertIsDisplayed()
        composeRule.onNodeWithText("Corrida").assertIsDisplayed()

        assertEquals(2, plan.days.filter { it.day.weekday == 1 }.size)
    }

    private fun day(
        weekday: Int,
        dayOrder: Int,
        modality: String,
        exercises: Int = 1,
    ): WorkoutDayWithExercises =
        WorkoutDayWithExercises(
            day = WorkoutDayEntity(
                id = 0,
                planId = 0,
                name = when (modality) {
                    "strength" -> "Treino A"
                    "run" -> "Corrida A"
                    "mobility" -> "Mobilidade A"
                    else -> "Dia"
                },
                dayOrder = dayOrder,
                weekday = weekday,
                modality = modality,
            ),
            exercises = List(exercises) { i ->
                WorkoutPlanExerciseEntity(
                    id = i.toLong(),
                    dayId = 0,
                    exerciseId = "ex$i",
                    exerciseOrder = i,
                    sets = 3,
                )
            },
        )
}
