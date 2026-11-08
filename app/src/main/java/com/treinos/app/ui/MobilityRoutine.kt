package com.treinos.app.ui

import kotlin.math.max

data class MobilityRoutine(
    val id: String,
    val name: String,
    val focus: String,
    val description: String,
    val movements: List<MobilityMovement>,
)

data class MobilityMovement(
    val name: String,
    val durationSeconds: Int,
    val side: String? = null,
    val cue: String,
    val catalogId: String? = catalogIdForMovement(name),
)

/**
 * H3-32: liga os movimentos curados a IDs reais do catálogo (free-exercise-db,
 * categoria `stretching`) para que a tela de mobilidade mostre a imagem do
 * exercício. Movimento sem correspondência fica `null` e cai no fallback de
 * letra — nunca um quadrado vazio. Pura para poder ser testada em JVM.
 */
fun catalogIdForMovement(name: String): String? = MOVEMENT_CATALOG_IDS[name]

private val MOVEMENT_CATALOG_IDS: Map<String, String> = mapOf(
    "Gato-Camelo" to "Cat_Stretch",
    "Rotação torácica em decúbito lateral" to "Torso_Rotation",
    "Rotação torácica em 4 apoios" to "Torso_Rotation",
    "Flexão do quadril em meia ponte" to "Pelvic_Tilt_Into_Bridge",
    "Mobilização pélvica em ponte" to "Pelvic_Tilt_Into_Bridge",
    "Mobilização pélvica relaxada" to "Pelvic_Tilt_Into_Bridge",
    "Alongamento do piriforme sentado" to "Seated_Glute",
    "Alongamento do piriforme" to "Lying_Glute",
    "Alongamento dos isquiotibiais" to "Hamstring_Stretch",
    "Alongamento dos isquiotibiais sentado" to "Seated_Hamstring",
    "Relaxamento em posição fetal" to "Hug_Knees_To_Chest",
    "Borboleta" to "Adductor_Groin",
    "Alongamento do quadríceps" to "Quad_Stretch",
    "Alongamento do flexor do quadril" to "Kneeling_Hip_Flexor",
    "Mobilização cervical" to "Side_Neck_Stretch",
    "Alongamento dos ombros" to "Shoulder_Stretch",
    "Relaxamento" to "Childs_Pose",
    "Alongamento dos panturrilhas" to "Standing_Gastrocnemius_Calf_Stretch",
    "Mobilização de tornozelo" to "Ankle_Circles",
)

object MobilityRoutineTemplates {
    /**
     * H3-31: pausa guiada entre movimentos; passa a valer de verdade no
     * [MobilitySessionEngine] (antes era definido e nunca usado).
     */
    const val TRANSITION_SECONDS = 5

