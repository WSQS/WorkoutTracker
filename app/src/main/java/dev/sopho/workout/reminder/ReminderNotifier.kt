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

    /**
     * 创建两个通知渠道。
     *
     * 注意两件事：
     *  1. 振动是**按渠道**控制的（Android 8+），不在渠道上设振动模式就是不震 —— 曾经因为漏了这一步，
     *     “提示音+震动”里只剩了提示音。
     *  2. 渠道创建后，应用**改不了**它的重要度/振动/声音（只能改名字与描述）——所以设错了只能换 id。
     *     这里给 id 带版本号并删掉旧的，保证已有安装也能真正拿到新设置。
     */
    fun ensureChannels() {
        val channels = listOf(
            channelId(ReminderKind.StartOfDay) to "开始提醒",
            channelId(ReminderKind.NextSet) to "组间提醒",
        )
        for ((id, name) in channels) {
            val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "每日运动"
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN
            }
            manager.createNotificationChannel(channel)
        }
        // 清掉没有版本号的旧渠道，别在设置里留一堆
        for (old in LEGACY_CHANNEL_IDS) {
            manager.deleteNotificationChannel(old)
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

        /** 两下短震（等待 0ms、震 300ms、停 200ms、再震 300ms） */
        private val VIBRATION_PATTERN = longArrayOf(0, 300, 200, 300)

        /** 渠道 id 带版本：渠道创建后改不了重要度/振动，改设置只能换 id（见 ensureChannels） */
        private const val CHANNEL_VERSION = 2

        /** 没有版本号的旧渠道（只在 1.2.0 试装时短暂存在过） */
        private val LEGACY_CHANNEL_IDS = listOf("reminder_startofday", "reminder_nextset")

        fun channelId(kind: ReminderKind): String =
            "reminder_${kind.name.lowercase()}_v$CHANNEL_VERSION"
    }
}
