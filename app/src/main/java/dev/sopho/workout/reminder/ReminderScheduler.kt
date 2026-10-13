package dev.sopho.workout.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.sopho.workout.data.PendingReminder
import dev.sopho.workout.data.ReminderKind
import dev.sopho.workout.data.ReminderMode

/**
 * 把一个待办变成系统里的一次闹钟。
 *
 * 有精确闹钟权限时用精确闹钟；没有就回退到 `setAndAllowWhileIdle`（零权限，但可能晚几分钟）。
 * 两个用途各用一个**稳定身份**的 PendingIntent：到期时刻放在 extra 里（靠 FLAG_UPDATE_CURRENT 更新），
 * 这样"重排"天然覆盖旧闹钟、"取消"只需按用途来，不必记住上次排的是几点。
 */
class ReminderScheduler(private val context: Context) {

    private val alarms: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    fun mode(): ReminderMode = if (canScheduleExact()) ReminderMode.Exact else ReminderMode.Approximate

    fun schedule(pending: PendingReminder) {
        val operation = operationFor(pending.kind, pending.dueAt)
        if (canScheduleExact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, pending.dueAt, operation)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, pending.dueAt, operation)
        }
    }

    fun cancel(kind: ReminderKind) {
        alarms.cancel(operationFor(kind, dueAt = null))
    }

    private fun operationFor(kind: ReminderKind, dueAt: Long?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(actionOf(kind))
        if (dueAt != null) intent.putExtra(ReminderReceiver.EXTRA_DUE_AT, dueAt)
        return PendingIntent.getBroadcast(
            context,
            requestCodeOf(kind),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        fun actionOf(kind: ReminderKind): String = "dev.sopho.workout.REMINDER_${kind.name}"

        fun requestCodeOf(kind: ReminderKind): Int = when (kind) {
            ReminderKind.StartOfDay -> 1001
            ReminderKind.NextSet -> 1002
        }
    }
}
