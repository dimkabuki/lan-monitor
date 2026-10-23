package com.dimkabuki.lanmonitor

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.SurfaceViewRenderer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var selectedRole by rememberSaveable { mutableStateOf<AppRole?>(null) }
                BackHandler(enabled = selectedRole != null) { selectedRole = null }
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (selectedRole) {
                        null -> RoleSelectionScreen(onRoleSelected = { selectedRole = it })
                        AppRole.CAMERA -> CameraScreen(onBack = { selectedRole = null })
                        AppRole.MONITOR -> MonitorScreen(onBack = { selectedRole = null })
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
            "M2 baseline: manual local-IP pairing, direct WebRTC audio/video, and explicit media status.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CameraScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val session = remember { LanWebRtcSession(context, AppRole.CAMERA) }
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    DisposableEffect(session) {
        onDispose {
            renderer?.let { session.detachRenderer(it, local = true) }
            session.dispose()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) {
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
                val granted = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    session.startCamera()
                } else {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                    )
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
        if (session.cameraAddresses.isNotEmpty()) {
            Text("Camera address", style = MaterialTheme.typography.titleMedium)
            Text(session.cameraAddresses.joinToString(separator = "\n"))
            Text("Monitor connects to port ${LanWebRtcSession.SIGNAL_PORT}. Use the address on the same Wi-Fi network.")
        }
        if (session.pairingToken.isNotEmpty()) {
            Text("Temporary pairing token", style = MaterialTheme.typography.titleMedium)
            Text(session.pairingToken, style = MaterialTheme.typography.headlineSmall)
            Text("Enter this token on the Monitor device. It is held only in memory and is not logged.")
        }
        Text(
            "Keep this screen open for the M2 baseline. Background and screen-off operation will be addressed in a later milestone.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun MonitorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val session = remember { LanWebRtcSession(context, AppRole.MONITOR) }
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var host by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }

    DisposableEffect(session) {
        onDispose {
            renderer?.let { session.detachRenderer(it, local = false) }
            session.dispose()
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
            onClick = { session.connectToCamera(host.trim(), token.trim()) },
            enabled = !session.isBusy && !session.sessionActive && host.isNotBlank() && token.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (session.isBusy) "Connecting…" else "Connect to Camera")
        }
        if (session.sessionActive || session.isBusy) {
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
            "Use a private LAN IPv4 address and the temporary token shown by Camera. Video and audio travel directly over WebRTC; this app configures no STUN/TURN or cloud signaling.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
