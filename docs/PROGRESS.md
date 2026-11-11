# DriveReply Progress Log

Last updated: May 29, 2026

## Summary

This log tracks delivered work against the roadmap phases in `docs/ROADMAP.md`.

## Phase Status

- Phase 1 (Reliability Lock): Delivered (multi-trigger state machine, self-message guard, conversation dedupe + cooldown, Android 12–15+ FGS compliance)
- Phase 2 (UX Confidence Layer): Delivered (Settings subpages, ReadinessCard, custom template rules editor, onboarding detection-mode wiring)
- Phase 3 (Update + Release Robustness): Delivered (`v2.0.<run_number>` migration to `com.drivereply.app`, in-app `ApkUpdateInstaller` with signature & downgrade verification, hardened CI/CD release pipeline)
- Phase 4 (Quality Automation): Delivered (unit & instrumentation test suite covering rule matching, notification extraction/scoring, updater pipeline, debug logger, and CI quality gates)

## Delivered Milestones

### Reliability and Reply Flow

- Expanded supported package detection for 9 messaging apps (WhatsApp, WhatsApp Business, Telegram, Telegram X, Signal, Messenger, Google/Samsung/AOSP Messages).
- Hardened notification listener behavior, delayed watchdog rebind recovery, and Android 14/15 `specialUse|location` foreground service compliance.
- Added `MessagingStyle` + `EXTRA_SELF_DISPLAY_NAME` self-message guard, conversation-level dedupe, and cross-session cooldown for replies.
- Centralized multi-trigger driving state (`ACTIVITY_RECOGNITION`, `BLUETOOTH`, `GPS_SPEED`, `MANUAL_SIMULATION`) and 2-minute exit debounce in `DriveReplyService`.
- Upgraded Room DB to v4 with synchronous default template prepopulation, indexed queries, case-insensitive contact rule matching, and automatic log retention pruning.

### Diagnostics and Debugging

- Added opt-in in-app debug log ring buffer (`MAX_ENTRIES = 500`, disabled by default for privacy, auto-cleared on disable) with PII redaction, viewer, copy, and clear actions.
- Added quick diagnostics snapshot export in Settings → Debugging.
- Added setup missing-requirements summary in Setup & Access.
- Added Phase 1 reliability checklist (`docs/PHASE1_RELIABILITY_CHECKLIST.md`).

### UX and Information Architecture

- Simplified Main screen to core runtime controls/status and extracted stateless `MainScreenContent` for UI testing.
- Moved setup/access/configuration complexity into Settings (`Setup & Access`, `Preferences`, `Automation`, `Reliability`, `Debugging`, `Updates & About`).
- Added schedule and contact-rule configuration in `EditTemplateScreen`.
- Wired Onboarding detection mode selection (`ACTIVITY`, `BLUETOOTH`, `MANUAL`) and preserved UI state across configuration changes (`rememberSaveable`).

### Versioning, Security and Update Channel

- Migrated applicationId from `com.example.drivereply` to `com.drivereply.app` (`v2.0.<run_number>`).
- Added seamless in-app `ApkUpdateInstaller` with HTTPS/host validation, `.apk.part` staging, package/versionCode verification, and signing certificate verification.
- Hardened GitHub Actions `ci.yml` and `release.yml` with lint/unit test gates, sanitized `tag_suffix`, scoped token permissions, `SHA256SUMS.txt`, and SBOM submission.

## Recent Delivery Timeline (Latest First)

- `6827364` Polish settings setup flow and permission clarity
- `ea77314` Remove settings shortcut helper from main screen
- `8fd6cb6` Add setup missing-requirements summary card
- `81acf4a` Refine settings subpages with setup status and page hints
- `58dedd1` Polish settings hub with setup summary and main cleanup
- `8e4b769` Simplify main screen and split settings into subpages
- `2bd3686` Improve home listener health and supported app clarity
- `9faaeee` Harden listener reliability and add diagnostics snapshot
- `db609f5` Align APK version metadata with GitHub releases and fix legacy update false positives
- `4964993` Add GitHub-based app version display and APK update checker

## Remaining Work (Short List)

- Validate reliability exit criteria on more physical devices/OEMs.
- Add broader automated tests for listener and notification payload variants.
- Add CI guard checks for release tag/version alignment assertions.
