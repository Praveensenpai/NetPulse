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

### `com.netpulse.app.core.BatteryOptimizer` (Role: core, Lines: 97)
- **Responsibility**: Samsung Device Care and system battery optimization exemption intents.
- **Public Symbols**:
  ```kotlin
  enum class BatteryExemptionResult { ALREADY_EXEMPT, REQUESTED, UNAVAILABLE }

  fun isIgnoringBatteryOptimizations(context: Context): Boolean
  fun requestIgnoreBatteryOptimizations(context: Context): BatteryExemptionResult
  fun openSamsungDeviceCare(context: Context): Boolean
  fun openAppDetailsSettings(context: Context)
  fun isSamsungDevice(): Boolean
  ```
- **Consumers**: `HomeViewModel`, `BatteryGuideRoute`.
- **Note**: `requestIgnoreBatteryOptimizations` returns `ALREADY_EXEMPT` without launching anything. The platform's `RequestIgnoreBatteryOptimizations` activity has nothing to ask once the exemption is held, so it opens and finishes immediately — which reads to the user as a dead button. `openSamsungDeviceCare` returns whether a Samsung package was launched and does not open app settings itself; the caller decides the fallback.

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

### `com.netpulse.app.data.source.LocalDataStore` (Role: data, Lines: 217)
- **Responsibility**: DataStore Preferences manager for application settings and usage history. The usage read path follows the calendar: `observeUsageHistory()` re-derives the day key on a ticker instead of resolving it once, so the windows roll over at local midnight without a restart. Writes maintain two separate records: bounded per-day buckets and monotonic `lifetime_mobile` / `lifetime_wifi` counters that pruning never touches, so all-time totals stay exact after old days are swept.
- **Public Functions**:
  ```kotlin
  val meterPreferencesFlow: Flow<MeterPreferences>
  suspend fun getMeterPreferences(): MeterPreferences
  suspend fun updateMeterPreferences(transform: (MeterPreferences) -> MeterPreferences)
  fun getTodayKey(): String
  fun observeDailyUsage(): Flow<DailyUsage>
  fun observeUsageHistory(): Flow<UsageHistory>
  suspend fun addUsage(dateKey: String = getTodayKey(), mobileDelta: Long, wifiDelta: Long)
  suspend fun resetUsage(dateKey: String = getTodayKey())
  ```
- **Consumers**: `PreferencesRepositoryImpl`, `UsageRepositoryImpl`.
- **Note**: `addUsage` increments the day bucket and both lifetime counters in one `edit`. `resetUsage` zeroes a day bucket *and* subtracts the same bytes from the lifetime counters, otherwise All Time would keep reporting usage the reset just deleted; the subtraction is delegated to `UsageAggregator.reducedLifetime` so it is unit-testable and clamped at zero.

### `com.netpulse.app.data.source.DayKeys` (Role: data, Lines: 127)
- **Responsibility**: Pure `java.time` day-bucket arithmetic with no Android types, so it is unit-testable. Owns the `yyyy-MM-dd` key format, next-midnight calculation, the day-key ticker that drives the usage rollover, and the retention rule that decides which usage buckets are stale.
- **Public Functions**:
  ```kotlin
  fun keyFor(nowMs: Long, zone: ZoneId): String
  fun nextMidnightMs(nowMs: Long, zone: ZoneId): Long
  fun dayKeyTicker(clock: () -> Long, zone: ZoneId, recheckMs: Long = RECHECK_MS): Flow<String>
  fun keysToPrune(keys: Collection<String>, todayKey: String, retentionDays: Int = RETENTION_DAYS): Set<String>
  fun parseKey(key: String): LocalDate?
  fun dateOfUsageKey(key: String): LocalDate?
  ```
- **Consumers**: `LocalDataStore`, `UsageAggregator`.
- **Note**: Uses `Instant.atZone(...).toLocalDate()` rather than `LocalDate.ofInstant`, which is API 34 and would break the API 33 floor. `keysToPrune` never returns a key it cannot parse as a usage bucket, and keeps future-dated keys so a backwards clock skew cannot delete data. `RETENTION_DAYS = 35` keeps a full trailing month plus slack; the lifetime counters are what make all-time independent of this window.

### `com.netpulse.app.data.source.UsageAggregator` (Role: data, Lines: 84)
- **Responsibility**: Pure roll-up of the retained per-day ledger into the Today / trailing-7-day / trailing-30-day / all-time windows, with no Android types so the boundary arithmetic is unit-testable.
- **Public Functions**:
  ```kotlin
  const val WEEK_DAYS = 7
  const val MONTH_DAYS = 30
  fun aggregate(days: Collection<DailyUsage>, todayKey: String, lifetime: UsageRange): UsageHistory
  fun seedLifetime(days: Collection<DailyUsage>): UsageRange
  ```
