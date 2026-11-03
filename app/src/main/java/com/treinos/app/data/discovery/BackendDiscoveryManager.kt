package com.treinos.app.data.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.util.concurrent.ConcurrentHashMap

class BackendDiscoveryManager(context: Context) {
    private val nsdManager: NsdManager? =
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val resolveListeners = ConcurrentHashMap<String, NsdManager.ResolveListener>()
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val found = linkedMapOf<String, DiscoveredBackend>()

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start(onFound: (DiscoveredBackend) -> Unit, onError: (String) -> Unit) {
        val manager = nsdManager
        if (manager == null) {
            onError("Descoberta indisponível neste dispositivo.")
            return
        }
        if (isRunning) return
        found.clear()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                isRunning = true
            }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolveService(manager, serviceInfo, onFound, onError)
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {
                isRunning = false
            }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                isRunning = false
                onError("Falha ao iniciar descoberta (erro $errorCode).")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                isRunning = false
            }
        }
        discoveryListener = listener
        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            isRunning = false
            onError(e.message ?: "Erro ao descobrir backends.")
        }
    }

    private fun resolveService(
        manager: NsdManager,
        serviceInfo: NsdServiceInfo,
        onFound: (DiscoveredBackend) -> Unit,
        onError: (String) -> Unit,
    ) {
        val key = "${serviceInfo.serviceName}@${serviceInfo.serviceType}"
        if (resolveListeners.containsKey(key)) return
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                resolveListeners.remove(key)
            }
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                resolveListeners.remove(key)
                val host = serviceInfo.host?.hostAddress ?: return
                val port = serviceInfo.port
                val backend = DiscoveredBackend.from(host, port, serviceInfo.serviceName) ?: return
                synchronized(found) {
                    val existing = found["${backend.host}:${backend.port}"]
                    found["${backend.host}:${backend.port}"] =
                        existing?.copy(name = backend.name) ?: backend
                }
                onFound(backend)
            }
        }
        resolveListeners[key] = listener
        try {
            manager.resolveService(serviceInfo, listener)
        } catch (e: Exception) {
            resolveListeners.remove(key)
        }
    }

    fun stop() {
        val manager = nsdManager ?: return
        val listener = discoveryListener ?: return
        try {
            manager.stopServiceDiscovery(listener)
        } catch (_: Exception) {
        }
        discoveryListener = null
        isRunning = false
    }

    companion object {
        const val SERVICE_TYPE = "_treinos._tcp."
    }
}
