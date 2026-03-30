package com.gameperf.report

import com.gameperf.core.*
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Generates a JSON performance report from analysis results.
 */
object JsonReporter {

    fun generate(result: AnalysisResult): String {
        val s = result.session
        val fp = result.fpsPercentiles
        val ft = result.frameTimePercentiles
        val memPeak = s.samples.mapNotNull { it.memoryInfo }.maxByOrNull { it.totalPssKb }
        val avgCpu = s.samples.mapNotNull { it.cpuSnapshot?.totalUsage }.average()
            .let { if (it.isNaN()) null else it }
        val batteryDrain = (s.batteryStart?.level ?: 0) - (s.batteryEnd?.level ?: 0)

        return """{
  "tool": "Android Game Performance Tool",
  "version": "${com.gameperf.config.AppConfig.APP_VERSION}",
  "device": {
    "model": "${esc(s.deviceSpecs.model)}",
    "cpu": "${esc(s.deviceSpecs.cpu)}",
    "gpu": "${esc(s.deviceSpecs.gpuModel)}",
    "ram_gb": ${"%.1f".format(s.deviceSpecs.ramGb)},
    "cores": ${s.deviceSpecs.cores}
  },
  "session": {
    "game": "${esc(s.gamePackage)}",
    "duration_seconds": ${s.durationSeconds},
    "timestamp": "${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(Date(s.startTime))}",
    "total_samples": ${s.samples.size},
    "total_events": ${s.events.size}
  },
  "fps": ${fpsJson(fp)},
  "frame_times": ${frameTimesJson(ft)},
  "memory": {
    "start_mb": ${s.memStart?.totalMb ?: "null"},
    "end_mb": ${s.memEnd?.totalMb ?: "null"},
    "peak_mb": ${memPeak?.totalMb ?: "null"},
    "native_peak_mb": ${memPeak?.nativeHeapMb ?: "null"},
    "java_peak_mb": ${memPeak?.javaHeapMb ?: "null"}
  },
  "cpu_avg_percent": ${avgCpu?.toInt() ?: "null"},
  "battery": {
    "start": ${s.batteryStart?.level ?: "null"},
    "end": ${s.batteryEnd?.level ?: "null"},
    "drain": $batteryDrain
  },
  "frame_drops": ${s.totalFrameDrops},
  "problems": ${problemsJson(result.problems)},
  "grade": "${result.grade}",
  "fps_samples": [${s.samples.filter { it.fps > 0 }.joinToString(",") { it.fps.toString() }}]
}"""
    }

    private fun fpsJson(fp: PercentileStats?): String {
        if (fp == null) return "null"
        return """{"avg":${fp.avg.toInt()},"min":${fp.min.toInt()},"max":${fp.max.toInt()},"p1":${fp.p1.toInt()},"p5":${fp.p5.toInt()},"p50":${fp.p50.toInt()},"p90":${fp.p90.toInt()},"p99":${fp.p99.toInt()}}"""
    }

    private fun frameTimesJson(ft: PercentileStats?): String {
        if (ft == null) return "null"
        return """{"avg_ms":${"%.1f".format(ft.avg)},"p50_ms":${"%.1f".format(ft.p50)},"p90_ms":${"%.1f".format(ft.p90)},"p99_ms":${"%.1f".format(ft.p99)}}"""
    }

    private fun problemsJson(problems: List<Problem>): String {
        if (problems.isEmpty()) return "[]"
        return problems.joinToString(",", "[", "]") { p ->
            """{"type":"${esc(p.type)}","severity":"${esc(p.severity.label)}","description":"${esc(p.description)}"}"""
        }
    }

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
}
