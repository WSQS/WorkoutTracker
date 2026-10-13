package dev.sopho.workout.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 08:00:00 UTC-ish base, only differences matter */
private const val T0 = 1_700_000_000_000L
private const val MINUTE = 60_000L

private fun marks(count: Int, at: Long? = null): ExerciseLog = List(count) { SetMark(at) }

class MarksTest {

    @Test
    fun tappingTheFirstCircleTimesThatGroup() {
        val log = tapMark(emptyList(), index = 0, now = T0)
        assertEquals(1, log.size)
        assertEquals(T0, log[0].at)
        assertEquals(0, log.bulkCount())
    }

    @Test
    fun jumpingAheadOnlyTimesTheGroupActuallyTapped() {
        // groups 1,2 were timed; the user now taps group 5 => 3,4 happened unseen
        val before = listOf(SetMark(T0), SetMark(T0 + MINUTE))
        val log = tapMark(before, index = 4, now = T0 + 10 * MINUTE)

        assertEquals(5, log.size)
        assertEquals(T0, log[0].at)
        assertEquals(T0 + MINUTE, log[1].at)
        assertNull(log[2].at)                       // 补记
        assertNull(log[3].at)                       // 补记
        assertEquals(T0 + 10 * MINUTE, log[4].at)
        assertEquals(2, log.bulkCount())
    }

    @Test
    fun tappingADoneGroupUndoesItAndEverythingAfter() {
        val log = marks(5, at = T0)
        assertEquals(2, tapMark(log, index = 2, now = T0).size)
        assertTrue(tapMark(log, index = 0, now = T0).isEmpty())
    }

    @Test
    fun tappingTheNextGroupAppendsOneTimedGroup() {
        val log = tapMark(marks(2, at = T0), index = 2, now = T0 + 5 * MINUTE)
        assertEquals(3, log.size)
        assertEquals(T0 + 5 * MINUTE, log.last().at)
        assertEquals(0, log.bulkCount())
    }

    @Test
    fun addAndDropRespectThePlan() {
        assertEquals(3, addMark(marks(2), now = T0, maxSets = 3).size)
        assertEquals(3, addMark(marks(3), now = T0, maxSets = 3).size)   // 已满，不再加
        assertEquals(T0, addMark(marks(2), now = T0, maxSets = 8).last().at)
        assertEquals(1, dropMark(marks(2)).size)
        assertTrue(dropMark(emptyList()).isEmpty())
    }

    @Test
    fun completeAllFillsWithoutInventingTimes() {
        val log = fillMarks(marks(2, at = T0), maxSets = 8)
        assertEquals(8, log.size)
        assertEquals(6, log.bulkCount())
        assertEquals(T0, log[0].at)
        assertNull(log[7].at)
        assertEquals(8, fillMarks(log, maxSets = 8).size)
    }

    @Test
    fun windowAndDurationUseOnlyRealTimes() {
        val log = listOf(SetMark(T0), SetMark(null), SetMark(T0 + 24 * MINUTE))
        assertEquals(T0 to T0 + 24 * MINUTE, log.logWindow())
        assertEquals(24 * MINUTE, log.logDuration())
        assertEquals(1, log.bulkCount())
    }

    @Test
    fun aSingleTimedGroupHasNoDuration() {
        assertNull(listOf(SetMark(T0)).logDuration())
        assertNull(marks(3).logWindow())
        assertNull(marks(3).logDuration())
        assertEquals(3, marks(3).bulkCount())
    }

    @Test
    fun aBackwardsClockIsNotReportedAsNegativeTime() {
        val log = listOf(SetMark(T0 + 10 * MINUTE), SetMark(T0))
        assertEquals(10 * MINUTE, log.logDuration())        // min/max, so still positive
        val broken = listOf(SetMark(T0 + 10 * MINUTE))
        assertNull(broken.logDuration())
    }

    @Test
    fun dayWindowSpansExercises() {
        val day: DayLog = mapOf(
            "pushup" to listOf(SetMark(T0), SetMark(T0 + 2 * MINUTE)),
            "squat" to listOf(SetMark(null), SetMark(T0 + 30 * MINUTE)),
            "situp" to marks(5),                            // 全部补记，不参与
        )
        val plan = DEFAULT_PLAN
        assertEquals(T0 to T0 + 30 * MINUTE, dayWindow(day, plan))
        assertEquals(30 * MINUTE, dayDuration(day, plan))
        assertEquals(6, dayBulkCount(day, plan))            // 1 补记 + 5 补记
    }

    @Test
    fun aDayWithLessThanTwoTimedGroupsHasNoDuration() {
        val day: DayLog = mapOf("pushup" to listOf(SetMark(T0)), "squat" to marks(12))
        assertNull(dayDuration(day, DEFAULT_PLAN))
        assertEquals(T0, dayWindow(day, DEFAULT_PLAN)?.first)
    }

    @Test
    fun lifetimeDurationSumsOnlyMeasurableDays() {
        val records = mapOf(
            "2026-07-11" to mapOf(
                "pushup" to listOf(SetMark(T0), SetMark(T0 + 20 * MINUTE)),
            ),
            "2026-07-10" to mapOf<String, ExerciseLog>("pushup" to marks(8)),   // 无时间
            "2026-07-09" to mapOf(
                "squat" to listOf(SetMark(T0), SetMark(T0 + 10 * MINUTE)),
            ),
        )
        assertEquals(30 * MINUTE, lifetimeDuration(records, DEFAULT_PLAN))
        assertEquals(2, daysWithDuration(records, DEFAULT_PLAN))
    }
}
