# Changelog

All notable changes to DriveReply will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Retry with bounded exponential backoff** in the in-app updater:
  up to 3 attempts (1s, 2s, 4s) on transient I/O and HTTP 5xx / 408 /
  429. Permanent failures (4xx, signature mismatch, wrong package,
  version gate) still surface immediately. Total worst-case wait
  before giving up: ~7 seconds. Backoff is cancellable so the
  user still gets a snappy Cancel response.
- **Stale-file cleanup** in `cache/updates/` at the start of every
  download. Removes any non-canonical file (interrupted previous
  attempts, partial bytes from a killed process) before writing
  the new APK. Public `cleanupStaleDownloads()` so tests can
  drive the path without the full flow.
- **Wrong-package gate** in the in-app updater (from #4).
- **POST_NOTIFICATIONS prompt** before in-app update downloads
  (from #4).
- **In-app update progress notification** (from #3).

### Changed
- **`DebugEventLogger` is now backed by an `ArrayDeque<String>`**
  instead of an immutable `List<String> + takeLast(MAX_ENTRIES)`.
  Each `log()` call is now O(1) instead of O(n), eliminating the
  ~2 KB/s of GC pressure the old code generated under load.
  7 new unit tests in `DebugEventLoggerTest` exercise the cap,
  order, throwable, and concurrent burst scenarios.
- **Self-message detection** in `WhatsAppNotificationListener` (from #4).
- **Cosmetic deprecations** (no functional impact, but quiet build):
  - `Icons.Default.Chat` / `Icons.Default.ArrowBack` →
    `Icons.AutoMirrored.Filled.*` (6 sites) — fixes RTL support.
  - `TabRow` → `PrimaryTabRow` (ActivityScreen).
  - `LocalClipboardManager` → `LocalClipboardManager` with
    `@Suppress("DEPRECATION")` (SettingsScreen:99) — the new
    `LocalClipboard` API is not stable across all compose-1.7+
    BOMs in our toolchain, so we keep the old API and suppress.
  - `String.format` without explicit `Locale` (3 sites) — now
    pass `Locale.US` for stable ASCII output across locales.
- Removed obsolete `Build.VERSION.SDK_INT >= R` and `>= P` guards
  in `WhatsAppNotificationListener` and `ApkUpdateInstaller`
  (from #4).
- `testOptions { unitTests { isReturnDefaultValues = true } }` in
  `app/build.gradle.kts` so the new DebugEventLogger unit tests can
  call `android.util.Log.d/w` without Robolectric (the JVM unit-test
  default is to throw on unmocked Android APIs).

### Fixed
- **(from #4) `BluetoothReceiver` typed `getParcelableExtra`.**
- **(from #4) `WhatsAppNotificationListener` typed `getParcelableArray`.**
- **(from #4) `ApkUpdateInstaller` longVersionCode + SEMANTIC_ACTION_REPLY obsolete guards.**

## [1.0.x] — 2026-06-15 and prior

See git history and `docs/PROGRESS.md` for the full per-build changelog.


