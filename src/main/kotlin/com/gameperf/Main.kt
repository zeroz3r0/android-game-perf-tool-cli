package com.gameperf

import com.gameperf.analysis.SessionAnalyzer
import com.gameperf.capture.CaptureSession
import com.gameperf.config.AppConfig
import com.gameperf.core.AdbConnector
import com.gameperf.core.AdbProvider
import com.gameperf.core.AndroidDevice
import com.gameperf.i18n.Strings
import com.gameperf.report.HtmlReporter
import com.gameperf.report.JsonReporter
import com.gameperf.report.TerminalReporter
import com.gameperf.rules.RulesEngine
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date

fun main(args: Array<String>) {
    val config = parseArgs(args)

    printBanner()

    val connector = AdbConnector()
    if (!connector.isAdbAvailable()) {
        println(Strings.ERROR_ADB_NOT_AVAILABLE)
        return
    }

    println("\n${Strings.SEARCHING_DEVICES}")
    val devices = connector.listDevices()
    if (devices.isEmpty()) {
        println(Strings.ERROR_NO_DEVICES)
        return
    }

    var device = devices.first()
    println(Strings.device(device.model))

    // WiFi mode
    device = handleWifiMode(connector, device, config)

    val specs = connector.getDeviceSpecs(device.id)
    val batteryStart = connector.getBatteryLevel(device.id)
    println(Strings.specs(
        specs.model, specs.cpu, specs.gpuModel,
        "%.1f".format(specs.ramGb), specs.cores, specs.sdkVersion,
        specs.resolution, batteryStart?.level
    ))

    // Detect game
    println("\n${Strings.SEARCHING_GAME}")
    val gamePackage = detectGame(connector, device, config)
    if (gamePackage == null) {
        println(Strings.ERROR_NO_GAME)
        return
    }
    println("  $gamePackage")

    // Capture
    val isWifi = connector.isWifiConnection(device.id)
    val session = CaptureSession(connector, config)
    val sessionData = session.capture(device, gamePackage, specs, batteryStart, isWifi)

    // Load rules
    val rulesConfig = if (config.rulesFile != null) {
        RulesEngine.loadRules(config.rulesFile)
    } else {
        RulesEngine.loadRules()
    }

    // Analyze
    val result = SessionAnalyzer.analyze(sessionData, rulesConfig)

    // Terminal output
    TerminalReporter.print(result, config)

    // Generate reports
    val outputDir = ensureOutputDir(config.outputDir)
    val safeName = gamePackage.replace(".", "_").takeLast(30)
    val deviceName = specs.model.replace(" ", "_").replace(Regex("[^a-zA-Z0-9_()-]"), "")
    val dateStr = SimpleDateFormat("yyyy-MM-dd_HHmm").format(Date())
    val baseName = "informe_${safeName}_${deviceName}_$dateStr"

    // HTML report
    val html = HtmlReporter.generate(result)
    val htmlFile = File(outputDir, "$baseName.html")
    htmlFile.writeText(html)
    println("\n${Strings.REPORT_GENERATED}: ${htmlFile.absolutePath}")

    // JSON report
    if (config.exportJson) {
        val json = JsonReporter.generate(result)
        val jsonFile = File(outputDir, "$baseName.json")
        jsonFile.writeText(json)
        println("${Strings.JSON_EXPORTED}: ${jsonFile.name}")
    }

    // Open report
    if (config.openReport) {
        try { Desktop.getDesktop().browse(URI("file://${htmlFile.absolutePath}")) } catch (_: Exception) {}
    }
}

// ===== Arg Parsing =====

fun parseArgs(args: Array<String>): AppConfig {
    if (args.any { it == "--help" || it == "-h" }) {
        println(Strings.help())
        kotlin.system.exitProcess(0)
    }
    if (args.any { it == "--version" || it == "-v" }) {
        println("${AppConfig.APP_NAME} v${AppConfig.APP_VERSION}")
        kotlin.system.exitProcess(0)
    }

    var packageName: String? = null
    var outputDir = "reports"
    var rulesFile: String? = null
    val argsIt = args.iterator()
    while (argsIt.hasNext()) {
        when (argsIt.next()) {
            "-p", "--package" -> if (argsIt.hasNext()) packageName = argsIt.next()
            "-o", "--output" -> if (argsIt.hasNext()) outputDir = argsIt.next()
            "--rules" -> if (argsIt.hasNext()) rulesFile = argsIt.next()
        }
    }

    return AppConfig(
        maxDuration = args.firstOrNull { it.toIntOrNull() != null }?.toInt() ?: -1,
        quiet = args.any { it == "--quiet" || it == "-q" },
        exportJson = args.any { it == "--json" },
        wifi = args.any { it == "--wifi" || it == "-w" },
        packageName = packageName,
        outputDir = outputDir,
        openReport = !args.any { it == "--no-open" },
        rulesFile = rulesFile
    )
}

// ===== Banner =====

private fun printBanner() {
    println("""
╔══════════════════════════════════════════════════════════════╗
║     ${AppConfig.APP_NAME} v${AppConfig.APP_VERSION}${" ".repeat(25 - AppConfig.APP_VERSION.length)}║
║     Analisis profesional de rendimiento                     ║
╚══════════════════════════════════════════════════════════════╝""")
}

// ===== WiFi Handling =====

private fun handleWifiMode(connector: AdbProvider, device: AndroidDevice, config: AppConfig): AndroidDevice {
    if (config.wifi && !connector.isWifiConnection(device.id)) {
        println("\n${Strings.WIFI_SWITCHING}")
        val wifiId = connector.switchToWifi(device.id)
        if (wifiId != null) {
            println("  ${Strings.WIFI_CONNECTED}: $wifiId")
            println("  ${Strings.WIFI_DISCONNECT_USB}")
            println("  ${Strings.WIFI_WAITING}")
            for (i in 10 downTo 1) {
                print("  $i... "); System.out.flush()
                Thread.sleep(1000)
            }
            println()

            val wifiDevices = connector.listDevices()
            val wifiDevice = wifiDevices.find { it.id == wifiId }
            if (wifiDevice != null) {
                println("  ${Strings.WIFI_VERIFIED}")
                return wifiDevice
            } else {
                println("  ${Strings.WIFI_VERIFY_FAILED}")
            }
        } else {
            println("  ${Strings.WARN_WIFI_FAILED}")
            println("  ${Strings.WIFI_CONTINUE_USB}")
        }
    } else if (connector.isWifiConnection(device.id)) {
        println("  ${Strings.WIFI_ALREADY}: ${device.id}")
    }
    return device
}

// ===== Game Detection =====

private fun detectGame(connector: AdbProvider, device: AndroidDevice, config: AppConfig): String? {
    // Manual package override
    if (config.packageName != null) return config.packageName

    // Auto-detect
    for (i in 1..30) {
        val pkg = connector.getGamePackage(device.id)
        if (pkg != null) return pkg
        print("."); System.out.flush(); Thread.sleep(1000)
    }
    return null
}

// ===== Output Directory =====

private fun ensureOutputDir(path: String): File {
    val dir = File(path)
    if (!dir.exists()) dir.mkdirs()
    return dir
}
