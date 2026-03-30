package com.gameperf.analysis

import com.gameperf.core.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionAnalyzerTest {

    private fun makeSession(
        fpsList: List<Int> = listOf(60, 58, 55, 59, 60, 57, 60, 58, 56, 60),
        frameTimes: List<Double> = listOf(16.0, 17.0, 18.0, 16.5, 16.0, 17.5, 16.0, 17.0, 18.5, 16.0),
        events: List<LogEntry> = emptyList(),
        memStart: MemoryInfo? = MemoryInfo(500 * 1024, 200 * 1024, 100 * 1024, 0),
        memEnd: MemoryInfo? = MemoryInfo(520 * 1024, 210 * 1024, 105 * 1024, 0),
        missedStart: Int = 0,
        missedEnd: Int = 0
    ): SessionData {
        val samples = fpsList.mapIndexed { i, fps ->
            SessionSample(
                timestamp = i * 1000L,
                fps = fps,
                frameTimes = frameTimes,
                memoryInfo = MemoryInfo(500 * 1024L + i * 1024, 200 * 1024, 100 * 1024, i * 1000L),
                cpuSnapshot = CpuSnapshot(35.0, listOf(30.0, 40.0, 35.0, 35.0), i * 1000L),
                gpuSnapshot = GpuSnapshot(45.0, i * 1000L),
                thermalSnapshot = ThermalSnapshot(38.0, 35.0, 30.0, 32.0, i * 1000L),
                renderResolution = RenderResolution(1080, 2400)
            )
        }

        return SessionData(
            gamePackage = "com.test.game",
            deviceSpecs = DeviceSpecs("Pixel 7", "Google", 33, "Tensor G2", 8_000_000_000L, "1080x2400", 8, "Adreno 730"),
            samples = samples.toMutableList(),
            events = events.toMutableList(),
            configChanges = mutableListOf(),
            batteryStart = BatteryInfo(80, 30f, false, 4200),
            batteryEnd = BatteryInfo(78, 32f, false, 4100),
            memStart = memStart,
            memEnd = memEnd,
            missedFramesStart = missedStart,
            missedFramesEnd = missedEnd,
            startTime = 0L,
            endTime = fpsList.size * 1000L,
            initialResolution = RenderResolution(1080, 2400)
        )
    }

    @Test
    fun `analyze produces complete result for good session`() {
        val result = SessionAnalyzer.analyze(makeSession())

        assertNotNull(result.fpsPercentiles)
        assertNotNull(result.frameTimePercentiles)
        assertTrue(result.problems.isEmpty(), "Good session should have no problems: ${result.problems}")
        assertEquals('A', result.grade)
        assertEquals(0, result.gcCount)
    }

    @Test
    fun `analyze detects problems in bad session`() {
        val result = SessionAnalyzer.analyze(makeSession(
            fpsList = listOf(20, 18, 15, 22, 19, 12, 25, 18, 10, 20),
            missedEnd = 50
        ))

        assertTrue(result.problems.isNotEmpty(), "Should have problems: ${result.problems}")
        assertTrue(result.problems.any { it.type == "LOW_FPS" }, "Should detect low FPS: ${result.problems.map { it.type }}")
        assertTrue(result.problems.any { it.type == "FRAME_DROPS" }, "Should detect frame drops: ${result.problems.map { it.type }}")
        assertTrue(result.grade in listOf('D', 'F'), "Grade should be D or F: ${result.grade}")
    }

    @Test
    fun `analyze categorizes GC events`() {
        val events = listOf(
            LogEntry(1000, LogLevel.DEBUG, "art", "GC freed 5000 objects", "test"),
            LogEntry(2000, LogLevel.DEBUG, "art", "GC freed 3000 objects", "test"),
            LogEntry(3000, LogLevel.DEBUG, "art", "GC freed 2000 objects", "test")
        )
        val result = SessionAnalyzer.analyze(makeSession(events = events))

        assertEquals(3, result.gcCount)
        assertTrue(result.categorizedEvents.any { it.category == EventCategory.GC })
    }

    @Test
    fun `analyze groups repeated errors`() {
        // Events need to be relevant (contain keywords like crash, fatal, etc.) to pass filterRelevant
        val events = (1..5).map { i ->
            LogEntry(i * 1000L, LogLevel.ERROR, "Unity", "FATAL crash NullReferenceException at GameManager", "test")
        }
        val result = SessionAnalyzer.analyze(makeSession(events = events))

        assertTrue(result.groupedErrors.isNotEmpty(), "Should have grouped errors. Relevant: ${result.relevantEvents.size}, categorized: ${result.categorizedEvents.size}")
    }

    @Test
    fun `analyze handles empty samples`() {
        val session = makeSession(fpsList = emptyList(), frameTimes = emptyList())
        val result = SessionAnalyzer.analyze(session)

        // Should not crash
        assertEquals(null, result.fpsPercentiles)
        assertNotNull(result.grade) // Should still produce a grade
    }

    @Test
    fun `analyze detects FPS drops correlated with events`() {
        val fpsList = listOf(60, 60, 60, 15, 60, 60, 60, 60, 60, 60)
        val events = listOf(
            LogEntry(3000, LogLevel.WARN, "thermal", "thermal throttling CPU frequency", "test")
        )
        val result = SessionAnalyzer.analyze(makeSession(fpsList = fpsList, events = events))

        assertTrue(result.fpsDrops.isNotEmpty(), "Should detect FPS drop")
    }
}
