# CODEBASE.md: NetPulse Semantic Digest

> **Notice**: This file is an AI-optimized semantic index. Do not write narrative prose. Keep token density high.

## 1. System Topology & Data Flow
```text
Kernel (/proc/net/dev) ──> TrafficDataSource ──> NetPulseService
                                                        │
                      ┌─────────────────────────────────┴─────────────────────────────────┐
                      ▼                                 ▼                                 ▼
           SpeedNotificationManager           FloatingOverlayManager               UsageRepository
                      │                                 │                                 │
                      ▼                                 ▼                                 ▼
             System Notification               WindowManager Overlay               LocalDataStore
                      ▲
                      │
               ScreenStateReceiver (ACTION_SCREEN_OFF / ACTION_SCREEN_ON)
```

## 2. Global Constraints & Architecture Patterns
- **Primary Language & Platform**: Kotlin 2.0.20, Android SDK API 33-35 (Min 33, Target 35).
- **Architectural Paradigm**: Clean Role-Based (core/, domain/, data/, service/, ui/).
- **UI Framework**: Jetpack Compose (Material 3 One UI style, strict XRoute / XScreen separation).
- **Hard Constraints**: <400 LOC/file, <60 LOC/composable, 0 emojis in UI layouts (Material Icons only), zero WakeLocks, screen-off suspension.
- **Target Distribution**: Android signed APK (`app-release.apk`) via GitHub Releases.

## 3. Module & Interface Skeleton

### `com.netpulse.app.core.Constants` (Role: core, Lines: 17)
- **Responsibility**: Global identifiers, notification channels, Samsung package constants.
- **Public Symbols**:
  ```kotlin
  const val CHANNEL_ID: String
  const val NOTIFICATION_ID: Int
  const val ACTION_START_SERVICE: String
  const val ACTION_STOP_SERVICE: String
  const val DEFAULT_REFRESH_INTERVAL_MS: Long
  const val SAMSUNG_DEVICE_CARE_PACKAGE: String
  ```
- **Consumers**: `NetPulseService`, `SpeedNotificationManager`, `HomeViewModel`, `MainActivity`.

### `com.netpulse.app.core.SpeedFormatter` (Role: core, Lines: 88)
- **Responsibility**: Pure speed throughput and storage data formatting for bytes/bits.
- **Public Functions**:
  ```kotlin
  fun formatSpeed(bytesPerSec: Long, inBits: Boolean = false): FormattedSpeed
  fun formatDataUsage(bytes: Long): String  // 0 MB | N KB | N.N MB | N.NN GB
  data class FormattedSpeed(val value: String, val unit: String)
  ```
- **Consumers**: `SpeedNotificationManager`, `FloatingOverlayManager`, `HomeViewModel`, UI cards.

### `com.netpulse.app.core.BatteryOptimizer` (Role: core, Lines: 50)
- **Responsibility**: Samsung Device Care and system battery optimization exemption intents.
- **Public Functions**:
  ```kotlin
  fun isIgnoringBatteryOptimizations(context: Context): Boolean
  fun requestIgnoreBatteryOptimizations(context: Context)
  fun openSamsungDeviceCare(context: Context): Boolean
  fun isSamsungDevice(): Boolean
  ```
- **Consumers**: `HomeViewModel`, `BatteryGuideRoute`.

### `com.netpulse.app.core.TelemetryManager` (Role: core, Lines: 19)
- **Responsibility**: Process-wide singleton `StateFlow<SpeedSnapshot>` bridging service sampling to UI, avoiding duplicate sampling loops.
- **Public Symbols**:
  ```kotlin
  val currentSpeed: StateFlow<SpeedSnapshot>
  fun updateSpeed(snapshot: SpeedSnapshot)
  fun reset()
  ```
- **Consumers**: `NetPulseService`, `HomeViewModel`.

### `com.netpulse.app.core.NetworkMonitor` (Role: core, Lines: 65)
- **Responsibility**: Observes network transports (Wi-Fi, Mobile) via `ConnectivityManager.NetworkCallback`.
- **Public Functions**:
  ```kotlin
  fun observeConnection(): Flow<NetworkConnection>
  fun getCurrentConnection(): NetworkConnection
  enum class ActiveNetworkType { WIFI, MOBILE, ETHERNET, OTHER, NONE }
  ```
- **Consumers**: `NetPulseService`, `HomeViewModel`.

### `com.netpulse.app.data.source.TrafficDataSource` (Role: data, Lines: 65)
- **Responsibility**: Samples raw kernel network statistics via `android.net.TrafficStats`.
- **Public Functions**:
  ```kotlin
  fun sample(): SpeedSnapshot
  fun reset()
  interface TrafficStatsProvider
  ```
