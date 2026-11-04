package com.treinos.app.data.discovery

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Descobre o backend do Treinos na rede local com `NsdManager` (mDNS), sem
 * dependência nova.
 *
 * A descoberta é sempre sob demanda — nada é iniciado sozinho — e [stop] tem de
 * ser chamado ao sair da tela para não gastar bateria.
 */
class BackendDiscoveryManager(context: Context) {

    private val appContext = context.applicationContext
    private val nsdManager: NsdManager? =
        appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val pendingResolves = ConcurrentHashMap<String, ResolveAttempt>()
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    @Volatile
    var isRunning: Boolean = false
        private set

    private class ResolveAttempt(
        val listener: NsdManager.ResolveListener,
        val timeoutTask: Runnable,
    )

    fun start(onFound: (DiscoveredBackend) -> Unit, onError: (String) -> Unit) {
        val manager = nsdManager
        if (manager == null) {
            onError("Este dispositivo não oferece descoberta de rede. Use o endereço manual do backend.")
            return
        }
        if (isRunning) return

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                isRunning = true
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolveService(manager, serviceInfo, onFound)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

            override fun onDiscoveryStopped(serviceType: String) {
                isRunning = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                isRunning = false
                onError("Não foi possível buscar na rede local (erro $errorCode). Use o endereço manual.")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                isRunning = false
            }
        }
        discoveryListener = listener
        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (error: Exception) {
            isRunning = false
            discoveryListener = null
            onError(error.message ?: "Erro ao procurar backends na rede local.")
        }
    }

    fun stop() {
        val listener = discoveryListener
        discoveryListener = null
        pendingResolves.values.forEach { mainHandler.removeCallbacks(it.timeoutTask) }
        pendingResolves.clear()
        isRunning = false
        if (listener == null) return
        try {
            nsdManager?.stopServiceDiscovery(listener)
        } catch (_: IllegalArgumentException) {
            // Discovery já foi parada pelo sistema; não há o que limpar.
        }
    }

    private fun resolveService(
        manager: NsdManager,
        serviceInfo: NsdServiceInfo,
        onFound: (DiscoveredBackend) -> Unit,
    ) {
        val key = "${serviceInfo.serviceName}@${serviceInfo.serviceType}"
        if (pendingResolves.containsKey(key)) return

        // O callback pode chegar depois do timeout; depois de descartado, ele
        // deixa de entregar o serviço para a UI.
        val delivered = AtomicBoolean(false)
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                if (delivered.compareAndSet(false, true)) pendingResolves.remove(key)
            }

            override fun onServiceResolved(info: NsdServiceInfo) {
                if (!delivered.compareAndSet(false, true)) return
                pendingResolves.remove(key)
                val host = info.host?.hostAddress ?: return
                val backend = DiscoveredBackend.from(
                    host = host,
                    port = info.port,
                    name = info.serviceName,
                    version = readTxtVersion(info),
                ) ?: return
                onFound(backend)
            }
        }
        val timeoutTask = Runnable {
            if (delivered.compareAndSet(false, true)) pendingResolves.remove(key)
        }
        pendingResolves[key] = ResolveAttempt(listener, timeoutTask)
        mainHandler.postDelayed(timeoutTask, RESOLVE_TIMEOUT_MS)
        try {
            manager.resolveService(serviceInfo, listener)
        } catch (_: Exception) {
            if (delivered.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeoutTask)
                pendingResolves.remove(key)
            }
        }
    }

    /** `NsdServiceInfo.attributes` (TXT) só existe a partir da API 28. */
    private fun readTxtVersion(info: NsdServiceInfo): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val raw = runCatching { info.attributes[TXT_VERSION_KEY] }.getOrNull() ?: return null
        return String(raw, Charsets.UTF_8).trim().takeIf { it.isNotEmpty() }
    }

    companion object {
        const val SERVICE_TYPE = "_treinos._tcp."
        private const val TXT_VERSION_KEY = "version"
        private const val RESOLVE_TIMEOUT_MS = 5_000L
    }
}
