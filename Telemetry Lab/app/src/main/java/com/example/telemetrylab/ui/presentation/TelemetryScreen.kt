package com.example.telemetrylab.ui.presentation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.telemetrylab.viewmodel.TelemetryLabViewModel


@Composable
fun TelemetryScreen(vm: TelemetryLabViewModel) {
    val latencyMs by vm.latencyMs.collectAsState()
    val jankPct by vm.jankPct.collectAsState()
    val isPowerSave by vm.isPowerSave.collectAsState()
    val isRunning by vm.isRunning.collectAsState()
    val frameHistory = vm.frameHistory
    var computeLoad by remember { mutableStateOf(2f) }


    val context = LocalContext.current

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Telemetry Lab", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Text("Jank% (30s): ${"%.1f".format(jankPct)}")
        }

        Spacer(Modifier.height(8.dp))
        if (isPowerSave) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Yellow)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Power-save mode active", color = Color.Black)
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Compute Load")
            Slider(
                enabled = isRunning,
                value = computeLoad.toFloat(),
                onValueChange = { computeLoad = it
                    vm.updateComputeLoad(computeLoad)
                                },
                valueRange = 1f..5f,
                steps = 3,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text(computeLoad.toString())
        }
        // Dashboard with real-time metrics
        Text("Current frame latency: ${latencyMs} ms")
        Text("Jank % (last 30s): ${"%.2f".format(jankPct)}%")
        val infiniteTransition = rememberInfiniteTransition(label = "indicator")
        val anim by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = if (isRunning) 1f else 0f,
            animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Restart),
            label = "indicator_anim"
        )
        Text("UI load indicator: ${"%.2f".format(anim)}")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                modifier = Modifier.fillMaxWidth(0.5f),
                onClick = { if (!isRunning) vm.start() else vm.stop() }
            ) {
                Text(if (!isRunning) "Start" else "Stop")
                Spacer(Modifier.width(12.dp))
                Switch(
                    modifier = Modifier.height(20.dp),
                    checked = !isRunning,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                        uncheckedThumbColor = MaterialTheme.colorScheme.secondary,
                        uncheckedTrackColor = MaterialTheme.colorScheme.secondaryContainer,
                    )
                )
            }
            Spacer(Modifier.width(12.dp))

        }

        Spacer(Modifier.height(12.dp))
        if (isPowerSave) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Yellow)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Power-save mode active",
                    color = Color.Black,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(items = frameHistory.orEmpty()) { logEntry ->
                Text(
                    "Frame ${logEntry.index}: ${logEntry.latency} ms, ${if (logEntry.isJank) "JANK" else "OK"}",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
