package com.gameperf

import com.gameperf.core.*
import java.awt.Desktop
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.URI

fun main(args: Array<String>) {
    println("""
        ╔══════════════════════════════════════════════════════════════╗
        ║     🎮 Android Game Performance Tool v4.0                    ║
        ║         Análisis PROFUNDO y FIABLE                        ║
        ╚══════════════════════════════════════════════════════════════╝
    """.trimIndent())
    
    val connector = AdbConnector()
    
    if (!connector.isAdbAvailable()) {
        println("❌ ADB no disponible. Instala Android SDK.")
        return
    }
    
    println("\n🔍 Buscando dispositivos...")
    val devices = connector.listDevices()
    if (devices.isEmpty()) {
        println("❌ No hay dispositivos conectados.")
        return
    }
    
    val device = devices.first()
    println("✅ Dispositivo: ${device.model}")
    
    // Obtener specs
    val specs = connector.getDeviceSpecs(device.id)
    val batteryStart = connector.getBatteryLevel(device.id)
    
    println("\n📱 SPECS DEL DISPOSITIVO:")
    println("   Modelo: ${specs.model}")
    println("   Fabricante: ${specs.manufacturer}")
    println("   CPU: ${specs.cpu}")
    println("   RAM: ${specs.ram / (1024*1024*1024)} GB")
    println("   Núcleos: ${specs.cores}")
    println("   Resolución: ${specs.resolution}")
    println("   SDK: ${specs.sdkVersion}")
    println("   Batería: ${batteryStart?.level}%")
    
    // Detectar juego - esperar hasta 30 segundos si no hay
    var gamePackage: String? = null
    println("\n🔎 Buscando juego en primer plano...")
    
    for (i in 1..30) {
        gamePackage = connector.getGamePackage(device.id)
        if (gamePackage != null) break
        print(".")
        System.out.flush()
        Thread.sleep(1000)
    }
    
    if (gamePackage == null) {
        println("\n❌ No se detectó ningún juego en primer plano.")
        println("   Abre un juego y ejecuta de nuevo.")
        return
    }
    
    println("   $gamePackage")
    
    // Capturar estado inicial
    val memStart = connector.getProcessStats(device.id, gamePackage)
    val memStartMb = memStart?.pssKb?.div(1024) ?: 0L
    val missedStart = connector.getMissedFrames(device.id)
    
    println("   Memoria inicial: $memStartMb MB")
    println("   Frame drops iniciales: $missedStart")
    
    println("\n" + "═".repeat(60))
    println("🎮 PRUEBA DE RENDIMIENTO")
    println("═".repeat(60))
    println()
    println("   📱 Juego: $gamePackage")
    println("   ⏱️ Muestreo: cada 100ms (frame-by-frame)")
    println()
    println("   🎮 Juega normalmente...")
    println("   ⏹️  Pulsa Ctrl+C para DETENER y generar informe")
    println("   ⏱️  O espera 60 segundos (por defecto)\n")
    println("   ----------------------------------------------------------")
    println()
    
    // Duración: si pasa argumento, usa ese tiempo. Si no, corre indefinidamente hasta que el usuario pare.
    val duracionMax = if (args.isNotEmpty()) args[0].toIntOrNull() ?: -1 else -1
    
    if (duracionMax > 0) {
        println("   ⏳ Duración: $duracionMax segundos (Ctrl+C para parar antes)\n")
    } else {
        println("   ⏳Duración: INDEFINIDA (Ctrl+C o ENTER para parar)\n")
    }
    
    // Intervalo de muestreo: 100ms = 10 muestras por segundo
    val intervaloMs = 100L
    val muestrasPorSegundo = 1000 / intervaloMs
    
    // Recolectar datos - muestra cada 100ms para capturar eventos rápidos
    val fpsSamples = mutableListOf<Int>()
    val memSamples = mutableListOf<Long>()
    val eventosPorSegundo = mutableListOf<Int>()
    val events = mutableListOf<LogEntry>()
    var missed = missedStart
    var muestras = 0
    var segundos = 0
    var eventosEsteSegundo = 0
    val startTime = System.currentTimeMillis()
    var seguir = true
    
    println("   Muestras: 0 | FPS: - | Mem: - | Events: 0")
    
    // Loop principal - corre hasta que el usuario pare (o hasta duracionMax si se especificó)
    try {
        while (seguir && (duracionMax <= 0 || segundos < duracionMax)) {
            muestras++
            
            // FPS instantáneo
            val fps = connector.getGameFps(device.id, gamePackage)
            if (fps > 0) fpsSamples.add(fps)
            
            // Memoria actual
            val mem = connector.getProcessStats(device.id, gamePackage)
            if (mem != null) memSamples.add(mem.pssKb / 1024)
            
            // Frame drops
            val currentMissed = connector.getMissedFrames(device.id)
            if (currentMissed > missed) {
                val nuevosDrops = currentMissed - missed
                println("   ⚠️ +$nuevosDrops frame drops detectados!")
                missed = currentMissed
        }
        
        // Eventos - capturar TODOS ABSOLUTAMENTE TODOS los logs
        val logs = connector.getGameLogs(device.id, gamePackage, 500)
        
        // Agregar TODOS los eventos sin filtrar
        val nuevosEventos = logs.filter { nuevo ->
            events.count { it.message == nuevo.message } < 10 // Allow up to 10 duplicates
        }
        
        if (nuevosEventos.isNotEmpty()) {
            val totalAntes = events.size
            events.addAll(nuevosEventos)
            eventosEsteSegundo += nuevosEventos.size
            
            // Mostrar solo resumen para no saturar
            if (events.size - totalAntes > 0) {
                val errores = nuevosEventos.count { it.level == LogLevel.ERROR }
                val warnings = nuevosEventos.count { it.level == LogLevel.WARN }
                val otros = nuevosEventos.size - errores - warnings
                
                var msg = "   📊 +${nuevosEventos.size} eventos"
                if (errores > 0) msg += " (🔴$errores)"
                if (warnings > 0) msg += " (🟡$warnings)"
                if (otros > 0) msg += " (ℹ️$otros)"
                println(msg)
            }
        }
        
        // Verificar si cambió el segundo
        val tiempoActual = (System.currentTimeMillis() - startTime) / 1000
        if (tiempoActual > segundos) {
            segundos = tiempoActual.toInt()
            eventosPorSegundo.add(eventosEsteSegundo)
            eventosEsteSegundo = 0
            
            val fpsActual = fpsSamples.takeLast(muestrasPorSegundo.toInt()).average()
            val memActual = memSamples.lastOrNull() ?: 0
            println("   ⏱️ ${segundos}s | Muestras: $muestras | FPS: ${fpsActual.toInt()} | Mem: ${memActual}MB | Events: ${events.size}")
        }
        
        // Verificar si el usuario quiere parar (sin bloquear)
        try {
            if (System.`in`.available() > 0) {
                val input = System.`in`.read()
                if (input == 10 || input == 13) { // ENTER
                    println("\n   ⏹️  Prueba detenida por el usuario")
                    seguir = false
                }
            }
        } catch (e: Exception) { }
        
        Thread.sleep(intervaloMs)
        }
    } catch (e: InterruptedException) {
        println("\n\n⏹️  Prueba detenida por el usuario (Ctrl+C)")
    }
    
    println("   ----------------------------------------------------------")
    println("\n📊 PROCESANDO DATOS...")
    
    // Capturar estado final
    val batteryEnd = connector.getBatteryLevel(device.id)
    val memEnd = connector.getProcessStats(device.id, gamePackage)
    val memEndMb = memEnd?.pssKb?.div(1024) ?: 0L
    val missedEnd = connector.getMissedFrames(device.id)
    val totalDrops = missedEnd - missedStart
    
    // ANALISIS
    val avgFps = if (fpsSamples.isNotEmpty()) fpsSamples.average() else 0.0
    val minFps = fpsSamples.minOrNull() ?: 0
    val maxFps = fpsSamples.maxOrNull() ?: 0
    val avgMem = if (memSamples.isNotEmpty()) memSamples.average().toLong() else 0L
    val peakMem = memSamples.maxOrNull() ?: 0L
    val memGrowth = memEndMb - memStartMb
    
    // Estabilidad FPS
    val stability = if (fpsSamples.size > 1) {
        val avg = fpsSamples.average()
        val variance = fpsSamples.map { (it - avg) * (it - avg) }.average()
        val stdDev = kotlin.math.sqrt(variance)
        ((1 - stdDev / avg) * 100).coerceIn(0.0, 100.0)
    } else 100.0
    
    // Analizar eventos
    val errorEvents = events.filter { it.level == LogLevel.ERROR }
    val warnEvents = events.filter { it.level == LogLevel.WARN }
    val gcEvents = events.filter { it.message.contains("gc", ignoreCase = true) }
    
    // ANALISIS INTELIGENTE - Solo lo que importa para juegos
    
    // Filtrar: solo errores/warnings relevantes para gaming
    val eventosRelevantes = events.filter { e ->
        val msg = e.message.lowercase()
        val tag = e.tag.lowercase()
        
        // CRÍTICO para juegos - siempre incluir
        val esCritico = msg.contains("jank") || msg.contains("hitch") || msg.contains("stutter") ||
                       msg.contains("anr") || msg.contains("not responding") ||
                       msg.contains("out of memory") || msg.contains("oom") ||
                       msg.contains("thermal") && (msg.contains("throttl") || msg.contains("cpu") || msg.contains("gpu")) ||
                       msg.contains("crash") || msg.contains("fatal") || msg.contains("died")
        
        // IMPORTANTE para juegos
        val esImportante = msg.contains("underrun") || msg.contains("audio") ||
                          msg.contains("gc ") || msg.contains("gc_") || msg.contains("garbage") ||
                          msg.contains("trimmemory") || msg.contains("memory pressure") ||
                          msg.contains("memalloc")
        
        // SurfaceFlinger errors
        val esGraphics = msg.contains("surfaceflinger") || msg.contains("hwc-") ||
                        msg.contains("gpu") || msg.contains("render") || msg.contains("opengl")
        
        // Network para juegos online
        val esNetwork = (msg.contains("timeout") || msg.contains("socket")) && 
                       (msg.contains("http") || msg.contains("connect") || msg.contains("game"))
        
        // Tags del juego
        val esDelJuego = tag.contains("zombie") || tag.contains("survival") || 
                        tag.contains("vivastudios") || tag.contains("freefire")
        
        esCritico || esImportante || esGraphics || esNetwork || esDelJuego
    }
    
    // Agrupar solo eventos relevantes
    val erroresRelevantes = eventosRelevantes.filter { it.level == LogLevel.ERROR }
    val warningsRelevantes = eventosRelevantes.filter { it.level == LogLevel.WARN }
    
    // Contadores para problemas
    val gcCount = erroresRelevantes.count { it.message.contains("gc", ignoreCase = true) } + 
                  warningsRelevantes.count { it.message.contains("gc", ignoreCase = true) }
    val audioIssues = erroresRelevantes.count { it.message.contains("audio") || it.message.contains("underrun") } +
                      warningsRelevantes.count { it.message.contains("audio") || it.message.contains("underrun") }
    val memoryIssues = erroresRelevantes.count { it.message.contains("memory") || it.message.contains("oom") } +
                       warningsRelevantes.count { it.message.contains("memory") || it.message.contains("trim") }
    
    val erroresAgrupados = erroresRelevantes.groupBy { it.message.take(60) }
        .filter { it.value.size >= 2 }
        .mapValues { it.value.size }
        .toList()
        .sortedByDescending { it.second }
        .take(5)
    
    val advertenciasAgrupadas = warningsRelevantes.groupBy { it.message.take(60) }
        .filter { it.value.size >= 3 }
        .mapValues { it.value.size }
        .toList()
        .sortedByDescending { it.second }
        .take(5)
    
    // Función para traducir mensajes técnicos
    fun traducirLog(mensaje: String, tag: String): String {
        val msg = mensaje.lowercase()
        val tagL = tag.lowercase()
        
        return when {
            // Audio - MUY IMPORTANTE para games
            msg.contains("underrun") -> 
                "🔊 AUDIO LAG - El dispositivo no puede procesar audio a tiempo. Síntoma de falta de recursos."
            msg.contains("audiopolicy") || msg.contains("audioflinger") ->
                "🔊 Problemas con el sistema de audio."
            msg.contains("audio_hw") ->
                "🔊 Error del hardware de audio."
            
            // GC - IMPORTANTE
            msg.contains("gc ") || msg.contains("gc_") || msg.contains("concurrent mark") || msg.contains("garbage") ->
                "🗑️ RECOLECTOR DE BASURA trabajando - puede causar micro-lags si es muy frecuente."
            
            // Thermal - MUY IMPORTANTE
            msg.contains("thermal") && (msg.contains("throttl") || msg.contains("cpu") || msg.contains("gpu")) ->
                "🌡️ THERMAL THROTTLING - El dispositivo se sobrecalienta y reduce CPU/GPU. AFECTA FPS."
            msg.contains("thermal") && msg.contains("skin") ->
                "🌡️ Temperatura del dispositivo subiendo."
            
            // Memory - MUY IMPORTANTE
            msg.contains("oom") || msg.contains("out of memory") ->
                "💾 MEMORIA LLENA - El juego necesita más RAM. Puede cerrar el juego."
            msg.contains("low memory") || msg.contains("lowmemory") ->
                "💾 Memoria baja - el sistema puede cerrar apps en segundo plano."
            msg.contains("trimmemory") || msg.contains("ontrimmemory") ->
                "💾 El sistema está LIBERANDO MEMORIA. Puede causar lags temporales."
            msg.contains("memalloc") || msg.contains("mm_alloc") ->
                "💾 Error asignando memoria."
            
            // Frame drops / Graphics - MUY IMPORTANTE
            msg.contains("jank") || msg.contains("hitch") || msg.contains("stutter") ->
                "🎬 JANK detected - El juego ha perdido frames visiblemente."
            msg.contains("dropped") && msg.contains("frame") ->
                "🎬 Frames perdidos detectados."
            msg.contains("surfaceflinger") ->
                "🖥️ Error del sistema gráfico (SurfaceFlinger)."
            msg.contains("hwc-display") || msg.contains("hwcomposer") ->
                "🖥️ Error del compositor de pantalla."
            
            // Network - IMPORTANTE para juegos online
            msg.contains("timeout") && (msg.contains("socket") || msg.contains("http") || msg.contains("connect")) ->
                "🌐 TIMEOUT - La conexión se ha colgado. Puede causar lags en juegos online."
            msg.contains("network") || msg.contains("connectivity") ->
                "🌐 Estado de red cambiado."
            msg.contains("dns") ->
                "🌐 Error resolviendo DNS - problemas de internet."
            msg.contains("httpdns") || msg.contains("httpendpoint") ->
                "🌐 El juego está haciendo peticiones de red."
            
            // ANR / Crashes - CRÍTICO
            msg.contains("anr") || msg.contains("application not responding") ->
                "⛔ APP COLGADA - El juego no responde. Puede crashar."
            msg.contains("fatal") || msg.contains("crash") ->
                "☠️ ERROR FATAL - El juego puede cerrarse."
            msg.contains("died") && msg.contains("process") ->
                "☠️ Proceso muerto - el juego puede haber cerrado."
            
            // Binder/IPC - menos importante
            msg.contains("binder") && msg.contains("transaction") ->
                "🔗 Comunicación entre procesos."
            
            // System services - generalmente no importante
            msg.contains("activitymanager") ->
                "📱 Gestor de actividades del sistema."
            msg.contains("contentprovider") || msg.contains("providerscache") ->
                "📱 Proveedor de contenido del sistema."
            msg.contains("packagesettings") || msg.contains("packagemanager") ->
                "📱 Gestor de paquetes."
            
            // Telephony - generalmente no importante para games
            msg.contains("telephony") || msg.contains("kny") || msg.contains("satellite") ->
                "📡 Sistema de telefonía (no afecta al juego)."
            
            // Game specific - IMPORTANTE
            tagL.contains("zombie") || tagL.contains("survival") || tagL.contains("freefire") || 
            tagL.contains("vivastudios") || msg.contains("zombie.survival") ->
                "🎮 LOGS DEL JUEGO."
            
            // Power/Battery - moderado
            msg.contains("battery") || msg.contains("power") ->
                "🔋 Estado de batería/power."
            msg.contains("wakelock") ->
                "🔋 El dispositivo está despierto (no en suspensión)."
            
            // WiFi/Bluetooth - menos importante
            msg.contains("wifi") || msg.contains("wlan") || msg.contains("supplicant") ->
                "📶 WiFi."
            msg.contains("bluetooth") ->
                "📶 Bluetooth."
            
            // Default - try to give useful info
            else -> "ℹ️ Evento del sistema."
        }
    }
    
    // Detectar problemas
    val problemas = mutableListOf<Problema>()
    
    if (avgFps < 30) {
        problemas.add(Problema("FPS_BAJO", "Alto", "FPS promedio de ${avgFps.toInt()} - Muy bajo"))
    } else if (avgFps < 45) {
        problemas.add(Problema("FPS_BAJO", "Medio", "FPS promedio de ${avgFps.toInt()} - Bajo"))
    }
    
    if (stability < 70) {
        problemas.add(Problema("FPS_INESTABLE", "Medio", "Estabilidad del ${stability.toInt()}% - Inconsistente"))
    }
    
    if (totalDrops > 30) {
        problemas.add(Problema("FRAME_DROPS", "Alto", "$totalDrops frames perdidos"))
    }
    
    if (peakMem > 2000) {
        problemas.add(Problema("MEMORIA_ALTA", "Alto", "Pico de $peakMem MB - Muy alto"))
    } else if (peakMem > 1500) {
        problemas.add(Problema("MEMORIA_ALTA", "Medio", "Pico de $peakMem MB - Alto"))
    }
    
    if (memGrowth > 500) {
        problemas.add(Problema("MEMORY_LEAK", "Alto", "Crecimiento de $memGrowth MB - Posible leak"))
    }
    
    if (gcCount > 10) {
        problemas.add(Problema("GC_FRECUENTE", "Medio", "$gcCount eventos GC - Puede causar micro-lags"))
    }
    
    if (audioIssues > 5) {
        problemas.add(Problema("AUDIO_LAG", "Medio", "$audioIssues problemas de audio - puede causar lags"))
    }
    
    if (erroresRelevantes.size > 3) {
        problemas.add(Problema("ERRORES", "Alto", "${erroresRelevantes.size} errores críticos"))
    }
    
    // Calcular nota
    val nota = calcularNota(avgFps, stability, problemas)
    
    // GENERAR RESUMEN
    println("\n" + "═".repeat(60))
    println("📋 RESULTADOS DEL ANÁLISIS")
    println("═".repeat(60))
    
    println("\n📊 MUESTREO:")
    println("   Muestras tomadas: $muestras")
    println("   Intervalo: ${intervaloMs}ms (${muestrasPorSegundo} muestras/seg)")
    println("   Duración: $segundos segundos")
    
    println("\n🎮 RENDIMIENTO:")
    println("   FPS Promedio: ${avgFps.toInt()}")
    println("   FPS Mínimo: $minFps")
    println("   FPS Máximo: $maxFps")
    println("   Estabilidad: ${stability.toInt()}%")
    println("   Frame Drops: $totalDrops")
    
    println("\n💾 MEMORIA:")
    println("   Inicio: $memStartMb MB")
    println("   Final: $memEndMb MB")
    println("   Pico: $peakMem MB")
    println("   Crecimiento: $memGrowth MB")
    
    println("\n🔋 BATERÍA:")
    println("   Inicio: ${batteryStart?.level}%")
    println("   Final: ${batteryEnd?.level}%")
    println("   Consumo: ${(batteryStart?.level ?: 0) - (batteryEnd?.level ?: 0)}%")
    
    println("\n📊 ANÁLISIS DE RENDIMIENTO:")
    println("   Total eventos capturados: ${events.size}")
    println("   Eventos relevantes para gaming: ${eventosRelevantes.size}")
    
    println()
    if (gcCount > 0) println("   🗑️ GC (basura): $gcCount eventos")
    if (audioIssues > 0) println("   🔊 Problemas audio: $audioIssues eventos")  
    // =============================================
    // EXPLICACIÓN DETALLADA PARA NO PROGRAMADORES
    // =============================================
    println("\n" + "═".repeat(60))
    println("📖 ANÁLISIS DETALLADO")
    println("═".repeat(60))
    
    // ============== FPS ==============
    println("\n🎮 ¿QUÈ SON LOS FPS?")
    println("   Los FPS (Frames Per Second) son las imágenes que el juego")
    println("   muestra por segundo. Cuantos más FPS, más fluido se ve.")
    println("   - 60 FPS = muy fluido (ideal)")
    println("   - 30-45 FPS = aceptable, pero se nota")
    println("   - <30 FPS = entrecortado")
    println()
    println("   TU RESULTADO: ${avgFps.toInt()} FPS")
    if (avgFps >= 55 && stability >= 90) {
        println("   ✅ PERFECTO - El juego va super fluido")
    } else if (avgFps >= 45) {
        println("   ✅ BIEN - El juego va bien")
    } else if (avgFps >= 30) {
        println("   🟡 REGULAR - Se nota algo entrecortado")
    } else {
        println("   🔴 MALO - Va muy lento")
    }
    
    // ============== AUDIO ==============
    if (audioIssues > 0) {
        println("\n" + "─".repeat(50))
        println("🔊 PROBLEMAS DE AUDIO (underruns)")
        println("─".repeat(50))
        println("   QUÈ ES: El dispositivo no puede procesar el audio")
        println("   lo suficientemente rápido. El sonido se 'corta' o se")
        println("   retrasa respecto a la imagen.")
        println()
        println("   Por qué pasa: El procesador está demasiado ocupado")
        println("   haciendo otras cosas (gráficos, juego) y no da")
        println("   prioridad al audio.")
        println()
        println("   TU RESULTADO: $audioIssues problemas detectados")
        if (audioIssues > 50) {
            println("   ⚠️ IMPORTANTE: Muchos problemas de audio.")
            println("   Esto puede afectar a tu experiencia de juego.")
            println("   En un battle royale necesitas oír pasos, disparos...")
        } else if (audioIssues > 10) {
            println("   🟡 Algunos problemas, pero probablemente no se note mucho.")
        } else {
            println("   ✅ Apenas hay problemas de audio.")
        }
    }
    
    // ============== GRAPHICS ==============
    val graphicsIssues = erroresRelevantes.count { it.message.contains("surfaceflinger") || it.message.contains("hwc") } +
                          warningsRelevantes.count { it.message.contains("surfaceflinger") || it.message.contains("hwc") }
    if (graphicsIssues > 0) {
        println("\n" + "─".repeat(50))
        println("🖥️ ERRORES DE GRÁFICOS (SurfaceFlinger)")
        println("─".repeat(50))
        println("   QUÈ ES: SurfaceFlinger es el programa del móvil que")
        println("   se encarga de pintar todo en la pantalla.")
        println()
        println("   Por qué pasa: El móvil tiene problemas para gestionar")
        println("   los gráficos. Puede ser por:")
        println("   - La pantalla está configurada de forma rara")
        println("   - El chip gráfico está ocupado")
        println("   - Conflictos con drivers")
        println()
        println("   TU RESULTADO: $graphicsIssues errores")
        println("   ⚠️ Esto PUEDE causar tirones en los gráficos.")
    }
    
    // ============== GC ==============
    if (gcCount > 0) {
        println("\n" + "─".repeat(50))
        println("🗑️ RECOLECTOR DE BASURA (GC - Garbage Collection)")
        println("─".repeat(50))
        println("   QUÈ ES: Cuando el juego usa memoria, crea 'basura'")
        println("   (datos antiguos que ya no necesita). El GC limpia esa")
        println("   basura para liberar memoria.")
        println()
        println("   Por qué importa: Cuando el GC funciona, el juego")
        println("   puede 'tirarse' un segundo o medio pensando, lo que")
        println("   causa un mini-tirón.")
        println()
        println("   TU RESULTADO: $gcCount ejecuciones del GC")
        if (gcCount > 20) {
            println("   ⚠️ MUY FRECUENTE - Puede causar tirones.")
        } else if (gcCount > 10) {
            println("   🟡 Algo frecuente, pero normal en Android.")
        } else {
            println("   ✅ Normal, apenas ocurre.")
        }
    }
    
    // ============== MEMORY ==============
    println("\n" + "─".repeat(50))
    println("💾 MEMORIA RAM")
    println("─".repeat(50))
    println("   QUÈ ES: La RAM es la memoria rápida del móvil.")
    println("   Cuanta más usa el juego, menos le queda al sistema.")
    println()
    println("   TU RESULTADO:")
    println("   - Inicio: $memStartMb MB")
    println("   - Final: $memEndMb MB") 
    println("   - Pico: $peakMem MB")
    println("   - Tu móvil tiene: ${specs.ram / (1024*1024*1024)} GB de RAM total")
    println()
    val percentUsed = (peakMem * 100) / (specs.ram / 1024)
    println("   El juego usa aproximadamente el $percentUsed% de tu RAM.")
    if (peakMem > 2000) {
        println("   ⚠️ MUCHA MEMORIA - Puede causar problemas si abres otras apps.")
    } else if (peakMem > 1500) {
        println("   🟡 Bastante memoria, pero aceptable.")
    } else {
        println("   ✅ Memoria razonable.")
    }
    
    // ============== FRAME DROPS ==============
    if (totalDrops > 0) {
        println("\n" + "─".repeat(50))
        println("🎬 FRAMES PERDIDOS (Frame Drops)")
        println("─".repeat(50))
        println("   QUÈ ES: Un 'frame' es una imagen. Cuando el juego")
        println("   no puede dibujar una imagen a tiempo, la 'pierde'.")
        println("   Eso se nota como un tirón o freeze.")
        println()
        println("   TU RESULTADO: $totalDrops frames perdidos")
        if (totalDrops > 30) {
            println("   🔴 MUY MALO - Muchos tirones visibles.")
        } else if (totalDrops > 10) {
            println("   🟡 Algunos tirones, probablemente no se note mucho.")
        } else {
            println("   ✅ Apenas ha perdido frames.")
        }
    }
    
    // ============== RESUMEN FINAL ==============
    println("\n" + "═".repeat(60))
    println("📊 RESUMEN FINAL")
    println("═".repeat(60))
    if (avgFps >= 45 && stability >= 85 && totalDrops < 5 && audioIssues < 10) {
        println("   ✅ El juego funciona BIEN en este dispositivo.")
        println("   Lo recomiendo para jugar.")
    } else if (avgFps >= 30 && stability >= 70) {
        println("   🟡 El juego funciona, pero tiene limitaciones.")
        println("   Puede que notes algunos tirones o problemas de audio.")
    } else {
        println("   🔴 El juego NO funciona bien en este dispositivo.")
        println("   Considera bajar la calidad gráfica o usar otro dispositivo.")
    }
    
    // Errores específicos
    if (erroresAgrupados.isNotEmpty()) {
        println("\n🚨 ERRORES TÉCNICOS ENCONTRADOS:")
        for ((msg, count) in erroresAgrupados) {
            val traduccion = traducirLog(msg, erroresRelevantes.find { it.message.take(60) == msg }?.tag ?: "")
            println("   🔴 ($count veces): $traduccion")
        }
    }
    
    if (advertenciasAgrupadas.isNotEmpty()) {
        println("\n⚠️ WARNINGSS (advertencias):")
        for ((msg, count) in advertenciasAgrupadas) {
            val traduccion = traducirLog(msg, warningsRelevantes.find { it.message.take(60) == msg }?.tag ?: "")
            println("   🟡 ($count veces): $traduccion")
        }
    }
    // Mostrar ultimos eventos relevantes (solo los importantes)
    if (eventosRelevantes.isNotEmpty()) {
        println("\n   📌 ÚLTIMOS EVENTOS RELEVANTES:")
        eventosRelevantes.takeLast(8).forEach { e ->
            val icono = when (e.level) {
                LogLevel.ERROR -> "🔴"
                LogLevel.WARN -> "🟡"
                LogLevel.FATAL -> "☠️"
                else -> "ℹ️"
            }
            val trad = traducirLog(e.message, e.tag)
            println("   $icono $trad")
        }
    }
    
    println("\n📈 NOTA FINAL: $nota")
    
    // Generar HTML
    val html = generarHtml(
        gamePackage = gamePackage,
        specs = specs,
        duration = segundos,
        avgFps = avgFps,
        minFps = minFps,
        maxFps = maxFps,
        stability = stability,
        drops = totalDrops,
        memStart = memStartMb,
        memEnd = memEndMb,
        peakMem = peakMem,
        memGrowth = memGrowth,
        batteryStart = batteryStart?.level ?: 0,
        batteryEnd = batteryEnd?.level ?: 0,
        errors = erroresRelevantes.size,
        warnings = warningsRelevantes.size,
        gcEvents = gcCount,
        problemas = problemas,
        nota = nota,
        fpsSamples = fpsSamples,
        eventos = eventosRelevantes,
        muestras = muestras,
        muestrasPorSegundo = muestrasPorSegundo.toInt(),
        erroresAgrupados = erroresAgrupados,
        advertenciasAgrupadas = advertenciasAgrupadas
    )
    
    val timestamp = System.currentTimeMillis()
    val archivo = File("informe_$timestamp.html")
    archivo.writeText(html)
    
    println("\n✅ INFORME GENERADO: ${archivo.name}")
    
    // Abrir en navegador
    try {
        Desktop.getDesktop().browse(URI("file://${archivo.absolutePath}"))
    } catch (e: Exception) { }
}

