package com.gameperf.report

import com.gameperf.config.AppConfig
import com.gameperf.core.*
import com.gameperf.i18n.Strings
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Generates a self-contained HTML performance report with
 * Chart.js graphs, CSS classes (no inline styles), and print support.
 */
object HtmlReporter {

    fun generate(result: AnalysisResult): String {
        val s = result.session
        val fp = result.fpsPercentiles
        val ft = result.frameTimePercentiles
        val dur = s.durationSeconds
        val durStr = "${dur / 60}m ${dur % 60}s"
        val gradeColor = gradeColor(result.grade)

        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang=\"es\">")
            appendHead(s, this)
            appendLine("<body>")
            appendLine("<div class=\"container\">")

            // Header
            appendHeader(s, result.grade, gradeColor, this)

            // Device card
            appendDeviceCard(s, this)

            // Session card
            appendSessionCard(s, durStr, this)

            // FPS card
            if (fp != null) appendFpsCard(fp, gradeColor, this)

            // Frame Times card
            if (ft != null) appendFrameTimesCard(ft, this)

            // Memory card
            appendMemoryCard(s, this)

            // CPU/GPU card
            appendCpuGpuCard(s, this)

            // Temperature card
            appendTemperatureCard(s, this)

            // Battery card
            appendBatteryCard(s, dur, this)

            // Graphics config card
            appendConfigCard(s, this)

            // Errors card
            appendErrorsCard(result, this)

            // Warnings card
            appendWarningsCard(result, this)

            // FPS drops card
            appendFpsDropsCard(result, s, this)

            // Event timeline card
            appendEventTimeline(result, s.startTime, this)

            // Problems card
            appendProblemsCard(result, this)

            // Grade breakdown card
            appendGradeBreakdownCard(result, fp, this)

            // Footer
            appendFooter(this)

            appendLine("</div>") // container

            // Charts script
            appendChartScripts(s, fp, ft, this)

