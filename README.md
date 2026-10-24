# Lan Monitor

Lan Monitor is a native Android app for direct, local-network audio/video monitoring between one Camera device and one Monitor device.

## Current implementation

- **Camera:** Start a session from the visible Camera screen. The app captures the rear camera and microphone, and hosts a local WebSocket signaling endpoint.
- **Pairing:** Camera displays a QR code containing a versioned pairing payload, stable local Camera identity, private IPv4 address hints, signaling port and temporary token. Monitor can scan the code to fill in the connection details.
- **Manual fallback:** The Monitor still supports entering a private IPv4 address and temporary token manually.
- **Media:** WebRTC carries audio and video directly between peers. No STUN/TURN, cloud signaling, analytics, telemetry or media recording is configured.
- **Current limits:** One Monitor per Camera. The Camera screen must remain open. QR scanning imports the address hints but does not yet provide mDNS discovery or recovery after the Camera's IP address changes. Background/screen-off monitoring, movement detection, adaptive quality and full reconnect handling remain to be implemented.
- **Builds:** Pull-request CI compiles Kotlin code and runs unit tests. A debug APK can be built on demand from GitHub Actions, and pushes to main publish development pre-releases. Debug APKs are not production-signed.

## Pairing workflow

1. Connect both phones to the same Wi-Fi network with client isolation disabled.
2. On Camera, choose **Camera**, grant camera/microphone permissions, and tap **Start Camera**. Keep the Camera screen open.
3. On Monitor, choose **Monitor** and tap **Scan Camera QR**. Grant camera permission if prompted, then scan the Camera's QR code.
4. Check the imported address and tap **Connect to Camera**. If scanning is unavailable, use the manual private-IP/token fields.
5. Verify video, remote microphone audio and decoded-frame status, then use **Stop Monitoring** and **Stop Session**.

The pairing QR contains a temporary secret. Show it only to the intended Monitor on a trusted home LAN. The token remains in memory on Camera; a stopped/new Camera session receives a new token.

## Validation policy

Automated CI and JVM unit tests do not prove live media, Wi-Fi discovery, Android background behavior or Samsung-specific behavior.

Per the project plan, physical-device acceptance is grouped into one verification phase after the core MVP implementation is ready. Until then, device-specific criteria remain **NOT TESTED** rather than being treated as blockers for each implementation PR. See [Testing](docs/testing.md) for the accumulated MVP checklist.

## Privacy and networking

Runtime signaling and media are intended for the local network only. WebSocket signaling uses unencrypted ws:// in this prototype; use it only on a trusted home Wi-Fi network. Do not expose port 8765 beyond that network.

See [Technical Specification](docs/technical-spec.md), [Architecture](docs/architecture.md), [Testing](docs/testing.md) and [Troubleshooting](docs/troubleshooting.md) for implementation constraints and validation details.
