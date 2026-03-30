package com.gameperf.i18n

import com.gameperf.core.LogLevel

/**
 * Centralized strings for terminal output and reports.
 * All user-facing text in Spanish with English technical terms.
 */
object Strings {

    // ===== Terminal Output =====

    val ERROR_ADB_NOT_AVAILABLE = """
        |ERROR: ADB no disponible.
        |
        |  ADB (Android Debug Bridge) es necesario para comunicarse con el dispositivo.
        |  Viene incluido en Android SDK Platform-Tools.
        |
        |  Como instalar:
        |    macOS:    brew install android-platform-tools
        |    Linux:    sudo apt install android-tools-adb
        |    Windows:  https://developer.android.com/studio/releases/platform-tools
        |
        |  Despues de instalar, verifica con: adb version
    """.trimMargin()

    val ERROR_NO_DEVICES = """
        |ERROR: No hay dispositivos conectados.
        |
        |  Asegurate de:
        |    1. Conectar el dispositivo por USB
        |    2. Activar "Opciones de desarrollador" (toca 7 veces "Numero de compilacion" en Ajustes > Acerca del telefono)
        |    3. Activar "Depuracion USB" en Opciones de desarrollador
        |    4. Aceptar el dialogo "Permitir depuracion USB" en el dispositivo
        |
        |  Verifica con: adb devices
    """.trimMargin()

    val ERROR_NO_GAME = """
        |ERROR: No se detecto juego en primer plano.
        |
        |  Asegurate de que el juego esta abierto y visible en pantalla.
        |  Si el juego no se detecta automaticamente, especificalo manualmente:
        |    gameperf -p com.nombre.del.juego
        |
        |  Para ver las apps instaladas: adb shell pm list packages -3
    """.trimMargin()
    const val ERROR_DEVICE_DISCONNECTED = "ERROR: Dispositivo desconectado o no responde. Deteniendo captura..."
    const val WARN_DEVICE_NO_RESPONSE = "AVISO: Sin respuesta del dispositivo"
    const val WARN_WIFI_FAILED = "ERROR: No se pudo activar WiFi. Asegurate de que el movil esta en la misma red WiFi."

    const val SEARCHING_DEVICES = "Buscando dispositivos..."
    const val SEARCHING_GAME = "Buscando juego en primer plano..."
    const val WAITING_RENDER = "Esperando renderizado... (asegurate que el juego esta en primer plano)"
    const val TEST_STOPPED = "Prueba detenida"
    const val TEST_STOPPED_CTRL_C = "Prueba detenida (Ctrl+C)"
    const val CHARGING_DISABLED = "Carga USB desactivada (medicion real de bateria)"
    const val CHARGING_RESTORED = "Carga USB restaurada"
    const val WIFI_MODE_BATTERY = "Modo WiFi: bateria se mide directamente (sin carga USB)"
    const val WIFI_DISCONNECT_USB = ">>> DESCONECTA EL CABLE USB AHORA <<<"
    const val WIFI_WAITING = "Esperando 10 segundos para que desconectes..."
    const val WIFI_VERIFIED = "Conexion WiFi verificada. Bateria se medira SIN carga USB."
    const val WIFI_VERIFY_FAILED = "AVISO: No se pudo verificar WiFi. Continuando por USB."
    const val WIFI_ALREADY = "Ya conectado via WiFi"
    const val WIFI_SWITCHING = "Cambiando a ADB WiFi..."
    const val WIFI_CONNECTED = "Conectado via WiFi"
    const val WIFI_CONTINUE_USB = "Continuando por USB..."

    const val REPORT_GENERATED = "INFORME GENERADO"
    const val JSON_EXPORTED = "JSON EXPORTADO"

    fun device(model: String) = "Dispositivo: $model"
    fun specs(model: String, cpu: String, gpu: String, ramGb: String, cores: Int, sdk: Int, resolution: String, battery: Int?) =
        """
        |SPECS:
        |  Modelo: $model | CPU: $cpu | GPU: $gpu
        |  RAM: $ramGb GB | Cores: $cores | SDK: $sdk
        |  Resolucion: $resolution | Bateria: ${battery ?: "?"}%
        """.trimMargin()

    fun captureHeader(gamePackage: String, resolution: String?) = buildString {
        appendLine("=".repeat(60))
        appendLine("PRUEBA DE RENDIMIENTO")
        appendLine("=".repeat(60))
        appendLine("  Juego: $gamePackage")
        if (resolution != null) appendLine("  Resolucion render: $resolution")
        appendLine("  Pulsa ENTER o Ctrl+C para detener")
    }

    fun sampleLine(elapsed: Int, fps: Int, avgFps: Int, cpu: String, gpu: String, mem: String, temp: String, events: Int) =
        "  ${elapsed}s | FPS: $fps (avg:$avgFps) | CPU:$cpu | GPU:$gpu | Mem:$mem | Temp:$temp | Events:$events"

