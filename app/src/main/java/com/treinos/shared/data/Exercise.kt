package com.treinos.shared.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "exercises")
@Serializable
data class Exercise(
    @PrimaryKey val id: String,
    val name: String,
    val force: String?,           // push | pull | static | null
    val level: String,            // beginner | intermediate | expert
    val mechanic: String?,        // isolation | compound | null
    val equipment: String?,       // body only, dumbbell, barbell, cable, etc.
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val instructions: List<String?>,  // free-exercise-db has nulls in lists
    val category: String,         // strength | cardio | stretching | powerlifting | plyometrics | olympic weightlifting | strongman
    val images: List<String>,
    // Local-only fields (not from free-exercise-db)
    val namePt: String? = null,   // Nome em PT-BR (preenchido depois)
    val isFavorite: Boolean = false,
    val lastUsedTimestamp: Long = 0
) {
    companion object {
        // Equipment enum values from schema
        const val EQUIP_BODY_ONLY = "body only"
        const val EQUIP_DUMBBELL = "dumbbell"
        const val EQUIP_BARBELL = "barbell"
        const val EQUIP_CABLE = "cable"
        const val EQUIP_BANDS = "bands"
        const val EQUIP_KETTLEBELLS = "kettlebells"
        const val EQUIP_MACHINE = "machine"
        const val EQUIP_MEDICINE_BALL = "medicine ball"
        const val EQUIP_FOAM_ROLL = "foam roll"
        const val EQUIP_EZ_CURL = "e-z curl bar"
        const val EQUIP_EXERCISE_BALL = "exercise ball"
        const val EQUIP_OTHER = "other"

        // Level values
        const val LEVEL_BEGINNER = "beginner"
        const val LEVEL_INTERMEDIATE = "intermediate"
        const val LEVEL_EXPERT = "expert"

        // Category values
        const val CAT_STRENGTH = "strength"
        const val CAT_CARDIO = "cardio"
        const val CAT_STRETCHING = "stretching"
        const val CAT_POWERLIFTING = "powerlifting"
        const val CAT_OLYMPIC_WL = "olympic weightlifting"
        const val CAT_STRONGMAN = "strongman"
        const val CAT_PLYOMETRICS = "plyometrics"
    }
}