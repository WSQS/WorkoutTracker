package dev.sopho.workout.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The plan asked for on day one, expressed as the test's own fixture so a change to
 * DEFAULT_PLAN has to be a conscious change here too.
 */
private val PLAN = listOf(
    Exercise("pushup", "俯卧撑", "💪", repsPerSet = 10, sets = 8),
    Exercise("squat", "深蹲", "🦵", repsPerSet = 10, sets = 12),
    Exercise("situp", "仰卧起坐", "🔥", repsPerSet = 20, sets = 5),
)

/** n groups, ticked one by one at [at] or with no time at all when [at] is null */
private fun marks(count: Int, at: Long? = null): ExerciseLog = List(count) { SetMark(at) }

private fun complete() = mapOf(
    "pushup" to marks(8),
    "squat" to marks(12),
    "situp" to marks(5),
)

private val EMPTY_DAY: DayLog = emptyMap()

class StatsTest {

    @Test
    fun defaultPlanMatchesTheDailyTarget() {
        assertEquals(80, DEFAULT_PLAN.first { it.id == "pushup" }.totalReps)
        assertEquals(120, DEFAULT_PLAN.first { it.id == "squat" }.totalReps)
        assertEquals(100, DEFAULT_PLAN.first { it.id == "situp" }.totalReps)
        assertEquals(300, planReps(DEFAULT_PLAN))
    }

    @Test
    fun partialDayIsCountedPerExercise() {
        val day: DayLog = mapOf("pushup" to marks(3), "squat" to marks(12), "situp" to emptyList())
        val pushup = PLAN[0]
        assertEquals(3, doneSets(day, pushup))
        assertEquals(30, doneReps(day, pushup))
        assertEquals(150, dayReps(day, PLAN))
        assertEquals(0.5f, dayRatio(day, PLAN), 0.001f)
        assertFalse(isComplete(day, PLAN))
        assertTrue(hasAny(day))
    }

    @Test
    fun extraSetsAreClampedToThePlan() {
        val day: DayLog = mapOf("pushup" to marks(99), "squat" to marks(12), "situp" to marks(5))
        assertEquals(8, doneSets(day, PLAN[0]))
        assertEquals(80, doneReps(day, PLAN[0]))
        assertEquals(1f, dayRatio(day, PLAN), 0.001f)
        assertTrue(isComplete(day, PLAN))
    }

    @Test
    fun emptyDayIsNeitherTrainedNorComplete() {
        assertEquals(0f, dayRatio(EMPTY_DAY, PLAN), 0.001f)
        assertEquals(0, dayReps(EMPTY_DAY, PLAN))
        assertFalse(hasAny(EMPTY_DAY))
        assertFalse(isComplete(EMPTY_DAY, PLAN))
        assertFalse(isComplete(mapOf("pushup" to marks(8)), PLAN))
    }

    @Test
    fun streakCountsBackFromTodayAndSurvivesAnUnfinishedToday() {
        val today = LocalDate.parse("2026-07-11")
        val records = mapOf(
            "2026-07-11" to mapOf<String, ExerciseLog>("pushup" to marks(2)), // started, not finished
            "2026-07-10" to complete(),
            "2026-07-09" to complete(),
            "2026-07-08" to mapOf<String, ExerciseLog>("squat" to marks(1)),  // misses push-up and sit-up
            "2026-07-07" to complete(),
        )
        assertEquals(2, currentStreak(records, PLAN, today))
        assertEquals(2, longestStreak(records, PLAN))
    }

    @Test
    fun finishingTodayExtendsTheStreak() {
        val today = LocalDate.parse("2026-07-11")
        val records = mapOf(
            "2026-07-11" to complete(),
            "2026-07-10" to complete(),
            "2026-07-09" to complete(),
        )
        assertEquals(3, currentStreak(records, PLAN, today))
    }

    @Test
    fun aSkippedDayBreaksTheStreak() {
        val today = LocalDate.parse("2026-07-11")
        val records = mapOf(
            "2026-07-11" to complete(),
            "2026-07-10" to complete(),
            // 2026-07-09 missing entirely
            "2026-07-08" to complete(),
            "2026-07-07" to complete(),
            "2026-07-06" to complete(),
        )
        assertEquals(2, currentStreak(records, PLAN, today))
        assertEquals(3, longestStreak(records, PLAN))
    }

    @Test
    fun lifetimeTotalsAndDayCounts() {
        val records = mapOf(
            "2026-07-11" to complete(),
            "2026-07-10" to mapOf<String, ExerciseLog>("pushup" to marks(4)),
            "2026-07-09" to complete(),
            "2026-07-08" to EMPTY_DAY,
        )
        val lifetime = lifetimeReps(records, PLAN)
        assertEquals(80 * 2 + 40, lifetime.getValue("pushup"))
        assertEquals(120 * 2, lifetime.getValue("squat"))
        assertEquals(100 * 2, lifetime.getValue("situp"))
        assertEquals(3, daysTrained(records))
        assertEquals(2, daysComplete(records, PLAN))
    }
}
