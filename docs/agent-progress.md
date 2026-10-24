# Agent Progress

## Current status — 2026-10-24

- **Repository:** [dimkabuki/lan-monitor](https://github.com/dimkabuki/lan-monitor); default branch `main`.
- **Current `main` head:** `540491410e651c47139df1faacfe9ce5f8e7e6d9`, merged [PR #7](https://github.com/dimkabuki/lan-monitor/pull/7).
- **Main CI:** post-merge Android CI [run #40049422554](https://github.com/dimkabuki/lan-monitor/actions/runs/40049422554) passed on current `main`.
- **Active PR:** [PR #8 — foreground service lifecycle](https://github.com/dimkabuki/lan-monitor/pull/8), branch `feat/m4-foreground-services`. Required CI `build-and-test` passed on code plus registry-test commit `a3e7093df0c0c56ab1ae22ecec7a5e781ae1b808` in [run #40050262931](https://github.com/dimkabuki/lan-monitor/actions/runs/40050262931). This handoff update changes documentation only; confirm the subsequent PR run on the refreshed head before review.
- **Latest required CI:** [Android CI run #41](https://github.com/dimkabuki/lan-monitor/actions/runs/40047058278) passed on `d21def77a9595c37d072323d32dea8b30fdf14f4`. The required `build-and-test` job passed; the optional APK job was correctly skipped for a PR event. Run #39 failed on Android NSD API mismatches; those were corrected, and run #41 passed.
- **Local discovery implementation in PR #6:**
  - `LocalCameraNsd` advertises `_lanmonitor._tcp.` with stable Camera UUID and protocol version in DNS-SD TXT data; no temporary pairing token is broadcast.
  - After a Monitor scans a QR, it searches for the matching stable UUID, resolves service records, verifies protocol version/identity, and accepts only a private IPv4 address.
  - The Monitor address field is updated after a matching Camera is found; QR address hints and manual IP/token entry remain fallback paths.
  - NSD is optional. If unavailable or registration/discovery fails, the app shows an explanatory status and leaves manual pairing available.
  - A Wi-Fi multicast lock is acquired only for Android 13 devices without T extension 7, and released on discovery stop/failure.
  - `CameraDiscoveryPolicy` is pure Kotlin with tests for matching identity, malformed IDs, missing endpoints, and private/public address handling.
- **Active-session recovery (PR #7, merged):** Monitor retains the pairing token and latest private Camera endpoint, retries signaling with bounded exponential backoff, rebuilds WebRTC after ICE failure or a 10-second frame stall, and stops retrying after an explicitly rejected pairing token. The Camera holds its single-Monitor gate until prior peer cleanup completes. PR CI and post-merge CI passed; physical Wi-Fi/IP-change recovery remains `NOT TESTED`. Only one Monitor remains supported; signaling is unencrypted `ws://` on a trusted home LAN.
- **Foreground-service slice (current branch):** adds Camera `camera|microphone` and Monitor `mediaPlayback` service types, an ongoing Stop notification, contextual notification permission request, task-removal cleanup and docs. The service is initial scaffolding while the controller remains UI/Compose-owned; Activity/process recreation and background/screen-off behavior still require work and device evidence.
- **Physical verification:** deliberately grouped into the consolidated MVP acceptance phase per owner direction. All device-only checks remain `NOT TESTED` until actual devices are used; do not block each implementation PR on physical testing or infer a pass from CI.
- **MVP device checklist:** [Testing guide](testing.md) includes both S20 FE / S25 role assignments, permissions, QR/manual pairing, live video/audio/frame progress, invalid tokens, stop/cleanup, WAN-off, discovery/reconnect, background/screen-off, motion notifications and two three-hour stability runs. Record `PASS` / `FAIL` / `NOT TESTED` evidence for AC-01–AC-15.
- **M0/M1/M2 and QR pairing:** implementation and CI baseline are merged. M2's live-media acceptance remains physically unverified and is deferred to the consolidated MVP phase.
- **Repository policy:** feature branches and PRs only; never commit directly to `main`, force-push, or merge PRs automatically. Required CI context is `build-and-test`; squash-only merge is configured; automatic deletion of merged branches is enabled.
- **Build baseline:** JDK 17; Gradle Wrapper 8.14.6; Android Gradle Plugin 8.13.2; Kotlin Gradle plugin 2.4.21; compile/target SDK 36; min SDK 33; WebRTC `150.7871.01`; Ktor `3.6.0`; ZXing Android Embedded `4.3.0`.
- **Next step:** verify required CI on the refreshed PR #8 head, review permission/start/stop cleanup, and wait for owner review/merge. Device checks remain grouped for MVP acceptance; do not merge PRs automatically.
