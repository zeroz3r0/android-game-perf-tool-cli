package com.gameperf.core

import com.gameperf.analysis.RuleMatch
import java.text.SimpleDateFormat
import java.util.Date

data class PerformanceReport(
    val deviceId: String,
    val deviceSpecs: DeviceSpecs?,
    val durationSeconds: Long,
    val batteryStart: BatteryInfo?,
    val batteryEnd: BatteryInfo?,
    val averageFps: Double?,
    val minFps: Double?,
    val maxFps: Double?,
    val fpsStability: Double,
    val frameDrops: Int,
    val slowFrames: Int,
    val averageFrameTime: Double?,
    val averageMemory: Double?,
    val peakMemory: Double?,
    val warnings: List<String>,
    val errors: List<String>,
    val events: List<SystemEvent>,
    val fpsHistory: List<Double>,
    val timestamp: Long
)

class ReportGenerator(
    private val metricsExtractor: MetricsExtractor,
    private val warnings: List<RuleMatch>,
    private val errors: List<RuleMatch>,
    private val events: List<SystemEvent>
) {
    
    fun generate(
        deviceId: String,
        deviceSpecs: DeviceSpecs? = null,
        durationSeconds: Long = 0,
        batteryStart: BatteryInfo? = null,
        batteryEnd: BatteryInfo? = null
    ): PerformanceReport {
        val fpsMetrics = metricsExtractor.getMetricsByType(MetricType.FPS)
        val memoryMetrics = metricsExtractor.getMetricsByType(MetricType.MEMORY)
        
        return PerformanceReport(
            deviceId = deviceId,
            deviceSpecs = deviceSpecs,
            durationSeconds = durationSeconds,
            batteryStart = batteryStart,
            batteryEnd = batteryEnd,
            averageFps = metricsExtractor.getAverageFps(),
            minFps = metricsExtractor.getMinFps(),
            maxFps = metricsExtractor.getMaxFps(),
            fpsStability = metricsExtractor.getFpsStability(),
            frameDrops = metricsExtractor.getFrameDrops(),
            slowFrames = metricsExtractor.getSlowFrames(),
            averageFrameTime = metricsExtractor.getAverageFrameTime(),
            averageMemory = if (memoryMetrics.isNotEmpty()) memoryMetrics.map { it.value }.average() else null,
            peakMemory = metricsExtractor.getPeakMemory(),
            warnings = warnings.map { it.rule.name },
            errors = errors.map { it.rule.name },
            events = events,
            fpsHistory = metricsExtractor.getFpsHistory(),
            timestamp = System.currentTimeMillis()
        )
    }
    
    fun toJson(report: PerformanceReport): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        
        return """
        {
            "device": {
                "id": "${report.deviceId}",
                "model": "${report.deviceSpecs?.model ?: "Unknown"}",
                "manufacturer": "${report.deviceSpecs?.manufacturer ?: "Unknown"}",
                "sdk": ${report.deviceSpecs?.sdkVersion ?: 0},
                "cpu": "${report.deviceSpecs?.cpu ?: "Unknown"}",
                "ram": "${report.deviceSpecs?.ram ?: "Unknown"}"
            },
            "session": {
                "durationSeconds": ${report.durationSeconds},
                "timestamp": "${dateFormat.format(Date(report.timestamp))}"
            },
            "battery": {
                "start": ${report.batteryStart?.level ?: 0},
                "end": ${report.batteryEnd?.level ?: 0},
                "drain": ${(report.batteryStart?.level ?: 0) - (report.batteryEnd?.level ?: 0)},
                "drainPerMinute": ${calculateBatteryDrainPerMinute(report)}
            },
            "fps": {
                "average": ${report.averageFps?.let { "%.2f".format(it) } ?: "null"},
                "min": ${report.minFps?.let { "%.2f".format(it) } ?: "null"},
                "max": ${report.maxFps?.let { "%.2f".format(it) } ?: "null"},
                "stability": ${"%.1f".format(report.fpsStability)},
                "frameDrops": ${report.frameDrops},
                "slowFrames": ${report.slowFrames}
            },
            "memory": {
                "average": ${report.averageMemory?.let { "%.2f".format(it) } ?: "null"},
                "peak": ${report.peakMemory?.let { "%.2f".format(it) } ?: "null"}
            },
            "warnings": ${report.warnings.map { "\"$it\"" }},
            "errors": ${report.errors.map { "\"$it\"" }},
            "events": ${report.events.size}
        }
        """.trimIndent()
    }
    
    fun toHtml(report: PerformanceReport): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val batteryDrain = (report.batteryStart?.level ?: 0) - (report.batteryEnd?.level ?: 0)
        val batteryPerMin = calculateBatteryDrainPerMinute(report)
        val fpsGrade = calculateFpsGrade(report)
        val perfSummary = generatePerformanceSummary(report)
        
        return """
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Performance Report - ${report.deviceSpecs?.model ?: report.deviceId}</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { 
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; 
            background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
            min-height: 100vh;
            color: #fff;
            padding: 20px;
        }
        .container { max-width: 1200px; margin: 0 auto; }
        
        h1 { 
            text-align: center; 
            margin-bottom: 10px;
            font-size: 2rem;
            background: linear-gradient(90deg, #00d4ff, #7b2cbf);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        
        .subtitle { text-align: center; color: #888; margin-bottom: 30px; }
        
        .grade {
            text-align: center;
            font-size: 6rem;
            font-weight: bold;
            margin: 20px 0;
            text-shadow: 0 0 30px currentColor;
        }
        .grade-a { color: #00ff88; }
        .grade-b { color: #88ff00; }
        .grade-c { color: #ffaa00; }
        .grade-d { color: #ff6600; }
        .grade-f { color: #ff0044; }
        
        .grid { 
            display: grid; 
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); 
            gap: 20px;
            margin-bottom: 20px;
        }
        
        .card {
            background: rgba(255,255,255,0.05);
            border-radius: 16px;
            padding: 24px;
            backdrop-filter: blur(10px);
            border: 1px solid rgba(255,255,255,0.1);
        }
        
        .card h2 {
            font-size: 1.2rem;
            margin-bottom: 16px;
            color: #00d4ff;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        
        .stat { margin-bottom: 12px; }
        .stat-label { color: #888; font-size: 0.9rem; }
        .stat-value { font-size: 1.4rem; font-weight: bold; }
        .stat-value.good { color: #00ff88; }
        .stat-value.warning { color: #ffaa00; }
        .stat-value.bad { color: #ff0044; }
        
        .timeline { 
            height: 200px; 
            background: rgba(0,0,0,0.3); 
            border-radius: 8px;
            padding: 10px;
            margin-top: 10px;
        }
        
        canvas { width: 100%; height: 100%; }
        
        .events-list {
            max-height: 300px;
            overflow-y: auto;
        }
        
        .event {
            padding: 8px 12px;
            margin-bottom: 8px;
            border-radius: 8px;
            font-size: 0.85rem;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        
        .event-fps { background: rgba(0,212,255,0.2); border-left: 3px solid #00d4ff; }
        .event-memory { background: rgba(255,170,0,0.2); border-left: 3px solid #ffaa00; }
        .event-cpu { background: rgba(123,44,191,0.2); border-left: 3px solid #7b2cbf; }
        .event-error { background: rgba(255,0,68,0.2); border-left: 3px solid #ff0044; }
        .event-gc { background: rgba(0,255,136,0.2); border-left: 3px solid #00ff88; }
        
        .badge {
            padding: 4px 8px;
            border-radius: 4px;
            font-size: 0.75rem;
            font-weight: bold;
        }
        .badge-error { background: #ff0044; }
        .badge-warning { background: #ffaa00; }
        .badge-info { background: #00d4ff; }
        
        .summary {
            background: linear-gradient(135deg, rgba(0,212,255,0.1), rgba(123,44,191,0.1));
            padding: 24px;
            border-radius: 16px;
            margin-top: 20px;
        }
        
        .summary h3 { margin-bottom: 12px; color: #00d4ff; }
        
        .summary ul { padding-left: 20px; }
        .summary li { margin-bottom: 8px; line-height: 1.6; }
        
        @media (max-width: 768px) {
            .grid { grid-template-columns: 1fr; }
            h1 { font-size: 1.5rem; }
            .grade { font-size: 4rem; }
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>📊 Android Game Performance Report</h1>
        <p class="subtitle">${dateFormat.format(Date(report.timestamp))}</p>
        
        <div class="grade grade-$fpsGrade">$fpsGrade</div>
        <p style="text-align: center; color: #888; margin-bottom: 30px;">Nota de Rendimiento</p>
        
        <div class="grid">
            <div class="card">
                <h2>📱 Dispositivo</h2>
                <div class="stat">
                    <div class="stat-label">Modelo</div>
                    <div class="stat-value">${report.deviceSpecs?.model ?: "Unknown"}</div>
                </div>
                <div class="stat">
                    <div class="stat-label">Fabricante</div>
                    <div class="stat-value">${report.deviceSpecs?.manufacturer?.replaceFirstChar { it.uppercase() } ?: "Unknown"}</div>
                </div>
                <div class="stat">
                    <div class="stat-label">SoC / CPU</div>
                    <div class="stat-value" style="font-size: 1rem;">${report.deviceSpecs?.cpu ?: "Unknown"}</div>
                </div>
                <div class="stat">
                    <div class="stat-label">RAM</div>
                    <div class="stat-value">${report.deviceSpecs?.ram ?: "Unknown"}</div>
                </div>
                <div class="stat">
                    <div class="stat-label">SDK Version</div>
                    <div class="stat-value">${report.deviceSpecs?.sdkVersion ?: "Unknown"}</div>
                </div>
            </div>
            
            <div class="card">
                <h2>⏱️ Sesión</h2>
                <div class="stat">
                    <div class="stat-label">Duración</div>
                    <div class="stat-value">${formatDuration(report.durationSeconds)}</div>
                </div>
                <div class="stat">
                    <div class="stat-label">Batería Inicio</div>
                    <div class="stat-value">${report.batteryStart?.level ?: "N/A"}%</div>
                </div>
                <div class="stat">
                    <div class="stat-label">Batería Final</div>
                    <div class="stat-value">${report.batteryEnd?.level ?: "N/A"}%</div>
                </div>
                <div class="stat">
                    <div class="stat-label">Consumo</div>
                    <div class="stat-value ${if (batteryDrain > 30) "bad" else if (batteryDrain > 15) "warning" else "good"}">
                        $batteryDrain% (${"%.1f".format(batteryPerMin)}%/min)
                    </div>
                </div>
                <div class="stat">
                    <div class="stat-label">Temperatura Batería</div>
                    <div class="stat-value">${report.batteryEnd?.temperature ?: "N/A"}°C</div>
                </div>
            </div>
            
            <div class="card">
                <h2>🎮 FPS</h2>
                <div class="stat">
                    <div class="stat-label">Promedio</div>
                    <div class="stat-value ${getFpsColor(report.averageFps)}">
                        ${report.averageFps?.let { "%.1f".format(it) } ?: "N/A"}
                    </div>
                </div>
                <div class="stat">
                    <div class="stat-label">Mínimo</div>
                    <div class="stat-value ${getFpsColor(report.minFps)}">
                        ${report.minFps?.let { "%.1f".format(it) } ?: "N/A"}
                    </div>
                </div>
                <div class="stat">
                    <div class="stat-label">Máximo</div>
                    <div class="stat-value good">${report.maxFps?.let { "%.1f".format(it) } ?: "N/A"}</div>
                </div>
                <div class="stat">
                    <div class="stat-label">Estabilidad</div>
                    <div class="stat-value ${getStabilityColor(report.fpsStability)}">${"%.1f".format(report.fpsStability)}%</div>
                </div>
                <div class="stat">
                    <div class="stat-label">Frame Drops</div>
                    <div class="stat-value ${if (report.frameDrops > 50) "bad" else if (report.frameDrops > 20) "warning" else "good"}">
                        ${report.frameDrops}
                    </div>
                </div>
                <div class="stat">
                    <div class="stat-label">Slow Frames (>33ms)</div>
                    <div class="stat-value ${if (report.slowFrames > 20) "bad" else if (report.slowFrames > 5) "warning" else "good"}">
                        ${report.slowFrames}
                    </div>
                </div>
            </div>
            
            <div class="card">
                <h2>💾 Memoria</h2>
                <div class="stat">
                    <div class="stat-label">Promedio</div>
                    <div class="stat-value">${report.averageMemory?.let { "%.1f MB".format(it) } ?: "N/A"}</div>
                </div>
                <div class="stat">
                    <div class="stat-label">Pico</div>
                    <div class="stat-value ${if ((report.peakMemory ?: 0.0) > 1024) "bad" else if ((report.peakMemory ?: 0.0) > 512) "warning" else "good"}">
                        ${report.peakMemory?.let { "%.1f MB".format(it) } ?: "N/A"}
                    </div>
                </div>
            </div>
        </div>
        
        <div class="card" style="margin-bottom: 20px;">
            <h2>📈 Evolución FPS</h2>
            <div class="timeline">
                <canvas id="fpsChart"></canvas>
            </div>
        </div>
        
        <div class="grid">
            <div class="card">
                <h2>⚠️ Warnings (${report.warnings.size})</h2>
                ${if (report.warnings.isEmpty()) "<p style='color:#888'>No hay warnings</p>" else 
                    report.warnings.joinToString("") { "<div class='badge badge-warning'>$it</div>" }}
            </div>
            
            <div class="card">
                <h2>❌ Errors (${report.errors.size})</h2>
                ${if (report.errors.isEmpty()) "<p style='color:#888'>No hay errores</p>" else 
                    report.errors.joinToString("") { "<div class='badge badge-error'>$it</div>" }}
            </div>
        </div>
        
        <div class="card">
            <h2>📝 Eventos del Sistema (${report.events.size})</h2>
            <div class="events-list">
                ${generateEventsHtml(report.events.take(50))}
            </div>
        </div>
        
        <div class="summary">
            <h3>📋 Resumen de Rendimiento</h3>
            <ul>
                ${perfSummary.joinToString("") { "<li>$it</li>" }}
            </ul>
        </div>
    </div>
    
    <script>
        const fpsData = ${generateFpsChartData(report.fpsHistory)};
        const canvas = document.getElementById('fpsChart');
        const ctx = canvas.getContext('2d');
        
        function drawChart() {
            const width = canvas.parentElement.clientWidth - 20;
            const height = canvas.parentElement.clientHeight - 20;
            canvas.width = width;
            canvas.height = height;
            
            ctx.fillStyle = 'rgba(0,0,0,0.3)';
            ctx.fillRect(0, 0, width, height);
            
            if (fpsData.length === 0) return;
            
            const maxFps = Math.max(...fpsData, 60);
            const minFps = 0;
            const range = maxFps - minFps;
            
            ctx.strokeStyle = '#00d4ff';
            ctx.lineWidth = 2;
            ctx.beginPath();
            
            fpsData.forEach((fps, i) => {
                const x = (i / (fpsData.length - 1)) * width;
                const y = height - ((fps - minFps) / range) * height;
                if (i === 0) ctx.moveTo(x, y);
                else ctx.lineTo(x, y);
            });
            ctx.stroke();
            
            // 60 FPS line
            ctx.strokeStyle = 'rgba(0,255,136,0.5)';
            ctx.setLineDash([5, 5]);
            const y60 = height - ((60 - minFps) / range) * height;
            ctx.beginPath();
            ctx.moveTo(0, y60);
            ctx.lineTo(width, y60);
            ctx.stroke();
            ctx.setLineDash([]);
            
            // 30 FPS line
            ctx.strokeStyle = 'rgba(255,0,68,0.5)';
            ctx.setLineDash([5, 5]);
            const y30 = height - ((30 - minFps) / range) * height;
            ctx.beginPath();
            ctx.moveTo(0, y30);
            ctx.lineTo(width, y30);
            ctx.stroke();
            ctx.setLineDash([]);
            
            ctx.fillStyle = '#888';
            ctx.font = '10px Arial';
            ctx.fillText('60 FPS', 5, y60 - 5);
            ctx.fillText('30 FPS', 5, y30 - 5);
        }
        
        window.addEventListener('resize', drawChart);
        drawChart();
    </script>
</body>
</html>
        """.trimIndent()
    }
    
    private fun formatDuration(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
    }
    
    private fun calculateBatteryDrainPerMinute(report: PerformanceReport): Double {
        val drain = (report.batteryStart?.level ?: 0) - (report.batteryEnd?.level ?: 0)
        val minutes = report.durationSeconds / 60.0
        return if (minutes > 0) drain / minutes else 0.0
    }
    
    private fun calculateFpsGrade(report: PerformanceReport): String {
        val avgFps = report.averageFps ?: 0.0
        val stability = report.fpsStability
        val drops = report.frameDrops
        
        val score = when {
            avgFps >= 55 && stability >= 95 && drops < 10 -> 100
            avgFps >= 50 && stability >= 90 && drops < 20 -> 90
            avgFps >= 45 && stability >= 85 && drops < 30 -> 80
            avgFps >= 40 && stability >= 80 -> 70
            avgFps >= 35 && stability >= 70 -> 60
            avgFps >= 30 -> 50
            else -> 30
        }
        
        return when {
            score >= 90 -> "a"
            score >= 80 -> "b"
            score >= 70 -> "c"
            score >= 60 -> "d"
            else -> "f"
        }
    }
    
    private fun generatePerformanceSummary(report: PerformanceReport): List<String> {
        val summary = mutableListOf<String>()
        
        val avgFps = report.averageFps ?: 0.0
        summary.add(
            when {
                avgFps >= 55 -> "✅ Rendimiento EXCELENTE: FPS promedio de ${"%.1f".format(avgFps)} - Jugabilidad perfecta"
                avgFps >= 45 -> "✅ Buen rendimiento: FPS promedio de ${"%.1f".format(avgFps)} - Sin problemas notables"
                avgFps >= 35 -> "⚠️ Rendimiento ACEPTABLE: FPS promedio de ${"%.1f".format(avgFps)} - Puede haber picos de lag"
                avgFps >= 25 -> "❌ Rendimiento BAJO: FPS promedio de ${"%.1f".format(avgFps)} - Experimentarás lentitud"
                else -> "❌ Rendimiento INSUFICIENTE: FPS promedio de ${"%.1f".format(avgFps)} - Necesita optimización"
            }
        )
        
        if (report.fpsStability < 80) {
            summary.add("⚠️ Inestabilidad detectada: Solo ${"%.1f".format(report.fpsStability)}% de estabilidad - Los frames no son consistentes")
        }
        
        if (report.frameDrops > 30) {
            summary.add("❌ Muchos frame drops: ${report.frameDrops} frames perdidos - Optimizar renderizado")
        }
        
        val batteryDrain = (report.batteryStart?.level ?: 0) - (report.batteryEnd?.level ?: 0)
        val batteryPerMin = calculateBatteryDrainPerMinute(report)
        summary.add(
            when {
                batteryPerMin < 0.5 -> "✅ Consumo de batería EXCELENTE: Solo ${batteryDrain}% en ${formatDuration(report.durationSeconds)}"
                batteryPerMin < 1.0 -> "✅ Consumo de batería NORMAL: ${batteryDrain}% en ${formatDuration(report.durationSeconds)}"
                batteryPerMin < 2.0 -> "⚠️ Consumo de batería ALTO: ${batteryDrain}% en ${formatDuration(report.durationSeconds)}"
                else -> "❌ Consumo de batería EXCESIVO: ${batteryDrain}% en ${formatDuration(report.durationSeconds)}"
            }
        )
        
        val errorEvents = report.events.filter { it.type.name.contains("CRASH") || it.type.name.contains("ANR") }
        if (errorEvents.isNotEmpty()) {
            summary.add("❌ Se detectaron ${errorEvents.size} eventos críticos (crashes/ANRs) - Revisar logs")
        }
        
        val gcEvents = report.events.count { it.type.name == "GC_EVENT" }
        if (gcEvents > 50) {
            summary.add("⚠️-many GC events: $gcEvents detected - Possible memory pressure")
        }
        
        val thermalEvents = report.events.filter { it.type.name == "THERMAL_THROTTLE" }
        if (thermalEvents.isNotEmpty()) {
            summary.add("⚠️ Thermal throttling detected: ${thermalEvents.size} events - Device overheating")
        }
        
        if (summary.size == 1) {
            summary.add("✅ No se detectaron eventos críticos del sistema")
        }
        
        return summary
    }
    
    private fun getFpsColor(fps: Double?): String {
        return when {
            fps == null -> ""
            fps >= 50 -> "good"
            fps >= 35 -> "warning"
            else -> "bad"
        }
    }
    
    private fun getStabilityColor(stability: Double): String {
        return when {
            stability >= 90 -> "good"
            stability >= 70 -> "warning"
            else -> "bad"
        }
    }
    
    private fun generateEventsHtml(events: List<SystemEvent>): String {
        if (events.isEmpty()) return "<p style='color:#888'>No events captured</p>"
        
        return events.take(50).joinToString("") { event ->
            val typeClass = when {
                event.type.name.contains("FPS") || event.type.name.contains("JANK") -> "event-fps"
                event.type.name.contains("MEMORY") -> "event-memory"
                event.type.name.contains("CPU") || event.type.name.contains("THERMAL") -> "event-cpu"
                event.type.name.contains("CRASH") || event.type.name.contains("ANR") -> "event-error"
                event.type.name.contains("GC") -> "event-gc"
                else -> "event-fps"
            }
            
            val icon = when {
                event.type.name.contains("FPS") -> "🎮"
                event.type.name.contains("MEMORY") -> "💾"
                event.type.name.contains("CPU") -> "🔥"
                event.type.name.contains("THERMAL") -> "🌡️"
                event.type.name.contains("GC") -> "🗑️"
                event.type.name.contains("CRASH") -> "💥"
                event.type.name.contains("ANR") -> "⏸️"
                event.type.name.contains("APP") -> "📱"
                else -> "📝"
            }
            
            "<div class='event $typeClass'><span>$icon</span><span>${event.description.take(100)}</span></div>"
        }
    }
    
    private fun generateFpsChartData(history: List<Double>): String {
        if (history.isEmpty()) return "[]"
        return history.joinToString(",", "[", "]")
    }
}