    val routines: List<MobilityRoutine> = listOf(
        MobilityRoutine(
            id = "back_10",
            name = "Costas e quadril — 10 min",
            focus = "back",
            description = "Alongamento e mobilidade para aliviar tensão nas costas e quadril.",
            movements = listOf(
                MobilityMovement(
                    name = "Respiração diafragmática",
                    durationSeconds = 40,
                    cue = "Deite-se de costas, inspire pelo nariz expandindo o abdômen e expire pela boca.",
                ),
                MobilityMovement(
                    name = "Gato-Camelo",
                    durationSeconds = 60,
                    cue = "Em 4 apoios, arqueie as costas para cima (gato) e depois para baixo (camelo), lentamente.",
                ),
                MobilityMovement(
                    name = "Rotação torácica em decúbito lateral",
                    durationSeconds = 50,
                    side = "left",
                    cue = "Deitado de lado, joelhos flexionados, abra o tronco levando o braço para trás, sem forçar.",
                ),
                MobilityMovement(
                    name = "Rotação torácica em decúbito lateral",
                    durationSeconds = 50,
                    side = "right",
                    cue = "Repita do lado direito, mantendo os quadris estáveis.",
                ),
                MobilityMovement(
                    name = "Flexão do quadril em meia ponte",
                    durationSeconds = 60,
                    cue = "Em ponte, empurre pelo calcanhar e alongue um quadril, trocando atenção entre os lados.",
                ),
                MobilityMovement(
                    name = "Alongamento do piriforme sentado",
                    durationSeconds = 50,
                    side = "left",
                    cue = "Sentado, cruze a perna sobre a outra e incline o tronco levemente à frente.",
                ),
                MobilityMovement(
                    name = "Alongamento do piriforme sentado",
                    durationSeconds = 50,
                    side = "right",
                    cue = "Repita do lado direito, sem forçar a amplitude.",
                ),
                MobilityMovement(
                    name = "Alongamento dos isquiotibiais",
                    durationSeconds = 40,
                    side = "left",
                    cue = "Com uma perna estendida, incline o tronco à frente até sentir alongamento, sem balanços.",
                ),
                MobilityMovement(
                    name = "Alongamento dos isquiotibiais",
                    durationSeconds = 40,
                    side = "right",
                    cue = "Repita do lado direito, mantendo coluna neutra.",
                ),
                MobilityMovement(
                    name = "Relaxamento em posição fetal",
                    durationSeconds = 60,
                    cue = "Deitado de lado em posição fetal, respire lentamente por 1 minuto para relaxar.",
                ),
            ),
        ),
        MobilityRoutine(
            id = "hips_10",
            name = "Quadril e posterior — 10 min",
            focus = "hips",
            description = "Foco em quadril, glúteos e isquiotibiais para mobilidade e bem-estar.",
            movements = listOf(
                MobilityMovement(
                    name = "Mobilização pélvica em ponte",
                    durationSeconds = 40,
                    cue = "Em ponte, incline a pelve para trás e para frente, controlando o movimento.",
                ),
                MobilityMovement(
                    name = "Borboleta",
                    durationSeconds = 50,
                    cue = "Sentado com solas juntas, aproxime os pés e pressione levemente os joelhos com as mãos.",
                ),
                MobilityMovement(
                    name = "Alongamento do quadríceps",
                    durationSeconds = 40,
                    side = "left",
                    cue = "Em pé, segure o tornozelo atrás e traga o calcanhar em direção ao glúteo, sem inclinar muito o tronco.",
                ),
                MobilityMovement(
                    name = "Alongamento do quadríceps",
                    durationSeconds = 40,
                    side = "right",
                    cue = "Repita do lado direito, mantendo equilíbrio.",
                ),
                MobilityMovement(
                    name = "Alongamento do flexor do quadril",
                    durationSeconds = 50,
                    side = "left",
                    cue = "Em posição de ajoelhado, avance um pé à frente e incline o tronco para frente levemente.",
                ),
                MobilityMovement(
                    name = "Alongamento do flexor do quadril",
                    durationSeconds = 50,
                    side = "right",
                    cue = "Repita do lado direito, sem exagerar na amplitude.",
                ),
                MobilityMovement(
                    name = "Alongamento dos isquiotibiais sentado",
                    durationSeconds = 50,
                    side = "left",
                    cue = "Sentado com uma perna estendida, incline o tronco à frente até sentir alongamento.",
                ),
                MobilityMovement(
                    name = "Alongamento dos isquiotibiais sentado",
                    durationSeconds = 50,
                    side = "right",
                    cue = "Repita do lado direito.",
                ),
                MobilityMovement(
                    name = "Alongamento do piriforme",
                    durationSeconds = 50,
                    side = "left",
                    cue = "Deitado, cruze uma perna sobre a outra e traga o joelho em direção ao ombro oposto.",
                ),
                MobilityMovement(
                    name = "Alongamento do piriforme",
                    durationSeconds = 50,
                    side = "right",
                    cue = "Repita do lado direito, sem forçar.",
                ),
                MobilityMovement(
                    name = "Mobilização pélvica relaxada",
                    durationSeconds = 40,
                    cue = "Em ponte ou deitado, movimente a pelve lentamente para relaxar.",
                ),
            ),
        ),
        MobilityRoutine(
            id = "full_15",
            name = "Corpo inteiro — 15 min",
            focus = "full_body",
            description = "Rotina completa de mobilidade para corpo inteiro, ideal após o dia ou como aquecimento leve.",
            movements = listOf(
                MobilityMovement(
                    name = "Respiração e aquecimento",
                    durationSeconds = 40,
                    cue = "Em pé, respire profundamente e movimente ombros levemente.",
                ),
                MobilityMovement(
                    name = "Mobilização cervical",
                    durationSeconds = 40,
                    cue = "Incline a cabeça para cada lado suavemente, sem girar bruscamente.",
                ),
                MobilityMovement(
                    name = "Gato-Camelo",
                    durationSeconds = 60,
                    cue = "Em 4 apoios, alterne entre arquear e abaixar as costas, com ritmo calmo.",
                ),
                MobilityMovement(
                    name = "Rotação torácica em 4 apoios",
                    durationSeconds = 50,
                    side = "left",
                    cue = "Em 4 apoios, coloque a mão atrás da cabeça e gire o tronco para o lado.",
                ),
                MobilityMovement(
                    name = "Rotação torácica em 4 apoios",
                    durationSeconds = 50,
                    side = "right",
                    cue = "Repita do lado direito.",
                ),
                MobilityMovement(
                    name = "Alongamento do flexor do quadril",
                    durationSeconds = 50,
                    side = "left",
                    cue = "Em posição de ajoelhado, incline o tronco à frente levemente para alongar.",
                ),
                MobilityMovement(
                    name = "Alongamento do flexor do quadril",
                    durationSeconds = 50,
                    side = "right",
                    cue = "Repita do lado direito.",
                ),
                MobilityMovement(
                    name = "Borboleta",
                    durationSeconds = 50,
                    cue = "Sentado com solas juntas, movimente levemente os joelhos para cima e para baixo.",
                ),
                MobilityMovement(
                    name = "Alongamento dos isquiotibiais",
                    durationSeconds = 50,
                    side = "left",
                    cue = "Com perna estendida, incline o tronco à frente sem balanços.",
                ),
                MobilityMovement(
                    name = "Alongamento dos isquiotibiais",
                    durationSeconds = 50,
                    side = "right",
                    cue = "Repita do lado direito.",
                ),
                MobilityMovement(
                    name = "Alongamento do quadríceps",
                    durationSeconds = 40,
                    side = "left",
                    cue = "Em pé, segure o tornozelo e traga o calcanhar ao glúteo.",
                ),
                MobilityMovement(
                    name = "Alongamento do quadríceps",
                    durationSeconds = 40,
                    side = "right",
                    cue = "Repita do lado direito.",
                ),
                MobilityMovement(
                    name = "Alongamento dos ombros",
                    durationSeconds = 40,
                    side = "left",
                    cue = "Traga um braço pelo peito, segure levemente com o outro braço.",
                ),
                MobilityMovement(
                    name = "Alongamento dos ombros",
                    durationSeconds = 40,
                    side = "right",
                    cue = "Repita do lado direito.",
                ),
                MobilityMovement(
                    name = "Relaxamento",
                    durationSeconds = 60,
                    cue = "Deitado de costas, respire lenta e profundamente para finalizar.",
                ),
            ),
        ),
        MobilityRoutine(
            id = "post_run_8",
            name = "Pós-corrida — 8 min",
            focus = "post_run",
            description = "Recuperação rápida após corrida, focando em pernas e tornozelos.",
            movements = listOf(
                MobilityMovement(
                    name = "Caminhada em pé relaxada",
                    durationSeconds = 40,
                    cue = "Em pé, respire e diminua o ritmo, preparando o alongamento.",
                ),
                MobilityMovement(
                    name = "Alongamento dos panturrilhas",
                    durationSeconds = 40,
                    side = "left",
                    cue = "Com uma perna atrás, mantenha o calcanhar no chão e incline o tronco à frente.",
                ),
                MobilityMovement(
                    name = "Alongamento dos panturrilhas",
                    durationSeconds = 40,
                    side = "right",
                    cue = "Repita do lado direito.",
                ),
                MobilityMovement(
                    name = "Alongamento dos isquiotibiais",
                    durationSeconds = 50,
                    side = "left",
                    cue = "Com perna estendida, incline o tronco à frente sem balanços.",
                ),
                MobilityMovement(
                    name = "Alongamento dos isquiotibiais",
                    durationSeconds = 50,
                    side = "right",
                    cue = "Repita do lado direito.",
                ),
                MobilityMovement(
                    name = "Alongamento do quadríceps",
                    durationSeconds = 40,
                    side = "left",
                    cue = "Em pé, segure o tornozelo e traga o calcanhar ao glúteo.",
                ),
                MobilityMovement(
                    name = "Alongamento do quadríceps",
                    durationSeconds = 40,
                    side = "right",
                    cue = "Repita do lado direito.",
                ),
                MobilityMovement(
                    name = "Alongamento do flexor do quadril",
                    durationSeconds = 50,
                    side = "left",
                    cue = "Em posição de ajoelhado, incline o tronco à frente levemente.",
                ),
                MobilityMovement(
                    name = "Alongamento do flexor do quadril",
                    durationSeconds = 50,
                    side = "right",
                    cue = "Repita do lado direito.",
                ),
                MobilityMovement(
                    name = "Mobilização de tornozelo",
                    durationSeconds = 40,
                    side = "left",
                    cue = "Com um pé à frente, movimente o tornozelo para cima e para baixo.",
                ),
                MobilityMovement(
                    name = "Mobilização de tornozelo",
                    durationSeconds = 40,
                    side = "right",
                    cue = "Repita do lado direito.",
                ),
            ),
        ),
    )

