package com.gameperf.core

data class Metric(
    val type: MetricType,
    val value: Double,
    val timestamp: Long,
    val deviceId: String
)

enum class MetricType {
    FPS,
    FRAME_TIME,
    MEMORY,
    CPU,
    JANK_FRAMES,
    SLOW_RENDER
}

class MetricsExtractor {
    
    private val metrics = mutableListOf<Metric>()
    private val fpsHistory = mutableListOf<Double>()
    private val frameTimes = mutableListOf<Double>()
    
    fun extract(logEntry: LogEntry): Metric? {
        val message = logEntry.message
        
        extractFps(message, logEntry)?.let { return it }
        extractFrameTime(message, logEntry)?.let { return it }
        extractMemory(message, logEntry)?.let { return it }
        extractCpu(message, logEntry)?.let { return it }
        extractJank(message, logEntry)?.let { return it }
        
        return null
    }
    
    fun extractFromDumpsys(deviceId: String, fps: Int, frameTime: Double, memoryMb: Double) {
        metrics.add(Metric(MetricType.FPS, fps.toDouble(), System.currentTimeMillis(), deviceId))
        fpsHistory.add(fps.toDouble())
        if (frameTime > 0) {
            metrics.add(Metric(MetricType.FRAME_TIME, frameTime, System.currentTimeMillis(), deviceId))
            frameTimes.add(frameTime)
        }
        if (memoryMb > 0) {
            metrics.add(Metric(MetricType.MEMORY, memoryMb, System.currentTimeMillis(), deviceId))
        }
    }
    
    private fun extractFps(message: String, logEntry: LogEntry): Metric? {
        val patterns = listOf(
            Regex("FPS[:\\s]+(\\d+\\.?\\d*)", RegexOption.IGNORE_CASE),
            Regex("fps[:\\s]+(\\d+)", RegexOption.IGNORE_CASE),
            Regex("(\\d+)\\s*fps", RegexOption.IGNORE_CASE),
            Regex("frames/sec[:\\s]+(\\d+\\.?\\d*)", RegexOption.IGNORE_CASE),
            Regex("SurfaceFlinger\\s+(\\d+)\\s+fps", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in patterns) {
            val match = pattern.find(message) ?: continue
            val fps = match.groupValues[1].toDoubleOrNull() ?: continue
            
            if (fps in 1.0..240.0) {
                fpsHistory.add(fps)
                return Metric(
                    type = MetricType.FPS,
                    value = fps,
                    timestamp = logEntry.timestamp,
                    deviceId = logEntry.deviceId
                ).also { metrics.add(it) }
            }
        }
        
        return null
    }
    
    private fun extractFrameTime(message: String, logEntry: LogEntry): Metric? {
        val framePattern = Regex("(?:Frame[\\s-]?time|frame|ftime)[:\\s]+(\\d+\\.?\\d*)\\s*ms?", RegexOption.IGNORE_CASE)
        val match = framePattern.find(message) ?: return null
        
        val frameTime = match.groupValues[1].toDoubleOrNull() ?: return null
        
        frameTimes.add(frameTime)
        return Metric(
            type = MetricType.FRAME_TIME,
            value = frameTime,
            timestamp = logEntry.timestamp,
            deviceId = logEntry.deviceId
        ).also { metrics.add(it) }
    }
    
    private fun extractMemory(message: String, logEntry: LogEntry): Metric? {
        val memPattern = Regex("(?:Memory|Mem|PSS)[:\\s]+(\\d+\\.?\\d*)\\s*(MB|GB|M|k|K)?", RegexOption.IGNORE_CASE)
        val match = memPattern.find(message) ?: return null
        
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].uppercase()
        
        val memoryMb = when (unit) {
            "GB" -> value * 1024
            "MB", "M" -> value
            "K" -> value / 1024
            else -> value
        }
        
        return Metric(
            type = MetricType.MEMORY,
            value = memoryMb,
            timestamp = logEntry.timestamp,
            deviceId = logEntry.deviceId
        ).also { metrics.add(it) }
    }
    
    private fun extractCpu(message: String, logEntry: LogEntry): Metric? {
        val cpuPattern = Regex("(?:CPU|cpu)[:\\s]+(\\d+\\.?\\d*)\\s*%", RegexOption.IGNORE_CASE)
        val match = cpuPattern.find(message) ?: return null
        
        val cpu = match.groupValues[1].toDoubleOrNull() ?: return null
        
        return Metric(
            type = MetricType.CPU,
            value = cpu,
            timestamp = logEntry.timestamp,
            deviceId = logEntry.deviceId
        ).also { metrics.add(it) }
    }
    
    private fun extractJank(message: String, logEntry: LogEntry): Metric? {
        if (message.contains("jank") || message.contains("dropped frame") || message.contains("slow frame")) {
            return Metric(
                type = MetricType.JANK_FRAMES,
                value = 1.0,
                timestamp = logEntry.timestamp,
                deviceId = logEntry.deviceId
            ).also { metrics.add(it) }
        }
        return null
    }
    
    fun getAllMetrics(): List<Metric> = metrics.toList()
    
    fun getMetricsByType(type: MetricType): List<Metric> = 
        metrics.filter { it.type == type }
    
    fun getAverageFps(): Double? {
        return if (fpsHistory.isNotEmpty()) fpsHistory.average() else null
    }
    
    fun getMinFps(): Double? {
        return fpsHistory.minOrNull()
    }
    
    fun getMaxFps(): Double? {
        return fpsHistory.maxOrNull()
    }
    
    fun getFpsStability(): Double {
        if (fpsHistory.size < 2) return 100.0
        val avg = fpsHistory.average()
        val variance = fpsHistory.map { (it - avg) * (it - avg) }.average()
        val stdDev = kotlin.math.sqrt(variance)
        return (1 - (stdDev / avg)) * 100
    }
    
    fun getFrameDrops(): Int {
        return frameTimes.count { it > 16.67 }
    }
    
    fun getSlowFrames(): Int {
        return frameTimes.count { it > 33.33 }
    }
    
    fun getAverageFrameTime(): Double? {
        return if (frameTimes.isNotEmpty()) frameTimes.average() else null
    }
    
    fun getMemoryUsage(): Double? {
        val memMetrics = getMetricsByType(MetricType.MEMORY)
        return memMetrics.lastOrNull()?.value
    }
    
    fun getPeakMemory(): Double? {
        return getMetricsByType(MetricType.MEMORY).maxOfOrNull { it.value }
    }
    
    fun getFpsHistory(): List<Double> = fpsHistory.toList()
    
    fun clear() {
        metrics.clear()
        fpsHistory.clear()
        frameTimes.clear()
    }
}
