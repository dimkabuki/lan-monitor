# Lan Monitor

Lan Monitor is a native Android app for direct, local-network audio/video monitoring from one Camera device to up to two Monitor devices.

## Current implementation

- **Camera:** Start a session from the visible Camera screen. The app captures the rear camera and microphone, and hosts a local WebSocket signaling endpoint for up to two independent Monitor peers.
- **Open LAN discovery:** Monitor can search for all Lan Monitor Cameras advertised on the local network, select one by its stable identity and private IPv4 address, and connect without scanning a QR code or entering a token.
- **Optional pairing:** Camera also displays a versioned QR payload with its stable identity, private IPv4 address hints, signaling port and temporary token. Scanning the code or entering the token manually selects the paired-token join flow.
- **Media:** WebRTC carries audio and video directly between the Camera and each Monitor over an independent peer connection. Camera capture tracks are shared; one Monitor's disconnect does not dispose the other Monitor's peer. No STUN/TURN, cloud signaling, analytics, telemetry or media recording is configured.
- **Session lifecycle:** User-started Camera and Monitor sessions use typed Android foreground services and an ongoing notification with a Stop action. A process-level controller is shared by the UI and foreground service; leaving/recreating the Compose screen detaches the renderer without disposing an active session. Back and notification Stop actions terminate the session, and removing the app task requests cleanup. The controller is retained only in the live app process; process-death recovery is not implemented. Background/screen-off reliability still requires physical-device validation.
- **Signaling peer validation:** The Camera validates the numeric socket peer address, not a reverse-resolved hostname, and continues to reject peers outside accepted private/link-local IPv4 ranges.
- **Local discovery and recovery:** Camera advertises its stable UUID with DNS-SD/mDNS. Monitor can search all valid Lan Monitor advertisements before pairing, or filter discovery to the identity imported from a QR. Discovery records contain no pairing token and are accepted only when the resolved identity is a canonical UUID and the endpoint is a private IPv4 address. During an active session, Monitor retries signaling with bounded exponential backoff, uses the latest matching discovery address after IP changes, and rebuilds WebRTC after signaling loss, ICE failure or a sustained frame stall. Recovery is best-effort and still requires physical-device validation.
- **Appearance:** Dark theme is enabled by default to reduce screen glare during night-time use. A persistent switch is available on the role-selection, Camera and Monitor screens; system theme is intentionally not followed. The launcher icon uses a colorful screen-mirroring/wireless mark.
- **Diagnostics:** Optional debug logging is disabled on each app launch by default. Enable it on the Camera or Monitor screen to write timestamped connection/discovery/WebRTC events to `Download/Lan Monitor/`. Pairing tokens, QR payloads, SDP/ICE payloads and media contents are excluded. Disable logging to mark the end of the capture; files can be collected with the phone's file manager.
- **Builds:** Pull-request CI compiles Kotlin code and runs unit tests. A debug APK can be built on demand from GitHub Actions, and pushes to main publish development pre-releases. Debug APKs are not production-signed.

## Connection workflow

1. Connect both phones to the same trusted Wi-Fi network with client isolation disabled.
2. On Camera, choose **Camera**, grant camera/microphone permissions, and tap **Start Camera**. Keep the Camera screen open.
3. On Monitor, choose **Monitor**, tap **Search Cameras on LAN**, then tap **Connect** beside the desired Camera. No QR scan or token entry is needed.
4. Alternatively, use **Scan Camera QR** or enter a private IPv4 address and optional token for the paired-token flow.
5. Verify video, remote microphone audio and decoded-frame status, then use **Stop Monitoring** and **Stop Session**.

**LAN access model:** Open LAN joins are deliberately unauthenticated. Any device able to reach the Camera's signaling port from an accepted private IPv4 address on that network can connect and view its audio/video. Use only a trusted home Wi-Fi network; do not use guest, public or otherwise untrusted Wi-Fi, and do not expose port 8765 beyond the LAN. Supplying a QR token remains supported, but does not prevent tokenless joins while open LAN mode is enabled.

## Validation policy

Automated CI and JVM unit tests do not prove live media, Wi-Fi discovery, Android background behavior or Samsung-specific behavior.

Per the project plan, physical-device acceptance is grouped into one verification phase after the core MVP implementation is ready. Until then, device-specific criteria remain **NOT TESTED** rather than being treated as blockers for each implementation PR. See [Testing](docs/testing.md) for the accumulated MVP checklist.

## Privacy and networking

Runtime signaling and media are intended for the local network only. WebSocket signaling uses unencrypted ws:// in this prototype; use it only on a trusted home Wi-Fi network. Do not expose port 8765 beyond that network.

See [Technical Specification](docs/technical-spec.md), [Architecture](docs/architecture.md), [Testing](docs/testing.md) and [Troubleshooting](docs/troubleshooting.md) for implementation constraints and validation details.
