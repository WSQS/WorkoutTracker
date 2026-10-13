package dev.sopho.workout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import dev.sopho.workout.data.WorkoutStore
import dev.sopho.workout.ui.WorkoutApp
import dev.sopho.workout.ui.WorkoutTheme
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    /**
     * Created once per process and rebuilt from SharedPreferences after a rotation, so
     * nothing is lost when the screen turns. [today] is kept in a snapshot state and
     * refreshed in onResume, so an app left open over midnight rolls over to the new day.
     */
    private val store by lazy { WorkoutStore(applicationContext) }
    private val today = mutableStateOf(LocalDate.now().toString())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WorkoutTheme {
                WorkoutApp(store = store, today = today.value)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        today.value = LocalDate.now().toString()
    }
}
