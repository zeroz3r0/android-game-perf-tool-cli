package com.gameperf

import com.gameperf.core.*
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date

// ===== App-level data classes =====

data class Config(
    val maxDuration: Int = -1,
    val quiet: Boolean = false,
    val exportJson: Boolean = false,
    val wifi: Boolean = false
)

data class SessionData(
    val gamePackage: String,
    val deviceSpecs: DeviceSpecs,
    val samples: MutableList<SessionSample>,
    val events: MutableList<LogEntry>,
    val configChanges: MutableList<GraphicsConfigChange>,
    val batteryStart: BatteryInfo?,
    val batteryEnd: BatteryInfo?,
    val memStart: MemoryInfo?,
    val memEnd: MemoryInfo?,
    val missedFramesStart: Int,
    val missedFramesEnd: Int,
    val startTime: Long,
    var endTime: Long,
    val initialResolution: RenderResolution?
) {
    val durationSeconds: Int get() = ((endTime - startTime) / 1000).toInt()
}

data class AnalysisResult(
    val session: SessionData,
    val fpsPercentiles: PercentileStats?,
    val frameTimePercentiles: PercentileStats?,
    val problemas: List<Problema>,
    val eventosRelevantes: List<LogEntry>,
    val eventosCategorized: List<CategorizedEvent>,
    val erroresAgrupados: List<Pair<String, Int>>,
    val advertenciasAgrupadas: List<Pair<String, Int>>,
    val fpsDrops: List<FpsDrop>,
    val nota: Char,
    val gcCount: Int,
    val audioIssues: Int,
    val thermalEvents: Int
)

// ===== Main =====

fun main(args: Array<String>) {
    val config = parseArgs(args)

    println("""
╔══════════════════════════════════════════════════════════════╗
║     Android Game Performance Tool v5.0                      ║
║     Analisis profesional de rendimiento                     ║
╚══════════════════════════════════════════════════════════════╝
    """.trimIndent())

    val connector = AdbConnector()
    if (!connector.isAdbAvailable()) { println("ERROR: ADB no disponible."); return }

    println("\nBuscando dispositivos...")
    val devices = connector.listDevices()
    if (devices.isEmpty()) { println("ERROR: No hay dispositivos conectados."); return }
    var device = devices.first()
    println("Dispositivo: ${device.model}")

    // WiFi mode: switch from USB to WiFi ADB
    if (config.wifi && !connector.isWifiConnection(device.id)) {
        println("\nCambiando a ADB WiFi...")
        val wifiId = connector.switchToWifi(device.id)
        if (wifiId != null) {
            println("  Conectado via WiFi: $wifiId")
            println("  >>> DESCONECTA EL CABLE USB AHORA <<<")
            println("  Esperando 10 segundos para que desconectes...")
            for (i in 10 downTo 1) {
                print("  $i... "); System.out.flush()
                Thread.sleep(1000)
            }
            println()
            
            // Verify WiFi connection still works
            val wifiDevices = connector.listDevices()
            val wifiDevice = wifiDevices.find { it.id == wifiId }
            if (wifiDevice != null) {
                device = wifiDevice
                println("  Conexion WiFi verificada. Bateria se medira SIN carga USB.")
            } else {
                println("  AVISO: No se pudo verificar WiFi. Continuando por USB.")
            }
        } else {
            println("  ERROR: No se pudo activar WiFi. Asegurate de que el movil esta en la misma red WiFi.")
            println("  Continuando por USB...")
        }
    } else if (connector.isWifiConnection(device.id)) {
        println("  Ya conectado via WiFi: ${device.id}")
    }

    val specs = connector.getDeviceSpecs(device.id)
    val batteryStart = connector.getBatteryLevel(device.id)
    println("\nSPECS:")
    println("  Modelo: ${specs.model} | CPU: ${specs.cpu} | GPU: ${specs.gpuModel}")
    val ramGb = "%.1f".format(specs.ram / (1024.0*1024*1024))
    println("  RAM: $ramGb GB | Cores: ${specs.cores} | SDK: ${specs.sdkVersion}")
    println("  Resolucion: ${specs.resolution} | Bateria: ${batteryStart?.level}%")

    println("\nBuscando juego en primer plano...")
    var gamePackage: String? = null
    for (i in 1..30) {
        gamePackage = connector.getGamePackage(device.id)
        if (gamePackage != null) break
        print("."); System.out.flush(); Thread.sleep(1000)
    }
    if (gamePackage == null) { println("\nERROR: No se detecto juego. Abre un juego y ejecuta de nuevo."); return }
    println("  $gamePackage")

    val isWifi = connector.isWifiConnection(device.id)
    val sessionData = captureLoop(connector, device, gamePackage, specs, batteryStart, config, isWifi)
    val result = analyzeSession(sessionData)
    printResults(result, config)

    val html = generateHtml(result)
    val safeName = gamePackage.replace(".", "_").takeLast(30)
    val dateStr = SimpleDateFormat("yyyy-MM-dd_HHmm").format(Date())
    val fileName = "informe_${safeName}_${specs.model.replace(" ", "_").replace("(", "").replace(")", "")}_${dateStr}.html"
    val archivo = File(fileName)
    archivo.writeText(html)
    println("\nINFORME GENERADO: ${archivo.absolutePath}")

    if (config.exportJson) {
        val jsonFile = File(fileName.replace(".html", ".json"))
        jsonFile.writeText(generateJson(result))
        println("JSON EXPORTADO: ${jsonFile.name}")
    }

    try { Desktop.getDesktop().browse(URI("file://${archivo.absolutePath}")) } catch (e: Exception) {}
}

// ===== Args =====

fun parseArgs(args: Array<String>): Config {
    if (args.any { it == "--help" || it == "-h" }) {
        println("Uso: apt [duracion_segundos] [opciones]")
        println("  --quiet, -q     Sin explicaciones detalladas")
        println("  --json          Exportar tambien en JSON")
        println("  --wifi, -w      Cambiar a ADB WiFi (desconecta USB, mide bateria real)")
        println("  --help, -h      Mostrar ayuda")
        println()
        println("Ejemplos:")
        println("  apt              Prueba indefinida por USB")
        println("  apt 60           Prueba de 60 segundos")
        println("  apt --wifi       Cambia a WiFi, desconecta cable, prueba indefinida")
        println("  apt --wifi 120   Cambia a WiFi, prueba de 2 minutos")
        println("  apt --wifi --json 60   WiFi + export JSON + 60 segundos")
        kotlin.system.exitProcess(0)
    }
    return Config(
        maxDuration = args.firstOrNull { it.toIntOrNull() != null }?.toInt() ?: -1,
        quiet = args.any { it == "--quiet" || it == "-q" },
        exportJson = args.any { it == "--json" },
        wifi = args.any { it == "--wifi" || it == "-w" }
    )
}

// ===== Capture Loop =====

