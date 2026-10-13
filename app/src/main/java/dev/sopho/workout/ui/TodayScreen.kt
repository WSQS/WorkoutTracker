package dev.sopho.workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sopho.workout.data.DayLog
import dev.sopho.workout.data.Exercise
import dev.sopho.workout.data.ExerciseLog
import dev.sopho.workout.data.bulkCount
import dev.sopho.workout.data.dayBulkCount
import dev.sopho.workout.data.dayDuration
import dev.sopho.workout.data.dayRatio
import dev.sopho.workout.data.dayReps
import dev.sopho.workout.data.dayWindow
import dev.sopho.workout.data.doneSets
import dev.sopho.workout.data.isComplete
import dev.sopho.workout.data.logDuration
import dev.sopho.workout.data.logWindow
import dev.sopho.workout.data.planReps

/**
 * The daily screen: one card per exercise, each group is a tap target, plus two
 * shortcuts for "one more group" and "undo one group".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(
    plan: List<Exercise>,
    done: DayLog,
    dateIso: String,
    streak: Int,
    onTapSet: (exerciseId: String, setIndex: Int) -> Unit,
    onAddSets: (exerciseId: String, delta: Int) -> Unit,
    onCompleteAll: (exerciseId: String) -> Unit,
) {
    val ratio = dayRatio(done, plan)
    val finished = isComplete(done, plan)
    val window = dayWindow(done, plan)
    val duration = dayDuration(done, plan)
    val bulk = dayBulkCount(done, plan)
    var timesFor by remember { mutableStateOf<Exercise?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SummaryCard(
            dateIso = dateIso,
            ratio = ratio,
            reps = dayReps(done, plan),
            target = planReps(plan),
            finished = finished,
            streak = streak,
            window = window,
            duration = duration,
            bulk = bulk,
        )

        plan.forEach { exercise ->
            ExerciseCard(
                exercise = exercise,
                log = done[exercise.id].orEmpty(),
                onTapSet = { index -> onTapSet(exercise.id, index) },
                onAddSets = { delta -> onAddSets(exercise.id, delta) },
                onCompleteAll = { onCompleteAll(exercise.id) },
                onShowTimes = { timesFor = exercise },
            )
        }

        Text(
            text = "点一下圆圈就是完成一组，点已完成的组可以撤销。数据只保存在这台手机上。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
    }

    timesFor?.let { exercise ->
        TimesDialog(
            exercise = exercise,
            log = done[exercise.id].orEmpty(),
            onDismiss = { timesFor = null },
        )
    }
}

@Composable
private fun SummaryCard(
    dateIso: String,
    ratio: Float,
    reps: Int,
    target: Int,
    finished: Boolean,
    streak: Int,
    window: Pair<Long, Long>?,
    duration: Long?,
    bulk: Int,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = dateLabel(dateIso),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (finished) "今日计划已完成 🎉" else "今日计划进行中",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = if (streak > 0) "🔥 连续达标 $streak 天" else "🔥 今天达标即可起头",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }

            Text(
                text = percent(ratio),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )

            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier.fillMaxWidth().height(10.dp),
                strokeCap = StrokeCap.Round,
            )

            Text(
                text = "已完成 $reps / $target 个",
                style = MaterialTheme.typography.bodyMedium,
            )

            if (window != null) {
                Text(
                    text = timingLine(window = window, duration = duration, bulk = bulk),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseCard(
    exercise: Exercise,
    log: ExerciseLog,
    onTapSet: (Int) -> Unit,
    onAddSets: (Int) -> Unit,
    onCompleteAll: () -> Unit,
    onShowTimes: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val sets = log.size.coerceAtMost(exercise.sets)
    val reps = sets * exercise.repsPerSet
    val allDone = sets >= exercise.sets
    val window = log.logWindow()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = exercise.emoji, fontSize = 26.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "每组 ${exercise.repsPerSet} 个 · 共 ${exercise.sets} 组 · 目标 ${exercise.totalReps} 个",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = if (allDone) "✓ 完成" else "$sets/${exercise.sets}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (allDone) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            LinearProgressIndicator(
                progress = { if (exercise.sets == 0) 0f else sets.toFloat() / exercise.sets },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                strokeCap = StrokeCap.Round,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "已完成 $sets / ${exercise.sets} 组（$reps / ${exercise.totalReps} 个）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (log.isNotEmpty()) {
                    TextButton(
                        onClick = onShowTimes,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("每组时间", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (window != null) {
                Text(
                    text = timingLine(window = window, duration = log.logDuration(), bulk = log.bulkCount()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(exercise.sets) { index ->
                    SetChip(
                        number = index + 1,
                        done = index < sets,
                        isNext = index == sets,
                        timed = index < sets && log[index].at != null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTapSet(index)
                        },
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAddSets(-1)
                    },
                    enabled = sets > 0,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) { Text("撤销一组") }

                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCompleteAll()
                    },
                    enabled = !allDone,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) { Text("全部完成") }

                Spacer(Modifier.weight(1f))

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAddSets(+1)
                    },
                    enabled = !allDone,
                ) { Text("+1 组") }
            }
        }
    }
}

/** "训练时段 08:10 – 08:34 · 用时 24 分钟 · 含 2 组补记" */
private fun timingLine(window: Pair<Long, Long>, duration: Long?, bulk: Int): String =
    buildString {
        append("训练时段 ${timeRangeLabel(window.first, window.second)}")
        if (duration != null) append(" · 用时 ${durationLabel(duration)}")
        if (bulk > 0) append(" · 含 $bulk 组补记")
    }

@Composable
private fun SetChip(
    number: Int,
    done: Boolean,
    isNext: Boolean,
    timed: Boolean,
    onClick: () -> Unit,
) {
    val background = when {
        !done -> if (isNext) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        timed -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val foreground = when {
        !done -> if (isNext) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        timed -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(background)
            .border(
                width = if (isNext) 2.dp else 0.dp,
                color = if (isNext) MaterialTheme.colorScheme.primary else background,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (done) "✓" else "$number",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = foreground,
            textAlign = TextAlign.Center,
        )
    }
}

/** every group of one exercise with the time it was ticked, or "补记" when there is none */
@Composable
private fun TimesDialog(
    exercise: Exercise,
    log: ExerciseLog,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${exercise.name} · 每组时间") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (log.isEmpty()) {
                    Text(
                        text = "这一项还没完成任何一组。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                log.forEachIndexed { index, mark ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "第 ${index + 1} 组",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = mark.at?.let { timeLabel(it) } ?: "补记（无时间）",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (mark.at == null) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }

                if (log.any { it.at == null }) {
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    Text(
                        text = "「补记」= 用「全部完成」或跳过中间的圆圈勾上的组：这些组确实做了，但没有记录到时间，所以不能给它编一个。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}