- **Consumers**: `LocalDataStore.observeUsageHistory`.
- **Note**: "This week" is the trailing seven calendar days including today, not the ISO week, which would drop days the user still considers current. Future-dated buckets are ignored so a backwards clock skew cannot inflate every window. `allTime = lifetime.atLeast(seedLifetime(days))` keeps all-time monotonic on upgrades from builds that had no lifetime counters, without writing a migration row.

### `com.netpulse.app.domain.model.UsageHistory` (Role: domain, Lines: 49)
- **Responsibility**: `UsageRange` (mobile + wifi byte pair, with `plus` and a per-transport `atLeast`) and `UsageHistory` (the four windows plus the retained per-day ledger for a sparkline).
- **Public Symbols**:
  ```kotlin
  data class UsageRange(val mobileBytes: Long = 0L, val wifiBytes: Long = 0L)
  data class UsageHistory(today, last7Days, last30Days, allTime, days: List<DailyUsage>)
  ```
- **Consumers**: `UsageAggregator`, `LocalDataStore`, `UsageSummaryCard`, `HomeUiState`.

### `com.netpulse.app.service.NetPulseService` (Role: service, Lines: 260)
- **Responsibility**: Foreground service managing low-overhead 1Hz polling loop with screen-off pause. Display state is read from `PowerManager.isInteractive` on every decision, never cached, and the pause preference is reconciled once the persisted value arrives rather than assumed from the constructor default.
- **Public Lifecycle**: `onCreate()`, `onStartCommand()`, `onDestroy()`.
- **Side Effects**: System notification updates, window overlay layout mutations, DataStore writes.
- **Note**: Every polling decision funnels through one private `applyPolling(screenOn)`; `reconcilePolling()` feeds it a live `isScreenOn` read and `onScreenStateChanged()` feeds it the broadcast value, so the two paths cannot drift. `ScreenStateReceiver` only reports future transitions, so a cached flag would stay `true` across any start that happens with the display already dark (boot, package replace, sticky restart). Symmetrically, `onStartCommand` runs before `preferencesFlow` has emitted, so it can only read the in-memory default `pauseWhenScreenOff = true`; `observeConfiguration` therefore calls `reconcilePolling()` when `lastPauseWhenScreenOff` changes, otherwise a stored `false` would be ignored for the whole session.

### `com.netpulse.app.service.PollingPolicy` (Role: service, Lines: 14)
- **Responsibility**: Pure screen-off rule, kept outside the `Service` so it is unit-testable without an Android runtime.
- **Public Functions**:
  ```kotlin
  internal fun shouldPoll(pauseWhenScreenOff: Boolean, screenOn: Boolean): Boolean
  ```
- **Consumers**: `NetPulseService` (`onStartCommand`, `onScreenStateChanged`).
- **Note**: Decides only *whether* to poll. The caller must still read the real display state, because receiver registration reports transitions, not current state.

### `com.netpulse.app.service.UsageAttribution` (Role: service, Lines: 56)
- **Responsibility**: Pure rule deciding what happens to the bytes measured since the last tick: record them, hold them, or drop them.
- **Public API**:
  ```kotlin
  internal const val MAX_HELD_USAGE_BYTES = 64L * 1024 * 1024

  internal sealed interface UsageAttribution {
      data class Record(val mobileBytes: Long, val wifiBytes: Long) : UsageAttribution
      data object Hold : UsageAttribution
      data object Drop : UsageAttribution
  }

  internal fun attributeUsage(
      type: ActiveNetworkType,
      deltaBytes: Long,
      heldBytes: Long = 0L
  ): UsageAttribution
  ```
- **Consumers**: `NetPulseService.recordConsumption`.
- **Note**: `NONE` holds bytes for the next tick because it is the handover and pre-attach state. `ETHERNET` and `OTHER` drop them: there is no bucket, and carrying them forward would misattribute to whichever transport appeared next. `OTHER` is what an always-on VPN reports, since VPN capabilities carry the VPN transport instead of the physical one. The hold ceiling stops a long outage from dumping an unbounded figure into a later day.

### `com.netpulse.app.service.ScreenStateReceiver` (Role: service, Lines: 40)
- **Responsibility**: Pauses/resumes polling loop upon display power state changes.
- **Public Functions**:
  ```kotlin
  fun register(context: Context)
  fun unregister(context: Context)
  ```
- **Consumers**: `NetPulseService`.

