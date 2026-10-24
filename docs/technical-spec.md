# Lan Monitor for Android — Technical Specification

**Version:** 1.16  
**Date:** 2026-10-24  
**Status:** M0–M5 implementation slices through PR #12 and UI refinement PR #14 are merged. The app has a persistent dark-by-default theme switch and a colorful adaptive launcher icon. Opt-in, privacy-filtered diagnostic logging is being added to help investigate physical-device connection failures. Physical integration, audio, screen lifecycle, movement and appearance acceptance remain to be verified.  
**Application name:** Lan Monitor  
**Repository:** https://github.com/dimkabuki/lan-monitor  
**Purpose:** Personal use on one home Wi-Fi LAN. No app-store publication is planned.

## 1. Project goal

Build one native Android APK with two selectable roles:

- **Camera** — captures video from the rear camera and audio from the microphone, then streams both directly to monitor devices.
- **Monitor** — displays the camera video, plays audio, monitors actual media progress, detects movement, and sends ordinary Android notifications while foregrounded or backgrounded, including with the screen off where the Android device permits it.

Keep the app deliberately small. No accounts, cloud services, remote access, telemetry, analytics, persistent audio/video recording, or internet-based signaling are allowed. Users start sessions manually. A session must be stopped explicitly or end as a result of the app task being removed, subject to platform behavior documented and tested on the target Samsung devices.

## 2. Naming, language, repository, and devices

- Application/display name: **Lan Monitor** (not “LAN Baby Monitor”).
- The entire app UI must be in **English**.
- Source code, identifiers, code comments, README, documentation, commit messages, branch names, pull-request titles/descriptions, CI workflow and test output labels should be in **English**.
- Target repository: `dimkabuki/lan-monitor`. Do not make changes to the older `dimkabuki/lan-baby-monitor` repository; it is reference material only.
- Proposed Android namespace/application ID: `com.dimkabuki.lanmonitor`. Verify this is acceptable before the first source-code commit and keep it stable after users install the app.
- Primary test devices:
  - Samsung Galaxy S20 FE, Android 13 / One UI 5.1.
  - Samsung Galaxy S25, Android 16 / One UI 8.5.
- Both devices must support both roles. Test both assignments: S20 FE as Camera/S25 as Monitor, and S25 as Camera/S20 FE as Monitor.
- Minimum OS for this project is Android 13 / API 33, unless a concrete dependency limitation is documented. Choose currently supported stable Kotlin, Android Gradle Plugin, Gradle, Jetpack Compose and WebRTC versions; pin versions and document them.
### 2.2. Diagnostic logging

- App-level diagnostic logging is disabled on a cold app launch and is enabled only by an explicit switch on the Camera or Monitor screen.
- While enabled, write timestamped events to a role-specific text file under `Download/Lan Monitor/` using Android's Downloads media collection. No broad storage permission is required on the minimum supported Android version.
- Log pairing workflow milestones without recording QR payloads or temporary tokens; record local discovery results/errors, WebSocket attempt/error/retry timing, WebRTC signaling/ICE state transitions, remote track events and decoded-video progress transitions.
- Never log audio/video contents, SDP descriptions, full ICE candidate payloads, or pairing secrets. Sanitize diagnostic details, flatten multi-line values, and cap each file at 2 MiB.
- Each event write opens, appends and closes the file so diagnostics remain readable after a process crash. Toggling logging off writes a final event and shows the file location. Files remain in Downloads until the user deletes them.
- These logs are short-session troubleshooting artifacts, not telemetry. They may contain local IP addresses and error details; users should review them before sharing. Automated CI cannot verify Samsung device connection behavior.
- Verify current Android SDK and target-SDK requirements before selecting `targetSdk`. If targeting API 37 or later, implement the Android 17+ `ACCESS_LOCAL_NETWORK` permission flow where applicable, and guard version-specific behavior at runtime. Do not call APIs or request permissions that do not exist on an older OS version.

### 2.1. Appearance and launcher identity

- The app uses a custom dark Material 3 color scheme by default, independently of the Android system theme.
- A user-controlled Dark theme switch is available on role selection and while either Camera or Monitor is active. The choice is persisted locally and applies immediately without ending the active session.
- A light theme is supported as an explicit user choice. System-theme synchronization is intentionally out of scope for now.
- Android status/navigation bar icon contrast follows the selected app theme.
- The launcher uses an adaptive vector icon depicting overlapping screens with colorful wireless/mirroring accents.

## 3. Architecture and technology

Preferred stack:

