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

### `com.netpulse.app.data.source.AppUsageDataSource` (Role: data, Lines: 305)
- **Responsibility**: Per-app byte totals since local midnight via `NetworkStatsManager`. Walks every installed UID with `queryDetailsForUid` (a whole-day `queryDetails` is silently capped at 100 rows, and Samsung records streaming traffic in the FOREGROUND stats set that `querySummary` omits). Discovery is spread across polls in slices of `SWEEP_SLICE`; apps already reporting traffic are refreshed every poll so their totals stay live. Queries end at `now + BUCKET_PAD_MS`, not `now`: `NetworkStatsManager` returns a bucket only when it lies entirely inside `[start, end]`, so the open bucket containing the current moment is otherwise invisible (device-verified: `day..now` returned 0 rows, `day..now+2h` returned the live bucket). Buckets are filtered through `countsTowardDay(end, dayStart)`, so the pad cannot leak yesterday into today. That predicate keeps a bucket that straddles midnight whole, because `NetworkStats.Bucket` exposes whole-bucket totals only; the overcount is bounded by one bucketDuration and is covered by `AppUsageDataSourceTest`. When the service is already running near midnight (`withinBaselineWindow`), it snapshots each UID's cumulative total and reports `all - baseline` instead, which removes the straddle overcount exactly. If the service started later, it falls back to whole-bucket sums so earlier bytes are never subtracted away. If the per-UID pass yields nothing, a chunked `queryDetails` fallback runs at most once per `FALLBACK_INTERVAL_MS`, subdividing any window that returns `ROW_CAP` rows so truncation cannot occur.
- **Public Functions**:
  ```kotlin
  fun hasUsageAccess(): Boolean                 // AppOps GET_USAGE_STATS
  fun queryTodayTotals(): Map<String, Long>     // packageName -> rx+tx bytes
  fun labelFor(packageName: String): String
  ```
- **Consumers**: `AppUsageTracker`.

### `com.netpulse.app.core.AppUsageTracker` (Role: core, Lines: 154)
- **Responsibility**: Converts per-app totals into live rates and a bounded minute-resolution log. Deltas are clamped (NetworkStats jitters) and reported rates decay over `RATE_DECAY_MS` instead of dropping instantly.
- **Public Symbols**:
  ```kotlin
  val liveApps: StateFlow<List<AppUsage>>       // sorted by bytesPerSec
  val log: StateFlow<List<UsageLogEntry>>       // MAX_LOG_ENTRIES = 300
  fun hasUsageAccess(): Boolean
  fun seedLog(entries: List<UsageLogEntry>)     // restore persisted log on start
  fun reset()
  fun poll(): Boolean                            // true when the log grew
  ```
- **Consumers**: `NetPulseService` (poll loop), `HomeViewModel`.

### `com.netpulse.app.ui.components.AppUsageCard / UsageLogCard` (Role: ui, Lines: 208)
- **Responsibility**: Renders the live per-app ranking with proportional bars, the usage-access prompt, and the scrollable minute log.
- **Public Functions**:
  ```kotlin
  fun AppUsageCard(apps: ImmutableList<AppUsage>, hasUsageAccess: Boolean, onRequestAccess: () -> Unit)
  fun UsageLogCard(entries: ImmutableList<UsageLogEntry>)
  ```
- **Consumers**: `HomeScreen`.

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
- **2026-10-28 (c)**: Midnight-straddle baseline subtraction. Bucket grid verified :30-aligned, so the first bucket of the day runs 23:30-01:30 and whole-bucket counting overstated today by up to 30 min of yesterday. When the service is running across midnight it captures a per-UID cumulative baseline and reports the exact difference; otherwise it keeps the previous whole-bucket behavior. Window decision extracted as `withinBaselineWindow` with 4 tests. 26 unit tests green.
- **2026-10-28 (b)**: Extracted the day filter into `countsTowardDay(end, dayStart)` and covered it with `AppUsageDataSourceTest` (midnight boundary, one ms after, whole-day, before-midnight, straddling). Documents the known overcount: a bucket spanning midnight is kept whole because `NetworkStats.Bucket` cannot be split. No behavior change; 22 unit tests green.
- **2026-10-28**: Per-app live usage + usage log. `NetworkStatsManager` is the only source that can see other UIDs (`TrafficStats.getUidRxBytes` returns -1 for them). Per-UID queries replace whole-day `queryDetails` (silently capped at 100 rows, which froze totals). `AppUsageTracker` derives decaying rates from clamped deltas and folds them into a 300-entry minute log persisted to DataStore. Manifest gains `QUERY_ALL_PACKAGES` (sideloaded apps were invisible) and `PACKAGE_USAGE_STATS`. Status-bar glyph now always keeps its unit letter (`MAX_LABEL_LENGTH = 4`, `TEXT_SCALE_X = 1.0`). Root cause of the empty/frozen per-app list: `NetworkStatsManager` discards any bucket not fully inside the queried window, so `end=now` hid the open bucket. Window is now padded (`BUCKET_PAD_MS`) and buckets ending before local midnight are dropped. Device-verified: YouTube 1.3 MB/s then 1.5 MB/s during transfer, decaying to 42.5 KB/s; usage log persisted `01:56 YouTube 11.9 MB` and `01:59 YouTube 8.0 MB`. Chunked `queryDetails` fallback retained and rate-limited to `FALLBACK_INTERVAL_MS`, subdividing `ROW_CAP` windows.