### `com.netpulse.app.service.OverlayLabel` (Role: service, Lines: 28)
- **Responsibility**: Pure text builder for the floating overlay pill, so the "Show Upload Speed" rule is unit-testable without an Android runtime. Renders `↓ dl  ↑ ul` when upload is enabled and `↓ dl` alone when it is not; `unitInBits` applies to both directions.
- **Public Functions**:
  ```kotlin
  fun format(downloadBytesPerSec: Long, uploadBytesPerSec: Long, unitInBits: Boolean = false, showUpload: Boolean = true): String
  ```
- **Consumers**: `FloatingOverlayManager.updateSpeed`.
- **Note**: `FloatingOverlayManager` previously formatted the string inline and always emitted both arrows, so `showUploadSpeed` governed the notification and the Home gauge but was silently ignored by the overlay.

### `com.netpulse.app.service.SpeedIconLabel` (Role: service, Lines: 62)
- **Responsibility**: Pure label compaction for the status-bar glyph. Drops the decimal and rounds (`5.8M` -> `6M`), promotes three-digit values to the next unit (`988K` -> `1M`) instead of truncating, shows `0B` when idle and `<1K` when a non-zero value rounds to zero. Capped at 3 characters.
- **Public Functions**:
  ```kotlin
  fun compact(value: String, unit: String): String
  ```
- **Consumers**: `SpeedIconGenerator`.

### `com.netpulse.app.service.SpeedNotificationManager` (Role: service, Lines: 75)
- **Responsibility**: Configures silent NotificationChannel and builds persistent status bar notification.
- **Public Functions**:
  ```kotlin
  fun buildNotification(speed: SpeedSnapshot, usage: DailyUsage, unitInBits: Boolean, showUpload: Boolean, useDynamicIcon: Boolean): Notification
  fun updateNotification(notification: Notification)
  fun createNotificationChannel(context: Context)
  ```
- **Consumers**: `NetPulseService`, `NetPulseApp`.

### `com.netpulse.app.service.SpeedIconGenerator` (Role: service, Lines: 77)
- **Responsibility**: Renders the status-bar speed glyph into a 96x96 bitmap. The platform scales a small icon by its width into a square slot, so the canvas is filled edge to edge and the font is fitted per label against both width and height. No `textScaleX` is ever applied: anisotropic squeezing is what made the digits look stretched; a wider label renders slightly smaller instead. Label text comes from `SpeedIconLabel`.
- **Public Functions**:
  ```kotlin
  fun generateSpeedBitmap(speed: FormattedSpeed): Bitmap
  ```
- **Consumers**: `SpeedNotificationManager`.

### `com.netpulse.app.service.NetPulseTileService` (Role: service, Lines: 50)
- **Responsibility**: Samsung Quick Settings panel tile toggle.
- **Public Lifecycle**: `onStartListening()`, `onClick()`.

### `com.netpulse.app.ui.home.HomeRoute / HomeScreen` (Role: ui, Lines: 220)
- **Responsibility**: Two-layer presentation displaying speed gauge, usage history, and service controls. `HomeRoute` wires the view model, re-reads permissions in `LifecycleResumeEffect`, and collects with `collectAsStateWithLifecycle()`.
- **State Contract**: `HomeUiState` (immutable, includes `usageHistory`, `selectedWindow`, `showUploadSpeed`), `HomeEvent` (sealed interface, includes `SelectUsageWindow`).

### `com.netpulse.app.ui.home.HomeUiState` (Role: ui, Lines: 26)
- **Responsibility**: Immutable home-screen state plus the `UsageWindow` enum (`TODAY`, `WEEK`, `MONTH`, `ALL_TIME`) that labels the usage selector.
- **Public Symbols**:
  ```kotlin
  enum class UsageWindow(val label: String)
  data class HomeUiState(..., val usageHistory: UsageHistory, val selectedWindow: UsageWindow)
  ```

### `com.netpulse.app.ui.components.UsageSummaryCard` (Role: ui, Lines: 156)
- **Responsibility**: Consumption card with a `FilterChip` row selecting the window and Mobile / Wi-Fi / total figures for the selected range. Window selection lives in the view model, so the card stays stateless.
- **Public Functions**:
  ```kotlin
  fun UsageSummaryCard(history: UsageHistory, selectedWindow: UsageWindow, onSelectWindow: (UsageWindow) -> Unit, modifier: Modifier)
  ```
- **Consumers**: `HomeScreen`.
- **Note**: `FilterChip` exposes selection as `checked`, not `selected` — verification that a tap landed has to read `checked="true"` from the semantics node, not the text node.

### `com.netpulse.app.ui.settings.SettingsRoute / SettingsScreen` (Role: ui, Lines: 90)
- **Responsibility**: Two-layer presentation for notification, overlay, units, and battery behavior toggles.
- **State Contract**: `SettingsUiState` (immutable), `SettingsEvent` (sealed interface).