- Kotlin, Android SDK and Jetpack Compose.
- Native Android WebRTC SDK for media capture, encoding, transport, decoding and playback.
- A local WebSocket signaling server hosted on the Camera device; Ktor CIO is a candidate if its Android support and dependencies are verified.
- WebRTC `VideoSink` or an equivalent local decoded-frame interface for motion detection on Monitor.
- Android Foreground Services for active camera/microphone capture and background monitoring, using the appropriate service types and permissions for each Android version.
- `NotificationManager` for movement alerts and any required ongoing service notification.
- `NsdManager` / DNS-SD (mDNS) for local Camera rediscovery, with a manual local-address fallback for diagnostics and recovery.
- Coroutines and `Flow` where they simplify lifecycle-safe asynchronous state handling.
- Gradle and GitHub Actions for automated builds, unit tests, and APK artifacts.

### Current implementation status (2026-10-24)

The initial M2 implementation was merged through [PR #3](https://github.com/dimkabuki/lan-monitor/pull/3) at `e48dc2dfc7610ba72216cdab138ba7ddde5751a5`; token and video-health policies were then covered in [PR #4](https://github.com/dimkabuki/lan-monitor/pull/4), merged at `32a83d873544c66c32fa76eab4c6bb154179babd`. Initial QR-based pairing was merged through [PR #5](https://github.com/dimkabuki/lan-monitor/pull/5). [PR #6](https://github.com/dimkabuki/lan-monitor/pull/6) adds DNS-SD/mDNS advertising and discovery based on the stable Camera identity. [PR #7](https://github.com/dimkabuki/lan-monitor/pull/7) adds active-session signaling retries, endpoint refresh from matching discovery results, and WebRTC rebuild after ICE failure or sustained frame stall. This implementation snapshot does not replace the target requirements in this specification:

- Camera starts rear-camera/microphone capture after explicit user action and permission grants, then hosts a local Ktor WebSocket signaling endpoint on port 8765.
- Monitor can scan a Camera QR code to import pairing details or enter the private IPv4 address and temporary in-memory token manually. The versioned QR URI contains a persistent local Camera UUID, current private IPv4 address hints, the fixed signaling port and the session token. The Camera accepts one Monitor at a time and rejects invalid tokens and peers whose remote address is not a private IPv4 address.
- Offer/answer SDP and ICE candidates are exchanged over `ws://`; WebRTC media is intended to flow directly between devices, with no STUN/TURN server list.
- The first implementation uses 640×480 at 15 fps and sends periodic Monitor health signals based on decoded video-frame progress. It must not claim healthy media from ICE state alone.
- QR generation/scanning and strict versioned payload parsing are merged. Camera advertises its stable UUID and protocol version over local DNS-SD/mDNS. After Monitor scans a QR, it validates service identity/version and private IPv4 before updating the target; the token is never broadcast in discovery. Active-session recovery retries signaling with bounded exponential backoff and rebuilds WebRTC after ICE failure or a sustained frame stall; the 15-second recovery target still requires physical validation.
- PR #8 introduced typed Camera/Monitor foreground services, an ongoing Stop notification, contextual notification-permission requests and stop-on-task-removal behavior. PR #9 moves controller retention into a process-level store: the foreground service holds a strong reference and screen disposal detaches only the renderer. The same in-memory session can be reattached after Activity recreation while the process/service remain alive. Controller state is not persisted across process death, and the service is `START_NOT_STICKY`; background/screen-off behavior remains subject to physical validation. Camera media-resource acquisition is transactional: failures before ownership transfer stop any attempted capture and dispose partially created tracks, sources, helpers and capturer resources.
- The local signaling channel is unencrypted in this prototype. Use it only on a trusted home LAN. Physical video/audio success must be tested on actual Android devices and is not established by a green CI run.

### 3.1. Media and network boundaries

- Send video/audio directly between Camera and each Monitor using WebRTC. Camera hosts signaling only; it must not relay the media stream through the signaling server.
- Do not configure STUN or TURN servers. Use LAN-local ICE candidates only; ensure no external ICE server list is configured.
- Do not use cloud discovery, external signaling, push services, telemetry, analytics, external storage, or remote APIs at runtime.
- Do not record or persist video/audio. Logs may include technical counters and errors only, never frame contents, audio contents, or encoded media payloads.
- The active session must continue to work with WAN access unavailable, provided both devices remain connected to the same working local Wi-Fi network and can communicate with one another.
- Assume both devices are on the same subnet and the Wi-Fi access point does not enable client isolation. If local discovery or direct connectivity is blocked by the network, show a useful diagnosis rather than claiming the app is connected.

### 3.2. Device and session model

- One active Camera per session; support up to two Monitor devices.
- Each Monitor receives its own direct WebRTC peer connection. Failure or disconnect of one Monitor must not terminate the other Monitor's stream.
- Camera remains available for reconnection while its user-started session remains active.
- Each camera session has a unique session ID and a cryptographically random local pairing token. The token is only for local pairing, not a user account. Store locally as needed and do not log or commit the token.
- Do not start multiple duplicate camera services, signaling servers, peer connections, or wake locks as a result of repeated start commands.

## 4. User flow and pairing

1. User opens the APK and chooses **Camera** or **Monitor**.
2. Camera requests the necessary permissions and the user taps **Start Camera**.
3. Camera starts signaling and local service discovery, then displays a QR code.
4. Monitor scans that code and saves the stable camera/session identity and pairing token.
5. After first pairing, Monitor must rediscover the same Camera after a DHCP address change or brief Wi-Fi outage, without scanning a new QR code.
6. If mDNS/DNS-SD is unavailable, show a clear status and offer a manual local-address connection or refreshed QR code as a fallback. Never display “Connected” unless media health confirms it.
7. On stop, close WebRTC peer connections and sockets, unregister discovery, stop capture and audio, stop services, release wake locks, and free decoder resources.

The QR payload must not depend solely on the current IP address. It must contain or reference the stable pairing identity and secret needed for rediscovery and reconnection. Re-pairing may be required if app data is cleared or the Camera identity/token is reset.

## 5. Functional requirements

### FR-01 — Camera role

- Use the rear/main camera; camera switching is not required.
- Send video and microphone audio to each connected Monitor using WebRTC.
- The UI displays truthful states such as Idle, Starting, Waiting for Monitor, Connecting, Streaming, Degraded, Reconnecting, Stopped and Error.
- Show Start/Stop, QR code, session duration, connected Monitor count, current quality profile and concise diagnostics.
- Track camera capture errors, last captured frame time, encoded/sent frame or RTP progress, signaling status and each peer connection's state.
- Start camera/microphone foreground work from a visible Activity after explicit user interaction and permission grants. Do not attempt hidden camera or microphone startup from the background.
- Keeping Camera transmission alive with its screen off is desirable but lower priority than reliable Monitor background behavior. Defer it if it creates substantial complexity or platform instability. If the Camera is not actually sending frames, do not pretend the stream is healthy.

### FR-02 — Monitor role

- Display received video and play the remote audio track.
- Include clear controls for **Stop Monitoring** and mute/unmute playback.
- Show actual stream health, not only WebRTC/ICE `connected` state.
- While Monitor is foregrounded with monitoring active, prevent automatic screen timeout using `FLAG_KEEP_SCREEN_ON` on the active Activity.
- When the app is backgrounded or the screen is off, continue the active media receive/decode path, movement analysis and notifications to the extent permitted by Android, through an appropriately declared Foreground Service and (only if required) a `PARTIAL_WAKE_LOCK`.
- Start monitoring manually; do not auto-start after reboot.
- Do not change battery-optimization settings programmatically. If Samsung battery management interferes, document manual settings and the limitation instead of promising guaranteed operation.

### FR-03 — Movement detection and alerts

- Analyze frames locally on Monitor. Do not use AI/ML or cloud processing.
- Implement a simple pixel/frame-difference detector over reduced frames, with global brightness-change compensation, noise filtering, temporal smoothing and confirmation across multiple frames.
- Initial tuning target: around 4 analysis frames/second and a reduced frame around 96×72 pixels (or a similar low-cost size). These parameters may be adjusted based on reproducible tests.
- Send a normal Android notification when movement is confirmed. Notification sound and vibration follow Android system and channel settings.
- Enforce at least an 8-second cooldown between movement notifications.
- Do not implement crying detection, sound classification, or audio-triggered alarms in this release. Audio must be transmitted, but audio must not be analyzed for event detection. Silence in the room is not a stream failure.
- If notification permission is denied, show a clear explanation and next step; do not claim notifications can be delivered.
- Initial implementation parameters: sample the decoded I420 luma plane at 96×72, at most every 250 ms; subtract mean signed luma change to suppress global brightness shifts; count samples with residual luma change of at least 20; confirm an event when at least 10% of samples change for 3 consecutive analysis frames; re-arm after 4 quiet samples. Apply an 8-second monotonic-time cooldown per Monitor session. These are initial values to validate, not claimed detection-accuracy results.

### FR-04 — Stream health and diagnostics

Monitor media progress, not only connection state.

On Camera, track:

- camera capture state and errors;
- time of last captured frame;
- progress in encoded frames and sent RTP packets/bytes, to the extent exposed by the SDK;
- ICE/PeerConnection state and signaling availability;
- state of each connected Monitor.

On Monitor, track:

- time of last received and decoded frame;
- progress in received RTP packets/bytes and decoded frames, to the extent exposed by the SDK;
- ICE/PeerConnection state and video-track state;
- background-service state and last reconnect/error reason.

Monitor sends periodic health/heartbeat information to Camera. Camera uses it as an additional diagnostic signal. Distinguish at least transport loss, capture stopped, received frames stopped, decode stalled and degraded media.

Requirements:

- If actual video-frame progress stops, detect it within 3 seconds and show “Stream interrupted” or “Reconnecting”.
- Never mark media healthy solely because ICE or PeerConnection reports connected.
- Use audio-track/transport progress for technical diagnostics; never interpret a quiet room as audio failure.
- Reconnect automatically after Wi-Fi restoration or Camera IP changes, without rescanning the QR code.
- Retry with a bounded exponential backoff and continue trying until recovery or user stop. Keep UI honest while disconnected.
- Use ICE restart and/or rebuild a peer connection when needed. Avoid restarting the entire camera capture pipeline on every brief network interruption.
- Target reconnection within 15 seconds after Wi-Fi has returned and devices can communicate over the LAN.

### FR-05 — Adaptive video quality

Optimize for continuity, not maximum resolution.

- Baseline: 640×480 at 15 fps.
- Low-quality fallback: approximately 320×240 when sustained network degradation is detected.
- Optional high profile: 1280×720 at 15 fps, only after baseline streaming and reconnect behavior are stable.
- Use WebRTC's built-in adaptation and available bandwidth/RTT/loss metrics. If capture resolution must change, do so without ending the user session.
- Apply hysteresis: step down after sustained degradation and step up only after a longer stable period. Document selected thresholds and justify them using logs or test results.
- Avoid rapid oscillation. If reliable dynamic 320p/480p changes cannot be implemented safely in the first iteration, prefer WebRTC's built-in adaptation over risky connection resets.

### FR-06 — Stop and cleanup

- The Stop action promptly ends the active session.
- Attempt to end the relevant session if the user swipes the app away from Recents or uses Close all, within Android and Samsung lifecycle behavior. Verify the exact behavior on physical devices; document any OS limitation rather than overpromising.
- Switching to another app by itself must not terminate Monitor monitoring.
- After stopping, halt camera/microphone capture and transmission, close peer connections and sockets, unregister local discovery, stop the Foreground Service, release all wake locks, free media resources, and remove the ongoing service notification.
- If Camera crashes or closes unexpectedly, Monitor must detect the missing media progress and display a lost/reconnecting state.
- Start/stop commands and cleanup must be idempotent. Do not leak resources when commands are repeated.

## 6. State machine

Use one documented explicit state model, for example:

`IDLE → PREPARING → WAITING_FOR_MONITOR / DISCOVERING → CONNECTING → STREAMING → DEGRADED → RECONNECTING → STREAMING`.

Every active state must support a safe `STOPPING → STOPPED` transition. Irrecoverable failures become `ERROR` with a human-readable explanation and suggested action.

The UI state must match actual media progress. If frames have not advanced for more than 3 seconds, the UI must not remain in `STREAMING` merely because the transport is connected.

## 7. Android permissions and platform constraints

- Camera permission: `CAMERA`.
- Microphone permission: `RECORD_AUDIO`.
- Notifications on Android 13+: `POST_NOTIFICATIONS`.
- Declare Foreground Service permissions and the relevant service types for actual camera/microphone/background-monitoring use. Verify current platform requirements for each supported API level.
- Start any service requiring camera/microphone access from a visible Activity following explicit user action and permission grants. Do not attempt to bypass background-start restrictions.
- If targeting Android 17/API 37 or later, verify and implement `ACCESS_LOCAL_NETWORK` on applicable OS versions before local discovery/connection; handle denial and guard version-specific APIs.
- Do not automatically request battery-optimization exemptions or change system settings. Use Foreground Service, foreground-only `FLAG_KEEP_SCREEN_ON`, and a time-bounded `PARTIAL_WAKE_LOCK` only as needed during an active session.
- Do not bypass permission revocation, force-stop, device shutdown or OS restrictions. Recovery after user force-stop, revoked permissions, shutdown or total Wi-Fi loss is not guaranteed.

Use current official Android documentation when implementing, including:

- [Foreground Service launch restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [Foreground Service types and changes](https://developer.android.com/develop/background-work/services/fgs/changes)
- [Android 13 notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- [Keeping the screen on](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on)
- [Local network permission](https://developer.android.com/privacy-and-security/local-network-permission)

## 8. MVP user interface

Keep the UI minimal and English-only:

1. **Role selection:** Camera / Monitor.
2. **Camera screen:** Start/Stop, QR code, status, session duration, connected Monitor count, quality profile and concise diagnostics.
3. **Monitor screen:** video, audio playback and mute control, actual stream status, session duration and Stop Monitoring.
4. **Permissions/errors:** short reason and a clear action.
5. **Diagnostics:** app version, role, WebRTC state, time of last frame, counters, reconnect count and last recovery time. Never display or log media contents.

Do not implement accounts, profiles, session history, recording/export, cloud sync, sound classification or multi-camera management.

## 9. Testing strategy

### 9.1. GitHub Actions CI

Use GitHub Actions as the source of truth for automated build and unit-test results. The repository currently has three distinct execution paths:

1. **Pull-request CI** — triggers for pull requests targeting `main`. The required job/check context is `build-and-test` in the `Android CI` workflow. It runs `:app:compileDebugKotlin` and `:app:testDebugUnitTest`, then uploads test reports for 14 days. It does not assemble or publish a downloadable APK artifact.
2. **On-demand debug APK** — the manual `workflow_dispatch` input `build_apk` defaults to false. When explicitly enabled, the workflow runs its compile/tests job first and then a separate `build-apk` job that runs `:app:assembleDebug` and uploads `lan-monitor-debug-apk` for 14 days. This is a debug APK, not a production-signed release.
3. **Development pre-release** — a push to `main` triggers `.github/workflows/android-release.yml`. It runs `:app:assembleDebug` and `:app:testDebugUnitTest`, then creates a GitHub pre-release tagged `snapshot-<short-sha>` and attaches the debug APK. The first run succeeded for merge commit `d166a2fc6262cef24da3708ae83c333ed07e6ce6`, publishing [`snapshot-d166a2f`](https://github.com/dimkabuki/lan-monitor/releases/tag/snapshot-d166a2f). The asset from that first run is named `app-debug.apk`. The workflow now copies the APK to the stable filename `lan-monitor-debug.apk` before attaching it to future releases; the existing release asset is not renamed retroactively.

The CI workflow uses concurrency cancellation for runs in the same workflow/ref group, so a newer PR commit supersedes an older in-progress run. Use least-privilege workflow permissions and never expose signing secrets to PR workflows.

The workflow is not yet a comprehensive test suite. As functionality is added, introduce deterministic tests for state-machine transitions, movement detection over synthetic frames, heartbeat/timeouts/retry/backoff/reconnection, and malformed/incomplete signaling messages. Add static/format checks when a concrete tool is adopted. Do not claim these tests exist before they are implemented and run.

Use stable, unique workflow/job names. The required ruleset status-check context is currently `build-and-test`; update the ruleset if this required job name changes.

CI results prove only the checks that actually ran. Emulator and CI results do not establish correct camera, Wi-Fi, background-service, screen-off, or Samsung behavior on physical devices.
### 9.2. Emulator tests

Check app launch, role selection, permission flows, UI state restoration, errors, notification-related code paths and successful build. State clearly what the emulator does not cover.

### 9.3. Physical Samsung integration tests

Test both role assignments:

- S20 FE = Camera; S25 = Monitor.
- S25 = Camera; S20 FE = Monitor.

For each assignment:

1. Install APK, grant permissions, manually start Camera and pair Monitor by QR.
2. Verify video and audio.
3. Disable WAN/internet access while retaining working shared Wi-Fi; media and signaling must continue.
4. Background Monitor, produce test movement, and verify notification.
5. Turn Monitor screen off, repeat movement, and verify notification.
6. Return to Monitor and verify UI/media recover correctly.
7. Disable Wi-Fi on Camera or Monitor and restore it; verify automatic reconnection without a new QR scan.
8. Safely reproduce a Camera local IP change if feasible and verify rediscovery.
9. Test Stop, swipe-away and Close all behavior and verify resource cleanup.
10. Test up to two simultaneous Monitor devices where feasible.

### 9.4. Movement detector tests

Use repeatable synthetic fixtures for frame-difference, global brightness changes, small noise, static scenes and genuine changes. Manual acceptance target: detect all 10 defined test movements (10/10) and no more than one false notification in 30 minutes in a static scene under stable lighting. Record the test conditions and thresholds.

### 9.5. Stream health tests

Test camera capture stall, lost frames, lost signaling, peer disconnection, malformed signaling messages, repeated reconnect attempts, Wi-Fi recovery, and one of two Monitors disconnecting. Confirm that the UI reflects actual frame progress and does not falsely claim a healthy stream.

### 9.6. Three-hour acceptance run

For each role assignment, perform a continuous 3-hour session without connecting the phones to a charger, under normal home conditions. Include several movements and at least one controlled Wi-Fi disconnect/reconnect. Record:

- start/stop time and whether the session stayed alive;
- video/audio operation;
- disconnect and recovery counts;
- time to detect missing frames and time to recover;
- movement notification delivery and false alerts;
- quality profile changes;
- battery drain, temperature, memory use and crashes/exceptions.

Logs must remain technical only; video/audio recording for diagnostics is prohibited. Battery use should be measured, but it is not the dominant success criterion.

## 10. Acceptance criteria

| ID | Criterion | Pass condition |
|---|---|---|
| AC-01 | APK build/install | APK builds through CI and can be manually installed on S20 FE and S25. |
| AC-02 | Both roles | Camera and Monitor work on both devices; both device assignments are manually tested. |
| AC-03 | LAN-only operation | Video, audio and signaling work without WAN access. |
| AC-04 | Initial connection | ≤10 seconds on a working home LAN. |
| AC-05 | Stream stall detection | Missing frame progress is detected within 3 seconds. |
| AC-06 | Reconnection | Target ≤15 seconds after Wi-Fi returns and LAN connectivity is available. |
| AC-07 | Movement alert latency | Notification within 3 seconds after confirmed movement. |
| AC-08 | Movement detector | 10/10 specified movements; ≤1 false notification per 30 minutes in stable static conditions. |
| AC-09 | Background Monitor | Analysis and notifications continue during background and screen-off test. |
| AC-10 | Foreground screen-on | Monitor screen does not auto-sleep while active and foregrounded. |
| AC-11 | Cleanup | Stop/swipe-away/Close all behavior is tested; services/connections/resources are released as required by platform behavior. |
| AC-12 | Stability | 3-hour session without crash or unrecoverable hang. |
| AC-13 | DHCP/Wi-Fi resilience | Camera rediscovery and reconnect work without rescanning QR after address changes/brief outages. |
| AC-14 | Locality/privacy | No external ICE servers, cloud runtime calls, analytics or media recording; confirmed by code review and no-WAN test. |
| AC-15 | Diagnostics | UI/logs distinguish degradation and failure types without logging media contents. |

If a criterion is not met, document reproduction steps, technical evidence, and status. Do not mark it passed by assumption. In final reports, use `PASS`, `FAIL` or `NOT TESTED` for each AC-01–AC-15.

## 11. Repository, branch protection, CI and artifacts

The implementation repository is `dimkabuki/lan-monitor`. As verified on 2026-10-24, the repository is public, `main` is the default branch, and bootstrap PR #1 has been squash-merged at commit `d166a2fc6262cef24da3708ae83c333ed07e6ce6`. The first post-merge development release was published successfully. Do not silently change repository visibility.
### 11.1. Main branch policy

- `main` is the stable branch. Never commit directly to `main` and never force-push.
- All implementation changes must be made on a descriptive branch and proposed through a pull request targeting `main`.
- Require PRs before merging. Since this is currently a single-maintainer project, do not require an approving review from another user unless a second reviewer is actually available; self-approval is not a workable gate. The required CI status check and explicit owner-controlled merge are the minimum gates.
- Block force pushes and branch deletion. Enable “Do not allow bypassing the above settings” / equivalent admin enforcement if available.
- Require the successful `build-and-test` status check. The active `Protect main` ruleset already requires this context. Keep its name stable; if it changes, update the ruleset and verify the required check on a test PR.
- The agent must never merge PRs automatically. The repository owner controls merging.
- The active `Protect main` ruleset currently targets the default branch, requires PRs and the `build-and-test` status check, blocks branch deletion and non-fast-forward updates (including force pushes), and has no bypass actors. Repository merge settings enable squash only. Re-verify these settings when governance is changed; do not claim protection is active without inspecting GitHub's actual settings.

### 11.2. Current governance and workflow state

- Default branch: `main`.
- Bootstrap PR #1 was merged by squash after CI and the ruleset were verified. The resulting merge commit is `d166a2fc6262cef24da3708ae83c333ed07e6ce6`.
- Active ruleset: `Protect main`, targeting the default branch.
- Required status check: `build-and-test`.
- Protection: PR required, deletion blocked, non-fast-forward updates blocked, bypass list empty; branch freshness relative to `main` is not required.
- Allowed merge method: squash only; merge commits, rebase merges, and auto-merge are disabled.
- Pull-request workflow: compile Kotlin code, run unit tests, upload test reports; do not publish an APK artifact for ordinary PR runs.
- Manual workflow: optionally build and upload a debug APK after compile/tests pass.
- Push to `main`: build, test, and publish a GitHub development pre-release containing the debug APK. Both post-merge releases have succeeded; the newer release uses the normalized `lan-monitor-debug.apk` asset name.
- Automatically delete head branches: enabled (`delete_branch_on_merge=true`, checked 2026-10-24). The old `chore/bootstrap-ci` branch has now been deleted. Future merged PR branches are removed automatically.
- Continue to use descriptive feature/documentation branches and PRs for every code or documentation change. Do not commit directly to `main`, force-push, or merge PRs automatically.

The repository bootstrap, initial M2 implementation and M2 policy tests are merged. Physical tests remain `NOT TESTED` until the planned MVP verification phase; continue with feature implementation without treating that deferred test as a per-PR blocker, and do not claim acceptance before evidence exists.
### 11.3. Repository contents

Maintain at least:

- Android source and Gradle wrapper;
- `README.md` with build/install/role-selection instructions;
- `docs/architecture.md`;
- `docs/testing.md` and a physical-device acceptance checklist;
- `docs/troubleshooting.md`;
- `docs/agent-progress.md` for handoff between sessions;
- pull request template and GitHub Actions workflow;
- tests for pure business logic and synthetic video frames.

Upload test reports from pull-request CI for 14 days. The optional manual APK job uploads the artifact `lan-monitor-debug-apk` for 14 days. The first published release contains `app-debug.apk`; the updated release workflow attaches `lan-monitor-debug.apk` for subsequent releases. Existing published assets are not renamed retroactively. Do not describe an artifact as available unless the workflow actually produced it.

A release APK may be signed later, once the owner provides/configures a stable signing keystore safely. Never commit a keystore, passwords, pairing token, or GitHub secret. Use protected GitHub Secrets for release signing. Do not publish to any app store.

**Repository visibility note:** the repository is currently public. Do not change it automatically. If the owner wants it private, discuss the trade-off first: GitHub's current documentation lists classic protected branches as available on public repositories with Free, while private-repository availability depends on plan (for example, Pro/Team/Enterprise). Verify current plan features before changing visibility or weakening branch protection.

## 12. Implementation milestones

- **M0 — Repository governance: COMPLETE (2026-10-23).** Default branch `main`, active PR-only ruleset, required `build-and-test` status check, deletion/force-push protection, and squash-only merge settings are verified.
- **M1 — Android scaffold and CI: COMPLETE (2026-10-23).** Kotlin/Compose role-selection placeholder, Gradle wrapper, unit test, pull-request compile/test workflow, optional manual APK job, development pre-release workflow, README and documentation are merged to `main`. The required CI ran green and the first development pre-release succeeded.
- **M2 — Minimal WebRTC: IMPLEMENTATION MERGED; ACCEPTANCE PENDING.** [PR #3](https://github.com/dimkabuki/lan-monitor/pull/3) merged at `e48dc2dfc7610ba72216cdab138ba7ddde5751a5`. The required PR CI and post-merge development-release workflow passed. A two-device physical video/audio smoke test has not yet been performed, so M2 end-to-end acceptance remains unverified.
- **M3 — Pairing and discovery: IMPLEMENTATION MERGED; ACCEPTANCE PENDING.** PR #5 provides versioned QR pairing and manual fallback. PR #6 adds stable-identity NSD/DNS-SD discovery. PR #7 adds initial active-session reconnection; physical recovery timing remains unverified.
- **M4 — Lifecycle: IMPLEMENTATION MERGED; ACCEPTANCE PENDING.** PR #8 adds typed foreground-service scaffolding, PR #9 adds process-level controller retention/service references and Activity/Compose reattachment, and PR #10 makes Camera media startup cleanup transactional. Process-death recovery and physical background/screen-off acceptance remain pending.
- **M5 — Media health and movement: IMPLEMENTATION IN PROGRESS; ACCEPTANCE PENDING.** Movement alerts sample decoded Monitor luma frames, apply brightness compensation and temporal confirmation, and post Android notifications with an 8-second cooldown. This PR adds foreground-only screen-on behavior and local remote-audio mute/unmute. Physical audio/lifecycle verification and false-positive/latency tuning remain outstanding; introduce a partial wake lock only if device testing demonstrates a need.
- **M6 — Adaptation and resilience:** quality adaptation, two Monitors, detailed retry/backoff and diagnostics.
- **M7 — Acceptance and release:** test both device assignments, no-WAN mode, Wi-Fi recovery, 3-hour runs, documentation and installable APK artifacts.

M0/M1/M2, QR pairing (PR #5), local discovery (PR #6), active-session recovery (PR #7), foreground-service scaffolding (PR #8), session-controller retention (PR #9), and transactional Camera startup cleanup (PR #10) are merged into `main`. PR #9 adds a process-level controller store shared by Compose and the foreground service so an active in-memory session can survive screen disposal and Activity recreation while the process remains alive. It does not implement recovery after process death. Physical integration tests are intentionally grouped for the MVP acceptance phase; until that phase, report device-only criteria as `NOT TESTED`, never as passed. Keep each implementation slice in its own PR, and inspect actual CI results before moving to the next slice.

## 13. Rules for the coding agent

### 13.1. Execution style

- Use one primary agent only. Do not create sub-agents or delegate parallel independent work.
- Work sequentially in small steps. Do not attempt to implement the entire specification in one pass.
- Assume sessions may stop when usage limits are reached. The repository, commit history and `docs/agent-progress.md` are the durable source of truth, not chat memory.
- After each stable milestone, update `docs/agent-progress.md` with completed milestone, branch, commit SHA, PR link, last CI workflow link/status, completed items, known failures/blockers and one exact next action.
- At the start of every resumed session, read this specification and `docs/agent-progress.md`, inspect recent commits/branches/PRs and check recent GitHub Actions runs before editing.

### 13.2. Branches and commits

- Work only on a branch, e.g. `chore/bootstrap-ci`, `feat/webrtc-baseline` or `fix/reconnect-timeout`.
- Do not commit directly to `main`, force-push, rewrite published history or merge PRs automatically.
- Make coherent commits at stable checkpoints; avoid committing an avoidably broken intermediate state.
- Prefer one small, reviewable PR per milestone. Do not open several parallel implementation tracks.
- Do not start the next milestone until the current changes are committed, the PR is open where appropriate, and the CI result has been inspected. If CI did not run, say so.

### 13.3. CI and testing

- GitHub Actions is the primary place to run automated builds/tests. Do not run the entire test suite locally by default. Run a narrow local command only when needed to diagnose a CI failure or bootstrap the project.
- If CI fails, inspect logs, fix the cause and push an explicit fix. Never hide failures by disabling checks or replacing real tests with commands that always succeed.
- Report separately: what changed; commit SHA/PR; actual CI link and result; checks that passed; checks not run; physical tests still required.
- Never claim that Samsung physical devices, screen-off behavior or a 3-hour run were tested by CI.

### 13.4. Technical discipline

- Follow the agreed workflow: implement the MVP's functional slices incrementally, using CI and deterministic tests; run the consolidated physical-device checklist after the core MVP is ready. Deferring device testing until then changes the test timing only, not the requirement for evidence before claiming MVP acceptance.
- No clouds, analytics, telemetry, external ICE servers, media recording or collection of personal data.
- No secrets, keystores, passwords or pairing tokens in Git.
- Do not leave mandatory requirements as silent TODOs. For any deferred item, state the blocker, impact and next step.
- Do not silently change requirements. Explain a concrete technical blocker and propose the smallest viable adjustment.
- At completion, report AC-01 through AC-15 with `PASS`, `FAIL` or `NOT TESTED`, evidence links, and the APK artifact link if it truly exists.

## 14. Limitations and failure behavior

Lan Monitor is a convenience tool, not a certified safety or life-critical monitoring system. It cannot guarantee operation after Android force-stop, permission revocation, device shutdown, system crash, power loss or total Wi-Fi unavailability.

On any detected failure, show truthful status and attempt recovery where possible. Never leave the UI reporting that streaming is healthy when real media progress has stopped.

## Appendix A — Session handoff

At the end of each session, update `docs/agent-progress.md`. Add a commit for the handoff only if repository files changed. Produce a concise report containing the current branch, last commit SHA, PR, last actual GitHub Actions run/status, completed milestone, blockers and one precise next step. A milestone is not complete if changes are uncommitted or the CI result is unknown.