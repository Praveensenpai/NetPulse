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

### `com.netpulse.app.data.source.AppUsageDataSource` (Role: data, Lines: 360)
- **Responsibility**: Per-app byte totals since local midnight via `NetworkStatsManager`. Walks every installed UID with `queryDetailsForUid` (a whole-day `queryDetails` is silently capped at 100 rows, and Samsung records streaming traffic in the FOREGROUND stats set that `querySummary` omits). Discovery is spread across polls in slices of `SWEEP_SLICE`; apps already reporting traffic are refreshed every poll so their totals stay live. Queries end at `now + BUCKET_PAD_MS`, not `now`: `NetworkStatsManager` returns a bucket only when it lies entirely inside `[start, end]`, so the open bucket containing the current moment is otherwise invisible (device-verified: `day..now` returned 0 rows, `day..now+2h` returned the live bucket). Buckets are filtered through `countsTowardDay(end, dayStart)`, so the pad cannot leak yesterday into today. That predicate keeps a bucket that straddles midnight whole, because `NetworkStats.Bucket` exposes whole-bucket totals only; the overcount is bounded by one bucketDuration and is covered by `AppUsageDataSourceTest`. When the service is already running near midnight (`withinBaselineWindow`), it snapshots each UID's cumulative total and reports `all - baseline` instead, which removes the straddle overcount exactly. The snapshotting visit itself reports 0 rather than the whole-bucket sum, so the figure grows from zero instead of jumping to an inflated value and collapsing on the next poll. If the service started later, it falls back to whole-bucket sums so earlier bytes are never subtracted away. `sinceBaseline(all, baseline)` returns null when netstats trims the history a baseline was taken against (or a reboot resets the counters); the UID is then re-baselined on the current cumulative total instead of being clamped to 0 B until the next midnight. The result map keeps every swept package, including 0 B ones, and `queryTodayTotals()` returns it unfiltered: callers must be able to tell a package that was swept and has 0 B from one that has not been swept yet. Dropping zeros made `pendingSweep` re-queue them forever, so `sweepComplete()` never returned true and v1.4.0's snapshot persistence stopped minutes after startup. `sweepComplete() = pendingSweep.isEmpty() && sweepTotals.isNotEmpty()`. The tracker filters zero-byte packages out of the UI rows. If the per-UID pass yields nothing, a chunked `queryDetails` fallback runs at most once per `FALLBACK_INTERVAL_MS`, subdividing any window that returns `ROW_CAP` rows so truncation cannot occur.
- **Public Functions**:
  ```kotlin
  fun hasUsageAccess(): Boolean                 // AppOps GET_USAGE_STATS
  fun queryTodayTotals(): Map<String, Long>     // packageName -> rx+tx bytes
  fun labelFor(packageName: String): String
  ```
- **Consumers**: `AppUsageTracker`.

### `com.netpulse.app.core.AppUsageTracker` (Role: core, Lines: 273)
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

### `com.netpulse.app.data.source.LocalDataStore` (Role: data, Lines: 203)
- **Responsibility**: DataStore Preferences manager for application settings and daily usage history.
- **Public Functions**:
  ```kotlin
  val meterPreferencesFlow: Flow<MeterPreferences>
  fun observeDailyUsage(dateKey: String): Flow<DailyUsage>
  suspend fun addUsage(dateKey: String, mobileDelta: Long, wifiDelta: Long)
  suspend fun resetUsage(dateKey: String)
  ```
- **Consumers**: `PreferencesRepositoryImpl`, `UsageRepositoryImpl`.

