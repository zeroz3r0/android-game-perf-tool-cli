package com.gameperf.analysis

import com.gameperf.core.*
import com.gameperf.i18n.Strings

/**
 * Categorizes raw log events into typed performance events
 * with impact assessment.
 */
object EventCategorizer {

    fun categorize(events: List<LogEntry>): List<CategorizedEvent> {
        return events.map { categorizeEntry(it) }
    }

    fun filterRelevant(events: List<LogEntry>): List<LogEntry> {
        return events.filter { isRelevant(it) }
    }

    private fun isRelevant(entry: LogEntry): Boolean {
        val msg = entry.message.lowercase()
        return msg.contains("jank") || msg.contains("hitch") || msg.contains("stutter") ||
            msg.contains("anr") || msg.contains("not responding") ||
            msg.contains("out of memory") || msg.contains("oom") ||
            (msg.contains("thermal") && (msg.contains("throttl") || msg.contains("cpu") || msg.contains("gpu"))) ||
            msg.contains("crash") || (msg.contains("fatal") && entry.level >= LogLevel.ERROR) ||
            msg.contains("underrun") ||
            Strings.GC_PATTERN.containsMatchIn(msg) ||
            msg.contains("trimmemory") || msg.contains("lowmemory") || msg.contains("low memory") ||
            msg.contains("surfaceflinger") ||
            (msg.contains("died") && msg.contains("process") && entry.level >= LogLevel.ERROR)
    }

    private fun categorizeEntry(entry: LogEntry): CategorizedEvent {
        val msg = entry.message.lowercase()

        val (category, description, impacts) = when {
            Strings.GC_PATTERN.containsMatchIn(msg) ->
                Triple(EventCategory.GC, "GC activo - puede causar micro-freeze de 1-50ms", true)

            msg.contains("underrun") ->
                Triple(EventCategory.AUDIO, "Audio underrun - buffer de audio vacio, sonido cortado", true)
            msg.contains("audiopolicy") || msg.contains("audioflinger") ->
                Triple(EventCategory.AUDIO, "Problema del sistema de audio", false)

            msg.contains("thermal") && (msg.contains("throttl") || msg.contains("cpu") || msg.contains("gpu")) ->
                Triple(EventCategory.THERMAL, "THERMAL THROTTLING - CPU/GPU reducidos por calor", true)
            msg.contains("thermal") ->
                Triple(EventCategory.THERMAL, "Evento termico detectado", false)

            msg.contains("oom") || msg.contains("out of memory") ->
                Triple(EventCategory.MEMORY, "SIN MEMORIA - riesgo de crash", true)
            msg.contains("lowmemory") || msg.contains("low memory") ->
                Triple(EventCategory.MEMORY, "Memoria baja - sistema cerrando apps", true)
            msg.contains("trimmemory") ->
                Triple(EventCategory.MEMORY, "Sistema liberando memoria - posible lag temporal", true)

            msg.contains("jank") || msg.contains("hitch") || msg.contains("stutter") ->
                Triple(EventCategory.JANK, "JANK - frames perdidos visiblemente", true)

            msg.contains("anr") || msg.contains("not responding") ->
                Triple(EventCategory.ANR, "APP COLGADA - no responde por mas de 5s", true)

            msg.contains("crash") || (msg.contains("fatal") && entry.level >= LogLevel.ERROR) ->
                Triple(EventCategory.CRASH, "ERROR FATAL - puede cerrar el juego", true)
            msg.contains("died") && msg.contains("process") ->
                Triple(EventCategory.CRASH, "Proceso terminado inesperadamente", true)

            msg.contains("timeout") && (msg.contains("socket") || msg.contains("connect")) ->
                Triple(EventCategory.NETWORK, "Timeout de red - lag en juegos online", true)

            msg.contains("surfaceflinger") ->
                Triple(EventCategory.GRAPHICS, "Error del compositor grafico", true)
            msg.contains("hwc") || msg.contains("hwcomposer") ->
                Triple(EventCategory.GRAPHICS, "Error del Hardware Composer", true)

            else -> Triple(EventCategory.OTHER, "Evento del sistema", false)
        }

        return CategorizedEvent(entry = entry, category = category, description = description, impactsPerformance = impacts)
    }
}
