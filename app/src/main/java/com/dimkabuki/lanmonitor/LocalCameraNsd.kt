package com.dimkabuki.lanmonitor

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ext.SdkExtensions
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Small lifecycle-bound adapter for local DNS-SD/mDNS.
 *
 * The advertised TXT record contains only a stable random Camera ID and protocol version. It never
 * broadcasts the pairing token. Discovery results are accepted only when the ID matches the QR
 * pairing and the resolved endpoint is a private/link-local IPv4 address.
 */
internal class LocalCameraNsd(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val callbackExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var closed = false
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var discoveryGeneration = 0
    private val resolvingNames = mutableSetOf<String>()
    private var multicastLock: WifiManager.MulticastLock? = null

    fun startAdvertising(
        cameraId: String,
        port: Int,
        onStatus: (String) -> Unit,
    ) {
        if (closed) return
        val nsdManager = manager ?: run {
            report(onStatus, "Android local network discovery is unavailable. Use QR/manual pairing.")
            return
        }
        stopAdvertising()

        val service = NsdServiceInfo().apply {
            serviceName = "lanmonitor-$cameraId"
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute(ATTRIBUTE_CAMERA_ID, cameraId)
            setAttribute(ATTRIBUTE_VERSION, PROTOCOL_VERSION)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                report(onStatus, "Local discovery advertised.")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                report(onStatus, "Local discovery unavailable (registration error $errorCode). Use QR/manual pairing.")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Unit
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                report(onStatus, "Local discovery cleanup failed (error $errorCode).")
            }
        }
        registrationListener = listener
        runCatching {
            nsdManager.registerService(
                service,
                NsdManager.PROTOCOL_DNS_SD,
                callbackExecutor,
                listener,
            )
        }.onFailure {
            registrationListener = null
            report(onStatus, "Local discovery unavailable. Use QR/manual pairing.")
        }
    }

    fun startDiscovery(
        expectedCameraId: String,
        onFound: (String) -> Unit,
        onLost: () -> Unit,
        onStatus: (String) -> Unit,
    ) {
        if (closed) return
        val nsdManager = manager ?: run {
            report(onStatus, "Android local network discovery is unavailable. Use the QR address or manual fallback.")
            return
        }
        stopDiscovery()
        val generation = synchronized(this) {
            discoveryGeneration += 1
            resolvingNames.clear()
            discoveryGeneration
        }
        acquireLegacyMulticastLock(onStatus)

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                reportForGeneration(generation, onStatus, "Searching for paired Camera on the local network…")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                reportForGeneration(generation, onStatus, "Local discovery could not start (error $errorCode). Use the QR address or manual fallback.")
                runCatching { nsdManager.stopServiceDiscovery(this) }
                releaseMulticastLock()
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!matchesServiceType(serviceInfo.serviceType)) return
                val serviceName = serviceInfo.serviceName ?: return
                // Service names include the stable ID as a discovery hint; resolved TXT identity
                // is still authoritative, and the temporary pairing secret is never advertised.
                if (!serviceName.startsWith("lanmonitor-$expectedCameraId")) return
                val shouldResolve = synchronized(resolvingNames) { resolvingNames.add(serviceName) }
                if (!shouldResolve) return

                runCatching {
                    nsdManager.resolveService(serviceInfo, callbackExecutor, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            synchronized(resolvingNames) { resolvingNames.remove(serviceName) }
                            reportForGeneration(generation, onStatus, "A local Camera advertisement could not be resolved (error $errorCode).")
                        }

                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            synchronized(resolvingNames) { resolvingNames.remove(serviceName) }
                            if (!isCurrentGeneration(generation)) return
                            val advertisedVersion = resolved.attributes[ATTRIBUTE_VERSION]
                                ?.toString(StandardCharsets.UTF_8)
                            if (advertisedVersion != PROTOCOL_VERSION) return
                            val advertisedId = resolved.attributes[ATTRIBUTE_CAMERA_ID]
                                ?.toString(StandardCharsets.UTF_8)
                            val address = resolved.host?.hostAddress
                            val selected = CameraDiscoveryPolicy.selectAddress(
                                expectedCameraId = expectedCameraId,
                                advertisedCameraId = advertisedId,
                                candidateAddress = address,
                            )
                            if (selected != null) {
                                postForGeneration(generation) {
                                    onFound(selected)
                                    onStatus("Paired Camera found at $selected.")
                                }
                            }
                        }
                    })
                }.onFailure {
                    synchronized(resolvingNames) { resolvingNames.remove(serviceName) }
                    reportForGeneration(generation, onStatus, "Local Camera lookup failed. Use the QR address or manual fallback.")
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val serviceName = serviceInfo.serviceName
                if (serviceName != null) {
                    synchronized(resolvingNames) { resolvingNames.remove(serviceName) }
                }
                val expectedPrefix = "lanmonitor-$expectedCameraId"
                if (serviceName?.startsWith(expectedPrefix) == true) {
                    postForGeneration(generation) {
                        onLost()
                        onStatus("Paired Camera advertisement disappeared. Searching for it again…")
                    }
                }
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                reportForGeneration(generation, onStatus, "Local discovery could not stop cleanly (error $errorCode).")
            }
        }
        discoveryListener = listener
        runCatching {
            // The String/Executor overload is not available on every supported SDK level.
            // The legacy discovery overload is compatible with API 33; subsequent resolution
            // callbacks use the executor overload where it exists.
            nsdManager.discoverServices(
                SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                listener,
            )
        }.onFailure {
            discoveryListener = null
            releaseMulticastLock()
            reportForGeneration(generation, onStatus, "Local discovery unavailable. Use the QR address or manual fallback.")
        }
    }

    fun stopAdvertising() {
        val listener = registrationListener ?: return
        registrationListener = null
        runCatching { manager?.unregisterService(listener) }
    }

    fun stopDiscovery() {
        synchronized(this) { discoveryGeneration += 1 }
        val listener = discoveryListener
        discoveryListener = null
        synchronized(resolvingNames) { resolvingNames.clear() }
        if (listener != null) {
            runCatching { manager?.stopServiceDiscovery(listener) }
        }
        releaseMulticastLock()
    }

    fun close() {
        if (closed) return
        closed = true
        stopDiscovery()
        stopAdvertising()
        callbackExecutor.shutdown()
    }

    private fun acquireLegacyMulticastLock(onStatus: (String) -> Unit) {
        // Android 13 devices without T extension 7 do not automatically permit foreground mDNS
        // multicast reception. Newer versions manage foreground multicast reception themselves.
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.TIRAMISU ||
            SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 7
        ) return

        runCatching {
            val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifi.createMulticastLock("LanMonitorLocalDiscovery").apply {
                setReferenceCounted(false)
                acquire()
            }.also { multicastLock = it }
        }.onFailure {
            report(onStatus, "This Android version could not enable local discovery. Use QR/manual pairing.")
        }
    }

    private fun releaseMulticastLock() {
        val lock = multicastLock ?: return
        multicastLock = null
        runCatching { if (lock.isHeld) lock.release() }
    }

    private fun matchesServiceType(value: String?): Boolean =
        value != null && value.trimEnd('.') == SERVICE_TYPE.trimEnd('.')

    private fun isCurrentGeneration(generation: Int): Boolean =
        !closed && discoveryListener != null && generation == synchronized(this) { discoveryGeneration }

    private fun report(callback: (String) -> Unit, message: String) {
        if (closed) return
        mainHandler.post { if (!closed) callback(message) }
    }

    private fun reportForGeneration(
        generation: Int,
        callback: (String) -> Unit,
        message: String,
    ) {
        postForGeneration(generation) { callback(message) }
    }

    private fun postForGeneration(generation: Int, block: () -> Unit) {
        if (closed) return
        mainHandler.post {
            if (isCurrentGeneration(generation)) block()
        }
    }

    companion object {
        const val SERVICE_TYPE = "_lanmonitor._tcp."
        private const val ATTRIBUTE_CAMERA_ID = "cameraid"
        private const val ATTRIBUTE_VERSION = "version"
        private const val PROTOCOL_VERSION = "1"
    }
}
