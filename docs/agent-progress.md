# Agent Progress

## Current status — 2026-10-24

- **Repository:** [dimkabuki/lan-monitor](https://github.com/dimkabuki/lan-monitor); default branch is `main`.
- **Main head:** `e48dc2dfc7610ba72216cdab138ba7ddde5751a5`, merge commit for [PR #3](https://github.com/dimkabuki/lan-monitor/pull/3).
- **M2 implementation:** PR #3 is merged. The final M2 PR CI run [#33](https://github.com/dimkabuki/lan-monitor/actions/runs/40037108359) and post-merge development release workflow [run #3](https://github.com/dimkabuki/lan-monitor/actions/runs/40037693854) succeeded. The release attached a debug APK; it is not production-signed.
- **Physical integration status:** NOT TESTED. CI confirms code compilation and unit tests, not a working live video/audio session between two Android devices. Do not mark M2 end-to-end acceptance passed until the owner runs the two-device smoke test described in [testing.md](testing.md).
- **Active branch / open PR:** [`test/m2-session-hardening`, PR #4](https://github.com/dimkabuki/lan-monitor/pull/4). This branch extracts two small policies from the Android/WebRTC controller so they are directly testable on the JVM:
  - `PairingToken` generates a 96-bit cryptographically random Base64 URL-safe token and compares nonblank token values using `MessageDigest.isEqual`.
  - `VideoFrameHealth` evaluates decoded-frame age against the three-second stall timeout, distinguishing no observed frame from a stalled stream.
- **Tests added:** token format/exact-match tests; no-frame, exact-boundary, stalled-frame, monotonic-time defensive behavior, and invalid-timeout tests. The PR CI must pass before merge. None of these tests substitutes for live device validation.
- **M2 remaining validation:** connect two phones to the same Wi-Fi (with client isolation disabled); test video and audible microphone audio; verify decoded frames advance; try an incorrect token; stop both ends; repeat with roles swapped. Record device models/Android versions, connection result/latency, and errors.
- **Next product milestone:** M3 adds QR pairing, local discovery and reconnection after DHCP/IP/Wi-Fi changes. Per the technical specification, prioritize the physical M2 smoke test before claiming M2 accepted or expanding the pairing feature set.
- **M0/M1:** complete. Main-branch governance, required CI context `build-and-test`, and squash-only merge settings remain active. Do not commit directly to `main`, force-push, or merge pull requests automatically.
- **Automatic deletion of merged branches:** enabled; repository API reports `delete_branch_on_merge=true`. The old `chore/bootstrap-ci` branch predates enabling it and remains until manually deleted. It can be safely deleted because its bootstrap changes are already in merged PR #1 / `main`.
- **Build baseline:** JDK 17; Gradle Wrapper 8.14.6; Android Gradle Plugin 8.13.2; Kotlin Gradle plugin 2.4.21; compile/target SDK 36; min SDK 33; WebRTC Android SDK `150.7871.01`; Ktor `3.6.0`.
- **Known prototype constraints:** manual private-IPv4/token pairing; one Monitor; Camera screen must remain open; no mDNS/QR pairing, background/screen-off monitoring, motion detection, dynamic quality, STUN/TURN or cloud signaling. Signaling is unencrypted `ws://` and must remain on a trusted home LAN.
