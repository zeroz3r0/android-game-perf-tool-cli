package com.gameperf.core

import java.io.BufferedReader
import java.io.InputStreamReader

data class AndroidDevice(
    val id: String,
    val name: String,
    val model: String,
    val manufacturer: String,
    val sdkVersion: Int,
    val isEmulator: Boolean,
    val cpu: String = "",
    val ram: Long = 0,
    val resolution: String = "",
    val batteryCapacity: Int = 0
)

data class DeviceSpecs(
    val model: String,
    val manufacturer: String,
    val sdkVersion: Int,
    val cpu: String,
    val ram: Long,
    val resolution: String,
    val totalStorage: String,
    val openGlVersion: String,
    val displayDensity: String,
    val cores: Int
)

data class BatteryInfo(
    val level: Int,
    val temperature: Float,
    val isCharging: Boolean,
    val voltage: Int
)

data class ProcessStats(
    val totalKb: Long,
    val pssKb: Long,
    val timestamp: Long
)

class AdbConnector {
    
    fun isAdbAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("adb", "version"))
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    fun listDevices(): List<AndroidDevice> {
        if (!isAdbAvailable()) return emptyList()
        
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("adb", "devices", "-l"))
            val output = process.inputStream.bufferedReader().readText()
            parseDevicesList(output)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun getDeviceSpecs(deviceId: String): DeviceSpecs {
        val model = getDeviceProperty(deviceId, "ro.product.model")
        val manufacturer = getDeviceProperty(deviceId, "ro.product.manufacturer")
        val sdk = getDeviceProperty(deviceId, "ro.build.version.sdk").toIntOrNull() ?: 0
        val cpu = getDeviceProperty(deviceId, "ro.hardware") + " " + getDeviceProperty(deviceId, "ro.board.platform")
        val ram = getRamFromDumpsys(deviceId)
        val resolution = getResolution(deviceId)
        val density = getDisplayDensity(deviceId)
        val cores = getCpuCores(deviceId)
        
        return DeviceSpecs(
            model = model,
            manufacturer = manufacturer.ifEmpty { "Unknown" },
            sdkVersion = sdk,
            cpu = cpu.trim(),
            ram = ram,
            resolution = resolution,
            totalStorage = getDeviceProperty(deviceId, "ro.build.version.release"),
            openGlVersion = "3.1+",
            displayDensity = density,
            cores = cores
        )
    }
    
    fun getBatteryLevel(deviceId: String): BatteryInfo? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("adb", "-s", deviceId, "shell", "dumpsys", "battery"))
            val output = process.inputStream.bufferedReader().readText()
            parseBatteryInfo(output)
        } catch (e: Exception) {
            null
        }
    }
    
    fun getGamePackage(deviceId: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("adb", "-s", deviceId, "shell", "dumpsys", "activity", "activities"))
            val output = process.inputStream.bufferedReader().readText()
            
            // Buscar packageName en actividad
            val patterns = listOf(
                Regex("packageName=([\\w.]+)"),
                Regex("A=(\\d+:)?([\\w.]+)"),
                Regex("cmp=([\\w.]+)/")
            )
            
            for (pattern in patterns) {
                val matches = pattern.findAll(output)
                for (match in matches) {
                    val pkg = match.groupValues.last()
                    // Filtrar paquetes del sistema
                    if (pkg.contains(".") && 
                        !pkg.startsWith("com.android") && 
                        !pkg.startsWith("com.google.android") &&
                        !pkg.startsWith("android") &&
                        !pkg.contains("launcher") &&
                        !pkg.contains("systemui")) {
                        return pkg
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
    
    fun getProcessStats(deviceId: String, packageName: String): ProcessStats? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("adb", "-s", deviceId, "shell", "dumpsys", "meminfo", packageName))
            val output = process.inputStream.bufferedReader().readText()
            
            val pssMatch = Regex("TOTAL PSS:\\s+(\\d+)").find(output)
            val pssKb = pssMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            
            ProcessStats(
                totalKb = pssKb,
                pssKb = pssKb,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
    }
    
    fun getMissedFrames(deviceId: String): Int {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("adb", "-s", deviceId, "shell", "dumpsys", "SurfaceFlinger"))
            val output = process.inputStream.bufferedReader().readText()
            
            val match = Regex("Total missed frame count:\\s*(\\d+)").find(output)
            match?.groupValues?.get(1)?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    fun getGameFps(deviceId: String, packageName: String): Int {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("adb", "-s", deviceId, "shell", "dumpsys", "SurfaceFlinger"))
            val output = process.inputStream.bufferedReader().readText()
            
            // Buscar frame rate del SurfaceFlinger
            val patterns = listOf(
                Regex("fps=([\\d.]+)\\s*Hz"),
                Regex("setFrameRate=\\(\\s*uid,\\s*frameRate\\s*\\)=\\{(\\d+),\\s*([\\d.]+)\\s*Hz")
            )
            
            for (pattern in patterns) {
                val match = pattern.find(output)
                if (match != null) {
                    val fps = match.groupValues.last().toIntOrNull() ?: continue
                    if (fps in 1..120) return fps
                }
            }
            
            60
        } catch (e: Exception) {
            60
        }
    }
    
    fun getGameLogs(deviceId: String, packageName: String, lastN: Int): List<LogEntry> {
        return try {
            // Capturar TODOS los logs - sin filtrar nada
            val process = Runtime.getRuntime().exec(
                arrayOf("adb", "-s", deviceId, "shell", "logcat", "-d", "-v", "threadtime", "-t", "2000")
            )
            val output = process.inputStream.bufferedReader().readText()
            
            val entries = mutableListOf<LogEntry>()
            val lines = output.lines()
            
            // Parsear TODAS las líneas sin filtro - ABSOLUTAMENTE TODO
            for (line in lines) {
                if (line.startsWith("---------")) continue  // Cabeceras
                if (line.length < 15) continue  // Líneas muy cortas
                
                val entry = parseLogLine(line, deviceId)
                if (entry != null) {
                    entries.add(entry)
                } else {
                    // Si no podemos parsear, guardar raw
                    entries.add(LogEntry(
                        timestamp = System.currentTimeMillis(),
                        level = if (line.contains(" E/") || line.contains(" E ")) LogLevel.ERROR 
                                else if (line.contains(" W/") || line.contains(" W ")) LogLevel.WARN 
                                else if (line.contains(" F/") || line.contains(" F ")) LogLevel.FATAL
                                else LogLevel.DEBUG,
                        tag = "RAW",
                        message = line.take(300),
                        deviceId = deviceId
                    ))
                }
            }
            
            // Devolver todos los eventos (sin filtrar)
            entries.takeLast(lastN)
        } catch (e: Exception) {
            println("   ⚠️ Error capturando logs: ${e.message}")
            emptyList()
        }
    }
    
    private fun parseLogLine(line: String, deviceId: String): LogEntry? {
        try {
            // Formato: MM-DD HH:MM:SS.mmm  PID  TID LEVEL TAG: message
            var pattern = Regex("(\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(\\d+)\\s+(\\d+)\\s+(\\w)/([^:]+):\\s*(.*)")
            var match = pattern.find(line)
            
            if (match != null) {
                val level = match.groupValues[4]
                val tag = match.groupValues[5]
                val message = match.groupValues[6].trim()
                
                return LogEntry(
                    timestamp = System.currentTimeMillis(),
                    level = parseLevel(level),
                    tag = tag,
                    message = message,
                    deviceId = deviceId
                )
            }
            
            // Probar formato alternativo sin TID
            pattern = Regex("(\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(\\d+)\\s+(\\w)/([^:]+):\\s*(.*)")
            match = pattern.find(line)
            if (match != null) {
                val level = match.groupValues[3]
                val tag = match.groupValues[4]
                val message = match.groupValues[5].trim()
                
                return LogEntry(
                    timestamp = System.currentTimeMillis(),
                    level = parseLevel(level),
                    tag = tag,
                    message = message,
                    deviceId = deviceId
                )
            }
            
            return null
        } catch (e: Exception) {
            return null
        }
    }
    
    private fun getDeviceProperty(deviceId: String, property: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("adb", "-s", deviceId, "shell", "getprop", property))
            process.inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) {
            ""
        }
    }
    
    private fun parseDevicesList(output: String): List<AndroidDevice> {
        val devices = mutableListOf<AndroidDevice>()
        val lines = output.split("\n").filter { it.contains("device") && !it.contains("List") }
        
        for (line in lines) {
            val parts = line.split("\\s+".toRegex())
            if (parts.size >= 2) {
                val id = parts[0]
                val sdkVersion = extractSdkVersion(line)
                val model = extractModel(line)
                
                devices.add(AndroidDevice(
                    id = id,
                    name = model,
                    model = model,
                    manufacturer = "",
                    sdkVersion = sdkVersion,
                    isEmulator = id.contains("emulator")
                ))
            }
        }
        return devices
    }
    
    private fun extractSdkVersion(line: String): Int {
        val sdkMatch = Regex("sdk:(\\d+)").find(line)
        return sdkMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }
    
    private fun extractModel(line: String): String {
        val modelMatch = Regex("model:([^:]+)").find(line)
        return modelMatch?.groupValues?.get(1)?.trim() ?: "Unknown"
    }
    
    private fun parseBatteryInfo(output: String): BatteryInfo {
        val level = Regex("level: (\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val temp = Regex("temperature: (\\d+)").find(output)?.groupValues?.get(1)?.toFloatOrNull()?.div(10f) ?: 0f
        val status = Regex("status: (\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        
        return BatteryInfo(
            level = level,
            temperature = temp,
            isCharging = status == 2 || status == 5,
            voltage = 0
        )
    }
    
    private fun getRamFromDumpsys(deviceId: String): Long {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("adb", "-s", deviceId, "shell", "cat", "/proc/meminfo"))
            val output = process.inputStream.bufferedReader().readText()
            val match = Regex("MemTotal:\\s+(\\d+)").find(output)
            (match?.groupValues?.get(1)?.toLongOrNull() ?: 0L) * 1024
        } catch (e: Exception) {
            0L
        }
    }
    
    private fun getResolution(deviceId: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("adb", "-s", deviceId, "shell", "wm", "size"))
            val output = process.inputStream.bufferedReader().readText().trim()
            output.ifEmpty { "Unknown" }
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    private fun getDisplayDensity(deviceId: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("adb", "-s", deviceId, "shell", "wm", "density"))
            process.inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    private fun getCpuCores(deviceId: String): Int {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("adb", "-s", deviceId, "shell", "cat", "/proc/cpuinfo"))
            val output = process.inputStream.bufferedReader().readText()
            Regex("processor\\s*:\\s*(\\d+)").findAll(output).count()
        } catch (e: Exception) {
            4
        }
    }
    
    private fun parseLevel(level: String): LogLevel {
        return when (level) {
            "V" -> LogLevel.VERBOSE
            "D" -> LogLevel.DEBUG
            "I" -> LogLevel.INFO
            "W", "wrn" -> LogLevel.WARN
            "E", "err" -> LogLevel.ERROR
            "F", "fatal" -> LogLevel.FATAL
            else -> LogLevel.DEBUG
        }
    }
}
