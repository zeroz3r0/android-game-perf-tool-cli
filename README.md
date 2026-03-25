# Android Game Performance Tool

CLI tool for measuring game performance on Android devices.

## Features

- 📱 List connected Android devices via ADB
- 📝 Capture logs in real-time
- 📊 Extract performance metrics (FPS, frame time, memory)
- 📋 Generate performance reports (JSON/Markdown)
- 🔍 Custom analysis with extensible rules

## Requirements

- Java 17+
- Android SDK (ADB)

## Installation

1. Download the latest JAR from releases
2. Or build from source:

```bash
git clone https://github.com/zeroz3r0/android-game-perf-tool.git
cd android-game-perf-tool-cli
gradle build
```

## Usage

```bash
java -jar build/libs/android-game-perf-tool-cli-0.1.0.jar
```

### Options

1. **List devices** - Shows connected Android devices
2. **Capture logs** - Stream logcat in real-time
3. **Extract metrics** - Gather FPS, memory, frame time
4. **Generate report** - Create performance report
5. **Custom analysis** - Use custom rules

## Custom Rules

Edit `src/main/resources/rules-default.json` to add custom patterns:

```json
{
  "rules": [
    {
      "id": "my_rule",
      "name": "My Custom Rule",
      "pattern": "MyPattern[:\\s]+(\\d+)",
      "metricType": "FPS",
      "severity": "WARNING"
    }
  ]
}
```

## Architecture

```
src/main/kotlin/com/gameperf/
├── Main.kt              # CLI entry point
├── core/
│   ├── AdbConnector.kt    # ADB device connection
│   ├── LogcatReader.kt    # Log capture
│   ├── MetricsExtractor.kt # Performance metrics
│   └── ReportGenerator.kt  # Report generation
└── analysis/
    └── RulesEngine.kt     # Analysis rules
```

## License

MIT