data class Problema(val tipo: String, val severidad: String, val descripcion: String)

fun calcularNota(avgFps: Double, stability: Double, problemas: List<Problema>): Char {
    var score = 100.0
    
    score -= when {
        avgFps >= 55 -> 0
        avgFps >= 45 -> 10
        avgFps >= 35 -> 20
        else -> 35
    }
    
    score -= when {
        stability >= 85 -> 0
        stability >= 70 -> 10
        else -> 20
    }
    
    for (p in problemas) {
        score -= when (p.severidad) {
            "Alto" -> 15
            "Medio" -> 8
            else -> 3
        }
    }
    
    return when {
        score >= 85 -> 'A'
        score >= 70 -> 'B'
        score >= 55 -> 'C'
        score >= 40 -> 'D'
        else -> 'F'
    }
}

fun generarHtml(
    gamePackage: String,
    specs: DeviceSpecs,
    duration: Int,
    avgFps: Double,
    minFps: Int,
    maxFps: Int,
    stability: Double,
    drops: Int,
    memStart: Long,
    memEnd: Long,
    peakMem: Long,
    memGrowth: Long,
    batteryStart: Int,
    batteryEnd: Int,
    errors: Int,
    warnings: Int,
    gcEvents: Int,
    problemas: List<Problema>,
    nota: Char,
    fpsSamples: List<Int>,
    eventos: List<LogEntry> = emptyList(),
    muestras: Int = 0,
    muestrasPorSegundo: Int = 1,
    erroresAgrupados: List<Pair<String, Int>> = emptyList(),
    advertenciasAgrupadas: List<Pair<String, Int>> = emptyList()
): String {
    val colorNota = when (nota) {
        'A' -> "#00ff88"
        'B' -> "#88ff00"
        'C' -> "#ffaa00"
        'D' -> "#ff6600"
        else -> "#ff0044"
    }
    
    val problemasHtml = if (problemas.isEmpty()) {
        "<p style='color:#00ff88'>✅ Sin problemas críticos</p>"
    } else {
        problemas.joinToString("") { p ->
            val color = when (p.severidad) {
                "Alto" -> "#ff0044"
                "Medio" -> "#ffaa00"
                else -> "#00d4ff"
            }
            """
            <div style="background:rgba(255,0,68,0.1);border-left:4px solid $color;padding:12px;margin:8px 0;border-radius:4px">
                <strong style="color:$color">${p.descripcion}</strong>
            </div>
            """.trimIndent()
        }
    }
    
    // Crear datos del gráfico FPS
    val fpsData = fpsSamples.joinToString(",")
    
    return """
<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Informe - $gamePackage</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%); min-height: 100vh; color: #fff; padding: 20px; }
        .container { max-width: 800px; margin: 0 auto; }
        h1 { text-align: center; margin-bottom: 10px; background: linear-gradient(90deg, #00d4ff, #7b2cbf); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
        .subtitle { text-align: center; color: #888; margin-bottom: 30px; }
        .grade { text-align: center; font-size: 8rem; font-weight: bold; color: $colorNota; text-shadow: 0 0 50px $colorNota; margin: 20px 0; }
        .grade-label { text-align: center; color: #888; font-size: 1.2rem; margin-bottom: 30px; }
        .card { background: rgba(255,255,255,0.05); border-radius: 16px; padding: 20px; margin-bottom: 16px; border: 1px solid rgba(255,255,255,0.1); }
        .card h2 { color: #00d4ff; margin-bottom: 16px; font-size: 1.2rem; }
        .stat { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px solid rgba(255,255,255,0.1); }
        .stat:last-child { border-bottom: none; }
        .stat-label { color: #888; }
        .stat-value { font-weight: bold; }
        .stat-value.good { color: #00ff88; }
        .stat-value.warning { color: #ffaa00; }
        .stat-value.bad { color: #ff0044; }
        .chart { height: 150px; background: rgba(0,0,0,0.3); border-radius: 8px; padding: 10px; margin-top: 10px; }
        .chart canvas { width: 100%; height: 100%; }
        .problems { margin-top: 20px; }
        .summary { background: linear-gradient(135deg, rgba(0,212,255,0.1), rgba(123,44,191,0.1)); padding: 20px; border-radius: 16px; margin-top: 20px; }
        .summary h3 { color: #00d4ff; margin-bottom: 12px; }
        .summary p { line-height: 1.8; color: #ccc; }
    </style>
</head>
<body>
    <div class="container">
        <h1>📊 Informe de Rendimiento</h1>
        <p class="subtitle">$gamePackage</p>
        
        <div class="grade">$nota</div>
        <p class="grade-label">Nota de Rendimiento</p>
        
        <div class="card">
            <h2>📱 Dispositivo</h2>
            <div class="stat"><span class="stat-label">Modelo</span><span class="stat-value">${specs.model}</span></div>
            <div class="stat"><span class="stat-label">RAM</span><span class="stat-value">${specs.ram / (1024*1024*1024)} GB</span></div>
            <div class="stat"><span class="stat-label">CPU</span><span class="stat-value">${specs.cpu}</span></div>
            <div class="stat"><span class="stat-label">Núcleos</span><span class="stat-value">${specs.cores}</span></div>
            <div class="stat"><span class="stat-label">Resolución</span><span class="stat-value">${specs.resolution}</span></div>
        </div>
        
        <div class="card">
            <h2>🎮 FPS</h2>
            <div class="stat"><span class="stat-label">Promedio</span><span class="stat-value ${if (avgFps >= 45) "good" else if (avgFps >= 30) "warning" else "bad"}">${avgFps.toInt()} FPS</span></div>
            <div class="stat"><span class="stat-label">Mínimo</span><span class="stat-value">$minFps FPS</span></div>
            <div class="stat"><span class="stat-label">Máximo</span><span class="stat-value">$maxFps FPS</span></div>
            <div class="stat"><span class="stat-label">Estabilidad</span><span class="stat-value ${if (stability >= 80) "good" else if (stability >= 60) "warning" else "bad"}">${stability.toInt()}%</span></div>
            <div class="stat"><span class="stat-label">Frame Drops</span><span class="stat-value ${if (drops < 10) "good" else if (drops < 30) "warning" else "bad"}">$drops</span></div>
        </div>
        
        <div class="card">
            <h2>💾 Memoria</h2>
            <div class="stat"><span class="stat-label">Inicio</span><span class="stat-value">$memStart MB</span></div>
            <div class="stat"><span class="stat-label">Final</span><span class="stat-value">$memEnd MB</span></div>
            <div class="stat"><span class="stat-label">Pico</span><span class="stat-value ${if (peakMem < 1500) "good" else if (peakMem < 2000) "warning" else "bad"}">$peakMem MB</span></div>
            <div class="stat"><span class="stat-label">Crecimiento</span><span class="stat-value ${if (memGrowth < 200) "good" else if (memGrowth < 500) "warning" else "bad"}">$memGrowth MB</span></div>
        </div>
        
        <div class="card">
            <h2>🔋 Batería</h2>
            <div class="stat"><span class="stat-label">Inicio</span><span class="stat-value">$batteryStart%</span></div>
            <div class="stat"><span class="stat-label">Fin</span><span class="stat-value">$batteryEnd%</span></div>
            <div class="stat"><span class="stat-label">Consumo</span><span class="stat-value ${if (batteryStart - batteryEnd < 5) "good" else if (batteryStart - batteryEnd < 10) "warning" else "bad"}">${batteryStart - batteryEnd}%</span></div>
        </div>
        
        <div class="card">
            <h2>📊 Muestreo</h2>
            <div class="stat"><span class="stat-label">Muestras totales</span><span class="stat-value">$muestras</span></div>
            <div class="stat"><span class="stat-label">Intervalo</span><span class="stat-value">${1000/muestrasPorSegundo}ms</span></div>
            <div class="stat"><span class="stat-label">Muestras/seg</span><span class="stat-value">$muestrasPorSegundo</span></div>
        </div>
        
        <div class="card">
            <h2>📝 Eventos</h2>
            <div class="stat"><span class="stat-label">Errors</span><span class="stat-value ${if (errors == 0) "good" else "bad"}">$errors</span></div>
            <div class="stat"><span class="stat-label">Warnings</span><span class="stat-value ${if (warnings < 10) "good" else "warning"}">$warnings</span></div>
            <div class="stat"><span class="stat-label">GC Events</span><span class="stat-value ${if (gcEvents < 10) "good" else "warning"}">$gcEvents</span></div>
        </div>
        
        ${if (erroresAgrupados.isNotEmpty()) """
        <div class="card" style="border-left:4px solid #ff0044;">
            <h2>🚨 Errores Repetidos</h2>
            ${erroresAgrupados.take(5).joinToString("") { (msg, count) ->
                """
                <div style="background:rgba(255,0,68,0.1);padding:10px;margin:8px 0;border-radius:4px;">
                    <strong style="color:#ff0044">x$count</strong> - <span style="color:#ccc;font-size:0.85em;">${msg.take(80)}</span>
                </div>
                """
            }}
        </div>
        """ else ""}
        
        ${if (advertenciasAgrupadas.isNotEmpty()) """
        <div class="card" style="border-left:4px solid #ffaa00;">
            <h2>⚠️ Advertencias Repetidas</h2>
            ${advertenciasAgrupadas.take(5).joinToString("") { (msg, count) ->
                """
                <div style="background:rgba(255,170,0,0.1);padding:10px;margin:8px 0;border-radius:4px;">
                    <strong style="color:#ffaa00">x$count</strong> - <span style="color:#ccc;font-size:0.85em;">${msg.take(80)}</span>
                </div>
                """
            }}
        </div>
        """ else ""}
        
        ${if (eventos.isNotEmpty()) """
        <div class="card">
            <h2>📋 Últimos Eventos</h2>
            <div style="margin-top:12px;max-height:300px;overflow-y:auto;background:rgba(0,0,0,0.3);border-radius:8px;padding:10px;font-size:0.8em;">
                ${eventos.takeLast(30).joinToString("<br>") { e ->
                    val color = when (e.level) {
                        com.gameperf.core.LogLevel.ERROR -> "#ff0044"
                        com.gameperf.core.LogLevel.WARN -> "#ffaa00"
                        com.gameperf.core.LogLevel.FATAL -> "#ff0000"
                        else -> "#888"
                    }
                    "<span style='color:$color'>[${e.tag}] ${e.message.take(100)}</span>"
                }}
            </div>
        </div>
        """ else ""}
        
        <div class="card problems">
            <h2>⚠️ Problemas</h2>
            $problemasHtml
        </div>
        
        <div class="summary">
            <h3>📋 Resumen</h3>
            <p>${generarResumen(gamePackage, specs.model, avgFps, stability, drops, peakMem, memGrowth, problemas)}</p>
        </div>
    </div>
</body>
</html>
    """.trimIndent()
}