    fun getRoutine(id: String): MobilityRoutine? = routines.firstOrNull { it.id == id }

    fun totalDurationSeconds(routine: MobilityRoutine): Int {
        val movementsTotal = routine.movements.sumOf { it.durationSeconds }
        val transitions = if (routine.movements.isEmpty()) 0 else max(0, routine.movements.size - 1) * TRANSITION_SECONDS
        return movementsTotal + transitions
    }

    fun validate(routine: MobilityRoutine): Boolean {
        if (routine.movements.isEmpty()) return false
        if (routine.movements.any { it.durationSeconds < 20 || it.durationSeconds > 60 }) return false
        val total = totalDurationSeconds(routine)
        // Allow some flexibility around target (5-15 min) but spec says sum + transitions differs by >60s invalid
        // Check against reasonable bounds per routine
        return when (routine.id) {
            "back_10" -> total in 540..660 // ~9-11 min
            "hips_10" -> total in 540..660
            "full_15" -> total in 840..960 // ~14-16 min
            "post_run_8" -> total in 420..540 // ~7-9 min
            else -> total >= 300 && total <= 900
        }
    }

    fun isValidTemplate(routine: MobilityRoutine): Boolean = validate(routine)

    /**
     * Escolhe a rotina pelo foco. back → back_10, hips → hips_10,
     * post_run → post_run_8, qualquer outro → full_15.
     */
    fun routineForFocus(focus: String?, minutes: Int): MobilityRoutine {
        return when (focus?.lowercase()) {
            "back" -> getRoutine("back_10") ?: routines.first()
            "hips" -> getRoutine("hips_10") ?: routines.first()
            "post_run" -> getRoutine("post_run_8") ?: routines.first()
            else -> getRoutine("full_15") ?: routines.first()
        }
    }
}
