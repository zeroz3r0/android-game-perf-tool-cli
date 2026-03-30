package com.gameperf.analysis

import com.gameperf.core.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProblemDetectorTest {

    private fun makeSessionData(
        samples: List<SessionSample> = emptyList(),
        memStart: MemoryInfo? = null,
        memEnd: MemoryInfo? = null,
        missedStart: Int = 0,
        missedEnd: Int = 0
    ) = SessionData(
        gamePackage = "com.test.game",
        deviceSpecs = DeviceSpecs("Test", "Test", 30, "Test", 4_000_000_000L, "1080x1920", 8, "Test"),
        samples = samples.toMutableList(),
        events = mutableListOf(),
        configChanges = mutableListOf(),
        batteryStart = null,
        batteryEnd = null,
        memStart = memStart,
        memEnd = memEnd,
        missedFramesStart = missedStart,
        missedFramesEnd = missedEnd,
        startTime = 0L,
        endTime = 60_000L,
        initialResolution = null
    )

    @Test
    fun `detects low FPS below 30`() {
        val fps = PercentileStats(p1 = 20.0, p5 = 22.0, p50 = 25.0, p90 = 28.0, p95 = 28.0, p99 = 29.0, min = 20.0, max = 29.0, avg = 25.0)
        val problems = ProblemDetector.detect(makeSessionData(), fps, 0, 0, 0)
        assertTrue(problems.any { it.type == "LOW_FPS" && it.severity == Severity.HIGH })
    }

    @Test
    fun `detects medium FPS between 30 and 45`() {
        val fps = PercentileStats(p1 = 30.0, p5 = 33.0, p50 = 38.0, p90 = 42.0, p95 = 43.0, p99 = 44.0, min = 30.0, max = 44.0, avg = 38.0)
        val problems = ProblemDetector.detect(makeSessionData(), fps, 0, 0, 0)
        assertTrue(problems.any { it.type == "LOW_FPS" && it.severity == Severity.MEDIUM })
    }

    @Test
    fun `no FPS problem above 45`() {
        val fps = PercentileStats(p1 = 50.0, p5 = 55.0, p50 = 60.0, p90 = 60.0, p95 = 60.0, p99 = 60.0, min = 50.0, max = 60.0, avg = 58.0)
        val problems = ProblemDetector.detect(makeSessionData(), fps, 0, 0, 0)
        assertTrue(problems.none { it.type == "LOW_FPS" })
    }

    @Test
    fun `detects frame drops above 30`() {
        val data = makeSessionData(missedStart = 0, missedEnd = 50)
        val problems = ProblemDetector.detect(data, null, 0, 0, 0)
        assertTrue(problems.any { it.type == "FRAME_DROPS" })
    }

    @Test
    fun `detects high memory above 2000MB`() {
        val mem = MemoryInfo(totalPssKb = 2100 * 1024L, nativeHeapKb = 1000 * 1024L, javaHeapKb = 500 * 1024L, timestamp = 0)
        val sample = SessionSample(0, 60, emptyList(), mem, null, null, null, null)
        val data = makeSessionData(samples = listOf(sample))
        val problems = ProblemDetector.detect(data, null, 0, 0, 0)
        assertTrue(problems.any { it.type == "HIGH_MEMORY" && it.severity == Severity.HIGH })
    }

    @Test
    fun `detects memory leak above 500MB growth`() {
        val memStart = MemoryInfo(totalPssKb = 500 * 1024L, nativeHeapKb = 200 * 1024L, javaHeapKb = 100 * 1024L, timestamp = 0)
        val memEnd = MemoryInfo(totalPssKb = 1100 * 1024L, nativeHeapKb = 600 * 1024L, javaHeapKb = 200 * 1024L, timestamp = 0)
        val data = makeSessionData(memStart = memStart, memEnd = memEnd)
        val problems = ProblemDetector.detect(data, null, 0, 0, 0)
        assertTrue(problems.any { it.type == "MEMORY_LEAK" })
    }

    @Test
    fun `detects GC pressure above 10`() {
        val problems = ProblemDetector.detect(makeSessionData(), null, 15, 0, 0)
        assertTrue(problems.any { it.type == "GC_PRESSURE" })
    }

    @Test
    fun `detects CPU saturation above 85 percent`() {
        val cpuSnap = CpuSnapshot(totalUsage = 90.0, perCoreUsage = emptyList(), timestamp = 0)
        val sample = SessionSample(0, 60, emptyList(), null, cpuSnap, null, null, null)
        val data = makeSessionData(samples = listOf(sample))
        val problems = ProblemDetector.detect(data, null, 0, 0, 0)
        assertTrue(problems.any { it.type == "CPU_SATURATED" })
    }

    @Test
    fun `detects overheating above 45C`() {
        val thermal = ThermalSnapshot(cpuTemp = 50.0, gpuTemp = 45.0, batteryTemp = 35.0, skinTemp = 38.0, timestamp = 0)
        val sample = SessionSample(0, 60, emptyList(), null, null, null, thermal, null)
        val data = makeSessionData(samples = listOf(sample))
        val problems = ProblemDetector.detect(data, null, 0, 0, 0)
        assertTrue(problems.any { it.type == "OVERHEATING" })
    }

    @Test
    fun `no problems for perfect session`() {
        val fps = PercentileStats(p1 = 55.0, p5 = 58.0, p50 = 60.0, p90 = 60.0, p95 = 60.0, p99 = 60.0, min = 55.0, max = 60.0, avg = 59.0)
        val problems = ProblemDetector.detect(makeSessionData(), fps, 0, 0, 0)
        assertTrue(problems.isEmpty())
    }

    @Test
    fun `detects critical errors above 3`() {
        val problems = ProblemDetector.detect(makeSessionData(), null, 0, 0, 5)
        assertTrue(problems.any { it.type == "ERRORS" })
    }

    @Test
    fun `detects audio issues above 5`() {
        val problems = ProblemDetector.detect(makeSessionData(), null, 0, 8, 0)
        assertTrue(problems.any { it.type == "AUDIO_LAG" })
    }
}
