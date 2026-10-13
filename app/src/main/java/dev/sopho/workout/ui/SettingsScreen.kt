package dev.sopho.workout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sopho.workout.data.Exercise
import dev.sopho.workout.data.MAX_REPS
import dev.sopho.workout.data.MAX_SETS
import dev.sopho.workout.data.MIN_REPS
import dev.sopho.workout.data.MIN_SETS
import dev.sopho.workout.data.planReps

private enum class ConfirmAction(
    val title: String,
    val message: String,
    val confirmLabel: String,
) {
    ResetToday(
        title = "重置今天的数据",
        message = "今天已完成的组数会清零，之前的记录不受影响。",
        confirmLabel = "重置今天",
    ),
    ClearAll(
        title = "清空全部记录",
        message = "所有日期的记录都会删除，且无法恢复。计划设置会保留。",
        confirmLabel = "全部清空",
    ),
    RestorePlan(
        title = "恢复默认计划",
        message = "计划将恢复为 俯卧撑 10×8、深蹲 10×12、仰卧起坐 20×5。历史记录不受影响。",
        confirmLabel = "恢复默认",
    ),
}

@Composable
fun SettingsScreen(
    plan: List<Exercise>,
    reminder: ReminderUiState,
    onReps: (id: String, reps: Int) -> Unit,
    onSets: (id: String, sets: Int) -> Unit,
    onResetToday: () -> Unit,
    onClearAll: () -> Unit,
    onRestorePlan: () -> Unit,
    onReminderEnabled: (Boolean) -> Unit,
    onIntervalMinutes: (Int) -> Unit,
    onWindow: (startMinuteOfDay: Int, endMinuteOfDay: Int) -> Unit,
) {
    var confirm by remember { mutableStateOf<ConfirmAction?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "每日计划",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "调整组数和每组个数，改动立刻生效；每日目标合计 ${planReps(plan)} 个。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                plan.forEach { exercise ->
                    HorizontalDivider()
                    ExerciseEditor(
                        exercise = exercise,
                        onReps = { reps -> onReps(exercise.id, reps) },
                        onSets = { sets -> onSets(exercise.id, sets) },
                    )
                }
            }
        }

        ReminderSettingsCard(
            reminder = reminder,
            onEnabled = onReminderEnabled,
            onIntervalMinutes = onIntervalMinutes,
            onWindow = onWindow,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "数据",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { confirm = ConfirmAction.ResetToday },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { Text("重置今天") }
                    OutlinedButton(
                        onClick = { confirm = ConfirmAction.ClearAll },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { Text("清空记录") }
                }
                OutlinedButton(
                    onClick = { confirm = ConfirmAction.RestorePlan },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("恢复默认计划") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "关于",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "每日运动 v1.0.0",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "记录只保存在本机应用私有目录，不联网、不申请任何权限；卸载应用会一并删除数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
            }
        }

        Spacer(Modifier.height(8.dp))
    }

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(action.title) },
            text = { Text(action.message) },
            confirmButton = {
                TextButton(onClick = {
                    when (action) {
                        ConfirmAction.ResetToday -> onResetToday()
                        ConfirmAction.ClearAll -> onClearAll()
                        ConfirmAction.RestorePlan -> onRestorePlan()
                    }
                    confirm = null
                }) { Text(action.confirmLabel) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ExerciseEditor(
    exercise: Exercise,
    onReps: (Int) -> Unit,
    onSets: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = exercise.emoji, fontSize = 22.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "合计 ${exercise.totalReps} 个/天",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(
                label = "每组个数",
                value = exercise.repsPerSet,
                range = MIN_REPS..MAX_REPS,
                onChange = onReps,
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = "组数",
                value = exercise.sets,
                range = MIN_SETS..MAX_SETS,
                onChange = onSets,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun NumberField(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilledTonalIconButton(
                onClick = { onChange((value - 1).coerceIn(range)) },
                enabled = value > range.first,
                modifier = Modifier.width(40.dp),
            ) { Text("−", style = MaterialTheme.typography.titleMedium) }

            OutlinedTextField(
                value = value.toString(),
                onValueChange = { text ->
                    text.filter { it.isDigit() }.take(3).toIntOrNull()?.let { onChange(it.coerceIn(range)) }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier.weight(1f),
            )

            FilledTonalIconButton(
                onClick = { onChange((value + 1).coerceIn(range)) },
                enabled = value < range.last,
                modifier = Modifier.width(40.dp),
            ) { Text("+", style = MaterialTheme.typography.titleMedium) }
        }
    }
}
