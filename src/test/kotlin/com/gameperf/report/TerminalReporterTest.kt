package com.gameperf.report

import com.gameperf.config.AppConfig
import com.gameperf.core.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalReporterTest {

    private fun makeResult(
        grade: Char = 'B',
        fps: PercentileStats? = PercentileStats(
            p1 = 40.0, p5 = 45.0, p50 = 55.0, p90 = 59.0,
            p95 = 60.0, p99 = 60.0, min = 38.0, max = 60.0, avg = 55.0
        ),
        frameTimes: PercentileStats? = PercentileStats(
            p1 = 15.0, p5 = 15.5, p50 = 16.5, p90 = 18.0,
            p95 = 20.0, p99 = 25.0, min = 14.0, max = 30.0, avg = 17.0
        ),
        problems: List<Problem> = listOf(
            Problem("HIGH_MEMORY", Severity.MEDIUM, "Pico de 600 MB", "Explicacion larga", "Solucion larga")
        )
    ): AnalysisResult {
        val session = SessionData(
            gamePackage = "com.test.game",
            deviceSpecs = DeviceSpecs("Pixel 7", "Google", 33, "Tensor G2", 8_000_000_000L, "1080x2400", 8, "Adreno 730"),
            samples = mutableListOf(
                SessionSample(1000, 55, listOf(16.5, 17.0), MemoryInfo(600 * 1024, 300 * 1024, 100 * 1024, 1000), null, null, null, null),
                SessionSample(2000, 58, listOf(16.0, 16.5), MemoryInfo(610 * 1024, 310 * 1024, 105 * 1024, 2000), null, null, null, null)
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
            initialResolution = null
        )

        return AnalysisResult(
            session = session,
            fpsPercentiles = fps,
            frameTimePercentiles = frameTimes,
            problems = problems,
            relevantEvents = emptyList(),
            categorizedEvents = emptyList(),
            groupedErrors = emptyList(),
            groupedWarnings = emptyList(),
            fpsDrops = emptyList(),
            grade = grade,
            gcCount = 0,
            audioIssues = 0,
            thermalEvents = 0
        )
    }

    private fun captureOutput(block: () -> Unit): String {
        val baos = ByteArrayOutputStream()
        val original = System.out
        System.setOut(PrintStream(baos))
        try {
            block()
        } finally {
            System.setOut(original)
        }
        return baos.toString()
    }

    // ===== Quiet Mode Tests =====

    @Test
    fun `quiet mode prints grade`() {
        val output = captureOutput {
            TerminalReporter.print(makeResult(grade = 'A'), AppConfig(quiet = true))
        }
        assertTrue(output.contains("NOTA: A"), "Should print grade. Output: $output")
    }

    @Test
    fun `quiet mode prints FPS summary`() {
        val output = captureOutput {
            TerminalReporter.print(makeResult(), AppConfig(quiet = true))
        }
        assertTrue(output.contains("FPS:"), "Should print FPS line")
        assertTrue(output.contains("avg=55"), "Should print avg FPS")
        assertTrue(output.contains("P1=40"), "Should print P1")
        assertTrue(output.contains("P5=45"), "Should print P5")
    }

    @Test
    fun `quiet mode prints frame time summary`() {
        val output = captureOutput {
            TerminalReporter.print(makeResult(), AppConfig(quiet = true))
        }
        assertTrue(output.contains("Frame Time:"), "Should print frame time. Output: $output")
        assertTrue(output.contains("avg=") && output.contains("ms"), "Should print avg frame time. Output: $output")
        assertTrue(output.contains("P99=") && output.contains("ms"), "Should print P99 frame time. Output: $output")
    }

    @Test
    fun `quiet mode prints memory peak`() {
        val output = captureOutput {
            TerminalReporter.print(makeResult(), AppConfig(quiet = true))
        }
        assertTrue(output.contains("Memoria pico:"), "Should print memory peak")
    }

    @Test
    fun `quiet mode prints problems count`() {
        val output = captureOutput {
            TerminalReporter.print(makeResult(), AppConfig(quiet = true))
        }
        assertTrue(output.contains("Problemas: 1"), "Should print problem count. Output: $output")
    }

    @Test
    fun `quiet mode skips banner and detailed sections`() {
        val output = captureOutput {
            TerminalReporter.print(makeResult(), AppConfig(quiet = true))
        }
        assertFalse(output.contains("RESULTADOS"), "Should not print results header")
        assertFalse(output.contains("Bateria:"), "Should not print battery details")
        assertFalse(output.contains("CPU:"), "Should not print CPU details")
        assertFalse(output.contains("GPU:"), "Should not print GPU details")
        assertFalse(output.contains("Explicacion larga"), "Should not print problem explanations")
    }

    @Test
    fun `quiet mode handles null FPS`() {
        val output = captureOutput {
            TerminalReporter.print(makeResult(fps = null), AppConfig(quiet = true))
        }
        assertTrue(output.contains("FPS: sin datos"), "Should handle null FPS gracefully")
    }

    // ===== Full Mode Tests =====

    @Test
    fun `full mode prints all sections`() {
        val output = captureOutput {
            TerminalReporter.print(makeResult(), AppConfig(quiet = false))
        }
        assertTrue(output.contains("RESULTADOS"), "Should print results header")
        assertTrue(output.contains("FPS:"), "Should print FPS section")
        assertTrue(output.contains("Frame Times:"), "Should print frame times")
        assertTrue(output.contains("Memoria:"), "Should print memory section")
        assertTrue(output.contains("NOTA:"), "Should print grade")
    }

    @Test
    fun `full mode prints problem descriptions`() {
        val output = captureOutput {
            TerminalReporter.print(makeResult(), AppConfig(quiet = false))
        }
        assertTrue(output.contains("PROBLEMAS:"), "Should print problems header")
        assertTrue(output.contains("Pico de 600 MB"), "Should print problem description")
    }
}
