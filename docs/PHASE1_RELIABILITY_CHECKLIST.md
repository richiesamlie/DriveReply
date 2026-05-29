# Phase 1 Reliability Checklist

Last updated: May 29, 2026

Use this checklist before a release candidate to verify listener and auto-reply reliability.

## Preconditions

- Debug logs enabled in Settings.
- Generate and copy a quick diagnostics snapshot from Settings (Debugging -> Copy Snapshot).
- Notification listener permission granted.
- Foreground service enabled.
- Manual driving simulation turned ON (or real driving trigger active).
- At least one active auto-reply template.

## Validation Steps

1. Listener startup health
- Action: Toggle service ON, wait 10 seconds.
- Expect logs:
  - `DriveReplyService` service created/start requested
  - `DriveReplyListener` listener connected
- Pass condition:
  - `listenerConnected=true` visible in permission refresh logs.

2. Rebind recovery path
- Action: Keep listener permission ON and wait up to 30 seconds after a disconnected state.
- Expect logs:
  - `Listener health check requested rebind (permissionGranted=true, connected=false)`
  - Followed by `Notification listener connected`
- Pass condition:
  - Listener reconnects without app restart.

3. Incoming notification detection
- Action: Send test message from another account to WhatsApp (or supported app).
- Expect logs:
  - `Posted package=... supported=true ...`
  - `Notif received package=...`
- Pass condition:
  - Notification is detected with supported package.

4. Decision-path observability
- Action: Trigger at least one send and one skip case.
- Expect logs include explicit reasons such as:
  - `Skip: driving mode OFF`
  - `Skip group conversation`
  - `Skip duplicate conversation`
  - `Skip: no reply action`
  - `Template selected ... source=...`
  - `Auto-reply sent ...`
- Pass condition:
  - No silent failure path for supported notifications.

5. Duplicate suppression in same driving session
- Action: Send multiple messages in same conversation while driving remains ON.
- Expect logs:
  - First message: `Auto-reply sent`
  - Later message(s): `Skip duplicate conversation`
- Pass condition:
  - Exactly one auto-reply per conversation per driving session.

6. Session reset behavior
- Action: Stop driving simulation, then start again, send new message in same chat.
- Expect logs:
  - Driving false -> true transition
  - New `Auto-reply sent` for conversation
- Pass condition:
  - Dedupe state resets across driving sessions.

7. Self-message guard
- Action: Observe outgoing/self notification updates from messaging app after reply.
- Expect logs:
  - `Skip self-message ...` for self-authored events when detected.
- Pass condition:
  - App does not auto-reply to self notification events.

## Release Gate

Release candidate passes Phase 1 only when:
- Steps 1-4 pass on target device(s).
- Steps 5-7 pass on at least one supported primary app (WhatsApp minimum).
- No crash or ANR appears during the validation run.
