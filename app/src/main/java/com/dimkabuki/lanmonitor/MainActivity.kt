package com.dimkabuki.lanmonitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.webrtc.SurfaceViewRenderer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var selectedRole by rememberSaveable { mutableStateOf<AppRole?>(null) }
                BackHandler(enabled = selectedRole != null) {
                    selectedRole?.let(SessionControllerStore::stopIfActive)
                    selectedRole = null
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (selectedRole) {
                        null -> RoleSelectionScreen(onRoleSelected = { selectedRole = it })
                        AppRole.CAMERA -> CameraScreen(onBack = {
                            SessionControllerStore.stopIfActive(AppRole.CAMERA)
                            selectedRole = null
                        })
                        AppRole.MONITOR -> MonitorScreen(onBack = {
                            SessionControllerStore.stopIfActive(AppRole.MONITOR)
                            selectedRole = null
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleSelectionScreen(onRoleSelected: (AppRole) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Lan Monitor", style = MaterialTheme.typography.headlineLarge)
        Text("Choose a role. Both devices must be connected to the same Wi-Fi network.")
        Button(onClick = { onRoleSelected(AppRole.CAMERA) }, modifier = Modifier.fillMaxWidth()) {
            Text("Camera")
        }
        Button(onClick = { onRoleSelected(AppRole.MONITOR) }, modifier = Modifier.fillMaxWidth()) {
            Text("Monitor")
        }
        Text(
            "M3 pairing: scan a Camera QR code or enter a private LAN address and temporary token manually.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CameraScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val session = remember { SessionControllerStore.attachUi(context, AppRole.CAMERA) }
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    val pairingQrBitmap = remember(session.pairingPayload) {
        session.pairingPayload.takeIf { it.isNotBlank() }?.let { payload ->
            runCatching { createQrCodeBitmap(payload, size = 480) }.getOrNull()
        }
    }

    DisposableEffect(session) {
        onDispose {
            renderer?.let { session.detachRenderer(it, local = true) }
            SessionControllerStore.detachUi(session)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        val capturePermissionsGranted =
            context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (capturePermissionsGranted) {
            session.startCamera()
        } else {
            session.showMessage("Camera and microphone permissions are required to start a session.")
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onBack) { Text("Back") }
            Text("Camera", style = MaterialTheme.typography.headlineMedium)
        }
        Text("Camera preview", style = MaterialTheme.typography.titleMedium)
        AndroidView(
            factory = { viewContext ->
                SurfaceViewRenderer(viewContext).also {
                    renderer = it
                    session.attachRenderer(it, local = true)
                }
            },
            modifier = Modifier.fillMaxWidth().height(220.dp),
        )
        Button(
            onClick = {
                val permissionsToRequest = buildList {
                    if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        add(Manifest.permission.CAMERA)
                    }
                    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        add(Manifest.permission.RECORD_AUDIO)
                    }
                    if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                if (permissionsToRequest.isEmpty()) {
                    session.startCamera()
                } else {
                    permissionLauncher.launch(permissionsToRequest.toTypedArray())
                }
            },
            enabled = !session.isBusy && !session.sessionActive,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (session.isBusy) "Starting…" else "Start Camera")
        }
        if (session.sessionActive) {
            Button(
                onClick = session::stopSession,
                enabled = !session.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Stop Session")
            }
        }
        Text("Status: ${session.status}", style = MaterialTheme.typography.bodyLarge)
        if (session.sessionActive || session.isBusy) {
            NotificationPermissionNotice(context = context, visible = true)
        }
        if (session.sessionActive) {
            Text("Local discovery: ${session.discoveryStatus}", style = MaterialTheme.typography.bodySmall)
        }
        if (session.cameraAddresses.isNotEmpty()) {
            Text("Camera address", style = MaterialTheme.typography.titleMedium)
            Text(session.cameraAddresses.joinToString(separator = "\n"))
            Text("Monitor connects to port ${LanWebRtcSession.SIGNAL_PORT}. Use the address on the same Wi-Fi network.")
        }
        if (pairingQrBitmap != null) {
            Text("Scan to pair", style = MaterialTheme.typography.titleMedium)
            Image(
                bitmap = pairingQrBitmap.asImageBitmap(),
                contentDescription = "Lan Monitor Camera pairing QR code",
                modifier = Modifier.size(220.dp),
            )
            Text("Scan this code on the Monitor device. It contains the current LAN address hints and temporary pairing secret.")
        } else if (session.pairingPayload.isNotBlank()) {
            Text("Pairing QR code could not be generated. Use the manual address/token fields on Monitor.")
        }
        if (session.pairingToken.isNotEmpty()) {
            Text("Temporary pairing token (manual fallback)", style = MaterialTheme.typography.titleMedium)
            Text(session.pairingToken, style = MaterialTheme.typography.headlineSmall)
            Text("The token is held only in memory and is not logged.")
        }
        Text(
            "An ongoing foreground-service notification accompanies active sessions. Background and screen-off reliability still require verification on the target devices; removing the app from Recents stops the session.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun MonitorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val session = remember { SessionControllerStore.attachUi(context, AppRole.MONITOR) }
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var host by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var pairedCameraId by rememberSaveable { mutableStateOf("") }
    var connectAfterNotificationPrompt by rememberSaveable { mutableStateOf(false) }

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ScanContract(),
    ) { result ->
        val raw = result.contents
        if (raw.isNullOrBlank()) {
            session.showMessage("QR scan cancelled.")
        } else {
            val pairing = CameraPairingCodec.decode(raw)
            if (pairing == null) {
                session.showMessage("This QR code is not a valid Lan Monitor pairing code.")
            } else {
                host = pairing.addresses.first()
                token = pairing.token
                pairedCameraId = pairing.cameraId
                session.showMessage("Pairing details loaded. Searching for this Camera on the local network.")
                session.discoverPairedCamera(pairing.cameraId)
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        if (connectAfterNotificationPrompt) {
            connectAfterNotificationPrompt = false
            session.connectToCamera(host.trim(), token.trim())
        }
    }

    val scanPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            scanLauncher.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt("Scan the Camera pairing QR code")
                    .setBeepEnabled(false)
                    .setOrientationLocked(false),
            )
        } else {
            session.showMessage("Camera permission is required to scan a pairing QR code.")
        }
    }

    DisposableEffect(session) {
        onDispose {
            renderer?.let { session.detachRenderer(it, local = false) }
            SessionControllerStore.detachUi(session)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onBack) { Text("Back") }
            Text("Monitor", style = MaterialTheme.typography.headlineMedium)
        }
        AndroidView(
            factory = { viewContext ->
                SurfaceViewRenderer(viewContext).also {
                    renderer = it
                    session.attachRenderer(it, local = false)
                }
            },
            modifier = Modifier.fillMaxWidth().height(240.dp),
        )
        Button(
            onClick = {
                if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    scanLauncher.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("Scan the Camera pairing QR code")
                            .setBeepEnabled(false)
                            .setOrientationLocked(false),
                    )
                } else {
                    scanPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            enabled = !session.isBusy && !session.sessionActive,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Scan Camera QR")
        }
        LaunchedEffect(session.discoveredCameraAddress) {
            if (session.discoveredCameraAddress.isNotBlank()) {
                host = session.discoveredCameraAddress
            }
        }
        if (pairedCameraId.isNotBlank()) {
            Text("Camera identity: $pairedCameraId", style = MaterialTheme.typography.bodySmall)
            Text("Local discovery: ${session.discoveryStatus}", style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = { session.discoverPairedCamera(pairedCameraId) },
                enabled = !session.isBusy && !session.sessionActive,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Find Camera on LAN")
            }
        }
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Camera private IPv4 address") },
            placeholder = { Text("192.168.1.20") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            enabled = !session.isBusy && !session.sessionActive,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Temporary pairing token") },
            singleLine = true,
            enabled = !session.isBusy && !session.sessionActive,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    connectAfterNotificationPrompt = true
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    session.connectToCamera(host.trim(), token.trim())
                }
            },
            enabled = !session.isBusy && !session.sessionActive && host.isNotBlank() && token.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (session.isBusy) "Connecting…" else "Connect to Camera")
        }
        if (session.sessionActive || session.isBusy) {
            NotificationPermissionNotice(context = context, visible = true)
            Button(
                onClick = session::stopSession,
                enabled = !session.isBusy || session.sessionActive,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Stop Monitoring")
            }
        }
        Text("Status: ${session.status}", style = MaterialTheme.typography.bodyLarge)
        Text(
            "Scanning by Camera identity only updates the connection address. If local discovery is unavailable, use the address embedded in the QR or type an address manually.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Use a private LAN IPv4 address and the temporary token shown by Camera. Video and audio travel directly over WebRTC; this app configures no STUN/TURN or cloud signaling.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun NotificationPermissionNotice(context: android.content.Context, visible: Boolean) {
    if (
        visible &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        Text(
            "Notifications are disabled. Android may hide the active-session notification from the notification drawer. Enable Lan Monitor notifications in system Settings; the active service remains visible in Android Task Manager.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
