# Android Game Performance Tool

CLI tool for measuring game performance on Android devices.

## Features

- 📱 List connected Android devices via ADB
- 📝 Capture logs in real-time
- 📊 Extract performance metrics (FPS, frame time, memory, CPU)
- 📋 Generate performance reports (JSON/Markdown)
- 🔍 Custom analysis with extensible rules
- ⚠️ Severity-based issue detection (INFO/WARNING/ERROR)

## Requirements

- Kotlin 1.9+
- Java 11+
- Android SDK (ADB)
- Android device with developer mode enabled

## Building

```bash
git clone https://github.com/zeroz3r0/android-game-perf-tool.git
cd android-game-perf-tool-cli
./gradlew build
```

## Running

### Quick Start

```bash
# List devices
./gradlew run --args="devices"

# Capture logs and generate report
./gradlew run --args="capture -d <device-id> -t 30 -o report.md"

# Analyze existing log file
./gradlew run --args="analyze -i logs.txt -o report.md"
```

### Commands

| Command | Description |
|---------|-------------|
| `devices` | List connected Android devices |
| `capture` | Capture live logs from device |
| `analyze` | Analyze existing log file |
| `report` | Generate report from analysis |

### Options

| Flag | Description | Default |
|------|-------------|---------|
| `-d, --device` | Device serial number | Auto-detect |
| `-t, --duration` | Capture duration (seconds) | 60 |
| `-i, --input` | Input log file | stdin |
| `-o, --output` | Output report file | stdout |
| `-f, --format` | Report format (md/json) | md |
| `-r, --rules` | Custom rules JSON file | default |

## Default Rules

The tool includes built-in rules in `src/main/resources/rules-default.json`:

| Rule | Type | Severity | Description |
|------|------|----------|-------------|
| Low FPS (<30) | FPS | ERROR | Unplayable frame rate |
| Medium FPS (<45) | FPS | WARNING | Suboptimal experience |
| High Frame Time (>33ms) | FRAME_TIME | ERROR | Below 30 FPS |
| High Memory (>512MB) | MEMORY | WARNING | Elevated memory |
| Critical Memory (>768MB) | MEMORY | ERROR | Risk of OOM |
| GC Pressure | MEMORY | WARNING | Garbage collection stutters |
| Dropped Frames | FRAME_TIME | WARNING | Missed frames |
| CPU Throttling | CPU | WARNING | Thermal throttling |
| Jank Detection | FRAME_TIME | ERROR | Visible stutters |

## Custom Rules

Edit `src/main/resources/rules-default.json` or create a custom rules file:

```json
{
  "rules": [
    {
      "id": "my_rule",
      "name": "My Custom Rule",
      "pattern": "MyPattern[:\\s]+(\\d+)",
      "metricType": "FPS",
      "severity": "WARNING",
      "threshold": 50,
      "description": "Custom detection rule"
    }
  ]
}
```

### Supported Metric Types

- `FPS` - Frames per second
- `FRAME_TIME` - Milliseconds per frame
- `MEMORY` - Memory usage (MB)
- `CPU` - CPU utilization

### Severity Levels

- `INFO` - Informational messages
- `WARNING` - Performance warnings
- `ERROR` - Critical issues

## Output Examples

### Markdown Report

```markdown
# Performance Report

## Summary
- Total Issues: 5
- Errors: 2
- Warnings: 3

## Metrics
- Average FPS: 52.3 (min: 28, max: 60)
- Average Memory: 450 MB

## Issues

### ERROR: Low FPS
- Value: 28 FPS
- Time: 2024-01-15 10:30:45

### WARNING: GC Pressure
- Pattern: GC_FOR_MALLOC
```

### JSON Report

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "device": "emulator-5554",
  "summary": { "errors": 2, "warnings": 3 },
  "metrics": { "fps": { "avg": 52.3 } },
  "issues": [...]
}
```

## Architecture

```
src/main/kotlin/com/gameperf/
├── Main.kt              # CLI entry point
├── core/
│   ├── AdbConnector.kt  # ADB device communication
│   ├── LogcatReader.kt  # Logcat parsing
│   ├── MetricsExtractor.kt # Metric extraction
│   └── ReportGenerator.kt  # Report generation
└── analysis/
    └── RulesEngine.kt   # Pattern matching engine
```

## License

MIT