package dev.sopho.workout.data

import dev.sopho.workout.ui.reminderLine
import dev.sopho.workout.ui.safeWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

private val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
private const val DAY = "2026-10-13"

/** "09:00" → 那一刻的 epoch millis */
private fun at(hhmm: String, date: String = DAY): Long =
    LocalDateTime.parse("${date}T$hhmm").atZone(ZONE).toInstant().toEpochMilli()

private val PLAN = listOf(
    Exercise("pushup", "俯卧撑", "💪", repsPerSet = 10, sets = 8),
    Exercise("squat", "深蹲", "🦵", repsPerSet = 10, sets = 12),
    Exercise("situp", "仰卧起坐", "🔥", repsPerSet = 20, sets = 5),
)

private val ON = ReminderSettings(enabled = true)

private fun dayWithOneSet(): Map<String, DayLog> =
    mapOf(DAY to mapOf("pushup" to listOf(SetMark(at("09:10")))))

private fun testPlan(now: Long, lastTickAt: Long?, records: Map<String, DayLog> = emptyMap()) =
    planReminders(
        now = now,
        settings = ON,
        records = records,
        plan = PLAN,
        lastTickAt = lastTickAt,
        zone = ZONE,
    )

/** 把规划器和界面文案串起来测：这正是运行时真正走的路径 */
private fun reminderLineText(
    now: Long,
    settings: ReminderSettings,
    lastTickAt: Long?,
    notificationsAllowed: Boolean = true,
    records: Map<String, DayLog> = emptyMap(),
    lastNotifiedDueAt: Long? = null,
): String? = reminderLine(
    now = now,
    settings = settings,
    pendings = planReminders(now, settings, records, PLAN, lastTickAt, ZONE),
    lastNotifiedDueAt = lastNotifiedDueAt,
    notificationsAllowed = notificationsAllowed,
    mode = ReminderMode.Exact,
    zone = ZONE,
)

class ReminderTest {

    /* ------------------------------------------------------ 活动时间窗口 */

    @Test
    fun windowIsStartInclusiveEndExclusive() {
        val window = ActivityWindow.DEFAULT // 9:00–18:00
        assertTrue(window.contains(at("09:00"), ZONE))
        assertTrue(window.contains(at("17:59"), ZONE))
        assertFalse(window.contains(at("08:59"), ZONE))
        assertFalse(window.contains(at("18:00"), ZONE))
    }

    /* ---------------------------------------------------------- 组间提醒 */

    @Test
    fun nextSetIsArmedWhenBothEndsAreInsideTheWindow() {
        val pending = testPlan(now = at("10:00"), lastTickAt = at("10:00"))
            .singleOrNull { it.kind == ReminderKind.NextSet }
        assertEquals(at("10:30"), pending?.dueAt)
    }

    @Test
    fun nextSetIsNotArmedWhenItsDueWouldFallOutsideTheWindow() {
        // 17:45 勾一组 → 到期 18:15，已在窗口外 → 不排（规则只有一句：提醒只在窗口内工作）
        val pendings = testPlan(now = at("17:45"), lastTickAt = at("17:45"))
        assertTrue(pendings.none { it.kind == ReminderKind.NextSet })
    }

    @Test
    fun nextSetIsNotArmedWhenTheTickItselfWasOutsideTheWindow() {
        // 8:45 勾一组（窗口外）→ 即使到期 9:15 落在窗口内，也不排
        val pendings = testPlan(now = at("08:45"), lastTickAt = at("08:45"))
        assertTrue(pendings.none { it.kind == ReminderKind.NextSet })
    }

    @Test
    fun nextSetDisappearsOnceTheWholePlanIsDone() {
        val complete = mapOf(
            DAY to mapOf(
                "pushup" to List(8) { SetMark(null) },
                "squat" to List(12) { SetMark(null) },
                "situp" to List(5) { SetMark(null) },
            ),
        )
        val pendings = testPlan(now = at("10:00"), lastTickAt = at("10:00"), records = complete)
        assertTrue(pendings.none { it.kind == ReminderKind.NextSet })
    }

