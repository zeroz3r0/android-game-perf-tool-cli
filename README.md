# Android Game Performance Tool

Herramienta CLI profesional para medir y analizar el rendimiento de juegos en dispositivos Android. Genera informes HTML interactivos con graficos, percentiles, timeline de eventos y diagnostico de problemas.

Validada en dispositivo real: **Pixel 7a (Tensor G2, Mali-G710, Android 15 SDK 36)**.

---

## Que mide

| Metrica | Fuente | Metodo |
|---------|--------|--------|
| **FPS** | `dumpsys SurfaceFlinger --latency` | Ventana temporal de 1s sobre timestamps de presentacion |
| **Frame Times** | Delta entre frames consecutivos | Filtrado IQR para eliminar outliers de medicion |
| **Memoria** | `dumpsys meminfo` | Total PSS + Native Heap + Java Heap por proceso |
| **CPU (sistema)** | `/proc/stat` | Delta entre muestras, total + per-core |
| **CPU (juego)** | `/proc/{pid}/stat` | Delta utime+stime del proceso del juego |
| **GPU** | `gpubusy` (Adreno) / `utilisation` (Mali) | Delta-based para Qualcomm, sysfs para Mali |
| **Temperatura** | `dumpsys thermalservice` + thermal zones | CPU, GPU, skin, bateria |
| **Bateria** | `dumpsys battery` | Drenaje real con USB charging disabled |
| **Frame Drops** | `dumpsys SurfaceFlinger` | Contador global de missed frames |
| **Render Resolution** | `dumpsys SurfaceFlinger` | Buffer size del SurfaceView del juego |
| **Eventos** | `logcat` filtrado por PID | GC, thermal throttling, ANR, crashes, audio, red |

---

## Requisitos

- **Java 8+** (viene incluido con Android SDK)
- **ADB** (Android Debug Bridge) en el PATH
- Dispositivo Android con **depuracion USB activada**

---

## Instalacion

### Opcion 1: Descargar JAR (recomendado)

```bash
# Descargar la ultima release
curl -LO https://github.com/zeroz3r0/android-game-perf-tool-cli/releases/latest/download/android-game-perf-tool-cli-6.0.0.jar

# Ejecutar
java -jar android-game-perf-tool-cli-6.0.0.jar
```

### Opcion 2: Compilar desde fuente

```bash
git clone https://github.com/zeroz3r0/android-game-perf-tool-cli.git
cd android-game-perf-tool-cli
./gradlew jar
java -jar build/libs/android-game-perf-tool-cli-6.0.0.jar
```

No necesitas instalar Gradle - el proyecto incluye Gradle Wrapper (`./gradlew`).

---

## Uso rapido

```bash
# 1. Conecta tu dispositivo Android por USB
# 2. Abre el juego que quieras medir
# 3. Ejecuta:

java -jar gameperf.jar          # Prueba indefinida (ENTER para parar)
java -jar gameperf.jar 60       # Prueba de 60 segundos
java -jar gameperf.jar --wifi   # Modo WiFi (mide bateria real)
```

El informe HTML se abre automaticamente en el navegador cuando termina.

---

## Opciones completas

| Flag | Descripcion | Default |
|------|-------------|---------|
| `[segundos]` | Duracion de la prueba | Indefinido (ENTER para parar) |
| `-p, --package <pkg>` | Paquete del juego manualmente | Auto-deteccion |
| `-o, --output <dir>` | Directorio de salida | `reports/` |
| `-w, --wifi` | Cambiar a ADB WiFi | USB |
| `-q, --quiet` | Sin explicaciones detalladas | No |
| `--json` | Exportar tambien en formato JSON | No |
| `--no-open` | No abrir informe automaticamente | Abre |
| `-h, --help` | Mostrar ayuda | - |
| `-v, --version` | Mostrar version | - |

### Ejemplos

