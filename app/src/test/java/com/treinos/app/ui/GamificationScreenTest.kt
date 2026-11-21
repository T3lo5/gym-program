package com.treinos.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.treinos.app.ui.gamification.UserGamificationState
import com.treinos.app.ui.gamification.GamificationScreen
import com.treinos.app.ui.TreinosTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * H6-8: tela de progresso/gamificação — renderiza nível e XP.
 * Robolectric + Compose. A lógica de badges é coberta por GamificationEngineTest.
 */
@RunWith(RobolectricTestRunner::class)
class GamificationScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val estadoBase = UserGamificationState(
        level = 1,
        xp = 0,
        xpToNext = 100,
        badges = emptyList(),
        totalSessions = 0,
        currentStreak = 0,
        totalPrs = 0,
    )

    @Test
    fun tela_mostra_nivel_e_xp() {
        composeRule.setContent {
            TreinosTheme {
                GamificationScreen(state = estadoBase, onBack = {})
            }
        }

        composeRule.onNodeWithText("Nível 1").assertIsDisplayed()
        composeRule.onNodeWithText("0 XP").assertIsDisplayed()
    }
}