    @Test
    fun yesterdaysTickDoesNotProduceATodayReminder() {
        val pendings = testPlan(
            now = at("10:00"),
            lastTickAt = at("17:00", date = "2026-10-12"),
        )
        assertTrue(pendings.none { it.kind == ReminderKind.NextSet })
    }

    /* ---------------------------------------------------------- 开始提醒 */

    @Test
    fun beforeTheWindowTheStartReminderIsToday() {
        val pending = testPlan(now = at("08:00"), lastTickAt = null)
            .singleOrNull { it.kind == ReminderKind.StartOfDay }
        assertEquals(at("09:00"), pending?.dueAt)
    }

    @Test
    fun insideTheWindowWithNothingDoneTheStartReminderIsOutstanding() {
        val pending = testPlan(now = at("09:30"), lastTickAt = null)
            .singleOrNull { it.kind == ReminderKind.StartOfDay }
        assertEquals(at("09:00"), pending?.dueAt)
        assertTrue(pending!!.isDue(at("09:30")))
    }

    @Test
    fun onceAnythingIsDoneTheStartReminderMovesToTomorrow() {
        val pending = testPlan(now = at("09:30"), lastTickAt = at("09:10"), records = dayWithOneSet())
            .singleOrNull { it.kind == ReminderKind.StartOfDay }
        assertEquals(at("09:00", date = "2026-10-14"), pending?.dueAt)
    }

    @Test
    fun afterTheWindowTheStartReminderIsTomorrow() {
        val pending = testPlan(now = at("20:00"), lastTickAt = null)
            .singleOrNull { it.kind == ReminderKind.StartOfDay }
        assertEquals(at("09:00", date = "2026-10-14"), pending?.dueAt)
    }

    @Test
    fun nextStartReminderKeepsTheChainAliveInsideTheWindow() {
        // 13:00、今天还没开始 → 今天这条已经是"已到期、正在等你开始"，不需要闹钟了，
        // 但明天 09:00 必须排上，否则用户不再打开应用就再也收不到提醒。
        val next = nextStartReminder(now = at("13:00"), settings = ON, records = emptyMap(), zone = ZONE)
        assertEquals(at("09:00", date = "2026-10-14"), next?.dueAt)

        // 窗口开始前：排的就是今天
        val beforeWindow = nextStartReminder(now = at("08:00"), settings = ON, records = emptyMap(), zone = ZONE)
        assertEquals(at("09:00"), beforeWindow?.dueAt)

        // 关掉提醒 → 什么都不排
        assertNull(
            nextStartReminder(at("13:00"), ReminderSettings(enabled = false), emptyMap(), ZONE),
        )
    }

    @Test
    fun turningRemindersOffRemovesEverything() {
        val pendings = planReminders(
            now = at("10:00"),
            settings = ReminderSettings(enabled = false),
            records = emptyMap(),
            plan = PLAN,
            lastTickAt = at("10:00"),
            zone = ZONE,
        )
        assertTrue(pendings.isEmpty())
    }

    /* ------------------------------------------------------------ 超时判定 */

    @Test
    fun missedNeedsFifteenMinutesAndNoNotification() {
        val pending = PendingReminder(ReminderKind.NextSet, dueAt = at("10:00"), armedFromAt = at("09:30"))
        assertFalse(pending.hasMissed(at("10:14"), lastNotifiedDueAt = null))
        assertTrue(pending.hasMissed(at("10:16"), lastNotifiedDueAt = null))
        // 已经发过通知的，不算"漏掉"
        assertFalse(pending.hasMissed(at("10:16"), lastNotifiedDueAt = at("10:00")))
        assertTrue(pending.alreadyNotified(at("10:00")))
    }

    @Test
    fun expiredBoundaryIsExactlyFifteenMinutes() {
        val pending = PendingReminder(ReminderKind.NextSet, dueAt = 1_000_000L, armedFromAt = 0L)
        assertFalse(pending.hasMissed(1_000_000L + 15 * 60_000L - 1, null))
        assertTrue(pending.hasMissed(1_000_000L + 15 * 60_000L, null))
    }

