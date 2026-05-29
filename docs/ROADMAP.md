# DriveReply Roadmap (Validated)

Last updated: May 29, 2026

This roadmap prioritizes reliability first, then UX clarity, then scale features.

## Planning Guardrails

- Goal: Improve real-world auto-reply reliability for supported chat apps while driving.
- Non-goal: Add AI-generated replies before delivery reliability is consistently high.
- Constraint: Keep Android 10+ compatibility and avoid new heavy dependencies.

## Current Baseline (Observed)

- Notification listener can be granted but temporarily disconnected after app/service start.
- Rebind recovery now exists and logs confirm listener can reconnect.
- WhatsApp notification interception and reply flow is working in recent logs.
- Supported-app detection UI (green checks) is implemented locally and pending release commit.

## Phase 1: Reliability Lock (May 30, 2026 to June 12, 2026)

- Scope:
  - Finalize and release listener health UX and diagnostics.
  - Validate reply behavior across WhatsApp, Telegram, Signal, Messenger, and SMS packages.
  - Ensure no self-reply or repeated reply loops in one driving session.
- Deliverables:
  - Structured debug logs covering full decision path (received, filtered, replied, skipped reason).
  - Stable notification listener status on Home with manual rebind fallback.
  - Release checklist for permission and listener-state verification.
- Exit criteria:
  - `listenerConnected=true` within 10 seconds after enabling service in >95% manual test runs.
  - No duplicate auto-reply per conversation per driving session.
  - No reply sent to self-authored notification events.

## Phase 2: UX Confidence Layer (June 13, 2026 to June 26, 2026)

- Scope:
  - Ship supported-app detection status with clear installed/not-installed indicators.
  - Improve setup clarity for permissions and failure recovery actions.
  - Reduce ambiguity between "permission granted" and "listener connected" states.
- Deliverables:
  - Home screen "Supported Apps Detected" list with installed green check indicators.
  - Setup flow copy updates for listener recovery steps.
  - Quick diagnostics section users can paste into support reports.
- Exit criteria:
  - New users can complete setup without external guidance in one pass.
  - Support logs include permission state, listener state, and reply decision reasons.

## Phase 3: Update + Release Robustness (June 27, 2026 to July 10, 2026)

- Scope:
  - Harden version alignment between installed APK metadata and GitHub release tags.
  - Prevent false positive update prompts for legacy version metadata.
  - Standardize release artifact naming and metadata checks.
- Deliverables:
  - CI guardrail that fails if release tag/versionName/versionCode mismatch.
  - Update checker regression tests for legacy version edge cases.
  - Documented manual QA for update flow.
- Exit criteria:
  - No false "update available" prompts on matching installed version.
  - CI green with explicit version-alignment validation.

## Phase 4: Quality Automation (July 11, 2026 to July 31, 2026)

- Scope:
  - Expand unit/instrumentation coverage around notification extraction and reply filtering.
  - Add deterministic test fixtures for known notification payload variants.
  - Add CI smoke checks for core flow.
- Deliverables:
  - Test matrix by app package and notification shape.
  - Regression tests for duplicate suppression and group-summary filtering.
  - CI summary artifact for test coverage delta.
- Exit criteria:
  - Core listener/reply path has automated regression coverage for major app variants.
  - CI failures clearly identify which app payload profile regressed.

## Backlog (After Phase 4)

- Quick Settings tile for instant manual override.
- Optional Android Auto companion cues.
- Context-aware reply personalization (only after reliability KPI holds).

## Risks and Mitigations

- Risk: OEM background restrictions break listener/service state.
  - Mitigation: Keep rebind action visible and improve battery optimization guidance.
- Risk: Messaging apps change notification action formats.
  - Mitigation: Maintain app payload fixtures and release hotfix path.
- Risk: Users misunderstand "service on" vs "listener connected".
  - Mitigation: Keep explicit dual-state indicators and guided recovery text.

## Definition of "Roadmap Correct"

- Each phase has:
  - a narrow scope,
  - a concrete time window,
  - measurable exit criteria,
  - explicit risk handling.
- Work sequencing is reliability -> UX clarity -> release hardening -> automation.
