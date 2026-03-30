# Android Game Performance Tool

Herramienta CLI profesional para medir y analizar el rendimiento de juegos en dispositivos Android.

Genera informes detallados con FPS, frame times, memoria, CPU, GPU, temperatura y bateria - todo en un informe HTML interactivo con graficos.

## Que mide

| Metrica | Fuente | Precision |
|---------|--------|-----------|
| **FPS** | SurfaceFlinger --latency (timestamps de presentacion) | Ventana temporal de 1s |
| **Frame Times** | Delta entre frames consecutivos con filtro de outliers | IQR-based cleaning |
| **Memoria** | dumpsys meminfo (Total PSS + Native Heap + Java Heap) | Por proceso |
| **CPU** | /proc/stat (total + per-core con delta) | Delta entre muestras |
| **GPU** | gpubusy (Adreno delta) / utilisation (Mali) | Delta-based para Adreno |
| **Temperatura** | thermal_zone sysfs (CPU, GPU, skin, bateria) | Directo del kernel |
| **Bateria** | dumpsys battery (con USB charging disabled) | Drenaje real |
| **Frame Drops** | SurfaceFlinger missed frame counter | Global del compositor |

## Requisitos

- Java 8+ (viene con Android SDK)
- ADB (Android Debug Bridge) en el PATH
- Dispositivo Android con modo desarrollador activado

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
gradle jar
java -jar build/libs/android-game-perf-tool-cli-6.0.0.jar
```

## Uso

```bash
# Prueba basica (detecta juego automaticamente)
java -jar gameperf.jar

# Prueba de 60 segundos
java -jar gameperf.jar 60

# Especificar paquete del juego
java -jar gameperf.jar -p com.supercell.clashofclans 120

# Modo WiFi (mide bateria real sin carga USB)
java -jar gameperf.jar --wifi

# Exportar JSON + HTML, sin abrir browser
java -jar gameperf.jar --json --no-open -o ~/reports 60
```

### Opciones

| Flag | Descripcion | Default |
|------|-------------|---------|
| `[segundos]` | Duracion de la prueba | Indefinido (ENTER para parar) |
| `-p, --package <pkg>` | Paquete del juego | Auto-deteccion |
| `-o, --output <dir>` | Directorio de salida | `reports/` |
| `-w, --wifi` | Cambiar a ADB WiFi | USB |
| `-q, --quiet` | Sin explicaciones detalladas | No |
| `--json` | Exportar tambien en JSON | No |
| `--no-open` | No abrir informe automaticamente | Abre |
| `-h, --help` | Mostrar ayuda | - |
| `-v, --version` | Mostrar version | - |

## Informe HTML

El informe incluye:

- **Nota de rendimiento** (A-F) con desglose transparente de la puntuacion
- **Graficos interactivos** (Chart.js): FPS temporal, histograma de frame times, memoria, CPU/GPU, temperatura
- **Percentiles** (P1, P5, P50, P90, P95, P99) para FPS y frame times
- **Timeline de eventos** categorizada (GC, thermal, jank, audio, memoria, crashes)
- **Problemas detectados** con explicaciones y soluciones recomendadas en espanol
- **Correlacion FPS-eventos**: muestra que evento causo cada caida de FPS
- **Soporte para impresion** con estilos adaptados

## Arquitectura

```
src/main/kotlin/com/gameperf/
├── Main.kt                     # CLI entry point + arg parsing
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
    ├── AdbConnector.kt          # Comunicacion con ADB
    └── Models.kt                # Data classes
```

## Tests

```bash
gradle test    # 41 tests
```

Cobertura:
- `PercentileStats`: calculo de percentiles, edge cases
- `GradeCalculator`: scoring, penalizaciones, breakdown
- `ProblemDetector`: deteccion de todos los tipos de problemas
- `EventCategorizer`: clasificacion de 10 categorias de eventos
- `JsonReporter`: estructura y contenido del JSON

## Como funciona

1. **Conexion**: Detecta dispositivos via ADB (USB o WiFi)
2. **Deteccion**: Encuentra el juego en primer plano automaticamente
3. **Captura**: Cada segundo muestrea FPS, memoria, CPU, GPU, temperatura, logs
4. **Analisis**: Calcula percentiles, detecta problemas, categoriza eventos, correlaciona FPS drops con eventos
5. **Reporte**: Genera informe HTML interactivo + JSON opcional

### Modo WiFi

Con `--wifi`, la herramienta:
1. Obtiene la IP del dispositivo
2. Activa ADB por TCP/IP
3. Te pide que desconectes el cable USB
4. Se conecta por WiFi
5. Mide el consumo REAL de bateria (sin carga USB)

## Versiones

- **v6.0.0** - Refactorizacion completa: arquitectura modular, FPS windowed, GPU delta-based, outlier filtering, 41 tests, CI/CD
- **v5.0** - WiFi ADB, frame-by-frame sampling, event categorization
- **v4.0** - Frame data unificado, deteccion de cambios graficos

## Licencia

MIT
