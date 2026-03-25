package com.gameperf.core

import com.gameperf.analysis.RuleMatch

data class PerformanceReport(
    val deviceId: String,
    val averageFps: Double?,
    val minFps: Double?,
    val maxFps: Double?,
    val frameDrops: Int,
    val averageMemory: Double?,
    val warnings: List<String>,
    val errors: List<String>,
    val timestamp: Long
)

class ReportGenerator(
    private val metricsExtractor: MetricsExtractor,
    private val warnings: List<RuleMatch>,
    private val errors: List<RuleMatch>
) {
    
    fun generate(deviceId: String): PerformanceReport {
        val fpsMetrics = metricsExtractor.getMetricsByType(MetricType.FPS)
        val memoryMetrics = metricsExtractor.getMetricsByType(MetricType.MEMORY)
        
        return PerformanceReport(
            deviceId = deviceId,
            averageFps = metricsExtractor.getAverageFps(),
            minFps = metricsExtractor.getMinFps(),
            maxFps = fpsMetrics.maxOfOrNull { it.value },
            frameDrops = metricsExtractor.getFrameDrops(),
            averageMemory = if (memoryMetrics.isNotEmpty()) 
                memoryMetrics.map { it.value }.average() else null,
            warnings = warnings.map { it.rule.name },
            errors = errors.map { it.rule.name },
            timestamp = System.currentTimeMillis()
        )
    }
    
    fun toJson(report: PerformanceReport): String {
        val warningsList = if (report.warnings.isNotEmpty()) {
            report.warnings.joinToString(",", "[", "]") { "\"$it\"" }
        } else {
            "[]"
        }
        
        val errorsList = if (report.errors.isNotEmpty()) {
            report.errors.joinToString(",", "[", "]") { "\"$it\"" }
        } else {
            "[]"
        }
        
        return """
        {
            "deviceId": "${report.deviceId}",
            "timestamp": ${report.timestamp},
            "fps": {
                "average": ${report.averageFps},
                "min": ${report.minFps},
                "max": ${report.maxFps},
                "frameDrops": ${report.frameDrops}
            },
            "memory": {
                "averageMb": ${report.averageMemory}
            },
            "warnings": $warningsList,
            "errors": $errorsList
        }
        """.trimIndent()
    }
    
    fun toMarkdown(report: PerformanceReport): String {
        val avgFps = report.averageFps?.let { "%.2f".format(it) } ?: "N/A"
        val minFps = report.minFps?.let { "%.2f".format(it) } ?: "N/A"
        val maxFps = report.maxFps?.let { "%.2f".format(it) } ?: "N/A"
        val avgMem = report.averageMemory?.let { "%.2f MB".format(it) } ?: "N/A"
        
        val warningsSection = if (report.warnings.isNotEmpty()) {
            "### Warnings\n${report.warnings.joinToString("\n") { "- $it" }}\n"
        } else {
            "### Warnings\nNone\n"
        }
        
        val errorsSection = if (report.errors.isNotEmpty()) {
            "### Errors\n${report.errors.joinToString("\n") { "- $it" }}\n"
        } else {
            "### Errors\nNone\n"
        }
        
        return """
# Performance Report

**Device**: ${report.deviceId}
**Generated**: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(report.timestamp))}

## FPS Analysis
- **Average**: $avgFps
- **Min**: $minFps
- **Max**: $maxFps
- **Frame Drops**: ${report.frameDrops}

## Memory
- **Average**: $avgMem

## Issues
$errorsSection
$warningsSection
        """.trimIndent()
    }
}
