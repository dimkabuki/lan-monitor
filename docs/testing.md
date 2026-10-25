# Testing and MVP acceptance checklist

## Pull-request CI

The required status-check context is `build-and-test` in the Android CI workflow. Each pull request targeting `main` compiles the app's debug Kotlin code with `:app:compileDebugKotlin`, runs `:app:testDebugUnitTest`, and uploads unit-test reports for 14 days. It does not produce an APK artifact.

Automated JVM tests cover role labels, private IPv4 validation (including rejection of reverse-DNS hostnames as numeric peer addresses), pairing-token format and exact matching, decoded-frame health boundaries, versioned QR-payload validation, discovery identity/address policy, reconnect backoff, the same-process active-session registry's Stop/error callback routing, movement confirmation/brightness compensation/cooldown policy, and remote-audio mute-state transitions. CI does not prove QR camera operation, actual DNS-SD/mDNS discovery on Wi-Fi, WebRTC media, audible audio output, or Android Activity window/lifecycle behavior.

## Signaling peer validation regression

- [ ] A Monitor on the same Wi-Fi connects when the Camera's reverse DNS maps the Monitor to a `.fritz.box` hostname.
- [ ] The Camera logs the numeric socket peer as `remote_address=192.168.x.x` (or another accepted private/link-local IPv4), not a reverse-resolved hostname.
- [ ] Public/non-private numeric peer addresses remain rejected; do not weaken the private IPv4 guard.

## Opt-in diagnostic logging

- [ ] Fresh install and each cold app launch begin with diagnostic logging disabled.
- [ ] Turning logging on from the Camera screen creates a `lan-monitor-camera-*.txt` file under `Download/Lan Monitor/` and records the Camera start/stop sequence.
- [ ] Turning logging on from the Monitor screen creates a `lan-monitor-monitor-*.txt` file under the same folder and records QR scan/decode, NSD discovery, WebSocket attempts, WebRTC states, and retries.
- [ ] Turning logging off appends a final event, stops further writes and displays the saved file location.
- [ ] Logs contain timestamps and role/event labels but not pairing tokens, QR payloads, SDP descriptions, raw ICE candidate payloads, audio or video.
- [ ] Diagnostic file creation/write failures are shown truthfully in the UI and do not crash or stop the monitoring session.
- [ ] The file-size safety cap is enforced.

Each event is appended to a Downloads text file using Android's Downloads media collection, without broad storage permissions. The log is intended for a short opt-in reproduction, not continuous production telemetry. LAN IP addresses and technical errors may still be sensitive; inspect files before sharing and do not publish them.

## Physical-device test timing

Per the agreed development workflow, physical verification is a single planned MVP acceptance phase after the core functionality is implemented. Do not block each incremental feature PR waiting for a phone test. Before the MVP phase, mark physical-only criteria **NOT TESTED**; do not infer a pass from CI. If a major implementation issue is discovered earlier, it can still be tested deliberately.

## Movement detector — automated baseline

The current initial policy samples 96×72 luma values at up to 4 Hz. It removes mean signed brightness change, counts residual changes of at least 20 luma levels, confirms movement after 3 consecutive samples affecting at least 10% of the grid, and re-arms after 4 quiet samples. The cooldown allows at most one posted alert per 8 seconds per Monitor session. JVM tests cover sustained movement, static noise, uniform brightness shifts, temporal confirmation and cooldown boundaries. These tests prove policy behavior only; they do not establish the real-room false-positive rate or movement detection accuracy.

## MVP acceptance — device checklist

Target devices:

- [ ] Samsung Galaxy S20 FE, Android 13 / One UI 5.1.
- [ ] Samsung Galaxy S25, Android 16 / One UI 8.5.

Run the full checklist with both role assignments: (A) S20 FE as Camera and S25 as Monitor; (B) S25 as Camera and S20 FE as Monitor. For each test, record PASS / FAIL / NOT TESTED and concise evidence.

### 1. Install and permissions

- [ ] Install the same MVP debug APK on both devices.
- [ ] App launches; role selection and all visible UI text are in English.
- [ ] Camera role asks for camera and microphone permissions only when starting a session; notification permission is requested contextually and denial does not block a session whose capture permissions are granted.
- [ ] Monitor QR scanner requests camera permission when needed; denial is handled with a clear message and manual-entry fallback remains usable.
- [ ] Permission revocation or a missing camera/microphone leads to a truthful error, not a false Connected/Streaming state.

### Appearance and launcher icon

- [ ] A fresh install opens in dark theme regardless of the Android system theme.
- [ ] The Dark theme switch is visible on role selection and remains available while Camera or Monitor is active.
- [ ] Switching themes updates the app immediately and does not stop an active session.
- [ ] The chosen theme is retained after closing and reopening the app.
- [ ] Light-theme text, buttons, fields and system-bar icons remain legible when light theme is selected.
- [ ] The launcher displays the new colorful screen-mirroring icon.

### 2. Pairing and connection

- [ ] Camera starts from an explicit user tap and shows its pairing QR and temporary token.
- [ ] Monitor scans the Camera QR, parses it, and loads a private LAN address plus token.
- [ ] Camera advertises its stable identity with DNS-SD/mDNS while the Camera session is active.
- [ ] After QR scan, Monitor automatically searches for the exact paired Camera identity and updates the address when a matching private IPv4 endpoint resolves.
- [ ] The discovery advertisement does not contain the pairing token.
- [ ] During an active session, a Camera IP change updates the paired endpoint and signaling/WebRTC reconnects without rescanning the QR.
- [ ] After a brief Wi-Fi outage, signaling retries automatically with bounded backoff; confirm video and audio recover within 15 seconds after LAN connectivity returns.
- [ ] A prolonged frame stall or WebRTC ICE failure triggers reconnection, while a wrong pairing token stops retrying and shows a useful error.
- [ ] If discovery is blocked or fails, the QR address and manual IP/token fields remain usable.
- [ ] A malformed/unsupported QR is rejected without crashing.
- [ ] Manual IP/token entry remains available as a fallback.
- [ ] Wrong pairing token is rejected; public or malformed IPv4 addresses are refused.
- [ ] Initial connection completes within 10 seconds on the working home LAN.
- [ ] Re-scan/re-pair behavior is understandable after Camera Stop/Start creates a new session token.

