package com.gameperf.core

import java.util.Calendar
import java.util.concurrent.TimeUnit

class AdbConnector {

    // ===== ADB Execution with Timeout =====

    private fun execAdb(vararg args: String, timeoutMs: Long = 5000): String {
        return try {
            val pb = ProcessBuilder(*args)
            pb.redirectErrorStream(true)
            val process = pb.start()
            // Read output in separate thread to avoid pipe buffer deadlock
            val outputFuture = java.util.concurrent.CompletableFuture.supplyAsync {
                process.inputStream.bufferedReader().readText()
            }
            val completed = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!completed) {
                process.destroyForcibly()
                return try { outputFuture.get(500, TimeUnit.MILLISECONDS) } catch (e: Exception) { "" }
            }
            try { outputFuture.get(1000, TimeUnit.MILLISECONDS) } catch (e: Exception) { "" }
        } catch (e: Exception) { "" }
    }

    private fun execAdbShell(deviceId: String, shellCmd: String, timeoutMs: Long = 5000): String {
        return execAdb("adb", "-s", deviceId, "shell", shellCmd, timeoutMs = timeoutMs)
    }

    private fun getDeviceProperty(deviceId: String, property: String): String {
        return execAdbShell(deviceId, "getprop $property").trim()
    }

    // ===== Device Discovery =====

    fun isAdbAvailable(): Boolean {
        return execAdb("adb", "version").isNotEmpty()
    }

    fun listDevices(): List<AndroidDevice> {
        if (!isAdbAvailable()) return emptyList()
        val output = execAdb("adb", "devices", "-l")
        if (output.isBlank()) return emptyList()

        val devices = mutableListOf<AndroidDevice>()
        for (line in output.lines()) {
            if (line.contains("device") && !line.contains("List") && !line.startsWith("*")) {
                val parts = line.split("\\s+".toRegex())
                if (parts.size >= 2 && parts[1] == "device") {
                    val id = parts[0]
                    val model = Regex("model:(\\S+)").find(line)?.groupValues?.get(1) ?: "Unknown"
                    devices.add(AndroidDevice(
                        id = id, name = model, model = model,
                        manufacturer = "", sdkVersion = 0,
                        isEmulator = id.contains("emulator")
                    ))
                }
            }
        }
        return devices
    }

    fun getDeviceSpecs(deviceId: String): DeviceSpecs {
        val model = getDeviceProperty(deviceId, "ro.product.model")
        val manufacturer = getDeviceProperty(deviceId, "ro.product.manufacturer")
        val sdk = getDeviceProperty(deviceId, "ro.build.version.sdk").toIntOrNull() ?: 0
        val hardware = getDeviceProperty(deviceId, "ro.hardware")
        val platform = getDeviceProperty(deviceId, "ro.board.platform")
        val cpu = "$hardware $platform".trim()
        val ram = getRamBytes(deviceId)
        val resolution = execAdbShell(deviceId, "wm size").trim()
        val cores = getCpuCoreCount(deviceId)
        val gpuModel = getGpuModel(deviceId)

        return DeviceSpecs(
            model = model.ifEmpty { "Unknown" },
            manufacturer = manufacturer.ifEmpty { "Unknown" },
            sdkVersion = sdk, cpu = cpu.ifEmpty { "Unknown" },
            ram = ram, resolution = resolution.ifEmpty { "Unknown" },
            cores = cores, gpuModel = gpuModel
        )
    }

    private fun getRamBytes(deviceId: String): Long {
        val output = execAdbShell(deviceId, "cat /proc/meminfo")
        val match = Regex("MemTotal:\\s+(\\d+)").find(output)
        return (match?.groupValues?.get(1)?.toLongOrNull() ?: 0L) * 1024
    }

    private fun getCpuCoreCount(deviceId: String): Int {
        val output = execAdbShell(deviceId, "cat /proc/cpuinfo")
        val count = Regex("processor\\s*:\\s*(\\d+)").findAll(output).count()
        return if (count > 0) count else 4
    }

    private fun getGpuModel(deviceId: String): String {
        val sf = execAdbShell(deviceId, "dumpsys SurfaceFlinger", timeoutMs = 3000)
        val match = Regex("GLES:\\s*(.+)").find(sf)
        if (match != null) return match.groupValues[1].trim().take(80)
        // Fallback
        val gpu = execAdbShell(deviceId, "getprop ro.hardware.egl").trim()
        return gpu.ifEmpty { "Unknown" }
    }

    // ===== Battery =====

    fun getBatteryLevel(deviceId: String): BatteryInfo? {
        val output = execAdbShell(deviceId, "dumpsys battery")
        if (output.isBlank()) return null
        val level = Regex("level: (\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val temp = Regex("temperature: (\\d+)").find(output)?.groupValues?.get(1)?.toFloatOrNull()?.div(10f) ?: 0f
        val status = Regex("status: (\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val voltage = Regex("voltage: (\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return BatteryInfo(level = level, temperature = temp, isCharging = status == 2 || status == 5, voltage = voltage)
    }

    // ===== WiFi ADB =====

    /**
     * Switch a USB-connected device to WiFi ADB mode.
     * Returns the WiFi device ID (ip:port) or null if failed.
     * 
     * Flow:
     * 1. Get device IP from wlan0 interface
     * 2. Enable tcpip mode on port 5555
     * 3. Wait for tcpip to activate
     * 4. Connect to device via WiFi
     * 5. Verify connection works
     */
    fun switchToWifi(usbDeviceId: String, port: Int = 5555): String? {
        // Get device IP address
        val ipOutput = execAdbShell(usbDeviceId, "ip addr show wlan0")
        val ipMatch = Regex("inet (\\d+\\.\\d+\\.\\d+\\.\\d+)/").find(ipOutput)
        val deviceIp = ipMatch?.groupValues?.get(1)
        
        if (deviceIp == null || deviceIp == "0.0.0.0") {
            // Try alternative method
            val altOutput = execAdbShell(usbDeviceId, "ifconfig wlan0")
            val altMatch = Regex("inet addr:(\\d+\\.\\d+\\.\\d+\\.\\d+)").find(altOutput)
                ?: Regex("inet (\\d+\\.\\d+\\.\\d+\\.\\d+)").find(altOutput)
            val altIp = altMatch?.groupValues?.get(1)
            if (altIp == null || altIp == "0.0.0.0") return null
            return connectWifi(usbDeviceId, altIp, port)
        }
        
        return connectWifi(usbDeviceId, deviceIp, port)
    }
    
    private fun connectWifi(usbDeviceId: String, ip: String, port: Int): String? {
        // Enable tcpip mode
        val tcpResult = execAdb("adb", "-s", usbDeviceId, "tcpip", port.toString(), timeoutMs = 10000)
        if (tcpResult.isBlank() && !tcpResult.contains("restarting")) {
            // Some devices don't print output but it still works, continue anyway
        }
        
        // Wait for tcpip to activate
        Thread.sleep(2000)
        
        // Connect via WiFi
        val wifiId = "$ip:$port"
        val connectResult = execAdb("adb", "connect", wifiId, timeoutMs = 10000)
        
        if (connectResult.contains("connected") || connectResult.contains("already")) {
            // Verify connection works
            Thread.sleep(500)
            val verifyResult = execAdb("adb", "-s", wifiId, "shell", "echo", "ok", timeoutMs = 5000)
            if (verifyResult.trim() == "ok") {
                return wifiId
            }
        }
        
        return null
    }
    
    /**
     * Check if a device ID looks like a WiFi connection (ip:port format)
     */
    fun isWifiConnection(deviceId: String): Boolean {
        return Regex("\\d+\\.\\d+\\.\\d+\\.\\d+:\\d+").matches(deviceId)
    }

    /**
     * Disable USB charging reporting so we can measure real battery drain.
     * The device still receives power via USB for ADB, but the OS stops
     * counting it as "charging" so battery level reflects real consumption.
     */
    fun disableCharging(deviceId: String) {
        execAdbShell(deviceId, "dumpsys battery unplug")
    }

    fun restoreCharging(deviceId: String) {
        execAdbShell(deviceId, "dumpsys battery reset")
    }

    // ===== Game Detection =====

    fun getGamePackage(deviceId: String): String? {
        val output = execAdbShell(deviceId, "dumpsys activity activities")
        if (output.isBlank()) return null

        val systemPrefixes = listOf(
            "com.android.", "com.google.android.", "android.",
            "com.motorola.", "com.samsung.", "com.huawei.", "com.xiaomi.",
            "com.oppo.", "com.bbk.", "com.coloros.", "com.miui."
        )
        val systemKeywords = listOf("launcher", "systemui", "settings", "keyboard", "inputmethod")

        val patterns = listOf(
            Regex("packageName=([\\w.]+)"),
            Regex("cmp=([\\w.]+)/")
        )
        for (pattern in patterns) {
            for (match in pattern.findAll(output)) {
                val pkg = match.groupValues[1]
                if (pkg.contains(".") &&
                    systemPrefixes.none { pkg.startsWith(it) } &&
                    systemKeywords.none { pkg.contains(it, ignoreCase = true) }
                ) return pkg
            }
        }
        return null
    }

    // ===== Frame Data (UNIFIED - FPS + Frame Times from single read) =====

    /** Cached layer name to avoid re-querying SurfaceFlinger every second */
    private var cachedLayer: Pair<String, String>? = null // (packageName, layerName)

    /**
     * Finds the game's rendering layer in SurfaceFlinger.
     * Prioritizes: SurfaceView[pkg] with BLAST > SurfaceView[pkg] without Background
     * > any layer containing the package name.
     * Caches the result since the layer name doesn't change during a session.
     */
    fun findGameLayer(deviceId: String, packageName: String): String? {
        // Return cached if same package
        cachedLayer?.let { (pkg, layer) ->
            if (pkg == packageName) return layer
        }

        val output = execAdb("adb", "-s", deviceId, "shell", "dumpsys", "SurfaceFlinger", "--list")
        if (output.isBlank()) return null
        val layers = output.lines().filter { it.contains(packageName) }

        val found = layers.find { it.contains("SurfaceView") && it.contains("BLAST") }
            ?: layers.find { it.contains("SurfaceView") && !it.contains("Background") }
            ?: layers.firstOrNull()

        if (found != null) cachedLayer = packageName to found
        return found
    }

    /**
     * Captures FPS and frame times from a SINGLE SurfaceFlinger --latency read.
     *
     * Reliability improvements over v5:
     * - Windowed FPS: only uses frames from the last ~1 second for instantaneous FPS
     * - Outlier filtering: removes frame times that are statistical outliers (>3 IQR)
     * - Temporal continuity: detects and handles gaps in the frame timeline
     * - Full buffer frame times: still collects all frame times for histogram analysis
     */
    fun captureFrameData(deviceId: String, packageName: String): FrameData? {
        val layerName = findGameLayer(deviceId, packageName) ?: return null

        val output = execAdbShell(deviceId, "dumpsys SurfaceFlinger --latency '$layerName'")
        val lines = output.lines()
        if (lines.size < 3) return null

        // Parse all present timestamps (column 2 = actualPresentTime in nanoseconds)
        val presentTimes = mutableListOf<Long>()
        for (i in 1 until lines.size) {
            val parts = lines[i].trim().split("\\s+".toRegex())
            if (parts.size >= 2) {
                val ts = parts[1].toLongOrNull() ?: continue
                // Filter invalid timestamps: must be positive, reasonable, and monotonic
                if (ts > 0 && ts < Long.MAX_VALUE / 2) {
                    if (presentTimes.isEmpty() || ts >= presentTimes.last()) {
                        presentTimes.add(ts)
                    }
                }
            }
        }
        if (presentTimes.size < 2) return null

        // Calculate ALL frame times for histogram
        val allFrameTimes = mutableListOf<Double>()
        for (i in 1 until presentTimes.size) {
            val deltaMs = (presentTimes[i] - presentTimes[i - 1]) / 1_000_000.0
            if (deltaMs in 0.1..1000.0) allFrameTimes.add(deltaMs)
        }
        if (allFrameTimes.isEmpty()) return null

        // Remove statistical outliers using IQR method
        val cleanFrameTimes = removeOutliers(allFrameTimes)

        // WINDOWED FPS: use only frames from the last ~1 second of the buffer
        // This gives instantaneous FPS instead of average over the entire buffer
        val windowNs = 1_000_000_000L // 1 second window
        val lastTime = presentTimes.last()
        val windowStart = lastTime - windowNs
        val windowedTimes = presentTimes.filter { it >= windowStart }

        val fps = if (windowedTimes.size >= 2) {
            val windowDelta = (windowedTimes.last() - windowedTimes.first()) / 1_000_000_000.0
            if (windowDelta > 0) ((windowedTimes.size - 1) / windowDelta).toInt().coerceIn(1, 144) else 0
        } else {
            // Fallback to full buffer if window too small
            val fullDelta = (presentTimes.last() - presentTimes.first()) / 1_000_000_000.0
            if (fullDelta > 0) ((presentTimes.size - 1) / fullDelta).toInt().coerceIn(1, 144) else 0
        }

        return FrameData(fps = fps, frameTimes = cleanFrameTimes, timestamp = System.currentTimeMillis())
    }

    /**
     * Removes statistical outliers from frame times using IQR method.
     * Keeps values within [Q1 - 3*IQR, Q3 + 3*IQR] (generous bounds
     * to preserve real jank while removing measurement artifacts).
     */
    private fun removeOutliers(values: List<Double>): List<Double> {
        if (values.size < 4) return values
        val sorted = values.sorted()
        val q1 = sorted[(sorted.size * 0.25).toInt()]
        val q3 = sorted[(sorted.size * 0.75).toInt()]
        val iqr = q3 - q1
        val lowerBound = q1 - 3 * iqr
        val upperBound = q3 + 3 * iqr
        return values.filter { it in lowerBound..upperBound }
    }

    // ===== Memory (Enhanced: Native + Java heap) =====

    fun getMemoryInfo(deviceId: String, packageName: String): MemoryInfo? {
        val output = execAdbShell(deviceId, "dumpsys meminfo $packageName", timeoutMs = 8000)
        if (output.isBlank()) return null

        val totalPss = Regex("TOTAL PSS:\\s+(\\d+)").find(output)?.groupValues?.get(1)?.toLongOrNull()
            ?: Regex("TOTAL\\s+(\\d+)").find(output)?.groupValues?.get(1)?.toLongOrNull()
            ?: return null

        // Parse Native Heap PSS
        val nativeHeap = Regex("Native Heap\\s+(\\d+)").find(output)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

        // Parse Java/Dalvik Heap PSS
        val javaHeap = Regex("(?:Dalvik|Java) Heap\\s+(\\d+)").find(output)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

        return MemoryInfo(
            totalPssKb = totalPss, nativeHeapKb = nativeHeap,
            javaHeapKb = javaHeap, timestamp = System.currentTimeMillis()
        )
    }

    // ===== Missed Frames (global SurfaceFlinger counter) =====

    fun getMissedFrames(deviceId: String): Int {
        val output = execAdbShell(deviceId, "dumpsys SurfaceFlinger", timeoutMs = 3000)
        return Regex("Total missed frame count:\\s*(\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    // ===== CPU Usage =====

    fun getCpuTimes(deviceId: String): Map<String, CpuTimes> {
        val output = execAdbShell(deviceId, "cat /proc/stat")
        if (output.isBlank()) return emptyMap()

        val result = mutableMapOf<String, CpuTimes>()
        for (line in output.lines()) {
            if (!line.startsWith("cpu")) continue
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size < 8) continue
            val name = parts[0] // "cpu", "cpu0", "cpu1", ...
            result[name] = CpuTimes(
                user = parts[1].toLongOrNull() ?: 0,
                nice = parts[2].toLongOrNull() ?: 0,
                system = parts[3].toLongOrNull() ?: 0,
                idle = parts[4].toLongOrNull() ?: 0,
                iowait = parts[5].toLongOrNull() ?: 0,
                irq = parts[6].toLongOrNull() ?: 0,
                softirq = parts[7].toLongOrNull() ?: 0
            )
        }
        return result
    }

    fun calculateCpuUsage(prev: Map<String, CpuTimes>, curr: Map<String, CpuTimes>): CpuSnapshot? {
        val prevTotal = prev["cpu"] ?: return null
        val currTotal = curr["cpu"] ?: return null
        val totalDelta = currTotal.total - prevTotal.total
        if (totalDelta <= 0) return null
        val busyDelta = currTotal.totalBusy - prevTotal.totalBusy
        val totalUsage = (busyDelta.toDouble() / totalDelta * 100).coerceIn(0.0, 100.0)

        val perCore = mutableListOf<Double>()
        var i = 0
        while (true) {
            val key = "cpu$i"
            val p = prev[key] ?: break
            val c = curr[key] ?: break
            val d = c.total - p.total
            if (d > 0) {
                perCore.add(((c.totalBusy - p.totalBusy).toDouble() / d * 100).coerceIn(0.0, 100.0))
            } else {
                perCore.add(0.0)
            }
            i++
        }

        return CpuSnapshot(totalUsage = totalUsage, perCoreUsage = perCore, timestamp = System.currentTimeMillis())
    }

    // ===== GPU Usage =====

    /** Previous GPU busy/total times for delta-based calculation */
    private var prevGpuBusyTotal: Pair<Long, Long>? = null

    /**
     * Gets GPU usage percentage.
     *
     * For Qualcomm Adreno: Uses gpu_busy_percentage which reports the percentage
     * from the last polling interval. We also try gpu_clock_stats for delta-based
     * measurement when available.
     *
     * For Mali: Reads utilization sysfs node.
     *
     * Fix over v5: The gpu_busy_percentage on some Qualcomm SoCs reports
     * a stale or incorrect value. We now also read busy_time/total_time
     * counters and compute delta-based usage for more accuracy.
     */
    fun getGpuUsage(deviceId: String): GpuSnapshot {
        val now = System.currentTimeMillis()

        // Qualcomm Adreno: Try delta-based busy_time first (more reliable)
        val busyTimeOutput = execAdbShell(deviceId, "cat /sys/class/kgsl/kgsl-3d0/gpubusy", timeoutMs = 2000)
        val busyMatch = Regex("(\\d+)\\s+(\\d+)").find(busyTimeOutput)
        if (busyMatch != null) {
            val busy = busyMatch.groupValues[1].toLongOrNull() ?: 0L
            val total = busyMatch.groupValues[2].toLongOrNull() ?: 0L
            if (total > 0) {
                val prev = prevGpuBusyTotal
                prevGpuBusyTotal = busy to total
                if (prev != null) {
                    val deltaBusy = busy - prev.first
                    val deltaTotal = total - prev.second
                    if (deltaTotal > 0) {
                        val pct = (deltaBusy.toDouble() / deltaTotal * 100).coerceIn(0.0, 100.0)
                        return GpuSnapshot(usage = pct, timestamp = now)
                    }
                }
                // First reading: compute from absolute values as fallback
                val pct = (busy.toDouble() / total * 100).coerceIn(0.0, 100.0)
                return GpuSnapshot(usage = pct, timestamp = now)
            }
        }

        // Qualcomm fallback: gpu_busy_percentage
        val pctOutput = execAdbShell(deviceId, "cat /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage", timeoutMs = 2000)
        val pctMatch = Regex("(\\d+)\\s*%?").find(pctOutput)
        if (pctMatch != null) {
            val pct = pctMatch.groupValues[1].toDoubleOrNull()
            if (pct != null) return GpuSnapshot(usage = pct.coerceIn(0.0, 100.0), timestamp = now)
        }

        // Mali: multiple possible paths
        val maliPaths = listOf(
            "cat /sys/devices/platform/*/gpu/utilization 2>/dev/null",
            "cat /sys/module/mali*/parameters/gpu_utilization 2>/dev/null",
            "cat /sys/class/misc/mali*/device/utilisation 2>/dev/null"
        )
        for (path in maliPaths) {
            val output = execAdbShell(deviceId, path, timeoutMs = 2000)
            val match = Regex("(\\d+)").find(output)
            if (match != null) {
                val pct = match.groupValues[1].toDoubleOrNull()
                if (pct != null) return GpuSnapshot(usage = pct.coerceIn(0.0, 100.0), timestamp = now)
            }
        }

        return GpuSnapshot(usage = -1.0, timestamp = now)
    }

    // ===== Temperature =====

    fun getThermalInfo(deviceId: String): ThermalSnapshot {
        // Read all thermal zones in one shot
        val types = execAdbShell(deviceId, "for z in /sys/class/thermal/thermal_zone*; do echo \"\$(cat \$z/type 2>/dev/null):\$(cat \$z/temp 2>/dev/null)\"; done", timeoutMs = 3000)

        var cpuTemp = -1.0
        var gpuTemp = -1.0
        var batteryTemp = -1.0
        var skinTemp = -1.0

        for (line in types.lines()) {
            val parts = line.split(":")
            if (parts.size != 2) continue
            val type = parts[0].lowercase()
            val tempRaw = parts[1].trim().toLongOrNull() ?: continue
            val temp = if (tempRaw > 1000) tempRaw / 1000.0 else tempRaw.toDouble() // some report millidegrees

            when {
                (type.contains("cpu") || type.contains("tsens") || type.contains("soc")) && cpuTemp < 0 -> cpuTemp = temp
                (type.contains("gpu")) && gpuTemp < 0 -> gpuTemp = temp
                (type.contains("battery") || type.contains("batt")) && batteryTemp < 0 -> batteryTemp = temp
                (type.contains("skin") || type.contains("back") || type.contains("quiet")) && skinTemp < 0 -> skinTemp = temp
            }
        }

        return ThermalSnapshot(
            cpuTemp = cpuTemp, gpuTemp = gpuTemp,
            batteryTemp = batteryTemp, skinTemp = skinTemp,
            timestamp = System.currentTimeMillis()
        )
    }

    // ===== Render Resolution =====

    fun getRenderResolution(deviceId: String, packageName: String): RenderResolution? {
        val output = execAdbShell(deviceId, "dumpsys SurfaceFlinger", timeoutMs = 3000)
        if (output.isBlank()) return null

        // Look for buffer size of the game's SurfaceView
        val pattern = Regex("name:SurfaceView\\[$packageName[^]]*\\][^,]*,\\s*id:\\w+,\\s*size:[^,]+,\\s*w/h:(\\w+)x(\\w+)")
        val match = pattern.find(output)
        if (match != null) {
            val w = match.groupValues[1].toIntOrNull() ?: match.groupValues[1].toIntOrNull(16) ?: return null
            val h = match.groupValues[2].toIntOrNull() ?: match.groupValues[2].toIntOrNull(16) ?: return null
            if (w > 0 && h > 0) return RenderResolution(w, h)
        }

        // Fallback: visible region
        val regionPattern = Regex("SurfaceView\\[$packageName[^]]*\\]\\(BLAST\\)[^\\[]*\\[\\s*(\\d+),\\s*(\\d+),\\s*(\\d+),\\s*(\\d+)\\]")
        val regionMatch = regionPattern.find(output)
        if (regionMatch != null) {
            val w = (regionMatch.groupValues[3].toIntOrNull() ?: 0) - (regionMatch.groupValues[1].toIntOrNull() ?: 0)
            val h = (regionMatch.groupValues[4].toIntOrNull() ?: 0) - (regionMatch.groupValues[2].toIntOrNull() ?: 0)
            if (w > 0 && h > 0) return RenderResolution(w, h)
        }
        return null
    }

    // ===== Logs (with REAL timestamp parsing) =====

    /**
     * Get game PID for filtering logs. Returns 0 if not found.
     */
    fun getGamePid(deviceId: String, packageName: String): Int {
        val output = execAdbShell(deviceId, "pidof $packageName")
        return output.trim().split("\\s+".toRegex()).firstOrNull()?.toIntOrNull() ?: 0
    }

    fun getGameLogs(deviceId: String, packageName: String, lastN: Int): List<LogEntry> {
        // Get game PID to filter logs
        val pid = getGamePid(deviceId, packageName)

        // If we have PID, filter by it. Otherwise grab all and filter by package-related tags
        val cmd = if (pid > 0) {
            "logcat -d -v threadtime --pid=$pid -t $lastN"
        } else {
            "logcat -d -v threadtime -t 2000"
        }

        val output = execAdbShell(deviceId, cmd, timeoutMs = 8000)
        if (output.isBlank()) return emptyList()

        val entries = mutableListOf<LogEntry>()
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        // System-level tags that are relevant even from other PIDs
        val systemTags = setOf(
            "surfaceflinger", "hwcomposer", "lowmemorydetector",
            "thermalmitigation", "thermal", "activitymanager"
        )

        for (line in output.lines()) {
            if (line.startsWith("---------") || line.length < 20) continue
            val entry = parseLogLine(line, deviceId, currentYear) ?: continue

            // If we filtered by PID, accept all. Otherwise filter.
            if (pid > 0 || entry.tag.lowercase() in systemTags) {
                entries.add(entry)
            }
        }
        return entries.takeLast(lastN)
    }

    private fun parseLogLine(line: String, deviceId: String, year: Int): LogEntry? {
        // Format: MM-DD HH:MM:SS.mmm  PID  TID LEVEL/TAG: message
        val pattern = Regex("(\\d{2})-(\\d{2})\\s+(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{3})\\s+(\\d+)\\s+(\\d+)\\s+(\\w)/([^:]+):\\s*(.*)")
        val match = pattern.find(line) ?: return null

        val month = match.groupValues[1].toIntOrNull() ?: return null
        val day = match.groupValues[2].toIntOrNull() ?: return null
        val hour = match.groupValues[3].toIntOrNull() ?: return null
        val min = match.groupValues[4].toIntOrNull() ?: return null
        val sec = match.groupValues[5].toIntOrNull() ?: return null
        val ms = match.groupValues[6].toIntOrNull() ?: return null
        val pid = match.groupValues[7].toIntOrNull() ?: 0
        val levelChar = match.groupValues[9]
        val tag = match.groupValues[10].trim()
        val message = match.groupValues[11].trim()

        // Build epoch timestamp
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, day, hour, min, sec)
        cal.set(Calendar.MILLISECOND, ms)
        val timestamp = cal.timeInMillis

        return LogEntry(
            timestamp = timestamp,
            level = parseLevel(levelChar),
            tag = tag, message = message,
            deviceId = deviceId, pid = pid
        )
    }

    private fun parseLevel(level: String): LogLevel {
        return when (level) {
            "V" -> LogLevel.VERBOSE
            "D" -> LogLevel.DEBUG
            "I" -> LogLevel.INFO
            "W" -> LogLevel.WARN
            "E" -> LogLevel.ERROR
            "F" -> LogLevel.FATAL
            else -> LogLevel.DEBUG
        }
    }
}
