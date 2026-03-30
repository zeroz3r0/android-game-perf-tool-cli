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
    val ram: Long,
    val resolution: String,
    val cores: Int,
    val gpuModel: String
) {
    val ramGb: Double get() = ram / (1024.0 * 1024 * 1024)
}

// ===== Battery =====

data class BatteryInfo(
    val level: Int,
    val temperature: Float,
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
    val frameTimes: List<Double>,
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
    val totalUsage: Double,
    val perCoreUsage: List<Double>,
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
    val usage: Double,
    val timestamp: Long
) {
    val isAvailable: Boolean get() = usage >= 0
}

// ===== Temperature =====

data class ThermalSnapshot(
    val cpuTemp: Double,
    val gpuTemp: Double,
    val batteryTemp: Double,
    val skinTemp: Double,
    val timestamp: Long
) {
    val hasCpuTemp: Boolean get() = cpuTemp > 0
    val hasGpuTemp: Boolean get() = gpuTemp > 0
    val hasSkinTemp: Boolean get() = skinTemp > 0
}

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
    val resolutionChanged: Boolean
        get() = previousResolution != null && newResolution != null && previousResolution != newResolution
    val resolutionDecreased: Boolean
        get() = resolutionChanged && (newResolution!!.pixels < previousResolution!!.pixels)
    val fpsImproved: Boolean
        get() = fpsAfterChange > fpsBeforeChange + 3
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

data class Problem(
    val type: String,
    val severity: Severity,
    val description: String,
    val explanation: String,
    val solution: String
)

enum class Severity(val label: String, val color: String, val weight: Int) {
    HIGH("Alto", "#ff0044", 12),
    MEDIUM("Medio", "#ffaa00", 6),
    LOW("Bajo", "#00d4ff", 2)
}

data class FpsDrop(
    val index: Int,
    val fps: Int,
    val timestamp: Long,
    val nearbyEvent: LogEntry?
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

data class SessionData(
    val gamePackage: String,
    val deviceSpecs: DeviceSpecs,
    val samples: MutableList<SessionSample>,
    val events: MutableList<LogEntry>,
    val configChanges: MutableList<GraphicsConfigChange>,
    val batteryStart: BatteryInfo?,
    val batteryEnd: BatteryInfo?,
    val memStart: MemoryInfo?,
    val memEnd: MemoryInfo?,
    val missedFramesStart: Int,
    val missedFramesEnd: Int,
    val startTime: Long,
    var endTime: Long,
    val initialResolution: RenderResolution?
) {
    val durationSeconds: Int get() = ((endTime - startTime) / 1000).toInt()
    val totalFrameDrops: Int get() = missedFramesEnd - missedFramesStart
}

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

// ===== Analysis Result =====

data class AnalysisResult(
    val session: SessionData,
    val fpsPercentiles: PercentileStats?,
    val frameTimePercentiles: PercentileStats?,
    val problems: List<Problem>,
    val relevantEvents: List<LogEntry>,
    val categorizedEvents: List<CategorizedEvent>,
    val groupedErrors: List<Pair<String, Int>>,
    val groupedWarnings: List<Pair<String, Int>>,
    val fpsDrops: List<FpsDrop>,
    val grade: Char,
    val gcCount: Int,
    val audioIssues: Int,
    val thermalEvents: Int
)
