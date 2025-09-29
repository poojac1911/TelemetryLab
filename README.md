Telemetry Lab
================================

What this project is
A Compose single-screen app that simulates an "edge inference" pipeline:
- Start/Stop toggle
- Slider controls compute load (1..5 passes of a 3×3 convolution on a 256×256 float array)
- Produces frames at 20Hz (drops to 10Hz when device is in Power Save)
- Background execution uses an Android Foreground Service (Android 14+ FGS type "dataSync")
- UI shows last-frame latency, moving average, and a tiny live scrolling list; shows Jank% over the last 30s using JankStats.

Why Foreground Service (vs WorkManager)
- This is a user-initiated, interactive, continuous task that must run immediately and continuously while the user expects it. A foreground service is the correct primitive for continuous, visible tasks.
- WorkManager is great for deferrable or scheduled work and can run in foreground mode, but it relies on JobScheduler and may not provide the same immediate, continuous behaviour or predictable frame cadence. For a 20Hz real-time loop the direct FGS approach is simpler and more appropriate. (If you implement a non-interactive batch variant, a CoroutineWorker with `setForegroundAsync()` is an alternative.)

Threading & backpressure approach
- All heavy compute is performed on `Dispatchers.Default` coroutines.
- The FGS executes a fixed-rate loop. For each frame the service `await`s compute completion (no compute on main thread) and then sleeps the remainder of the frame window.
- If compute exceeds the frame interval, we do not start extra parallel compute tasks — we yield and start the next frame immediately (frames are not queued). This prevents unbounded buildup of pending compute tasks.
- UI updates are sent as summaries (mean/std/lastLatency) via broadcasts. The ViewModel collects these into a `SharedFlow` and updates Compose with throttling; we avoid spamming recompositions and use stable keys / derivedStateOf where needed.

Power & adaptivity
- The service detects `PowerManager.isPowerSaveMode` and reduces frame rate from 20→10Hz and lowers compute load by 1 (minimum 1). UI shows a small "Power-save mode" banner.

JankStats & measuring smoothness
- `JankStats` is created in the Activity; it listens to frames and we compute a 30s sliding-window jank percentage. The UI displays Jank% (last 30s).
- Capture logs or screenshot of JankStats output for load=2 for 30s and include it with submission (example command: use `adb logcat` while running or capture a screenshot).

How to run
1. Build and install on an Android device (physical device recommended).
2. Start the app, press Start. Notification indicates service is running.
3. Observe the UI dashboard and Jank% while changing "Compute Load".
4. To reproduce: set load=2, run for 30 seconds and capture Jank%-value shown in the app.
