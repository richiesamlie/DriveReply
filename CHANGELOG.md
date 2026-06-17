# Changelog

All notable changes to DriveReply will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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



