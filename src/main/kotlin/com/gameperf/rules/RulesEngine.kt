package com.gameperf.rules

import com.gameperf.core.*
import java.io.File

/**
 * Motor de reglas configurable para deteccion de problemas de rendimiento.
 *
 * Carga reglas desde `rules-default.json` (classpath) o desde un archivo
 * externo especificado por el usuario. Evalua cada regla contra los datos
 * de la sesion de captura.
 *
 * NO usa librerias JSON externas — parsea manualmente con regex.
 */
object RulesEngine {

    // ===== Data Classes =====

    /** Una regla individual de deteccion de rendimiento. */
    data class Rule(
        val id: String,
        val name: String,
        val description: String,
        val metric: String,
        val condition: String,
        val threshold: Double,
        val severity: String,
        val pattern: String = "",
        val message: String = ""
    )

    /** Umbrales globales de rendimiento. */
    data class GlobalThresholds(
        val fpsMin: Int,
        val fpsTarget: Int,
        val frameTimeMax: Double,
        val frameTimeWarning: Double,
        val memoryWarning: Int,
        val memoryCritical: Int,
        val cpuWarning: Int,
        val cpuCritical: Int,
        val tempWarning: Int,
        val tempCritical: Int
    )

    /** Configuracion completa de reglas y umbrales. */
    data class RulesConfig(
        val rules: List<Rule>,
        val globalThresholds: GlobalThresholds
    )

    // ===== Default Thresholds =====

    /** Umbrales por defecto cuando no se puede cargar el JSON. */
    private val DEFAULT_THRESHOLDS = GlobalThresholds(
        fpsMin = 30,
        fpsTarget = 60,
        frameTimeMax = 16.67,
        frameTimeWarning = 20.0,
        memoryWarning = 512,
        memoryCritical = 768,
        cpuWarning = 70,
        cpuCritical = 85,
        tempWarning = 42,
        tempCritical = 45
    )

    // ===== Loading =====

    /**
     * Carga reglas desde el classpath (`rules-default.json`).
     * Si falla, retorna configuracion con valores por defecto y sin reglas.
     */
    fun loadRules(): RulesConfig {
        return try {
            val json = RulesEngine::class.java.getResourceAsStream("/rules-default.json")
                ?.bufferedReader()?.readText()
                ?: return RulesConfig(emptyList(), DEFAULT_THRESHOLDS)
            parseRulesJson(json)
        } catch (e: Exception) {
            RulesConfig(emptyList(), DEFAULT_THRESHOLDS)
        }
    }

    /**
     * Carga reglas desde un archivo externo.
     * Si falla, cae al default del classpath.
     */
    fun loadRules(filePath: String): RulesConfig {
        return try {
            val file = File(filePath)
            if (!file.exists()) {
                System.err.println("AVISO: Archivo de reglas no encontrado: $filePath — usando reglas por defecto")
                return loadRules()
            }
            parseRulesJson(file.readText())
        } catch (e: Exception) {
            System.err.println("AVISO: Error cargando reglas de $filePath — usando reglas por defecto: ${e.message}")
            loadRules()
        }
    }

    // ===== Evaluation =====

    /**
     * Evalua las reglas de patron contra los eventos de la sesion.
     * Retorna problemas adicionales detectados por pattern matching.
     */
    fun evaluate(
        events: List<LogEntry>,
        config: RulesConfig
    ): List<Problem> {
        val problems = mutableListOf<Problem>()

        for (rule in config.rules) {
            if (rule.pattern.isEmpty()) continue

            val regex = try {
                Regex(rule.pattern, RegexOption.IGNORE_CASE)
            } catch (_: Exception) {
                continue // Patron invalido, saltar regla
            }

            val matchCount = events.count { regex.containsMatchIn(it.message) }
            if (matchCount > 0) {
                val severity = when (rule.severity.uppercase()) {
                    "ERROR" -> Severity.HIGH
                    "WARNING" -> Severity.MEDIUM
                    "INFO" -> Severity.LOW
                    else -> Severity.LOW
                }
                problems.add(Problem(
                    type = "RULE_${rule.id.uppercase()}",
                    severity = severity,
                    description = "${rule.name}: $matchCount ocurrencias detectadas",
                    explanation = rule.description,
                    solution = rule.message.ifEmpty {
                        "Revisar los logs relacionados con ${rule.name.lowercase()} para mas detalles."
                    }
                ))
            }
        }

        return problems
    }

    // ===== JSON Parsing (manual, sin librerias externas) =====

    /**
     * Parsea el JSON de reglas manualmente.
     * Extrae el array "rules" y el objeto "globalThresholds".
     */
    internal fun parseRulesJson(json: String): RulesConfig {
        val rules = parseRulesArray(json)
        val thresholds = parseGlobalThresholds(json)
        return RulesConfig(rules, thresholds)
    }

    /** Extrae y parsea el array "rules" del JSON. */
    private fun parseRulesArray(json: String): List<Rule> {
        val rulesArrayStart = json.indexOf("\"rules\"")
        if (rulesArrayStart == -1) return emptyList()

        val arrayStart = json.indexOf('[', rulesArrayStart)
        if (arrayStart == -1) return emptyList()

        val arrayEnd = findMatchingBracket(json, arrayStart, '[', ']')
        if (arrayEnd == -1) return emptyList()

        val rulesContent = json.substring(arrayStart + 1, arrayEnd)
        return parseObjectsFromArray(rulesContent).mapNotNull { parseRule(it) }
    }

