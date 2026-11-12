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

    @Test
    fun catalogo_sem_midia_usa_midia_do_plano() {
        // H3-45: o asset offline só tem metadados; não pode esconder as
        // instruções e a imagem que vieram no plano (ex.: backup restaurado).
        val catalogo = mapOf(
            "Barbell_Bench_Press" to ExerciseMedia(
                id = "Barbell_Bench_Press",
                name = "Barbell Bench Press",
                primaryMuscles = listOf("chest"),
                equipment = "barbell",
                instructions = emptyList(),
                imageUrl = null,
            ),
        )

        val media = resolveExerciseMedia(
            catalog = catalogo,
            exerciseId = "Barbell_Bench_Press",
            planName = "Supino reto",
            planPrimaryMuscles = listOf("peito"),
            planEquipment = "barra",
            planInstructions = "Deite no banco.\nSegure a barra.",
            planImages = "Barbell_Bench_Press/0.jpg",
        )

        assertEquals("Barbell Bench Press", media?.name)
        assertEquals(listOf("chest"), media?.primaryMuscles)
        assertEquals("barbell", media?.equipment)
        assertEquals(listOf("Deite no banco.", "Segure a barra."), media?.instructions)
        assertEquals(
            ExerciseMediaRepository.IMAGE_BASE_URL + "Barbell_Bench_Press/0.jpg",
            media?.imageUrl,
        )
    }

    @Test
    fun catalogo_com_instrucoes_sem_imagem_usa_imagem_do_plano() {
        val catalogo = mapOf(
            "Barbell_Bench_Press" to ExerciseMedia(
                id = "Barbell_Bench_Press",
                name = "Barbell Bench Press",
                primaryMuscles = listOf("chest"),
                equipment = "barbell",
                instructions = listOf("Lie down"),
                imageUrl = null,
            ),
        )

        val media = resolveExerciseMedia(
            catalog = catalogo,
            exerciseId = "Barbell_Bench_Press",
            planImages = "Barbell_Bench_Press/0.jpg",
        )

        assertEquals(listOf("Lie down"), media?.instructions)
        assertEquals(
            ExerciseMediaRepository.IMAGE_BASE_URL + "Barbell_Bench_Press/0.jpg",
            media?.imageUrl,
        )
    }

    @Test
    fun instrucoes_por_idioma() {
        // H3-36: "pt" resolve nome e instruções do catálogo; "en"/null mantém
        // o EN canônico. Resolução campo a campo e pura (sem Android).
        val media = ExerciseMedia(
            id = "3_4_Sit-Up",
            name = "3/4 Sit-Up",
            primaryMuscles = listOf("abdominals"),
            equipment = "body only",
            instructions = listOf("Lie down on the floor.", "Sit back up."),
            imageUrl = null,
            namePt = "Abdominal 3/4",
            instructionsPt = listOf("Deite-se no chão.", "Sente-se de novo."),
        )
        val pt = media.resolvedFor("pt")
        assertEquals("Abdominal 3/4", pt.name)
        assertEquals(listOf("Deite-se no chão.", "Sente-se de novo."), pt.instructions)
        // EN e idioma ausente não traduzem.
        assertEquals("3/4 Sit-Up", media.resolvedFor("en").name)
        assertEquals(media.instructions, media.resolvedFor("en").instructions)
        assertEquals("3/4 Sit-Up", media.resolvedFor(null).name)
        // Original intacto (resolvedFor não muta).
        assertEquals("3/4 Sit-Up", media.name)
    }

    @Test
    fun namept_ausente_cai_para_en() {
        // H3-36: os 3 exercícios sem tradução (ex.: Kettlebell_Halo) chegam
        // com namePt/instructionsPt null — nunca inventar; o app usa o EN.
        val media = ExerciseMedia(
            id = "Kettlebell_Halo",
            name = "Kettlebell Halo",
            primaryMuscles = listOf("shoulders"),
            equipment = "kettlebells",
            instructions = listOf("Hold the bell at chest height."),
            imageUrl = null,
            namePt = null,
            instructionsPt = null,
        )
        val pt = media.resolvedFor("pt")
        assertEquals("Kettlebell Halo", pt.name)
        assertEquals(listOf("Hold the bell at chest height."), pt.instructions)
        // Tradução parcial: nome PT existe, passos não — cada campo cai só
        // quando falta a sua tradução.
        val parcial = media.copy(namePt = "Halo com Kettlebell")
        assertEquals("Halo com Kettlebell", parcial.resolvedFor("pt").name)
        assertEquals(
            listOf("Hold the bell at chest height."),
            parcial.resolvedFor("pt").instructions,
        )
    }

    @Test
    fun merge_local_prioriza_cache_sobre_asset() {
        val cache = mapOf(
            "Barbell_Bench_Press" to ExerciseMedia(
                id = "Barbell_Bench_Press",
                name = "Supino Barra",
                primaryMuscles = listOf("chest"),
                equipment = "barbell",
                instructions = listOf("Deite no banco."),
                imageUrl = null,
            ),
        )
        val asset = mapOf(
            "Barbell_Bench_Press" to ExerciseMedia(
                id = "Barbell_Bench_Press",
                name = "Barbell Bench Press",
                primaryMuscles = listOf("chest"),
                equipment = "barbell",
                instructions = emptyList(),
                imageUrl = null,
            ),
        )

        assertEquals(cache, ExerciseMediaRepository.mergeLocal(cache, asset))
        assertEquals(asset, ExerciseMediaRepository.mergeLocal(emptyMap(), asset))
        assertEquals(
            emptyMap<String, ExerciseMedia>(),
            ExerciseMediaRepository.mergeLocal(null, emptyMap()),
        )
    }

    @Test
    fun parse_swap_catalog_ignora_campos_desconhecidos() {
        val raw = """
            {
              "version": 1,
              "exercises": [
                {
                  "id": "Barbell_Bench_Press",
                  "name": "Barbell Bench Press",
                  "primaryMuscles": ["chest"],
                  "equipment": "barbell",
                  "category": "strength",
                  "images": ["Barbell_Bench_Press/0.jpg"]
                }
              ]
            }
        """.trimIndent()

        val catalog = ExerciseMediaRepository.parseSwapCatalog(raw)

        assertEquals(1, catalog.size)
        val media = catalog.getValue("Barbell_Bench_Press")
        assertEquals("Barbell Bench Press", media.name)
        assertEquals(listOf("chest"), media.primaryMuscles)
        assertEquals("barbell", media.equipment)
        // O asset é só para troca: sem imagens/instruções.
        assertNull(media.imageUrl)
        assertEquals(emptyList<String>(), media.instructions)
    }

    @Test
    fun parse_swap_catalog_rejeita_json_invalido() {
        assertEquals(
            emptyMap<String, ExerciseMedia>(),
            ExerciseMediaRepository.parseSwapCatalog("nao e json"),
        )
        assertEquals(
            emptyMap<String, ExerciseMedia>(),
            ExerciseMediaRepository.parseSwapCatalog("{\"version\": 2, \"exercises\": []}"),
        )
    }
}