    fun resolutionChange(from: String, to: String) = "  CAMBIO RESOLUCION: $from -> $to"
    fun fpsJump(from: Int, to: Int) = "  SUBIDA FPS: $from -> $to (posible cambio calidad)"
    fun attempts(n: Int) = "($n intentos)"

    // ===== Progress Bar =====

    fun progressBar(elapsed: Int, total: Int): String {
        if (total <= 0) return ""
        val pct = (elapsed.toDouble() / total * 100).toInt().coerceIn(0, 100)
        val filled = (pct / 5).coerceIn(0, 20)
        val bar = "${"█".repeat(filled)}${"░".repeat(20 - filled)}"
        val remaining = total - elapsed
        val minR = remaining / 60
        val secR = remaining % 60
        return "  [$bar] $pct% | Quedan: ${minR}m ${secR}s"
    }

    // ===== Results =====

    fun resultsHeader(minutes: Int, seconds: Int) = buildString {
        appendLine()
        appendLine("=".repeat(60))
        appendLine("RESULTADOS (${minutes}m ${seconds}s)")
        appendLine("=".repeat(60))
    }

    // ===== Help =====

    fun help() = """
        |Uso: gameperf [opciones] [duracion_segundos]
        |
        |Opciones:
        |  -p, --package <pkg>  Paquete del juego (ej: com.supercell.clashofclans)
        |  -o, --output <dir>   Directorio de salida (default: reports/)
        |  -w, --wifi           Cambiar a ADB WiFi (desconecta USB, mide bateria real)
        |  -q, --quiet          Sin explicaciones detalladas
        |  --json               Exportar tambien en JSON
        |  --no-open            No abrir el informe automaticamente
        |  -h, --help           Mostrar esta ayuda
        |  -v, --version        Mostrar version
        |
        |Ejemplos:
        |  gameperf                    Prueba indefinida por USB
        |  gameperf 60                 Prueba de 60 segundos
        |  gameperf --wifi             WiFi, desconecta cable, prueba indefinida
        |  gameperf --wifi 120         WiFi, prueba de 2 minutos
        |  gameperf -p com.game.pkg 60 Paquete especifico, 60 segundos
        |  gameperf --json -o ~/out    Exportar JSON a directorio custom
    """.trimMargin()

    // ===== Log Translation =====

    fun translateLog(message: String, @Suppress("UNUSED_PARAMETER") tag: String): String {
        val msg = message.lowercase()
        return when {
            msg.contains("underrun") -> "AUDIO LAG - El dispositivo no puede procesar audio a tiempo."
            msg.contains("audiopolicy") || msg.contains("audioflinger") -> "Problema con el sistema de audio."
            GC_PATTERN.containsMatchIn(msg) -> "RECOLECTOR DE BASURA - puede causar micro-lags."
            msg.contains("thermal") && (msg.contains("throttl") || msg.contains("cpu") || msg.contains("gpu")) ->
                "THERMAL THROTTLING - Dispositivo sobrecalentado, reduce CPU/GPU."
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

    fun explainError(message: String): String {
        val msg = message.lowercase()
        return when {
            msg.contains("underrun") ->
                "Un audio underrun ocurre cuando el buffer de audio se vacia antes de que el sistema lo rellene. El procesador esta demasiado ocupado con graficos."
            GC_PATTERN.containsMatchIn(msg) ->
                "El recolector de basura se activo para liberar memoria. El juego se congela brevemente (1-50ms) mientras limpia."
            msg.contains("thermal") && msg.contains("throttl") ->
                "El dispositivo se sobrecalienta y reduce la velocidad del procesador/GPU. Causa caida directa de FPS."
            msg.contains("oom") || msg.contains("out of memory") ->
                "El sistema se quedo sin memoria RAM. Android cierra procesos. El juego puede crashear."
            msg.contains("trimmemory") ->
                "Android pide a las apps que liberen memoria. La RAM esta bajo presion."
            msg.contains("jank") || msg.contains("hitch") ->
                "El juego no pudo dibujar frames a tiempo. La pantalla se congelo brevemente."
            msg.contains("surfaceflinger") ->
                "SurfaceFlinger (compositor grafico) reporta problemas. Puede causar frames perdidos."
            msg.contains("anr") || msg.contains("not responding") ->
                "La app se bloqueo mas de 5 segundos sin responder. El usuario ve el dialogo 'No responde'."
            msg.contains("fatal") || msg.contains("crash") ->
                "Error fatal en el juego. Normalmente causa que se cierre."
            msg.contains("timeout") && msg.contains("socket") ->
                "Conexion de red caida por timeout. En juegos online causa lag o desconexion."
            msg.contains("hwc") || msg.contains("hwcomposer") ->
                "El Hardware Composer tiene problemas. Puede causar parpadeos o frames perdidos."
            else ->
                "Evento del sistema detectado. Su repeticion puede indicar un problema."
        }
    }

    val GC_PATTERN = Regex("\\bgc\\b|concurrent.?mark|garbage.?collect|clamp.?gc", RegexOption.IGNORE_CASE)
}
