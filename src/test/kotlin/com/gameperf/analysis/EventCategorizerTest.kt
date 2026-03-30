package com.gameperf.analysis

import com.gameperf.core.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventCategorizerTest {

    private fun makeEntry(message: String, level: LogLevel = LogLevel.INFO) =
        LogEntry(timestamp = 0, level = level, tag = "Test", message = message, deviceId = "test")

    @Test
    fun `categorizes GC events`() {
        val events = listOf(
            makeEntry("concurrent mark sweep freed 5000 objects"),
            makeEntry("GC freed 2048K"),
            makeEntry("Clamp GC for background")
        )
        val categorized = EventCategorizer.categorize(events)
        assertTrue(categorized.all { it.category == EventCategory.GC }, "All should be GC: ${categorized.map { "${it.category}: ${it.entry.message}" }}")
        assertTrue(categorized.all { it.impactsPerformance })
    }

    @Test
    fun `categorizes audio underrun as impactful`() {
        val events = listOf(makeEntry("AudioTrack: underrun, framesReady(0) < framesDesired(1024)"))
        val categorized = EventCategorizer.categorize(events)
        assertEquals(EventCategory.AUDIO, categorized.first().category)
        assertTrue(categorized.first().impactsPerformance)
    }

    @Test
    fun `categorizes thermal throttling as impactful`() {
        val events = listOf(makeEntry("thermal throttling activated, reducing CPU frequency"))
        val categorized = EventCategorizer.categorize(events)
        assertEquals(EventCategory.THERMAL, categorized.first().category)
        assertTrue(categorized.first().impactsPerformance)
    }

    @Test
    fun `categorizes OOM as memory critical`() {
        val events = listOf(makeEntry("Out of memory: Failed to allocate 50MB"))
        val categorized = EventCategorizer.categorize(events)
        assertEquals(EventCategory.MEMORY, categorized.first().category, "Got: ${categorized.first().category} for: ${categorized.first().entry.message}")
        assertTrue(categorized.first().impactsPerformance)
    }

    @Test
    fun `categorizes ANR correctly`() {
        val events = listOf(makeEntry("Application Not Responding: com.test.game"))
        val categorized = EventCategorizer.categorize(events)
        assertEquals(EventCategory.ANR, categorized.first().category)
        assertTrue(categorized.first().impactsPerformance)
    }

    @Test
    fun `categorizes crash as impactful`() {
        val events = listOf(makeEntry("FATAL EXCEPTION: main", LogLevel.ERROR))
        val categorized = EventCategorizer.categorize(events)
        assertEquals(EventCategory.CRASH, categorized.first().category)
        assertTrue(categorized.first().impactsPerformance)
    }

    @Test
    fun `categorizes jank events`() {
        val events = listOf(makeEntry("Choreographer: Skipped 45 frames! jank detected"))
        val categorized = EventCategorizer.categorize(events)
        assertEquals(EventCategory.JANK, categorized.first().category)
    }

    @Test
    fun `categorizes network timeout`() {
        val events = listOf(makeEntry("Socket connect timeout after 30000ms"))
        val categorized = EventCategorizer.categorize(events)
        assertEquals(EventCategory.NETWORK, categorized.first().category)
    }

    @Test
    fun `categorizes surfaceflinger as graphics`() {
        val events = listOf(makeEntry("SurfaceFlinger: buffer not ready"))
        val categorized = EventCategorizer.categorize(events)
        assertEquals(EventCategory.GRAPHICS, categorized.first().category)
    }

    @Test
    fun `filterRelevant removes non-performance events`() {
        val events = listOf(
            makeEntry("GC freed 5000 objects"),       // contains gc -> relevant
            makeEntry("Activity started"),             // not relevant
            makeEntry("thermal throttling CPU"),       // thermal + throttl -> relevant
            makeEntry("Loading texture atlas"),        // not relevant
            makeEntry("out of memory crash")           // out of memory -> relevant
        )
        val filtered = EventCategorizer.filterRelevant(events)
        assertEquals(3, filtered.size, "Filtered: ${filtered.map { it.message }}")
    }

    @Test
    fun `low memory and trimmemory are relevant`() {
        val events = listOf(
            makeEntry("lowmemory detected"),
            makeEntry("onTrimMemory RUNNING_LOW")
        )
        val filtered = EventCategorizer.filterRelevant(events)
        assertEquals(2, filtered.size)
    }
}
