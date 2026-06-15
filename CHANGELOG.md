# Changelog

All notable changes to DriveReply will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Full security & quality audit in `docs/SECURITY_AUDIT.md`.
- (Planned) Branded adaptive launcher icon (replaces Android Studio placeholder).
- (Planned) In-app APK download + signature-verified install (seamless update, no browser).
- (Planned) SHA256 manifest attached to each GitHub release.

### Changed
- (Planned) `android:allowBackup` default flipped to exclude `datastore/` and `databases/` to prevent leaking reply templates and logs to cloud backup.
- (Planned) CI now runs `lint` + `test` + `assembleDebug` as quality gates; release is gated on a configured signing key.

### Fixed
- (Planned) `BluetoothReceiver` and `ActivityTransitionReceiver` no longer call `context.startService()` from a background context (Android 12+ restriction hardening).

### Security
- (Planned) In-app update flow now refuses to install an APK whose signing certificate does not match the currently installed app.
- (Planned) Debug log buffer is now size-capped to prevent unbounded growth.

## [1.0.x] — 2026-06-15 and prior

See git history and `docs/PROGRESS.md` for the full per-build changelog.
