package dev.sopho.workout.reminder

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.sopho.workout.MainActivity
import dev.sopho.workout.R
import dev.sopho.workout.data.PendingReminder
import dev.sopho.workout.data.ReminderKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 到点时的那一条通知。**只在到期时发**：不做常驻通知、不做倒计时通知
 * （倒计时只显示在应用界面里，见 docs/adr/0001）。
 */
class ReminderNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun notificationsAllowed(): Boolean = manager.areNotificationsEnabled()

    /**
     * 发通知。返回**是否真的发出去了**：没有通知权限时返回 false，
     * 调用方据此不去记"已提醒过"，界面才能如实显示"未启用"而不是假装提醒过了。
     */
    @SuppressLint("MissingPermission") // 上面已经用 areNotificationsEnabled() 守过
    fun notify(pending: PendingReminder, now: Long): Boolean {
        if (!notificationsAllowed()) return false
        ensureChannels()

        val clock = DateTimeFormatter.ofPattern("HH:mm")
        val dueText = clock.format(Instant.ofEpochMilli(pending.dueAt).atZone(ZoneId.systemDefault()))
        val lateMinutes = ((now - pending.dueAt) / 60_000L).coerceAtLeast(0L)

        val (title, text) = when (pending.kind) {
            ReminderKind.NextSet -> {
                val suffix = if (lateMinutes >= 1) "（预定 $dueText，晚了 $lateMinutes 分钟）" else "（预定 $dueText）"
                "该做下一组了" to "上一次勾选已经过去一段时间，去做下一组吧 $suffix"
            }

            ReminderKind.StartOfDay -> "今天还没开始" to "活动时间已到（$dueText），今天还没有勾过任何一组"
        }

        val notification = NotificationCompat.Builder(context, channelId(pending.kind))
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        return try {
            // 一个固定 id：后一条提醒覆盖前一条，不堆叠
            manager.notify(NOTIFICATION_ID, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /** 你勾选了（或待办已不存在）→ 把那条提醒撤掉 */
    fun cancel() = manager.cancel(NOTIFICATION_ID)

    fun ensureChannels() {
        val channels = listOf(
            channelId(ReminderKind.StartOfDay) to "开始提醒",
            channelId(ReminderKind.NextSet) to "组间提醒",
        )
        for ((id, name) in channels) {
            val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "每日运动"
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun openApp(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            2001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val NOTIFICATION_ID = 1001

        fun channelId(kind: ReminderKind): String = "reminder_${kind.name.lowercase()}"
    }
}