```bash
# Prueba basica - detecta juego automaticamente
java -jar gameperf.jar

# Prueba de 2 minutos con paquete especifico
java -jar gameperf.jar -p com.supercell.clashofclans 120

# Modo WiFi - desconecta cable, mide bateria real
java -jar gameperf.jar --wifi 60

# Exportar HTML + JSON a directorio custom, sin abrir browser
java -jar gameperf.jar --json --no-open -o ~/informes 60
```

---

## Modo WiFi ADB

Con `--wifi`, la herramienta permite medir el **consumo real de bateria** sin que el cable USB cargue el dispositivo:

1. Detecta la IP del dispositivo en la red WiFi
2. Activa ADB por TCP/IP (puerto 5555)
3. Muestra countdown de 10 segundos para que desconectes el cable USB
4. Se conecta al dispositivo via WiFi
5. Mide el drenaje de bateria real durante toda la prueba

**Requisito**: El dispositivo y el ordenador deben estar en la misma red WiFi.

---

## Informe HTML

El informe generado incluye:

### Nota de rendimiento (A-F)
Puntuacion transparente con desglose:
- **Base**: 100 puntos
- **Penalizacion FPS**: 0-40 puntos segun FPS promedio
- **Penalizacion P1**: 0-15 puntos segun peor caso
- **Penalizacion problemas**: HIGH=-12, MEDIUM=-6, LOW=-2 cada uno
- Umbrales: A>=85, B>=70, C>=55, D>=40, F<40

### Graficos interactivos (Chart.js)
- **FPS temporal**: linea con anotaciones en 30fps y 60fps
- **Histograma frame times**: distribucion con rangos descriptivos (<8ms, 8-16ms, etc.)
- **Memoria**: Total PSS, Native Heap, Java Heap en el tiempo
- **CPU/GPU**: uso porcentual en el tiempo
- **Temperatura**: CPU, GPU, skin en el tiempo

### Percentiles
Tabla con P1, P5, P50, P90, P95, P99 para FPS y frame times.

### Timeline de eventos
Eventos categorizados que afectan al rendimiento:

| Categoria | Que detecta | Impacto |
|-----------|-------------|---------|
| GC | Recolector de basura | Micro-freeze de 1-50ms |
| Thermal | Thermal throttling | Reduce CPU/GPU |
| Memory | OOM, low memory, trim | Riesgo de crash |
| Jank | Frames perdidos | Tirones visibles |
| ANR | App no responde | Congelacion >5s |
| Crash | Errores fatales | Cierre del juego |
| Audio | Audio underrun | Sonido cortado |
| Network | Timeout de red | Lag online |
| Graphics | SurfaceFlinger errors | Frames perdidos |

### Problemas detectados
Cada problema incluye:
- Severidad (Alto/Medio/Bajo) con color
- Explicacion tecnica de que significa
- Solucion recomendada paso a paso

### Soporte para impresion
CSS adaptado para imprimir o exportar a PDF.

---

## Compatibilidad de dispositivos

### Verificado
- **Pixel 7a** (Tensor G2, Mali-G710, Android 15 SDK 36) - FPS, frame times, memoria, CPU, temperatura: OK. GPU: limitado por restricciones sysfs.

### Soportado
- **Qualcomm** (Snapdragon): FPS, frame times, memoria, CPU, GPU (gpubusy delta), temperatura
- **Samsung Exynos**: FPS, frame times, memoria, CPU, temperatura via thermalservice
- **Google Tensor**: FPS, frame times, memoria, CPU, temperatura via thermalservice
- **MediaTek**: FPS, frame times, memoria, CPU, temperatura via thermal zones

### Limitaciones conocidas
- **GPU en Pixel/Tensor**: sysfs paths estan restringidos en Android 14+, reporta -1 (no disponible)
- **Bateria WiFi en sesiones cortas**: el cambio de bateria es <1% en sesiones de <2 minutos
- **Juegos con VulkanSC**: pueden usar layers no estandar que SurfaceFlinger no reporta

---

## Arquitectura

