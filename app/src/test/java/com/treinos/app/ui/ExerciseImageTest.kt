package com.treinos.app.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult
import com.treinos.app.ui.components.ExerciseImage
import java.io.IOException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * H3-32: a imagem de exercício nunca deixa um quadrado vazio — sem URL, durante
 * o carregamento e quando a rede falha, cai no fallback de letra.
 */
@RunWith(RobolectricTestRunner::class)
class ExerciseImageTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun erro_de_rede_mostra_fallback_letra() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val failing = ImageLoader.Builder(context)
            .components {
                add(
                    object : Interceptor {
                        override suspend fun intercept(chain: Interceptor.Chain): ImageResult =
                            ErrorResult(null, chain.request, IOException("offline"))
                    },
                )
            }
            .build()
        Coil.setImageLoader(failing)

        composeRule.setContent {
            ExerciseImage(
                imageUrl = "https://example.invalid/supino.jpg",
                exerciseName = "Supino reto",
                size = 72.dp,
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("S").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("S").assertExists()
    }

    @Test
    fun sem_url_mostra_letra() {
        composeRule.setContent {
            ExerciseImage(imageUrl = null, exerciseName = "Agachamento", size = 72.dp)
        }
        composeRule.onNodeWithText("A").assertExists()
    }
}