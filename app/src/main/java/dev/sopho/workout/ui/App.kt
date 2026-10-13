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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import dev.sopho.workout.data.WorkoutStore
import dev.sopho.workout.data.currentStreak
import java.time.LocalDate

private enum class Tab(val label: String, val title: String, val icon: ImageVector) {
    Today("今天", "每日运动", Icons.Filled.Check),
    History("记录", "运动记录", Icons.Filled.DateRange),
    Settings("设置", "计划与设置", Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutApp(store: WorkoutStore, today: String) {
    val plan by store.plan.collectAsState()
    val records by store.records.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val current = Tab.entries[tab]
    val todayDate = LocalDate.parse(today)

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
                    onReps = { id, reps -> store.updateExercise(id, repsPerSet = reps) },
                    onSets = { id, sets -> store.updateExercise(id, sets = sets) },
                    onResetToday = { store.resetDay(today) },
                    onClearAll = { store.clearAllRecords() },
                    onRestorePlan = { store.restoreDefaultPlan() },
                )
            }
        }
    }
}