### `com.netpulse.app.service.NetPulseService` (Role: service, Lines: 246)
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
- **2026-10-29 (i)**: Midnight-baseline subtraction exercised live for the first time, on a disposable AVD (`midnight_probe`, since deleted) so clock manipulation could not contaminate `habit_emu`'s netstats. Sequence: app running at 23:55, 12,000,206 B fetched (log row 25,386 B, snapshot `usesBaseline=0`), clock advanced past midnight with the service alive, baseline captured, then 12,000,206 B fetched again. Result: snapshot header flipped to `usesBaseline=1`, YouTube's total reset to 0 on the capture visit (no inflated-then-collapsed jump), and it accumulated to 14,541,050 B = 12,457,300 + 2,083,750, excluding the 12,573,160 B pre-midnight sum. Bracketed post-midnight fetch matched netstats byte-exactly (2,083,750 B). Note: after a restart past `BASELINE_GRACE_MS` the app correctly drops back to `usesBaseline=0`; on the probe that showed 0 B because every netstats bucket was still stamped `st=1793167200` (bucket rotation was anchored at boot with the pre-jump clock), so the whole-bucket fallback dropped them all. That is a clock-jump artifact, not a defect: a real midnight yields a bucket straddling the boundary, which `countsTowardDay` keeps.
- **2026-10-28 (h)**: Fixed a v1.4.0 regression that silently disabled snapshot persistence. `recordTotal` dropped 0-byte entries from `sweepTotals`, so the next poll saw those packages as unswept, re-queued them, measured 0 again, and repeated forever; `sweepComplete()` never returned true and the recovery snapshot froze minutes after startup. Zero-byte results are now retained in `sweepTotals`; `queryTodayTotals()` returns the map unfiltered and `AppUsageTracker` filters zero-byte packages out of the UI rows. 39 unit tests green, lint 0 errors, live device check: snapshot header advanced `1793157389294` -> `1793157450096` -> `1793158622495`, no zero-byte rows in the live list, fresh minute log rows.
- **2026-10-28 (g)**: Restart-gap recovery. Bytes moved while the service was down were silently dropped: the first poll after a start set `lastTotals` with no previous value, so every delta was 0. The tracker now persists a per-package totals snapshot (`AppUsageSnapshot(atMs, usesBaseline, totals)`) and adopts it on the first poll when `isAdoptable` passes (same accounting mode, not in the future, within `RECOVERY_WINDOW_MS = 300_000`, not before local midnight). Two follow-on defects found on device: `lastTotals = totals` replaced the adopted baseline with the first partial 16-UID sweep (fixed with a `CARRY_MS` merge window so unswept packages keep their baseline), and snapshots were written before the sweep had covered every app, so the persisted map was incomplete (fixed by gating on `sweepComplete()`). Log and snapshot are written in one DataStore edit so a crash cannot desync them. Device-verified byte-exact: 12,000,206 B moved while the app was dead appeared as `08:04 YouTube 12,465,112 B`, matching the netstats delta of 12,465,112 B for UID 10139. 40 unit tests green (`isAdoptable` + `mergedTotals`).
- **2026-10-28 (f)**: Stale-row fix. The `if (bytes > 0L)` guards at both result-map writes meant the re-baseline poll's deliberate 0 was dropped, leaving a total computed against the baseline v1.3.2 had just discarded (stale total + decaying rate). Extracted `recordTotal(totals, key, bytes)` which stores positive values and removes the key on zero; 3 tests. 32 unit tests green, lint clean, shipped as v1.3.3.
- **2026-10-28 (e)**: Baseline reset guard. `queryUid(0L, queryEnd)` sums all retained history, and netstats trims old buckets, so `all` can fall below a stored midnight baseline; the previous `coerceAtLeast(0L)` then froze that app at 0 B until the next midnight. Extracted `sinceBaseline(all, baseline): Long?` (null on regression) with 3 tests; the caller re-baselines on the current total and reports 0 for that poll. Also moved the `countsTowardDay` KDoc off `withinBaselineWindow`. 29 unit tests green, lint clean, device-verified live rows.
- **2026-10-13**: Initialized complete NetPulse architecture (core, domain, data, service, Compose UI, unit tests, GitHub CI/Release workflows).
- **2026-10-14**: v1.0.1 fix release. Added runtime `POST_NOTIFICATIONS` request (fixes dead notification/status-bar speed on Android 13+); R8 minify + resource shrink (42 MB -> 2.1 MB); stable release keystore (`keystore.properties` + GitHub secrets, fixes per-build dynamic debug key); `TelemetryManager` (core) decouples sampling from UI; `HomeUiState` gains `hasNotificationPermission`/`canDrawOverlays`; overlay permission prompt in `SettingsViewModel`.
- **2026-10-28 (d)**: Fixed a double-wrong first sample in the baseline path. The visit that captured the baseline also returned the whole-bucket sum, which included yesterday, then the next poll dropped to `all - baseline` (near zero). The capture visit now reports 0 and the total grows exactly from there. Runtime re-verified: per-app rows render, fresh log rows, no exceptions.
- **2026-10-28 (c)**: Midnight-straddle baseline subtraction. Bucket grid verified :30-aligned, so the first bucket of the day runs 23:30-01:30 and whole-bucket counting overstated today by up to 30 min of yesterday. When the service is running across midnight it captures a per-UID cumulative baseline and reports the exact difference; otherwise it keeps the previous whole-bucket behavior. Window decision extracted as `withinBaselineWindow` with 4 tests. 26 unit tests green.
- **2026-10-28 (b)**: Extracted the day filter into `countsTowardDay(end, dayStart)` and covered it with `AppUsageDataSourceTest` (midnight boundary, one ms after, whole-day, before-midnight, straddling). Documents the known overcount: a bucket spanning midnight is kept whole because `NetworkStats.Bucket` cannot be split. No behavior change; 22 unit tests green.
- **2026-10-28**: Per-app live usage + usage log. `NetworkStatsManager` is the only source that can see other UIDs (`TrafficStats.getUidRxBytes` returns -1 for them). Per-UID queries replace whole-day `queryDetails` (silently capped at 100 rows, which froze totals). `AppUsageTracker` derives decaying rates from clamped deltas and folds them into a 300-entry minute log persisted to DataStore. Manifest gains `QUERY_ALL_PACKAGES` (sideloaded apps were invisible) and `PACKAGE_USAGE_STATS`. Status-bar glyph now always keeps its unit letter (`MAX_LABEL_LENGTH = 4`, `TEXT_SCALE_X = 1.0`). Root cause of the empty/frozen per-app list: `NetworkStatsManager` discards any bucket not fully inside the queried window, so `end=now` hid the open bucket. Window is now padded (`BUCKET_PAD_MS`) and buckets ending before local midnight are dropped. Device-verified: YouTube 1.3 MB/s then 1.5 MB/s during transfer, decaying to 42.5 KB/s; usage log persisted `01:56 YouTube 11.9 MB` and `01:59 YouTube 8.0 MB`. Chunked `queryDetails` fallback retained and rate-limited to `FALLBACK_INTERVAL_MS`, subdividing `ROW_CAP` windows.
