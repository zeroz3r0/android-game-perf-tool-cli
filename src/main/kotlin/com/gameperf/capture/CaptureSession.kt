package com.gameperf.capture

import com.gameperf.config.AppConfig
import com.gameperf.core.*
import com.gameperf.i18n.Strings
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages the real-time capture loop: connects to device, samples metrics
 * every second, detects resolution changes and FPS jumps, handles
 * graceful shutdown via ENTER or Ctrl+C.
 */
class CaptureSession(
    private val connector: AdbConnector,
    private val config: AppConfig
) {
    private val running = AtomicBoolean(false)

    fun capture(
        device: AndroidDevice,
        gamePackage: String,
        specs: DeviceSpecs,
        batteryStart: BatteryInfo?,
        isWifi: Boolean
    ): SessionData {
        val memStart = connector.getMemoryInfo(device.id, gamePackage)
        val missedStart = connector.getMissedFrames(device.id)
        val initialRes = connector.getRenderResolution(device.id, gamePackage)

        // Disable USB charging for real battery measurement
        val needBatteryUnplug = !isWifi
        if (needBatteryUnplug) {
            connector.disableCharging(device.id)
            println("  ${Strings.CHARGING_DISABLED}")
        } else {
            println("  ${Strings.WIFI_MODE_BATTERY}")
        }

        println()
        println(Strings.captureHeader(gamePackage, initialRes?.toString()))
        println()

        val samples = mutableListOf<SessionSample>()
        val events = mutableListOf<LogEntry>()
        val configChanges = mutableListOf<GraphicsConfigChange>()
        var lastResolution = initialRes
        val startTime = System.currentTimeMillis()
        running.set(true)
        var prevCpuTimes = connector.getCpuTimes(device.id)
        var consecutiveFailures = 0

        val shutdownHook = Thread {
            running.set(false)
            if (needBatteryUnplug) connector.restoreCharging(device.id)
        }
        Runtime.getRuntime().addShutdownHook(shutdownHook)

        try {
            while (running.get() && !durationExceeded(startTime, config.maxDuration)) {
                Thread.sleep(1000)
                val now = System.currentTimeMillis()
                val elapsed = ((now - startTime) / 1000).toInt()

                // Collect all metrics
                val frameData = connector.captureFrameData(device.id, gamePackage)
                val fps = frameData?.fps ?: 0
                val mem = connector.getMemoryInfo(device.id, gamePackage)
                val currCpuTimes = connector.getCpuTimes(device.id)
                val cpuSnap = connector.calculateCpuUsage(prevCpuTimes, currCpuTimes)
                prevCpuTimes = currCpuTimes
                val gpuSnap = connector.getGpuUsage(device.id)
                val thermal = connector.getThermalInfo(device.id)

                // Detect device disconnect
                val allFailed = frameData == null && mem == null && cpuSnap == null
                if (allFailed) {
                    consecutiveFailures++
                    if (consecutiveFailures >= 5) {
                        println("  ${Strings.ERROR_DEVICE_DISCONNECTED}")
                        running.set(false)
                        continue
                    } else if (consecutiveFailures >= 3) {
                        println("  ${Strings.WARN_DEVICE_NO_RESPONSE} ${Strings.attempts(consecutiveFailures)}")
                    }
                } else {
                    consecutiveFailures = 0
                }

                // Waiting for render
                if (fps == 0 && connector.findGameLayer(device.id, gamePackage) == null && samples.none { it.fps > 0 }) {
                    println("  ${Strings.WAITING_RENDER}")
                }

                // Resolution changes
                val currentRes = connector.getRenderResolution(device.id, gamePackage)
                if (currentRes != null && lastResolution != null && currentRes != lastResolution) {
                    val lastFps = if (samples.size >= 3)
                        samples.takeLast(3).filter { it.fps > 0 }.map { it.fps }.average().toInt() else fps
                    configChanges.add(GraphicsConfigChange(now, lastResolution, currentRes, lastFps, fps))
                    println(Strings.resolutionChange(lastResolution.toString(), currentRes.toString()))
                }
                if (currentRes != null) lastResolution = currentRes

                // FPS jump detection
                if (fps > 0 && samples.size >= 4) {
                    val prevAvg = samples.takeLast(3).filter { it.fps > 0 }.map { it.fps }.average()
                    if (fps - prevAvg > 8 && configChanges.none { kotlin.math.abs(it.timestamp - now) < 3000 }) {
                        configChanges.add(GraphicsConfigChange(now, lastResolution, lastResolution, prevAvg.toInt(), fps))
                        println(Strings.fpsJump(prevAvg.toInt(), fps))
                    }
                }

                // Collect events
                val logs = connector.getGameLogs(device.id, gamePackage, 500)
                val newEvents = logs.filter { nuevo ->
                    events.none { it.timestamp == nuevo.timestamp && it.message == nuevo.message }
                }
                if (newEvents.isNotEmpty()) events.addAll(newEvents)

                // Store sample
                samples.add(SessionSample(
                    timestamp = now, fps = fps,
                    frameTimes = frameData?.frameTimes ?: emptyList(),
                    memoryInfo = mem, cpuSnapshot = cpuSnap,
                    gpuSnapshot = gpuSnap, thermalSnapshot = thermal,
                    renderResolution = currentRes
                ))

                // Status line
                val cpuStr = cpuSnap?.let { "${it.totalUsage.toInt()}%" } ?: "--"
                val gpuStr = if (gpuSnap.isAvailable) "${gpuSnap.usage.toInt()}%" else "--"
                val memStr = mem?.let { "${it.totalMb}MB" } ?: "--"
                val tempStr = if (thermal.hasCpuTemp) "${thermal.cpuTemp.toInt()}C" else "--"
                val avgFps = samples.filter { it.fps > 0 }.map { it.fps }.average()
                    .let { if (it.isNaN()) 0.0 else it }.toInt()

                println(Strings.sampleLine(elapsed, fps, avgFps, cpuStr, gpuStr, memStr, tempStr, events.size))

                // Progress bar for timed sessions
                if (config.maxDuration > 0) {
                    println(Strings.progressBar(elapsed, config.maxDuration))
                }

                // Check ENTER to stop
                checkForStop()
            }
        } catch (e: InterruptedException) {
            println("\n  ${Strings.TEST_STOPPED_CTRL_C}")
        }

        try { Runtime.getRuntime().removeShutdownHook(shutdownHook) } catch (_: Exception) {}

        val endTime = System.currentTimeMillis()
        val batteryEnd = connector.getBatteryLevel(device.id)

        if (needBatteryUnplug) {
            connector.restoreCharging(device.id)
            println("  ${Strings.CHARGING_RESTORED}")
        }

        val memEnd = connector.getMemoryInfo(device.id, gamePackage)
        val missedEnd = connector.getMissedFrames(device.id)

        return SessionData(
            gamePackage = gamePackage, deviceSpecs = specs, samples = samples,
            events = events, configChanges = configChanges,
            batteryStart = batteryStart, batteryEnd = batteryEnd,
            memStart = memStart, memEnd = memEnd,
            missedFramesStart = missedStart, missedFramesEnd = missedEnd,
            startTime = startTime, endTime = endTime, initialResolution = initialRes
        )
    }

    private fun durationExceeded(startTime: Long, maxDuration: Int): Boolean {
        if (maxDuration <= 0) return false
        return ((System.currentTimeMillis() - startTime) / 1000).toInt() >= maxDuration
    }

    private fun checkForStop() {
        try {
            if (System.`in`.available() > 0) {
                val b = System.`in`.read()
                if (b == 10 || b == 13) {
                    println("\n  ${Strings.TEST_STOPPED}")
                    running.set(false)
                }
            }
        } catch (_: Exception) {}
    }
}
