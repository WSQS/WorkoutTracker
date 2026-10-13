package dev.sopho.workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sopho.workout.data.DayLog
import dev.sopho.workout.data.Exercise
import dev.sopho.workout.data.currentStreak
import dev.sopho.workout.data.daysComplete
import dev.sopho.workout.data.daysTrained
import dev.sopho.workout.data.daysWithDuration
import dev.sopho.workout.data.dayBulkCount
import dev.sopho.workout.data.dayDuration
import dev.sopho.workout.data.dayRatio
import dev.sopho.workout.data.dayWindow
import dev.sopho.workout.data.doneSets
import dev.sopho.workout.data.hasAny
import dev.sopho.workout.data.isComplete
import dev.sopho.workout.data.lifetimeDuration
import dev.sopho.workout.data.lifetimeReps
import dev.sopho.workout.data.longestStreak
import dev.sopho.workout.data.planReps
import java.time.LocalDate

/** History: lifetime totals, a 14-day bar chart and the last 30 days day by day. */
@Composable
fun HistoryScreen(
    plan: List<Exercise>,
    records: Map<String, DayLog>,
    today: LocalDate,
) {
    val last30 = remember(today) { (0 until 30).map { today.minusDays(it.toLong()) } }
    val last14 = remember(today) { (0 until 14).map { today.minusDays(13L - it) } }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { TotalsCard(plan = plan, records = records, today = today) }
        item { ChartCard(plan = plan, records = records, days = last14) }
        item {
            Text(
                text = "最近 30 天",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        items(items = last30, key = { it.toString() }) { date ->
            DayCard(
                plan = plan,
                done = records[date.toString()].orEmpty(),
                dateIso = date.toString(),
                today = today,
            )
        }
    }
}

@Composable
private fun TotalsCard(plan: List<Exercise>, records: Map<String, DayLog>, today: LocalDate) {
    val lifetime = remember(records, plan) { lifetimeReps(records, plan) }
    val duration = remember(records, plan) { lifetimeDuration(records, plan) }
    val timedDays = remember(records, plan) { daysWithDuration(records, plan) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "累计数据",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("打卡天数", "${daysTrained(records)} 天", Modifier.weight(1f))
                StatTile("达标天数", "${daysComplete(records, plan)} 天", Modifier.weight(1f))
                StatTile(
                    label = "当前连续",
                    value = "${currentStreak(records, plan, today)} 天",
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider()

            plan.forEach { exercise ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${exercise.emoji} ${exercise.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${lifetime[exercise.id] ?: 0} 个",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Text(
                text = "最长连续达标 ${longestStreak(records, plan)} 天 · 每日目标合计 ${planReps(plan)} 个",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (duration > 0 && timedDays > 0) {
                Text(
                    text = "累计训练时长 ${durationLabel(duration)} · 平均每次 ${durationLabel(duration / timedDays)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ChartCard(plan: List<Exercise>, records: Map<String, DayLog>, days: List<LocalDate>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "最近 14 天完成度",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Row(
                modifier = Modifier.fillMaxWidth().height(100.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                days.forEach { date ->
                    val ratio = dayRatio(records[date.toString()].orEmpty(), plan)
                    val barHeight = (96f * ratio).coerceAtLeast(4f).dp
                    val color = if (ratio <= 0f) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f + 0.55f * ratio)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(barHeight)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                days.forEach { date ->
                    Text(
                        text = "${date.dayOfMonth}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCard(plan: List<Exercise>, done: DayLog, dateIso: String, today: LocalDate) {
    val complete = isComplete(done, plan)
    val ratio = dayRatio(done, plan)
    val tinted = complete || dateIso == today.toString()
    val window = dayWindow(done, plan)
    val duration = dayDuration(done, plan)
    val bulk = dayBulkCount(done, plan)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (complete) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dateLabelShort(dateIso),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                val relative = relativeLabel(dateIso, today)
                if (relative.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = relative,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (complete) "达标 ✓" else percent(ratio),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (tinted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (hasAny(done)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    plan.forEach { exercise ->
                        Text(
                            text = "${exercise.emoji} ${doneSets(done, exercise)}/${exercise.sets}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else {
                Text(
                    text = "未打卡",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (window != null) {
                Text(
                    text = buildString {
                        append("🕘 ${timeRangeLabel(window.first, window.second)}")
                        if (duration != null) append(" · 用时 ${durationLabel(duration)}")
                        if (bulk > 0) append(" · 含 $bulk 组补记")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
