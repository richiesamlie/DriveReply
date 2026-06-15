# Changelog

All notable changes to DriveReply will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Branded adaptive launcher icon** — replaces the default Android Studio
  green-robot-on-green-grid placeholder. Electric-cyan chat bubble
  containing a white steering wheel, on a deep-navy radial gradient.
  Vector drawables for the adaptive icon (`drawable/ic_launcher_*`) plus
  legacy PNGs at all 5 densities generated from
  `tools/generate_launcher_icons.py`. Themed-icon support via
  `<monochrome>` for Android 13+.
- **Seamless in-app update flow** — `util/ApkUpdateInstaller.kt` streams
  the release APK into `cacheDir/updates/`, verifies the signing
  certificate matches the installed app, and launches the system
  package installer via a `FileProvider` URI. The user sees a
  linear progress bar in Settings, then a single native install
  confirmation dialog. No more leaving the app to a browser.
- **CI quality gates** — new `.github/workflows/ci.yml` (PR + develop
  branch) runs `lintDebug + testDebugUnitTest + assembleDebug` and
  uploads the debug APK as an artifact. `release.yml` is split into a
  `quality-gate` job and a `build-and-release` job.
- **Release integrity** — each release now ships a `SHA256SUMS.txt`
  manifest alongside the APKs, plus the Gradle dependency graph
  (SBOM) is published to the GitHub Dependency Graph.
- **Signing-key enforcement on main** — a `main`-branch push without
  `SIGNING_KEYSTORE` configured fails the release job with a clear
  error rather than silently shipping an unsigned APK.

### Changed
- **Backup scope** — `res/xml/backup_rules.xml` and
  `res/xml/data_extraction_rules.xml` now explicitly exclude the Room
  database (templates, rules, reply log) and DataStore preferences
  from cloud backup. Device-to-device transfer still keeps the user's
  data so they don't lose it on a phone migration.
- **Foreground-service notification updates** — `BluetoothReceiver`
  and `ActivityTransitionReceiver` no longer call
  `context.startService()` from a background context (a latent
  Android 12+ restriction). They update a `StateFlow<String>` on
  `DriveReplyService` instead; the service collects the flow and
  republishes to the OS notification.
- **`GitHubReleaseChecker.shouldSuppressLegacyFalsePositive` is now
  `internal`** so the unit test can exercise it directly.

### Fixed
- `BluetoothReceiver` and `ActivityTransitionReceiver` no longer
  build an `ACTION_UPDATE_NOTIFICATION` Intent just to change the
  foreground-service notification text. Replaced with
  `DriveReplyService.setNotificationText(...)`.

### Security
- **In-app update refuses to install an APK whose signing
  certificate does not match the currently installed app.** A
  mismatched APK is deleted from cache and a typed error is
  surfaced in Settings instead.
- Room DB, DataStore preferences, and downloaded APKs are excluded
  from cloud backup (S-01).

### Notes
- `DebugEventLogger` already has a `MAX_ENTRIES = 500` cap and
  uses `takeLast(MAX_ENTRIES)` (S-05 from the audit was a false
  alarm; no change needed).
- The package name `com.example.drivereply` remains; migrating to a
  Play-acceptable namespace requires a coordinated release (S-06).
  Out of scope for this PR.

## [1.0.x] — 2026-06-15 and prior

See git history and `docs/PROGRESS.md` for the full per-build changelog.

