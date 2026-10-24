# Agent Progress

## Current status — 2026-10-24

- **Repository:** [dimkabuki/lan-monitor](https://github.com/dimkabuki/lan-monitor); default branch `main`.
- **Current `main` head:** `bbfcf5410d0fb533fe3fcc2f16d95a6dada89114`, merged [PR #6](https://github.com/dimkabuki/lan-monitor/pull/6).
- **Main CI:** latest PR #6 head [run #42](https://github.com/dimkabuki/lan-monitor/actions/runs/40047191034) passed. The last published development APK is from [release run #5](https://github.com/dimkabuki/lan-monitor/actions/runs/40046268926); it is a debug APK, not production-signed.
- **Active branch / open PR:** [`feat/m4-session-recovery`, PR #7](https://github.com/dimkabuki/lan-monitor/pull/7). Implementation commit `3ab8c84fc9e5ac9c865838425eddb646ac5450f8` passed required `build-and-test` in [run #40048913133](https://github.com/dimkabuki/lan-monitor/actions/runs/40048913133). Recheck the live PR head after any later commits.
- **Latest required CI:** [Android CI run #41](https://github.com/dimkabuki/lan-monitor/actions/runs/40047058278) passed on `d21def77a9595c37d072323d32dea8b30fdf14f4`. The required `build-and-test` job passed; the optional APK job was correctly skipped for a PR event. Run #39 failed on Android NSD API mismatches; those were corrected, and run #41 passed.
- **Local discovery implementation in PR #6:**
  - `LocalCameraNsd` advertises `_lanmonitor._tcp.` with stable Camera UUID and protocol version in DNS-SD TXT data; no temporary pairing token is broadcast.
  - After a Monitor scans a QR, it searches for the matching stable UUID, resolves service records, verifies protocol version/identity, and accepts only a private IPv4 address.
  - The Monitor address field is updated after a matching Camera is found; QR address hints and manual IP/token entry remain fallback paths.
  - NSD is optional. If unavailable or registration/discovery fails, the app shows an explanatory status and leaves manual pairing available.
  - A Wi-Fi multicast lock is acquired only for Android 13 devices without T extension 7, and released on discovery stop/failure.
  - `CameraDiscoveryPolicy` is pure Kotlin with tests for matching identity, malformed IDs, missing endpoints, and private/public address handling.
- **Active-session recovery in PR #7:** Monitor retains the pairing token and latest private Camera endpoint, retries signaling with bounded exponential backoff, rebuilds WebRTC after ICE failure or a 10-second frame stall, and stops retrying after an explicitly rejected pairing token. The Camera holds its single-Monitor gate until prior peer cleanup completes. Required CI passed on implementation commit `3ab8c84`; physical Wi-Fi/IP-change recovery remains `NOT TESTED`. Only one Monitor remains supported; Camera UI must remain open; signaling is unencrypted `ws://` on a trusted home LAN.
- **Physical verification:** deliberately grouped into the consolidated MVP acceptance phase per owner direction. All device-only checks remain `NOT TESTED` until actual devices are used; do not block each implementation PR on physical testing or infer a pass from CI.
- **MVP device checklist:** [Testing guide](testing.md) includes both S20 FE / S25 role assignments, permissions, QR/manual pairing, live video/audio/frame progress, invalid tokens, stop/cleanup, WAN-off, discovery/reconnect, background/screen-off, motion notifications and two three-hour stability runs. Record `PASS` / `FAIL` / `NOT TESTED` evidence for AC-01–AC-15.
- **M0/M1/M2 and QR pairing:** implementation and CI baseline are merged. M2's live-media acceptance remains physically unverified and is deferred to the consolidated MVP phase.
- **Repository policy:** feature branches and PRs only; never commit directly to `main`, force-push, or merge PRs automatically. Required CI context is `build-and-test`; squash-only merge is configured; automatic deletion of merged branches is enabled.
- **Build baseline:** JDK 17; Gradle Wrapper 8.14.6; Android Gradle Plugin 8.13.2; Kotlin Gradle plugin 2.4.21; compile/target SDK 36; min SDK 33; WebRTC `150.7871.01`; Ktor `3.6.0`; ZXing Android Embedded `4.3.0`.
- **Next step:** review the PR #7 diff and confirm required CI is green on its live head before owner merge. After merge, continue with the next MVP slice only if recovery and coverage are sound. Device checks remain grouped for MVP acceptance; do not merge PR #7 automatically.
