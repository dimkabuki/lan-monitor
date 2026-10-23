# Lan Monitor

Lan Monitor is a native Android app for direct, local-network audio/video monitoring between one Camera device and one Monitor device in the current M2 baseline.

## Current state

- M0 repository governance and M1 Android scaffold/CI are merged to `main`.
- M2 introduces a first WebRTC/session implementation: Camera starts a local signaling WebSocket, and Monitor connects with a private IPv4 address and a temporary pairing token.
- The current M2 implementation supports one Monitor at a time. Pairing is manual; QR pairing, mDNS rediscovery, automatic recovery after address changes, motion detection, and background/screen-off monitoring are not implemented yet.
- Pull-request CI compiles Kotlin code and runs unit tests. A debug APK can be built on demand from GitHub Actions, and pushes to `main` publish development pre-releases.
- Debug APKs are for development/testing and are not production-signed.

## M2 manual smoke test

1. Connect both phones to the same Wi-Fi network with client isolation disabled.
2. On the Camera phone, choose **Camera**, grant camera/microphone permissions, and tap **Start Camera**. Keep the Camera screen open.
3. Note one private IPv4 address and the temporary pairing token shown by Camera.
4. On the Monitor phone, choose **Monitor**, enter the Camera address/token, and tap **Connect to Camera**.
5. Verify live video and audio and check the status reports decoded video-frame progress. Use **Stop Monitoring** and **Stop Session** to clean up.

Automated CI does not prove that media is working on physical Samsung devices. Record device, OS, video/audio result, logs, and reconnect observations when running the smoke test. Do not claim physical-device acceptance until it has been performed.

## Privacy and networking

Runtime signaling and media are intended for the local network only. The current manual M2 signaling endpoint accepts a private IPv4 peer and a random, in-memory pairing token. WebSocket signaling is unencrypted (`ws://`) in this prototype; use it only on a trusted home Wi-Fi network. No STUN/TURN servers, cloud signaling, analytics, telemetry, or persistent media recording are configured.

See [Technical Specification](docs/technical-spec.md) for requirements and acceptance criteria. See [Architecture](docs/architecture.md), [Testing](docs/testing.md), and [Troubleshooting](docs/troubleshooting.md) for implementation and validation details.