### `com.netpulse.app.ui.guide.BatteryGuideRoute / BatteryGuideScreen` (Role: ui, Lines: 214)
- **Responsibility**: Device-appropriate battery optimisation instructions and intent triggers. `BatteryGuideRoute` holds the exemption state, re-reads it on `ON_RESUME` via `LifecycleEventObserver`, and resolves the Samsung flag once.
- **State Contract**: `isBatteryExempt` (drives the disabled/granted button), `isSamsungDevice` (selects the guide copy and the second button).

### `com.netpulse.app.ui.guide.BatteryGuideTexts` (Role: ui, Lines: 75)
- **Responsibility**: Pure, Compose-free copy selection so the wording is unit-testable. Returns the Samsung One UI guide or a generic Android guide depending on the manufacturer.
- **Public Symbols**:
  ```kotlin
  data class BatteryGuideStep(val title: String, val description: String)
  data class BatteryGuide)
  fun forDevice(isSamsungDevice: Boolean): BatteryGuideContent
  ```
- **Consumers**: `BatteryGuideScreen`.
- **Note**: A test (`BatteryGuideTextsTest`) fails the build if "Samsung", "One UI", "Device Care", or "Deep Sleeping" appears in the generic copy. The generic guide previously did not exist: every device was told to configure Device Care, which most do not have.

## 4. Execution Lifecycle Trace
1. **Startup**: `NetPulseApp` initializes singletons, ensures notification channel, and starts `NetPulseService` if enabled.
2. **Foreground Telemetry**: `NetPulseService` runs 1s polling interval on `Dispatchers.Default` reading `TrafficDataSource`.
3. **Screen-Off Suspension**: `ScreenStateReceiver` catches `ACTION_SCREEN_OFF`, immediately cancelling polling coroutine to eliminate CPU wakeups.
4. **Resumption**: `ACTION_SCREEN_ON` resumes polling coroutine.
5. **Tile Interaction**: `NetPulseTileService` toggles service state from the Android Quick Settings tile.

## 5. Verification Commands
```bash
# Unit Tests
./gradlew testDebugUnitTest --no-daemon

# Android Lint (must be 0 errors; NewApi gates minSdk = 33)
./gradlew lintDebug --no-daemon

# Assemble Debug APK
./gradlew assembleDebug --no-daemon

# Assemble Signed Release APK
./gradlew assembleRelease --no-daemon
```

