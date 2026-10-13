package dev.sopho.workout.data

/**
 * One exercise of the daily plan. A plan is always "sets groups of repsPerSet reps".
 */
data class Exercise(
    val id: String,
    val name: String,
    val emoji: String,
    val repsPerSet: Int,
    val sets: Int,
) {
    /** how many reps one full day of this exercise costs */
    val totalReps: Int get() = repsPerSet * sets
}

/**
 * The plan asked for on 2026-07-11:
 *   push-ups   10 x 8  = 80
 *   squats     10 x 12 = 120
 *   sit-ups    20 x 5  = 100
 */
val DEFAULT_PLAN: List<Exercise> = listOf(
    Exercise(id = "pushup", name = "俯卧撑", emoji = "💪", repsPerSet = 10, sets = 8),
    Exercise(id = "squat", name = "深蹲", emoji = "🦵", repsPerSet = 10, sets = 12),
    Exercise(id = "situp", name = "仰卧起坐", emoji = "🔥", repsPerSet = 20, sets = 5),
)

const val MIN_REPS = 1
const val MAX_REPS = 200
const val MIN_SETS = 1
const val MAX_SETS = 50
