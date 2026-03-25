package com.gameperf.core

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val deviceId: String,
    val rawLine: String = ""
)

enum class LogLevel {
    VERBOSE, DEBUG, INFO, WARN, ERROR, FATAL
}

data class SystemEvent(
    val timestamp: Long,
    val type: EventType,
    val description: String,
    val tag: String = "",
    val details: Map<String, String> = emptyMap()
)

enum class EventType {
    FPS_CHANGE,
    FRAME_DROP,
    MEMORY_PRESSURE,
    GC_EVENT,
    CPU_THROTTLE,
    THERMAL_THROTTLE,
    APP_SWITCH,
    NOTIFICATION,
    NETWORK_CHANGE,
    SCREEN_ROTATION,
    LOW_MEMORY,
    ANR,
    CRASH,
    JANK,
    SLOW_RENDER,
    PACKAGE_EVENT,
    ACTIVITY_EVENT,
    SYSTEM_EVENT,
    BINDER_TRANSACTION,
    VIEW_DRAW,
    INPUT_LATENCY
}

class LogcatReader(
    private val deviceId: String,
    private val onLogReceived: (LogEntry) -> Unit = {}
) {
    private var process: Process? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var isRunning = false
    private val events = mutableListOf<SystemEvent>()
    private var lastFps = 0
    private var lastMemory = 0.0
    
    fun start() {
        if (isRunning) return
        
        val args = arrayOf(
            "adb", "-s", deviceId, "logcat", "-v", "threadtime", 
            "*:V"
        )
        
        process = Runtime.getRuntime().exec(args)
        isRunning = true
        
        executor.execute {
            val reader = BufferedReader(InputStreamReader(process!!.inputStream))
            var line: String? = ""
            
            while (isRunning && reader.readLine().also { line = it } != null) {
                line?.let { parseAndAnalyze(it) }
            }
        }
    }
    
    fun stop() {
        isRunning = false
        try {
            process?.destroy()
        } catch (e: Exception) { }
    }
    
    fun getEvents(): List<SystemEvent> = events.toList()
    
    fun getLastFps(): Int = lastFps
    fun getLastMemory(): Double = lastMemory
    
    private fun parseAndAnalyze(line: String) {
        if (line.length < 30) return
        
        val entry = parseLogEntry(line) ?: return
        
        onLogReceived(entry)
        analyzeForEvents(entry)
    }
    
    private fun analyzeForEvents(entry: LogEntry) {
        val msg = entry.message.lowercase()
        val tag = entry.tag.lowercase()
        
        // FPS detection
        if (msg.contains("fps") || msg.contains("frame") || msg.contains("draw")) {
            val fpsMatch = Regex("(\\d+)\\s*fps").find(entry.message) ?: 
                          Regex("fps[:=]\\s*(\\d+)").find(entry.message)
            fpsMatch?.let {
                lastFps = it.groupValues[1].toIntOrNull() ?: lastFps
            }
            events.add(SystemEvent(
                timestamp = entry.timestamp,
                type = EventType.FPS_CHANGE,
                description = entry.message.take(150),
                tag = entry.tag
            ))
        }
        
        // Jank detection
        if (msg.contains("jank") || msg.contains("dropped frame") || msg.contains("slow frame")) {
            events.add(SystemEvent(
                timestamp = entry.timestamp,
                type = EventType.JANK,
                description = entry.message.take(150),
                tag = entry.tag
            ))
        }
        
        // Memory pressure
        if (msg.contains("low memory") || msg.contains("memory pressure") || 
            msg.contains("oom") || msg.contains("out of memory")) {
            events.add(SystemEvent(
                timestamp = entry.timestamp,
                type = EventType.MEMORY_PRESSURE,
                description = entry.message.take(150),
                tag = entry.tag
            ))
        }
        
        // Memory logging
        Regex("(mem|ram|pss)\\s*[:=]\\s*(\\d+)").find(entry.message)?.let {
            lastMemory = it.groupValues[2].toDoubleOrNull() ?: lastMemory
        }
        
        // GC events
        if (msg.contains("gc") && (msg.contains("collecting") || msg.contains("pause") || 
            msg.contains("freed") || tag.contains("gc"))) {
            events.add(SystemEvent(
                timestamp = entry.timestamp,
                type = EventType.GC_EVENT,
                description = entry.message.take(150),
                tag = entry.tag
            ))
        }
        
        // CPU throttling
        if ((msg.contains("cpu") || tag.contains("cpu")) && 
            (msg.contains("throttle") || msg.contains("freq") || msg.contains("limit"))) {
            events.add(SystemEvent(
                timestamp = entry.timestamp,
                type = EventType.CPU_THROTTLE,
                description = entry.message.take(150),
                tag = entry.tag
            ))
        }
        
        // Thermal
        if ((msg.contains("thermal") || msg.contains("temperature") || msg.contains("hot")) &&
            (tag.contains("thermal") || tag.contains("power") || entry.level >= LogLevel.WARN)) {
            events.add(SystemEvent(
                timestamp = entry.timestamp,
                type = EventType.THERMAL_THROTTLE,
                description = entry.message.take(150),
                tag = entry.tag
            ))
        }
        
        // Activity events
        if ((tag.contains("activitymanager") || tag.contains("am")) && 
            (msg.contains("start") || msg.contains("resume") || msg.contains("pause"))) {
            events.add(SystemEvent(
                timestamp = entry.timestamp,
                type = EventType.ACTIVITY_EVENT,
                description = entry.message.take(150),
                tag = entry.tag
            ))
        }
        
        // ANR
        if (msg.contains("anr") || msg.contains("application not responding")) {
            events.add(SystemEvent(
                timestamp = entry.timestamp,
                type = EventType.ANR,
                description = entry.message.take(150),
                tag = entry.tag
            ))
        }
        
        // Crashes
        if ((msg.contains("fatal") || msg.contains("crash") || msg.contains("exception") || 
             msg.contains("error") || msg.contains("failed")) && 
            (entry.level >= LogLevel.ERROR || tag.contains("crash") || tag.contains("fatal"))) {
            events.add(SystemEvent(
                timestamp = entry.timestamp,
                type = EventType.CRASH,
                description = entry.message.take(150),
                tag = entry.tag
            ))
        }
        
        // Binder transactions
        if ((tag.contains("binder") || tag.contains("hwbinder")) && 
            (msg.contains("transaction") || msg.contains("latency") || msg.contains("delay"))) {
            events.add(SystemEvent(
                timestamp = entry.timestamp,
                type = EventType.BINDER_TRANSACTION,
                description = entry.message.take(150),
                tag = entry.tag
            ))
        }
        
        // Slow rendering
        if (tag.contains("view") && (msg.contains("draw") || msg.contains("measure") || msg.contains("layout"))) {
            if (msg.contains("slow") || msg.contains("too long")) {
                events.add(SystemEvent(
                    timestamp = entry.timestamp,
                    type = EventType.SLOW_RENDER,
                    description = entry.message.take(150),
                    tag = entry.tag
                ))
            }
        }
        
        // Input latency
        if (tag.contains("input") && (msg.contains("latency") || msg.contains("delay"))) {
            events.add(SystemEvent(
                timestamp = entry.timestamp,
                type = EventType.INPUT_LATENCY,
                description = entry.message.take(150),
                tag = entry.tag
            ))
        }
    }
    
    private fun parseLogEntry(line: String): LogEntry? {
        // Multiple patterns to handle different log formats
        val patterns = listOf(
            Regex("(\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(\\d+)\\s+(\\d+)\\s+(\\w)/([^:]+):\\s*(.*)"),
            Regex("(\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(\\d+)\\s+(\\d+)\\s+(\\w)\\s+(\\w+):\\s*(.*)"),
            Regex("(\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})\\s+(\\d+)\\s+(\\d+)\\s+(\\w)/([^:]+):\\s*(.*)")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(line) ?: continue
            
            val level = match.groupValues[4]
            val tag = match.groupValues[5]
            val message = match.groupValues[6]
            
            return LogEntry(
                timestamp = System.currentTimeMillis(),
                level = parseLevel(level),
                tag = tag,
                message = message,
                deviceId = deviceId,
                rawLine = line
            ).also { onLogReceived(it) }
        }
        
        return null
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
