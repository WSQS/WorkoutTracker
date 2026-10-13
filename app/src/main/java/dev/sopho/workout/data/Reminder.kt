package dev.sopho.workout.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 提醒的两种用途，见 CONTEXT.md */
enum class ReminderKind { StartOfDay, NextSet }

/**
 * 活动时间：允许发出提醒的时段，同一天内、不跨零点。
 * 区间是 [start, end)：18:00 整已经算窗口之外。
 */
data class ActivityWindow(
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
) {
    init {
        require(startMinuteOfDay >= 0 && endMinuteOfDay <= 24 * 60) { "分钟数越界" }
        require(startMinuteOfDay < endMinuteOfDay) { "活动时间必须开始早于结束（不支持跨零点）" }
    }

    fun contains(instant: Long, zone: ZoneId): Boolean {
        val time = Instant.ofEpochMilli(instant).atZone(zone).toLocalTime()
        val minute = time.hour * 60 + time.minute
        return minute >= startMinuteOfDay && minute < endMinuteOfDay
    }

    fun startOf(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).plusMinutes(startMinuteOfDay.toLong()).toInstant().toEpochMilli()

    /** 这一天的活动时间结束时刻 */
    fun endOf(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).plusMinutes(endMinuteOfDay.toLong()).toInstant().toEpochMilli()

    companion object {
        /** 默认 9:00–18:00 */
        val DEFAULT = ActivityWindow(startMinuteOfDay = 9 * 60, endMinuteOfDay = 18 * 60)
    }
}

data class ReminderSettings(
    val enabled: Boolean = false,
    val intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
    val window: ActivityWindow = ActivityWindow.DEFAULT,
) {
    companion object {
        const val DEFAULT_INTERVAL_MINUTES = 30
        const val MIN_INTERVAL_MINUTES = 1
        const val MAX_INTERVAL_MINUTES = 180
        val DEFAULT = ReminderSettings()
    }
}

/** 提醒的准时程度（用户可见的差别，不是权限状态） */
enum class ReminderMode { Exact, Approximate }

/**
 * 超过这个时长还没送达，就认为这一次提醒已经错过。
 * 15 分钟取自"不精确闹钟在 Doze 下的限频约 9 分钟/次"，留出余量，
 * 免得闹钟只是稍晚一点就误报"超时"。
 */
const val REMINDER_EXPIRED_AFTER_MILLIS = 15 * 60_000L

/** 待办：已经排定、正在等到点（或已到点但还没处理）的那一次提醒 */
data class PendingReminder(
    val kind: ReminderKind,
    val dueAt: Long,
    /** 它是被什么排出来的：组间提醒 = 那次勾选的时刻；开始提醒 = 排它的时刻 */
    val armedFromAt: Long,
) {
    fun isDue(now: Long): Boolean = now >= dueAt
}

/**
 * 已经过期到"可以确定它不会来了"的程度：到期已过 [PendingReminder.EXPIRED_AFTER_MILLIS]，
 * 且没有发出过通知。15 分钟取自"不精确闹钟在 Doze 下限频约 9 分钟/次"，留出余量，
 * 免得闹钟只是稍晚就误报"超时"。
 */
fun PendingReminder.hasMissed(now: Long, lastNotifiedDueAt: Long?): Boolean =
    now >= dueAt + REMINDER_EXPIRED_AFTER_MILLIS && !alreadyNotified(lastNotifiedDueAt)

/** 这一次提醒是否已经发过通知（避免重复打扰，也让界面能说"已提醒"） */
fun PendingReminder.alreadyNotified(lastNotifiedDueAt: Long?): Boolean = lastNotifiedDueAt == dueAt

/** 某一种用途此刻该对系统里的闹钟做什么 */
sealed interface ReminderAction {
    data class Schedule(val pending: PendingReminder) : ReminderAction

    /** 这条用途已经没有待办了（例如提醒关掉、或今天计划已完成）→ 撤掉闹钟 */
    data object Cancel : ReminderAction

    /**
     * 已到期、正在等系统投递（可能落在系统的延迟窗口里）→ **不要碰**那条闹钟。
     * 把它当成“不需要了”去取消，会让提醒在你打开一次应用之后就静静消失 —— 而它本来还会送到。
     */
    data object LeaveAlone : ReminderAction
}