## 6. Recent Iteration Changes
- **2026-11-02 (m)**: The floating mini overlay ignored the *Show Upload Speed* setting. `FloatingOverlayManager.updateSpeed()` formatted the pill inline and always emitted both arrows, so the preference governed the notification and the Home gauge but never the overlay. Fix: the rule moved to a new pure `OverlayLabel.format(download, upload, unitInBits, showUpload)` (28 lines), `updateSpeed()` now accepts a showUpload flag, `NetPulseService` passes `currentPreferences.showUploadSpeed`, and the manager's unused `SpeedFormatter` import was removed. 4 new `OverlayLabelTest` cases fail the build if upload is rendered while the setting is off. Device proof on `emulator-5554`, fresh process per run: overlay windows are hidden from `uiautomator`, so the pill background `Color.argb(220, 20, 24, 33)` was found by decoding the screencap PNG in pure Python and taking that colour run's extent. Pre-fix, show_upload_speed = false: 367 px wide (x 102..468), the defect. Fixed, same false: 193 px (x 102..294), exactly the upload half removed. Fixed, true: 351 px (x 102..452), the positive control. 70 tests, 0 lint errors (debug and release, 18 warnings each). Shipped as v1.6.3.
- **2026-11-02 (n)**: Traffic that moved while monitoring was paused never reached the usage ledger. `NetPulseService.startMonitoring()` called `trafficDataSource.reset()` on every resume, which sets `lastRxBytes = -1`. The next `sample()` then re-baselined against the current counters and reported zero, so the entire screen-off interval was dropped. Measured on `emulator-5554` against the shipped v1.6.3 release build with `pause_when_screen_off = true`: `wlan0` moved +20,750,690 B while `mWakefulness=Asleep`, and the ledger grew only +96 B after wake. Fix: new `TrafficDataSource.catchUp()` folds the gap into the pending delta, advances the baseline to the current counters, and refreshes the timestamp so the next tick's rate stays normal (no 20 MB/s spike from the catch-up). `startMonitoring()` now calls `catchUp()`; `stopMonitoring()` keeps the hard `reset()` so a window where the user disabled monitoring is never attributed. 4 new `TrafficDataSourceTest` cases (gap preserved, no speed spike, counter reset after reboot, pre-first-sample no-op). Re-measured on the R8-shrunk v1.6.4 build, same procedure: `wlan0` +20,750,486 B during the pause, ledger +20,765,766 B after wake. 74 unit tests, 0 failures; 0 lint errors on debug and release (18 warnings each). Shipped as v1.6.4. Trade-off: bytes from a pause spanning local midnight are attributed to the resume day, since the pause yields one delta rather than per-bucket ones.
- **2026-11-02 (l)**: With *Pause When Screen Off* turned **off**, the app still refused to sample whenever it was started while the display was already dark. `onStartCommand` decides whether to poll by calling `shouldPoll(currentPreferences.pauseWhenScreenOff, isScreenOn)`, but `currentPreferences` is still the in-memory constructor default at that point — `pauseWhenScreenOff = true`. The persisted preferences only arrive later on `preferencesFlow`, and `observeConfiguration` assigned them without ever re-evaluating the polling decision, so the stale `true` survived for the entire session unless the user happened to cycle the display. Reachable on every boot with *Start on Boot*, every `MY_PACKAGE_REPLACED`, and every `START_STICKY` restart while the screen is off. This is the exact mirror of `2026-11-01 (g)`: that fix removed a cached *display* state from the same decision, this one removes a cached *preference* state. Fix: a new private `applyPolling(screenOn)` now holds the only copy of the decision, `reconcilePolling()` calls it with a live `isScreenOn` read, `onScreenStateChanged()` calls it with the broadcast value, and `observeConfiguration()` calls it whenever `lastPauseWhenScreenOff` changes, so the stored preference overrides the constructor default as soon as it is known and the two entry points cannot drift. Device proof on `emulator-5554` with `mWakefulness=Asleep` throughout and 20,000,206 B of real traffic pulled each run: pre-fix build with `pause_when_screen_off = false` never created the usage datastore at all (0 B recorded, the defect); fixed build with the same `false` recorded 20,664,655 B; fixed build with `true` again created no datastore, proving the reconcile honours the preference rather than always polling. The first negative-control attempt was contaminated by my own harness — the datastore was deleted before the previous process had fully exited, so the dying process rewrote its in-memory map (20,664,655 -> 20,664,827, a 172 B move rather than a doubling) — and was re-run with a verified-empty baseline, which gave the clean 0. 66 tests, 0 lint errors (debug and release, 18 warnings). Shipped as v1.6.2.
- **2026-11-02 (k)**: "Reset Today's Data Usage" left All Time untouched. The button zeroed `usage_<today>_mobile` / `_wifi` but never touched `lifetime_mobile` / `lifetime_wifi`, which are the source of the All Time window, so after a reset the same screen reported `Today 0 B` beside an All Time figure still containing the discarded bytes, and the next `addUsage` kept adding on top of the stale counter. Fix: `resetUsage()` reads the day bucket, zeroes it, and subtracts the same pair from the lifetime counters inside one `edit`; the arithmetic is a new pure `UsageAggregator.reducedLifetime(lifetime, removed)` clamped at zero, because a bucket written by a build predating the counters can exceed the counter value and a negative counter would corrupt every later read. 3 new tests (66 total) cover normal subtraction, clamping, and a zero-byte reset. Device proof on `emulator-5554` with a seeded ledger of `usage_2026-11-02_mobile = 1,000,000`, `_wifi = 2,000,000`, `lifetime_mobile = 50,000,000`, `lifetime_wifi = 80,000,000`: All Time read `47.7 MB / 76.3 MB` before, the reset was performed through Settings, and afterwards Today read `0 B` while All Time read `46.7 MB / 74.4 MB` — exactly 1,000,000 / 2,000,000 B removed. The stored protobuf confirms it independently: `usage_*_mobile = 0`, `usage_*_wifi = 0`, `lifetime_mobile = 49,000,000`, `lifetime_wifi = 78,000,000`. 0 lint errors (debug and release, 18 warnings). Shipped as v1.6.1.
- **2026-11-02 (j)**: Usage history did not exist. The app stored only today — `DayKeys.RETENTION_DAYS = 2` and no code path ever read a past day, so "this week / this month / all time" were not a missing label but missing data. Fix: retention raised to 35 days so a trailing month has real buckets behind it; new pure `UsageAggregator` rolls the retained ledger into Today / trailing-7-day / trailing-30-day windows; all-time comes from monotonic `lifetime_mobile` / `lifetime_wifi` counters written alongside every day bucket, because pruning deletes day buckets and the preferences file is rewritten in full on every write under the 1 Hz loop, so unbounded day retention is not viable. On upgrade from a build with no counters, `allTime = lifetime.atLeast(seedLifetime(days))` keeps the figure monotonic without a migration row. Device proof on `emulator-5554` with a seeded ledger holding days at 0, 3, 10 and 61 days of age plus counters above the retained sum: Today `977 KB / 1.9 MB`, Week `5.7 MB / 1.9 MB`, Month `15.3 MB / 21.0 MB`, All Time `190.7 MB / 381.5 MB` — all four match the seed exactly, and the 61-day-old bucket is excluded from Month yet counted in All Time, which is only reachable via the counters. Chip selection confirmed by `checked="true"` moving between nodes. The first seeding attempt was wiped because the still-installed 2-day build pruned it before the new APK was installed; re-seeding after the upgrade produced the correct numbers — a real hazard of shipping a retention change, recorded here rather than silently retried. Also fixed in the same change: the battery guide described Samsung One UI on every device; copy moved to pure `BatteryGuideTexts`, and the non-Samsung emulator now renders "Battery Optimization" with generic steps and 0 occurrences of Samsung / One UI / Device Care / Deep Sleeping. 13 new tests (63 total), 0 lint errors (debug and release, 18 warnings). Shipped as v1.6.0.
- **2026-11-01 (i)**: The Battery tab had two buttons that appeared to do nothing. (1) "Open Battery Exemption Settings" launched `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, but when the app already held the exemption the platform activity had nothing to ask, so it started and finished instantly (`result code=0`, `topResumedActivity` still `MainActivity`) — a visible flash with no effect. A/B reproduced on the emulator: exempt → nothing opened; exemption removed with `dumpsys deviceidle whitelist -<pkg>` → the "Let app always run in background?" dialog appeared. (2) "Open Samsung Device Care" was rendered on every device; on non-Samsung hardware neither `com.samsung.android.lool` nor `com.samsung.android.sm` exists, so it silently fell through to generic App Info while still claiming to be Device Care, and `isSamsungDevice()` existed but was never called. Fix: `requestIgnoreBatteryOptimizations` returns `BatteryExemptionResult` and short-circuits on `ALREADY_EXEMPT`; the button renders disabled as "Battery Exemption Granted" with a check icon; `BatteryGuideRoute` re-reads the exemption on `ON_RESUME` via `LifecycleEventObserver`; the second button is labelled "Open Samsung Device Care" only when `isSamsungDevice()` is true and "Open App Info" otherwise. Device proof: not exempt → "Open Battery Exemption Settings" → dialog → Allow → `dumpsys deviceidle whitelist` shows `user,com.netpulse.app.debug,10192` → on resume the button reads "Battery Exemption Granted"; tapping it afterwards produced `0` settings intents and left `MainActivity` resumed. 50 tests, 0 lint errors (debug and release). Shipped as v1.5.1.
- **2026-11-01 (h)**: Usage accounting silently discarded bytes whenever the active transport was neither Wi-Fi nor mobile. `recordConsumption()` called `consumeDelta()`, which clears the pending counters, *before* choosing a bucket; the empty `else -> Unit` then threw those bytes away. Reachable in normal use: `NONE` is reported during a Wi-Fi/cellular handover and before the first network attaches after boot, and `OTHER` is what an always-on VPN reports because VPN capabilities carry the VPN transport rather than the physical one. Fix: `peekDelta()` reads without clearing, `attributeUsage(...)` decides first, and `NetPulseService` only calls `consumeDelta()` once the decision is `Record` or `Drop`. `NONE` now holds the bytes for the next tick, capped at `MAX_HELD_USAGE_BYTES` (64 MB). 8 new tests (50 total), 0 lint errors. Device proof: 8,000,205 B pulled inside the emulator grew the ledger 8,281,951 B; a 40 MB transfer interrupted by disabling Wi-Fi recorded 11,920,639 B against 11,938,537 B of interface traffic with both buckets written. The `NONE` hold path was then proven directly: a transfer cut mid-flight with both radios disabled ran to completion (8,505,189 B) with `Active default network: none` for 8 consecutive seconds, and after restore the ledger delta (8,941,440 B) matched the interface delta (8,855,284 B) with no shortfall. Note that a socket cannot be *opened* while `activeNetwork` is null, so the branch is only reachable by a connection established before the transport disappears — which is exactly the handover case. Shipped as v1.5.0.
- **2026-11-01 (g)**: Screen-off pause was bypassed by any start that happened while the display was already dark. `NetPulseService` held `isScreenOn = true` as a starting assumption and only ever wrote it from `ScreenStateReceiver` callbacks. Receiver registration delivers future transitions only, so `onStartCommand`'s `pauseWhenScreenOff && !isScreenOn` test read `true` on boot with *Start on boot*, on `MY_PACKAGE_REPLACED`, and on a `START_STICKY` restart, then polled indefinitely with the screen off until the user cycled the display. Fix: `isScreenOn` is now a computed property reading `PowerManager.isInteractive`, and the rule moved to a pure `shouldPoll(pauseWhenScreenOff, screenOn)` in `PollingPolicy.kt` with 2 unit tests (42 total). Reproduced before the fix on `emulator-5554`: `mWakefulness=Asleep`, service started, ledger grew 23,391,976 -> 23,396,972 B (+4,996 B) over 25 s. After the fix, same start: ledger byte-identical across 30 s with `mWakefulness=Asleep`, then grew again on `KEYCODE_WAKEUP`. Lint 0 errors. Shipped as v1.4.9 (tag peels `16dc120`), release assets checksum-verified.
- **2026-11-01 (f)**: CI and Release ran no lint step, so a `NewApi` violation would pass every gate and ship. This was not hypothetical: the midnight fix in `2026-11-01 (d)` first used `LocalDate.ofInstant` (API 34) against `minSdk = 33`, failed lint locally, and the corrected build was released — but only because the mistake was caught by hand before pushing. `ci.yml` ran `testDebugUnitTest` and `assembleDebug`; `release.yml` ran `testDebugUnitTest` and `assembleRelease`. Neither ever invoked `lint*`. Fix: `./gradlew lintDebug --no-daemon` added to CI after unit tests, `./gradlew lintRelease --no-daemon` added to Release after unit tests. Proof the gate blocks rather than warns: re-injecting `LocalDate.ofInstant` into `DayKeys.keyFor` made `lintDebug` exit `BUILD FAILED` with `Error: Call requires API level 34 ... [NewApi]`, and reverting restored `BUILD SUCCESSFUL`. Current tree: 0 lint errors, 18 warnings (debug and release variants).
- **2026-11-01 (e)**: Usage ledger grew without bound. A pair of DataStore keys (`usage_YYYY-MM-DD_mobile` / `_wifi`) was written per day and nothing ever removed them: `resetUsage()` only zeroed today's pair, and after the per-app usage screens were deleted in `2026-11-01 (b)` no code path read a past day at all. DataStore rewrites and fsyncs the entire preferences protobuf on every `edit`, and `addUsage()` runs on the 1 Hz service loop, so every dead day was paid for continuously rather than once. Fix: `DayKeys.keysToPrune(keys, todayKey, retentionDays)` is a pure function returning stale usage keys, deliberately conservative - it ignores names it cannot parse and keeps future-dated keys so a backwards clock skew cannot delete data. `LocalDataStore.pruneOldUsage()` applies it after the write, at most once per day (guarded by `lastPrunedDayKey`). `RETENTION_DAYS = 2` keeps today and yesterday, the latter purely to cover a rollover straddling a restart. 6 new tests (40 total, 14 in `DayKeysTest`), lint 0 errors. Device proof on `emulator-5554`: seeded `usage_2026-10-25_*`, `2026-10-20_*`, `2026-10-18_*` by back-dating the clock and generating traffic, then jumped to 2026-11-01 and wrote bytes. All three stale pairs were swept; the file went to 63 bytes holding only `usage_2026-11-01_wifi = 21,907,483 B`, matching the 20.9 MB on screen byte for byte.
- **2026-11-01 (d)**: Daily usage counter froze at midnight until an app restart. `observeDailyUsage(dateKey: String = getTodayKey())` evaluated its default argument once, when the Flow was subscribed, so the key was baked into the `map` closure forever; `addUsage()` called `getTodayKey()` fresh on every write. Post-midnight writes therefore landed on `usage_2026-11-02_*` while the UI, notification, and overlay stayed subscribed to `usage_2026-11-01_*`. Proof from the stored protobuf after crossing midnight with the process alive: `11-01_wifi = 168,826,534 B` (160.9 MB, on screen) alongside `11-02_wifi = 21,835,663 B` (20.8 MB, invisible). Fix: extracted `DayKeys` (pure `java.time`, 73 lines) providing `keyFor`, `nextMidnightMs`, and a `dayKeyTicker` that re-reads the clock every `RECHECK_MS = 30_000` and `distinctUntilChanged()`; `observeDailyUsage()` now `flatMapLatest`s over that ticker. `LocalDataStore` shrank 203 -> 122 lines. 8 new tests (42 total). Device proof on `emulator-5554` after the fix, no restart: card moved 160.9 MB -> 63.3 MB, matching stored `11-02_wifi = 66,333,995 B` byte for byte. Lint initially failed with `NewApi` on `LocalDate.ofInstant` (API 34); switched to `Instant.atZone(...).toLocalDate()` and lint returned to 0 errors.
- **2026-11-01 (c)**: Glyph finalised. The two-run stacked design shipped in v1.4.5 measured 15x37 px on device, not the 39x28 px the notes claimed: stacking the unit under the number overflows the 26 px status-bar row, so the ink read as broken. Replaced with a single centred run sized per label against both canvas dimensions, so no `textScaleX` is ever applied and no label is distorted. Labels cap at three characters, the decimal is dropped (`5.8M` -> `5M`), and three-digit values promote to the next unit (`988K` -> `1M`) instead of truncating. Device measurements on `emulator-5554`: glyph ink 24-28 px tall vs a 27 px clock across captures (24 px for the two-character release-build frame, 27-28 px in debug builds), with natural glyph aspect at every length. A 192x96 wide-canvas probe proved the platform scales small icons by width into a square slot, which is why the canvas must stay square and be filled edge to edge.
- **2026-11-01 (b)**: Removed Live App Usage and Usage Log. They worked (seeded rows, byte-exact log) but a list of `0 B/s` rows reads as broken when the phone is idle, and the per-UID sweep needed ~50 s to cover every UID. Deleted `AppUsageTracker`, `AppUsageDataSource`, `AppUsageCard`, `UsageLogCard`, `AppUsage`, `UsageLogEntry`, both test files, the DataStore log/snapshot keys and codecs, `PACKAGE_USAGE_STATS`, and the `RequestUsageAccess` event. Restart-gap recovery and the midnight baseline went with them. Glyph rewritten at the same time: `SpeedIconGenerator` no longer applies `textScaleX`, which squashed digits horizontally while height stayed fixed — the stretch. It now draws the number and a smaller unit as two runs at natural width, sizing the font from measured digit ink (`getTextBounds`) rather than `fontMetrics`, whose ascender/descender padding had left the number at half the canvas height. Measured on `emulator-5554`: ink 39x28 px, matching the clock cap height; 17 unit tests green, lint 0 errors.
- **2026-11-01**: Live App Usage card showed nothing for the first minute after a restart. Not a stuck sweep: `NetworkStatsManager` publishes per-UID buckets lazily, so the first polls return package keys with 0 bytes and the `total > 0` filter hides them, leaving "Waiting for the first sample…". Fix: `NetPulseService.startMonitoring()` seeds the tracker from today's persisted log before the poll loop; `AppUsageTracker` holds each seed for `SEED_CARRY_MS = 180_000` and drops it only when the sweep reports a **positive** total (a presence test would drop every seed on the first 0-byte sweep, which the first implementation did and the device caught). Extracted `seedTotals(log, dayStartMs)` and `seededRows(seeded, measured)` as pure companion functions so the rule is unit-testable. 10 unit tests green, lint 0 errors, device proof: 5 s after restart the card listed 8 rows at `0 B/s` matching the `12:00` log entries, where the previous build showed the waiting placeholder.
- **2026-10-28 (k)**: `MIN_TEXT_SCALE_X` lowered 0.6 -> 0.5 to close the last of the glyph height gap. At 0.6 the font was still width-bound by the squeeze floor, capping ink at 24 px against a 28 px clock. At 0.5 the size term `byWidth / MIN_TEXT_SCALE_X` rises to ~94 units, so the worst case only needs the floor itself. Measured on `emulator-5554`, eight frames mid-transfer, same crop and 35% threshold: ink 38-39 x 28 px, constant height, level with the clock's 28 px cap height and double the original 14 px. The four-character `2.8M` was checked at 7x: decimal point survives as a distinct sliver, `2` counter and `M` apex stay open, no clipping. 0.5 is the floor; 0.37 (true height-bound) would crush the decimal.
- **2026-10-28 (j)**: Status-bar glyph was width-bound. `fixedTextSize` sized the font so the worst case `000B` fit `MAX_WIDTH`, which left the ink filling only ~27-41% of the 96 px canvas; the platform scales that padded square into the ~37 px slot, so the number rendered at half the clock's cap height. `fittedTextSize` now takes `min(byHeight, byWidth / MIN_TEXT_SCALE_X)`, sizing from `MAX_HEIGHT` first, and `horizontalScale` squeezes each label toward the 0.6 floor. Measured on `emulator-5554` at the same crop and 35% threshold: `0B` went 19x14 -> 32x24 px, `3.3M` renders 39x24 px, i.e. height is constant at 24 px across label lengths and now 86% of the clock's 28 px instead of 50%. Tests and lint green.
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