    /** Parsea un solo objeto Rule desde su JSON string. */
    private fun parseRule(obj: String): Rule? {
        val id = extractString(obj, "id") ?: return null
        val name = extractString(obj, "name") ?: return null
        val description = extractString(obj, "description") ?: ""
        val pattern = extractString(obj, "pattern") ?: ""
        val metric = extractString(obj, "metricType") ?: ""
        val severity = extractString(obj, "severity") ?: "INFO"
        val threshold = extractNumber(obj, "threshold") ?: 0.0
        val message = extractString(obj, "message") ?: ""

        // Mapea la condicion basada en el metric type y threshold
        val condition = when {
            metric == "FPS" -> "below_threshold"
            metric == "FRAME_TIME" -> "above_threshold"
            metric == "MEMORY" -> "above_threshold"
            metric == "CPU" -> "above_threshold"
            pattern.isNotEmpty() -> "pattern_match"
            else -> "above_threshold"
        }

        return Rule(
            id = id,
            name = name,
            description = description,
            metric = metric,
            condition = condition,
            threshold = threshold,
            severity = severity,
            pattern = pattern,
            message = message
        )
    }

    /** Extrae y parsea el objeto "globalThresholds" del JSON. */
    private fun parseGlobalThresholds(json: String): GlobalThresholds {
        val threshStart = json.indexOf("\"globalThresholds\"")
        if (threshStart == -1) return DEFAULT_THRESHOLDS

        val objStart = json.indexOf('{', threshStart)
        if (objStart == -1) return DEFAULT_THRESHOLDS

        val objEnd = findMatchingBracket(json, objStart, '{', '}')
        if (objEnd == -1) return DEFAULT_THRESHOLDS

        val threshContent = json.substring(objStart, objEnd + 1)

        // Extrae sub-objetos: fps, frameTime, memory
        val fpsMin = extractNestedNumber(threshContent, "fps", "minimum")?.toInt() ?: DEFAULT_THRESHOLDS.fpsMin
        val fpsTarget = extractNestedNumber(threshContent, "fps", "target")?.toInt() ?: DEFAULT_THRESHOLDS.fpsTarget
        val frameTimeMax = extractNestedNumber(threshContent, "frameTime", "maxMs") ?: DEFAULT_THRESHOLDS.frameTimeMax
        val frameTimeWarning = extractNestedNumber(threshContent, "frameTime", "warningMs") ?: DEFAULT_THRESHOLDS.frameTimeWarning
        val memoryWarning = extractNestedNumber(threshContent, "memory", "warningMB")?.toInt() ?: DEFAULT_THRESHOLDS.memoryWarning
        val memoryCritical = extractNestedNumber(threshContent, "memory", "criticalMB")?.toInt() ?: DEFAULT_THRESHOLDS.memoryCritical

        return GlobalThresholds(
            fpsMin = fpsMin,
            fpsTarget = fpsTarget,
            frameTimeMax = frameTimeMax,
            frameTimeWarning = frameTimeWarning,
            memoryWarning = memoryWarning,
            memoryCritical = memoryCritical,
            cpuWarning = DEFAULT_THRESHOLDS.cpuWarning,
            cpuCritical = DEFAULT_THRESHOLDS.cpuCritical,
            tempWarning = DEFAULT_THRESHOLDS.tempWarning,
            tempCritical = DEFAULT_THRESHOLDS.tempCritical
        )
    }

    // ===== JSON Helpers =====

    /** Extrae un valor string de un campo JSON: "key": "value" */
    internal fun extractString(json: String, key: String): String? {
        val pattern = Regex(""""$key"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        return pattern.find(json)?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
    }

    /** Extrae un valor numerico de un campo JSON: "key": 123.45 */
    internal fun extractNumber(json: String, key: String): Double? {
        val pattern = Regex(""""$key"\s*:\s*(-?\d+\.?\d*)""")
        return pattern.find(json)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    /** Extrae un numero de un sub-objeto: "parent": { "child": 123 } */
    private fun extractNestedNumber(json: String, parent: String, child: String): Double? {
        val parentStart = json.indexOf("\"$parent\"")
        if (parentStart == -1) return null

        val objStart = json.indexOf('{', parentStart)
        if (objStart == -1) return null

        val objEnd = findMatchingBracket(json, objStart, '{', '}')
        if (objEnd == -1) return null

        val subObj = json.substring(objStart, objEnd + 1)
        return extractNumber(subObj, child)
    }

    /** Encuentra el bracket de cierre correspondiente, respetando anidamiento. */
    private fun findMatchingBracket(json: String, start: Int, open: Char, close: Char): Int {
        var depth = 0
        var inString = false
        var escaped = false

        for (i in start until json.length) {
            val c = json[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (c == '\\' && inString) {
                escaped = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == open) depth++
                else if (c == close) {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return -1
    }

    /** Extrae objetos individuales de un array JSON (ya sin los brackets exteriores). */
    private fun parseObjectsFromArray(content: String): List<String> {
        val objects = mutableListOf<String>()
        var i = 0
        while (i < content.length) {
            val objStart = content.indexOf('{', i)
            if (objStart == -1) break

            val objEnd = findMatchingBracket(content, objStart, '{', '}')
            if (objEnd == -1) break

            objects.add(content.substring(objStart, objEnd + 1))
            i = objEnd + 1
        }
        return objects
    }
}
