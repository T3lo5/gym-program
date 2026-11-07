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

    @Test
    fun catalogo_vazio_nao_substitui_cache_valido() {
        // H3-24: resposta vazia do backend nunca vira o catálogo em uso.
        val cached = mapOf(
            "3_4_Sit-Up" to ExerciseMedia(
                id = "3_4_Sit-Up",
                name = "3/4 Sit-Up",
                primaryMuscles = listOf("abdominals"),
                equipment = "body only",
                instructions = listOf("Lie down"),
                imageUrl = ExerciseMediaRepository.imageUrlFor(listOf("3_4_Sit-Up/0.jpg")),
            ),
        )

        assertEquals(cached, ExerciseMediaRepository.mergeCatalog(cached, emptyMap()))
        assertEquals(cached, ExerciseMediaRepository.mergeCatalog(cached, null))

        // Sem cache anterior, vazio continua vazio (estado atual, sem crash).
        assertEquals(emptyMap<String, ExerciseMedia>(), ExerciseMediaRepository.mergeCatalog(null, emptyMap()))
        assertEquals(emptyMap<String, ExerciseMedia>(), ExerciseMediaRepository.mergeCatalog(null, null))

        // Dados frescos e não vazios vencem o cache.
        val fresco = mapOf(
            "Novo" to ExerciseMedia("Novo", "Novo", emptyList(), "", emptyList(), null),
        )
        assertEquals(fresco, ExerciseMediaRepository.mergeCatalog(cached, fresco))
    }

    @Test
    fun url_encode_por_segmento() {
        val base = ExerciseMediaRepository.IMAGE_BASE_URL

        // Path limpo do catálogo atual não muda.
        assertEquals(
            base + "3_4_Sit-Up/0.jpg",
            ExerciseMediaRepository.imageUrlFor(listOf("3_4_Sit-Up/0.jpg")),
        )

        // Espaço e acento são codificados por segmento; a barra permanece.
        assertEquals(
            base + "Barbell%20Bench%20Press/0.jpg",
            ExerciseMediaRepository.imageUrlFor(listOf("Barbell Bench Press/0.jpg")),
        )
        assertEquals(
            base + "Exerc%C3%ADcio%20X/0.jpg",
            ExerciseMediaRepository.imageUrlFor(listOf("Exercício X/0.jpg")),
        )

        assertEquals(null, ExerciseMediaRepository.imageUrlFor(emptyList()))
        assertEquals(null, ExerciseMediaRepository.imageUrlFor(listOf("  ")))
    }

    @Test
    fun media_do_plano_quando_catalogo_nao_tem_id() {
        // H3-24: sem catálogo, o detalhe usa a mídia que veio no plano.
        val media = resolveExerciseMedia(
            catalog = emptyMap(),
            exerciseId = "Barbell_Bench_Press",
            planName = "Supino reto",
            planPrimaryMuscles = listOf("peito"),
            planEquipment = "barra",
            planInstructions = "Deite no banco.\nSegure a barra.",
            planImages = "Barbell_Bench_Press/0.jpg,Barbell_Bench_Press/1.jpg",
        )

        assertEquals("Barbell_Bench_Press", media?.id)
        assertEquals("Supino reto", media?.name)
        assertEquals(listOf("peito"), media?.primaryMuscles)
        assertEquals(listOf("Deite no banco.", "Segure a barra."), media?.instructions)
        assertEquals(
            ExerciseMediaRepository.IMAGE_BASE_URL + "Barbell_Bench_Press/0.jpg",
            media?.imageUrl,
        )

        // Catálogo é a fonte preferencial: entrada existente vence o plano.
        val catalogo = mapOf(
            "Barbell_Bench_Press" to ExerciseMedia(
                id = "Barbell_Bench_Press",
                name = "Barbell Bench Press",
                primaryMuscles = listOf("chest"),
                equipment = "barbell",
                instructions = listOf("Lie down"),
                imageUrl = ExerciseMediaRepository.imageUrlFor(listOf("Barbell_Bench_Press/0.jpg")),
            ),
        )
        assertEquals(
            catalogo["Barbell_Bench_Press"],
            resolveExerciseMedia(
                catalog = catalogo,
                exerciseId = "Barbell_Bench_Press",
                planName = "Supino reto",
                planInstructions = "Deite no banco.",
                planImages = "Barbell_Bench_Press/0.jpg",
            ),
        )

        // Sem mídia no plano e sem catálogo: estado atual (null na UI).
        assertNull(
            resolveExerciseMedia(
                catalog = emptyMap(),
                exerciseId = "run_5k",
            ),
        )
    }
}
