package dev.sopho.workout.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/**
 * Single source of truth for plan + daily records + reminder state.
 *
 * Everything lives in this app's private SharedPreferences as small JSON blobs, so the
 * app needs no database and no network. State is exposed as StateFlow so Compose redraws
 * on every change, and every write goes straight to disk.
 *
 * Records are per group, not per count: each group carries the moment it was ticked
 * (see [SetMark]). Storage format is v2 — the old v1 files (a bare group count) are not
 * migrated: nothing reads them, an unreadable file simply means "no records yet".
 *
 * The pending reminders are **derived**, never stored (see [pendings]): fewer stored facts,
 * fewer ways for the stored state and the real state to disagree.
 */
class WorkoutStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val planFlow = MutableStateFlow(readPlan())
    private val recordsFlow = MutableStateFlow(readRecords())

    val plan: StateFlow<List<Exercise>> = planFlow.asStateFlow()
    val records: StateFlow<Map<String, DayLog>> = recordsFlow.asStateFlow()

    /* ------------------------------------------------------------ reminders */

    private val reminderSettingsFlow = MutableStateFlow(readReminderSettings())
    private val lastTickFlow = MutableStateFlow(readTime(KEY_LAST_TICK))
    private val notifiedFlow = MutableStateFlow(readTime(KEY_NOTIFIED_DUE))

    val reminderSettings: StateFlow<ReminderSettings> = reminderSettingsFlow.asStateFlow()

    /** 上一次"刚做完一组"的时刻，组间提醒以它为基准 */
    val lastTickAt: StateFlow<Long?> = lastTickFlow.asStateFlow()

    fun updateReminderSettings(settings: ReminderSettings) {
        reminderSettingsFlow.value = settings
        prefs.edit().putString(KEY_REMINDER_SETTINGS, reminderSettingsToJson(settings)).apply()
    }

    fun lastNotifiedDueAt(): Long? = notifiedFlow.value

    /** 记下"这一次提醒确实发出去了"，避免重复打扰 */
    fun markNotified(dueAt: Long) {
        notifiedFlow.value = dueAt
        prefs.edit().putLong(KEY_NOTIFIED_DUE, dueAt).apply()
    }

    /** 此刻尚未处理的提醒（到期已过但仍未处理的也在里面，见 planReminders） */
    fun pendings(now: Long = System.currentTimeMillis()): List<PendingReminder> = planReminders(
        now = now,
        settings = reminderSettingsFlow.value,
        records = recordsFlow.value,
        plan = planFlow.value,
        lastTickAt = lastTickFlow.value,
        zone = ZoneId.systemDefault(),
    )

    fun today(): String = LocalDate.now().toString()

    fun doneOn(date: String): DayLog = recordsFlow.value[date].orEmpty()

    /* ------------------------------------------------------------- records */

    /**
     * Tapping group no. [setIndex] (0-based) of [exerciseId] on [date]:
     * tick up to it (timing only the tapped group) or undo from it. See [tapMark].
     */
    fun tapSet(date: String, exerciseId: String, setIndex: Int) =
        mutateDay(date, exerciseId) { log, _ -> tapMark(log, setIndex, now()) }

    /** +1 group now, or undo the last one */
    fun addSets(date: String, exerciseId: String, delta: Int) =
        mutateDay(date, exerciseId) { log, exercise ->
            if (delta >= 0) addMark(log, now(), exercise.sets) else dropMark(log)
        }

    /** mark the rest of the plan in one go — those groups get no honest time */
    fun completeAll(date: String, exerciseId: String) =
        mutateDay(date, exerciseId) { log, exercise -> fillMarks(log, exercise.sets) }

    fun resetDay(date: String) {
        val all = recordsFlow.value.toMutableMap()
        all.remove(date)
        writeRecords(all)
    }

    fun clearAllRecords() = writeRecords(emptyMap())

    /** applies [transform] to one exercise's log and persists the result */
    private fun mutateDay(
        date: String,
        exerciseId: String,
        transform: (ExerciseLog, Exercise) -> ExerciseLog,
    ) {
        val exercise = planFlow.value.firstOrNull { it.id == exerciseId } ?: return
        val current = recordsFlow.value[date]?.get(exerciseId).orEmpty()
        val next = transform(current, exercise).take(exercise.sets)

        // 多做了几组 = 刚完成一组 → 组间提醒以此刻为基准重新起算。
        // 撤销不改变基准（物理上你已经休息过了），因此只看"变多"。
        if (next.size > current.size && date == today()) touchLastTick()

        val day = recordsFlow.value[date].orEmpty().toMutableMap()
        if (next.isEmpty()) day.remove(exerciseId) else day[exerciseId] = next

        val all = recordsFlow.value.toMutableMap()
        if (day.isEmpty()) all.remove(date) else all[date] = day
        writeRecords(all)
    }

    private fun now(): Long = System.currentTimeMillis()

    private fun touchLastTick() {
        val at = now()
        lastTickFlow.value = at
        prefs.edit().putLong(KEY_LAST_TICK, at).apply()
    }

    private fun readTime(key: String): Long? = prefs.getLong(key, 0L).takeIf { it > 0L }

    private fun readReminderSettings(): ReminderSettings = runCatching {
        val raw = prefs.getString(KEY_REMINDER_SETTINGS, null)
            ?: return@runCatching ReminderSettings.DEFAULT
        val obj = JSONObject(raw)
        val window = runCatching {
            ActivityWindow(
                startMinuteOfDay = obj.optInt("windowStart", ActivityWindow.DEFAULT.startMinuteOfDay),
                endMinuteOfDay = obj.optInt("windowEnd", ActivityWindow.DEFAULT.endMinuteOfDay),
            )
        }.getOrDefault(ActivityWindow.DEFAULT)
        ReminderSettings(
            enabled = obj.optBoolean("enabled", false),
            intervalMinutes = obj.optInt("intervalMinutes", ReminderSettings.DEFAULT_INTERVAL_MINUTES)
                .coerceIn(ReminderSettings.MIN_INTERVAL_MINUTES, ReminderSettings.MAX_INTERVAL_MINUTES),
            window = window,
        )
    }.getOrDefault(ReminderSettings.DEFAULT)

    private fun reminderSettingsToJson(settings: ReminderSettings): String = JSONObject()
        .put("enabled", settings.enabled)
        .put("intervalMinutes", settings.intervalMinutes)
        .put("windowStart", settings.window.startMinuteOfDay)
        .put("windowEnd", settings.window.endMinuteOfDay)
        .toString()

    /* ---------------------------------------------------------------- plan */

    fun updateExercise(id: String, repsPerSet: Int? = null, sets: Int? = null) {
        val updated = planFlow.value.map { exercise ->
            if (exercise.id != id) {
                exercise
            } else {
                exercise.copy(
                    repsPerSet = (repsPerSet ?: exercise.repsPerSet).coerceIn(MIN_REPS, MAX_REPS),
                    sets = (sets ?: exercise.sets).coerceIn(MIN_SETS, MAX_SETS),
                )
            }
        }
        planFlow.value = updated
        prefs.edit().putString(KEY_PLAN, planToJson(updated)).apply()
    }

    fun restoreDefaultPlan() {
        planFlow.value = DEFAULT_PLAN
        prefs.edit().putString(KEY_PLAN, planToJson(DEFAULT_PLAN)).apply()
    }

    /* ------------------------------------------------------------ storage */

    private fun readPlan(): List<Exercise> = runCatching {
        val raw = prefs.getString(KEY_PLAN, null) ?: return@runCatching DEFAULT_PLAN
        val array = JSONArray(raw)
        val parsed = buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val id = item.optString("id")
                val known = DEFAULT_PLAN.firstOrNull { it.id == id }
                if (id.isEmpty()) continue
                add(
                    Exercise(
                        id = id,
                        name = item.optString("name", known?.name ?: id),
                        emoji = item.optString("emoji", known?.emoji ?: "🏃"),
                        repsPerSet = item.optInt("reps", known?.repsPerSet ?: 10)
                            .coerceIn(MIN_REPS, MAX_REPS),
                        sets = item.optInt("sets", known?.sets ?: 1)
                            .coerceIn(MIN_SETS, MAX_SETS),
                    ),
                )
            }
        }
        if (parsed.isEmpty()) DEFAULT_PLAN else parsed
    }.getOrDefault(DEFAULT_PLAN)

    private fun readRecords(): Map<String, DayLog> = runCatching {
        val raw = prefs.getString(KEY_RECORDS, null) ?: return@runCatching emptyMap()
        readV2(JSONObject(raw))
    }.getOrDefault(emptyMap())

    /** {"v":2,"days":{"2026-08-30":{"pushup":[{"at":1690000000000},{"at":null}]}}} */
    private fun readV2(root: JSONObject): Map<String, DayLog> {
        val days = root.optJSONObject("days") ?: return emptyMap()
        return buildMap {
            for (date in days.keys()) {
                val dayObj = days.optJSONObject(date) ?: continue
                val day = buildMap {
                    for (exerciseId in dayObj.keys()) {
                        val marksJson = dayObj.optJSONArray(exerciseId) ?: continue
                        val marks = buildList {
                            for (i in 0 until marksJson.length()) {
                                val mark = marksJson.optJSONObject(i)
                                val at = if (mark == null || mark.isNull("at")) null else mark.optLong("at")
                                add(SetMark(at))
                            }
                        }
                        if (marks.isNotEmpty()) put(exerciseId, marks)
                    }
                }
                if (day.isNotEmpty()) put(date, day)
            }
        }
    }

    private fun writeRecords(records: Map<String, DayLog>) {
        recordsFlow.value = records
        prefs.edit().putString(KEY_RECORDS, recordsToJson(records)).apply()
    }

    private fun recordsToJson(records: Map<String, DayLog>): String {
        val days = JSONObject()
        for ((date, day) in records) {
            if (day.isEmpty()) continue
            val dayObj = JSONObject()
            for ((exerciseId, marks) in day) {
                if (marks.isEmpty()) continue
                val marksJson = JSONArray()
                for (mark in marks) {
                    marksJson.put(JSONObject().put("at", mark.at ?: JSONObject.NULL))
                }
                dayObj.put(exerciseId, marksJson)
            }
            days.put(date, dayObj)
        }
        return JSONObject().put("v", FORMAT_V2).put("days", days).toString()
    }

    private fun planToJson(plan: List<Exercise>): String {
        val array = JSONArray()
        for (exercise in plan) {
            array.put(
                JSONObject()
                    .put("id", exercise.id)
                    .put("name", exercise.name)
                    .put("emoji", exercise.emoji)
                    .put("reps", exercise.repsPerSet)
                    .put("sets", exercise.sets),
            )
        }
        return array.toString()
    }

    private companion object {
        const val PREFS = "workout_store"
        const val KEY_PLAN = "plan_json"
        const val KEY_RECORDS = "records_json"
        const val KEY_REMINDER_SETTINGS = "reminder_settings_json"

        /** 上一次"刚做完一组"的时刻（组间提醒的基准） */
        const val KEY_LAST_TICK = "reminder_last_tick"

        /** 最后一次确实发出通知的那条提醒的到期时间 */
        const val KEY_NOTIFIED_DUE = "reminder_notified_due"

        /** records format version written by this build; older files are not read */
        const val FORMAT_V2 = 2
    }
}
