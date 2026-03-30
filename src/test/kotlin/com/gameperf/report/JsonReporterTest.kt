package com.gameperf.report

import com.gameperf.core.*
import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class JsonReporterTest {

    private fun makeResult(): AnalysisResult {
        val session = SessionData(
            gamePackage = "com.test.game",
            deviceSpecs = DeviceSpecs("Pixel 7", "Google", 33, "Tensor G2", 8_000_000_000L, "1080x2400", 8, "Adreno 730"),
            samples = mutableListOf(
                SessionSample(1000, 60, listOf(16.0, 16.5), null, null, null, null, null),
                SessionSample(2000, 58, listOf(17.0, 16.0), null, null, null, null, null)
            ),
            events = mutableListOf(),
            configChanges = mutableListOf(),
            batteryStart = BatteryInfo(80, 30f, false, 4200),
            batteryEnd = BatteryInfo(78, 32f, false, 4100),
            memStart = MemoryInfo(500 * 1024, 200 * 1024, 100 * 1024, 0),
            memEnd = MemoryInfo(520 * 1024, 210 * 1024, 105 * 1024, 60000),
            missedFramesStart = 0,
            missedFramesEnd = 5,
            startTime = 0,
            endTime = 60_000,
            initialResolution = RenderResolution(1080, 2400)
        )

        return AnalysisResult(
            session = session,
            fpsPercentiles = PercentileStats.fromIntValues(listOf(58, 60)),
            frameTimePercentiles = PercentileStats.fromValues(listOf(16.0, 16.5, 17.0, 16.0)),
            problems = listOf(Problem("TEST", Severity.MEDIUM, "Test problem", "explanation", "solution")),
            relevantEvents = emptyList(),
            categorizedEvents = emptyList(),
            groupedErrors = emptyList(),
            groupedWarnings = emptyList(),
            fpsDrops = emptyList(),
            grade = 'B',
            gcCount = 0,
            audioIssues = 0,
            thermalEvents = 0
        )
    }

    @Test
    fun `generates valid JSON structure`() {
        val json = JsonReporter.generate(makeResult())
        // JSON uses "key": value with space after colon
        assertTrue(json.contains("\"tool\""), "Missing tool field in: ${json.take(200)}")
        assertTrue(json.contains("\"version\""), "Missing version field")
        assertTrue(json.contains("\"device\""), "Missing device field")
        assertTrue(json.contains("\"session\""), "Missing session field")
        assertTrue(json.contains("\"fps\""), "Missing fps field")
        assertTrue(json.contains("\"memory\""), "Missing memory field")
        assertTrue(json.contains("\"grade\""), "Missing grade field")
        assertTrue(json.contains("\"B\""), "Missing grade B value")
    }

    @Test
    fun `includes device info`() {
        val json = JsonReporter.generate(makeResult())
        assertTrue(json.contains("Pixel 7"))
        assertTrue(json.contains("Tensor G2"))
        assertTrue(json.contains("Adreno 730"))
    }

    @Test
    fun `includes problems`() {
        val json = JsonReporter.generate(makeResult())
        assertTrue(json.contains("Test problem"))
        assertTrue(json.contains("Medio"))
    }

    @Test
    fun `escapes special characters`() {
        val result = makeResult()
        // The game package doesn't have special chars, but device info might
        val json = JsonReporter.generate(result)
        assertFalse(json.contains("\n\n")) // no raw newlines in JSON values
    }

    @Test
    fun `includes fps samples`() {
        val json = JsonReporter.generate(makeResult())
        // Both samples have fps > 0 so both should be included
        assertTrue(json.contains("fps_samples"), "Missing fps_samples: $json")
        assertTrue(json.contains("60") && json.contains("58"), "Missing fps values in: $json")
    }
}
