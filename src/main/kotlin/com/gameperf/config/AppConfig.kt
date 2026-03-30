package com.gameperf.config

data class AppConfig(
    val maxDuration: Int = -1,
    val quiet: Boolean = false,
    val exportJson: Boolean = false,
    val wifi: Boolean = false,
    val packageName: String? = null,
    val outputDir: String = "reports",
    val openReport: Boolean = true
) {
    companion object {
        const val APP_NAME = "Android Game Performance Tool"
        const val APP_VERSION = "6.0.0"
        const val APP_SHORT_NAME = "gameperf"
    }
}
