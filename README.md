# Cron

An intelligent Android alarm app that automatically wakes you up before your first calendar event of the day.

## Overview

Cron reads your calendar, finds tomorrow's first event, and intelligently schedules an alarm to wake you up with enough time to prepare. It factors in travel time using the Google Routes API and adjusts the wake-up time accordingly.

## Features

- **Automatic Scheduling**: Scans your calendar and sets an alarm before your first event of the day
- **Smart Travel Time**: Integrates with Google Routes API to calculate drive time and wake you up earlier when needed
- **Configurable Windows**: Set your earliest/latest acceptable alarm times (default: 6:00 AM - 10:00 AM)
- **Event Grouping**: Merges closely spaced events into blocks to avoid multiple alarms
- **Real-time Updates**: Monitors calendar changes and automatically adjusts alarms
- **Snooze Support**: Includes configurable snooze functionality (10 min, max 3 snoozes)
- **Material Design 3**: Modern UI built with Jetpack Compose
- **Background Sync**: Periodic WorkManager tasks ensure alarms stay synchronized

## Requirements

- Android 8.0 (API 26) or higher
- Calendar app with events
- Permissions:
  - Calendar (read access)
  - Notifications
  - Coarse location (optional, for travel time calculation)

## Building the Project

### Prerequisites

- JDK 11
- Android Studio (latest stable version recommended)
- Gradle 8.x (included via wrapper)

### Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd Cron
   ```

2. **Configure API keys** (optional but recommended for travel time features)
   
   Create a `local.properties` file in the project root:
   ```properties
   GOOGLE_ROUTES_API_KEY=your_api_key_here
   ```
   
   To get a Google Routes API key:
   - Go to [Google Cloud Console](https://console.cloud.google.com/)
   - Enable the Routes API
   - Create an API key with Routes API access

3. **Build the project**
   ```bash
   ./gradlew assembleDebug
   ```

4. **Run on device/emulator**
   ```bash
   ./gradlew installDebug
   ```
   
   Or open the project in Android Studio and click Run.

### Build Variants

- **Debug**: Development build with debugging enabled
  ```bash
  ./gradlew assembleDebug
  ```

- **Release**: Optimized production build
  ```bash
  ./gradlew assembleRelease
  ```

### Running Tests

```bash
# Unit tests
./gradlew testDebugUnitTest

# Lint checks
./gradlew lintDebug

# All checks
./gradlew check
```

## CI/CD

The project includes GitHub Actions workflows:

- **Build CI** (`.github/workflows/build.yml`): Runs on every push/PR to main
  - Builds debug APK
  - Runs lint checks
  - Executes unit tests
  - Skips draft pull requests

- **Release CI** (`.github/workflows/release.yml`): Triggered on version tags
  - Builds release APK
  - Creates GitHub release with APK attachment
  - Auto-generates release notes

### Creating a Release

```bash
git tag v1.0.0
git push origin v1.0.0
```

### Environment Variables for CI

Store sensitive configuration in GitHub Secrets (Settings → Secrets and variables → Actions):
- `GOOGLE_ROUTES_API_KEY`: Your Google Routes API key

## Architecture

The app follows a clean architecture pattern:

```
app/
├── engine/              # Core business logic (calendar, scheduling, travel time)
│   ├── calendar/        # Calendar reading
│   ├── orchestrator/    # Main synchronization engine
│   ├── scheduler/       # Alarm scheduling
│   ├── travel/          # Travel time estimation
│   ├── config/          # Configuration
│   └── model/           # Data models
├── ui/                  # Jetpack Compose UI
│   ├── screens/         # Screen composables and ViewModels
│   ├── components/      # Reusable UI components
│   └── theme/           # Material Design 3 theme
├── receiver/            # BroadcastReceivers (boot, alarm)
└── worker/              # WorkManager background tasks
```

### Key Components

- **CronOrchestrator**: The brain of the app. Reads calendar events, calculates optimal alarm time, and schedules alarms.
- **CalendarReader**: Reads events from the Android Calendar Provider.
- **AlarmScheduler**: Schedules/cancels alarms using AlarmManager.
- **GoogleRoutesTravelTimeProvider**: Fetches real-time travel duration from Google Routes API.
- **HomeViewModel**: Bridges the engine to the Compose UI.

## How It Works

1. **Event Discovery**: Reads calendar events in a 36-hour look-ahead window
2. **Event Filtering**: Focuses on tomorrow's events, skipping all-day events
3. **Block Detection**: Groups events closer than 30 minutes into a single block
4. **Travel Time**: Optionally fetches drive time to the event location
5. **Alarm Calculation**: 
   - Default wake time: 90 minutes before first event
   - Adjusted by travel time if available
   - Clamped to configured earliest/latest times (6:00 AM - 10:00 AM)
6. **Scheduling**: Sets alarm using Android AlarmManager
7. **Monitoring**: ContentObserver watches for calendar changes and re-syncs automatically

## Configuration

Default settings can be found in `CronConfig.kt`:

```kotlin
val DEFAULT = CronConfig(
    enabled = true,
    defaultPrepTime = Duration.ofMinutes(90),
    earliestAlarm = LocalTime.of(6, 0),
    latestAlarm = LocalTime.of(10, 0),
    snoozeDuration = Duration.ofMinutes(10),
    maxSnoozeCount = 3,
    skipAllDayEvents = true,
    eventMergeThreshold = Duration.ofMinutes(30),
    lookAheadHours = 36
)
```

## License

[Add your license here]

## Contributing

[Add contribution guidelines here]

