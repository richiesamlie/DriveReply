# DriveReply — Security & Quality Audit

**Date:** 2026-06-15
**Scope:** Full repository (Android Kotlin/Jetpack Compose app, ~25 source files, 1 release workflow)
**Auditor:** Hermes Agent
**Branch baseline:** `main` @ `c5a36f8`

---

## 1. Executive Summary

| Category | Critical | High | Medium | Low |
|----------|---------:|-----:|-------:|----:|
| Security | 0 | 2 | 4 | 3 |
| UX/Branding | 1 | 1 | 1 | 0 |
| Build/CI | 0 | 2 | 3 | 1 |
| Code Quality | 0 | 1 | 3 | 2 |

The app is **functionally sound** (auto-reply engine, dedupe, listener recovery are well-designed) but ships with the **default Android Studio launcher icon** and a **non-seamless update flow** that pushes the user to a browser. There are no critical security vulnerabilities, but several hardening opportunities and two real release blockers (icon + update UX).

---

## 2. Findings

### 2.1 Branding / UX (the user's primary complaints)

| ID | Sev | Issue | File | Fix |
|----|----:|-------|------|-----|
| **B-01** | **CRITICAL** | ~~App icon is the default Android Studio placeholder.~~ **Resolved:** Replaced adaptive icon foreground/background drawables (`ic_launcher_foreground.xml`, `ic_launcher_background.xml`) and regenerated density mipmaps. | `app/src/main/res/mipmap-*`, `drawable/ic_launcher_foreground.xml` | Completed. |
| **B-02** | **HIGH** | ~~Update flow is not seamless.~~ **Resolved:** Implemented in-app `ApkUpdateInstaller` with progress bar, system progress notification, `FileProvider`, signature/version verification, and `ACTION_INSTALL_PACKAGE` handoff. | `ui/settings/SettingsScreen.kt`, `util/ApkUpdateInstaller.kt` | Completed. |
| B-03 | MED | ~~Settings "About" shows raw `v1.0.<n>` without friendly status.~~ **Resolved:** Shows `v2.0.<n>` and localized status messages via `R.string.updates_*`. | `SettingsScreen.kt` | Completed. |

### 2.2 Security

