package dev.sopho.workout.data

/**
 * One completed group of an exercise.
 *
 * [at] is the wall-clock moment the group's circle was tapped, in epoch millis — the only
 * honest time we have. It is null for groups that were marked in bulk ("全部完成", or
 * jumping ahead with a tap on a later circle): those groups really happened but we never
 * saw when, and inventing a timestamp for them would be fake precision.
 */
data class SetMark(val at: Long?) {
    val isBulk: Boolean get() = at == null
}

/** one day of one exercise. marks[i] is group no. i+1, in the order they were ticked off */
typealias ExerciseLog = List<SetMark>

/** one day of training, keyed by exercise id */
typealias DayLog = Map<String, ExerciseLog>

/* ---------------------------------------------------------------- editing */

/**
 * Tapping group no. [index] (0-based):
 *  - a group that is already done -> undo it and everything after it
 *  - a group not done yet -> the groups in between could not have been timed, so they are
 *    recorded as bulk marks, and only the tapped one gets [now]
 */
fun tapMark(log: ExerciseLog, index: Int, now: Long): ExerciseLog =
    if (index < log.size) {
        log.take(index)
    } else {
        log + List(index - log.size) { SetMark(null) } + SetMark(now)
    }

/** one more group, the honest way: it happened right now */
fun addMark(log: ExerciseLog, now: Long, maxSets: Int): ExerciseLog =
    if (log.size >= maxSets) log else log + SetMark(now)

/** undo the last group */
fun dropMark(log: ExerciseLog): ExerciseLog =
    if (log.isEmpty()) log else log.dropLast(1)

/** fill the log up to [maxSets] without times: used by "全部完成" */
fun fillMarks(log: ExerciseLog, maxSets: Int): ExerciseLog =
    if (log.size >= maxSets) log else log + List(maxSets - log.size) { SetMark(null) }

/* ----------------------------------------------------------------- timing */

fun ExerciseLog.realTimes(): List<Long> = mapNotNull { it.at }

/** groups that carry no honest time */
fun ExerciseLog.bulkCount(): Int = count { it.isBulk }

/** [first, last] real tap of this exercise, or null when nothing was timed */
fun ExerciseLog.logWindow(): Pair<Long, Long>? =
    realTimes().let { times -> if (times.isEmpty()) null else times.min() to times.max() }

/**
 * How long this exercise took. Null when fewer than two groups were timed (a single tap has
 * no measurable duration) or when the clock moved backwards in between.
 */
fun ExerciseLog.logDuration(): Long? {
    val times = realTimes()
    if (times.size < 2) return null
    val span = times.max() - times.min()
    return if (span <= 0) null else span
}

fun dayWindow(day: DayLog, plan: List<Exercise>): Pair<Long, Long>? =
    plan.flatMap { day[it.id].orEmpty().realTimes() }
        .let { times -> if (times.isEmpty()) null else times.min() to times.max() }

/** how long the whole day's training took; see [logDuration] for why it can be null */
fun dayDuration(day: DayLog, plan: List<Exercise>): Long? {
    val times = plan.flatMap { day[it.id].orEmpty().realTimes() }
    if (times.size < 2) return null
    val span = times.max() - times.min()
    return if (span <= 0) null else span
}

/** bulk-marked groups on that day, across the whole plan */
fun dayBulkCount(day: DayLog, plan: List<Exercise>): Int =
    plan.sumOf { day[it.id].orEmpty().bulkCount() }

/* --------------------------------------------------------------- lifetime */

/** total measured training time over all recorded days */
fun lifetimeDuration(records: Map<String, DayLog>, plan: List<Exercise>): Long =
    records.values.sumOf { dayDuration(it, plan) ?: 0L }

/** days that have a measurable duration, i.e. the divisor for an average */
fun daysWithDuration(records: Map<String, DayLog>, plan: List<Exercise>): Int =
    records.values.count { dayDuration(it, plan) != null }
