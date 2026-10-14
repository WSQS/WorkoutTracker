package dev.sopho.workout.reminder

import android.content.Context
import dev.sopho.workout.data.ReminderAction
import dev.sopho.workout.data.ReminderKind
import dev.sopho.workout.data.WorkoutStore
import dev.sopho.workout.data.reminderAction
import java.time.ZoneId

/**
 * 让系统里的闹钟与「此刻尚未处理的提醒」保持一致。应用里每次状态变化都调用它（幂等）。
 *
 * 该做什么由纯函数 [reminderAction] 决定（那里有好几种边界，都能单测）；
 * 这里只负责执行：排、撤、或“别碰”。
 */
class ReminderCenter(private val context: Context, private val store: WorkoutStore) {

    private val scheduler = ReminderScheduler(context)
    private val notifier = ReminderNotifier(context)

    fun reconcile(now: Long = System.currentTimeMillis()) {
        val outstanding = store.pendings(now)
        val zone = ZoneId.systemDefault()

        // 提醒开着就把通知渠道建好：这样你第一次发提醒之前，就能在系统设置里看到并调整它
        // （渠道是第一次发通知时才创建的旧做法，会让你在“设置→通知”里找不到东西）。
        if (store.reminderSettings.value.enabled) {
            notifier.ensureChannels()
        }

        for (kind in ReminderKind.entries) {
            val action = reminderAction(
                kind = kind,
                outstanding = outstanding.firstOrNull { it.kind == kind },
                now = now,
                settings = store.reminderSettings.value,
                records = store.records.value,
                zone = zone,
            )
            when (action) {
                is ReminderAction.Schedule -> scheduler.schedule(action.pending)
                ReminderAction.Cancel -> scheduler.cancel(kind)
                ReminderAction.LeaveAlone -> Unit
            }
        }

        // 通知：只有"已经发过的那条提醒"仍然未处理时才留着。
        // 你勾选之后，旧提醒会从 outstanding 里消失 → 这里把通知撤掉。
        val notifiedDueAt = store.lastNotifiedDueAt()
        val stillRelevant = outstanding.any { it.dueAt == notifiedDueAt }
        if (notifiedDueAt == null || !stillRelevant) notifier.cancel()
    }
}
