# Changelog

Todos los cambios notables del proyecto se documentan aqui.
Formato basado en [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [6.0.0] - 2026-03-30

### Validado
- Probado en Pixel 7a (Tensor G2, Mali-G710, Android 15 SDK 36): FPS 59-60, frame times 16.7ms, CPU 40%, temperatura 52C

### Cambiado (Breaking)
- Arquitectura completamente refactorizada: Main.kt (1036 lineas) dividido en 14 modulos con responsabilidad unica
- Modelo de datos renombrado a ingles (Problem, Severity, AnalysisResult)
- Informes se generan en directorio `reports/` en vez del directorio actual

### Agregado
- **FPS windowed**: calcula FPS instantaneo sobre ventana de 1 segundo en vez del buffer completo de SurfaceFlinger
- **GPU delta-based**: medicion de GPU por delta de busy_time/total_time (Adreno gpubusy) para Qualcomm, mas preciso que gpu_busy_percentage
- **Outlier filtering**: filtro estadistico IQR para frame times, elimina artifacts de medicion
- **Layer caching**: cache del nombre del layer de SurfaceFlinger durante la sesion
- **Monotonic timestamps**: filtra timestamps no-monotonicos de SurfaceFlinger
- **47 tests unitarios**: PercentileStats, GradeCalculator, ProblemDetector, EventCategorizer, SessionAnalyzer, JsonReporter
- **Interfaz AdbProvider**: abstraccion para dependency injection y testing
- **CPU per-process**: medicion de CPU del juego via /proc/{pid}/stat
- **KDoc**: documentacion en toda la API publica (Models, AdbProvider)
- **CSS en resource**: report.css como archivo separado cargado en runtime
- **Gradle wrapper**: ./gradlew incluido, zero-setup builds
- **Temperatura via thermalservice**: fallback a `dumpsys thermalservice` para Android 10+
- **SurfaceFlinger Android 15+**: soporte para formato RequestedLayerState{}
- **CI/CD**: GitHub Actions con build + test automatico + releases con JAR
- **Grade breakdown**: desglose transparente de la nota en el informe HTML
- **Progress bar**: barra de progreso para sesiones con duracion definida
- **--package flag**: especificar paquete del juego manualmente
- **--no-open flag**: no abrir informe automaticamente
- **--version flag**: mostrar version
- **Footer con branding**: version y timestamp en el informe HTML
- **Print mode mejorado**: CSS completo para impresion con colores adaptados
- **i18n centralizado**: todos los textos en Strings.kt

### Mejorado
- CSS con clases en vez de estilos inline (mantenibilidad del HTML)
- Opacidades de cores con 2 decimales en vez de 15
- Labels en graficos de FPS (lineas de 30fps y 60fps etiquetadas)
- Histograma de frame times con ranges descriptivos (ej: "8-16ms (60-120fps)")
- .gitignore actualizado para excluir informes generados

### Corregido
- GPU mostraba 3% constante en algunos Qualcomm (ahora usa delta-based)
- FPS calculaba promedio sobre buffer completo (128 frames) en vez de ventana temporal
- Frame times incluia outliers de medicion que distorsionaban percentiles
- SurfaceFlinger --list en Android 15 devuelve formato diferente (RequestedLayerState{})
- Temperaturas no se leian en Pixel/Tensor (sysfs restringido, ahora usa thermalservice)
- README listaba archivos que ya no existian (MetricsExtractor, LogcatReader, etc.)
- build/ y .gradle/ eliminados de git tracking
- 18 informes HTML viejos eliminados del repositorio

## [5.0.0] - 2026-03-26

### Agregado
- WiFi ADB mode para medicion real de bateria
- Frame-by-frame sampling via SurfaceFlinger --latency
- Event categorization (10 categorias)
- Event timeline en informe HTML
- Deteccion de cambios de resolucion

## [4.0.0] - 2026-03-25

### Agregado
- Frame data unificado (FPS + frame times de una sola lectura)
- Deteccion de cambios de configuracion grafica
- User control (ENTER/Ctrl+C para detener)
