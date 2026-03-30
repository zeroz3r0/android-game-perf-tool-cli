package com.gameperf.analysis

import com.gameperf.core.PercentileStats
import com.gameperf.core.Problem
import com.gameperf.core.Severity
import org.junit.Test
import kotlin.test.assertEquals

class GradeCalculatorTest {

    @Test
    fun `perfect FPS no problems gives A`() {
        val fps = PercentileStats(p1 = 55.0, p5 = 58.0, p50 = 60.0, p90 = 60.0, p95 = 60.0, p99 = 60.0, min = 55.0, max = 60.0, avg = 59.0)
        assertEquals('A', GradeCalculator.calculate(fps, emptyList()))
    }

    @Test
    fun `good FPS with one medium problem gives A`() {
        // avg=55, >=55 -> fpsPenalty=0; p1=40, >=30 -> p1Penalty=0; 1 MEDIUM=6
        // 100 - 0 - 0 - 6 = 94 -> A
        val fps = PercentileStats(p1 = 40.0, p5 = 45.0, p50 = 55.0, p90 = 60.0, p95 = 60.0, p99 = 60.0, min = 40.0, max = 60.0, avg = 55.0)
        val problems = listOf(Problem("TEST", Severity.MEDIUM, "test", "test", "test"))
        assertEquals('A', GradeCalculator.calculate(fps, problems))
    }

    @Test
    fun `low FPS with multiple problems gives F`() {
        val fps = PercentileStats(p1 = 10.0, p5 = 15.0, p50 = 25.0, p90 = 28.0, p95 = 29.0, p99 = 29.0, min = 10.0, max = 29.0, avg = 24.0)
        val problems = listOf(
            Problem("LOW_FPS", Severity.HIGH, "test", "test", "test"),
            Problem("FRAME_DROPS", Severity.HIGH, "test", "test", "test"),
            Problem("OVERHEATING", Severity.HIGH, "test", "test", "test")
        )
        assertEquals('F', GradeCalculator.calculate(fps, problems))
    }

    @Test
    fun `null FPS gives maximum FPS penalty`() {
        val grade = GradeCalculator.calculate(null, emptyList())
        // 100 - 40 (no fps) - 0 (null p1, p1=0 < 20 -> 15) = 45 -> D
        // Wait: p1 = fpsPercentiles?.p1 ?: 0.0 = 0.0 < 20 -> p1Penalty=15
        // 100 - 40 - 15 = 45 -> D
        assertEquals('D', grade)
    }

    @Test
    fun `breakdown shows correct penalty values`() {
        val fps = PercentileStats(p1 = 15.0, p5 = 20.0, p50 = 40.0, p90 = 45.0, p95 = 45.0, p99 = 45.0, min = 15.0, max = 45.0, avg = 38.0)
        val problems = listOf(Problem("TEST", Severity.HIGH, "test", "test", "test"))
        val breakdown = GradeCalculator.breakdown(fps, problems)

        assertEquals(20, breakdown.fpsPenalty)  // avg 38, between 35-45 -> 20
        assertEquals(15, breakdown.p1Penalty)   // p1=15, < 20 -> 15
        assertEquals(12, breakdown.problemPenalty) // 1 HIGH = 12
        // 100 - 20 - 15 - 12 = 53 -> D
        assertEquals('D', breakdown.grade)
    }

    @Test
    fun `medium FPS gives correct penalty`() {
        val fps = PercentileStats(p1 = 35.0, p5 = 40.0, p50 = 48.0, p90 = 50.0, p95 = 50.0, p99 = 50.0, min = 35.0, max = 50.0, avg = 47.0)
        val breakdown = GradeCalculator.breakdown(fps, emptyList())
        assertEquals(10, breakdown.fpsPenalty) // avg 47, between 45-55 -> 10
        assertEquals(0, breakdown.p1Penalty)   // p1=35, >= 30 -> 0
    }

    @Test
    fun `score never goes below zero`() {
        val fps = PercentileStats(p1 = 5.0, p5 = 8.0, p50 = 15.0, p90 = 18.0, p95 = 19.0, p99 = 20.0, min = 5.0, max = 20.0, avg = 14.0)
        val problems = List(10) { Problem("TEST", Severity.HIGH, "test", "test", "test") }
        val breakdown = GradeCalculator.breakdown(fps, problems)
        assertEquals(0.0, breakdown.score)
    }
}
