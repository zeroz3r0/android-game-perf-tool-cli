package com.gameperf.report

import com.gameperf.config.AppConfig
import com.gameperf.core.*
import com.gameperf.i18n.Strings

/**
 * Prints analysis results to the terminal in a readable format.
 */
object TerminalReporter {

    fun print(result: AnalysisResult, @Suppress("UNUSED_PARAMETER") config: AppConfig) {
        val s = result.session
        val dur = s.durationSeconds
        println(Strings.resultsHeader(dur / 60, dur % 60))

        printFps(result.fpsPercentiles)
        printFrameTimes(result.frameTimePercentiles, s)
        printMemory(s)
        printCpuGpu(s)
        printTemperature(s)
        printBattery(s)
        printEvents(result)
        printProblems(result.problems)
        printGrade(result.grade)
    }

    private fun printFps(fp: PercentileStats?) {
        println("\nFPS:")
        if (fp != null) {
            println("  Avg: ${fp.avg.toInt()} | Min: ${fp.min.toInt()} | Max: ${fp.max.toInt()}")
            println("  P1: ${fp.p1.toInt()} | P5: ${fp.p5.toInt()} | P50: ${fp.p50.toInt()} | P90: ${fp.p90.toInt()} | P99: ${fp.p99.toInt()}")
        } else {
            println("  Sin datos de FPS")
        }
    }

    private fun printFrameTimes(ft: PercentileStats?, session: SessionData) {
        if (ft == null) return
        println("\nFrame Times:")
        println("  Avg: ${"%.1f".format(ft.avg)}ms | P50: ${"%.1f".format(ft.p50)}ms | P99: ${"%.1f".format(ft.p99)}ms")
        val jank = session.samples.sumOf { it.frameTimes.count { t -> t > 16.67 } }
        val stutter = session.samples.sumOf { it.frameTimes.count { t -> t > 100.0 } }
        println("  Jank (>16ms): $jank | Stutter (>100ms): $stutter")
    }

    private fun printMemory(s: SessionData) {
        println("\nMemoria:")
        val memPeak = s.samples.mapNotNull { it.memoryInfo }.maxByOrNull { it.totalPssKb }
        if (memPeak != null) {
            println("  Total: ${memPeak.totalMb}MB | Native: ${memPeak.nativeHeapMb}MB | Java: ${memPeak.javaHeapMb}MB")
            println("  Inicio: ${s.memStart?.totalMb ?: "?"}MB | Final: ${s.memEnd?.totalMb ?: "?"}MB")
        }
    }

    private fun printCpuGpu(s: SessionData) {
        val avgCpu = s.samples.mapNotNull { it.cpuSnapshot?.totalUsage }.average()
            .let { if (it.isNaN()) -1.0 else it }
        val maxCpu = s.samples.mapNotNull { it.cpuSnapshot?.totalUsage }.maxOrNull() ?: -1.0
        val avgGpu = s.samples.mapNotNull { it.gpuSnapshot }.filter { it.isAvailable }
            .map { it.usage }.average().let { if (it.isNaN()) -1.0 else it }

        println("\nCPU: ${if (avgCpu >= 0) "avg ${avgCpu.toInt()}% / max ${maxCpu.toInt()}%" else "no disponible"}")
        println("GPU: ${if (avgGpu >= 0) "avg ${avgGpu.toInt()}%" else "no disponible"}")
    }

    private fun printTemperature(s: SessionData) {
        val maxTemp = s.samples.mapNotNull { it.thermalSnapshot?.cpuTemp }
            .filter { it > 0 }.maxOrNull()
        if (maxTemp != null) println("Temp CPU max: ${maxTemp.toInt()}C")
    }

    private fun printBattery(s: SessionData) {
        val drain = (s.batteryStart?.level ?: 0) - (s.batteryEnd?.level ?: 0)
        println("\nBateria: ${s.batteryStart?.level ?: "?"}% -> ${s.batteryEnd?.level ?: "?"}% (consumo: ${drain}%)")
        println("Frame drops: ${s.totalFrameDrops}")
    }

    private fun printEvents(result: AnalysisResult) {
        val catSummary = result.categorizedEvents.groupBy { it.category }
            .mapValues { it.value.size }
            .toList().sortedByDescending { it.second }

        if (catSummary.isEmpty()) return

        val impactTotal = result.categorizedEvents.count { it.impactsPerformance }
        println("\nEventos de rendimiento: ${result.categorizedEvents.size} total, $impactTotal afectan rendimiento")
        for ((cat, count) in catSummary) {
            val impact = result.categorizedEvents.count { it.category == cat && it.impactsPerformance }
            println("  ${cat.label}: $count${if (impact > 0) " ($impact criticos)" else ""}")
        }
    }

    private fun printProblems(problems: List<Problem>) {
        if (problems.isEmpty()) return
        println("\nPROBLEMAS:")
        for (p in problems) println("  [${p.severity.label}] ${p.description}")
    }

    private fun printGrade(grade: Char) {
        println("\nNOTA: $grade")
    }
}
