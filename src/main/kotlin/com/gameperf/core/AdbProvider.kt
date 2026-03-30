package com.gameperf.core

/**
 * Abstraction over ADB communication for testability.
 *
 * All device interaction goes through this interface, allowing
 * tests to inject mock implementations without needing a real
 * device connected.
 *
 * @see AdbConnector for the real implementation
 */
interface AdbProvider {

    // ===== Connection =====

    /** Returns true if the `adb` command is available in PATH. */
    fun isAdbAvailable(): Boolean

    /** Lists all connected Android devices. */
    fun listDevices(): List<AndroidDevice>

    /** Retrieves hardware specs for a specific device. */
    fun getDeviceSpecs(deviceId: String): DeviceSpecs

    /** Checks if the device ID is a WiFi connection (ip:port format). */
    fun isWifiConnection(deviceId: String): Boolean

    /** Switches a USB-connected device to WiFi ADB. Returns WiFi device ID or null. */
    fun switchToWifi(usbDeviceId: String, port: Int = 5555): String?

    // ===== Battery =====

    /** Gets current battery level and temperature. */
    fun getBatteryLevel(deviceId: String): BatteryInfo?

    /** Disables USB charging reporting for real battery drain measurement. */
    fun disableCharging(deviceId: String)

    /** Restores normal charging reporting. */
    fun restoreCharging(deviceId: String)

    // ===== Game Detection =====

    /** Detects the foreground game package, filtering out system apps. */
    fun getGamePackage(deviceId: String): String?

    /** Gets the PID of the running game process. */
    fun getGamePid(deviceId: String, packageName: String): Int

    // ===== Frame Data =====

    /** Finds the SurfaceFlinger layer name for the game's rendering surface. */
    fun findGameLayer(deviceId: String, packageName: String): String?

    /**
     * Captures FPS and frame times from SurfaceFlinger --latency.
     *
     * Uses windowed FPS calculation (last 1 second) for instantaneous
     * measurement, and IQR-based outlier filtering for frame times.
     */
    fun captureFrameData(deviceId: String, packageName: String): FrameData?

    // ===== Memory =====

    /** Gets memory usage for the game process (Total PSS, Native Heap, Java Heap). */
    fun getMemoryInfo(deviceId: String, packageName: String): MemoryInfo?

    // ===== Frame Drops =====

    /** Gets the global missed frame counter from SurfaceFlinger. */
    fun getMissedFrames(deviceId: String): Int

    // ===== CPU =====

    /** Reads /proc/stat for system-wide CPU times (total + per-core). */
    fun getCpuTimes(deviceId: String): Map<String, CpuTimes>

    /** Calculates CPU usage percentage from two snapshots of /proc/stat. */
    fun calculateCpuUsage(prev: Map<String, CpuTimes>, curr: Map<String, CpuTimes>): CpuSnapshot?

    /**
     * Gets CPU usage specifically for the game process via /proc/{pid}/stat.
     * Returns percentage (0-100) or -1 if unavailable.
     */
    fun getProcessCpuUsage(deviceId: String, packageName: String): Double

    // ===== GPU =====

    /**
     * Gets GPU usage percentage.
     * Uses delta-based measurement for Qualcomm Adreno (gpubusy),
     * falls back to gpu_busy_percentage, then Mali sysfs paths.
     */
    fun getGpuUsage(deviceId: String): GpuSnapshot

    // ===== Temperature =====

    /** Reads thermal zones for CPU, GPU, battery and skin temperatures. */
    fun getThermalInfo(deviceId: String): ThermalSnapshot

    // ===== Render Resolution =====

    /** Detects the game's actual render resolution from SurfaceFlinger buffer info. */
    fun getRenderResolution(deviceId: String, packageName: String): RenderResolution?

    // ===== Logs =====

    /** Gets recent game logs, filtered by PID when available. */
    fun getGameLogs(deviceId: String, packageName: String, lastN: Int): List<LogEntry>
}
