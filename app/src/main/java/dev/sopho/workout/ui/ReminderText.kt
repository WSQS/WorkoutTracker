package dev.sopho.workout.ui

import dev.sopho.workout.data.PendingReminder
import dev.sopho.workout.data.ReminderKind
import dev.sopho.workout.data.ReminderMode
import dev.sopho.workout.data.ReminderSettings
import dev.sopho.workout.data.alreadyNotified
import dev.sopho.workout.data.hasMissed
import java.time.ZoneId

/**
 * 今天页顶部那一行提醒状态。返回 null = 什么都不用说（提醒关着）。
 * 纯函数，边界（窗口外、已到期、已提醒、超时、近似模式）都能单测。
 */
fun reminderLine(
    now: Long,
    settings: ReminderSettings,
    pendings: List<PendingReminder>,
    lastNotifiedDueAt: Long?,
    notificationsAllowed: Boolean,
    mode: ReminderMode,
    zone: ZoneId = ZoneId.systemDefault(),
): String? {
    if (!settings.enabled) return null
    if (!notificationsAllowed) return "提醒未启用：缺少通知权限（到「设置」里打开）"

    val window = "${clockText(settings.window.startMinuteOfDay)}–${clockText(settings.window.endMinuteOfDay)}"
    val modeNote = if (mode == ReminderMode.Approximate) " · 近似模式，可能晚十几分钟到一小时" else ""

    val nextSet = pendings.firstOrNull { it.kind == ReminderKind.NextSet }
    if (nextSet != null) {
        return when {
            nextSet.hasMissed(now, lastNotifiedDueAt) ->
                "上次排的下一组已超时 ${minutesBetween(nextSet.dueAt, now)} 分钟$modeNote"

            nextSet.isDue(now) ->
                "该做下一组了${if (nextSet.alreadyNotified(lastNotifiedDueAt)) "（已提醒）" else ""}$modeNote"

            else ->
                "距下一组 ${countdownText(nextSet.dueAt - now)}（$window 内提醒）$modeNote"
        }
    }

    val start = pendings.firstOrNull { it.kind == ReminderKind.StartOfDay }
    if (start != null && start.isDue(now)) {
        return "今天还没开始${if (start.alreadyNotified(lastNotifiedDueAt)) "（已提醒）" else ""}"
    }

    return "当前不在活动时间（$window 内提醒）$modeNote"
}

/** 分钟数（一天的分钟）→ "09:00" */
fun clockText(minuteOfDay: Int): String =
    "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

/** 剩余毫秒 → "12:34"；超过一小时显示 "1:02:03" */
fun countdownText(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

private fun minutesBetween(from: Long, to: Long): Long = ((to - from) / 60_000L).coerceAtLeast(0)
