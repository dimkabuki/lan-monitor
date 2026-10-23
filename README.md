# Lan Monitor

Lan Monitor is a native Android app for direct, local-network audio/video monitoring between one Camera device and up to two Monitor devices.

## Current state

- The Android scaffold and CI baseline are merged to `main`.
- The app currently contains the role-selection placeholder; runtime camera capture, signaling, WebRTC media, local discovery, and movement detection are not implemented yet.
- Pull-request CI compiles the app code and runs unit tests. A debug APK can be built on demand from GitHub Actions, and pushes to `main` publish development pre-releases.
- The debug APK is for development/testing and is not production-signed.

## Development

See [Technical Specification](docs/technical-spec.md) for the functional requirements, privacy constraints, milestone plan, and acceptance criteria.

See [Architecture](docs/architecture.md), [Testing](docs/testing.md), and [Troubleshooting](docs/troubleshooting.md) for the current build and validation process.

The project is deliberately local-network-only at runtime: no cloud signaling, external STUN/TURN, analytics, telemetry, or persistent media recording. Do not claim physical-device behavior is validated until the Samsung integration tests have actually been performed.
