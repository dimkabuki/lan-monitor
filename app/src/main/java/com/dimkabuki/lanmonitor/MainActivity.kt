package com.dimkabuki.lanmonitor

import android.Manifest
import android.graphics.Color as AndroidColor
import android.view.View
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.webrtc.SurfaceViewRenderer

private const val APP_PREFERENCES_NAME = "lan_monitor_preferences"
private const val DARK_THEME_KEY = "dark_theme_enabled"

private val LanMonitorDarkColors = darkColorScheme(
    primary = Color(0xFF9AB0FF),
    onPrimary = Color(0xFF10234A),
    secondary = Color(0xFF65D8CE),
    onSecondary = Color(0xFF003731),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE8EDF4),
    surface = Color(0xFF111820),
    onSurface = Color(0xFFE8EDF4),
    surfaceVariant = Color(0xFF202A35),
    onSurfaceVariant = Color(0xFFBDC7D5),
    outline = Color(0xFF778497),
)

private val LanMonitorLightColors = lightColorScheme(
    primary = Color(0xFF4058B8),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF087F8C),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF172033),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF172033),
    surfaceVariant = Color(0xFFE8EDF5),
    onSurfaceVariant = Color(0xFF455268),
    outline = Color(0xFF727E90),
)

class MainActivity : ComponentActivity() {
    private val pictureInPictureModeState = mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        SessionControllerStore.existing(AppRole.CAMERA)?.recoverCameraAfterForegroundReturn()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pictureInPictureModeState.value = isInPictureInPictureMode
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val preferences = remember {
                getSharedPreferences(APP_PREFERENCES_NAME, MODE_PRIVATE)
            }
            var darkThemeEnabled by rememberSaveable {
                mutableStateOf(preferences.getBoolean(DARK_THEME_KEY, true))
            }
            val onDarkThemeChanged: (Boolean) -> Unit = { enabled ->
                darkThemeEnabled = enabled
                preferences.edit().putBoolean(DARK_THEME_KEY, enabled).apply()
            }
            val diagnosticLoggingEnabled = DiagnosticLog.enabled.value
            val diagnosticLogStatusMessage = DiagnosticLog.statusMessage.value
            val onDiagnosticLoggingChanged: (AppRole, Boolean) -> Unit = { role, enabled ->
                DiagnosticLog.setEnabled(this@MainActivity, role, enabled)
            }

            SideEffect {
                val lightSystemBarFlags =
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                val existingFlags = window.decorView.systemUiVisibility
                window.decorView.systemUiVisibility = if (darkThemeEnabled) {
                    existingFlags and lightSystemBarFlags.inv()
                } else {
                    existingFlags or lightSystemBarFlags
                }
                window.statusBarColor = if (darkThemeEnabled) {
                    AndroidColor.rgb(11, 15, 20)
                } else {
                    AndroidColor.rgb(246, 248, 252)
                }
                window.navigationBarColor = if (darkThemeEnabled) {
                    AndroidColor.rgb(11, 15, 20)
                } else {
                    AndroidColor.rgb(246, 248, 252)
                }
            }

