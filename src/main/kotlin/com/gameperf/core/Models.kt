package com.gameperf.core

// ===== Device =====
data class AndroidDevice(
    val id: String,
    val name: String,
    val model: String,
    val manufacturer: String,
    val sdkVersion: Int,
    val isEmulator: Boolean
)

data class DeviceSpecs(
    val model: String,
    val manufacturer: String,
    val sdkVersion: Int,
    val cpu: String,
    val ram: Long, // bytes
    val resolution: String,
    val cores: Int,
    val gpuModel: String
)

// ===== Battery =====
data class BatteryInfo(
    val level: Int,
    val temperature: Float, // Celsius
    val isCharging: Boolean,
    val voltage: Int
)

// ===== Memory =====
data class MemoryInfo(
    val totalPssKb: Long,
    val nativeHeapKb: Long,
    val javaHeapKb: Long,
    val timestamp: Long
) {
    val totalMb: Long get() = totalPssKb / 1024
    val nativeHeapMb: Long get() = nativeHeapKb / 1024
    val javaHeapMb: Long get() = javaHeapKb / 1024
}

// ===== Frame Data =====
data class FrameData(
    val fps: Int,
    val frameTimes: List<Double>, // ms per frame
    val timestamp: Long
) {
    val jankFrames: Int get() = frameTimes.count { it > 16.67 }
    val severeJankFrames: Int get() = frameTimes.count { it > 33.33 }
    val stutterFrames: Int get() = frameTimes.count { it > 100.0 }
}

data class RenderResolution(
    val width: Int,
    val height: Int
) {
    val pixels: Long get() = width.toLong() * height.toLong()
    override fun toString() = "${width}x${height}"
}

// ===== CPU =====
data class CpuSnapshot(
    val totalUsage: Double, // 0-100%
    val perCoreUsage: List<Double>, // 0-100% per core
    val timestamp: Long
)

data class CpuTimes(
    val user: Long,
    val nice: Long,
    val system: Long,
    val idle: Long,
    val iowait: Long,
    val irq: Long,
    val softirq: Long
) {
    val totalBusy: Long get() = user + nice + system + irq + softirq
    val total: Long get() = totalBusy + idle + iowait
}

// ===== GPU =====
data class GpuSnapshot(
    val usage: Double, // 0-100%, -1 if unavailable
    val timestamp: Long
)

// ===== Temperature =====
data class ThermalSnapshot(
    val cpuTemp: Double, // Celsius, -1 if unavailable
    val gpuTemp: Double, // Celsius, -1 if unavailable
    val batteryTemp: Double, // Celsius
    val skinTemp: Double, // Celsius, -1 if unavailable
    val timestamp: Long
)

// ===== Logs =====
enum class LogLevel {
    VERBOSE, DEBUG, INFO, WARN, ERROR, FATAL
}

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val deviceId: String,
    val pid: Int = 0
)

// ===== Graphics Config =====
data class GraphicsConfigChange(
    val timestamp: Long,
    val previousResolution: RenderResolution?,
    val newResolution: RenderResolution?,
    val fpsBeforeChange: Int,
    val fpsAfterChange: Int
) {
    val resolutionChanged: Boolean get() = previousResolution != null && newResolution != null && previousResolution != newResolution
    val resolutionDecreased: Boolean get() = resolutionChanged && (newResolution!!.pixels < previousResolution!!.pixels)
    val fpsImproved: Boolean get() = fpsAfterChange > fpsBeforeChange + 3
}

// ===== Event Categories =====
enum class EventCategory(val label: String, val icon: String, val color: String) {
    GC("Recolector de basura", "GC", "#00ff88"),
    AUDIO("Audio", "AUD", "#ffaa00"),
    THERMAL("Temperatura", "TMP", "#ff6600"),
    MEMORY("Memoria", "MEM", "#ff0044"),
    JANK("Jank/Stutter", "JNK", "#00d4ff"),
    CRASH("Crash/Fatal", "CRS", "#ff0044"),
    ANR("App no responde", "ANR", "#ff0044"),
    NETWORK("Red", "NET", "#7b2cbf"),
    GRAPHICS("Graficos", "GFX", "#88ff00"),
    OTHER("Sistema", "SYS", "#888888")
}

data class CategorizedEvent(
    val entry: LogEntry,
    val category: EventCategory,
    val description: String,
    val impactsPerformance: Boolean
)

// ===== Analysis =====
data class Problema(
    val tipo: String,
    val severidad: String,
    val descripcion: String,
    val explicacion: String,
    val solucion: String
)

data class FpsDrop(
    val index: Int,
    val fps: Int,
    val timestamp: Long,
    val eventoCercano: LogEntry?
)

// ===== Session =====
data class SessionSample(
    val timestamp: Long,
    val fps: Int,
    val frameTimes: List<Double>,
    val memoryInfo: MemoryInfo?,
    val cpuSnapshot: CpuSnapshot?,
    val gpuSnapshot: GpuSnapshot?,
    val thermalSnapshot: ThermalSnapshot?,
    val renderResolution: RenderResolution?
)

// ===== Percentiles =====
data class PercentileStats(
    val p1: Double,
    val p5: Double,
    val p50: Double,
    val p90: Double,
    val p95: Double,
    val p99: Double,
    val min: Double,
    val max: Double,
    val avg: Double
) {
    companion object {
        fun fromValues(values: List<Double>): PercentileStats? {
            if (values.isEmpty()) return null
            val sorted = values.sorted()
            val n = sorted.size
            return PercentileStats(
                p1 = sorted[(n * 0.01).toInt().coerceIn(0, n - 1)],
                p5 = sorted[(n * 0.05).toInt().coerceIn(0, n - 1)],
                p50 = sorted[(n * 0.50).toInt().coerceIn(0, n - 1)],
                p90 = sorted[(n * 0.90).toInt().coerceIn(0, n - 1)],
                p95 = sorted[(n * 0.95).toInt().coerceIn(0, n - 1)],
                p99 = sorted[(n * 0.99).toInt().coerceIn(0, n - 1)],
                min = sorted.first(),
                max = sorted.last(),
                avg = values.average()
            )
        }

        fun fromIntValues(values: List<Int>): PercentileStats? {
            return fromValues(values.map { it.toDouble() })
        }
    }
}
