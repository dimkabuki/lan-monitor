package com.dimkabuki.lanmonitor

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.install
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.*
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSink
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * M2 media/session controller. Signaling uses a single authenticated Monitor over the home LAN.
 * Media is carried directly by WebRTC; this controller does not configure STUN or TURN servers.
 */
class LanWebRtcSession(
    context: Context,
    internal val role: AppRole,
) {
    private class CameraMonitorPeer(
        val id: String,
        val remoteAddress: String,
        val sender: (String) -> Unit,
    ) {
        val closed = AtomicBoolean(false)
        val localCandidateLock = Any()
        val remoteCandidateLock = Any()
        val pendingLocalCandidates = mutableListOf<IceCandidate>()
        val pendingRemoteCandidates = mutableListOf<IceCandidate>()
        @Volatile var joined = false
        @Volatile var videoHealthy = false
        @Volatile var localDescriptionSignaled = false
        @Volatile var remoteDescriptionSet = false
        @Volatile var peerConnection: PeerConnection? = null
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val eglBase: EglBase = EglBase.create()
    private val cameraPeerAdmission = CameraPeerAdmissionPolicy()
    private val cameraPeers = ConcurrentHashMap<String, CameraMonitorPeer>()
    private val candidateLock = Any()
    private val remoteCandidateLock = Any()

    var status by androidx.compose.runtime.mutableStateOf("Idle")
        private set
    var isBusy by androidx.compose.runtime.mutableStateOf(false)
        private set
    var sessionActive by androidx.compose.runtime.mutableStateOf(false)
        private set
    var connectedMonitorCount by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    var cameraAddresses by androidx.compose.runtime.mutableStateOf(emptyList<String>())
        private set
    var pairingToken by androidx.compose.runtime.mutableStateOf("")
        private set
    var pairingPayload by androidx.compose.runtime.mutableStateOf("")
        private set
    var discoveryStatus by androidx.compose.runtime.mutableStateOf("Not active")
        private set
    var discoveredCameraAddress by androidx.compose.runtime.mutableStateOf("")
        private set
    internal var discoveredCameras by androidx.compose.runtime.mutableStateOf(emptyList<CameraDiscoveryResult>())
        private set
    var movementStatus by androidx.compose.runtime.mutableStateOf("Waiting for Monitor video.")
        private set
    var remoteAudioMuted by androidx.compose.runtime.mutableStateOf(false)
        private set

    @Volatile private var disposed = false
    @Volatile private var shutdownRequested = false
    @Volatile private var controllerHasWork = false
    @Volatile private var cameraStartJob: Job? = null
    @Volatile private var cameraRecoveryJob: Job? = null

    internal val hasSessionWork: Boolean
        get() = controllerHasWork

    internal val isDisposed: Boolean
        get() = disposed
    internal val isShuttingDown: Boolean
        get() = shutdownRequested
    @Volatile private var monitorSender: ((String) -> Unit)? = null
    @Volatile private var lastLocalVideoFrameAtMs = 0L
    @Volatile private var lastRemoteVideoFrameAtMs = 0L
    @Volatile private var lastLoggedVideoHealth: Boolean? = null
    @Volatile private var lastMovementAnalysisAtMs = 0L
    @Volatile private var movementAnalysisReady = false
    @Volatile private var lastMovementAnalysisError: String? = null
    private val movementDetector = MovementDetector()
    private val remoteAudioMuteState = RemoteAudioMuteState()
    private val remoteAudioLock = Any()
    @Volatile private var localDescriptionSignaled = false
    @Volatile private var remoteDescriptionSet = false

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var cameraServer: EmbeddedServer<*, *>? = null
    private var localCameraNsd: LocalCameraNsd? = null
    private var monitorClient: HttpClient? = null
    private var monitorConnectionJob: Job? = null
    @Volatile private var monitorTargetHost: String? = null
    @Volatile private var monitorAuthenticationRejected = false
    @Volatile private var monitorReconnectRequester: (() -> Unit)? = null
    @Volatile private var monitorConnectionStartedAtMs = 0L
    private var healthJob: Job? = null
    private var peerConnection: PeerConnection? = null
    private var cameraCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var audioSource: org.webrtc.AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var remoteAudioTrack: AudioTrack? = null
    @Volatile private var localRenderer: SurfaceViewRenderer? = null
    @Volatile private var remoteRenderer: SurfaceViewRenderer? = null
    private val pendingLocalCandidates = mutableListOf<IceCandidate>()
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()

    fun toggleRemoteAudioMuted() {
        if (role != AppRole.MONITOR) return

        val muted: Boolean
        synchronized(remoteAudioLock) {
            muted = remoteAudioMuteState.toggle()
            remoteAudioTrack?.setEnabled(!muted)
        }
        updateUi { remoteAudioMuted = muted }
    }

    private val localVideoHealthSink = object : VideoSink {
        override fun onFrame(frame: org.webrtc.VideoFrame) {
            lastLocalVideoFrameAtMs = SystemClock.elapsedRealtime()
        }
    }

    private val remoteVideoHealthSink = object : VideoSink {
        override fun onFrame(frame: org.webrtc.VideoFrame) {
            val nowMs = SystemClock.elapsedRealtime()
            if (lastRemoteVideoFrameAtMs == 0L) {
                DiagnosticLog.log(
                    role,
                    "remote_video_first_frame",
                    "width=${frame.buffer.width}; height=${frame.buffer.height}",
                )
            }
            lastRemoteVideoFrameAtMs = nowMs
            inspectMovementFrame(frame, nowMs)
        }
    }

    private fun inspectMovementFrame(frame: org.webrtc.VideoFrame, nowMs: Long) {
        if (role != AppRole.MONITOR || disposed || shutdownRequested) return
        if (
            lastMovementAnalysisAtMs != 0L &&
            nowMs - lastMovementAnalysisAtMs < MovementFrameSampler.ANALYSIS_INTERVAL_MS
        ) return
        lastMovementAnalysisAtMs = nowMs

        val i420 = try {
            frame.buffer.toI420()
        } catch (_: RuntimeException) {
            return
        } ?: return

        try {
            val samples = MovementFrameSampler.sampleLuma(i420)
            val result = movementDetector.process(samples, nowMs)
            if (lastMovementAnalysisError != null) lastMovementAnalysisError = null
            if (!movementAnalysisReady) {
                movementAnalysisReady = true
                updateUi { movementStatus = "Watching for movement." }
            }
            if (result.movementStarted) publishMovementEvent(result.notificationAllowed)
        } catch (error: RuntimeException) {
            val message = "Movement analysis unavailable: ${error.message ?: error.javaClass.simpleName}"
            if (lastMovementAnalysisError != message) {
                lastMovementAnalysisError = message
                updateUi { movementStatus = message }
            }
        } finally {
            runCatching { i420.release() }
        }
    }

    private fun publishMovementEvent(notificationAllowed: Boolean) {
        if (!notificationAllowed) {
            updateUi { movementStatus = "Movement detected; alert cooldown is active." }
            return
        }

        when (MovementAlertNotifier.post(appContext)) {
            MovementNotificationOutcome.POSTED ->
                updateUi { movementStatus = "Movement detected; notification posted." }
            MovementNotificationOutcome.PERMISSION_DENIED ->
                updateUi {
                    movementStatus = "Movement detected, but notifications are not permitted. Allow Lan Monitor notifications in Android Settings."
                }
            MovementNotificationOutcome.APP_NOTIFICATIONS_DISABLED,
            MovementNotificationOutcome.CHANNEL_DISABLED ->
                updateUi {
                    movementStatus = "Movement detected, but notifications are disabled. Enable them in Android Settings."
                }
            MovementNotificationOutcome.FAILED ->
                updateUi { movementStatus = "Movement detected, but the notification could not be posted." }
        }
    }

    fun recoverCameraAfterForegroundReturn() {
        if (role != AppRole.CAMERA || !sessionActive || isBusy || disposed || shutdownRequested) return
        if (cameraRecoveryJob?.isActive == true) return

        // Refresh only peers that were already joined when the Activity resumed. A new socket
        // negotiates its own offer and must not receive a duplicate one here.
        val peersAtResume = cameraPeers.values.filter { it.joined && !it.closed.get() }
        cameraRecoveryJob = scope.launch {
            try {
                delay(CameraCaptureRecoveryPolicy.FOREGROUND_RETURN_GRACE_MS)
                if (!sessionActive || isBusy || shutdownRequested || disposed) return@launch

                val capturer = cameraCapturer ?: return@launch
                val nowMs = SystemClock.elapsedRealtime()
                val lastFrameAtMs = lastLocalVideoFrameAtMs
                val captureStalled = CameraCaptureRecoveryPolicy.shouldRecover(lastFrameAtMs, nowMs)
                val frameAgeMs = lastFrameAtMs.takeIf { it > 0L }?.let { nowMs - it } ?: -1L

                DiagnosticLog.log(
                    role,
                    "camera_foreground_resume_recovery_started",
                    "local_frame_age_ms=${frameAgeMs}; capture_stalled=${captureStalled}; joined_peer_count_at_resume=${peersAtResume.size}",
                )

                if (captureStalled) {
                    runCatching { capturer.stopCapture() }.onFailure { error ->
                        DiagnosticLog.log(
                            role,
                            "camera_foreground_capture_stop_failed",
                            "exception=${error.javaClass.simpleName}",
                        )
                    }
                    if (!currentCoroutineContext().isActive || shutdownRequested || disposed || cameraCapturer !== capturer) {
                        return@launch
                    }

                    val restartResult = runCatching {
                        capturer.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)
                    }
                    if (restartResult.isFailure) {
                        val error = restartResult.exceptionOrNull()
                        DiagnosticLog.log(
                            role,
                            "camera_foreground_capture_restart_failed",
                            "exception=${error?.javaClass?.simpleName ?: "unknown"}",
                        )
                        updateStatus("Could not resume camera capture: ${error?.message ?: "unknown error"}")
                        return@launch
                    }
                    delay(400L)
                }

                if (!sessionActive || shutdownRequested || disposed || cameraCapturer !== capturer) return@launch
                val connectedPeers = peersAtResume.filter { peer ->
                    !peer.closed.get() && peer.joined && cameraPeers[peer.id] === peer
                }
                connectedPeers.forEach(::createCameraOffer)
                if (connectedPeers.isNotEmpty()) {
                    updateStatus("Camera resumed; refreshing media for ${connectedPeers.size} Monitor(s).")
                } else {
                    updateStatus("Camera resumed; waiting for Monitor to connect.")
                }
                DiagnosticLog.log(
                    role,
                    "camera_foreground_resume_recovery_completed",
                    "capture_restarted=${captureStalled}; signaling_peers_refreshed=${connectedPeers.size}",
                )
            } finally {
                cameraRecoveryJob = null
            }
        }
    }

    fun attachRenderer(renderer: SurfaceViewRenderer, local: Boolean) {
        renderer.init(eglBase.eglBaseContext, null)
        renderer.setEnableHardwareScaler(true)
        renderer.setMirror(false)
        if (local) {
            localRenderer = renderer
            localVideoTrack?.addSink(renderer)
        } else {
            remoteRenderer = renderer
            remoteVideoTrack?.addSink(renderer)
        }
    }

    fun detachRenderer(renderer: SurfaceViewRenderer, local: Boolean) {
        if (local) {
            localVideoTrack?.removeSink(renderer)
            if (localRenderer === renderer) localRenderer = null
        } else {
            remoteVideoTrack?.removeSink(renderer)
            if (remoteRenderer === renderer) remoteRenderer = null
        }
        runCatching { renderer.release() }
    }

    fun showMessage(message: String) {
        updateStatus(message)
    }

    fun startCamera() {
        if (role != AppRole.CAMERA || isBusy || sessionActive || disposed) return
        DiagnosticLog.log(role, "camera_start_requested")
        shutdownRequested = false
        controllerHasWork = true
        updateUi {
            isBusy = true
            status = "Starting camera and local signaling…"
        }
        if (!beginForegroundExecution()) return
        cameraStartJob = scope.launch {
            try {
                ensurePeerConnectionFactory()
                startLocalMedia()
                DiagnosticLog.log(role, "camera_local_media_started", "video_width=$VIDEO_WIDTH; video_height=$VIDEO_HEIGHT; fps=$VIDEO_FPS; audio_track_created=true")
                val token = PairingToken.generate()
                val cameraId = CameraIdentityStore.getOrCreate(appContext)
                startCameraSignalingServer(token)
                val addresses = LocalLanAddress.privateIpv4Addresses()
                val nsd = LocalCameraNsd(appContext)
                localCameraNsd = nsd
                nsd.startAdvertising(cameraId, SIGNAL_PORT) { message ->
                    DiagnosticLog.log(AppRole.CAMERA, "camera_discovery_status", message)
                    updateUi { discoveryStatus = message }
                }
                val payload = if (addresses.isNotEmpty()) {
                    runCatching {
                        CameraPairingCodec.encode(
                            CameraPairingPayload(
                                cameraId = cameraId,
                                addresses = addresses.distinct().take(8),
                                port = SIGNAL_PORT,
                                token = token,
                            ),
                        )
                    }.getOrElse { error ->
                        throw IllegalStateException("Could not prepare pairing QR: ${error.message}", error)
                    }
                } else {
                    ""
                }
                DiagnosticLog.log(
                    role,
                    "camera_session_ready",
                    "private_ipv4_addresses=${addresses.distinct().joinToString(",")}; signaling_port=$SIGNAL_PORT; qr_available=${payload.isNotBlank()}",
                )
                updateUi {
                    pairingToken = token
                    pairingPayload = payload
                    cameraAddresses = addresses
                    sessionActive = true
                    isBusy = false
                    status = if (addresses.isEmpty()) {
                        "Signaling is listening; connect both devices to the same Wi-Fi."
                    } else {
                        "Camera ready. Waiting for Monitor."
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                DiagnosticLog.log(
                    role,
                    "camera_start_failed",
                    "exception=${error.javaClass.simpleName}; message=${error.message.orEmpty()}",
                )
                cleanupSession()
                updateUi {
                    isBusy = false
                    sessionActive = false
                    pairingPayload = ""
                    pairingToken = ""
                    cameraAddresses = emptyList()
                    discoveryStatus = "Not active"
                    status = "Could not start Camera: ${error.message ?: error.javaClass.simpleName}"
                }
                SessionControllerStore.onSessionEnded(this@LanWebRtcSession)
            }
        }
    }

    fun discoverPairedCamera(cameraId: String) {
        if (role != AppRole.MONITOR || disposed || shutdownRequested) return
        DiagnosticLog.log(role, "monitor_discovery_requested", "paired_identity_present=${cameraId.isNotBlank()}")
        discoveredCameraAddress = ""
        discoveredCameras = emptyList()
        discoveryStatus = "Starting local discovery…"
        val nsd = localCameraNsd ?: LocalCameraNsd(appContext).also { localCameraNsd = it }
        nsd.startDiscovery(
            expectedCameraId = cameraId,
            onFound = { camera ->
                DiagnosticLog.log(AppRole.MONITOR, "monitor_camera_discovered", "camera_id=${camera.cameraId}; host=${camera.address}")
                monitorTargetHost = camera.address
                updateUi {
                    discoveredCameras = listOf(camera)
                    discoveredCameraAddress = camera.address
                    discoveryStatus = "Paired Camera found at ${camera.address}."
                    if (sessionActive && monitorSender == null) {
                        status = "Paired Camera found at ${camera.address}; reconnecting."
                    }
                }
            },
            onLost = { lostCameraId ->
                if (lostCameraId == cameraId) {
                    DiagnosticLog.log(AppRole.MONITOR, "monitor_camera_advertisement_lost", "camera_id=$lostCameraId")
                    updateUi {
                        discoveredCameras = discoveredCameras.filterNot { it.cameraId == lostCameraId }
                        discoveredCameraAddress = ""
                        discoveryStatus = "Paired Camera advertisement was lost; continuing to search."
                    }
                }
            },
            onStatus = { message ->
                DiagnosticLog.log(AppRole.MONITOR, "monitor_discovery_status", message)
                updateUi { discoveryStatus = message }
            },
        )
    }

    fun discoverCameras() {
        if (role != AppRole.MONITOR || disposed || shutdownRequested || isBusy || sessionActive) return
        DiagnosticLog.log(role, "monitor_open_discovery_requested")
        discoveredCameraAddress = ""
        discoveredCameras = emptyList()
        discoveryStatus = "Starting local search…"
        val nsd = localCameraNsd ?: LocalCameraNsd(appContext).also { localCameraNsd = it }
        nsd.startDiscovery(
            expectedCameraId = null,
            onFound = { camera ->
                DiagnosticLog.log(AppRole.MONITOR, "monitor_camera_discovered", "camera_id=${camera.cameraId}; host=${camera.address}; mode=open_search")
                updateUi {
                    discoveredCameras = (discoveredCameras.filterNot { it.cameraId == camera.cameraId } + camera)
                        .sortedBy { it.address }
                    discoveryStatus = "Found ${discoveredCameras.size} Camera(s) on the local network."
                }
            },
            onLost = { lostCameraId ->
                updateUi {
                    discoveredCameras = discoveredCameras.filterNot { it.cameraId == lostCameraId }
                    discoveryStatus = "A Camera disappeared. Continuing to search…"
                }
            },
            onStatus = { message ->
                DiagnosticLog.log(AppRole.MONITOR, "monitor_discovery_status", message)
                updateUi { discoveryStatus = message }
            },
        )
    }

    fun stopCameraDiscovery() {
        if (role != AppRole.MONITOR) return
        DiagnosticLog.log(role, "monitor_discovery_stopped_by_app")
        runCatching { localCameraNsd?.stopDiscovery() }
        updateUi {
            discoveredCameraAddress = ""
            discoveredCameras = emptyList()
            discoveryStatus = "Stopped"
        }
    }

    fun connectToCamera(host: String, token: String) {
        if (role != AppRole.MONITOR || isBusy || sessionActive || disposed) return
        val requestedHost = host.trim()
        if (!LocalLanAddress.isPrivateIpv4(requestedHost)) {
            DiagnosticLog.log(role, "monitor_connect_rejected", "reason=invalid_private_ipv4; entered_host=$requestedHost")
            updateStatus("Enter the Camera's private IPv4 address, for example 192.168.1.20.")
            return
        }
        val requestedToken = token.trim()
        val connectionMode = if (requestedToken.isBlank()) "open_lan" else "paired_token"

        DiagnosticLog.log(role, "monitor_connect_requested", "host=$requestedHost; mode=$connectionMode")
        shutdownRequested = false
        controllerHasWork = true
        movementDetector.reset()
        lastMovementAnalysisAtMs = 0L
        movementAnalysisReady = false
        lastMovementAnalysisError = null
        lastLoggedVideoHealth = null
        updateUi { movementStatus = "Waiting for Monitor video." }
        runCatching { MovementAlertNotifier.ensureChannel(appContext) }
        monitorTargetHost = requestedHost
        monitorAuthenticationRejected = false
        monitorConnectionStartedAtMs = 0L
        monitorConnectionJob?.cancel()
        updateUi {
            isBusy = true
            // Keep Stop available while a connection is being retried.
            sessionActive = true
            status = "Connecting to Camera signaling…"
        }
        if (!beginForegroundExecution()) return
        monitorConnectionJob = scope.launch {
            try {
                ensurePeerConnectionFactory()
                runMonitorConnectionLoop(requestedHost, requestedToken)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                DiagnosticLog.log(
                    role,
                    "monitor_prepare_failed",
                    "exception=${error.javaClass.simpleName}; message=${error.message.orEmpty()}",
                )
                if (!shutdownRequested && !disposed) {
                    cleanupSession()
                    updateUi {
                        isBusy = false
                        sessionActive = false
                        status = "Could not prepare Monitor media: ${error.message ?: error.javaClass.simpleName}"
                    }
                    SessionControllerStore.onSessionEnded(this@LanWebRtcSession)
                }
            }
        }
    }

    /**
     * Keeps the user's pairing intent alive while the Camera is temporarily unreachable.
     * Every attempt uses the latest identity-validated discovery address, falling back to
     * the original QR/manual address until discovery supplies a better endpoint.
     */
    private suspend fun runMonitorConnectionLoop(initialHost: String, token: String) {
        var connectedAtLeastOnce = false
        var retryDelayMs = MonitorReconnectPolicy.INITIAL_DELAY_MS
        var attemptNumber = 0

        try {
            while (
                currentCoroutineContext().isActive &&
                !shutdownRequested &&
                !disposed &&
                !monitorAuthenticationRejected
            ) {
                val host = monitorTargetHost
                    ?.takeIf { LocalLanAddress.isPrivateIpv4(it) }
                    ?: initialHost
                attemptNumber += 1
                DiagnosticLog.log(
                    role,
                    "monitor_connection_attempt",
                    "attempt=$attemptNumber; host=$host; previous_retry_delay_ms=$retryDelayMs; connected_before=$connectedAtLeastOnce; network=${DiagnosticLog.networkSummary(appContext)}",
                )
                updateUi {
                    isBusy = !connectedAtLeastOnce
                    sessionActive = true
                    status = if (connectedAtLeastOnce) {
                        "Reconnecting to paired Camera at $host…"
                    } else {
                        "Connecting to Camera at $host…"
                    }
                }

                var attemptClient: HttpClient? = null
                try {
                    val client = HttpClient(ClientCIO) { install(ClientWebSockets) }
                    attemptClient = client
                    monitorClient = client
                    DiagnosticLog.log(role, "monitor_websocket_connecting", "attempt=$attemptNumber; host=$host; port=$SIGNAL_PORT")
                    client.webSocket(urlString = "ws://$host:$SIGNAL_PORT/signal") {
                        DiagnosticLog.log(role, "monitor_websocket_opened", "attempt=$attemptNumber; host=$host")
                        val outbound = Channel<String>(Channel.UNLIMITED)
                        val reconnectSignal = Channel<Unit>(Channel.CONFLATED)
                        val sender: (String) -> Unit = { raw ->
                            outbound.trySend(raw)
                            Unit
                        }
                        val requestReconnect: () -> Unit = {
                            reconnectSignal.trySend(Unit)
                            Unit
                        }
                        monitorSender = sender
                        monitorReconnectRequester = requestReconnect
                        val writer = launch {
                            for (raw in outbound) send(Frame.Text(raw))
                        }
                        var recoveryRequested = false
                        try {
                            outbound.trySend(
                                JSONObject().put("type", "join").put("token", token).toString(),
                            )
                            connectedAtLeastOnce = true
                            monitorConnectionStartedAtMs = SystemClock.elapsedRealtime()
                            DiagnosticLog.log(role, "monitor_join_sent", "attempt=$attemptNumber; join_message_sent=true")
                            updateUi {
                                isBusy = false
                                sessionActive = true
                                status = if (token.isBlank()) {
                                    "Connected on the local network; waiting for Camera media."
                                } else {
                                    "Connected to signaling; waiting for Camera media."
                                }
                            }
                            startMonitorHealthLoop()

                            coroutineScope {
                                val reader = launch {
                                    for (frame in incoming) {
                                        if (frame is Frame.Text) {
                                            handleSignalMessage(JSONObject(frame.readText()))
                                        }
                                    }
                                }
                                select<Unit> {
                                    reader.onJoin { }
                                    reconnectSignal.onReceive { recoveryRequested = true }
                                }
                                reader.cancelAndJoin()
                            }
                        } finally {
                            if (monitorSender === sender) monitorSender = null
                            if (monitorReconnectRequester === requestReconnect) {
                                monitorReconnectRequester = null
                            }
                            reconnectSignal.close()
                            outbound.close()
                            writer.cancel()
                        }
                        DiagnosticLog.log(
                            role,
                            "monitor_websocket_loop_ended",
                            "attempt=$attemptNumber; recovery_requested=$recoveryRequested",
                        )
                        if (recoveryRequested && !shutdownRequested && !disposed) {
                            updateStatus("Media transport stalled; reconnecting to the paired Camera.")
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    DiagnosticLog.log(
                        role,
                        "monitor_connection_attempt_failed",
                        "attempt=$attemptNumber; exception=${error.javaClass.simpleName}; message=${error.message.orEmpty()}",
                    )
                    if (!shutdownRequested && !disposed && !monitorAuthenticationRejected) {
                        updateUi {
                            isBusy = false
                            sessionActive = true
                            status = if (connectedAtLeastOnce) {
                                "Signaling interrupted: ${error.message ?: error.javaClass.simpleName}. Retrying."
                            } else {
                                "Camera not reachable: ${error.message ?: error.javaClass.simpleName}. Retrying."
                            }
                        }
                    }
                } finally {
                    // Reset backoff only after media actually flowed. A WebSocket upgrade alone
                    // can be immediately rejected while the Camera is still cleaning up a peer.
                    val hadDecodedFrames = lastRemoteVideoFrameAtMs > 0L
                    DiagnosticLog.log(
                        role,
                        "monitor_connection_attempt_cleanup",
                        "attempt=$attemptNumber; had_decoded_frames=$hadDecodedFrames",
                    )
                    if (monitorClient === attemptClient) monitorClient = null
                    runCatching { attemptClient?.close() }
                    monitorSender = null
                    monitorReconnectRequester = null
                    healthJob?.cancel()
                    healthJob = null
                    closePeerConnection()
                    if (hadDecodedFrames) {
                        retryDelayMs = MonitorReconnectPolicy.INITIAL_DELAY_MS
                    }
                }

                if (
                    !currentCoroutineContext().isActive ||
                    shutdownRequested ||
                    disposed ||
                    monitorAuthenticationRejected
                ) break

                updateUi {
                    isBusy = false
                    sessionActive = true
                    status = if (connectedAtLeastOnce) {
                        "Connection interrupted; rediscovering and retrying…"
                    } else {
                        "Camera not reachable; retrying…"
                    }
                }
                DiagnosticLog.log(role, "monitor_retry_scheduled", "attempt=$attemptNumber; delay_ms=$retryDelayMs")
                delay(retryDelayMs)
                retryDelayMs = MonitorReconnectPolicy.nextDelayMs(retryDelayMs)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            monitorSender = null
            monitorReconnectRequester = null
            healthJob?.cancel()
            healthJob = null
            runCatching { monitorClient?.close() }
            monitorClient = null
            closePeerConnection()
            if (!shutdownRequested && !disposed) {
                DiagnosticLog.log(
                    role,
                    "monitor_connection_loop_ended",
                    "pairing_rejected=$monitorAuthenticationRejected",
                )
                val terminalStatus = if (monitorAuthenticationRejected) {
                    "Pairing rejected. Check the Camera QR code and token, then reconnect."
                } else {
                    "Camera connection ended."
                }
                cleanupSession()
                updateUi {
                    isBusy = false
                    sessionActive = false
                    status = terminalStatus
                }
                SessionControllerStore.onSessionEnded(this@LanWebRtcSession)
            }
        }
    }

    fun stopSession() = stopSessionWithStatus("Stopped.")

    private fun stopSessionWithStatus(finalStatus: String) {
        if (shutdownRequested || disposed || !controllerHasWork) return
        DiagnosticLog.log(role, "session_stop_requested", "reason=$finalStatus")
        shutdownRequested = true
        ActiveSessionRegistry.unregister(this)
        val startupJob = cameraStartJob
        cameraStartJob = null
        startupJob?.cancel()
        SessionForegroundService.stop(appContext)
        val connectionJob = monitorConnectionJob
        monitorConnectionJob = null
        connectionJob?.cancel()
        monitorReconnectRequester = null
        updateUi {
            isBusy = true
            status = "Stopping session…"
        }
        scope.launch {
            startupJob?.join()
            connectionJob?.join()
            cleanupSession()
            updateUi {
                isBusy = false
                sessionActive = false
                cameraAddresses = emptyList()
                pairingToken = ""
                pairingPayload = ""
                discoveryStatus = "Stopped"
                discoveredCameraAddress = ""
                status = finalStatus
            }
            DiagnosticLog.log(role, "session_stop_completed", "result=$finalStatus")
            SessionControllerStore.onSessionEnded(this@LanWebRtcSession)
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        shutdownRequested = true
        controllerHasWork = false
        ActiveSessionRegistry.unregister(this)
        SessionForegroundService.stop(appContext)
        val startupJob = cameraStartJob
        cameraStartJob = null
        startupJob?.cancel()
        val connectionJob = monitorConnectionJob
        monitorConnectionJob = null
        connectionJob?.cancel()
        monitorReconnectRequester = null
        scope.launch {
            startupJob?.join()
            connectionJob?.join()
            cleanupSession()
            releaseControllerResources()
            scope.cancel()
        }
    }

    /**
     * Releases WebRTC factory/EGL resources after [cleanupSession] already completed.
     */
    internal fun disposeAfterCleanup() {
        if (disposed) return
        disposed = true
        shutdownRequested = true
        controllerHasWork = false
        ActiveSessionRegistry.unregister(this)
        SessionForegroundService.stop(appContext)
        scope.launch {
            releaseControllerResources()
            scope.cancel()
        }
    }

    private suspend fun releaseControllerResources() {
        runCatching { peerConnectionFactory?.dispose() }
        peerConnectionFactory = null
        runCatching { eglBase.release() }
    }

    private fun beginForegroundExecution(): Boolean {
        DiagnosticLog.log(role, "foreground_execution_requested")
        val admissionError = SessionControllerStore.admissionError(this)
        if (admissionError != null) {
            DiagnosticLog.log(role, "foreground_execution_rejected", "reason=session_admission; message=$admissionError")
            controllerHasWork = false
            shutdownRequested = true
            updateUi {
                isBusy = false
                sessionActive = false
                status = admissionError
            }
            return false
        }
        ActiveSessionRegistry.register(
            owner = this,
            onStop = { stopSession() },
            onFailure = { message -> stopSessionWithStatus(message) },
        )
        val error = SessionForegroundService.start(appContext, role)
        if (error == null) {
            DiagnosticLog.log(role, "foreground_service_started")
            return true
        }
        DiagnosticLog.log(role, "foreground_service_start_failed", "message=$error")

        ActiveSessionRegistry.unregister(this)
        controllerHasWork = false
        shutdownRequested = true
        updateUi {
            isBusy = false
            sessionActive = false
            status = error
        }
        return false
    }

    private suspend fun ensurePeerConnectionFactory() {
        if (peerConnectionFactory != null) return
        WebRtcInitializer.ensure(appContext)
        val factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
        peerConnectionFactory = factory
    }

    private suspend fun startLocalMedia() {
        val factory = peerConnectionFactory ?: error("WebRTC is not initialized")
        var createdCapturer: CameraVideoCapturer? = null
        var createdHelper: SurfaceTextureHelper? = null
        var createdSource: VideoSource? = null
        var createdVideoTrack: VideoTrack? = null
        var createdAudioSource: org.webrtc.AudioSource? = null
        var createdAudioTrack: AudioTrack? = null
        var captureStartAttempted = false
        var ownershipTransferred = false

        try {
            val enumerator = Camera2Enumerator(appContext)
            val cameraName = enumerator.deviceNames.firstOrNull { enumerator.isBackFacing(it) }
                ?: error("No rear camera is available on this device.")
            val capturer = enumerator.createCapturer(cameraName, null)
                ?: error("Could not initialize the rear camera.")
            createdCapturer = capturer

            val helper = SurfaceTextureHelper.create("LanMonitorCameraCapture", eglBase.eglBaseContext)
                ?: error("Could not initialize the camera texture helper.")
            createdHelper = helper

            val source = factory.createVideoSource(false)
            createdSource = source
            capturer.initialize(helper, appContext, source.capturerObserver)
            // Mark this before the call: startCapture() can fail after partially acquiring the camera.
            captureStartAttempted = true
            capturer.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)

            val videoTrack = factory.createVideoTrack("lan-monitor-video", source)
            createdVideoTrack = videoTrack
            videoTrack.addSink(localVideoHealthSink)
            videoTrack.setEnabled(true)
            val audioSource = factory.createAudioSource(MediaConstraints())
            createdAudioSource = audioSource
            val audioTrack = factory.createAudioTrack("lan-monitor-audio", audioSource)
            createdAudioTrack = audioTrack
            audioTrack.setEnabled(true)

            localRenderer?.let(videoTrack::addSink)

            // Publish ownership only after every startup step above has succeeded. If any step
            // throws, the finally block releases every resource created by this attempt.
            cameraCapturer = capturer
            surfaceTextureHelper = helper
            videoSource = source
            localVideoTrack = videoTrack
            this.audioSource = audioSource
            localAudioTrack = audioTrack
            ownershipTransferred = true
        } finally {
            if (!ownershipTransferred) {
                localRenderer?.let { renderer ->
                    runCatching { createdVideoTrack?.removeSink(renderer) }
                }
                if (captureStartAttempted) {
                    runCatching { createdCapturer?.stopCapture() }
                }
                runCatching { createdVideoTrack?.removeSink(localVideoHealthSink) }
                runCatching { createdAudioTrack?.dispose() }
                runCatching { createdAudioSource?.dispose() }
                runCatching { createdVideoTrack?.dispose() }
                runCatching { createdSource?.dispose() }
                runCatching { createdHelper?.dispose() }
                runCatching { createdCapturer?.dispose() }
            }
        }
    }

    private fun startCameraSignalingServer(token: String) {
        DiagnosticLog.log(role, "camera_signaling_server_starting", "port=$SIGNAL_PORT")
        cameraServer = embeddedServer(
            factory = ServerCIO,
            host = "0.0.0.0",
            port = SIGNAL_PORT,
        ) {
            install(ServerWebSockets)
            routing {
                webSocket("/signal") {
                    // Use the numeric socket peer address. Ktor's remoteHost can reverse-resolve
                    // this same LAN peer to a hostname, which is not valid input for the IPv4 guard.
                    val remoteAddress = call.request.local.remoteAddress
                    if (!LocalLanAddress.isPrivateIpv4(remoteAddress)) {
                        DiagnosticLog.log(role, "camera_socket_rejected", "reason=non_private_ipv4; remote_address=$remoteAddress")
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Local private IPv4 clients only"))
                        return@webSocket
                    }

                    val outbound = Channel<String>(Channel.UNLIMITED)
                    val sender: (String) -> Unit = { raw ->
                        outbound.trySend(raw)
                        Unit
                    }
                    val peer = CameraMonitorPeer(
                        id = java.util.UUID.randomUUID().toString(),
                        remoteAddress = remoteAddress,
                        sender = sender,
                    )
                    if (!admitCameraPeer(peer)) {
                        DiagnosticLog.log(
                            role,
                            "camera_socket_rejected",
                            "reason=monitor_capacity_reached; max_monitors=${CameraPeerAdmissionPolicy.MAX_MONITORS}; remote_address=$remoteAddress",
                        )
                        outbound.close()
                        close(
                            CloseReason(
                                CloseReason.Codes.VIOLATED_POLICY,
                                "Camera already has ${CameraPeerAdmissionPolicy.MAX_MONITORS} connected Monitors.",
                            ),
                        )
                        return@webSocket
                    }

                    DiagnosticLog.log(
                        role,
                        "camera_socket_opened",
                        "peer_id=${peer.id}; remote_address=$remoteAddress; reserved_slots=${cameraPeerAdmission.activeCount()}",
                    )
                    val writer = launch {
                        for (raw in outbound) send(Frame.Text(raw))
                    }
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val message = try {
                                JSONObject(frame.readText())
                            } catch (_: Exception) {
                                DiagnosticLog.log(role, "camera_signaling_message_malformed", "peer_id=${peer.id}")
                                outbound.trySend(JSONObject().put("type", "error").put("message", "Malformed signaling message.").toString())
                                continue
                            }

                            if (!peer.joined) {
                                val suppliedToken = message.optString("token")
                                val joinMode = CameraJoinPolicy.resolve(
                                    messageType = message.optString("type"),
                                    expectedToken = token,
                                    suppliedToken = suppliedToken,
                                )
                                if (joinMode == null) {
                                    DiagnosticLog.log(role, "camera_join_rejected", "peer_id=${peer.id}; message_type=${message.optString("type", "unknown")}; reason=invalid_join_or_token")
                                    outbound.trySend(JSONObject().put("type", "error").put("message", "Invalid pairing token.").toString())
                                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid pairing token"))
                                    break
                                }
                                peer.joined = true
                                updateCameraMonitorCount()
                                DiagnosticLog.log(
                                    role,
                                    "camera_join_accepted",
                                    "peer_id=${peer.id}; remote_address=$remoteAddress; mode=${joinMode.name.lowercase()}",
                                )
                                outbound.trySend(JSONObject().put("type", "ready").toString())
                                updateStatus("Monitor connected (${currentJoinedMonitorCount()}/${CameraPeerAdmissionPolicy.MAX_MONITORS}); negotiating WebRTC.")
                                scope.launch { createCameraOffer(peer) }
                            } else {
                                handleSignalMessage(message, cameraPeer = peer)
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        DiagnosticLog.log(
                            role,
                            "camera_signaling_socket_error",
                            "peer_id=${peer.id}; exception=${error.javaClass.simpleName}; message=${error.message.orEmpty()}",
                        )
                        if (!shutdownRequested && !disposed) {
                            updateStatus("Monitor signaling error: ${error.message ?: error.javaClass.simpleName}")
                        }
                    } finally {
                        DiagnosticLog.log(
                            role,
                            "camera_socket_closed",
                            "peer_id=${peer.id}; joined=${peer.joined}; remote_address=$remoteAddress",
                        )
                        outbound.close()
                        writer.cancel()
                        releaseCameraPeer(peer)
                    }
                }
            }
        }.start(wait = false)
        DiagnosticLog.log(role, "camera_signaling_server_started", "port=$SIGNAL_PORT")
    }

    private fun createCameraOffer() {
        DiagnosticLog.log(role, "webrtc_offer_creation_started")
        try {
            resetDescriptionState()
            val connection = createPeerConnection(isCamera = true)
            connection.createOffer(
                createSdpObserver(
                    onCreateSuccess = { description ->
                        connection.setLocalDescription(
                            createSdpObserver(
                                onSetSuccess = { publishLocalDescription("offer", description) },
                                onFailure = {
                                    DiagnosticLog.log(role, "webrtc_offer_set_local_failed", "error=$it")
                                    updateStatus("Could not set local offer: $it")
                                },
                            ),
                            description,
                        )
                    },
                    onFailure = {
                        DiagnosticLog.log(role, "webrtc_offer_creation_failed", "error=$it")
                        updateStatus("Could not create offer: $it")
                    },
                ),
                MediaConstraints(),
            )
        } catch (error: Throwable) {
            DiagnosticLog.log(
                role,
                "webrtc_offer_start_failed",
                "exception=${error.javaClass.simpleName}; message=${error.message.orEmpty()}",
            )
            updateStatus("Could not start WebRTC offer: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun createPeerConnection(isCamera: Boolean): PeerConnection {
        DiagnosticLog.log(role, "webrtc_peer_connection_create", "local_camera_role=$isCamera")
        closePeerConnection()
        resetDescriptionState()
        val factory = peerConnectionFactory ?: error("WebRTC is not initialized")
        val configuration = PeerConnection.RTCConfiguration(emptyList())
        val connection = factory.createPeerConnection(configuration, peerConnectionObserver())
            ?: error("Could not create WebRTC peer connection.")
        peerConnection = connection
        if (isCamera) {
            connection.addTrack(localVideoTrack ?: error("Camera video is not ready."), listOf(STREAM_ID))
            connection.addTrack(localAudioTrack ?: error("Camera microphone is not ready."), listOf(STREAM_ID))
        }
        return connection
    }

    private fun peerConnectionObserver() = object : PeerConnection.Observer {
        override fun onSignalingChange(newState: PeerConnection.SignalingState) {
            DiagnosticLog.log(role, "webrtc_signaling_state", "state=$newState")
        }

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
            DiagnosticLog.log(role, "webrtc_ice_connection_state", "state=$newState")
            when (newState) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> {
                    if (role == AppRole.CAMERA) {
                        updateStatus("Transport connected; waiting for Monitor media confirmation.")
                    } else {
                        updateStatus("Transport connected; waiting for decoded video frames.")
                    }
                }
                PeerConnection.IceConnectionState.DISCONNECTED ->
                    updateStatus("Transport disconnected; attempting to recover.")
                PeerConnection.IceConnectionState.FAILED -> {
                    updateStatus("WebRTC connection failed; reconnecting to the paired Camera.")
                    if (role == AppRole.MONITOR) monitorReconnectRequester?.invoke()
                }
                PeerConnection.IceConnectionState.CLOSED -> Unit
                else -> updateStatus("Establishing direct LAN media connection…")
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {
            DiagnosticLog.log(role, "webrtc_ice_receiving_changed", "receiving=$receiving")
        }
        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
            DiagnosticLog.log(role, "webrtc_ice_gathering_state", "state=$newState")
        }

        override fun onIceCandidate(candidate: IceCandidate) {
            val candidateType = candidate.sdp.substringAfter(" typ ", "unknown").substringBefore(' ')
            DiagnosticLog.log(role, "webrtc_local_ice_candidate", "type=$candidateType; mid_present=${candidate.sdpMid != null}; line=${candidate.sdpMLineIndex}")
            val shouldSend: Boolean
            synchronized(candidateLock) {
                shouldSend = localDescriptionSignaled
                if (!shouldSend) pendingLocalCandidates.add(candidate)
            }
            if (shouldSend) sendIceCandidate(candidate)
        }

        override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) {
            stream.audioTracks.forEach(::attachRemoteAudioTrack)
            stream.videoTracks.firstOrNull()?.let(::attachRemoteVideoTrack)
        }
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(dataChannel: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit

        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<MediaStream>) {
            when (val track = receiver.track()) {
                is VideoTrack -> attachRemoteVideoTrack(track)
                is AudioTrack -> attachRemoteAudioTrack(track)
            }
        }

        override fun onTrack(transceiver: RtpTransceiver) {
            when (val track = transceiver.receiver.track()) {
                is VideoTrack -> attachRemoteVideoTrack(track)
                is AudioTrack -> attachRemoteAudioTrack(track)
            }
        }
    }

    private fun attachRemoteAudioTrack(track: AudioTrack) {
        DiagnosticLog.log(role, "webrtc_remote_audio_track_received")
        synchronized(remoteAudioLock) {
            remoteAudioTrack = track
            track.setEnabled(!remoteAudioMuteState.isMuted)
        }
    }

    private fun attachRemoteVideoTrack(track: VideoTrack) {
        DiagnosticLog.log(role, "webrtc_remote_video_track_received")
        if (remoteVideoTrack !== track) {
            remoteVideoTrack?.removeSink(remoteVideoHealthSink)
            remoteRenderer?.let { renderer -> remoteVideoTrack?.removeSink(renderer) }
            remoteVideoTrack = track
            track.setEnabled(true)
            track.addSink(remoteVideoHealthSink)
            remoteRenderer?.let(track::addSink)
        }
        updateStatus("Video track received; waiting for decoded frames.")
    }

    private fun handleSignalMessage(message: JSONObject) {
        val signalType = message.optString("type", "unknown")
        if (signalType != "health" && signalType != "candidate") {
            DiagnosticLog.log(role, "signaling_message_received", "type=$signalType")
        }
        when (signalType) {
            "ready" -> updateStatus("Pairing accepted; waiting for media negotiation.")
            "offer" -> handleRemoteOffer(message.optString("sdp"))
            "answer" -> handleRemoteAnswer(message.optString("sdp"))
            "candidate" -> handleRemoteCandidate(message)
            "health" -> {
                if (role == AppRole.CAMERA) {
                    if (message.optBoolean("videoHealthy", false)) {
                        updateStatus("Streaming: Monitor confirms decoded video frames.")
                    } else {
                        updateStatus("Media not yet confirmed: Monitor reports no recent decoded video frames.")
                    }
                }
            }
            "error" -> {
                val errorMessage = message.optString("message", "Unknown error")
                if (role == AppRole.MONITOR &&
                    errorMessage.contains("invalid pairing token", ignoreCase = true)
                ) {
                    monitorAuthenticationRejected = true
                }
                DiagnosticLog.log(role, "signaling_peer_error", "message=$errorMessage")
                updateStatus("Signaling error: $errorMessage")
            }
        }
    }

    private fun handleRemoteOffer(sdp: String) {
        if (role != AppRole.MONITOR || sdp.isBlank()) {
            DiagnosticLog.log(role, "webrtc_offer_rejected", "reason=wrong_role_or_empty_sdp")
            return
        }
        DiagnosticLog.log(role, "webrtc_remote_offer_received")
        try {
            val connection = createPeerConnection(isCamera = false)
            val offer = SessionDescription(SessionDescription.Type.OFFER, sdp)
            connection.setRemoteDescription(
                createSdpObserver(
                    onSetSuccess = {
                        markRemoteDescriptionSet()
                        connection.createAnswer(
                            createSdpObserver(
                                onCreateSuccess = { answer ->
                                    connection.setLocalDescription(
                                        createSdpObserver(
                                            onSetSuccess = { publishLocalDescription("answer", answer) },
                                            onFailure = {
                                                DiagnosticLog.log(role, "webrtc_answer_set_local_failed", "error=$it")
                                                updateStatus("Could not set local answer: $it")
                                            },
                                        ),
                                        answer,
                                    )
                                },
                                onFailure = {
                                    DiagnosticLog.log(role, "webrtc_answer_creation_failed", "error=$it")
                                    updateStatus("Could not create answer: $it")
                                },
                            ),
                            MediaConstraints(),
                        )
                    },
                    onFailure = {
                        DiagnosticLog.log(role, "webrtc_remote_offer_rejected", "error=$it")
                        updateStatus("Could not accept Camera offer: $it")
                    },
                ),
                offer,
            )
            updateStatus("Offer received; preparing answer.")
        } catch (error: Throwable) {
            DiagnosticLog.log(
                role,
                "webrtc_remote_offer_handling_failed",
                "exception=${error.javaClass.simpleName}; message=${error.message.orEmpty()}",
            )
            updateStatus("Could not handle offer: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun handleRemoteAnswer(sdp: String) {
        if (role != AppRole.CAMERA || sdp.isBlank()) {
            DiagnosticLog.log(role, "webrtc_answer_rejected", "reason=wrong_role_or_empty_sdp")
            return
        }
        DiagnosticLog.log(role, "webrtc_remote_answer_received")
        val connection = peerConnection ?: run {
            DiagnosticLog.log(role, "webrtc_remote_answer_rejected", "reason=peer_connection_missing")
            return
        }
        connection.setRemoteDescription(
            createSdpObserver(
                onSetSuccess = {
                    markRemoteDescriptionSet()
                    updateStatus("Answer accepted; confirming media progress.")
                },
                onFailure = {
                    DiagnosticLog.log(role, "webrtc_remote_answer_rejected", "error=$it")
                    updateStatus("Could not accept Monitor answer: $it")
                },
            ),
            SessionDescription(SessionDescription.Type.ANSWER, sdp),
        )
    }

    private fun handleRemoteCandidate(message: JSONObject) {
        val candidateText = message.optString("candidate")
        if (candidateText.isBlank()) {
            DiagnosticLog.log(role, "webrtc_remote_ice_candidate_rejected", "reason=empty_candidate")
            return
        }
        val candidateType = candidateText.substringAfter(" typ ", "unknown").substringBefore(' ')
        DiagnosticLog.log(role, "webrtc_remote_ice_candidate", "type=$candidateType; mid_present=${message.optString("sdpMid").isNotBlank()}")
        val mid = message.optString("sdpMid").takeIf { it.isNotBlank() }
        val candidate = IceCandidate(mid, message.optInt("sdpMLineIndex", 0), candidateText)
        val shouldAdd: Boolean
        synchronized(remoteCandidateLock) {
            shouldAdd = remoteDescriptionSet
            if (!shouldAdd) pendingRemoteCandidates.add(candidate)
        }
        if (shouldAdd) peerConnection?.addIceCandidate(candidate)
    }

    private fun markRemoteDescriptionSet() {
        val candidates: List<IceCandidate>
        synchronized(remoteCandidateLock) {
            remoteDescriptionSet = true
            candidates = pendingRemoteCandidates.toList()
            pendingRemoteCandidates.clear()
        }
        candidates.forEach { peerConnection?.addIceCandidate(it) }
    }

    private fun publishLocalDescription(type: String, description: SessionDescription) {
        DiagnosticLog.log(role, "webrtc_local_description_ready", "type=$type")
        val candidates: List<IceCandidate>
        synchronized(candidateLock) {
            localDescriptionSignaled = true
            candidates = pendingLocalCandidates.toList()
            pendingLocalCandidates.clear()
        }
        sendSignal(JSONObject().put("type", type).put("sdp", description.description))
        candidates.forEach(::sendIceCandidate)
    }

    private fun sendIceCandidate(candidate: IceCandidate) {
        sendSignal(
            JSONObject()
                .put("type", "candidate")
                .put("sdpMid", candidate.sdpMid ?: "")
                .put("sdpMLineIndex", candidate.sdpMLineIndex)
                .put("candidate", candidate.sdp),
        )
    }

    private fun sendSignal(message: JSONObject) {
        val sender = if (role == AppRole.CAMERA) cameraSender else monitorSender
        sender?.invoke(message.toString())
    }

    private fun startMonitorHealthLoop() {
        healthJob?.cancel()
        healthJob = scope.launch {
            while (isActive && !shutdownRequested && !disposed) {
                delay(HEALTH_INTERVAL_MS)
                if (role != AppRole.MONITOR || monitorSender == null) continue
                val nowMs = SystemClock.elapsedRealtime()
                val health = VideoFrameHealth.evaluate(
                    lastFrameAtMs = lastRemoteVideoFrameAtMs,
                    nowMs = nowMs,
                    stallTimeoutMs = VIDEO_STALL_TIMEOUT_MS,
                )
                sendSignal(
                    JSONObject()
                        .put("type", "health")
                        .put("videoHealthy", health.isHealthy)
                        .put("lastFrameAgeMs", health.frameAgeMs ?: -1L),
                )
                if (lastLoggedVideoHealth != health.isHealthy) {
                    lastLoggedVideoHealth = health.isHealthy
                    DiagnosticLog.log(
                        role,
                        "monitor_video_health_changed",
                        "healthy=${health.isHealthy}; frame_age_ms=${health.frameAgeMs ?: -1L}",
                    )
                }
                if (health.isHealthy) {
                    updateStatus("Streaming: decoded video frames are advancing.")
                } else if (health.frameAgeMs != null) {
                    updateStatus("Stream interrupted: no decoded video frames for ${health.frameAgeMs} ms.")
                } else if (peerConnection != null) {
                    updateStatus("Connected; waiting for the first decoded video frame.")
                }

                val lastFrameAtMs = lastRemoteVideoFrameAtMs
                val mediaAgeMs = if (lastFrameAtMs > 0L) {
                    nowMs - lastFrameAtMs
                } else {
                    nowMs - monitorConnectionStartedAtMs
                }
                if (
                    peerConnection != null &&
                    monitorConnectionStartedAtMs > 0L &&
                    mediaAgeMs >= MonitorReconnectPolicy.MEDIA_STALL_RECONNECT_MS
                ) {
                    DiagnosticLog.log(
                        role,
                        "monitor_media_stall_reconnect_requested",
                        "media_age_ms=$mediaAgeMs; timeout_ms=${MonitorReconnectPolicy.MEDIA_STALL_RECONNECT_MS}",
                    )
                    monitorReconnectRequester?.invoke()
                }
            }
        }
    }

    private fun createSdpObserver(
        onCreateSuccess: (SessionDescription) -> Unit = {},
        onSetSuccess: () -> Unit = {},
        onFailure: (String) -> Unit,
    ) = object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = onCreateSuccess.invoke(description)
        override fun onSetSuccess() = onSetSuccess.invoke()
        override fun onCreateFailure(error: String) = onFailure.invoke(error)
        override fun onSetFailure(error: String) = onFailure.invoke(error)
    }

    private fun closePeerConnection() {
        synchronized(candidateLock) {
            localDescriptionSignaled = false
            pendingLocalCandidates.clear()
        }
        synchronized(remoteCandidateLock) {
            remoteDescriptionSet = false
            pendingRemoteCandidates.clear()
        }
        val connection = peerConnection
        peerConnection = null
        remoteVideoTrack?.removeSink(remoteVideoHealthSink)
        remoteRenderer?.let { remoteVideoTrack?.removeSink(it) }
        remoteVideoTrack = null
        synchronized(remoteAudioLock) {
            remoteAudioTrack?.setEnabled(false)
            remoteAudioTrack = null
        }
        lastRemoteVideoFrameAtMs = 0L
        lastMovementAnalysisAtMs = 0L
        movementAnalysisReady = false
        movementDetector.resetBaseline()
        if (role == AppRole.MONITOR) {
            updateUi { movementStatus = "Waiting for Monitor video." }
        }
        runCatching { connection?.close() }
        runCatching { connection?.dispose() }
    }

    private suspend fun cleanupSession() {
        shutdownRequested = true
        val recoveryJob = cameraRecoveryJob
        cameraRecoveryJob = null
        recoveryJob?.cancelAndJoin()
        lastLocalVideoFrameAtMs = 0L
        ActiveSessionRegistry.unregister(this)
        SessionForegroundService.stop(appContext)
        healthJob?.cancel()
        healthJob = null
        runCatching { monitorClient?.close() }
        monitorClient = null
        runCatching { localCameraNsd?.close() }
        localCameraNsd = null
        runCatching { cameraServer?.stop(500, 1_000) }
        cameraServer = null
        monitorSender = null
        cameraSender = null
        cameraPeerJoined = false
        closePeerConnection()

        localRenderer?.let { renderer -> runCatching { localVideoTrack?.removeSink(renderer) } }
        runCatching { cameraCapturer?.stopCapture() }
        runCatching { cameraCapturer?.dispose() }
        cameraCapturer = null
        runCatching { surfaceTextureHelper?.dispose() }
        surfaceTextureHelper = null
        runCatching { videoSource?.dispose() }
        videoSource = null
        runCatching { localVideoTrack?.removeSink(localVideoHealthSink) }
        runCatching { localVideoTrack?.dispose() }
        localVideoTrack = null
        runCatching { localAudioTrack?.dispose() }
        localAudioTrack = null
        runCatching { audioSource?.dispose() }
        audioSource = null

        monitorTargetHost = null
        monitorConnectionStartedAtMs = 0L
        monitorAuthenticationRejected = false
        lastMovementAnalysisAtMs = 0L
        movementAnalysisReady = false
        lastMovementAnalysisError = null
        movementDetector.reset()
        remoteAudioMuteState.reset()
        serverConnectionClaimed.set(false)
        controllerHasWork = false
        updateUi {
            sessionActive = false
            isBusy = false
            movementStatus = "Not active."
            remoteAudioMuted = false
        }
    }

    private fun resetDescriptionState() {
        synchronized(candidateLock) {
            localDescriptionSignaled = false
            pendingLocalCandidates.clear()
        }
        synchronized(remoteCandidateLock) {
            remoteDescriptionSet = false
            pendingRemoteCandidates.clear()
        }
    }

    private fun updateStatus(value: String) = updateUi { status = value }

    private fun updateUi(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (!disposed || !isBusy) block()
        } else {
            mainHandler.post {
                if (!disposed || !isBusy) block()
            }
        }
    }

    companion object {
        const val SIGNAL_PORT = 8765
        private const val VIDEO_WIDTH = 640
        private const val VIDEO_HEIGHT = 480
        private const val VIDEO_FPS = 15
        private const val HEALTH_INTERVAL_MS = 1_000L
        private const val VIDEO_STALL_TIMEOUT_MS = 3_000L
        private const val STREAM_ID = "lan-monitor-session"
    }

    private object WebRtcInitializer {
        @Volatile private var initialized = false

        @Synchronized
        fun ensure(context: Context) {
            if (initialized) return
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions(),
            )
            initialized = true
        }
    }
}
