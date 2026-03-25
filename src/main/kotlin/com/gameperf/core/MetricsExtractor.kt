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
    CPU
}

class MetricsExtractor {
    
    private val metrics = mutableListOf<Metric>()
    
    fun extract(logEntry: LogEntry): Metric? {
        val message = logEntry.message
        
        // FPS patterns
        extractFps(message, logEntry)?.let { return it }
        
        // Frame time patterns  
        extractFrameTime(message, logEntry)?.let { return it }
        
        // Memory patterns
        extractMemory(message, logEntry)?.let { return it }
        
        return null
    }
    
    private fun extractFps(message: String, logEntry: LogEntry): Metric? {
        // Pattern: "FPS: 60" or "fps: 60" or "FPS: 59.98"
        val fpsPattern = Regex("FPS[:\\s]+(\\d+\\.?\\d*)", RegexOption.IGNORE_CASE)
        val match = fpsPattern.find(message) ?: return null
        
        val fps = match.groupValues[1].toDoubleOrNull() ?: return null
        
        return Metric(
            type = MetricType.FPS,
            value = fps,
            timestamp = logEntry.timestamp,
            deviceId = logEntry.deviceId
        ).also { metrics.add(it) }
    }
    
    private fun extractFrameTime(message: String, logEntry: LogEntry): Metric? {
        // Pattern: "Frame time: 16.67ms" or "frame: 16ms"
        val framePattern = Regex("(?:Frame[\\s-]?time|frame)[:\\s]+(\\d+\\.?\\d*)\\s*ms?", RegexOption.IGNORE_CASE)
        val match = framePattern.find(message) ?: return null
        
        val frameTime = match.groupValues[1].toDoubleOrNull() ?: return null
        
        return Metric(
            type = MetricType.FRAME_TIME,
            value = frameTime,
            timestamp = logEntry.timestamp,
            deviceId = logEntry.deviceId
        ).also { metrics.add(it) }
    }
    
    private fun extractMemory(message: String, logEntry: LogEntry): Metric? {
        // Pattern: "Memory: 256MB" or "mem: 256 MB"
        val memPattern = Regex("(?:Memory|Mem)[:\\s]+(\\d+\\.?\\d*)\\s*(MB|GB|M)?", RegexOption.IGNORE_CASE)
        val match = memPattern.find(message) ?: return null
        
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].uppercase()
        
        val memoryMb = when (unit) {
            "GB" -> value * 1024
            "MB" -> value
            "M" -> value
            else -> value / (1024 * 1024)
        }
        
        return Metric(
            type = MetricType.MEMORY,
            value = memoryMb,
            timestamp = logEntry.timestamp,
            deviceId = logEntry.deviceId
        ).also { metrics.add(it) }
    }
    
    fun getAllMetrics(): List<Metric> = metrics.toList()
    
    fun getMetricsByType(type: MetricType): List<Metric> = 
        metrics.filter { it.type == type }
    
    fun getAverageFps(): Double? {
        val fpsMetrics = getMetricsByType(MetricType.FPS)
        return if (fpsMetrics.isNotEmpty()) fpsMetrics.map { it.value }.average() else null
    }
    
    fun getMinFps(): Double? {
        val fpsMetrics = getMetricsByType(MetricType.FPS)
        return fpsMetrics.minOfOrNull { it.value }
    }
    
    fun getFrameDrops(): Int {
        val frameTimeMetrics = getMetricsByType(MetricType.FRAME_TIME)
        return frameTimeMetrics.count { it.value > 16.67 } // Above 60fps threshold
    }
    
    fun clear() = metrics.clear()
}