```
src/main/kotlin/com/gameperf/
├── Main.kt                     # CLI entry point + arg parsing (196 lineas)
├── config/
│   └── AppConfig.kt            # Configuracion de la app
├── capture/
│   └── CaptureSession.kt       # Loop de captura en tiempo real
├── analysis/
│   ├── SessionAnalyzer.kt      # Orquestador del analisis
│   ├── ProblemDetector.kt      # Deteccion de problemas
│   ├── EventCategorizer.kt     # Categorizacion de eventos
│   └── GradeCalculator.kt      # Calculo de nota A-F
├── report/
│   ├── TerminalReporter.kt     # Output de consola
│   ├── HtmlReporter.kt         # Informe HTML con graficos
│   └── JsonReporter.kt         # Export JSON
├── i18n/
│   └── Strings.kt              # Textos centralizados
└── core/
    ├── AdbProvider.kt           # Interfaz para testabilidad
    ├── AdbConnector.kt          # Implementacion ADB real
    └── Models.kt                # Data classes con KDoc
```

14 archivos de produccion, 6 de tests. Cada archivo tiene una sola responsabilidad.

---

## Tests

```bash
./gradlew test    # 46 tests
```

| Suite | Tests | Que cubre |
|-------|-------|-----------|
| `PercentileStatsTest` | 6 | Calculo de percentiles, edge cases |
| `GradeCalculatorTest` | 7 | Scoring, penalizaciones, breakdown |
| `ProblemDetectorTest` | 11 | Todos los tipos de problemas |
| `EventCategorizerTest` | 11 | 10 categorias + filtering |
| `SessionAnalyzerTest` | 6 | Pipeline de analisis completo |
| `JsonReporterTest` | 5 | Estructura JSON, escaping |

---

## CI/CD

GitHub Actions ejecuta `build + test` en cada push/PR. Las releases con JAR se generan automaticamente al crear un tag `v*`.

---

## Como funciona internamente

### 1. Conexion
```
adb devices -l  -->  Detecta dispositivos conectados
getprop ro.product.model, ro.hardware, ...  -->  Lee specs del hardware
```

### 2. Deteccion del juego
```
dumpsys activity activities  -->  Busca foreground activity
Filtra paquetes de sistema (com.android.*, com.google.android.*, etc.)
```

### 3. Captura (cada segundo)
```
dumpsys SurfaceFlinger --latency '{layer}'  -->  Timestamps de presentacion
  └─> Ventana de 1s para FPS instantaneo
  └─> Delta entre frames para frame times
  └─> IQR filter para eliminar outliers

dumpsys meminfo {package}  -->  Total PSS, Native Heap, Java Heap
cat /proc/stat  -->  CPU total + per-core (delta entre muestras)
cat /proc/{pid}/stat  -->  CPU del proceso del juego
gpubusy / gpu_busy_percentage / utilisation  -->  GPU usage
dumpsys thermalservice  -->  Temperaturas (CPU, GPU, skin, battery)
dumpsys battery  -->  Nivel y estado de bateria
logcat --pid={pid}  -->  Logs del juego (GC, crashes, thermal, etc.)
dumpsys SurfaceFlinger  -->  Render resolution + missed frames
```

### 4. Analisis
```
PercentileStats  -->  P1, P5, P50, P90, P95, P99 para FPS y frame times
EventCategorizer  -->  Clasifica logs en 10 categorias
ProblemDetector  -->  Detecta problemas con thresholds
GradeCalculator  -->  Nota A-F con desglose transparente
```

### 5. Reporte
```
TerminalReporter  -->  Resumen en consola
HtmlReporter  -->  Informe HTML con Chart.js + CSS desde resource
JsonReporter  -->  Datos estructurados para integracion
```

---

## Versiones

- **v6.0.0** - Refactorizacion completa: arquitectura modular, interfaz AdbProvider, FPS windowed, GPU delta, outlier filtering, CPU per-process, 46 tests, CI/CD, validacion en Pixel 7a
- **v5.0** - WiFi ADB, frame-by-frame sampling, event categorization
- **v4.0** - Frame data unificado, deteccion de cambios graficos

---

## Licencia

MIT
