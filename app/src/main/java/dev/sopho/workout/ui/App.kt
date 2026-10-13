package dev.sopho.workout.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import dev.sopho.workout.data.ActivityWindow
import dev.sopho.workout.data.PendingReminder
import dev.sopho.workout.data.ReminderMode
import dev.sopho.workout.data.ReminderSettings
import dev.sopho.workout.data.WorkoutStore
import dev.sopho.workout.data.currentStreak
import dev.sopho.workout.reminder.ReminderCenter
import dev.sopho.workout.reminder.ReminderScheduler
import kotlinx.coroutines.delay
import java.time.LocalDate

private enum class Tab(val label: String, val title: String, val icon: ImageVector) {
    Today("今天", "每日运动", Icons.Filled.Check),
    History("记录", "运动记录", Icons.Filled.DateRange),
    Settings("设置", "计划与设置", Icons.Filled.Settings),
}

/** 提醒相关的界面状态，一次算好传给两个页面 */
data class ReminderUiState(
    val settings: ReminderSettings,
    val pendings: List<PendingReminder>,
    val lastNotifiedDueAt: Long?,
    val notificationsAllowed: Boolean,
    val mode: ReminderMode,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutApp(store: WorkoutStore, today: String) {
    val plan by store.plan.collectAsState()
    val records by store.records.collectAsState()
    val reminderSettings by store.reminderSettings.collectAsState()
    val lastTickAt by store.lastTickAt.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val current = Tab.entries[tab]
    val todayDate = LocalDate.parse(today)
    val context = LocalContext.current

    // 每秒走一次的时钟：今天页的倒计时要跟着变（只在应用里走，不唤醒设备）
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    // 状态一变就把系统里的闹钟对齐一次（幂等；应用被关掉后由接收器自己接力）
    LaunchedEffect(reminderSettings, records, lastTickAt) {
        ReminderCenter(context.applicationContext, store).reconcile()
    }

    val reminderState = ReminderUiState(
        settings = reminderSettings,
        pendings = store.pendings(now),
        lastNotifiedDueAt = store.lastNotifiedDueAt(),
        notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        mode = ReminderScheduler(context).mode(),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current.title) },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { index, entry ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(entry.icon, contentDescription = entry.label) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (current) {
                Tab.Today -> TodayScreen(
                    plan = plan,
                    done = records[today].orEmpty(),
                    dateIso = today,
                    streak = currentStreak(records, plan, todayDate),
                    reminderText = reminderLine(
                        now = now,
                        settings = reminderState.settings,
                        pendings = reminderState.pendings,
                        lastNotifiedDueAt = reminderState.lastNotifiedDueAt,
                        notificationsAllowed = reminderState.notificationsAllowed,
                        mode = reminderState.mode,
                    ),
                    onTapSet = { exerciseId, index -> store.tapSet(today, exerciseId, index) },
                    onAddSets = { exerciseId, delta -> store.addSets(today, exerciseId, delta) },
                    onCompleteAll = { exerciseId -> store.completeAll(today, exerciseId) },
                )

                Tab.History -> HistoryScreen(
                    plan = plan,
                    records = records,
                    today = todayDate,
                )

                Tab.Settings -> SettingsScreen(
                    plan = plan,
                    reminder = reminderState,
                    onReps = { id, reps -> store.updateExercise(id, repsPerSet = reps) },
                    onSets = { id, sets -> store.updateExercise(id, sets = sets) },
                    onResetToday = { store.resetDay(today) },
                    onClearAll = { store.clearAllRecords() },
                    onRestorePlan = { store.restoreDefaultPlan() },
                    onReminderEnabled = { enabled ->
                        store.updateReminderSettings(reminderState.settings.copy(enabled = enabled))
                    },
                    onIntervalMinutes = { minutes ->
                        store.updateReminderSettings(reminderState.settings.copy(intervalMinutes = minutes))
                    },
                    onWindow = { start, end ->
                        store.updateReminderSettings(
                            reminderState.settings.copy(window = safeWindow(start, end)),
                        )
                    },
                )
            }
        }
    }
}

/**
 * 活动时间不支持跨零点（提醒的语义就是"白天"），所以这里保证开始早于结束：
 * 用户把起点设到终点之后时，把另一端推开一小时，而不是崩掉或静默忽略。
 */
fun safeWindow(startMinuteOfDay: Int, endMinuteOfDay: Int): ActivityWindow {
    val start = startMinuteOfDay.coerceIn(0, 24 * 60 - 1)
    val end = endMinuteOfDay.coerceIn(0, 24 * 60)
    if (start < end) return ActivityWindow(start, end)
    // 用户把一端越过了另一端：把结束推到开始之后一小时（不够就往前留一小时），
    // 保证窗口始终合法，而不是崩掉或默默忽略。
    val pushed = (start + 60).coerceAtMost(24 * 60)
    return if (pushed > start) ActivityWindow(start, pushed) else ActivityWindow(start - 60, start)
}
