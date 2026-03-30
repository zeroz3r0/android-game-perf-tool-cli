package com.gameperf.core

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PercentileStatsTest {

    @Test
    fun `fromValues returns null for empty list`() {
        assertNull(PercentileStats.fromValues(emptyList()))
    }

    @Test
    fun `fromValues returns correct stats for single value`() {
        val stats = PercentileStats.fromValues(listOf(60.0))
        assertNotNull(stats)
        assertEquals(60.0, stats.avg)
        assertEquals(60.0, stats.min)
        assertEquals(60.0, stats.max)
        assertEquals(60.0, stats.p50)
    }

    @Test
    fun `fromValues calculates correct percentiles for known distribution`() {
        // 100 values from 1 to 100
        val values = (1..100).map { it.toDouble() }
        val stats = PercentileStats.fromValues(values)
        assertNotNull(stats)

        assertEquals(1.0, stats.min)
        assertEquals(100.0, stats.max)
        assertEquals(50.5, stats.avg)
        // Percentile values depend on index calculation: (n * pct).toInt()
        // For n=100: p1 = sorted[1] = 2.0, p5 = sorted[5] = 6.0, etc.
        assertEquals(2.0, stats.p1)
        assertEquals(6.0, stats.p5)
        assertEquals(51.0, stats.p50)
        assertEquals(91.0, stats.p90)
        assertEquals(96.0, stats.p95)
        assertEquals(100.0, stats.p99)
    }

    @Test
    fun `fromIntValues converts correctly`() {
        val stats = PercentileStats.fromIntValues(listOf(30, 60, 45))
        assertNotNull(stats)
        assertEquals(45.0, stats.avg)
        assertEquals(30.0, stats.min)
        assertEquals(60.0, stats.max)
    }

    @Test
    fun `fromValues handles all same values`() {
        val stats = PercentileStats.fromValues(List(50) { 60.0 })
        assertNotNull(stats)
        assertEquals(60.0, stats.avg)
        assertEquals(60.0, stats.min)
        assertEquals(60.0, stats.max)
        assertEquals(60.0, stats.p1)
        assertEquals(60.0, stats.p99)
    }

    @Test
    fun `fromValues handles two values`() {
        val stats = PercentileStats.fromValues(listOf(10.0, 90.0))
        assertNotNull(stats)
        assertEquals(50.0, stats.avg)
        assertEquals(10.0, stats.min)
        assertEquals(90.0, stats.max)
    }
}
