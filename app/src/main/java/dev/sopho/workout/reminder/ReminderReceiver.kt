package dev.sopho.workout.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.sopho.workout.data.ReminderKind
import dev.sopho.workout.data.WorkoutStore
import dev.sopho.workout.data.alreadyNotified

/**
 * 闹钟到点、以及精确闹钟权限变化时被系统叫醒的地方。
 *
 * 应用此刻可能完全是冷的（被冻结/被杀/被划掉）—— 这没关系，排的是系统闹钟。
 * 这里只做三件事：核对这条闹钟现在还算数吗、发通知、重排下一步。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val store = WorkoutStore(context)

        // 精确闹钟权限被授予/撤销：我们能不能用精确闹钟变了（系统也可能已取消已排的闹钟）→ 重排一次
        if (intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
            ReminderCenter(context, store).reconcile()
            return
        }

        val kind = kindOf(intent.action) ?: return
        val armedDueAt = intent.getLongExtra(EXTRA_DUE_AT, -1L)
        val now = System.currentTimeMillis()

        // 陈旧闹钟（之后又勾选了、或计划已完成）在这里被挡掉：它现在不在"未处理的提醒"里
        val pending = store.pendings(now)
            .firstOrNull { it.kind == kind && it.dueAt == armedDueAt } ?: return
        if (pending.alreadyNotified(store.lastNotifiedDueAt())) return

        // 只有真的发出去了才记"已提醒"：没通知权限时不记，界面因此能如实显示"未启用"
        if (ReminderNotifier(context).notify(pending, now)) {
            store.markNotified(pending.dueAt)
        }

        // 发完重排：例如开始提醒发出后，把明天那条排上（不必等应用再被打开）
        ReminderCenter(context, store).reconcile()
    }

    companion object {
        const val EXTRA_DUE_AT = "due_at"

        fun kindOf(action: String?): ReminderKind? = ReminderKind.entries
            .firstOrNull { ReminderScheduler.actionOf(it) == action }
    }
}
