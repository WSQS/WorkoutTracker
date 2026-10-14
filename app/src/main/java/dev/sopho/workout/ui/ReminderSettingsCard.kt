package dev.sopho.workout.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.sopho.workout.data.ReminderMode
import dev.sopho.workout.data.ReminderSettings

/**
 * 设置页里的「提醒」卡。权限不是装饰：通知权限没有时，提醒**一条都发不出去**；
 * 精确闹钟权限没有时会退回近似模式（实测投递窗口 20 分钟到 1 小时）—— 两种状态都必须如实显示出来。
 */
@Composable
fun ReminderSettingsCard(
    reminder: ReminderUiState,
    onEnabled: (Boolean) -> Unit,
    onIntervalMinutes: (Int) -> Unit,
    onWindow: (startMinuteOfDay: Int, endMinuteOfDay: Int) -> Unit,
) {
    val context = LocalContext.current
    val settings = reminder.settings

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 结果由 areNotificationsEnabled() 反映，界面会自动刷新 */ }

    // Android 13 起才有通知运行时权限；更早的版本只能跳到应用通知设置页
    val askNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "提醒",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "勾完一组、过一段时间提醒你做下一组；活动时间开始时如果今天还没开始，提醒一次。" +
                            "只在活动时间内提醒。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { enabled ->
                        onEnabled(enabled)
                        if (enabled && !reminder.notificationsAllowed) askNotifications()
                    },
                )
            }

            if (settings.enabled) {
                HorizontalDivider()

                StatusRow(
                    label = "通知权限",
                    ok = reminder.notificationsAllowed,
                    okText = "已开启",
                    badText = "未开启 —— 没有它一条通知都发不出去",
                    actionText = "去开启",
                    onAction = askNotifications,
                )

                StatusRow(
                    label = "精确闹钟",
                    ok = reminder.mode == ReminderMode.Exact,
                    okText = "已授权 —— 提醒按分钟准点",
                    badText = "未授权 —— 退回近似模式：实测系统给的投递窗口约 22 分钟到 1 小时，提醒可能晚这么久",
                    actionText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "去授权" else null,
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM),
                            )
                        }
                    },
                )

                NumberField(
                    label = "间隔（分钟）",
                    value = settings.intervalMinutes,
                    range = ReminderSettings.MIN_INTERVAL_MINUTES..ReminderSettings.MAX_INTERVAL_MINUTES,
                    onChange = onIntervalMinutes,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeField(
                        label = "活动时间 · 开始",
                        minuteOfDay = settings.window.startMinuteOfDay,
                        onChange = { onWindow(it, settings.window.endMinuteOfDay) },
                        modifier = Modifier.weight(1f),
                    )
                    TimeField(
                        label = "活动时间 · 结束",
                        minuteOfDay = settings.window.endMinuteOfDay,
                        onChange = { onWindow(settings.window.startMinuteOfDay, it) },
                        modifier = Modifier.weight(1f),
                    )
                }

                Text(
                    text = "窗口之外一律不提醒：在窗口外勾选不会排提醒，到期时间落在窗口外也不会排。" +
                        "手机在提醒前重启会丢掉已排的提醒，下次打开应用时会在今天页说明。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    ok: Boolean,
    okText: String,
    badText: String,
    actionText: String?,
    onAction: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            Text(
                text = if (ok) okText else badText,
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        if (!ok && actionText != null) {
            TextButton(onClick = onAction) { Text(actionText) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(
    label: String,
    minuteOfDay: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(clockText(minuteOfDay), style = MaterialTheme.typography.titleMedium)
        }
    }

    if (showPicker) {
        val state = rememberTimePickerState(
            initialHour = minuteOfDay / 60,
            initialMinute = minuteOfDay % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(state.hour * 60 + state.minute)
                    showPicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            },
            text = { TimePicker(state = state) },
        )
    }
}
