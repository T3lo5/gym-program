package com.treinos.shared.util

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.preferencesDataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.InetAddress

object BackendDiscovery {
    const val SERVICE_TYPE = "_treinos._tcp."
    const val SERVICE_NAME = "TreinosBackend"
    const val DEFAULT_PORT = 3000
    const val DATASTORE_KEY = "backend_base_url"
    
    val KEY_BASE_URL = stringPreferencesKey(DATASTORE_KEY)
}

class BackendDiscoveryManager(
    private val context: Context,
    private val scope: CoroutineScope
) : LifecycleObserver {
    
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val discoveredServices = MutableStateFlow<Map<String, NsdServiceInfo>>(emptyMap())
    private var resolveListener: NsdManager.ResolveListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var isDiscovering = false
    
    val services: StateFlow<Map<String, NsdServiceInfo>> = discoveredServices
    
    // Datastore for persisted backend URL
    private val dataStore = context.preferencesDataStore("treinos_settings")
    
    suspend fun getSavedBackendUrl(): String? {
        return dataStore.data
            .map { prefs -> prefs[BackendDiscovery.KEY_BASE_URL] }
            .first()
    }
    
    suspend fun saveBackendUrl(url: String) {
        dataStore.edit { prefs ->
            prefs[BackendDiscovery.KEY_BASE_URL] = url
        }.await()
    }
    
    fun startDiscovery() {
        if (isDiscovering) return
        isDiscovering = true
        
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                // Discovery started
            }
            
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType == BackendDiscovery.SERVICE_TYPE) {
                    // Found a potential backend, resolve it to get IP/port
                    nsdManager.resolveService(serviceInfo, getResolveListener())
                }
            }
            
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                discoveredServices.update { it - serviceInfo.serviceName }
            }
            
            override fun onDiscoveryStopped(serviceType: String) {
                isDiscovering = false
            }
            
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                isDiscovering = false
                nsdManager.stopServiceDiscovery(this)
            }
            
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                isDiscovering = false
            }
        }
        
        nsdManager.discoverServices(
            BackendDiscovery.SERVICE_TYPE,
            NsdManager.PROTOCOL_DNS_SD,
            discoveryListener!!
        )
    }
    
    fun stopDiscovery() {
        if (isDiscovering && discoveryListener != null) {
            nsdManager.stopServiceDiscovery(discoveryListener!!)
            isDiscovering = false
        }
    }
    
    private fun getResolveListener(): NsdManager.ResolveListener {
        if (resolveListener == null) {
            resolveListener = object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    // Resolution failed
                }
                
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    // Service resolved, we have IP and port
                    discoveredServices.update { current ->
                        current + (serviceInfo.serviceName to serviceInfo)
                    }
                }
            }
        }
        return resolveListener!!
    }
    
    // For the backend to register itself
    fun registerBackendService(port: Int = BackendDiscovery.DEFAULT_PORT) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = BackendDiscovery.SERVICE_NAME
            serviceType = BackendDiscovery.SERVICE_TYPE
            setPort(port)
        }
        
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                // Backend registered successfully
            }
            
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                // Registration failed
            }
            
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                // Service unregistered
            }
            
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                // Unregistration failed
            }
        }
        
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener!!)
    }
    
    fun unregisterBackendService() {
        registrationListener?.let { listener ->
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = BackendDiscovery.SERVICE_NAME
                serviceType = BackendDiscovery.SERVICE_TYPE
            }
            nsdManager.unregisterService(listener)
        }
    }
    
    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        stopDiscovery()
        unregisterBackendService()
    }
}

data class DiscoveredBackend(
    val name: String,
    val host: String,
    val port: Int,
    val baseUrl: String
) {
    override fun toString(): String = "$name ($host:$port)"
}

class BackendConnectionTester {
    suspend fun testConnection(baseUrl: String): Boolean {
        return try {
            val url = "$baseUrl/health"
            val request = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            request.requestMethod = "GET"
            request.connectTimeout = 5000
            request.readTimeout = 5000
            request.responseCode == 200
        } catch (e: Exception) {
            false
        }
    }
}