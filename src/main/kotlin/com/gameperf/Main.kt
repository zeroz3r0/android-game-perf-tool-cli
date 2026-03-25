package com.gameperf

import com.gameperf.core.*

fun main(args: Array<String>) {
    println("""
        ╔══════════════════════════════════════════════════════╗
        ║     Android Game Performance Tool - CLI              ║
        ╚══════════════════════════════════════════════════════╝
        
        Selecciona una opción:
        
        1. Listar dispositivos conectados
        2. Capturar logs en tiempo real
        3. Extraer métricas de rendimiento
        4. Generar informe de performance
        5. Análisis personalizado
        
        0. Salir
    """.trimIndent())
    
    print("Opción: ")
    val option = readLine()?.toIntOrNull() ?: 0
    
    when (option) {
        1 -> listDevices()
        2 -> captureLogs()
        3 -> extractMetrics()
        4 -> generateReport()
        5 -> customAnalysis()
        0 -> println("¡Hasta luego!")
        else -> println("Opción inválida")
    }
}

fun listDevices() {
    println("\n📱 Buscando dispositivos Android...")
    val connector = AdbConnector()
    
    if (!connector.isAdbAvailable()) {
        println("❌ ADB no está disponible. Asegúrate de tener Android SDK instalado.")
        return
    }
    
    val devices = connector.listDevices()
    
    if (devices.isEmpty()) {
        println("⚠️ No se encontraron dispositivos conectados")
        println("   Asegúrate de:")
        println("   1. Tener USB debugging enabled en tu Android")
        println("   2. Conectar el dispositivo por USB")
        println("   3. Autorizar la conexión en el dispositivo")
    } else {
        println("\n✅ Dispositivos encontrados:\n")
        devices.forEachIndexed { index, device ->
            println("  [$index] ${device.model} (${device.id})")
            println("      SDK: ${device.sdkVersion} | Emulador: ${device.isEmulator}")
        }
    }
}

fun captureLogs() {
    println("\n📝 Captura de logs")
    print("Ingresa el ID del dispositivo: ")
    val deviceId = readLine() ?: return
    
    println("\n🔄 Iniciando captura de logs... (Ctrl+C para detener)")
    println("Presiona Enter para continuar en background...")
    readLine()
    
    val reader = LogcatReader(deviceId) { entry ->
        println("[${entry.level}] ${entry.tag}: ${entry.message}")
    }
    
    reader.start()
    println("✅ Captura iniciada. Presiona Enter para detener.")
    readLine()
    reader.stop()
    println("🛑 Captura detenida")
}

fun extractMetrics() {
    println("\n📊 Extracción de métricas")
    print("Ingresa el ID del dispositivo: ")
    val deviceId = readLine() ?: return
    
    println("\n🔄 Extrayendo métricas...")
    
    val reader = LogcatReader(deviceId) { }
    val extractor = MetricsExtractor()
    
    reader.start()
    Thread.sleep(10000) // Captura por 10 segundos
    reader.stop()
    
    val avgFps = extractor.getAverageFps()
    val minFps = extractor.getMinFps()
    val drops = extractor.getFrameDrops()
    
    println("\n📈 Métricas recopiladas:")
    println("   FPS Promedio: ${avgFps ?: "N/A"}")
    println("   FPS Mínimo: ${minFps ?: "N/A"}")
    println("   Frame Drops: $drops")
}

fun generateReport() {
    println("\n📋 Generar informe de performance")
    print("Ingresa el ID del dispositivo: ")
    val deviceId = readLine() ?: return
    
    print("Duración de captura (segundos): ")
    val duration = readLine()?.toIntOrNull() ?: 30
    
    println("\n🔄 Capturando datos por $duration segundos...")
    
    val extractor = MetricsExtractor()
    val reader = LogcatReader(deviceId) { entry ->
        extractor.extract(entry)
    }
    
    reader.start()
    Thread.sleep(duration * 1000L)
    reader.stop()
    
    val report = ReportGenerator(extractor, emptyList(), emptyList())
    val performanceReport = report.generate(deviceId)
    
    println("\n" + report.toMarkdown(performanceReport))
}

fun customAnalysis() {
    println("\n🔍 Análisis personalizado")
    println("Esta función permite analizar logs con reglas personalizadas.")
    println("Editar src/main/resources/rules-default.json para agregar reglas.")
}
