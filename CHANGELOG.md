# Changelog

All notable changes to DriveReply will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Wrong-package gate in the in-app updater** — refuses to install an
  APK whose `packageName` does not match the installed app. A signing
  certificate can be shared across multiple apps from the same
  developer, so a cert match alone is not sufficient. New
  `UpdateError.WrongPackage` variant surfaces both the expected and
  actual package names.
- **POST_NOTIFICATIONS prompt before in-app update downloads** — on
  API 33+, Settings → Updates requests the permission when the user
  taps *Download & install*, so the new system progress notification
  actually shows. The download still proceeds if the user denies (the
  in-app progress bar remains the source of truth).
- **(from #3) In-app update progress notification** — alongside the
  in-app progress bar. Tapping the notification deep-links to
  MainActivity → Settings (handled via onNewIntent + recreate).
- **(from #3) `compareVersionCodes` gate in the in-app updater** —
  refuses to install an APK whose `versionCode` is less than or equal
  to the installed one, with typed `UpdateError.Downgrade` and
  `UpdateError.AlreadyCurrent` variants.

### Changed
- **Self-message detection** in `WhatsAppNotificationListener` now
  uses `NotificationCompat.isOwnNotification(sbn)` instead of comparing
  the deprecated `Notification.EXTRA_SELF_DISPLAY_NAME` to the contact
  name. More reliable, doesn't depend on the messaging app populating
  the extra or on the user having set a display name.
- Removed obsolete `Build.VERSION.SDK_INT >= R` guards from
  `WhatsAppNotificationListener` (R is API 30, our minSdk is 29, so
  these were always true).
- Removed obsolete `Build.VERSION.SDK_INT >= P` guards around
  `longVersionCode` and `SEMANTIC_ACTION_REPLY` in
  `ApkUpdateInstaller.kt` and `WhatsAppNotificationListener.kt`
  (both fields are API 28+, our minSdk is 29).

### Fixed
- **(from #3) `BluetoothReceiver`** — `Intent.getParcelableExtra(name)`
  (deprecated on API 33+) now uses the typed overload on Tiramisu+.
- **(from #3) `WhatsAppNotificationListener`** —
  `Bundle.getParcelableArray(name)` (deprecated on API 33+) now uses
  the typed overload on Tiramisu+. Dropped the legacy
  `Notification.MessagingStyle.Message.sender` (CharSequence) fallback
  in favor of `senderPerson?.name` (API 28+).
- **(from #3) `ApkUpdateInstaller`** — `Intent.ACTION_INSTALL_PACKAGE`
  is the only public contract for sideloading an APK; the deprecation
  is suppressed with an explanatory comment so the build is clean.
- **(from #3) In-app update notification permission** — the system
  notification is now gated on an explicit `POST_NOTIFICATIONS` check
  on API 33+, so the app does not throw `SecurityException` when the
  user has denied the permission.

### Notes
- `DebugEventLogger` already has a `MAX_ENTRIES = 500` cap and
  uses `takeLast(MAX_ENTRIES)` (S-05 from the audit was a false
  alarm; no change needed).
- The package name `com.example.drivereply` remains; migrating to a
  Play-acceptable namespace requires a coordinated release (S-06).
  Out of scope for this PR.

## [1.0.x] — 2026-06-15 and prior

See git history and `docs/PROGRESS.md` for the full per-build changelog.