    /* ------------------------------------------------------- 活动时间校验 */

    @Test
    fun safeWindowAlwaysKeepsStartBeforeEnd() {
        assertEquals(ActivityWindow(9 * 60, 18 * 60), safeWindow(9 * 60, 18 * 60))
        // 用户把起点越过了终点 → 终点被推到起点之后一小时
        assertEquals(ActivityWindow(20 * 60, 21 * 60), safeWindow(20 * 60, 9 * 60))
        // 起点贴到一天末尾 → 仍合法
        val late = safeWindow(23 * 60 + 30, 0)
        assertTrue(late.startMinuteOfDay < late.endMinuteOfDay)
    }

    /* ------------------------------------------------- 系统闹钟的动作决策 */

    @Test
    fun anAlreadyDueNextSetAlarmIsLeftAloneNotCancelled() {
        // 到期已过、但系统可能还在延迟窗口里投递 → 绝对不能在这里取消它，
        // 否则“用户在这段时间里打开一次应用”就会让提醒静静消失。
        val due = PendingReminder(ReminderKind.NextSet, dueAt = at("10:30"), armedFromAt = at("10:00"))
        assertEquals(
            ReminderAction.LeaveAlone,
            reminderAction(
                kind = ReminderKind.NextSet,
                outstanding = due,
                now = at("10:35"),
                settings = ON,
                records = emptyMap(),
                zone = ZONE,
            ),
        )
    }

    @Test
    fun aFutureReminderGetsScheduled() {
        val pending = PendingReminder(ReminderKind.NextSet, dueAt = at("10:30"), armedFromAt = at("10:00"))
        assertEquals(
            ReminderAction.Schedule(pending),
            reminderAction(ReminderKind.NextSet, pending, at("10:05"), ON, emptyMap(), ZONE),
        )
    }

    @Test
    fun withoutAnOutstandingReminderTheAlarmIsCancelled() {
        assertEquals(
            ReminderAction.Cancel,
            reminderAction(ReminderKind.NextSet, null, at("10:05"), ON, emptyMap(), ZONE),
        )
    }

    @Test
    fun aDueStartReminderSchedulesTomorrowsInstead() {
        val due = PendingReminder(ReminderKind.StartOfDay, dueAt = at("09:00"), armedFromAt = at("08:00"))
        val action = reminderAction(ReminderKind.StartOfDay, due, at("13:00"), ON, emptyMap(), ZONE)
        assertTrue(action is ReminderAction.Schedule)
        assertEquals(at("09:00", date = "2026-10-14"), (action as ReminderAction.Schedule).pending.dueAt)
    }

    /* ------------------------------------------------------------- 界面文案 */

    @Test
    fun reminderLineIsSilentWhenRemindersAreOff() {
        assertNull(
            reminderLineText(
                now = at("10:00"),
                settings = ReminderSettings(enabled = false),
                lastTickAt = null,
            ),
        )
    }

    @Test
    fun reminderLineSaysWhenNotificationsAreMissing() {
        val text = reminderLineText(
            now = at("10:00"),
            settings = ON,
            lastTickAt = at("10:00"),
            notificationsAllowed = false,
        )
        assertTrue(text!!.contains("通知权限"))
    }

    @Test
    fun reminderLineCountsDownToTheNextSet() {
        val text = reminderLineText(now = at("10:15"), settings = ON, lastTickAt = at("10:00"))
        assertTrue(text!!.contains("距下一组"))
        assertTrue(text.contains("14:59") || text.contains("15:00"))
    }

    @Test
    fun reminderLineReportsAMissedReminder() {
        // 到期 10:30，现在 11:00 → 超过 15 分钟且没发过通知 → 算超时
        val text = reminderLineText(now = at("11:00"), settings = ON, lastTickAt = at("10:00"))
        assertTrue(text!!.contains("已超时"))
        assertTrue(text.contains("30 分钟"))
    }

    @Test
    fun reminderLineSaysOutsideTheWindow() {
        val text = reminderLineText(now = at("19:00"), settings = ON, lastTickAt = null)
        assertTrue(text!!.contains("不在活动时间"))
    }
}