- **Consumers**: `NetPulseService`, `HomeViewModel`.

### `com.netpulse.app.data.source.LocalDataStore` (Role: data, Lines: 85)
- **Responsibility**: DataStore Preferences manager for application settings and daily usage history.
- **Public Functions**:
  ```kotlin
  val meterPreferencesFlow: Flow<MeterPreferences>
  fun observeDailyUsage(dateKey: String): Flow<DailyUsage>
  suspend fun addUsage(dateKey: String, mobileDelta: Long, wifiDelta: Long)
  suspend fun resetUsage(dateKey: String)
  ```
- **Consumers**: `PreferencesRepositoryImpl`, `UsageRepositoryImpl`.

### `com.netpulse.app.service.NetPulseService` (Role: service, Lines: 145)
- **Responsibility**: Foreground service managing low-overhead 1Hz polling loop with screen-off pause.
- **Public Lifecycle**: `onCreate()`, `onStartCommand()`, `onDestroy()`.
- **Side Effects**: System notification updates, window overlay layout mutations, DataStore writes.

### `com.netpulse.app.service.ScreenStateReceiver` (Role: service, Lines: 40)
- **Responsibility**: Pauses/resumes polling loop upon display power state changes.
- **Public Functions**:
  ```kotlin
  fun register(context: Context)
  fun unregister(context: Context)
  ```
- **Consumers**: `NetPulseService`.

### `com.netpulse.app.service.SpeedNotificationManager` (Role: service, Lines: 75)
- **Responsibility**: Configures silent NotificationChannel and builds persistent status bar notification.
- **Public Functions**:
  ```kotlin
  fun buildNotification(speed: SpeedSnapshot, usage: DailyUsage, unitInBits: Boolean, showUpload: Boolean, useDynamicIcon: Boolean): Notification
  fun updateNotification(notification: Notification)
  fun createNotificationChannel(context: Context)
  ```
- **Consumers**: `NetPulseService`, `NetPulseApp`.

### `com.netpulse.app.service.NetPulseTileService` (Role: service, Lines: 50)
- **Responsibility**: Samsung Quick Settings panel tile toggle.
- **Public Lifecycle**: `onStartListening()`, `onClick()`.

### `com.netpulse.app.ui.home.HomeRoute / HomeScreen` (Role: ui, Lines: 120)
- **Responsibility**: Two-layer presentation displaying speed gauge, daily usage, and service controls.
- **State Contract**: `HomeUiState` (immutable, includes `showUploadSpeed`), `HomeEvent` (sealed interface).

### `com.netpulse.app.ui.settings.SettingsRoute / SettingsScreen` (Role: ui, Lines: 90)
- **Responsibility**: Two-layer presentation for notification, overlay, units, and battery behavior toggles.
- **State Contract**: `SettingsUiState` (immutable), `SettingsEvent` (sealed interface).

### `com.netpulse.app.ui.guide.BatteryGuideRoute / BatteryGuideScreen` (Role: ui, Lines: 110)
- **Responsibility**: Samsung One UI battery exemption instructions and intent triggers.

## 4. Execution Lifecycle Trace
1. **Startup**: `NetPulseApp` initializes singletons, ensures notification channel, and starts `NetPulseService` if enabled.
2. **Foreground Telemetry**: `NetPulseService` runs 1s polling interval on `Dispatchers.Default` reading `TrafficDataSource`.
3. **Screen-Off Suspension**: `ScreenStateReceiver` catches `ACTION_SCREEN_OFF`, immediately cancelling polling coroutine to eliminate CPU wakeups.
4. **Resumption**: `ACTION_SCREEN_ON` resumes polling coroutine.
5. **Tile Interaction**: `NetPulseTileService` toggles service state from Samsung Quick Panel.

## 5. Verification Commands
```bash
# Unit Tests
./gradlew testDebugUnitTest --no-daemon

# Assemble Debug APK
./gradlew assembleDebug --no-daemon

# Assemble Signed Release APK
./gradlew assembleRelease --no-daemon
```

## 6. Recent Iteration Changes
- **2026-10-13**: Initialized complete NetPulse architecture (core, domain, data, service, Compose UI, unit tests, GitHub CI/Release workflows).
- **2026-10-14**: v1.0.1 fix release. Added runtime `POST_NOTIFICATIONS` request (fixes dead notification/status-bar speed on Android 13+); R8 minify + resource shrink (42 MB -> 2.1 MB); stable release keystore (`keystore.properties` + GitHub secrets, fixes per-build dynamic debug key); `TelemetryManager` (core) decouples sampling from UI; `HomeUiState` gains `hasNotificationPermission`/`canDrawOverlays`; overlay permission prompt in `SettingsViewModel`.