fun captureLoop(
    connector: AdbConnector, device: AndroidDevice, gamePackage: String,
    specs: DeviceSpecs, batteryStart: BatteryInfo?, config: Config,
    isWifi: Boolean = false
): SessionData {
    val memStart = connector.getMemoryInfo(device.id, gamePackage)
    val missedStart = connector.getMissedFrames(device.id)
    val initialRes = connector.getRenderResolution(device.id, gamePackage)

    // Disable USB charging to measure real battery drain (only needed if connected by USB)
    val needBatteryUnplug = !isWifi
    if (needBatteryUnplug) {
        connector.disableCharging(device.id)
        println("  Carga USB desactivada (medicion real de bateria)")
    } else {
        println("  Modo WiFi: bateria se mide directamente (sin carga USB)")
    }

    println("\n${"=".repeat(60)}")
    println("PRUEBA DE RENDIMIENTO")
    println("=".repeat(60))
    println("  Juego: $gamePackage")
    if (initialRes != null) println("  Resolucion render: $initialRes")
    println("  Pulsa ENTER o Ctrl+C para detener\n")

    val samples = mutableListOf<SessionSample>()
    val events = mutableListOf<LogEntry>()
    val configChanges = mutableListOf<GraphicsConfigChange>()
    var lastResolution = initialRes
    val startTime = System.currentTimeMillis()
    val running = java.util.concurrent.atomic.AtomicBoolean(true)
    var prevCpuTimes = connector.getCpuTimes(device.id)
    var consecutiveFailures = 0

    val hook = Thread {
        running.set(false)
        if (needBatteryUnplug) connector.restoreCharging(device.id)
    }
    Runtime.getRuntime().addShutdownHook(hook)

    try {
        while (running.get() && (config.maxDuration <= 0 || ((System.currentTimeMillis() - startTime) / 1000).toInt() < config.maxDuration)) {
            Thread.sleep(1000)
            val now = System.currentTimeMillis()
            val elapsed = ((now - startTime) / 1000).toInt()

            // Frame data (FPS + frame times from single read)
            val frameData = connector.captureFrameData(device.id, gamePackage)
            val fps = frameData?.fps ?: 0

            // Memory
            val mem = connector.getMemoryInfo(device.id, gamePackage)

            // CPU
            val currCpuTimes = connector.getCpuTimes(device.id)
            val cpuSnap = connector.calculateCpuUsage(prevCpuTimes, currCpuTimes)
            prevCpuTimes = currCpuTimes

            // GPU
            val gpuSnap = connector.getGpuUsage(device.id)

            // Temperature
            val thermal = connector.getThermalInfo(device.id)

            // Detect device disconnect
            val allFailed = frameData == null && mem == null && cpuSnap == null
            if (allFailed) {
                consecutiveFailures++
                if (consecutiveFailures >= 5) {
                    println("  ERROR: Dispositivo desconectado o no responde. Deteniendo...")
                    running.set(false)
                    continue
                } else if (consecutiveFailures >= 3) {
                    println("  AVISO: Sin respuesta del dispositivo (${consecutiveFailures} intentos)")
                }
            } else {
                consecutiveFailures = 0
            }

            if (fps == 0 && connector.findGameLayer(device.id, gamePackage) == null && samples.none { it.fps > 0 }) {
                println("  Esperando renderizado... (asegurate que el juego esta en primer plano)")
            }

            // Resolution changes
            val currentRes = connector.getRenderResolution(device.id, gamePackage)
            if (currentRes != null && lastResolution != null && currentRes != lastResolution) {
                val lastFps = if (samples.size >= 3) samples.takeLast(3).filter { it.fps > 0 }.map { it.fps }.average().toInt() else fps
                configChanges.add(GraphicsConfigChange(now, lastResolution, currentRes, lastFps, fps))
                println("  CAMBIO RESOLUCION: $lastResolution -> $currentRes")
            }
            if (currentRes != null) lastResolution = currentRes

            // FPS jump detection
            if (fps > 0 && samples.size >= 4) {
                val prevAvg = samples.takeLast(3).filter { it.fps > 0 }.map { it.fps }.average()
                if (fps - prevAvg > 8 && configChanges.none { kotlin.math.abs(it.timestamp - now) < 3000 }) {
                    configChanges.add(GraphicsConfigChange(now, lastResolution, lastResolution, prevAvg.toInt(), fps))
                    println("  SUBIDA FPS: ${prevAvg.toInt()} -> $fps (posible cambio calidad)")
                }
            }

            // Events
            val logs = connector.getGameLogs(device.id, gamePackage, 500)
            val newEvents = logs.filter { nuevo -> events.none { it.timestamp == nuevo.timestamp && it.message == nuevo.message } }
            if (newEvents.isNotEmpty()) events.addAll(newEvents)

            // Frame drops
            val missed = connector.getMissedFrames(device.id)

            // Store sample
            samples.add(SessionSample(
                timestamp = now, fps = fps,
                frameTimes = frameData?.frameTimes ?: emptyList(),
                memoryInfo = mem, cpuSnapshot = cpuSnap,
                gpuSnapshot = gpuSnap, thermalSnapshot = thermal,
                renderResolution = currentRes
            ))

            // Status line
            val cpuStr = if (cpuSnap != null) "${cpuSnap.totalUsage.toInt()}%" else "--"
            val gpuStr = if (gpuSnap != null && gpuSnap.usage >= 0) "${gpuSnap.usage.toInt()}%" else "--"
            val memStr = if (mem != null) "${mem.totalMb}MB" else "--"
            val tempStr = if (thermal.cpuTemp > 0) "${thermal.cpuTemp.toInt()}C" else "--"
            val avgFps = samples.filter { it.fps > 0 }.map { it.fps }.average().let { if (it.isNaN()) 0.0 else it }.toInt()
            println("  ${elapsed}s | FPS: $fps (avg:$avgFps) | CPU:$cpuStr | GPU:$gpuStr | Mem:$memStr | Temp:$tempStr | Events:${events.size}")

            // Check ENTER (only newline/carriage return stops the session)
            try {
                if (System.`in`.available() > 0) {
                    val b = System.`in`.read()
                    if (b == 10 || b == 13) { // LF or CR
                        println("\n  Prueba detenida"); running.set(false)
                    }
                }
            } catch (e: Exception) {}
        }
    } catch (e: InterruptedException) { println("\n  Prueba detenida (Ctrl+C)") }

    try { Runtime.getRuntime().removeShutdownHook(hook) } catch (e: Exception) {}

    val endTime = System.currentTimeMillis()
    val batteryEnd = connector.getBatteryLevel(device.id)
    
    // Restore charging BEFORE returning (only if we disabled it)
    if (needBatteryUnplug) {
        connector.restoreCharging(device.id)
        println("  Carga USB restaurada")
    }
    val memEnd = connector.getMemoryInfo(device.id, gamePackage)
    val missedEnd = connector.getMissedFrames(device.id)

    return SessionData(
        gamePackage = gamePackage, deviceSpecs = specs, samples = samples,
        events = events, configChanges = configChanges,
        batteryStart = batteryStart, batteryEnd = batteryEnd,
        memStart = memStart, memEnd = memEnd,
        missedFramesStart = missedStart, missedFramesEnd = missedEnd,
        startTime = startTime, endTime = endTime, initialResolution = initialRes
    )
}

// ===== Analysis =====

