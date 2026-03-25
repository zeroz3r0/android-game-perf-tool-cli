package com.gameperf.analysis

import com.gameperf.core.LogEntry
import com.gameperf.core.LogLevel
import com.gameperf.core.Metric
import com.gameperf.core.MetricType

data class Rule(
    val id: String,
    val name: String,
    val pattern: String,
    val metricType: MetricType,
    val severity: Severity
)

enum class Severity {
    INFO, WARNING, ERROR
}

data class RuleMatch(
    val rule: Rule,
    val value: String,
    val logEntry: LogEntry
)

class RulesEngine {
    
    private val rules = mutableListOf<Rule>()
    private val compiledPatterns = mutableMapOf<String, Regex>()
    
    fun loadRulesFromJson(json: String) {
        // Placeholder - will be implemented in Phase 6
        // Parse JSON and populate rules list
    }
    
    fun addRule(rule: Rule) {
        rules.add(rule)
        compiledPatterns[rule.id] = Regex(rule.pattern, RegexOption.IGNORE_CASE)
    }
    
    fun evaluate(logEntry: LogEntry): List<RuleMatch> {
        val matches = mutableListOf<RuleMatch>()
        val message = logEntry.message
        
        for (rule in rules) {
            val pattern = compiledPatterns[rule.id] ?: continue
            val match = pattern.find(message)
            
            if (match != null) {
                matches.add(RuleMatch(
                    rule = rule,
                    value = match.groupValues.getOrNull(1) ?: match.value,
                    logEntry = logEntry
                ))
            }
        }
        
        return matches
    }
    
    fun evaluateMetric(metric: Metric): RuleMatch? {
        // Check if metric exceeds thresholds
        return when (metric.type) {
            MetricType.FPS -> {
                if (metric.value < 30) {
                    RuleMatch(
                        rule = Rule("low_fps", "Low FPS", "", MetricType.FPS, Severity.ERROR),
                        value = metric.value.toString(),
                        logEntry = LogEntry(
                            timestamp = metric.timestamp,
                            level = LogLevel.WARN,
                            tag = "GamePerf",
                            message = "Low FPS detected: ${metric.value}",
                            deviceId = metric.deviceId
                        )
                    )
                } else null
            }
            MetricType.FRAME_TIME -> {
                if (metric.value > 33.33) { // Below 30fps
                    RuleMatch(
                        rule = Rule("high_frame_time", "High Frame Time", "", MetricType.FRAME_TIME, Severity.ERROR),
                        value = "${metric.value}ms",
                        logEntry = LogEntry(
                            timestamp = metric.timestamp,
                            level = LogLevel.WARN,
                            tag = "GamePerf",
                            message = "High frame time: ${metric.value}ms",
                            deviceId = metric.deviceId
                        )
                    )
                } else null
            }
            else -> null
        }
    }
    
    fun getRules(): List<Rule> = rules.toList()
}
