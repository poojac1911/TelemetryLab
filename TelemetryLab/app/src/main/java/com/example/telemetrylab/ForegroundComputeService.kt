package com.example.telemetrylab

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ForegroundComputeService : Service() {

    companion object {
        const val ACTION_JANK_UPDATE = "action.JANK_UPDATE"
        const val ACTION_POWER_SAVE = "action.POWER_SAVE"
        const val NOTIF_CHANNEL = "telemetry_lab_channel"
        const val NOTIF_ID = 1001
    }

    private var isRunningLoop = false
    private lateinit var powerManager: PowerManager
    private var isPowerSaveMode = false

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val powerSaveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            isPowerSaveMode = powerManager.isPowerSaveMode
            sendPowerSaveBroadcast()
        }
    }

    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        isPowerSaveMode = powerManager.isPowerSaveMode

        registerReceiver(
            powerSaveReceiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        )
        sendPowerSaveBroadcast()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        //startComputeLoop()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL,
                "Telemetry Lab",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("Telemetry Lab Running")
            .setContentText("Foreground compute active")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()

    private fun sendPowerSaveBroadcast() {
        sendBroadcast(
            Intent(ACTION_POWER_SAVE).putExtra("isPowerSave", isPowerSaveMode)
        )
    }

    private fun startComputeLoop() {
        serviceScope.launch {
            var frameRateHz = 20
            var computeLoad = 2
            val arraySize = 256
            val kernel = arrayOf(
                floatArrayOf(0f, 1f, 0f),
                floatArrayOf(1f, -4f, 1f),
                floatArrayOf(0f, 1f, 0f)
            )

            while (isActive) {
                val startTime = System.currentTimeMillis()

                if (isPowerSaveMode) {
                    frameRateHz = 10
                    computeLoad = (computeLoad - 1).coerceAtLeast(1)
                } else {
                    frameRateHz = 20
                    computeLoad = 2
                }

                runConvolution(arraySize, kernel, computeLoad)
                val latency = System.currentTimeMillis() - startTime
                val isJank = latency > 16 // >16ms considered jank

                sendBroadcast(
                    Intent(ACTION_JANK_UPDATE)
                        .putExtra("latencyMs", latency)
                        .putExtra("isJank", isJank)
                )

                delay(1000L / frameRateHz)
            }
        }
    }


    /*private fun startComputeLoop() {
        serviceScope.launch {
            var frameRateHz = 20
            var computeLoad = 2

            val arraySize = 256
            val kernel = arrayOf(
                floatArrayOf(0.0f, 1f, 0.0f),
                floatArrayOf(1f, -4f, 1f),
                floatArrayOf(0.0f, 1f, 0.0f)
            )

            while (isActive) {
                val startTime = SystemClock.elapsedRealtime()

                // adapt to power-save
                if (isPowerSaveMode) {
                    frameRateHz = 10
                    computeLoad = (computeLoad - 1).coerceAtLeast(1)
                } else {
                    frameRateHz = 20
                    computeLoad = 2
                }

                // CPU-bound convolution task
                runConvolution(arraySize, kernel, computeLoad)

                val latency = SystemClock.elapsedRealtime() - startTime
                sendBroadcast(Intent(ACTION_JANK_UPDATE).putExtra("latencyMs", latency))

                delay(1000L / frameRateHz)
            }
        }
    }*/

    private fun runConvolution(size: Int, kernel: Array<FloatArray>, repeats: Int) {
        val input = Array(size) { FloatArray(size) { Math.random().toFloat() } }
        val output = Array(size) { FloatArray(size) }

        repeat(repeats) {
            for (i in 1 until size - 1) {
                for (j in 1 until size - 1) {
                    var sum = 0f
                    for (ki in kernel.indices) {
                        for (kj in kernel[0].indices) {
                            sum += input[i + ki - 1][j + kj - 1] * kernel[ki][kj]
                        }
                    }
                    output[i][j] = sum
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val action = it.getStringExtra("action")
            if (action == "STOP") {
                stopLoop()
                stopSelf() // stop the service
                return START_NOT_STICKY
            }
        }

        if (!isRunningLoop) {
            isRunningLoop = true
            startComputeLoop()
        }

        return START_STICKY
    }

    private fun stopLoop() {
        isRunningLoop = false
        serviceScope.coroutineContext.cancelChildren()
        stopForeground(true)
    }


    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(powerSaveReceiver)
        serviceScope.cancel()
    }
}
