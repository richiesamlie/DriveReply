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
| **B-01** | **CRITICAL** | **App icon is the default Android Studio placeholder** (green Android robot on green grid). The README positions the app as "premium, high-performance" but the launcher is the template. | `app/src/main/res/mipmap-*`, `drawable/ic_launcher_foreground.xml` | Replace foreground/background drawables + regenerate mipmaps. See §3. |
| **B-02** | **HIGH** | **Update flow is not seamless** — `SettingsUpdatesAbout` only opens the release page in the browser via `Intent.ACTION_VIEW`. User must: leave app → download APK → grant "install unknown apps" to browser → confirm install → relaunch. | `ui/settings/SettingsScreen.kt:670-677`, `util/GitHubReleaseChecker.kt` | Implement in-app downloader (OkHttp + progress notification), write to `cacheDir/updates/`, expose via `FileProvider`, launch `ACTION_INSTALL_PACKAGE`. See §3. |
| B-03 | MED | Settings "About" shows raw `v1.0.<n>` without a friendly "Up to date" / "Update available" badge. | `SettingsScreen.kt:678` | Cosmetic, address with B-02. |

### 2.2 Security

| ID | Sev | Issue | File | Recommendation |
|----|----:|-------|------|----------------|
| S-01 | HIGH | `android:allowBackup="true"` will sync `message_templates`, `template_rules`, and `reply_log` to Google cloud backup. Reply logs can contain sensitive content. | `AndroidManifest.xml:40` + `res/xml/backup_rules.xml`, `data_extraction_rules.xml` | Set `allowBackup="false"` *or* explicitly exclude `datastore/`, `databases/` in `data_extraction_rules.xml`. |
| S-02 | HIGH | The release CI **attaches an unsigned APK** if no keystore is configured, but the in-app `GitHubReleaseChecker` is willing to recommend it as the "Download APK" because it filters only by `-release.apk` filename, not signature. A user could install a tampered build via the official updater path. | `.github/workflows/release.yml:53-57`, `GitHubReleaseChecker.kt:51-58` | (a) In CI, **fail the job** if `RELEASE_KEYSTORE_PATH` is missing on `main` rather than attaching an unsigned APK. (b) On the client, after download, compare the downloaded APK's signing certificate (PackageInfo.signatures / SigningInfo) against the installed app's certificate before allowing install. |
| S-03 | MED | `WhatsAppNotificationListener` is `exported="true"` — required because `BIND_NOTIFICATION_LISTENER_SERVICE` is a system-only permission, but no extra hardening. | `AndroidManifest.xml:64-71` | Acceptable. The system-only `permission` attribute is the correct protection. Document this in `ARCHITECTURE.md`. |
| S-04 | MED | `BluetoothReceiver` and `BootReceiver` are `exported="true"` — required for system broadcasts. No `android:permission` guard. | `AndroidManifest.xml:79-96` | Acceptable for `BOOT_COMPLETED` and `ACL_CONNECTED` (system-sender only). Document. |
| S-05 | MED | `DebugEventLogger` is held in-memory and exposed in Settings, but no log size cap. Long sessions can grow unboundedly and degrade UI rendering. | `util/DebugEventLogger.kt` (not read) | Add `MAX_ENTRIES = 500` cap + eviction. |
| S-06 | MED | Package name `com.example.drivereply` is in the `com.example.*` reserved namespace. Google Play Console rejects it. Fine for sideloading (current model) but blocks Play distribution. | `app/build.gradle.kts:27,30` | Long-term: migrate to `com.richiesamlie.drivereply` (requires full uninstall+reinstall; migration story documented in release notes). |
| S-07 | LOW | No `networkSecurityConfig` — `INTERNET` permission is held, all cleartext is blocked by default on API 28+, but explicit config makes intent clear. | `AndroidManifest.xml` | Optional: add `network_security_config.xml` pinning `api.github.com`. |
| S-08 | LOW | JSON parsing in `GitHubReleaseChecker` uses `org.json.JSONObject` — fine for trusted GitHub response, no injection vector. | `util/GitHubReleaseChecker.kt` | No action. |
| S-09 | LOW | `HttpURLConnection` with 10s timeouts — adequate. No certificate pinning, acceptable for public GitHub API. | `util/GitHubReleaseChecker.kt` | No action. |

### 2.3 Build / CI

| ID | Sev | Issue | File | Fix |
|----|----:|-------|------|-----|
| C-01 | HIGH | **CI does not run tests or lint.** A green CI run only means "it compiled". Regressions can ship. | `.github/workflows/release.yml:39-40` | Add `./gradlew lint test` as a separate `test` job, gate release on its success. |
| C-02 | HIGH | **No dependency vulnerability scanning** in CI. | release.yml | Add `gradle/actions/dependency-submission` (dependency graph) + a scheduled OWASP/`dependency-check` job. |
| C-03 | MED | `softprops/action-gh-release@v2` — pinned major only. Use a SHA or latest minor for supply-chain hygiene. | `release.yml:60` | Bump to `@v2.3.x` or pin SHA. |
| C-04 | MED | Release workflow runs on every `push` to `main` — no manual gate, no dry-run for tagged releases. | `release.yml:4-7` | Add `workflow_dispatch` input + only create release on tag pushes (`v*`). Keep `assembleDebug` on every push. |
| C-05 | MED | APK names embed `${{ github.run_number }}` — fine, but no SHA256 manifest attached. Users have no way to verify integrity. | `release.yml:49-57` | Emit `SHA256SUMS.txt` and append to release notes. |
| C-06 | LOW | No `pull_request` workflow — PRs don't get a build verification. | release.yml | Add `pull_request.yml` running `lint test assembleDebug`. |

### 2.4 Code Quality

| ID | Sev | Issue | Recommendation |
|----|----:|-------|----------------|
| Q-01 | HIGH | `BluetoothReceiver.kt:72,82` and `ActivityTransitionReceiver.kt:68` call `context.startService(...)` to update a notification. On Android 12+ (API 31+), `startService` from a background context is restricted to a few exempt cases. Currently works because the service is already foreground, but brittle. | Use a `LocalBroadcastManager` (deprecated) or, preferably, expose a `BroadcastReceiver` in the service for in-process updates, or use a `MutableStateFlow` collected by the service. |
| Q-02 | MED | `WhatsAppNotificationListener` is misnamed — it handles Telegram, Signal, Messenger, SMS in addition to WhatsApp. | Rename to `AutoReplyNotificationListener`. (Breaking change; can wait.) |
| Q-03 | MED | Test suite is 3 files (only data classes, no service/listener/flow tests). | Add unit tests for `GitHubReleaseChecker`, `DriveReplyService.setDrivingState` debounce logic, and `WhatsAppNotificationListener` template extraction. |
| Q-04 | MED | `BuildConfig` is disabled (`buildConfig = false`), so debug toggles cannot be compile-time gated. | Enable `buildConfig = true` if you want debug flags. Otherwise no action. |
| Q-05 | LOW | `app/proguard-rules.pro` is empty — relies entirely on consumer rules from libraries. Verify release APK with `apkanalyzer` after first signed build to confirm `DriveReplyApplication` and Room schemas survive R8. | Add a smoke check to CI. |
| Q-06 | LOW | `_manualSimulationOverride` is read in `setDrivingState` without `@Volatile`; only the write path mutates it. MutableStateFlow handles visibility, so this is safe — but the field type is `MutableStateFlow<Boolean>` not `volatile var`. | No action; just clarifying. |

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