fun analyzeSession(data: SessionData): AnalysisResult {
    val allFps = data.samples.filter { it.fps > 0 }.map { it.fps }
    val allFrameTimes = data.samples.flatMap { it.frameTimes }
    val fpsPerc = PercentileStats.fromIntValues(allFps)
    val ftPerc = PercentileStats.fromValues(allFrameTimes)

    val gcPattern = Regex("\\bgc\\b|concurrent.?mark|garbage.?collect|clamp.?gc", RegexOption.IGNORE_CASE)
    val relevantEvents = data.events.filter { e ->
        val msg = e.message.lowercase()
        val tag = e.tag.lowercase()
        msg.contains("jank") || msg.contains("hitch") || msg.contains("stutter") ||
        msg.contains("anr") || msg.contains("not responding") ||
        msg.contains("out of memory") || msg.contains("oom") ||
        (msg.contains("thermal") && (msg.contains("throttl") || msg.contains("cpu") || msg.contains("gpu"))) ||
        msg.contains("crash") || (msg.contains("fatal") && e.level >= LogLevel.ERROR) ||
        msg.contains("underrun") ||
        gcPattern.containsMatchIn(msg) ||
        msg.contains("trimmemory") || msg.contains("lowmemory") || msg.contains("low memory") ||
        msg.contains("surfaceflinger") ||
        (msg.contains("died") && msg.contains("process") && e.level >= LogLevel.ERROR)
    }

    val gcCount = relevantEvents.count { gcPattern.containsMatchIn(it.message) }
    val audioIssues = relevantEvents.count { it.message.contains("underrun", true) }
    val thermalEvents = relevantEvents.count { it.message.contains("thermal", true) && it.message.contains("throttl", true) }

    val errores = relevantEvents.filter { it.level >= LogLevel.ERROR }
    val warnings = relevantEvents.filter { it.level == LogLevel.WARN }

    val erroresAgrupados = errores.groupBy { it.message.take(60) }
        .filter { it.value.size >= 2 }.mapValues { it.value.size }
        .toList().sortedByDescending { it.second }.take(8)

    val advertenciasAgrupadas = warnings.groupBy { it.message.take(60) }
        .filter { it.value.size >= 3 }.mapValues { it.value.size }
        .toList().sortedByDescending { it.second }.take(8)

    // FPS drops correlation with events
    val fpsSamples = data.samples.filter { it.fps > 0 }
    val fpsDrops = mutableListOf<FpsDrop>()
    if (fpsSamples.size > 4) {
        for (i in 2 until fpsSamples.size - 2) {
            val current = fpsSamples[i].fps
            val neighbors = listOf(fpsSamples[i-2].fps, fpsSamples[i-1].fps, fpsSamples[i+1].fps, fpsSamples[i+2].fps)
            val avgNeighbors = neighbors.average()
            if (avgNeighbors - current > 10) {
                val ts = fpsSamples[i].timestamp
                val nearEvent = relevantEvents.filter { kotlin.math.abs(it.timestamp - ts) < 3000 }
                    .minByOrNull { kotlin.math.abs(it.timestamp - ts) }
                fpsDrops.add(FpsDrop(i, current, ts, nearEvent))
            }
        }
    }

    // Categorize events
    val categorized = categorizeEvents(relevantEvents)

    val problemas = detectProblemas(data, fpsPerc, gcCount, audioIssues, errores.size)
    val nota = calcularNota(fpsPerc, problemas)

    return AnalysisResult(
        session = data, fpsPercentiles = fpsPerc, frameTimePercentiles = ftPerc,
        problemas = problemas, eventosRelevantes = relevantEvents,
        eventosCategorized = categorized,
        erroresAgrupados = erroresAgrupados, advertenciasAgrupadas = advertenciasAgrupadas,
        fpsDrops = fpsDrops, nota = nota,
        gcCount = gcCount, audioIssues = audioIssues, thermalEvents = thermalEvents
    )
}

