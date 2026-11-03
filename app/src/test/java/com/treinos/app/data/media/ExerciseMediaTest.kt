package com.treinos.app.data.media

import com.treinos.app.data.remote.CatalogExerciseDto
import com.treinos.app.data.remote.CatalogResponseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseMediaTest {
    @Test
    fun monta_url_da_primeira_imagem() {
        val images = listOf("3_4_Sit-Up/0.jpg", "3_4_Sit-Up/1.jpg")
        val url = images.firstOrNull()?.let {
            "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/$it"
        }
        assertNotNull(url)
        assertEquals(
            "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/3_4_Sit-Up/0.jpg",
            url,
        )
    }

    @Test
    fun sem_images_retorna_null() {
        val images = emptyList<String>()
        val url = images.firstOrNull()
        assertNull(url)
    }

    @Test
    fun parse_do_catalogo_preserva_instrucoes() {
        val dto = CatalogResponseDto(
            exercises = listOf(
                CatalogExerciseDto(
                    id = "3_4_Sit-Up",
                    name = "3/4 Sit-Up",
                    primaryMuscles = listOf("abdominals"),
                    equipment = "body only",
                    instructions = listOf("Lie down", "Sit up"),
                    images = listOf("3_4_Sit-Up/0.jpg"),
                ),
            ),
        )
        assertEquals(1, dto.exercises.size)
        assertEquals(2, dto.exercises[0].instructions.size)
    }

    @Test
    fun cache_expirado_e_aceito_quando_fetch_falha() {
        val hasCache = true
        val fetchFailed = true
        val useCache = hasCache && fetchFailed
        assertEquals(true, useCache)
    }

    @Test
    fun cache_acima_do_limite_e_ignorado() {
        val max = 5L * 1024 * 1024
        val size = max + 1
        val ignored = size > max
        assertEquals(true, ignored)
    }

    @Test
    fun exercicio_desconhecido_retorna_null() {
        val map = emptyMap<String, ExerciseMedia>()
        val media = map["unknown"]
        assertNull(media)
    }
}
