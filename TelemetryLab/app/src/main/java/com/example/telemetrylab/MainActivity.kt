package com.example.telemetrylab

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.metrics.performance.JankStats
import com.example.telemetrylab.ui.theme.TelemetryLabTheme
import android.util.Log
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.telemetrylab.ui.presentation.TelemetryScreen
import com.example.telemetrylab.viewmodel.TelemetryLabViewModel

class MainActivity : ComponentActivity() {

    private var jankStats: JankStats? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val vm: TelemetryLabViewModel = viewModel()
            TelemetryScreen(vm)
        }

        // Attach JankStats to this activity's window
        jankStats = JankStats.createAndTrack(window) { frameData ->
            val ts = SystemClock.elapsedRealtime()
            val isJank = frameData.isJank

            frameTimestamps.addLast(ts to isJank)

            // Keep only the last 30s of frame data
            while (frameTimestamps.isNotEmpty() &&
                ts - frameTimestamps.first().first > WINDOW_MS
            ) {
                frameTimestamps.removeFirst()
            }

            val total = frameTimestamps.size
            val jankCount = frameTimestamps.count { it.second }
            val pct = if (total == 0) 0.0 else (100.0 * jankCount / total)

            // Send broadcast → ForegroundService / ViewModel bridge
            sendBroadcast(
                Intent(ForegroundComputeService.ACTION_JANK_UPDATE)
                    .putExtra("jankPct", pct)
            )


            Log.v("JankStatsSample", frameData.toString())
        }
    }

    override fun onResume() {
        super.onResume()
        jankStats?.isTrackingEnabled = true
    }

    override fun onPause() {
        super.onPause()
        jankStats?.isTrackingEnabled = false
    }


    override fun onDestroy() {
        super.onDestroy()
        jankStats?.isTrackingEnabled = false
    }

    companion object {
        private const val WINDOW_MS = 30_000L
        private val frameTimestamps = ArrayDeque<Pair<Long, Boolean>>()
    }
}


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    TelemetryLabTheme {
        Greeting("Android")
    }
}