### 3. Audio/video and truthfulness

- [ ] Live video is visible on Monitor and decoded frame progress advances.
- [ ] Remote microphone audio is audible; Monitor mute/unmute disables and restores Monitor playback without disconnecting video or restarting the session.
- [ ] Camera does not report Streaming from ICE state alone.
- [ ] Stop Monitoring and Stop Session close their connection/capture paths and clear active UI state.
- [ ] Recreating the Activity while an active session is running reuses the same controller and does not silently stop the session; the renderer reattaches.
- [ ] Navigating Back from the active role intentionally stops the session and removes the ongoing foreground notification.
- [ ] Starting the other role while a different role's session remains active is rejected with a useful message.
- [ ] The ongoing notification's Stop action stops capture/signaling or Monitor connection cleanup.
- [ ] Removing the app task from Recents requests session cleanup and removes the ongoing notification.
- [ ] Process death is not treated as a supported automatic-recovery path; reopening the app does not falsely show a previous session as active.
- [ ] With notification permission denied, the UI explains that Android may hide the active notification from the drawer and points to system Settings.
- [ ] Repeated Start/Stop actions do not create duplicate services, servers, capture tracks or peer connections.

### 4. LAN-only behavior and rediscovery

- [ ] Video, audio, and signaling continue with WAN/internet unavailable while local Wi-Fi remains functional.
- [ ] Both devices on the same Wi-Fi can communicate with client isolation disabled.
- [ ] Monitor rediscovers the same Camera after a DHCP/IP change without rescanning a QR code.
- [ ] Following a brief Wi-Fi disconnect and restoration, media automatically reconnects within the 15-second target.
- [ ] A network that blocks peer-to-peer LAN traffic produces a useful failure state rather than a false success.
- [ ] No external ICE servers, cloud signaling, analytics, telemetry, or media recording are present.

### 5. Background, notifications, and movement

- [ ] Monitor remains active after switching to another app, with its ongoing foreground-service notification shown when notification permission permits it.
- [ ] Monitor continues to receive/analyze frames while the screen is off, to the extent allowed by Android/Samsung policies.
- [ ] While Monitor is active and foregrounded, its screen stays awake. When monitoring stops or the Monitor screen is disposed, normal screen-timeout behavior resumes.
- [ ] Confirmed motion creates a normal Android notification; notification permission denial is explained clearly.
- [ ] No more than one notification is sent within the 8-second cooldown.
- [ ] In stable static conditions, no more than one false movement notification occurs in 30 minutes; record lighting and test conditions.
- [ ] Turning room lights on/off or changing scene brightness does not generate persistent false alerts; record the observed behavior rather than assuming brightness compensation is sufficient.

### 6. Cleanup and resilience

- [ ] Camera capture/signaling stops promptly when requested.
- [ ] A Camera media-startup failure after partial resource creation stops any attempted capture, releases created WebRTC resources, clears active state and does not leave the foreground notification running.
- [ ] Monitor detects stopped/missing video frames within 3 seconds and does not remain marked healthy.
- [ ] Stop, navigating Back, swiping the app away from Recents, and Close all have the documented lifecycle behavior.
- [ ] No ongoing service notification, wake lock, socket or media capture is left behind after stop, when these features are implemented. The screen-on flag is also cleared when the active Monitor UI is disposed or monitoring stops.
- [ ] Wi-Fi loss, Camera app closure, invalid signaling and peer disconnection yield truthful status and recover when expected.
- [ ] No automatic start after reboot.

### 7. Three-hour stability run

For each of the two role assignments:

- [ ] Run continuously for three hours under ordinary home conditions, without charging the phones.
- [ ] Include several known movements and one controlled Wi-Fi disconnect/reconnect.
- [ ] Record disconnects/reconnects, video/audio interruptions, movement notifications/false alerts, quality changes, battery drain, temperature, memory issues and crashes.
- [ ] Confirm no unrecoverable hang or crash.

Never record or retain room audio/video as diagnostic evidence. Use technical logs and written observations only.

## Evidence record

For every failed or not-tested item, note the acceptance ID, device/OS, app build SHA/release, reproduction steps, actual result, and relevant technical logs without media contents. Report AC-01–AC-15 with `PASS`, `FAIL` or `NOT TESTED`. Do not mark physical-device acceptance complete until both role assignments and the required checklist have been reviewed.

## Build an APK on demand

In GitHub, open **Actions → Android CI → Run workflow** and enable **Build and upload a debug APK**. The workflow runs compile/tests first, then builds `:app:assembleDebug` and uploads the `lan-monitor-debug-apk` artifact for 14 days. This is a debug build, not production-signed.

## Development releases

A push to main triggers the Android Development Release workflow. It runs tests, builds the debug APK, and creates a GitHub pre-release named `snapshot-<short-sha>`, with asset `lan-monitor-debug.apk`. This is a development snapshot, not a production release.

## Local verification

With JDK 17 and Android SDK Platform 36 installed:

```sh
./gradlew --no-daemon :app:compileDebugKotlin :app:testDebugUnitTest
```

Use `:app:assembleDebug` additionally when a complete installable APK is needed.
