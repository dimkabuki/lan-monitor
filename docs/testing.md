# Testing

## CI

The intended required workflow job context is `Android CI / build-and-test`.

The workflow runs for pull requests targeting `main`, pushes to `main`, and manual dispatch. It builds `:app:assembleDebug`, runs `:app:testDebugUnitTest`, uploads test reports when available, and uploads `lan-monitor-debug.apk` only if the APK was produced.

## Local verification

With JDK 17 and Android SDK Platform 36 installed:

```sh
./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest
```

Current unit coverage checks the English role labels. It does not test media, networking, background lifecycle, movement detection, or reconnect behavior because those features are not implemented.

## Acceptance scope

No emulator or physical-device acceptance test is claimed by M1. Later milestones must add deterministic tests for state transitions, media-progress timeouts, retry/backoff, signaling validation, and synthetic-frame movement detection. Final acceptance must include both Samsung device role assignments, screen-off tests, and the specified three-hour runs.
