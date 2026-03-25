package com.gameperf.core

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val deviceId: String
)

enum class LogLevel {
    VERBOSE, DEBUG, INFO, WARN, ERROR
}

class LogcatReader(
    private val deviceId: String,
    private val onLogReceived: (LogEntry) -> Unit
) {
    private var process: Process? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var isRunning = false
    
    fun start(filter: String? = null) {
        if (isRunning) return
        
        val args = mutableListOf("adb", "-s", deviceId, "logcat")
        if (filter != null) {
            args.add(filter)
        }
        
        process = Runtime.getRuntime().exec(args.toTypedArray())
        isRunning = true
        
        executor.execute {
            val reader = BufferedReader(InputStreamReader(process!!.inputStream))
            var line: String? = ""
            
            while (isRunning && reader.readLine().also { line = it } != null) {
                line?.let { parseLogEntry(it) }
            }
        }
    }
    
    fun stop() {
        isRunning = false
        process?.destroy()
    }
    
    private fun parseLogEntry(line: String): LogEntry? {
        val pattern = Regex("(\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(\\w)/(\\w+)\\s+(.*)")
        val match = pattern.find(line) ?: return null
        
        val (timestamp, level, tag, message) = match.destructured
        
        return LogEntry(
            timestamp = System.currentTimeMillis(),
            level = parseLevel(level),
            tag = tag,
            message = message,
            deviceId = deviceId
        ).also { onLogReceived(it) }
    }
    
    private fun parseLevel(level: String): LogLevel {
        return when (level) {
            "V" -> LogLevel.VERBOSE
            "D" -> LogLevel.DEBUG
            "I" -> LogLevel.INFO
            "W" -> LogLevel.WARN
            "E" -> LogLevel.ERROR
            else -> LogLevel.DEBUG
        }
    }
}