            appendLine("</body>")
            appendLine("</html>")
        }
    }

    // ===== Head =====

    private fun appendHead(s: SessionData, sb: StringBuilder) {
        sb.appendLine("""<head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0">
<title>Informe - ${esc(s.gamePackage)}</title>
<script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
<script src="https://cdn.jsdelivr.net/npm/chartjs-plugin-annotation"></script>
<style>
${CSS}
</style>
</head>""")
    }

    // ===== Header =====

    private fun appendHeader(s: SessionData, grade: Char, gradeColor: String, sb: StringBuilder) {
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm").format(Date(s.startTime))
        sb.appendLine("""
<div class="header">
    <h1>Informe de Rendimiento</h1>
    <p class="subtitle">${esc(s.gamePackage)} | $dateStr</p>
    <div class="grade" style="color:$gradeColor;text-shadow:0 0 40px $gradeColor">$grade</div>
    <p class="subtitle">Nota de Rendimiento</p>
</div>""")
    }

    // ===== Device =====

    private fun appendDeviceCard(s: SessionData, sb: StringBuilder) {
        sb.appendLine("""
<div class="card">
    <h2>Dispositivo</h2>
    <div class="stat"><span class="stat-label">Modelo</span><span class="stat-value">${esc(s.deviceSpecs.model)}</span></div>
    <div class="stat"><span class="stat-label">CPU</span><span class="stat-value">${esc(s.deviceSpecs.cpu)}</span></div>
    <div class="stat"><span class="stat-label">GPU</span><span class="stat-value">${esc(s.deviceSpecs.gpuModel)}</span></div>
    <div class="stat"><span class="stat-label">RAM</span><span class="stat-value">${"%.1f".format(s.deviceSpecs.ramGb)} GB</span></div>
    <div class="stat"><span class="stat-label">Cores</span><span class="stat-value">${s.deviceSpecs.cores}</span></div>
    <div class="stat"><span class="stat-label">SDK</span><span class="stat-value">${s.deviceSpecs.sdkVersion}</span></div>
    <div class="stat"><span class="stat-label">Resolucion</span><span class="stat-value">${esc(s.deviceSpecs.resolution)}</span></div>
</div>""")
    }

    // ===== Session =====

    private fun appendSessionCard(s: SessionData, durStr: String, sb: StringBuilder) {
        val dropsClass = when {
            s.totalFrameDrops > 30 -> "bad"
            s.totalFrameDrops > 10 -> "warning"
            else -> "good"
        }
        sb.appendLine("""
<div class="card">
    <h2>Sesion de juego</h2>
    <div class="stat"><span class="stat-label">Duracion</span><span class="stat-value">$durStr</span></div>
    <div class="stat"><span class="stat-label">Lecturas FPS</span><span class="stat-value">${s.samples.count { it.fps > 0 }}</span></div>
    <div class="stat"><span class="stat-label">Eventos capturados</span><span class="stat-value">${s.events.size}</span></div>
    <div class="stat"><span class="stat-label">Frame drops</span><span class="stat-value $dropsClass">${s.totalFrameDrops}</span></div>
</div>""")
    }

    // ===== FPS =====

    private fun appendFpsCard(fp: PercentileStats, gradeColor: String, sb: StringBuilder) {
        sb.appendLine("""
<div class="card">
    <h2>FPS</h2>
    <table class="ptable">
        <tr><th>P1</th><th>P5</th><th>P50</th><th>P90</th><th>P99</th><th>Min</th><th>Max</th><th>Avg</th></tr>
        <tr>
            <td class="${fpsClass(fp.p1, 20.0, 30.0)}">${fp.p1.toInt()}</td>
            <td class="${fpsClass(fp.p5, 25.0, 35.0)}">${fp.p5.toInt()}</td>
            <td>${fp.p50.toInt()}</td><td>${fp.p90.toInt()}</td><td>${fp.p99.toInt()}</td>
            <td class="bad">${fp.min.toInt()}</td><td class="good">${fp.max.toInt()}</td>
            <td class="grade-cell" style="color:$gradeColor">${fp.avg.toInt()}</td>
        </tr>
    </table>
    <div class="chart-container"><canvas id="fpsChart"></canvas></div>
</div>""")
    }

    // ===== Frame Times =====

    private fun appendFrameTimesCard(ft: PercentileStats, sb: StringBuilder) {
        sb.appendLine("""
<div class="card">
    <h2>Frame Times</h2>
    <table class="ptable">
        <tr><th>P50</th><th>P90</th><th>P95</th><th>P99</th><th>Avg</th></tr>
        <tr>
            <td>${"%.1f".format(ft.p50)}ms</td>
            <td>${"%.1f".format(ft.p90)}ms</td>
            <td>${"%.1f".format(ft.p95)}ms</td>
            <td class="${fpsClass(50.0 - ft.p99, 0.0, 17.0)}">${"%.1f".format(ft.p99)}ms</td>
            <td>${"%.1f".format(ft.avg)}ms</td>
        </tr>
    </table>
    <div class="chart-container"><canvas id="ftChart"></canvas></div>
</div>""")
    }

    // ===== Memory =====

    private fun appendMemoryCard(s: SessionData, sb: StringBuilder) {
        val memPeak = s.samples.mapNotNull { it.memoryInfo }.maxByOrNull { it.totalPssKb }
        val peakClass = when {
            (memPeak?.totalMb ?: 0) > 2000 -> "bad"
            (memPeak?.totalMb ?: 0) > 1500 -> "warning"
            else -> "good"
        }
        sb.appendLine("""
<div class="card">
    <h2>Memoria</h2>
    <div class="stat"><span class="stat-label">Inicio</span><span class="stat-value">${s.memStart?.totalMb ?: "?"}MB (Native:${s.memStart?.nativeHeapMb ?: "?"}MB Java:${s.memStart?.javaHeapMb ?: "?"}MB)</span></div>
    <div class="stat"><span class="stat-label">Final</span><span class="stat-value">${s.memEnd?.totalMb ?: "?"}MB (Native:${s.memEnd?.nativeHeapMb ?: "?"}MB Java:${s.memEnd?.javaHeapMb ?: "?"}MB)</span></div>
    <div class="stat"><span class="stat-label">Pico</span><span class="stat-value $peakClass">${memPeak?.totalMb ?: "?"}MB</span></div>
    <div class="chart-container"><canvas id="memChart"></canvas></div>
</div>""")
    }

    // ===== CPU/GPU =====

    private fun appendCpuGpuCard(s: SessionData, sb: StringBuilder) {
        val avgCpu = s.samples.mapNotNull { it.cpuSnapshot?.totalUsage }.average()
            .let { if (it.isNaN()) -1.0 else it }
        val maxCpu = s.samples.mapNotNull { it.cpuSnapshot?.totalUsage }.maxOrNull() ?: -1.0
        val avgGpu = s.samples.mapNotNull { it.gpuSnapshot }.filter { it.isAvailable }
            .map { it.usage }.average().let { if (it.isNaN()) -1.0 else it }
        val cpuCores = s.samples.lastOrNull()?.cpuSnapshot?.perCoreUsage

        val cpuClass = when {
            avgCpu > 85 -> "bad"
            avgCpu > 70 -> "warning"
            else -> "good"
        }

        sb.appendLine("""
<div class="card">
    <h2>CPU y GPU</h2>
    <div class="stat"><span class="stat-label">CPU promedio</span><span class="stat-value $cpuClass">${if (avgCpu >= 0) "${avgCpu.toInt()}%" else "N/A"}</span></div>
    <div class="stat"><span class="stat-label">CPU maximo</span><span class="stat-value">${if (maxCpu >= 0) "${maxCpu.toInt()}%" else "N/A"}</span></div>
    <div class="stat"><span class="stat-label">GPU promedio</span><span class="stat-value">${if (avgGpu >= 0) "${avgGpu.toInt()}%" else "N/A"}</span></div>""")

        if (cpuCores != null && cpuCores.isNotEmpty()) {
            sb.appendLine("""    <div class="core-grid">""")
            cpuCores.forEachIndexed { i, usage ->
                val opacity = "%.2f".format((usage / 100).coerceIn(0.1, 1.0))
                sb.appendLine("""        <span class="core-badge" style="opacity:$opacity">C$i: ${usage.toInt()}%</span>""")
            }
            sb.appendLine("""    </div>""")
        }

        sb.appendLine("""    <div class="chart-container"><canvas id="cpuGpuChart"></canvas></div>
</div>""")
    }

    // ===== Temperature =====

    private fun appendTemperatureCard(s: SessionData, sb: StringBuilder) {
        val maxTemp = s.samples.mapNotNull { it.thermalSnapshot?.cpuTemp }
            .filter { it > 0 }.maxOrNull() ?: 0.0
        val tempClass = when {
            maxTemp > 45 -> "bad"
            maxTemp > 40 -> "warning"
            else -> "good"
        }
        sb.appendLine("""
<div class="card">
    <h2>Temperatura</h2>
    <div class="stat"><span class="stat-label">CPU max</span><span class="stat-value $tempClass">${if (maxTemp > 0) "${maxTemp.toInt()}C" else "N/A"}</span></div>
    <div class="chart-container"><canvas id="tempChart"></canvas></div>
</div>""")
    }

    // ===== Battery =====

    private fun appendBatteryCard(s: SessionData, dur: Int, sb: StringBuilder) {
        val drain = (s.batteryStart?.level ?: 0) - (s.batteryEnd?.level ?: 0)
        val drainPerMin = if (dur > 0) drain.toDouble() / (dur / 60.0) else 0.0
        val drainClass = when {
            drain > 10 -> "bad"
            drain > 5 -> "warning"
            else -> "good"
        }
        sb.appendLine("""
<div class="card">
    <h2>Bateria</h2>
    <div class="stat"><span class="stat-label">Inicio</span><span class="stat-value">${s.batteryStart?.level ?: "N/A"}%</span></div>
    <div class="stat"><span class="stat-label">Final</span><span class="stat-value">${s.batteryEnd?.level ?: "N/A"}%</span></div>
    <div class="stat"><span class="stat-label">Consumo</span><span class="stat-value $drainClass">${drain}% (${"%.1f".format(drainPerMin)}%/min)</span></div>
    <div class="stat"><span class="stat-label">Temperatura</span><span class="stat-value">${s.batteryEnd?.temperature ?: "N/A"}C</span></div>
</div>""")
    }

    // ===== Graphics Config =====

    private fun appendConfigCard(s: SessionData, sb: StringBuilder) {
        if (s.configChanges.isEmpty() && s.initialResolution == null) return
        sb.appendLine("""
<div class="card card-accent-purple">
    <h2>Configuracion grafica</h2>""")
        if (s.initialResolution != null) {
            sb.appendLine("""    <div class="stat"><span class="stat-label">Resolucion render</span><span class="stat-value">${s.initialResolution}</span></div>""")
        }
        for (ch in s.configChanges) {
            val t = ((ch.timestamp - s.startTime) / 1000)
            if (ch.resolutionChanged) {
                sb.appendLine("""    <div class="event-box event-purple">
        <strong class="text-purple">${if (ch.resolutionDecreased) "Bajada" else "Subida"} de resolucion a los ${t}s</strong>
        <div class="text-muted">${ch.previousResolution} -> ${ch.newResolution}</div>
        <div class="text-dim">FPS: ${ch.fpsBeforeChange} -> ${ch.fpsAfterChange}${if (ch.fpsImproved) " - Mejora confirmada" else ""}</div>
    </div>""")
            } else {
                sb.appendLine("""    <div class="event-box event-blue">
        <strong class="text-blue">Subida brusca FPS a los ${t}s</strong>
        <div class="text-muted">FPS: ${ch.fpsBeforeChange} -> ${ch.fpsAfterChange} (+${ch.fpsAfterChange - ch.fpsBeforeChange})</div>
        <div class="text-dim">Posible cambio de calidad grafica (texturas, sombras, efectos).</div>
    </div>""")
            }
        }
        sb.appendLine("</div>")
    }

    // ===== Errors =====

    private fun appendErrorsCard(result: AnalysisResult, sb: StringBuilder) {
        if (result.groupedErrors.isEmpty()) return
        sb.appendLine("""
<div class="card card-accent-red">
    <h2>Errores detectados</h2>
    <p class="text-dim card-desc">Errores repetidos durante la sesion con explicacion de su significado.</p>""")
        for ((msg, count) in result.groupedErrors) {
            val translated = esc(Strings.translateLog(msg, ""))
            val explanation = esc(Strings.explainError(msg))
            val msgShort = esc(if (msg.length > 100) msg.take(97) + "..." else msg)
            sb.appendLine("""    <div class="issue-box issue-red">
        <div class="issue-header">
            <span class="text-blue issue-title">$translated</span>
            <span class="badge badge-red">x$count</span>
        </div>
        <div class="issue-explanation"><strong>Que significa:</strong> $explanation</div>
        <div class="issue-log">Log: $msgShort</div>
    </div>""")
        }
        sb.appendLine("</div>")
    }

    // ===== Warnings =====

    private fun appendWarningsCard(result: AnalysisResult, sb: StringBuilder) {
        if (result.groupedWarnings.isEmpty()) return
        sb.appendLine("""
<div class="card card-accent-orange">
    <h2>Advertencias detectadas</h2>""")
        for ((msg, count) in result.groupedWarnings) {
            val translated = esc(Strings.translateLog(msg, ""))
            val explanation = esc(Strings.explainError(msg))
            val msgShort = esc(if (msg.length > 100) msg.take(97) + "..." else msg)
            sb.appendLine("""    <div class="issue-box issue-orange">
        <div class="issue-header">
            <span class="text-blue issue-title">$translated</span>
            <span class="badge badge-orange">x$count</span>
        </div>
        <div class="issue-explanation"><strong>Que significa:</strong> $explanation</div>
        <div class="issue-log">Log: $msgShort</div>
    </div>""")
        }
        sb.appendLine("</div>")
    }

    // ===== FPS Drops =====

    private fun appendFpsDropsCard(result: AnalysisResult, s: SessionData, sb: StringBuilder) {
        if (result.fpsDrops.isEmpty()) return
        sb.appendLine("""
<div class="card card-accent-orange-dark">
    <h2>Caidas de FPS (${result.fpsDrops.size})</h2>""")
        for (drop in result.fpsDrops.take(10)) {
            val t = ((drop.timestamp - s.startTime) / 1000)
            sb.appendLine("""    <div class="event-box event-orange">
        <span class="text-orange">${t}s</span> - <span class="bad">${drop.fps} FPS</span>""")
            if (drop.nearbyEvent != null) {
                sb.appendLine("""        <div class="text-blue drop-event">Evento: ${esc(Strings.translateLog(drop.nearbyEvent.message, drop.nearbyEvent.tag))}</div>""")
            }
            sb.appendLine("    </div>")
        }
        sb.appendLine("</div>")
    }

    // ===== Event Timeline =====

    private fun appendEventTimeline(result: AnalysisResult, startTime: Long, sb: StringBuilder) {
        val events = result.categorizedEvents
        if (events.isEmpty()) return

        val impactEvents = events.filter { it.impactsPerformance }
        val totalImpact = impactEvents.size

        // Category summary
        val byCat = events.groupBy { it.category }.mapValues { it.value.size }
            .toList().sortedByDescending { it.second }

        sb.appendLine("""
<div class="card card-accent-blue">
    <h2>Timeline de eventos de rendimiento</h2>
    <p class="text-dim card-desc">${events.size} eventos relevantes detectados, <strong class="text-orange">$totalImpact afectan directamente al rendimiento</strong>.</p>
    <div class="category-grid">""")
        for ((cat, count) in byCat) {
            val impactCount = events.count { it.category == cat && it.impactsPerformance }
            sb.appendLine("""        <span class="category-badge" style="border-left-color:${cat.color}"><span class="category-name" style="color:${cat.color}">${cat.label}</span> <strong>$count</strong>${if (impactCount > 0) " <span class=\"text-orange-sm\">($impactCount afectan)</span>" else ""}</span>""")
        }
        sb.appendLine("    </div>")

        // Timeline entries
        val timelineEvents = impactEvents
            .sortedBy { it.entry.timestamp }
            .distinctBy { "${it.category}_${it.entry.message.take(40)}_${it.entry.timestamp / 5000}" }
            .take(30)

        if (timelineEvents.isEmpty()) {
            sb.appendLine("""    <p class="text-dim">No se detectaron eventos que afecten directamente al rendimiento.</p>""")
        } else {
            sb.appendLine("""    <h3 class="timeline-title">Eventos que afectan al rendimiento (cronologico)</h3>
    <div class="timeline-scroll">""")
            for (ev in timelineEvents) {
                val t = ((ev.entry.timestamp - startTime) / 1000).let { if (it < 0) 0 else it }
                val levelIcon = when (ev.entry.level) {
                    LogLevel.ERROR, LogLevel.FATAL -> "E"
                    LogLevel.WARN -> "W"
                    else -> "I"
                }
                val levelClass = when (ev.entry.level) {
                    LogLevel.ERROR, LogLevel.FATAL -> "bad"
                    LogLevel.WARN -> "warning"
                    else -> "text-dim"
                }
                val msgShort = esc(if (ev.entry.message.length > 120) ev.entry.message.take(117) + "..." else ev.entry.message)
                sb.appendLine("""        <div class="timeline-entry" style="border-left-color:${ev.category.color}">
            <div class="timeline-time">
                <div class="timeline-seconds">${t}s</div>
                <div class="timeline-icon" style="color:${ev.category.color}">${ev.category.icon}</div>
            </div>
            <div class="timeline-content">
                <div class="timeline-desc" style="color:${ev.category.color}">${esc(ev.description)}</div>
                <div class="timeline-log">[${esc(ev.entry.tag)}] $msgShort</div>
            </div>
            <div class="timeline-level $levelClass">$levelIcon</div>
        </div>""")
            }
            sb.appendLine("    </div>")
        }
        sb.appendLine("</div>")
    }

    // ===== Problems =====

    private fun appendProblemsCard(result: AnalysisResult, sb: StringBuilder) {
        sb.appendLine("""
<div class="card">
    <h2>Problemas detectados</h2>""")
        if (result.problems.isEmpty()) {
            sb.appendLine("""    <p class="good">Sin problemas criticos detectados.</p>""")
        } else {
            for (p in result.problems) {
                sb.appendLine("""    <div class="problem-box" style="border-left-color:${p.severity.color}">
        <div class="problem-header">
            <strong style="color:${p.severity.color}">${esc(p.description)}</strong>
            <span class="badge" style="background:${p.severity.color}">Severidad: ${p.severity.label}</span>
        </div>
        <div class="problem-explanation"><strong class="text-blue">Que significa:</strong> ${esc(p.explanation)}</div>
        <div class="problem-solution">
            <strong class="text-blue">Solucion recomendada:</strong>
            <div class="solution-text">${esc(p.solution)}</div>
        </div>
    </div>""")
            }
        }
        sb.appendLine("</div>")
    }

    // ===== Grade Breakdown =====

    private fun appendGradeBreakdownCard(result: AnalysisResult, fp: PercentileStats?, sb: StringBuilder) {
        val breakdown = com.gameperf.analysis.GradeCalculator.breakdown(fp, result.problems)
        sb.appendLine("""
<div class="card">
    <h2>Desglose de la nota</h2>
    <p class="text-dim card-desc">Puntuacion base: 100 puntos. Se restan penalizaciones por bajo rendimiento y problemas detectados.</p>
    <div class="stat"><span class="stat-label">Penalizacion FPS</span><span class="stat-value bad">-${breakdown.fpsPenalty}</span></div>
    <div class="stat"><span class="stat-label">Penalizacion P1 (peor caso)</span><span class="stat-value bad">-${breakdown.p1Penalty}</span></div>
    <div class="stat"><span class="stat-label">Penalizacion problemas (${result.problems.size})</span><span class="stat-value bad">-${breakdown.problemPenalty}</span></div>
    <div class="stat"><span class="stat-label">Puntuacion final</span><span class="stat-value" style="color:${gradeColor(breakdown.grade)};font-size:1.3em">${"%.0f".format(breakdown.score)} / 100 = ${breakdown.grade}</span></div>
</div>""")
    }

    // ===== Footer =====

    private fun appendFooter(sb: StringBuilder) {
        val now = SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(Date())
        sb.appendLine("""
<footer class="footer">
    <p>${AppConfig.APP_NAME} v${AppConfig.APP_VERSION}</p>
    <p>Informe generado: $now</p>
</footer>""")
    }

    // ===== Chart Scripts =====

    private fun appendChartScripts(s: SessionData, @Suppress("UNUSED_PARAMETER") fp: PercentileStats?, @Suppress("UNUSED_PARAMETER") ft: PercentileStats?, sb: StringBuilder) {
        val fpsData = s.samples.filter { it.fps > 0 }.joinToString(",") { it.fps.toString() }
        val fpsLabels = s.samples.filter { it.fps > 0 }.joinToString(",") { "\"${((it.timestamp - s.startTime) / 1000)}s\"" }
        val memTotalData = s.samples.mapNotNull { it.memoryInfo?.totalMb }.joinToString(",")
        val memNativeData = s.samples.mapNotNull { it.memoryInfo?.nativeHeapMb }.joinToString(",")
        val memJavaData = s.samples.mapNotNull { it.memoryInfo?.javaHeapMb }.joinToString(",")
        val memLabels = s.samples.filter { it.memoryInfo != null }.joinToString(",") { "\"${((it.timestamp - s.startTime) / 1000)}s\"" }
        val cpuData = s.samples.mapNotNull { it.cpuSnapshot?.totalUsage?.toInt() }.joinToString(",")
        val gpuData = s.samples.map { it.gpuSnapshot?.let { g -> if (g.isAvailable) g.usage.toInt().toString() else "null" } ?: "null" }.joinToString(",")
        val cpuGpuLabels = s.samples.joinToString(",") { "\"${((it.timestamp - s.startTime) / 1000)}s\"" }
        val tempCpuData = s.samples.map { it.thermalSnapshot?.let { t -> if (t.hasCpuTemp) t.cpuTemp.toInt().toString() else "null" } ?: "null" }.joinToString(",")
        val tempGpuData = s.samples.map { it.thermalSnapshot?.let { t -> if (t.hasGpuTemp) t.gpuTemp.toInt().toString() else "null" } ?: "null" }.joinToString(",")
        val tempSkinData = s.samples.map { it.thermalSnapshot?.let { t -> if (t.hasSkinTemp) t.skinTemp.toInt().toString() else "null" } ?: "null" }.joinToString(",")

        val allFt = s.samples.flatMap { it.frameTimes }
        val ftBuckets = listOf(
            allFt.count { it < 8.0 },
            allFt.count { it >= 8.0 && it < 16.67 },
            allFt.count { it >= 16.67 && it < 33.33 },
            allFt.count { it >= 33.33 && it < 50.0 },
            allFt.count { it >= 50.0 && it < 100.0 },
            allFt.count { it >= 100.0 }
        ).joinToString(",")

        sb.appendLine("""
<script>
const D={responsive:true,maintainAspectRatio:false,plugins:{legend:{labels:{color:'#888'}}},scales:{x:{ticks:{color:'#666',maxTicksLimit:15},grid:{color:'rgba(255,255,255,0.05)'}},y:{ticks:{color:'#888'},grid:{color:'rgba(255,255,255,0.08)'}}}};
${if (fpsData.isNotEmpty()) """
new Chart(document.getElementById('fpsChart'),{type:'line',data:{labels:[$fpsLabels],datasets:[{label:'FPS',data:[$fpsData],borderColor:'#00d4ff',backgroundColor:'rgba(0,212,255,0.1)',fill:true,tension:0.3,pointRadius:1}]},options:{...D,plugins:{...D.plugins,annotation:{annotations:{line30:{type:'line',yMin:30,yMax:30,borderColor:'#ff0044',borderWidth:1,borderDash:[5,5],label:{content:'30 FPS',display:true,color:'#ff0044',font:{size:10}}},line60:{type:'line',yMin:60,yMax:60,borderColor:'#00ff88',borderWidth:1,borderDash:[5,5],label:{content:'60 FPS',display:true,color:'#00ff88',font:{size:10}}}}}}}});""" else ""}
${if (ftBuckets.isNotEmpty()) """
new Chart(document.getElementById('ftChart'),{type:'bar',data:{labels:['<8ms (>120fps)','8-16ms (60-120fps)','16-33ms (30-60fps)','33-50ms (20-30fps)','50-100ms (<20fps)','>100ms (stutter)'],datasets:[{label:'Frames',data:[$ftBuckets],backgroundColor:['#00ff88','#88ff00','#ffaa00','#ff6600','#ff0044','#cc0033']}]},options:{...D}});""" else ""}
${if (memTotalData.isNotEmpty()) """
new Chart(document.getElementById('memChart'),{type:'line',data:{labels:[$memLabels],datasets:[{label:'Total PSS (MB)',data:[$memTotalData],borderColor:'#00d4ff',tension:0.3,pointRadius:1},{label:'Native Heap (MB)',data:[$memNativeData],borderColor:'#ff6600',tension:0.3,pointRadius:1},{label:'Java Heap (MB)',data:[$memJavaData],borderColor:'#00ff88',tension:0.3,pointRadius:1}]},options:{...D}});""" else ""}
${if (cpuData.isNotEmpty()) """
new Chart(document.getElementById('cpuGpuChart'),{type:'line',data:{labels:[$cpuGpuLabels],datasets:[{label:'CPU %',data:[$cpuData],borderColor:'#00d4ff',tension:0.3,pointRadius:1},{label:'GPU %',data:[$gpuData],borderColor:'#7b2cbf',tension:0.3,pointRadius:1}]},options:{...D,scales:{...D.scales,y:{...D.scales.y,min:0,max:100}}}});""" else ""}
${if (tempCpuData.isNotEmpty()) """
new Chart(document.getElementById('tempChart'),{type:'line',data:{labels:[$cpuGpuLabels],datasets:[{label:'CPU',data:[$tempCpuData],borderColor:'#ff0044',tension:0.3,pointRadius:1},{label:'GPU',data:[$tempGpuData],borderColor:'#ff6600',tension:0.3,pointRadius:1},{label:'Skin',data:[$tempSkinData],borderColor:'#ffaa00',tension:0.3,pointRadius:1}]},options:{...D}});""" else ""}
</script>""")
    }

    // ===== Utilities =====

    private fun gradeColor(grade: Char) = when (grade) {
        'A' -> "#00ff88"; 'B' -> "#88ff00"; 'C' -> "#ffaa00"; 'D' -> "#ff6600"; else -> "#ff0044"
    }

    private fun fpsClass(value: Double, badThreshold: Double, warnThreshold: Double) = when {
        value < badThreshold -> "bad"
        value < warnThreshold -> "warning"
        else -> "good"
    }

    private fun esc(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;")

    // ===== CSS =====

    /** Loads CSS from resource file. Falls back to minimal inline CSS if resource not found. */
    private val CSS: String by lazy {
        try {
            HtmlReporter::class.java.getResourceAsStream("/report.css")?.bufferedReader()?.readText()
                ?: "body{font-family:sans-serif;background:#1a1a2e;color:#fff;padding:20px}.container{max-width:900px;margin:0 auto}"
        } catch (e: Exception) {
            "body{font-family:sans-serif;background:#1a1a2e;color:#fff;padding:20px}.container{max-width:900px;margin:0 auto}"
        }
    }
}
