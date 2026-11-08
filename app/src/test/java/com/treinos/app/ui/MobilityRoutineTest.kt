package com.treinos.app.ui

import com.treinos.app.ui.MobilityRoutineTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MobilityRoutineTest {
    @Test
    fun templates_tem_movimentos_validos() {
        MobilityRoutineTemplates.routines.forEach { routine ->
            assertFalse(routine.movements.isEmpty())
            routine.movements.forEach { movement ->
                assertTrue(movement.durationSeconds >= 20)
                assertTrue(movement.durationSeconds <= 60)
                assertTrue(movement.name.isNotBlank())
                assertTrue(movement.cue.isNotBlank())
            }
        }
    }

    @Test
    fun duracao_soma_movimentos_e_transicoes() {
        val routine = MobilityRoutineTemplates.getRoutine("back_10")!!
        val movementsTotal = routine.movements.sumOf { it.durationSeconds }
        val transitions = (routine.movements.size - 1) * 5
        assertEquals(movementsTotal + transitions, MobilityRoutineTemplates.totalDurationSeconds(routine))
    }

    @Test
    fun nao_ha_movimento_duplicado_na_mesma_rotina() {
        MobilityRoutineTemplates.routines.forEach { routine ->
            val names = routine.movements.map { it.name to it.side }
            assertEquals(names.size, names.distinct().size)
        }
    }

    @Test
    fun foco_back_tem_movimentos_de_costas() {
        val routine = MobilityRoutineTemplates.getRoutine("back_10")!!
        assertEquals("back", routine.focus)
        val hasBack = routine.movements.any { it.name.contains("Gato") || it.name.contains("torácic") || it.name.contains("piriforme") || it.name.contains("Costas") }
        assertTrue(hasBack)
    }

    @Test
    fun rotina_vazia_e_rejeitada() {
        val empty = MobilityRoutine("empty", "Empty", "test", "test", emptyList())
        assertFalse(MobilityRoutineTemplates.isValidTemplate(empty))
    }

    @Test
    fun foco_back_escolhe_back_10() {
        val routine = MobilityRoutineTemplates.routineForFocus("back", 10)
        assertEquals("back_10", routine.id)
    }

    @Test
    fun foco_hips_escolhe_hips_10() {
        val routine = MobilityRoutineTemplates.routineForFocus("hips", 10)
        assertEquals("hips_10", routine.id)
    }

    @Test
    fun foco_post_run_escolhe_post_run_8() {
        val routine = MobilityRoutineTemplates.routineForFocus("post_run", 8)
        assertEquals("post_run_8", routine.id)
    }

    @Test
    fun foco_desconhecido_usa_full_body() {
        val routine = MobilityRoutineTemplates.routineForFocus("qualquer_coisa", 15)
        assertEquals("full_15", routine.id)
    }

    @Test
    fun catalog_ids_dos_movimentos_existem_no_catalogo_stretching() {
        MobilityRoutineTemplates.routines.forEach { routine ->
            routine.movements.forEach movement@{ movement ->
                val id = movement.catalogId ?: return@movement
                assertTrue("catalogId $id deve existir no catálogo stretching", id in STRETCHING_IDS)
            }
        }
    }

    @Test
    fun movimentos_mapeados_tem_catalog_id_e_respiracao_nao() {
        assertEquals("Cat_Stretch", catalogIdForMovement("Gato-Camelo"))
        assertEquals("Kneeling_Hip_Flexor", catalogIdForMovement("Alongamento do flexor do quadril"))
        assertEquals("Ankle_Circles", catalogIdForMovement("Mobilização de tornozelo"))
        assertNull(catalogIdForMovement("Respiração diafragmática"))
        assertNull(catalogIdForMovement("Caminhada em pé relaxada"))
    }

    @Test
    fun toda_rotina_tem_pelo_um_movimento_com_midia() {
        MobilityRoutineTemplates.routines.forEach { routine ->
            assertTrue(routine.movements.any { it.catalogId != null })
        }
    }

    companion object {
        /** IDs reais de `category=stretching` em back/app/exercise_catalog.json (H3-32). */
        private val STRETCHING_IDS = setOf(
            "90_90_Hamstring",
            "Adductor",
            "Adductor_Groin",
            "All_Fours_Quad_Stretch",
            "Ankle_Circles",
            "Ankle_On_The_Knee",
            "Anterior_Tibialis-SMR",
            "Arm_Circles",
            "Behind_Head_Chest_Stretch",
            "Brachialis-SMR",
            "Calf_Stretch_Elbows_Against_Wall",
            "Calf_Stretch_Hands_Against_Wall",
            "Calves-SMR",
            "Cat_Stretch",
            "Chair_Leg_Extended_Stretch",
            "Chair_Lower_Back_Stretch",
            "Chair_Upper_Body_Stretch",
            "Chest_And_Front_Of_Shoulder_Stretch",
            "Chest_Stretch_on_Stability_Ball",
            "Childs_Pose",
            "Chin_To_Chest_Stretch",
            "Crossover_Reverse_Lunge",
            "Dancers_Stretch",
            "Dynamic_Back_Stretch",
            "Dynamic_Chest_Stretch",
            "Elbow_Circles",
            "Elbows_Back",
            "Foot-SMR",
            "Frog_Hops",
            "Front_Leg_Raises",
            "Groin_and_Back_Stretch",
            "Groiners",
            "Hamstring-SMR",
            "Hamstring_Stretch",
            "Hip_Circles_prone",
            "Hug_A_Ball",
            "Hug_Knees_To_Chest",
            "IT_Band_and_Glute_Stretch",
            "Iliotibial_Tract-SMR",
            "Inchworm",
            "Intermediate_Groin_Stretch",
            "Intermediate_Hip_Flexor_and_Quad_Stretch",
            "Iron_Crosses_stretch",
            "Knee_Across_The_Body",
            "Knee_Circles",
            "Kneeling_Forearm_Stretch",
            "Kneeling_Hip_Flexor",
            "Latissimus_Dorsi-SMR",
            "Leg-Up_Hamstring_Stretch",
            "Looking_At_Ceiling",
            "Lower_Back-SMR",
            "Lower_Back_Curl",
            "Lying_Bent_Leg_Groin",
            "Lying_Crossover",
            "Lying_Glute",
            "Lying_Hamstring",
            "Lying_Prone_Quadriceps",
            "Middle_Back_Stretch",
            "Neck-SMR",
            "On-Your-Back_Quad_Stretch",
            "On_Your_Side_Quad_Stretch",
            "One_Arm_Against_Wall",
            "One_Half_Locust",
            "One_Handed_Hang",
            "One_Knee_To_Chest",
            "Overhead_Lat",
            "Overhead_Stretch",
            "Overhead_Triceps",
            "Pelvic_Tilt_Into_Bridge",
            "Peroneals-SMR",
            "Peroneals_Stretch",
            "Piriformis-SMR",
            "Posterior_Tibialis_Stretch",
            "Pyramid",
            "Quad_Stretch",
            "Quadriceps-SMR",
            "Rear_Leg_Raises",
            "Rhomboids-SMR",
            "Round_The_World_Shoulder_Stretch",
            "Runners_Stretch",
            "Scissor_Kick",
            "Seated_Biceps",
            "Seated_Calf_Stretch",
            "Seated_Floor_Hamstring_Stretch",
            "Seated_Front_Deltoid",
            "Seated_Glute",
            "Seated_Hamstring",
            "Seated_Hamstring_and_Calf_Stretch",
            "Seated_Overhead_Stretch",
            "Shoulder_Circles",
            "Shoulder_Raise",
            "Shoulder_Stretch",
            "Side-Lying_Floor_Stretch",
            "Side_Leg_Raises",
            "Side_Lying_Groin_Stretch",
            "Side_Neck_Stretch",
            "Side_Wrist_Pull",
            "Sit_Squats",
            "Spinal_Stretch",
            "Split_Squats",
            "Standing_Biceps_Stretch",
            "Standing_Elevated_Quad_Stretch",
            "Standing_Gastrocnemius_Calf_Stretch",
            "Standing_Hamstring_and_Calf_Stretch",
            "Standing_Hip_Circles",
            "Standing_Hip_Flexors",
            "Standing_Lateral_Stretch",
            "Standing_Pelvic_Tilt",
            "Standing_Soleus_And_Achilles_Stretch",
            "Standing_Toe_Touches",
            "Stomach_Vacuum",
            "Superman",
            "The_Straddle",
            "Toe_Touchers",
            "Torso_Rotation",
            "Tricep_Side_Stretch",
            "Triceps_Stretch",
            "Upper_Back-Leg_Grab",
            "Upper_Back_Stretch",
            "Upward_Stretch",
            "Windmills",
            "Worlds_Greatest_Stretch",
            "Wrist_Circles",
        )
    }
}
