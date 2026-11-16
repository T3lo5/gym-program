package com.treinos.app.ui.programs

import com.treinos.app.ui.progression.ProgressionAction
import com.treinos.app.ui.progression.ProgressionContext
import com.treinos.app.ui.progression.ProgressionSet
import com.treinos.app.ui.progression.suggestProgression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * H4-10: determinismo, estrutura e limites dos templates de programas prontos.
 * Puro, sem Android: o catálogo não pode depender de relógio nem de contexto.
 */
class ProgramLibraryTest {

    private companion object {
        const val NEXT_REVIEW = "2026-12-13T00:00:00Z"
        const val NOW_MILLIS = 1_800_000_000_000L
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }

    private fun build(id: String) = buildProgram(id, NEXT_REVIEW)!!

    @Test
    fun catalogo_tem_quatro_programas_na_ordem() {
        assertEquals(listOf("ppl", "531", "gzclp", "phul"), programCatalog().map { it.id })
    }

    @Test
    fun ids_unicos() {
        val ids = programCatalog().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun build_programa_deterministico() {
        val primeiro = buildProgram("gzclp", NEXT_REVIEW)
        val segundo = buildProgram("gzclp", NEXT_REVIEW)
        assertEquals(primeiro, segundo)
    }

    @Test
    fun dias_e_ordens_consistentes() {
        programCatalog().forEach { template ->
            val plan = build(template.id)
            assertEquals("daysPerWeek != days.size em ${template.id}", template.daysPerWeek, plan.workout.days.size)
            plan.workout.days.forEachIndexed { dayIndex, day ->
                assertEquals("dayOrder com buraco em ${template.id}", dayIndex, day.dayOrder)
                day.exercises.forEachIndexed { exerciseIndex, exercise ->
                    assertEquals(
                        "exerciseOrder com buraco em ${template.id}/${day.name}",
                        exerciseIndex,
                        exercise.exerciseOrder,
                    )
                }
            }
        }
    }

    @Test
    fun faixas_de_reps_parseaveis_pelo_motor() {
        programCatalog().forEach { template ->
            build(template.id).workout.days.flatMap { it.exercises }.forEach { exercise ->
                // Formato fechado da spec: "n" ou "a-b", nunca vazio nem AMRAP.
                val match = Regex("""(\d+)(?:-(\d+))?""").matchEntire(exercise.targetReps)
                assertTrue("faixa inválida: '${exercise.targetReps}'", match != null)
                val top = match!!.groupValues[2].ifEmpty { match.groupValues[1] }.toInt()

                // Duas sessões no topo da faixa com RPE confortável: o motor
                // sugere progressão — prova que a faixa é parseável desde a
                // 1ª sessão (H3-5 funciona sem ajuste manual).
                val history = listOf(
                    ProgressionSet(
                        sessionId = 1,
                        sessionStartedAt = NOW_MILLIS - DAY_MILLIS,
                        exerciseId = exercise.exerciseId,
                        isCompleted = true,
                        isWarmup = false,
                        reps = top,
                        weightKg = 60.0,
                        rpe = 8.0,
                    ),
                    ProgressionSet(
                        sessionId = 2,
                        sessionStartedAt = NOW_MILLIS - 2 * DAY_MILLIS,
                        exerciseId = exercise.exerciseId,
                        isCompleted = true,
                        isWarmup = false,
                        reps = top,
                        weightKg = 60.0,
                        rpe = 8.0,
                    ),
                )
                val suggestion = suggestProgression(
                    ProgressionContext(
                        exerciseId = exercise.exerciseId,
                        targetReps = exercise.targetReps,
                        equipment = exercise.equipment,
                        primaryMuscles = exercise.primaryMuscles.joinToString(", "),
                    ),
                    history,
                    NOW_MILLIS,
                )
                assertEquals(
                    "motor não parseou '${exercise.targetReps}' (${exercise.exerciseId})",
                    ProgressionAction.INCREASE,
                    suggestion.action,
                )
            }
        }
    }

    @Test
    fun limites_de_series_e_descanso() {
        programCatalog().forEach { template ->
            build(template.id).workout.days.flatMap { it.exercises }.forEach { exercise ->
                assertTrue("sets fora de 1..10 em ${exercise.exerciseId}", exercise.sets in 1..10)
                assertTrue(
                    "restSeconds fora de 30..300 em ${exercise.exerciseId}",
                    exercise.restSeconds in 30..300,
                )
                exercise.targetRpe?.let { rpe ->
                    assertTrue("targetRpe fora de 1.0..10.0 em ${exercise.exerciseId}", rpe in 1.0..10.0)
                }
            }
        }
    }

    @Test
    fun sem_exercicio_repetido_no_mesmo_dia() {
        programCatalog().forEach { template ->
            build(template.id).workout.days.forEach { day ->
                val ids = day.exercises.map { it.exerciseId }
                assertEquals(
                    "exercício repetido em ${template.id}/${day.name}",
                    ids.size,
                    ids.toSet().size,
                )
            }
        }
    }

    @Test
    fun ppl_tem_seis_dias() {
        val plan = build("ppl")
        assertEquals(6, plan.workout.daysPerWeek)
        assertEquals(6, plan.workout.days.size)
        assertEquals(
            listOf("Push A", "Pull A", "Legs A", "Push B", "Pull B", "Legs B"),
            plan.workout.days.map { it.name },
        )
    }

    @Test
    fun demais_programas_tem_quatro_dias() {
        assertEquals(
            listOf("Agachamento", "Supino", "Levantamento terra", "Desenvolvimento"),
            build("531").workout.days.map { it.name },
        )
        assertEquals(listOf("A1", "B1", "A2", "B2"), build("gzclp").workout.days.map { it.name })
        assertEquals(
            listOf("Upper Power", "Lower Power", "Upper Hypertrophy", "Lower Hypertrophy"),
            build("phul").workout.days.map { it.name },
        )
        listOf("531", "gzclp", "phul").forEach { id ->
            assertEquals("diasPerWeek != 4 em $id", 4, build(id).workout.daysPerWeek)
        }
    }

    @Test
    fun id_desconhecido_retorna_null() {
        assertNull(buildProgram("nao-existe", NEXT_REVIEW))
    }

    @Test
    fun plano_local_sem_ia_e_com_revisao_em_4_semanas() {
        val response = buildProgram("ppl", NEXT_REVIEW)!!
        assertEquals("local", response.source)
        assertNull(response.fallbackReason)
        // Default do parâmetro: revisão em 4 semanas, na data passada pelo chamador.
        assertEquals(4, response.reviewAfterWeeks)
        assertEquals(NEXT_REVIEW, response.nextReviewAt)
        assertEquals(6, response.workout.daysPerWeek)
        // Conteúdo prescrito da spec, primeira linha do PPL.
        val primeiro = response.workout.days.first().exercises.first()
        assertEquals("Barbell_Bench_Press_-_Medium_Grip", primeiro.exerciseId)
        assertEquals(4, primeiro.sets)
        assertEquals("6-8", primeiro.targetReps)
        assertEquals(8.0, primeiro.targetRpe ?: 0.0, 0.001)
        assertEquals(150, primeiro.restSeconds)
    }
}
