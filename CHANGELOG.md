# Changelog

All notable changes to DriveReply will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.49] — 2026-11-25

### Changed
- Upgraded Gradle wrapper to `9.9.2` (with `100755` POSIX permissions and LF line endings on `gradlew`), Android Gradle Plugin to `9.5.2`, Kotlin to `2.4.21`, KSP to `2.3.14`, and AndroidX/Compose dependencies in `gradle/libs.versions.toml` to latest stable releases.
- Raised `compileSdk` to `37.2` and `targetSdk` to `37`.
- Upgraded GitHub Actions workflows (`ci.yml` and `release.yml`) to Node 24–compatible action major versions (`checkout@v7`, `setup-java@v6`, `setup-gradle@v6`, `dependency-submission@v6`, `upload-artifact@v7`, `action-gh-release@v3`) and fixed the invalid `build-scan-publish-action-token` input.

### Fixed
- Wired `R.string.updates_install_signature_mismatch` in `SettingsViewModel` and `R.string.updates_install_no_activity` in `ApkUpdateInstaller`, and removed unused `updates_notification_text` from `strings.xml`.
- Added rounded-square corner masking in `tools/generate_launcher_icons.py` and regenerated legacy `mipmap-*/ic_launcher.png` icons to resolve `IconLauncherShape` warnings.
- Migrated `MainScreenTest` to `androidx.compose.ui.test.junit4.v2.createAndroidComposeRule` to clear Kotlin compiler deprecation warnings.

## [2.0.48] — 2026-11-25

### Added
- Multi-rule template editor in `EditTemplateScreen` with Material 3 `TimePicker` windows, day chips, and per-contact overrides (`TemplateRuleDao.replaceRulesForTemplate`).
- `Activity Recognition Trigger` toggle in **Settings → Automation**, wired end-to-end with onboarding detection mode (`ACTIVITY` / `BLUETOOTH` / `MANUAL`).
- Y-axis tick labels on the Analytics 7-day spline chart.
- End-to-end JVM unit tests for `ApkUpdateInstaller.downloadAndVerify` and `DriveReplyService` multi-trigger / cooldown state.

### Changed
- Kept `StatusCard` and its toggle switch stationary while pulsing only the inner status icon during active driving.
- Merged `mipmap-anydpi-v26` into `mipmap-anydpi` (cleaning obsolete SDK resource qualifiers for `minSdk = 29`).

## [2.0.47] — 2026-11-11

### Security & Reliability
- Upgraded Room DB to v4 with indexed tables, synchronous prepopulation, and automatic log retention pruning.
- Hardened `ApkUpdateInstaller` (`.apk.part` staging, `sanitizeTag`, HTTPS GitHub host allowlist, `MAX_APK_BYTES` cap, key-rotation/multi-signer checks) and `DebugEventLogger` (opt-in by default, PII redaction).
- Added Android 14+/15+ `specialUse|location` foreground service declarations, R8 `NavKey`/`kotlinx.serialization` ProGuard rules, and signed-only CI release publication.

## [2.0.0] — 2026-06-17

### ⚠️ BREAKING CHANGE: package migration `com.example.drivereply` → `com.drivereply.app`

The v2.0.0 release moves the app out of the Play-blocking
`com.example.*` reserved namespace into a real applicationId.

**This is a hard break for existing v1.x users:**
- The 1.x APK and 2.0 APK have **different applicationIds** and
  therefore **different package identities**, even though both
  are signed by the same `Richie Samlie` certificate.
- A user who installs v2.0 over a v1.x install will get
  **"App not installed"** from the package installer, because
  the OS treats the two as separate apps.
- **Required action for v1.x users:** uninstall the old build
  first (Settings → Apps → DriveReply → Uninstall), then install
  v2.0 as a fresh app. Saved settings, reply history, and
  template rules live in the app's private data dir and do **not**
  transfer across this migration (the new app has a different
  internal storage path).

**Why now:** S-06 from the security audit blocked Play
distribution. v2.0 is the unblocking release. The in-app
updater cannot bridge this — a `com.example.drivereply` build
will never see a `com.drivereply.app` update offer, and vice
versa, because the signature-match check in `ApkUpdateInstaller`
includes the package name.

**Migration is one-time only.** Future releases (2.0.x, 2.1.x,
3.x) will use the same `com.drivereply.app` applicationId and
the in-app updater will work normally.

## [1.0.x] — 2026-06-15 and prior

See git history and `docs/PROGRESS.md` for the full per-build changelog.



