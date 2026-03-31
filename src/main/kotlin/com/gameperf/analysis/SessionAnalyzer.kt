package com.gameperf.analysis

import com.gameperf.core.*
import com.gameperf.i18n.Strings
import com.gameperf.rules.RulesEngine
import com.gameperf.rules.RulesEngine.RulesConfig

/**
 * Orchestrates full analysis of a capture session.
 * Delegates to specialized analyzers for each concern.
 */
object SessionAnalyzer {

    /** Convenience overload that loads default rules. */
    fun analyze(data: SessionData): AnalysisResult {
        return analyze(data, RulesEngine.loadRules())
    }

    fun analyze(data: SessionData, rulesConfig: RulesConfig): AnalysisResult {
        val allFps = data.samples.filter { it.fps > 0 }.map { it.fps }
        val allFrameTimes = data.samples.flatMap { it.frameTimes }
        val fpsPercentiles = PercentileStats.fromIntValues(allFps)
        val frameTimePercentiles = PercentileStats.fromValues(allFrameTimes)

        // Filter and categorize events
        val relevantEvents = EventCategorizer.filterRelevant(data.events)
        val categorizedEvents = EventCategorizer.categorize(relevantEvents)

        // Count event types
        val gcCount = relevantEvents.count { Strings.GC_PATTERN.containsMatchIn(it.message) }
        val audioIssues = relevantEvents.count { it.message.contains("underrun", true) }
        val thermalEvents = relevantEvents.count {
            it.message.contains("thermal", true) && it.message.contains("throttl", true)
        }

        // Group repeated errors and warnings
        val errors = relevantEvents.filter { it.level >= LogLevel.ERROR }
        val warnings = relevantEvents.filter { it.level == LogLevel.WARN }

        val groupedErrors = errors.groupBy { it.message.take(60) }
            .filter { it.value.size >= 2 }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(8)

        val groupedWarnings = warnings.groupBy { it.message.take(60) }
            .filter { it.value.size >= 3 }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(8)

        // Detect FPS drops correlated with events
        val fpsDrops = detectFpsDrops(data, relevantEvents)

        // Detect problems
        val problems = ProblemDetector.detect(data, fpsPercentiles, gcCount, audioIssues, errors.size, rulesConfig)

        // Calculate grade
        val grade = GradeCalculator.calculate(fpsPercentiles, problems)

        return AnalysisResult(
            session = data,
            fpsPercentiles = fpsPercentiles,
            frameTimePercentiles = frameTimePercentiles,
            problems = problems,
            relevantEvents = relevantEvents,
            categorizedEvents = categorizedEvents,
            groupedErrors = groupedErrors,
            groupedWarnings = groupedWarnings,
            fpsDrops = fpsDrops,
            grade = grade,
            gcCount = gcCount,
            audioIssues = audioIssues,
            thermalEvents = thermalEvents
        )
    }

    private fun detectFpsDrops(data: SessionData, relevantEvents: List<LogEntry>): List<FpsDrop> {
        val fpsSamples = data.samples.filter { it.fps > 0 }
        val drops = mutableListOf<FpsDrop>()

        if (fpsSamples.size <= 4) return drops

        for (i in 2 until fpsSamples.size - 2) {
            val current = fpsSamples[i].fps
            val neighbors = listOf(
                fpsSamples[i - 2].fps, fpsSamples[i - 1].fps,
                fpsSamples[i + 1].fps, fpsSamples[i + 2].fps
            )
            val avgNeighbors = neighbors.average()

            if (avgNeighbors - current > 10) {
                val ts = fpsSamples[i].timestamp
                val nearbyEvent = relevantEvents
                    .filter { kotlin.math.abs(it.timestamp - ts) < 3000 }
                    .minByOrNull { kotlin.math.abs(it.timestamp - ts) }
                drops.add(FpsDrop(i, current, ts, nearbyEvent))
            }
        }

        return drops
    }
}
