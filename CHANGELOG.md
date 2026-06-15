# Changelog

All notable changes to DriveReply will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **In-app update progress notification** — when the user starts a
  download, a sticky `Notification` with a progress bar appears in the
  shade. The notification updates on every progress event, switches to
  a "Verifying signature…" indeterminate state, and dismisses itself
  when the system install dialog takes over. Tapping the notification
  deep-links to MainActivity → Settings, even if the activity is
  already in the back stack. Implementation in
  `ApkUpdateInstaller.SystemUpdateNotifier`; the notifier is pluggable
  via the new `UpdateNotifier` interface so tests don't need a real
  `NotificationManager`.
- **Version-direction gate in the in-app updater** — `ApkUpdateInstaller`
  now reads the `versionCode` of the downloaded APK and refuses to
  install a build that is older (`UpdateError.Downgrade`) or equal
  (`UpdateError.AlreadyCurrent`) to the installed app. Surface these
  in Settings as typed errors instead of letting the system install
  dialog fail with a generic "App not installed" toast.
- **Unit tests** for the version gate and the new `UpdateError`
  variants: 8 new tests in `ApkUpdateInstallerTest`.

### Fixed
- **`BluetoothReceiver`** — `Intent.getParcelableExtra(name)` (deprecated
  on API 33+) now uses the typed overload on Tiramisu+.
- **`WhatsAppNotificationListener`** — `Bundle.getParcelableArray(name)`
  (deprecated on API 33+) now uses the typed overload on Tiramisu+.
  Dropped the legacy `Notification.MessagingStyle.Message.sender`
  (CharSequence) fallback in favor of `senderPerson?.name` (API 28+).
  `EXTRA_SELF_DISPLAY_NAME` is now explicitly suppressed with a
  comment explaining there is no public replacement helper on the
  platform.
- **`ApkUpdateInstaller`** — `Intent.ACTION_INSTALL_PACKAGE` is the
  only public contract for sideloading an APK; the deprecation is
  suppressed with an explanatory comment so the build is clean.
- **In-app update notification permission** — the system notification
  is now gated on an explicit `POST_NOTIFICATIONS` check on API 33+,
  so the app does not throw `SecurityException` when the user has
  denied the permission. In-app UI is unchanged and remains the
  source of truth for download progress.

### Notes
- `DebugEventLogger` already has a `MAX_ENTRIES = 500` cap and
  uses `takeLast(MAX_ENTRIES)` (S-05 from the audit was a false
  alarm; no change needed).
- The package name `com.example.drivereply` remains; migrating to a
  Play-acceptable namespace requires a coordinated release (S-06).
  Out of scope for this PR.

## [1.0.x] — 2026-06-15 and prior

See git history and `docs/PROGRESS.md` for the full per-build changelog.

