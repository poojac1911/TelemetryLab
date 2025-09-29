package com.example.telemetrylab.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.example.telemetrylab.ForegroundComputeService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class FrameData(val index: Int, val latency: Long, val isJank: Boolean)

class TelemetryLabViewModel(app: Application) : AndroidViewModel(app) {

    @SuppressLint("StaticFieldLeak")
    private val context = app.applicationContext


    private val _latencyMs = MutableStateFlow(0L)
    val latencyMs: StateFlow<Long> = _latencyMs

    private val _jankPct = MutableStateFlow(0.0)
    val jankPct: StateFlow<Double> = _jankPct

    private val _isPowerSave = MutableStateFlow(false)
    val isPowerSave: StateFlow<Boolean> = _isPowerSave

    private val _frameHistory = MutableStateFlow<List<FrameData>>(emptyList())
    val frameHistory: List<FrameData> get() = _frameHistory.value

    private val frameTimestamps = ArrayDeque<Pair<Long, Boolean>>()
    private var frameCounter = 0

    private var _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    init {
        val context = getApplication<Application>().applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent ?: return
                when (intent.action) {
                    ForegroundComputeService.ACTION_JANK_UPDATE -> {
                        val latency = intent.getLongExtra("latencyMs", 0L)
                        val isJank = intent.getBooleanExtra("isJank", false)
                        val ts = System.currentTimeMillis()
                        frameTimestamps.addLast(ts to isJank)
                        frameCounter++

                        // keep 30s window
                        while (frameTimestamps.isNotEmpty() && ts - frameTimestamps.first().first > 30_000) {
                            frameTimestamps.removeFirst()
                        }

                        val total = frameTimestamps.size
                        val jankCount = frameTimestamps.count { it.second }
                        val pct = if (total == 0) 0.0 else (100.0 * jankCount / total)
                        _jankPct.value = pct
                        _latencyMs.value = latency

                        // update frame history (keep last 50)
                        val history = _frameHistory.value.toMutableList()
                        history.add(FrameData(frameCounter, latency, isJank))
                        if (history.size > 50) history.removeAt(0)
                        _frameHistory.value = history
                    }
                    ForegroundComputeService.ACTION_POWER_SAVE -> {
                        _isPowerSave.value = intent.getBooleanExtra("isPowerSave", false)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ForegroundComputeService.ACTION_JANK_UPDATE)
            addAction(ForegroundComputeService.ACTION_POWER_SAVE)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    fun start() {
        val intent = Intent(context, ForegroundComputeService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        _isRunning.value = true
    }

    fun stop() {
        val intent = Intent(context, ForegroundComputeService::class.java).apply {
            putExtra("action", "STOP")
        }
        context.startService(intent)
        _isRunning.value = false
    }

    fun updateComputeLoad(load: Float) {
        val intent = Intent(context, ForegroundComputeService::class.java).apply {
            putExtra("computeLoad", load)
        }
        context.startService(intent)
    }
}