fun generarResumen(juego: String, dispositivo: String, avgFps: Double, stability: Double, drops: Int, peakMem: Long, memGrowth: Long, problemas: List<Problema>): String {
    val sb = StringBuilder()
    
    sb.append("El análisis de <strong>$juego</strong> en <strong>$dispositivo</strong> ")
    sb.append("durante 60 segundos muestra un rendimiento de <strong>${avgFps.toInt()} FPS</strong> ")
    sb.append("con una estabilidad del ${stability.toInt()}%.\n\n")
    
    if (problemas.isEmpty()) {
        sb.append("✅ <strong>Sin problemas críticos.</strong> El juego funciona correctamente en este dispositivo.")
    } else {
        sb.append("❌ <strong>Se detectaron ${problemas.size} problema(s):</strong>\n<ul>")
        for (p in problemas) {
            sb.append("<li><strong>${p.tipo}:</strong> ${p.descripcion}</li>")
        }
        sb.append("</ul>")
        
        if (problemas.any { it.tipo == "FPS_BAJO" || it.tipo == "FPS_INESTABLE" }) {
            sb.append("\nEl rendimiento FPS está por debajo de lo ideal. ")
            if (avgFps < 30) {
                sb.append("El dispositivo no es suficiente para este juego.")
            } else {
                sb.append("Considera reducir la calidad gráfica.")
            }
        }
        
        if (problemas.any { it.tipo == "MEMORIA_ALTA" || it.tipo == "MEMORY_LEAK" }) {
            sb.append("\n⚠️ Hay problemas de memoria que pueden causar crashes.")
        }
    }
    
    return sb.toString()
}