            MaterialTheme(
                colorScheme = if (darkThemeEnabled) LanMonitorDarkColors else LanMonitorLightColors,
            ) {
                var selectedRole by rememberSaveable { mutableStateOf<AppRole?>(null) }
                BackHandler(enabled = selectedRole != null) {
                    selectedRole?.let { SessionControllerStore.stopIfActive(it) }
                    selectedRole = null
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (selectedRole) {
                        null -> RoleSelectionScreen(
                            onRoleSelected = { selectedRole = it },
                            darkThemeEnabled = darkThemeEnabled,
                            onDarkThemeChanged = onDarkThemeChanged,
                        )
                        AppRole.CAMERA -> CameraScreen(
                            onBack = {
                                SessionControllerStore.stopIfActive(AppRole.CAMERA)
                                selectedRole = null
                            },
                            darkThemeEnabled = darkThemeEnabled,
                            onDarkThemeChanged = onDarkThemeChanged,
                            diagnosticLoggingEnabled = diagnosticLoggingEnabled,
                            diagnosticLogStatusMessage = diagnosticLogStatusMessage,
                            onDiagnosticLoggingChanged = onDiagnosticLoggingChanged,
                        )
                        AppRole.MONITOR -> MonitorScreen(
                            onBack = {
                                SessionControllerStore.stopIfActive(AppRole.MONITOR)
                                selectedRole = null
                            },
                            darkThemeEnabled = darkThemeEnabled,
                            onDarkThemeChanged = onDarkThemeChanged,
                            diagnosticLoggingEnabled = diagnosticLoggingEnabled,
                            diagnosticLogStatusMessage = diagnosticLogStatusMessage,
                            onDiagnosticLoggingChanged = onDiagnosticLoggingChanged,
                            isPictureInPictureMode = pictureInPictureModeState.value,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSwitch(
    darkThemeEnabled: Boolean,
    onDarkThemeChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Dark theme", style = MaterialTheme.typography.labelMedium)
        Switch(
            checked = darkThemeEnabled,
            onCheckedChange = onDarkThemeChanged,
            modifier = Modifier.semantics { contentDescription = "Dark theme" },
        )
    }
}

@Composable
private fun DiagnosticLoggingControl(
    role: AppRole,
    enabled: Boolean,
    statusMessage: String,
    onChanged: (AppRole, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Diagnostic logging",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = enabled,
                onCheckedChange = { requested -> onChanged(role, requested) },
                modifier = Modifier.semantics { contentDescription = "Diagnostic logging" },
            )
        }
        Text(
            statusMessage,
            style = MaterialTheme.typography.bodySmall,
        )
        if (enabled) {
            Text(
                "Logs connection and discovery events only. Pairing secrets, SDP/ICE payloads and media are excluded.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RoleSelectionScreen(
    onRoleSelected: (AppRole) -> Unit,
    darkThemeEnabled: Boolean,
    onDarkThemeChanged: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Lan Monitor",
            style = MaterialTheme.typography.headlineLarge,
        )
        Text("Choose a role. Both devices must be connected to the same Wi-Fi network.")
        Button(onClick = { onRoleSelected(AppRole.CAMERA) }, modifier = Modifier.fillMaxWidth()) {
            Text("Camera")
        }
        Button(onClick = { onRoleSelected(AppRole.MONITOR) }, modifier = Modifier.fillMaxWidth()) {
            Text("Monitor")
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            "Pair by scanning the Camera QR code, or enter its private LAN address and temporary token manually.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThemeSwitch(
                darkThemeEnabled = darkThemeEnabled,
                onDarkThemeChanged = onDarkThemeChanged,
            )
        }
        AppVersionLabel()
    }
}

@Composable
private fun AppVersionLabel() {
    Text(
        "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_COMMIT})",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CameraScreen(
    onBack: () -> Unit,
    darkThemeEnabled: Boolean,
    onDarkThemeChanged: (Boolean) -> Unit,
    diagnosticLoggingEnabled: Boolean,
    diagnosticLogStatusMessage: String,
    onDiagnosticLoggingChanged: (AppRole, Boolean) -> Unit,
) {
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onBack) { Text("Back") }
            Text(
                "Camera",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
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
        if (session.sessionActive) {
            Text("Local discovery: ${session.discoveryStatus}", style = MaterialTheme.typography.bodySmall)
        }
        if (session.cameraAddresses.isNotEmpty()) {
            Text("Camera address", style = MaterialTheme.typography.titleMedium)
            Text(session.cameraAddresses.joinToString(separator = "\n"))
            Text("Monitor connects to port ${LanWebRtcSession.SIGNAL_PORT}. Use the address on the same Wi-Fi network.")
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
        DiagnosticLoggingControl(
            role = AppRole.CAMERA,
            enabled = diagnosticLoggingEnabled,
            statusMessage = diagnosticLogStatusMessage,
            onChanged = onDiagnosticLoggingChanged,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThemeSwitch(darkThemeEnabled, onDarkThemeChanged)
        }
    }
}

@Composable
private fun MonitorScreen(
    onBack: () -> Unit,
    darkThemeEnabled: Boolean,
    onDarkThemeChanged: (Boolean) -> Unit,
    diagnosticLoggingEnabled: Boolean,
    diagnosticLogStatusMessage: String,
    onDiagnosticLoggingChanged: (AppRole, Boolean) -> Unit,
    isPictureInPictureMode: Boolean,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val session = remember { SessionControllerStore.attachUi(context, AppRole.MONITOR) }
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var host by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var pairedCameraId by rememberSaveable { mutableStateOf("") }
    var connectAfterNotificationPrompt by rememberSaveable { mutableStateOf(false) }
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    val compactVideoMode = fullScreen || isPictureInPictureMode

    BackHandler(enabled = fullScreen && !isPictureInPictureMode) {
        fullScreen = false
    }

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ScanContract(),
    ) { result ->
        val raw = result.contents
        if (raw.isNullOrBlank()) {
            DiagnosticLog.log(AppRole.MONITOR, "pairing_qr_scan_cancelled")
            session.showMessage("QR scan cancelled.")
        } else {
            val pairing = CameraPairingCodec.decode(raw)
            if (pairing == null) {
                DiagnosticLog.log(AppRole.MONITOR, "pairing_qr_rejected", "reason=invalid_or_unsupported_payload")
                session.showMessage("This QR code is not a valid Lan Monitor pairing code.")
            } else {
                DiagnosticLog.log(
                    AppRole.MONITOR,
                    "pairing_qr_decoded",
                    "address_hint_count=${pairing.addresses.size}; camera_identity_present=${pairing.cameraId.isNotBlank()}",
                )
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

    DisposableEffect(activity, session.sessionActive, isPictureInPictureMode) {
        val keepScreenOn = session.sessionActive && !isPictureInPictureMode
        if (keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (keepScreenOn) {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    DisposableEffect(activity, fullScreen, isPictureInPictureMode) {
        val decorView = activity?.window?.decorView
        val previousSystemUiVisibility = decorView?.systemUiVisibility
        if (decorView != null && fullScreen && !isPictureInPictureMode) {
            val immersiveFlags =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            decorView.systemUiVisibility = previousSystemUiVisibility!! or immersiveFlags
        }
        onDispose {
            if (decorView != null && previousSystemUiVisibility != null) {
                decorView.systemUiVisibility = previousSystemUiVisibility
            }
        }
    }

    LaunchedEffect(session.discoveredCameraAddress) {
        if (session.discoveredCameraAddress.isNotBlank()) {
            host = session.discoveredCameraAddress
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = if (compactVideoMode) {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
            },
            verticalArrangement = if (compactVideoMode) Arrangement.Top else Arrangement.spacedBy(14.dp),
        ) {
            if (!compactVideoMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = onBack) { Text("Back") }
                    Text(
                        "Monitor",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            AndroidView(
                factory = { viewContext ->
                    SurfaceViewRenderer(viewContext).also {
                        renderer = it
                        session.attachRenderer(it, local = false)
                    }
                },
                modifier = if (compactVideoMode) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(240.dp),
            )

            if (!compactVideoMode) {
                Button(
                    onClick = {
                        DiagnosticLog.log(AppRole.MONITOR, "pairing_qr_scan_requested")
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
                    if (session.sessionActive) {
                        Button(
                            onClick = session::toggleRemoteAudioMuted,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (session.remoteAudioMuted) "Unmute Audio" else "Mute Audio")
                        }
                        Button(
                            onClick = { fullScreen = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Full screen")
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Button(
                                onClick = {
                                    val params = PictureInPictureParams.Builder()
                                        .setAspectRatio(Rational(4, 3))
                                        .build()
                                    activity?.enterPictureInPictureMode(params)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Picture-in-picture")
                            }
                        }
                    }
                    Button(
                        onClick = session::stopSession,
                        enabled = !session.isBusy || session.sessionActive,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Stop Monitoring")
                    }
                }
                Text("Status: ${session.status}", style = MaterialTheme.typography.bodyLarge)
                Text("Movement alerts: ${session.movementStatus}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Scanning by Camera identity only updates the connection address. If local discovery is unavailable, use the address embedded in the QR or type an address manually.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Use a private LAN IPv4 address and the temporary token shown by Camera. Video and audio travel directly over WebRTC; this app configures no STUN/TURN or cloud signaling.",
                    style = MaterialTheme.typography.bodySmall,
                )
                DiagnosticLoggingControl(
                    role = AppRole.MONITOR,
                    enabled = diagnosticLoggingEnabled,
                    statusMessage = diagnosticLogStatusMessage,
                    onChanged = onDiagnosticLoggingChanged,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ThemeSwitch(darkThemeEnabled, onDarkThemeChanged)
                }
            }
        }
        if (fullScreen && !isPictureInPictureMode) {
            Button(
                onClick = { fullScreen = false },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            ) {
                Text("Exit full screen")
            }
        }
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
