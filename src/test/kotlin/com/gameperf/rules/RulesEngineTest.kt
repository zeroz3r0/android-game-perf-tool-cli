package com.gameperf.rules

import com.gameperf.core.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RulesEngineTest {

    // ===== Loading Tests =====

    @Test
    fun `loadRules loads default rules from classpath`() {
        val config = RulesEngine.loadRules()
        assertTrue(config.rules.isNotEmpty(), "Should load rules from rules-default.json")
        assertTrue(config.rules.size >= 10, "Should have at least 10 rules, got ${config.rules.size}")
    }

    @Test
    fun `loadRules parses all rule fields correctly`() {
        val config = RulesEngine.loadRules()
        val lowFps = config.rules.find { it.id == "low_fps_threshold" }
        assertNotNull(lowFps, "Should find low_fps_threshold rule")
        assertEquals("Low FPS Detection", lowFps.name)
        assertEquals("FPS", lowFps.metric)
        assertEquals("ERROR", lowFps.severity)
        assertEquals(30.0, lowFps.threshold)
        assertTrue(lowFps.pattern.isNotEmpty(), "Should have a pattern")
    }

    @Test
    fun `loadRules parses global thresholds from JSON`() {
        val config = RulesEngine.loadRules()
        val t = config.globalThresholds
        assertEquals(30, t.fpsMin)
        assertEquals(60, t.fpsTarget)
        assertEquals(16.67, t.frameTimeMax)
        assertEquals(512, t.memoryWarning)
        assertEquals(768, t.memoryCritical)
    }

    @Test
    fun `loadRules with nonexistent file falls back to classpath`() {
        val config = RulesEngine.loadRules("/nonexistent/path/rules.json")
        assertTrue(config.rules.isNotEmpty(), "Should fall back to classpath rules")
    }

    // ===== JSON Parsing Tests =====

    @Test
    fun `extractString parses simple string`() {
        val json = """{"name": "Test Rule", "id": "test_1"}"""
        assertEquals("Test Rule", RulesEngine.extractString(json, "name"))
        assertEquals("test_1", RulesEngine.extractString(json, "id"))
    }

    @Test
    fun `extractString handles escaped quotes`() {
        val json = """{"pattern": "FPS[:\\s]+(\\d+\\.?\\d*)"}"""
        val result = RulesEngine.extractString(json, "pattern")
        assertNotNull(result)
        assertTrue(result.contains("FPS"), "Should contain FPS pattern: $result")
    }

    @Test
    fun `extractNumber parses integer and decimal`() {
        val json = """{"threshold": 30, "maxMs": 16.67}"""
        assertEquals(30.0, RulesEngine.extractNumber(json, "threshold"))
        assertEquals(16.67, RulesEngine.extractNumber(json, "maxMs"))
    }

    @Test
    fun `extractString returns null for missing key`() {
        val json = """{"name": "test"}"""
        assertEquals(null, RulesEngine.extractString(json, "missing"))
    }

    @Test
    fun `parseRulesJson parses complete JSON`() {
        val json = """{
            "rules": [
                {
                    "id": "test_rule",
                    "name": "Test Rule",
                    "description": "A test",
                    "pattern": "test_pattern",
                    "metricType": "FPS",
                    "severity": "WARNING",
                    "threshold": 45
                }
            ],
            "globalThresholds": {
                "fps": { "minimum": 25, "target": 60 },
                "frameTime": { "maxMs": 20.0, "warningMs": 25.0 },
                "memory": { "warningMB": 1024, "criticalMB": 2048 }
            }
        }"""

        val config = RulesEngine.parseRulesJson(json)
        assertEquals(1, config.rules.size)
        assertEquals("test_rule", config.rules[0].id)
        assertEquals(25, config.globalThresholds.fpsMin)
        assertEquals(1024, config.globalThresholds.memoryWarning)
        assertEquals(2048, config.globalThresholds.memoryCritical)
    }

    // ===== Evaluation Tests =====

    @Test
    fun `evaluate detects GC events matching pattern`() {
        val config = RulesEngine.loadRules()
        val events = listOf(
            LogEntry(1000, LogLevel.DEBUG, "art", "GC_CONCURRENT freed 5000K", "test"),
            LogEntry(2000, LogLevel.DEBUG, "art", "GC_FOR_MALLOC freed 2048K", "test")
        )

        val problems = RulesEngine.evaluate(events, config)
        val gcRule = problems.find { it.type.contains("GC_PRESSURE", ignoreCase = true) }
        assertNotNull(gcRule, "Should detect GC pressure: ${problems.map { it.type }}")
    }

    @Test
    fun `evaluate detects jank events matching pattern`() {
        val config = RulesEngine.loadRules()
        val events = listOf(
            LogEntry(1000, LogLevel.WARN, "Choreographer", "jank detected in frame rendering", "test")
        )

        val problems = RulesEngine.evaluate(events, config)
        val jankRule = problems.find { it.type.contains("JANK", ignoreCase = true) }
        assertNotNull(jankRule, "Should detect jank: ${problems.map { it.type }}")
    }

    @Test
    fun `evaluate returns empty for clean events`() {
        val config = RulesEngine.loadRules()
        val events = listOf(
            LogEntry(1000, LogLevel.INFO, "System", "Activity resumed com.test.game", "test"),
            LogEntry(2000, LogLevel.DEBUG, "View", "Layout requested", "test")
        )

        val problems = RulesEngine.evaluate(events, config)
        assertTrue(problems.isEmpty(), "Clean events should not trigger rules: ${problems.map { it.type }}")
    }

    @Test
    fun `evaluate with custom thresholds respects severity mapping`() {
        val rule = RulesEngine.Rule(
            id = "custom_test",
            name = "Custom Test",
            description = "Test rule",
            metric = "FRAME_TIME",
            condition = "pattern_match",
            threshold = 0.0,
            severity = "ERROR",
            pattern = "custom_error_pattern"
        )
        val config = RulesEngine.RulesConfig(
            rules = listOf(rule),
            globalThresholds = RulesEngine.loadRules().globalThresholds
        )
        val events = listOf(
            LogEntry(1000, LogLevel.ERROR, "Test", "custom_error_pattern detected", "test")
        )

        val problems = RulesEngine.evaluate(events, config)
        assertEquals(1, problems.size)
        assertEquals(Severity.HIGH, problems[0].severity, "ERROR severity should map to HIGH")
    }

    @Test
    fun `evaluate ignores rules with empty pattern`() {
        val rule = RulesEngine.Rule(
            id = "no_pattern",
            name = "No Pattern",
            description = "Rule without pattern",
            metric = "FPS",
            condition = "below_threshold",
            threshold = 30.0,
            severity = "ERROR",
            pattern = ""
        )
        val config = RulesEngine.RulesConfig(
            rules = listOf(rule),
            globalThresholds = RulesEngine.loadRules().globalThresholds
        )
        val events = listOf(
            LogEntry(1000, LogLevel.INFO, "Test", "anything", "test")
        )

        val problems = RulesEngine.evaluate(events, config)
        assertTrue(problems.isEmpty(), "Rules without pattern should be skipped")
    }
}
