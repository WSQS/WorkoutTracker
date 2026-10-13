package dev.sopho.workout.data

import java.time.LocalDate

/* ---------------------------------------------------------------- per day */

/** groups of [ex] already done on that day, never more than the plan asks for */
fun doneSets(day: DayLog, ex: Exercise): Int = (day[ex.id]?.size ?: 0).coerceIn(0, ex.sets)

/** reps of [ex] already done on that day */
fun doneReps(day: DayLog, ex: Exercise): Int = doneSets(day, ex) * ex.repsPerSet

/** reps done on that day over the whole plan */
fun dayReps(day: DayLog, plan: List<Exercise>): Int = plan.sumOf { doneReps(day, it) }

/** reps the whole plan asks for */
fun planReps(plan: List<Exercise>): Int = plan.sumOf { it.totalReps }

/** 0f..1f completion of that day against the plan */
fun dayRatio(day: DayLog, plan: List<Exercise>): Float {
    val total = planReps(plan)
    if (total <= 0) return 0f
    return (dayReps(day, plan).toFloat() / total).coerceIn(0f, 1f)
}

/** true when every exercise of the plan is fully done: that is what counts as 达标 */
fun isComplete(day: DayLog, plan: List<Exercise>): Boolean =
    plan.isNotEmpty() && plan.all { doneSets(day, it) >= it.sets }

/** true when at least one group was ticked */
fun hasAny(day: DayLog): Boolean = day.values.any { it.isNotEmpty() }

/* ------------------------------------------------------------- statistics */

/**
 * Streak of fully completed days ending today. A day that is still in progress
 * (today not finished yet) does not break the streak, it just does not add to it.
 */
fun currentStreak(records: Map<String, DayLog>, plan: List<Exercise>, today: LocalDate): Int {
    if (plan.isEmpty()) return 0
    var cursor = today
    if (!isComplete(records[cursor.toString()].orEmpty(), plan)) cursor = cursor.minusDays(1)
    var streak = 0
    while (isComplete(records[cursor.toString()].orEmpty(), plan)) {
        streak++
        cursor = cursor.minusDays(1)
    }
    return streak
}

/** longest run of fully completed days ever recorded */
fun longestStreak(records: Map<String, DayLog>, plan: List<Exercise>): Int {
    if (plan.isEmpty()) return 0
    val completeDays = records
        .filterValues { isComplete(it, plan) }
        .keys
        .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        .sorted()

    var best = 0
    var run = 0
    var previous: LocalDate? = null
    for (day in completeDays) {
        run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
        if (run > best) best = run
        previous = day
    }
    return best
}

/** days that have at least one ticked group */
fun daysTrained(records: Map<String, DayLog>): Int = records.values.count { hasAny(it) }

/** days fully completed */
fun daysComplete(records: Map<String, DayLog>, plan: List<Exercise>): Int =
    records.values.count { isComplete(it, plan) }

/** lifetime reps per exercise id, computed with the current plan's reps-per-set */
fun lifetimeReps(records: Map<String, DayLog>, plan: List<Exercise>): Map<String, Int> =
    plan.associate { ex -> ex.id to records.values.sumOf { doneReps(it, ex) } }
