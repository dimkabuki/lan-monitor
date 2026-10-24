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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * M2 media/session controller. Signaling uses a single authenticated Monitor over the home LAN.
 * Media is carried directly by WebRTC; this controller does not configure STUN or TURN servers.
 */
class LanWebRtcSession(
    context: Context,
    private val role: AppRole,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val eglBase: EglBase = EglBase.create()
    private val serverConnectionClaimed = AtomicBoolean(false)
    private val candidateLock = Any()
    private val remoteCandidateLock = Any()

    var status by androidx.compose.runtime.mutableStateOf("Idle")
        private set
    var isBusy by androidx.compose.runtime.mutableStateOf(false)
        private set
    var sessionActive by androidx.compose.runtime.mutableStateOf(false)
        private set
    var cameraAddresses by androidx.compose.runtime.mutableStateOf(emptyList<String>())
        private set
    var pairingToken by androidx.compose.runtime.mutableStateOf("")
        private set
    var pairingPayload by androidx.compose.runtime.mutableStateOf("")
        private set

    @Volatile private var disposed = false
    @Volatile private var shutdownRequested = false
    @Volatile private var cameraSender: ((String) -> Unit)? = null
    @Volatile private var monitorSender: ((String) -> Unit)? = null
    @Volatile private var lastRemoteVideoFrameAtMs = 0L
    @Volatile private var localDescriptionSignaled = false
    @Volatile private var remoteDescriptionSet = false

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var cameraServer: EmbeddedServer<*, *>? = null
    private var monitorClient: HttpClient? = null
    private var monitorConnectionJob: Job? = null
    private var healthJob: Job? = null
    private var peerConnection: PeerConnection? = null
    private var cameraCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var audioSource: org.webrtc.AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    @Volatile private var localRenderer: SurfaceViewRenderer? = null
    @Volatile private var remoteRenderer: SurfaceViewRenderer? = null
    private val pendingLocalCandidates = mutableListOf<IceCandidate>()
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()

    private val remoteVideoHealthSink = object : VideoSink {
        override fun onFrame(frame: org.webrtc.VideoFrame) {
            lastRemoteVideoFrameAtMs = SystemClock.elapsedRealtime()
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
        shutdownRequested = false
        updateUi {
            isBusy = true
            status = "Starting camera and local signaling…"
        }
        scope.launch {
            try {
                ensurePeerConnectionFactory()
                startLocalMedia()
                val token = PairingToken.generate()
                startCameraSignalingServer(token)
                val addresses = LocalLanAddress.privateIpv4Addresses()
                val payload = if (addresses.isNotEmpty()) {
                    runCatching {
                        CameraPairingCodec.encode(
                            CameraPairingPayload(
                                cameraId = CameraIdentityStore.getOrCreate(appContext),
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
            } catch (error: Throwable) {
                cleanupSession()
                updateUi {
                    isBusy = false
                    sessionActive = false
                    pairingPayload = ""
                    pairingToken = ""
                    cameraAddresses = emptyList()
                    status = "Could not start Camera: ${error.message ?: error.javaClass.simpleName}"
                }
            }
        }
    }

    fun connectToCamera(host: String, token: String) {
        if (role != AppRole.MONITOR || isBusy || sessionActive || disposed) return
        if (!LocalLanAddress.isPrivateIpv4(host)) {
            updateStatus("Enter the Camera's private IPv4 address, for example 192.168.1.20.")
            return
        }
        if (token.isBlank()) {
            updateStatus("Enter the pairing token shown on the Camera device.")
            return
        }

        shutdownRequested = false
        updateUi {
            isBusy = true
            status = "Connecting to Camera signaling…"
        }
        monitorConnectionJob = scope.launch {
            try {
                ensurePeerConnectionFactory()
                val client = HttpClient(ClientCIO) { install(ClientWebSockets) }
                monitorClient = client
                client.webSocket(urlString = "ws://$host:$SIGNAL_PORT/signal") {
                    val outbound = Channel<String>(Channel.UNLIMITED)
                    val sender: (String) -> Unit = { raw ->
                        outbound.trySend(raw)
                        Unit
                    }
                    monitorSender = sender
                    val writer = launch {
                        for (raw in outbound) send(Frame.Text(raw))
                    }
                    outbound.trySend(JSONObject().put("type", "join").put("token", token).toString())
                    updateUi {
                        isBusy = false
                        sessionActive = true
                        status = "Connected to signaling; waiting for Camera."
                    }
                    startMonitorHealthLoop()
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                handleSignalMessage(JSONObject(frame.readText()))
                            }
                        }
                    } finally {
                        if (monitorSender === sender) monitorSender = null
                        outbound.close()
                        writer.cancel()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (!shutdownRequested && !disposed) {
                    updateUi {
                        isBusy = false
                        sessionActive = false
                        status = "Connection failed: ${error.message ?: error.javaClass.simpleName}"
                    }
                }
            } finally {
                if (!shutdownRequested && !disposed) {
                    closePeerConnection()
                    updateUi {
                        isBusy = false
                        sessionActive = false
                        if (!status.startsWith("Connection failed") && !status.startsWith("Signaling error")) {
                            status = "Signaling disconnected."
                        }
                    }
                }
            }
        }
    }

    fun stopSession() {
        shutdownRequested = true
        updateUi {
            isBusy = true
            status = "Stopping session…"
        }
        scope.launch {
            cleanupSession()
            updateUi {
                isBusy = false
                sessionActive = false
                cameraAddresses = emptyList()
                pairingToken = ""
                pairingPayload = ""
                status = "Stopped."
            }
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        shutdownRequested = true
        scope.launch {
            cleanupSession()
            runCatching { peerConnectionFactory?.dispose() }
            peerConnectionFactory = null
            runCatching { eglBase.release() }
            scope.cancel()
        }
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
        val enumerator = Camera2Enumerator(appContext)
        val cameraName = enumerator.deviceNames.firstOrNull { enumerator.isBackFacing(it) }
            ?: error("No rear camera is available on this device.")
        val capturer = enumerator.createCapturer(cameraName, null)
            ?: error("Could not initialize the rear camera.")
        val helper = SurfaceTextureHelper.create("LanMonitorCameraCapture", eglBase.eglBaseContext)
            ?: error("Could not initialize the camera texture helper.")
        val source = factory.createVideoSource(false)
        capturer.initialize(helper, appContext, source.capturerObserver)
        capturer.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)

        val videoTrack = factory.createVideoTrack("lan-monitor-video", source).apply {
            setEnabled(true)
        }
        val newAudioSource = factory.createAudioSource(MediaConstraints())
        val audioTrack = factory.createAudioTrack("lan-monitor-audio", newAudioSource).apply {
            setEnabled(true)
        }

        cameraCapturer = capturer
        surfaceTextureHelper = helper
        videoSource = source
        localVideoTrack = videoTrack
        audioSource = newAudioSource
        localAudioTrack = audioTrack
        localRenderer?.let(videoTrack::addSink)
    }

    private fun startCameraSignalingServer(token: String) {
        cameraServer = embeddedServer(
            factory = ServerCIO,
            host = "0.0.0.0",
            port = SIGNAL_PORT,
        ) {
            install(ServerWebSockets)
            routing {
                webSocket("/signal") {
                    // The listener binds all interfaces, but accepts only RFC 1918/link-local IPv4 peers.
                    if (!LocalLanAddress.isPrivateIpv4(call.request.local.remoteHost)) {
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Local private IPv4 clients only"))
                        return@webSocket
                    }
                    if (!serverConnectionClaimed.compareAndSet(false, true)) {
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Only one Monitor is supported in M2"))
                        return@webSocket
                    }

                    val outbound = Channel<String>(Channel.UNLIMITED)
                    val sender: (String) -> Unit = { raw ->
                        outbound.trySend(raw)
                        Unit
                    }
                    cameraSender = sender
                    val writer = launch {
                        for (raw in outbound) send(Frame.Text(raw))
                    }
                    var authenticated = false
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val message = try {
                                JSONObject(frame.readText())
                            } catch (_: Exception) {
                                outbound.trySend(JSONObject().put("type", "error").put("message", "Malformed signaling message.").toString())
                                continue
                            }

                            if (!authenticated) {
                                val suppliedToken = message.optString("token")
                                val isValidJoin = message.optString("type") == "join" &&
                                    PairingToken.matches(token, suppliedToken)
                                if (!isValidJoin) {
                                    outbound.trySend(JSONObject().put("type", "error").put("message", "Invalid pairing token.").toString())
                                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid pairing token"))
                                    break
                                }
                                authenticated = true
                                outbound.trySend(JSONObject().put("type", "ready").toString())
                                updateStatus("Monitor paired; negotiating WebRTC.")
                                scope.launch { createCameraOffer() }
                            } else {
                                handleSignalMessage(message)
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        if (!shutdownRequested && !disposed) {
                            updateStatus("Monitor signaling error: ${error.message ?: error.javaClass.simpleName}")
                        }
                    } finally {
                        cameraSender = null
                        outbound.close()
                        writer.cancel()
                        serverConnectionClaimed.set(false)
                        if (authenticated && !shutdownRequested && !disposed) {
                            scope.launch {
                                closePeerConnection()
                                updateStatus("Monitor disconnected. Waiting for Monitor.")
                            }
                        }
                    }
                }
            }
        }.start(wait = false)
    }

    private fun createCameraOffer() {
        try {
            resetDescriptionState()
            val connection = createPeerConnection(isCamera = true)
            connection.createOffer(
                createSdpObserver(
                    onCreateSuccess = { description ->
                        connection.setLocalDescription(
                            createSdpObserver(
                                onSetSuccess = { publishLocalDescription("offer", description) },
                                onFailure = { updateStatus("Could not set local offer: $it") },
                            ),
                            description,
                        )
                    },
                    onFailure = { updateStatus("Could not create offer: $it") },
                ),
                MediaConstraints(),
            )
        } catch (error: Throwable) {
            updateStatus("Could not start WebRTC offer: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun createPeerConnection(isCamera: Boolean): PeerConnection {
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
        override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
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
                PeerConnection.IceConnectionState.FAILED ->
                    updateStatus("WebRTC connection failed. Stop and reconnect.")
                PeerConnection.IceConnectionState.CLOSED -> Unit
                else -> updateStatus("Establishing direct LAN media connection…")
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) = Unit

        override fun onIceCandidate(candidate: IceCandidate) {
            val shouldSend: Boolean
            synchronized(candidateLock) {
                shouldSend = localDescriptionSignaled
                if (!shouldSend) pendingLocalCandidates.add(candidate)
            }
            if (shouldSend) sendIceCandidate(candidate)
        }

        override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) {
            stream.audioTracks.forEach { it.setEnabled(true) }
            stream.videoTracks.firstOrNull()?.let(::attachRemoteVideoTrack)
        }
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(dataChannel: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit

        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<MediaStream>) {
            when (val track = receiver.track()) {
                is VideoTrack -> attachRemoteVideoTrack(track)
                is AudioTrack -> track.setEnabled(true)
            }
        }

        override fun onTrack(transceiver: RtpTransceiver) {
            when (val track = transceiver.receiver.track()) {
                is VideoTrack -> attachRemoteVideoTrack(track)
                is AudioTrack -> track.setEnabled(true)
            }
        }
    }

    private fun attachRemoteVideoTrack(track: VideoTrack) {
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
        when (message.optString("type")) {
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
            "error" -> updateStatus("Signaling error: ${message.optString("message", "Unknown error")}")
        }
    }

    private fun handleRemoteOffer(sdp: String) {
        if (role != AppRole.MONITOR || sdp.isBlank()) return
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
                                            onFailure = { updateStatus("Could not set local answer: $it") },
                                        ),
                                        answer,
                                    )
                                },
                                onFailure = { updateStatus("Could not create answer: $it") },
                            ),
                            MediaConstraints(),
                        )
                    },
                    onFailure = { updateStatus("Could not accept Camera offer: $it") },
                ),
                offer,
            )
            updateStatus("Offer received; preparing answer.")
        } catch (error: Throwable) {
            updateStatus("Could not handle offer: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun handleRemoteAnswer(sdp: String) {
        if (role != AppRole.CAMERA || sdp.isBlank()) return
        val connection = peerConnection ?: return
        connection.setRemoteDescription(
            createSdpObserver(
                onSetSuccess = {
                    markRemoteDescriptionSet()
                    updateStatus("Answer accepted; confirming media progress.")
                },
                onFailure = { updateStatus("Could not accept Monitor answer: $it") },
            ),
            SessionDescription(SessionDescription.Type.ANSWER, sdp),
        )
    }

    private fun handleRemoteCandidate(message: JSONObject) {
        val candidateText = message.optString("candidate")
        if (candidateText.isBlank()) return
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
                val health = VideoFrameHealth.evaluate(
                    lastFrameAtMs = lastRemoteVideoFrameAtMs,
                    nowMs = SystemClock.elapsedRealtime(),
                    stallTimeoutMs = VIDEO_STALL_TIMEOUT_MS,
                )
                sendSignal(
                    JSONObject()
                        .put("type", "health")
                        .put("videoHealthy", health.isHealthy)
                        .put("lastFrameAgeMs", health.frameAgeMs ?: -1L),
                )
                if (health.isHealthy) {
                    updateStatus("Streaming: decoded video frames are advancing.")
                } else if (health.frameAgeMs != null) {
                    updateStatus("Stream interrupted: no decoded video frames for ${health.frameAgeMs} ms.")
                } else if (peerConnection != null) {
                    updateStatus("Connected; waiting for the first decoded video frame.")
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
        lastRemoteVideoFrameAtMs = 0L
        runCatching { connection?.close() }
        runCatching { connection?.dispose() }
    }

    private suspend fun cleanupSession() {
        healthJob?.cancel()
        healthJob = null
        runCatching { monitorClient?.close() }
        monitorClient = null
        runCatching { cameraServer?.stop(500, 1_000) }
        cameraServer = null
        monitorSender = null
        cameraSender = null
        closePeerConnection()

        localRenderer?.let { renderer -> runCatching { localVideoTrack?.removeSink(renderer) } }
        runCatching { cameraCapturer?.stopCapture() }
        runCatching { cameraCapturer?.dispose() }
        cameraCapturer = null
        runCatching { surfaceTextureHelper?.dispose() }
        surfaceTextureHelper = null
        runCatching { videoSource?.dispose() }
        videoSource = null
        runCatching { localVideoTrack?.dispose() }
        localVideoTrack = null
        runCatching { localAudioTrack?.dispose() }
        localAudioTrack = null
        runCatching { audioSource?.dispose() }
        audioSource = null

        updateUi {
            sessionActive = false
            isBusy = false
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