| ID | Sev | Issue | File | Recommendation |
|----|----:|-------|------|----------------|
| S-01 | HIGH | ~~`android:allowBackup="true"` will sync `message_templates`, `template_rules`, and `reply_log` to Google cloud backup.~~ **Resolved:** `backup_rules.xml` and `data_extraction_rules.xml` explicitly exclude `database` (`drivereply.db`) and `datastore` (`drivereply_prefs.preferences_pb`) from cloud backup and device transfer. | `AndroidManifest.xml` + `res/xml/backup_rules.xml`, `data_extraction_rules.xml` | Completed. |
| S-02 | HIGH | ~~The release CI attaches an unsigned APK if no keystore is configured, and `GitHubReleaseChecker` can select non-release APKs.~~ **Resolved:** `release.yml` validates all 4 signing secrets on `main` and only publishes signed release APKs; `GitHubReleaseChecker` strictly filters for signed `-release.apk` assets over HTTPS; `ApkUpdateInstaller` enforces HTTPS/GitHub host allowlisting, 100 MB stream cap, `.apk.part` staging, `longVersionCode` downgrade check, and multi-signer/rotation-safe certificate verification. | `.github/workflows/release.yml`, `GitHubReleaseChecker.kt`, `ApkUpdateInstaller.kt` | Completed. |
| S-03 | MED | `WhatsAppNotificationListener` is `exported="true"` — required because `BIND_NOTIFICATION_LISTENER_SERVICE` is a system-only permission. | `AndroidManifest.xml` | Acceptable. Protected by system-only `BIND_NOTIFICATION_LISTENER_SERVICE`. |
| S-04 | MED | `BluetoothReceiver` and `BootReceiver` are `exported="true"` — required for system broadcasts. | `AndroidManifest.xml` | Acceptable for `BOOT_COMPLETED` and `ACL_CONNECTED`/`ACL_DISCONNECTED` (protected system broadcasts). |
| S-05 | MED | ~~`DebugEventLogger` is held in-memory without a size cap or opt-in privacy default.~~ **Resolved:** `MAX_ENTRIES = 500` ring buffer implemented, default changed to `enabled = false` (`debugLogsEnabled = false`), buffer cleared on disable, and PII redacted. | `util/DebugEventLogger.kt`, `data/PreferencesManager.kt` | Completed. |
| S-06 | MED | ~~Package name `com.example.drivereply` is in the `com.example.*` reserved namespace.~~ **Resolved in v2.0.0 (PR #6):** migrated to `com.drivereply.app`. | `app/build.gradle.kts` | Completed. |
| S-07 | LOW | No `networkSecurityConfig` — `INTERNET` permission is held, all cleartext is blocked by default on API 28+, and `ApkUpdateInstaller` + `GitHubReleaseChecker` explicitly enforce `https://` and trusted GitHub hosts. | `AndroidManifest.xml`, `util/ApkUpdateInstaller.kt` | Enforced in transport layer. |
| S-08 | LOW | JSON parsing in `GitHubReleaseChecker` uses `org.json.JSONObject` — fine for trusted GitHub response, no injection vector. | `util/GitHubReleaseChecker.kt` | No action. |
| S-09 | LOW | `HttpURLConnection` with timeouts and HTTPS/host validation. | `util/GitHubReleaseChecker.kt`, `util/ApkUpdateInstaller.kt` | Completed. |

### 2.3 Build / CI

| ID | Sev | Issue | File | Fix |
|----|----:|-------|------|-----|
| C-01 | HIGH | ~~CI does not run tests or lint.~~ **Resolved:** `quality-gate` job in `release.yml` and `ci.yml` run `lintDebug` and `testDebugUnitTest`. | `.github/workflows/release.yml`, `.github/workflows/ci.yml` | Completed. |
| C-02 | HIGH | ~~No dependency vulnerability scanning in CI.~~ **Resolved:** `gradle/actions/dependency-submission@v3` publishes the dependency graph (SBOM). | `.github/workflows/release.yml` | Completed. |
| C-03 | MED | `softprops/action-gh-release@v2` and workflow permissions hardened (`contents: read` at top level, `contents: write` scoped to `build-and-release`, `tag_suffix` sanitized via `env:`, keystore written to `$RUNNER_TEMP` and shredded in `if: always()`). | `.github/workflows/release.yml` | Completed. |
| C-04 | MED | `workflow_dispatch` with sanitized `tag_suffix` supported alongside `main` pushes. | `.github/workflows/release.yml` | Completed. |
| C-05 | MED | ~~No SHA256 manifest attached.~~ **Resolved:** `SHA256SUMS.txt` generated and attached to releases. | `.github/workflows/release.yml` | Completed. |
| C-06 | LOW | ~~No `pull_request` workflow.~~ **Resolved:** `.github/workflows/ci.yml` runs on PRs to `main` and `develop`. | `.github/workflows/ci.yml` | Completed. |

### 2.4 Code Quality

| ID | Sev | Issue | Recommendation |
|----|----:|-------|----------------|
| Q-01 | HIGH | ~~Receivers call `context.startService(...)` to update a notification.~~ **Resolved:** `DriveReplyService.notificationText` `StateFlow` updates the notification in-process, `DriveReplyService.start()` catches `ForegroundServiceStartNotAllowedException`, and `BluetoothReceiver` checks `isServiceEnabled` before starting the service. | Completed. |
| Q-02 | MED | `WhatsAppNotificationListener` handles all 9 supported messaging apps (WhatsApp, WhatsApp Business, Telegram, Telegram X, Signal, Messenger, Google/Samsung/AOSP Messages). | Retained class name for manifest compatibility so existing users do not lose notification listener grant across updates. |
| Q-03 | MED | ~~Test suite is 3 files.~~ **Resolved:** Expanded unit and instrumentation tests covering `TemplateRule.matches` (including overnight windows), `WhatsAppNotificationListener` extraction/scoring/dedupe, `ApkUpdateInstaller` download/verification pipeline, `DebugEventLogger`, `GitHubReleaseChecker`, and stateless `MainScreenContent`. | Completed. |
| Q-04 | MED | `BuildConfig` is disabled (`buildConfig = false`); `DebugEventLogger` is gated by user opt-in (`enabled = false` by default). | Completed. |
| Q-05 | LOW | ~~`app/proguard-rules.pro` is empty.~~ **Resolved:** Added explicit R8 keep rules for `@kotlinx.serialization.Serializable` and `NavKey` navigation destinations. | Completed. |
| Q-06 | LOW | Driving trigger sources (`ACTIVITY_RECOGNITION`, `BLUETOOTH`, `GPS_SPEED`, `MANUAL_SIMULATION`) and 2-minute exit debounce centralized in `DriveReplyService`. | Completed. |

---

## 3. Proposed Implementation Plan (Phased)

The user has already approved (in their message) the app icon and seamless update work. Here is the exact, phased plan I'll execute, in order:

### Phase 0 — Foundations
- Create `develop` branch from `main` (per user's repo policy: never commit directly to main).
- Add `docs/SECURITY_AUDIT.md` (this file).
- Add `CHANGELOG.md` (keep-a-changelog style).

### Phase 1 — Branded App Icon (resolves B-01)
1. Author a new adaptive icon:
   - **Foreground vector drawable** (`ic_launcher_foreground.xml`): a brand mark — **steering wheel outline + reply bubble** rendered in a 108dp safe zone (66dp center), single accent color.
   - **Background drawable** (`ic_launcher_background.xml`): solid brand color with subtle radial gradient (replaces the green grid).
2. Generate legacy mipmap PNGs/WebPs for `mdpi` → `xxxhdpi` and the `round` variant at each density. Use Python (Pillow) so this runs in CI as well.
3. Verify `mipmap-anydpi-v26/ic_launcher.xml` references the new drawables; keep `<monochrome>` for Android 13+ themed icons.
4. Update `app_name` strings if a tagline is added (`<string name="app_tagline">…</string>`).
5. Add a `tools/proguard` smoke rule: nothing changes, just confirm the icon survives R8 (icons are resources, not code, so this is a visual check).

**User decision needed:** icon style direction (4 options, see clarification below).

### Phase 2 — Seamless In-App Update (resolves B-02, S-02)
1. Add `androidx.core` `FileProvider` to manifest + `res/xml/file_paths.xml` (paths: `cache/updates/`).
2. New util: `util/ApkUpdateInstaller.kt`
   - Downloads APK to `cacheDir/updates/DriveReply-<tag>.apk` using `HttpURLConnection` (or OkHttp if added) with streaming + progress callback.
   - Reads signing certificate of the downloaded APK via `PackageInfo.GET_SIGNING_CERTIFICATES` (API 28+) and compares SHA-256 of `Signature` bytes against the currently installed app's signature. Refuses install on mismatch.
3. Extend `UpdateCheckUiState` with: `downloadProgress` (0..100), `isDownloading`, `error`.
4. Settings UI: replace "Download APK (browser)" button with a row of buttons:
   - **Check for updates** (existing)
   - **Download & install** (kicks off background download with progress bar; on completion, prompts system installer)
5. Optional: add a notification with progress while the user leaves the screen.
6. `GitHubReleaseChecker` continues to do the metadata check. `ApkUpdateInstaller` does the bytes.

### Phase 3 — Hardening (resolves S-01, S-05, Q-01, Q-03)
1. Set `android:allowBackup="false"` (or write a proper `data_extraction_rules.xml` that excludes `datastore/` and `databases/`).
2. Add `MAX_ENTRIES` cap to `DebugEventLogger`.
3. Refactor `BluetoothReceiver` and `ActivityTransitionReceiver` to use a one-way `Intent` action that the service registers a `BroadcastReceiver` for, removing the `startService()` call from a background context.
4. Add unit tests:
   - `GitHubReleaseCheckerTest` — covers `parseNumericParts`, `compareVersionTags`, `shouldSuppressLegacyFalsePositive`, JSON parsing happy/sad path.
   - `DriveReplyServiceTest` (Robolectric or pure-JVM with a fake `StateFlow` source) for debounce + manual-override guard.
   - `ApkUpdateInstallerTest` (pure JVM, fakes `Context`) for signature match/mismatch.

### Phase 4 — CI Quality Gates (resolves C-01 → C-06)
1. Add `assembleDebug + lint + test` to the existing release workflow as a pre-step.
2. Add `pull_request.yml` that runs the same gates.
3. Emit `SHA256SUMS.txt` in the release artifact.
4. Pin `softprops/action-gh-release` to latest patch version + add `gradle/actions/dependency-submission@v3` to publish SBOM.
5. **Hardening gate:** fail release if `RELEASE_KEYSTORE_PATH` env is missing on `main` pushes. Stop attaching unsigned APKs as "the release".

### Phase 5 — Documentation & PR
1. Update `README.md` to mention the new in-app update flow and the new icon.
2. Update `docs/ARCHITECTURE.md` with a new §6 "Update Pipeline" describing in-app download + signature check.
3. Update `docs/PROGRESS.md` with the milestone entries.
4. Commit-by-commit history with conventional-commit messages. Open a single PR from `develop` → `main` with the full changelog in the description.

---

## 4. Out of Scope (Not Addressed Unless Requested)

- Migration off `com.example.*` package (S-06) — would force full reinstall on all users; should be its own release.
- Renaming `WhatsAppNotificationListener` → `AutoReplyNotificationListener` (Q-02) — user-facing impact: none; internal impact: medium. Will leave for a follow-up.
- On-device AI auto-replies (mentioned in README roadmap).
- Migration to a work profile / AccessibilityService–based reply (would lift the `NotificationListenerService` requirement, but requires explicit user grant). Heavy change; not in scope.

---

## 5. Verification Strategy

Per phase, before marking complete:
- `./gradlew assembleDebug` succeeds
- `./gradlew test` passes (with new tests where applicable)
- `./gradlew lint` clean for changed files
- Manual: install debug APK on a physical device or emulator with API 33+, verify icon on launcher, verify in-app update flow against a draft release

---

## 6. Token & Continuity Notes

This audit + plan document consumes ~6% of a long-task token budget. Phases 1–5 are each scoped to stay below 20% of budget per phase so the user can interrupt and resume with the audit + this document as the source of truth.
