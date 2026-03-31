package com.gameperf.analysis

import com.gameperf.core.*
import com.gameperf.rules.RulesEngine
import com.gameperf.rules.RulesEngine.GlobalThresholds
import com.gameperf.rules.RulesEngine.RulesConfig

/**
 * Detects performance problems from session data and assigns
 * severity levels with user-facing explanations and solutions.
 *
 * Thresholds are loaded from [RulesConfig.globalThresholds] when provided,
 * or falls back to the defaults from `rules-default.json`.
 */
object ProblemDetector {

    /**
     * Convenience overload that loads default rules from classpath.
     * Mantiene compatibilidad con codigo existente.
     */
    fun detect(
        data: SessionData,
        fpsPercentiles: PercentileStats?,
        gcCount: Int,
        audioIssues: Int,
        errorCount: Int
    ): List<Problem> {
        val config = RulesEngine.loadRules()
        return detect(data, fpsPercentiles, gcCount, audioIssues, errorCount, config)
    }

    /**
     * Detects problems using thresholds from the provided [RulesConfig].
     * Evaluates both hardcoded heuristics (with configurable thresholds)
     * and pattern-match rules from the rules file.
     */
    fun detect(
        data: SessionData,
        fpsPercentiles: PercentileStats?,
        gcCount: Int,
        audioIssues: Int,
        errorCount: Int,
        rulesConfig: RulesConfig
    ): List<Problem> {
        val t = rulesConfig.globalThresholds
        val problems = mutableListOf<Problem>()
        val avgFps = fpsPercentiles?.avg ?: 0.0
        val totalDrops = data.totalFrameDrops
        val peakMem = data.samples.mapNotNull { it.memoryInfo?.totalMb }.maxOrNull() ?: 0L
        val memGrowth = (data.memEnd?.totalMb ?: 0) - (data.memStart?.totalMb ?: 0)
        val avgCpu = data.samples.mapNotNull { it.cpuSnapshot?.totalUsage }.average().let { if (it.isNaN()) 0.0 else it }
        val maxTemp = data.samples.mapNotNull { it.thermalSnapshot?.cpuTemp }.filter { it > 0 }.maxOrNull() ?: 0.0

        // FPS problems — thresholds from globalThresholds
        if (avgFps > 0 && avgFps < t.fpsMin) {
            problems.add(Problem(
                type = "LOW_FPS",
                severity = Severity.HIGH,
                description = "FPS promedio de ${avgFps.toInt()} - Muy bajo",
                explanation = "El juego renderiza menos de ${t.fpsMin} imagenes por segundo. El movimiento se ve entrecortado y la respuesta a los controles tiene retraso visible.",
                solution = "1) Bajar la calidad grafica en ajustes del juego. 2) Cerrar apps en segundo plano. 3) Activar modo rendimiento si disponible. 4) Desactivar efectos de post-procesado."
            ))
        } else if (avgFps in t.fpsMin.toDouble()..44.99) {
            problems.add(Problem(
                type = "LOW_FPS",
                severity = Severity.MEDIUM,
                description = "FPS promedio de ${avgFps.toInt()} - Bajo",
                explanation = "El juego va por debajo de 45 FPS. Se nota falta de fluidez en escenas con mucha accion.",
                solution = "1) Reducir resolucion del juego. 2) Bajar calidad de sombras y particulas. 3) Desactivar modo ahorro de bateria."
            ))
        }

        // FPS stability
        val stability = fpsPercentiles?.let {
            if (it.avg > 0) (1 - ((it.max - it.min) / it.avg / 2)).coerceIn(0.0, 1.0) * 100 else 100.0
        } ?: 100.0
        if (stability < 70) {
            problems.add(Problem(
                type = "UNSTABLE_FPS",
                severity = Severity.MEDIUM,
                description = "Estabilidad del ${stability.toInt()}%",
                explanation = "Los FPS varian mucho durante la partida causando tirones intermitentes.",
                solution = "1) Comprobar temperatura del dispositivo. 2) Liberar RAM cerrando apps. 3) Puede ser problema de optimizacion del juego."
            ))
        }

        // Frame drops
        if (totalDrops > 30) {
            problems.add(Problem(
                type = "FRAME_DROPS",
                severity = Severity.HIGH,
                description = "$totalDrops frames perdidos",
                explanation = "Se perdieron $totalDrops frames. Cada frame perdido es un tiron visible.",
                solution = "1) Bajar calidad grafica. 2) Si solo ocurre en ciertos momentos, puede ser problema del juego."
            ))
        }

        // Memory — thresholds from globalThresholds
        if (peakMem > t.memoryCritical) {
            problems.add(Problem(
                type = "HIGH_MEMORY",
                severity = Severity.HIGH,
                description = "Pico de $peakMem MB",
                explanation = "El juego uso $peakMem MB de RAM, superando el umbral critico de ${t.memoryCritical}MB.",
                solution = "1) Cerrar todas las apps antes de jugar. 2) Reiniciar el dispositivo. 3) Si tiene 4GB o menos de RAM, el juego es muy exigente."
            ))
        } else if (peakMem > t.memoryWarning) {
            problems.add(Problem(
                type = "HIGH_MEMORY",
                severity = Severity.MEDIUM,
                description = "Pico de $peakMem MB",
                explanation = "El juego usa bastante RAM (umbral: ${t.memoryWarning}MB). No critico pero cerca del limite en dispositivos con poca RAM.",
                solution = "1) Cerrar apps en segundo plano. 2) Evitar Chrome con muchas pestanas."
            ))
        }

        // Memory leak
        if (memGrowth > 500) {
            problems.add(Problem(
                type = "MEMORY_LEAK",
                severity = Severity.HIGH,
                description = "Crecimiento de $memGrowth MB",
                explanation = "La memoria crecio ${memGrowth}MB sin bajar. Posible memory leak del juego.",
                solution = "1) Es un BUG del juego. 2) Reiniciar el juego periodicamente. 3) Reportar al desarrollador."
            ))
        }

        // GC pressure
        if (gcCount > 10) {
            problems.add(Problem(
                type = "GC_PRESSURE",
                severity = Severity.MEDIUM,
                description = "$gcCount eventos de recolector de basura",
                explanation = "El recolector de basura se ejecuto $gcCount veces, causando micro-congelaciones.",
                solution = "1) Problema de optimizacion del juego. 2) Cerrar apps para reducir presion de memoria."
            ))
        }

        // Audio
        if (audioIssues > 5) {
            problems.add(Problem(
                type = "AUDIO_LAG",
                severity = Severity.MEDIUM,
                description = "$audioIssues problemas de audio",
                explanation = "Se detectaron $audioIssues underruns de audio. El sonido se corta o crepita.",
                solution = "1) Cerrar apps que usen audio (Spotify, YouTube). 2) Bajar calidad grafica para liberar CPU. 3) Probar sin Bluetooth."
            ))
        }

        // CPU saturation — threshold from globalThresholds
        if (avgCpu > t.cpuCritical) {
            problems.add(Problem(
                type = "CPU_SATURATED",
                severity = Severity.HIGH,
                description = "CPU al ${avgCpu.toInt()}%",
                explanation = "El procesador esta casi al maximo (umbral: ${t.cpuCritical}%). Es el cuello de botella principal del rendimiento.",
                solution = "1) Cerrar todas las apps. 2) Bajar calidad de IA/fisica en el juego si posible. 3) El dispositivo puede no ser suficiente."
            ))
        }

        // Overheating — threshold from globalThresholds
        if (maxTemp > t.tempCritical) {
            problems.add(Problem(
                type = "OVERHEATING",
                severity = Severity.HIGH,
                description = "Temperatura maxima: ${maxTemp.toInt()}C",
                explanation = "El dispositivo alcanzo ${maxTemp.toInt()}C. A partir de ~${t.tempWarning}C se activa el thermal throttling que reduce CPU/GPU.",
                solution = "1) Quitar funda del dispositivo. 2) No jugar mientras carga. 3) Jugar en ambiente fresco. 4) Hacer pausas de 5 min cada 15-20 min."
            ))
        }

        // Critical errors
        if (errorCount > 3) {
            problems.add(Problem(
                type = "ERRORS",
                severity = Severity.HIGH,
                description = "$errorCount errores criticos",
                explanation = "Se detectaron $errorCount errores graves que pueden indicar inestabilidad.",
                solution = "1) Actualizar el juego a la ultima version. 2) Borrar cache del juego. 3) Si crashea, reinstalar."
            ))
        }

        // Pattern-match rules from the rules file
        val ruleProblems = RulesEngine.evaluate(data.events, rulesConfig)
        problems.addAll(ruleProblems)

        return problems
    }
}