fun categorizeEvents(events: List<LogEntry>): List<CategorizedEvent> {
    val gcPattern = Regex("\\bgc\\b|concurrent.?mark|garbage.?collect", RegexOption.IGNORE_CASE)
    
    return events.map { e ->
        val msg = e.message.lowercase()
        
        val (category, description, impacts) = when {
            gcPattern.containsMatchIn(msg) -> 
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
            
            msg.contains("crash") || (msg.contains("fatal") && e.level >= LogLevel.ERROR) ->
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
        
        CategorizedEvent(entry = e, category = category, description = description, impactsPerformance = impacts)
    }
}

fun detectProblemas(data: SessionData, fpsPerc: PercentileStats?, gcCount: Int, audioIssues: Int, errorCount: Int): List<Problema> {
    val p = mutableListOf<Problema>()
    val avgFps = fpsPerc?.avg ?: 0.0
    val totalDrops = data.missedFramesEnd - data.missedFramesStart
    val peakMem = data.samples.mapNotNull { it.memoryInfo?.totalMb }.maxOrNull() ?: 0L
    val memGrowth = (data.memEnd?.totalMb ?: 0) - (data.memStart?.totalMb ?: 0)
    val avgCpu = data.samples.mapNotNull { it.cpuSnapshot?.totalUsage }.average().let { if (it.isNaN()) 0.0 else it }
    val maxTemp = data.samples.mapNotNull { it.thermalSnapshot?.cpuTemp }.filter { it > 0 }.maxOrNull() ?: 0.0

    if (avgFps > 0 && avgFps < 30) p.add(Problema("FPS_BAJO", "Alto", "FPS promedio de ${avgFps.toInt()} - Muy bajo",
        "El juego renderiza menos de 30 imagenes por segundo. El movimiento se ve entrecortado y la respuesta a los controles tiene retraso visible.",
        "1) Bajar la calidad grafica en ajustes del juego. 2) Cerrar apps en segundo plano. 3) Activar modo rendimiento si disponible. 4) Desactivar efectos de post-procesado."))
    else if (avgFps >= 30 && avgFps < 45) p.add(Problema("FPS_BAJO", "Medio", "FPS promedio de ${avgFps.toInt()} - Bajo",
        "El juego va por debajo de 45 FPS. Se nota falta de fluidez en escenas con mucha accion.",
        "1) Reducir resolucion del juego. 2) Bajar calidad de sombras y particulas. 3) Desactivar modo ahorro de bateria."))

    val stability = fpsPerc?.let { if (it.avg > 0) (1 - ((it.max - it.min) / it.avg / 2)).coerceIn(0.0, 1.0) * 100 else 100.0 } ?: 100.0
    if (stability < 70) p.add(Problema("FPS_INESTABLE", "Medio", "Estabilidad del ${stability.toInt()}%",
        "Los FPS varian mucho durante la partida causando tirones intermitentes.",
        "1) Comprobar temperatura del dispositivo. 2) Liberar RAM cerrando apps. 3) Puede ser problema de optimizacion del juego."))

    if (totalDrops > 30) p.add(Problema("FRAME_DROPS", "Alto", "$totalDrops frames perdidos",
        "Se perdieron $totalDrops frames. Cada frame perdido es un tiron visible.",
        "1) Bajar calidad grafica. 2) Si solo ocurre en ciertos momentos, puede ser problema del juego."))

    if (peakMem > 2000) p.add(Problema("MEMORIA_ALTA", "Alto", "Pico de $peakMem MB",
        "El juego uso $peakMem MB de RAM, dejando poco margen para el sistema.",
        "1) Cerrar todas las apps antes de jugar. 2) Reiniciar el dispositivo. 3) Si tiene 4GB o menos de RAM, el juego es muy exigente."))
    else if (peakMem > 1500) p.add(Problema("MEMORIA_ALTA", "Medio", "Pico de $peakMem MB",
        "El juego usa bastante RAM. No critico pero cerca del limite en dispositivos con poca RAM.",
        "1) Cerrar apps en segundo plano. 2) Evitar Chrome con muchas pestanas."))

    if (memGrowth > 500) p.add(Problema("MEMORY_LEAK", "Alto", "Crecimiento de $memGrowth MB",
        "La memoria crecio ${memGrowth}MB sin bajar. Posible memory leak del juego.",
        "1) Es un BUG del juego. 2) Reiniciar el juego periodicamente. 3) Reportar al desarrollador."))

    if (gcCount > 10) p.add(Problema("GC_FRECUENTE", "Medio", "$gcCount eventos de recolector de basura",
        "El recolector de basura se ejecuto $gcCount veces, causando micro-congelaciones.",
        "1) Problema de optimizacion del juego. 2) Cerrar apps para reducir presion de memoria."))

    if (audioIssues > 5) p.add(Problema("AUDIO_LAG", "Medio", "$audioIssues problemas de audio",
        "Se detectaron $audioIssues underruns de audio. El sonido se corta o crepita.",
        "1) Cerrar apps que usen audio (Spotify, YouTube). 2) Bajar calidad grafica para liberar CPU. 3) Probar sin Bluetooth."))

    if (avgCpu > 85) p.add(Problema("CPU_SATURADA", "Alto", "CPU al ${avgCpu.toInt()}%",
        "El procesador esta casi al maximo. Es el cuello de botella principal del rendimiento.",
        "1) Cerrar todas las apps. 2) Bajar calidad de IA/fisica en el juego si posible. 3) El dispositivo puede no ser suficiente."))

    if (maxTemp > 45) p.add(Problema("SOBRECALENTAMIENTO", "Alto", "Temperatura maxima: ${maxTemp.toInt()}C",
        "El dispositivo alcanzo ${maxTemp.toInt()}C. A partir de ~42C se activa el thermal throttling que reduce CPU/GPU.",
        "1) Quitar funda del dispositivo. 2) No jugar mientras carga. 3) Jugar en ambiente fresco. 4) Hacer pausas de 5 min cada 15-20 min."))

    if (errorCount > 3) p.add(Problema("ERRORES", "Alto", "$errorCount errores criticos",
        "Se detectaron $errorCount errores graves que pueden indicar inestabilidad.",
        "1) Actualizar el juego a la ultima version. 2) Borrar cache del juego. 3) Si crashea, reinstalar."))

    return p
}

// ===== Grade =====

fun calcularNota(fpsPerc: PercentileStats?, problemas: List<Problema>): Char {
    var score = 100.0
    val avgFps = fpsPerc?.avg ?: 0.0
    score -= when { avgFps >= 55 -> 0; avgFps >= 45 -> 10; avgFps >= 35 -> 20; avgFps > 0 -> 35; else -> 40 }
    val p1 = fpsPerc?.p1 ?: 0.0
    if (p1 < 20) score -= 15 else if (p1 < 30) score -= 8
    for (pr in problemas) score -= when (pr.severidad) { "Alto" -> 12; "Medio" -> 6; else -> 2 }
    return when { score >= 85 -> 'A'; score >= 70 -> 'B'; score >= 55 -> 'C'; score >= 40 -> 'D'; else -> 'F' }
}

// ===== Terminal Output =====

fun printResults(result: AnalysisResult, config: Config) {
    val s = result.session
    val dur = s.durationSeconds
    println("\n${"=".repeat(60)}")
    println("RESULTADOS (${dur/60}m ${dur%60}s)")
    println("=".repeat(60))

    val fp = result.fpsPercentiles
    println("\nFPS:")
    if (fp != null) {
        println("  Avg: ${fp.avg.toInt()} | Min: ${fp.min.toInt()} | Max: ${fp.max.toInt()}")
        println("  P1: ${fp.p1.toInt()} | P5: ${fp.p5.toInt()} | P50: ${fp.p50.toInt()} | P90: ${fp.p90.toInt()} | P99: ${fp.p99.toInt()}")
    } else println("  Sin datos de FPS")

    val ft = result.frameTimePercentiles
    if (ft != null) {
        println("\nFrame Times:")
        println("  Avg: ${"%.1f".format(ft.avg)}ms | P50: ${"%.1f".format(ft.p50)}ms | P99: ${"%.1f".format(ft.p99)}ms")
        val jank = s.samples.sumOf { it.frameTimes.count { t -> t > 16.67 } }
        val stutter = s.samples.sumOf { it.frameTimes.count { t -> t > 100.0 } }
        println("  Jank (>16ms): $jank | Stutter (>100ms): $stutter")
    }

    println("\nMemoria:")
    val memPeak = s.samples.mapNotNull { it.memoryInfo }.maxByOrNull { it.totalPssKb }
    if (memPeak != null) {
        println("  Total: ${memPeak.totalMb}MB | Native: ${memPeak.nativeHeapMb}MB | Java: ${memPeak.javaHeapMb}MB")
        println("  Inicio: ${s.memStart?.totalMb ?: "?"}MB | Final: ${s.memEnd?.totalMb ?: "?"}MB")
    }

    val avgCpu = s.samples.mapNotNull { it.cpuSnapshot?.totalUsage }.average().let { if (it.isNaN()) -1.0 else it }
    val maxCpu = s.samples.mapNotNull { it.cpuSnapshot?.totalUsage }.maxOrNull() ?: -1.0
    val avgGpu = s.samples.mapNotNull { it.gpuSnapshot }.filter { it.usage >= 0 }.map { it.usage }.average().let { if (it.isNaN()) -1.0 else it }
    println("\nCPU: ${if (avgCpu >= 0) "avg ${avgCpu.toInt()}% / max ${maxCpu.toInt()}%" else "no disponible"}")
    println("GPU: ${if (avgGpu >= 0) "avg ${avgGpu.toInt()}%" else "no disponible"}")

    val maxTemp = s.samples.mapNotNull { it.thermalSnapshot?.cpuTemp }.filter { it > 0 }.maxOrNull()
    if (maxTemp != null) println("Temp CPU max: ${maxTemp.toInt()}C")

    println("\nBateria: ${s.batteryStart?.level ?: "?"}% -> ${s.batteryEnd?.level ?: "?"}% (consumo: ${(s.batteryStart?.level ?: 0) - (s.batteryEnd?.level ?: 0)}%)")
    println("Frame drops: ${s.missedFramesEnd - s.missedFramesStart}")

    // Event summary
    val catSummary = result.eventosCategorized.groupBy { it.category }
        .mapValues { it.value.size }
        .toList().sortedByDescending { it.second }
    if (catSummary.isNotEmpty()) {
        val impactTotal = result.eventosCategorized.count { it.impactsPerformance }
        println("\nEventos de rendimiento: ${result.eventosCategorized.size} total, $impactTotal afectan rendimiento")
        for ((cat, count) in catSummary) {
            val impact = result.eventosCategorized.count { it.category == cat && it.impactsPerformance }
            println("  ${cat.label}: $count${if (impact > 0) " ($impact criticos)" else ""}")
        }
    }

    if (result.problemas.isNotEmpty()) {
        println("\nPROBLEMAS:")
        for (pr in result.problemas) println("  [${pr.severidad}] ${pr.descripcion}")
    }

    println("\nNOTA: ${result.nota}")
}

// ===== Log Translation (SINGLE copy) =====

fun traducirLog(mensaje: String, tag: String): String {
    val msg = mensaje.lowercase()
    return when {
        msg.contains("underrun") -> "AUDIO LAG - El dispositivo no puede procesar audio a tiempo."
        msg.contains("audiopolicy") || msg.contains("audioflinger") -> "Problema con el sistema de audio."
        Regex("\\bgc\\b|concurrent.?mark|garbage.?collect").containsMatchIn(msg) -> "RECOLECTOR DE BASURA - puede causar micro-lags."
        msg.contains("thermal") && (msg.contains("throttl") || msg.contains("cpu") || msg.contains("gpu")) -> "THERMAL THROTTLING - Dispositivo sobrecalentado, reduce CPU/GPU."
        msg.contains("oom") || msg.contains("out of memory") -> "MEMORIA LLENA - Puede cerrar el juego."
        msg.contains("low memory") || msg.contains("lowmemory") -> "Memoria baja - sistema cerrando apps."
        msg.contains("trimmemory") -> "Sistema liberando memoria."
        msg.contains("jank") || msg.contains("hitch") || msg.contains("stutter") -> "JANK - Frames perdidos visiblemente."
        msg.contains("surfaceflinger") -> "Error del sistema grafico."
        msg.contains("anr") || msg.contains("not responding") -> "APP COLGADA - No responde."
        msg.contains("fatal") || msg.contains("crash") -> "ERROR FATAL - Puede cerrarse."
        msg.contains("died") && msg.contains("process") -> "Proceso terminado inesperadamente."
        msg.contains("timeout") && (msg.contains("socket") || msg.contains("connect")) -> "TIMEOUT de red."
        else -> "Evento del sistema."
    }
}

fun explicarError(mensaje: String): String {
    val msg = mensaje.lowercase()
    return when {
        msg.contains("underrun") -> "Un audio underrun ocurre cuando el buffer de audio se vacia antes de que el sistema lo rellene. El procesador esta demasiado ocupado con graficos."
        Regex("\\bgc\\b|concurrent.?mark|garbage.?collect").containsMatchIn(msg) -> "El recolector de basura se activo para liberar memoria. El juego se congela brevemente (1-50ms) mientras limpia."
        msg.contains("thermal") && msg.contains("throttl") -> "El dispositivo se sobrecalienta y reduce la velocidad del procesador/GPU. Causa caida directa de FPS."
        msg.contains("oom") || msg.contains("out of memory") -> "El sistema se quedo sin memoria RAM. Android cierra procesos. El juego puede crashear."
        msg.contains("trimmemory") -> "Android pide a las apps que liberen memoria. La RAM esta bajo presion."
        msg.contains("jank") || msg.contains("hitch") -> "El juego no pudo dibujar frames a tiempo. La pantalla se congelo brevemente."
        msg.contains("surfaceflinger") -> "SurfaceFlinger (compositor grafico) reporta problemas. Puede causar frames perdidos."
        msg.contains("anr") || msg.contains("not responding") -> "La app se bloqueo mas de 5 segundos sin responder. El usuario ve el dialogo 'No responde'."
        msg.contains("fatal") || msg.contains("crash") -> "Error fatal en el juego. Normalmente causa que se cierre."
        msg.contains("timeout") && msg.contains("socket") -> "Conexion de red caida por timeout. En juegos online causa lag o desconexion."
        msg.contains("hwc") || msg.contains("hwcomposer") -> "El Hardware Composer tiene problemas. Puede causar parpadeos o frames perdidos."
        else -> "Evento del sistema detectado. Su repeticion puede indicar un problema."
    }
}

// ===== Escaping =====

fun escHtml(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
fun escJson(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")

// ===== HTML Report =====

fun generateHtml(result: AnalysisResult): String {
    val s = result.session
    val fp = result.fpsPercentiles
    val ft = result.frameTimePercentiles
    val dur = s.durationSeconds
    val durStr = "${dur/60}m ${dur%60}s"
    val totalDrops = s.missedFramesEnd - s.missedFramesStart
    val colorNota = when (result.nota) { 'A' -> "#00ff88"; 'B' -> "#88ff00"; 'C' -> "#ffaa00"; 'D' -> "#ff6600"; else -> "#ff0044" }

    // Prepare chart data
    val fpsData = s.samples.filter { it.fps > 0 }.joinToString(",") { it.fps.toString() }
    val fpsLabels = s.samples.filter { it.fps > 0 }.joinToString(",") { "\"${((it.timestamp - s.startTime) / 1000)}s\"" }
    val memTotalData = s.samples.mapNotNull { it.memoryInfo?.totalMb }.joinToString(",")
    val memNativeData = s.samples.mapNotNull { it.memoryInfo?.nativeHeapMb }.joinToString(",")
    val memJavaData = s.samples.mapNotNull { it.memoryInfo?.javaHeapMb }.joinToString(",")
    val memLabels = s.samples.filter { it.memoryInfo != null }.joinToString(",") { "\"${((it.timestamp - s.startTime) / 1000)}s\"" }
    val cpuData = s.samples.mapNotNull { it.cpuSnapshot?.totalUsage?.toInt() }.joinToString(",")
    val gpuData = s.samples.map { it.gpuSnapshot?.let { g -> if (g.usage >= 0) g.usage.toInt().toString() else "null" } ?: "null" }.joinToString(",")
    val cpuGpuLabels = s.samples.joinToString(",") { "\"${((it.timestamp - s.startTime) / 1000)}s\"" }
    val tempCpuData = s.samples.map { it.thermalSnapshot?.let { t -> if (t.cpuTemp > 0) t.cpuTemp.toInt().toString() else "null" } ?: "null" }.joinToString(",")
    val tempGpuData = s.samples.map { it.thermalSnapshot?.let { t -> if (t.gpuTemp > 0) t.gpuTemp.toInt().toString() else "null" } ?: "null" }.joinToString(",")
    val tempSkinData = s.samples.map { it.thermalSnapshot?.let { t -> if (t.skinTemp > 0) t.skinTemp.toInt().toString() else "null" } ?: "null" }.joinToString(",")

    // Frame time histogram
    val allFt = s.samples.flatMap { it.frameTimes }
    val ftBuckets = listOf(
        allFt.count { it < 8.0 },
        allFt.count { it >= 8.0 && it < 16.67 },
        allFt.count { it >= 16.67 && it < 33.33 },
        allFt.count { it >= 33.33 && it < 50.0 },
        allFt.count { it >= 50.0 && it < 100.0 },
        allFt.count { it >= 100.0 }
    ).joinToString(",")

    // Memory stats
    val memPeak = s.samples.mapNotNull { it.memoryInfo }.maxByOrNull { it.totalPssKb }
    val memStart = s.memStart
    val memEnd = s.memEnd
    val avgCpu = s.samples.mapNotNull { it.cpuSnapshot?.totalUsage }.average().let { if (it.isNaN()) -1.0 else it }
    val maxCpu = s.samples.mapNotNull { it.cpuSnapshot?.totalUsage }.maxOrNull() ?: -1.0
    val avgGpu = s.samples.mapNotNull { it.gpuSnapshot }.filter { it.usage >= 0 }.map { it.usage }.average().let { if (it.isNaN()) -1.0 else it }
    val cpuCores = s.samples.lastOrNull()?.cpuSnapshot?.perCoreUsage
    val maxTemp = s.samples.mapNotNull { it.thermalSnapshot?.cpuTemp }.filter { it > 0 }.maxOrNull() ?: 0.0

    val batteryDrain = (s.batteryStart?.level ?: 0) - (s.batteryEnd?.level ?: 0)
    val drainPerMin = if (dur > 0) batteryDrain.toDouble() / (dur / 60.0) else 0.0

    // Problems HTML
    val problemasHtml = if (result.problemas.isEmpty()) {
        "<p style='color:#00ff88'>Sin problemas criticos detectados.</p>"
    } else result.problemas.joinToString("") { pr ->
        val color = when (pr.severidad) { "Alto" -> "#ff0044"; "Medio" -> "#ffaa00"; else -> "#00d4ff" }
        """<div style="background:rgba(255,255,255,0.03);border-left:4px solid $color;padding:16px;margin:12px 0;border-radius:8px">
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px">
                <strong style="color:$color;font-size:1.1em">${pr.descripcion}</strong>
                <span style="background:$color;color:#000;padding:2px 10px;border-radius:12px;font-size:0.75em;font-weight:700">Severidad: ${pr.severidad}</span>
            </div>
            <div style="color:#ccc;margin-bottom:12px;line-height:1.6;font-size:0.9em"><strong style="color:#00d4ff">Que significa:</strong> ${pr.explicacion}</div>
            <div style="background:rgba(0,212,255,0.08);padding:12px;border-radius:6px;border-left:3px solid #00d4ff">
                <strong style="color:#00d4ff;font-size:0.85em">Solucion recomendada:</strong>
                <div style="color:#aaa;margin-top:6px;line-height:1.6;font-size:0.85em">${pr.solucion}</div>
            </div>
        </div>"""
    }

    // Errors HTML
    val erroresHtml = if (result.erroresAgrupados.isEmpty()) "" else """
        <div class="card" style="border-left:4px solid #ff0044">
            <h2>Errores detectados</h2>
            <p style="color:#888;font-size:0.85em;margin-bottom:16px">Errores repetidos durante la sesion con explicacion de su significado.</p>
            ${result.erroresAgrupados.joinToString("") { (msg, count) ->
                val trad = escHtml(traducirLog(msg, ""))
                val expl = escHtml(explicarError(msg))
                val msgShort = escHtml(if (msg.length > 100) msg.take(97) + "..." else msg)
                """<div style="background:rgba(255,0,68,0.08);padding:16px;margin:10px 0;border-radius:8px;border-left:3px solid #ff0044">
                    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px">
                        <span style="color:#00d4ff;font-weight:700">$trad</span>
                        <span style="background:#ff0044;color:#fff;padding:2px 10px;border-radius:12px;font-size:0.75em;font-weight:700">x$count</span>
                    </div>
                    <div style="color:#aaa;font-size:0.8em;line-height:1.5;margin-bottom:8px"><strong>Que significa:</strong> $expl</div>
                    <div style="color:#666;font-size:0.7em;font-family:monospace;word-break:break-all;background:rgba(0,0,0,0.3);padding:8px;border-radius:4px">Log: $msgShort</div>
                </div>"""
            }}
        </div>"""

    // Warnings HTML
    val warningsHtml = if (result.advertenciasAgrupadas.isEmpty()) "" else """
        <div class="card" style="border-left:4px solid #ffaa00">
            <h2>Advertencias detectadas</h2>
            ${result.advertenciasAgrupadas.joinToString("") { (msg, count) ->
                val trad = escHtml(traducirLog(msg, ""))
                val expl = escHtml(explicarError(msg))
                val msgShort = escHtml(if (msg.length > 100) msg.take(97) + "..." else msg)
                """<div style="background:rgba(255,170,0,0.08);padding:16px;margin:10px 0;border-radius:8px;border-left:3px solid #ffaa00">
                    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px">
                        <span style="color:#00d4ff;font-weight:700">$trad</span>
                        <span style="background:#ffaa00;color:#000;padding:2px 10px;border-radius:12px;font-size:0.75em;font-weight:700">x$count</span>
                    </div>
                    <div style="color:#aaa;font-size:0.8em;line-height:1.5;margin-bottom:8px"><strong>Que significa:</strong> $expl</div>
                    <div style="color:#666;font-size:0.7em;font-family:monospace;word-break:break-all;background:rgba(0,0,0,0.3);padding:8px;border-radius:4px">Log: $msgShort</div>
                </div>"""
            }}
        </div>"""

    // Config changes HTML
    val configHtml = if (s.configChanges.isEmpty() && s.initialResolution == null) "" else """
        <div class="card" style="border-left:4px solid #7b2cbf">
            <h2>Configuracion grafica</h2>
            ${if (s.initialResolution != null) "<div class='stat'><span class='stat-label'>Resolucion render</span><span class='stat-value'>${s.initialResolution}</span></div>" else ""}
            ${if (s.configChanges.isNotEmpty()) s.configChanges.joinToString("") { ch ->
                val t = ((ch.timestamp - s.startTime) / 1000)
                if (ch.resolutionChanged) """<div style="background:rgba(123,44,191,0.1);padding:14px;margin:8px 0;border-radius:8px;border-left:3px solid #7b2cbf">
                    <strong style="color:#7b2cbf">${if (ch.resolutionDecreased) "Bajada" else "Subida"} de resolucion a los ${t}s</strong>
                    <div style="color:#ccc;font-size:0.9em">${ch.previousResolution} -> ${ch.newResolution}</div>
                    <div style="color:#888;font-size:0.8em">FPS: ${ch.fpsBeforeChange} -> ${ch.fpsAfterChange}${if (ch.fpsImproved) " - Mejora confirmada" else ""}</div>
                </div>""" else """<div style="background:rgba(0,212,255,0.08);padding:14px;margin:8px 0;border-radius:8px;border-left:3px solid #00d4ff">
                    <strong style="color:#00d4ff">Subida brusca FPS a los ${t}s</strong>
                    <div style="color:#ccc;font-size:0.9em">FPS: ${ch.fpsBeforeChange} -> ${ch.fpsAfterChange} (+${ch.fpsAfterChange - ch.fpsBeforeChange})</div>
                    <div style="color:#888;font-size:0.8em">Posible cambio de calidad grafica (texturas, sombras, efectos).</div>
                </div>"""
            } else "<p style='color:#888;font-size:0.9em'>Sin cambios detectados.</p>"}
        </div>"""

    // FPS drops HTML
    val fpsDropsHtml = if (result.fpsDrops.isEmpty()) "" else """
        <div class="card" style="border-left:4px solid #ff6600">
            <h2>Caidas de FPS (${result.fpsDrops.size})</h2>
            ${result.fpsDrops.take(10).joinToString("") { drop ->
                val t = ((drop.timestamp - s.startTime) / 1000)
                """<div style="background:rgba(255,102,0,0.1);padding:12px;margin:8px 0;border-radius:8px;border-left:3px solid #ff6600">
                    <span style="color:#ff6600;font-weight:700">${t}s</span> - <span style="color:#ff0044;font-weight:700">${drop.fps} FPS</span>
                    ${if (drop.eventoCercano != null) "<div style='color:#00d4ff;font-size:0.85em;margin-top:4px'>Evento: ${traducirLog(drop.eventoCercano.message, drop.eventoCercano.tag)}</div>" else ""}
                </div>"""
            }}
        </div>"""

    return """<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0">
<title>Informe - ${escHtml(s.gamePackage)}</title>
<script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
<script src="https://cdn.jsdelivr.net/npm/chartjs-plugin-annotation"></script>
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:linear-gradient(135deg,#1a1a2e 0%,#16213e 100%);min-height:100vh;color:#fff;padding:20px}
.container{max-width:900px;margin:0 auto}
h1{text-align:center;margin-bottom:10px;background:linear-gradient(90deg,#00d4ff,#7b2cbf);-webkit-background-clip:text;-webkit-text-fill-color:transparent;font-size:1.8rem}
.subtitle{text-align:center;color:#888;margin-bottom:30px}
.grade{text-align:center;font-size:7rem;font-weight:bold;color:$colorNota;text-shadow:0 0 40px $colorNota;margin:20px 0}
.card{background:rgba(255,255,255,0.05);border-radius:16px;padding:20px;margin-bottom:16px;border:1px solid rgba(255,255,255,0.1)}
.card h2{color:#00d4ff;margin-bottom:16px;font-size:1.2rem}
.stat{display:flex;justify-content:space-between;padding:8px 0;border-bottom:1px solid rgba(255,255,255,0.1)}
.stat:last-child{border-bottom:none}
.stat-label{color:#888}.stat-value{font-weight:bold}
.good{color:#00ff88}.warning{color:#ffaa00}.bad{color:#ff0044}
.chart-container{height:250px;background:rgba(0,0,0,0.3);border-radius:8px;padding:10px;margin-top:10px;position:relative}
.ptable{width:100%;border-collapse:collapse;margin-top:10px}
.ptable td,.ptable th{padding:8px 12px;text-align:center;border:1px solid rgba(255,255,255,0.1)}
.ptable th{color:#00d4ff;font-size:0.85em}.ptable td{font-weight:bold;font-size:1.1em}
@media print{body{background:#fff;color:#000}.card{border:1px solid #ddd;background:#fafafa}.grade{text-shadow:none}}
</style>
</head>
<body>
<div class="container">
<h1>Informe de Rendimiento</h1>
<p class="subtitle">${escHtml(s.gamePackage)} | ${SimpleDateFormat("dd/MM/yyyy HH:mm").format(Date(s.startTime))}</p>
<div class="grade">${result.nota}</div>
<p style="text-align:center;color:#888;margin-bottom:30px">Nota de Rendimiento</p>

<div class="card">
    <h2>Dispositivo</h2>
    <div class="stat"><span class="stat-label">Modelo</span><span class="stat-value">${escHtml(s.deviceSpecs.model)}</span></div>
    <div class="stat"><span class="stat-label">CPU</span><span class="stat-value">${escHtml(s.deviceSpecs.cpu)}</span></div>
    <div class="stat"><span class="stat-label">GPU</span><span class="stat-value">${escHtml(s.deviceSpecs.gpuModel)}</span></div>
    <div class="stat"><span class="stat-label">RAM</span><span class="stat-value">${"%.1f".format(s.deviceSpecs.ram / (1024.0*1024*1024))} GB</span></div>
    <div class="stat"><span class="stat-label">Cores</span><span class="stat-value">${s.deviceSpecs.cores}</span></div>
</div>

<div class="card">
    <h2>Sesion de juego</h2>
    <div class="stat"><span class="stat-label">Duracion</span><span class="stat-value">$durStr</span></div>
    <div class="stat"><span class="stat-label">Lecturas FPS</span><span class="stat-value">${s.samples.count { it.fps > 0 }}</span></div>
    <div class="stat"><span class="stat-label">Eventos capturados</span><span class="stat-value">${s.events.size}</span></div>
    <div class="stat"><span class="stat-label">Frame drops</span><span class="stat-value ${if (totalDrops > 30) "bad" else if (totalDrops > 10) "warning" else "good"}">$totalDrops</span></div>
</div>

${if (fp != null) """
<div class="card">
    <h2>FPS</h2>
    <table class="ptable">
        <tr><th>P1</th><th>P5</th><th>P50</th><th>P90</th><th>P99</th><th>Min</th><th>Max</th><th>Avg</th></tr>
        <tr>
            <td class="${if (fp.p1 < 20) "bad" else if (fp.p1 < 30) "warning" else "good"}">${fp.p1.toInt()}</td>
            <td class="${if (fp.p5 < 25) "bad" else if (fp.p5 < 35) "warning" else "good"}">${fp.p5.toInt()}</td>
            <td>${fp.p50.toInt()}</td><td>${fp.p90.toInt()}</td><td>${fp.p99.toInt()}</td>
            <td class="bad">${fp.min.toInt()}</td><td class="good">${fp.max.toInt()}</td>
            <td style="color:$colorNota;font-size:1.3em">${fp.avg.toInt()}</td>
        </tr>
    </table>
    <div class="chart-container"><canvas id="fpsChart"></canvas></div>
</div>""" else ""}

${if (ft != null) """
<div class="card">
    <h2>Frame Times</h2>
    <table class="ptable">
        <tr><th>P50</th><th>P90</th><th>P95</th><th>P99</th><th>Avg</th></tr>
        <tr>
            <td>${"%.1f".format(ft.p50)}ms</td><td>${"%.1f".format(ft.p90)}ms</td>
            <td>${"%.1f".format(ft.p95)}ms</td>
            <td class="${if (ft.p99 > 50) "bad" else if (ft.p99 > 33) "warning" else "good"}">${"%.1f".format(ft.p99)}ms</td>
            <td>${"%.1f".format(ft.avg)}ms</td>
        </tr>
    </table>
    <div class="chart-container"><canvas id="ftChart"></canvas></div>
</div>""" else ""}

<div class="card">
    <h2>Memoria</h2>
    <div class="stat"><span class="stat-label">Inicio</span><span class="stat-value">${memStart?.totalMb ?: "?"}MB (Native:${memStart?.nativeHeapMb ?: "?"}MB Java:${memStart?.javaHeapMb ?: "?"}MB)</span></div>
    <div class="stat"><span class="stat-label">Final</span><span class="stat-value">${memEnd?.totalMb ?: "?"}MB (Native:${memEnd?.nativeHeapMb ?: "?"}MB Java:${memEnd?.javaHeapMb ?: "?"}MB)</span></div>
    <div class="stat"><span class="stat-label">Pico</span><span class="stat-value ${if ((memPeak?.totalMb ?: 0) > 2000) "bad" else if ((memPeak?.totalMb ?: 0) > 1500) "warning" else "good"}">${memPeak?.totalMb ?: "?"}MB</span></div>
    <div class="chart-container"><canvas id="memChart"></canvas></div>
</div>

<div class="card">
    <h2>CPU y GPU</h2>
    <div class="stat"><span class="stat-label">CPU promedio</span><span class="stat-value ${if (avgCpu > 85) "bad" else if (avgCpu > 70) "warning" else "good"}">${if (avgCpu >= 0) "${avgCpu.toInt()}%" else "N/A"}</span></div>
    <div class="stat"><span class="stat-label">CPU maximo</span><span class="stat-value">${if (maxCpu >= 0) "${maxCpu.toInt()}%" else "N/A"}</span></div>
    <div class="stat"><span class="stat-label">GPU promedio</span><span class="stat-value">${if (avgGpu >= 0) "${avgGpu.toInt()}%" else "N/A"}</span></div>
    ${if (cpuCores != null && cpuCores.isNotEmpty()) """
    <div style="margin-top:12px">
        <span class="stat-label">Uso por core:</span>
        <div style="display:flex;gap:8px;flex-wrap:wrap;margin-top:8px">
            ${cpuCores.mapIndexed { i, usage -> "<span style='background:rgba(0,212,255,${(usage/100).coerceIn(0.1, 1.0)});padding:4px 10px;border-radius:6px;font-size:0.8em'>C$i: ${usage.toInt()}%</span>" }.joinToString("")}
        </div>
    </div>""" else ""}
    <div class="chart-container"><canvas id="cpuGpuChart"></canvas></div>
</div>

<div class="card">
    <h2>Temperatura</h2>
    <div class="stat"><span class="stat-label">CPU max</span><span class="stat-value ${if (maxTemp > 45) "bad" else if (maxTemp > 40) "warning" else "good"}">${if (maxTemp > 0) "${maxTemp.toInt()}C" else "N/A"}</span></div>
    <div class="chart-container"><canvas id="tempChart"></canvas></div>
</div>

<div class="card">
    <h2>Bateria</h2>
    <div class="stat"><span class="stat-label">Inicio</span><span class="stat-value">${s.batteryStart?.level ?: "N/A"}%</span></div>
    <div class="stat"><span class="stat-label">Final</span><span class="stat-value">${s.batteryEnd?.level ?: "N/A"}%</span></div>
    <div class="stat"><span class="stat-label">Consumo</span><span class="stat-value ${if (batteryDrain > 10) "bad" else if (batteryDrain > 5) "warning" else "good"}">${batteryDrain}% (${"%.1f".format(drainPerMin)}%/min)</span></div>
    <div class="stat"><span class="stat-label">Temperatura</span><span class="stat-value">${s.batteryEnd?.temperature ?: "N/A"}C</span></div>
</div>

$configHtml
$erroresHtml
$warningsHtml
$fpsDropsHtml

${generateEventTimelineHtml(result, s.startTime)}

<div class="card">
    <h2>Problemas</h2>
    $problemasHtml
</div>

</div>

<script>
const chartDefaults = {responsive:true,maintainAspectRatio:false,plugins:{legend:{labels:{color:'#888'}}},scales:{x:{ticks:{color:'#666',maxTicksLimit:15},grid:{color:'rgba(255,255,255,0.05)'}},y:{ticks:{color:'#888'},grid:{color:'rgba(255,255,255,0.08)'}}}};

${if (fpsData.isNotEmpty()) """
new Chart(document.getElementById('fpsChart'),{type:'line',data:{labels:[$fpsLabels],datasets:[{label:'FPS',data:[$fpsData],borderColor:'#00d4ff',backgroundColor:'rgba(0,212,255,0.1)',fill:true,tension:0.3,pointRadius:1}]},options:{...chartDefaults,plugins:{...chartDefaults.plugins,annotation:{annotations:{line30:{type:'line',yMin:30,yMax:30,borderColor:'#ff0044',borderWidth:1,borderDash:[5,5]},line60:{type:'line',yMin:60,yMax:60,borderColor:'#00ff88',borderWidth:1,borderDash:[5,5]}}}}}});
""" else ""}

${if (ftBuckets.isNotEmpty()) """
new Chart(document.getElementById('ftChart'),{type:'bar',data:{labels:['<8ms','8-16ms','16-33ms','33-50ms','50-100ms','>100ms'],datasets:[{label:'Frames',data:[$ftBuckets],backgroundColor:['#00ff88','#88ff00','#ffaa00','#ff6600','#ff0044','#cc0033']}]},options:{...chartDefaults}});
""" else ""}

${if (memTotalData.isNotEmpty()) """
new Chart(document.getElementById('memChart'),{type:'line',data:{labels:[$memLabels],datasets:[{label:'Total PSS',data:[$memTotalData],borderColor:'#00d4ff',tension:0.3,pointRadius:1},{label:'Native Heap',data:[$memNativeData],borderColor:'#ff6600',tension:0.3,pointRadius:1},{label:'Java Heap',data:[$memJavaData],borderColor:'#00ff88',tension:0.3,pointRadius:1}]},options:{...chartDefaults}});
""" else ""}

${if (cpuData.isNotEmpty()) """
new Chart(document.getElementById('cpuGpuChart'),{type:'line',data:{labels:[$cpuGpuLabels],datasets:[{label:'CPU %',data:[$cpuData],borderColor:'#00d4ff',tension:0.3,pointRadius:1},{label:'GPU %',data:[$gpuData],borderColor:'#7b2cbf',tension:0.3,pointRadius:1}]},options:{...chartDefaults,scales:{...chartDefaults.scales,y:{...chartDefaults.scales.y,min:0,max:100}}}});
""" else ""}

${if (tempCpuData.isNotEmpty()) """
new Chart(document.getElementById('tempChart'),{type:'line',data:{labels:[$cpuGpuLabels],datasets:[{label:'CPU',data:[$tempCpuData],borderColor:'#ff0044',tension:0.3,pointRadius:1},{label:'GPU',data:[$tempGpuData],borderColor:'#ff6600',tension:0.3,pointRadius:1},{label:'Skin',data:[$tempSkinData],borderColor:'#ffaa00',tension:0.3,pointRadius:1}]},options:{...chartDefaults}});
""" else ""}
</script>
</body>
</html>"""
}

// ===== Event Timeline HTML =====

fun generateEventTimelineHtml(result: AnalysisResult, startTime: Long): String {
    val events = result.eventosCategorized
    if (events.isEmpty()) return ""

    // Summary by category
    val byCat = events.groupBy { it.category }
        .mapValues { it.value.size }
        .toList()
        .sortedByDescending { it.second }

    val impactEvents = events.filter { it.impactsPerformance }
    val totalImpact = impactEvents.size

    // Category summary cards
    val summaryHtml = byCat.joinToString("") { (cat, count) ->
        val impactCount = events.filter { it.category == cat && it.impactsPerformance }.size
        """<div style="display:inline-flex;align-items:center;gap:8px;background:rgba(255,255,255,0.05);padding:8px 14px;border-radius:8px;border-left:3px solid ${cat.color};margin:4px">
            <span style="color:${cat.color};font-weight:700;font-size:0.85em">${cat.label}</span>
            <span style="color:#fff;font-weight:700">$count</span>
            ${if (impactCount > 0) "<span style='color:#ff6600;font-size:0.75em'>($impactCount afectan rendimiento)</span>" else ""}
        </div>"""
    }

    // Timeline: only show events that impact performance, deduplicated, max 30
    val timelineEvents = impactEvents
        .sortedBy { it.entry.timestamp }
        .distinctBy { "${it.category}_${it.entry.message.take(40)}_${it.entry.timestamp / 5000}" } // dedup within 5s windows
        .take(30)

    val timelineHtml = if (timelineEvents.isEmpty()) {
        "<p style='color:#888;font-size:0.9em;margin-top:12px'>No se detectaron eventos que afecten directamente al rendimiento.</p>"
    } else {
        """<div style="margin-top:16px;max-height:500px;overflow-y:auto">
            ${timelineEvents.joinToString("") { ev ->
                val t = ((ev.entry.timestamp - startTime) / 1000).let { if (it < 0) 0 else it }
                val levelIcon = when (ev.entry.level) {
                    LogLevel.ERROR, LogLevel.FATAL -> "E"
                    LogLevel.WARN -> "W"
                    else -> "I"
                }
                val borderColor = if (ev.impactsPerformance) ev.category.color else "rgba(255,255,255,0.1)"
                val msgShort = escHtml(if (ev.entry.message.length > 120) ev.entry.message.take(117) + "..." else ev.entry.message)
                """<div style="display:flex;gap:12px;padding:10px;margin:4px 0;background:rgba(255,255,255,0.03);border-radius:6px;border-left:3px solid $borderColor;align-items:flex-start">
                    <div style="min-width:50px;text-align:right">
                        <div style="color:#888;font-size:0.8em;font-weight:700">${t}s</div>
                        <div style="color:${ev.category.color};font-size:0.65em;font-weight:700">${ev.category.icon}</div>
                    </div>
                    <div style="flex:1">
                        <div style="color:${ev.category.color};font-weight:600;font-size:0.9em">${escHtml(ev.description)}</div>
                        <div style="color:#666;font-size:0.7em;font-family:monospace;margin-top:4px;word-break:break-all">[${escHtml(ev.entry.tag)}] $msgShort</div>
                    </div>
                    <div style="min-width:24px">
                        <span style="color:${if (ev.entry.level >= LogLevel.ERROR) "#ff0044" else if (ev.entry.level == LogLevel.WARN) "#ffaa00" else "#888"};font-size:0.75em;font-weight:700">$levelIcon</span>
                    </div>
                </div>"""
            }}
        </div>"""
    }

    return """
    <div class="card" style="border-left:4px solid #00d4ff">
        <h2>Timeline de eventos de rendimiento</h2>
        <p style="color:#888;font-size:0.85em;margin-bottom:12px">
            ${events.size} eventos relevantes detectados, <strong style="color:#ff6600">$totalImpact afectan directamente al rendimiento</strong>.
        </p>
        <div style="display:flex;flex-wrap:wrap;gap:4px;margin-bottom:16px">
            $summaryHtml
        </div>
        <h3 style="color:#00d4ff;font-size:0.95em;margin-bottom:8px">Eventos que afectan al rendimiento (cronologico)</h3>
        $timelineHtml
    </div>"""
}

// ===== JSON Export =====

fun generateJson(result: AnalysisResult): String {
    val s = result.session
    val fp = result.fpsPercentiles
    val ft = result.frameTimePercentiles
    val memPeak = s.samples.mapNotNull { it.memoryInfo }.maxByOrNull { it.totalPssKb }
    val avgCpu = s.samples.mapNotNull { it.cpuSnapshot?.totalUsage }.average().let { if (it.isNaN()) null else it }

    return """{
  "device": {"model":"${escJson(s.deviceSpecs.model)}","cpu":"${escJson(s.deviceSpecs.cpu)}","gpu":"${escJson(s.deviceSpecs.gpuModel)}","ram_gb":${"%.1f".format(s.deviceSpecs.ram/(1024.0*1024*1024))}},
  "session": {"game":"${escJson(s.gamePackage)}","duration_seconds":${s.durationSeconds},"timestamp":"${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(s.startTime))}"},
  "fps": ${if (fp != null) """{"avg":${fp.avg.toInt()},"min":${fp.min.toInt()},"max":${fp.max.toInt()},"p1":${fp.p1.toInt()},"p5":${fp.p5.toInt()},"p50":${fp.p50.toInt()},"p90":${fp.p90.toInt()},"p99":${fp.p99.toInt()}}""" else "null"},
  "frame_times": ${if (ft != null) """{"avg_ms":${"%.1f".format(ft.avg)},"p50_ms":${"%.1f".format(ft.p50)},"p99_ms":${"%.1f".format(ft.p99)}}""" else "null"},
  "memory": {"peak_mb":${memPeak?.totalMb ?: 0},"native_peak_mb":${memPeak?.nativeHeapMb ?: 0},"java_peak_mb":${memPeak?.javaHeapMb ?: 0}},
  "cpu_avg_percent":${avgCpu?.toInt() ?: "null"},
  "battery": {"start":${s.batteryStart?.level ?: 0},"end":${s.batteryEnd?.level ?: 0},"drain":${(s.batteryStart?.level ?: 0) - (s.batteryEnd?.level ?: 0)}},
  "frame_drops":${s.missedFramesEnd - s.missedFramesStart},
  "problems":${result.problemas.size},
  "grade":"${result.nota}",
  "fps_samples":[${s.samples.filter { it.fps > 0 }.joinToString(",") { it.fps.toString() }}]
}"""
}