fun reminderAction(
    kind: ReminderKind,
    outstanding: PendingReminder?,
    now: Long,
    settings: ReminderSettings,
    records: Map<String, DayLog>,
    zone: ZoneId,
): ReminderAction = when {
    outstanding == null -> ReminderAction.Cancel

    outstanding.dueAt > now -> ReminderAction.Schedule(outstanding)

    // 开始提醒到期后仍然“未处理”，但下一次窗口开始必须排上，否则提醒链会断
    kind == ReminderKind.StartOfDay ->
        nextStartReminder(now, settings, records, zone)
            ?.let { ReminderAction.Schedule(it) }
            ?: ReminderAction.Cancel

    else -> ReminderAction.LeaveAlone
}

/**
 * 下一个**未来的**开始提醒。
 *
 * 这条单独存在的原因：一条开始提醒到期之后仍然留在 [planReminders] 的结果里（"正在等你开始"），
 * 但它自己已经不需要闹钟了；如果此时不再把明天那条排上，提醒链就断了 ——
 * 用户在一个窗口内的时间里开启提醒、之后不再打开应用，就再也收不到任何提醒。
 * 所以窗口内那条已到期时，直接把下一次窗口开始排上。
 */
fun nextStartReminder(
    now: Long,
    settings: ReminderSettings,
    records: Map<String, DayLog>,
    zone: ZoneId,
): PendingReminder? {
    if (!settings.enabled) return null
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val target = if (now < settings.window.startOf(today, zone)) today else today.plusDays(1)
    if (hasAny(records[target.toString()].orEmpty())) return null
    return PendingReminder(
        kind = ReminderKind.StartOfDay,
        dueAt = settings.window.startOf(target, zone),
        armedFromAt = now,
    )
}

/**
 * 提醒的全部规则，纯函数。返回的是**尚未处理的提醒**，不是"未来的闹钟"：
 * 一条提醒到期之后仍留在列表里，直到你勾选下一组（或当天结束）。
 * 「排闹钟」只排其中到期时间在未来的那些（见 ReminderCenter）—— 这个区分很重要，
 * 否则会出现"开始提醒刚发出就被自己撤掉"这类问题。
 *
 * 规则（每条都对应一次明确的决定，见 docs/adr/0001）：
 *  - 提醒设置关掉 → 列表为空。
 *  - 组间提醒：上次勾选在活动时间内、且「勾选 + 间隔」也仍在活动时间内，才存在；否则不排。
 *  - 当天计划已全部完成 → 不再有组间提醒（没什么可催的了）。
 *  - 开始提醒（同一种用途最多一条）：
 *      窗口开始前 → 今天这条（未来）；窗口内且今天还没勾过任何一组 → 今天这条（到期已过，即"正在等你开始"）；
 *      其余情况 → 明天这条。
 *  - 到期日期跨过零点的旧提醒自动消失。
 */
fun planReminders(
    now: Long,
    settings: ReminderSettings,
    records: Map<String, DayLog>,
    plan: List<Exercise>,
    lastTickAt: Long?,
    zone: ZoneId,
): List<PendingReminder> {
    if (!settings.enabled) return emptyList()

    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val todayLog = records[today.toString()].orEmpty()
    val result = mutableListOf<PendingReminder>()

    // —— 组间提醒 ——
    val planFinishedToday = plan.isNotEmpty() && isComplete(todayLog, plan)
    if (lastTickAt != null && !planFinishedToday) {
        val dueAt = lastTickAt + settings.intervalMinutes * 60_000L
        val dueIsToday = Instant.ofEpochMilli(dueAt).atZone(zone).toLocalDate() == today
        if (dueIsToday &&
            settings.window.contains(lastTickAt, zone) &&
            settings.window.contains(dueAt, zone)
        ) {
            result += PendingReminder(ReminderKind.NextSet, dueAt = dueAt, armedFromAt = lastTickAt)
        }
    }

    // —— 开始提醒 ——
    val windowStart = settings.window.startOf(today, zone)
    val windowEnd = settings.window.endOf(today, zone)
    val waitingToStart = now >= windowStart && now < windowEnd && !hasAny(todayLog)
    val startDueAt = if (waitingToStart) {
        windowStart
    } else {
        val target = if (now < windowStart) today else today.plusDays(1)
        settings.window.startOf(target, zone)
    }
    val targetDate = Instant.ofEpochMilli(startDueAt).atZone(zone).toLocalDate()
    if (!hasAny(records[targetDate.toString()].orEmpty())) {
        result += PendingReminder(ReminderKind.StartOfDay, dueAt = startDueAt, armedFromAt = now)
    }

    return result
}
