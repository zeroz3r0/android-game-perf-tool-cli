package com.gameperf.core

data class AndroidDevice(
    val id: String,
    val name: String,
    val model: String,
    val sdkVersion: Int,
    val isEmulator: Boolean
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
    
    private fun parseDevicesList(output: String): List<AndroidDevice> {
        val devices = mutableListOf<AndroidDevice>()
        val lines = output.split("\n").filter { it.contains("device") && !it.contains("List") }
        
        for (line in lines) {
            val parts = line.split("\\s+".toRegex())
            if (parts.size >= 2) {
                val id = parts[0]
                val isEmulator = id.contains("emulator") || line.contains("emulator")
                val sdkVersion = extractSdkVersion(line)
                val model = extractModel(line)
                
                devices.add(AndroidDevice(
                    id = id,
                    name = model,
                    model = model,
                    sdkVersion = sdkVersion,
                    isEmulator = isEmulator
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
}
