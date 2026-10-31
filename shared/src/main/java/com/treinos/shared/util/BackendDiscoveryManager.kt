package com.treinos.shared.util

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

val Context.treinosDataStore: DataStore<Preferences> by preferencesDataStore(name = "treinos_settings")

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
    private val dataStore = context.treinosDataStore
    
    suspend fun getSavedBackendUrl(): String? {
        return dataStore.data
            .map { prefs -> prefs[BackendDiscovery.KEY_BASE_URL] }
            .first()
    }
    
    suspend fun saveBackendUrl(url: String) {
        dataStore.edit { prefs ->
            prefs[BackendDiscovery.KEY_BASE_URL] = url
        }
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
                    nsdManager.resolveService(serviceInfo, getResolveListener())
                }
            }
            
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                discoveredServices.update { current -> current - serviceInfo.serviceName }
            }
            
            override fun onDiscoveryStopped(serviceType: String) {
                isDiscovering = false
            }
            
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                isDiscovering = false
                try {
                    nsdManager.stopServiceDiscovery(this)
                } catch (e: Exception) {
                }
            }
            
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                isDiscovering = false
            }
        }
        
        try {
            nsdManager.discoverServices(
                BackendDiscovery.SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener!!
            )
        } catch (e: Exception) {
            isDiscovering = false
        }
    }
    
    fun stopDiscovery() {
        if (isDiscovering && discoveryListener != null) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener!!)
            } catch (e: Exception) {
            }
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
        
        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener!!)
        } catch (e: Exception) {
        }
    }
    
    fun unregisterBackendService() {
        if (registrationListener != null) {
            try {
                nsdManager.unregisterService(registrationListener!!)
            } catch (e: Exception) {
            }
        }
    }
    
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        stopDiscovery()
        unregisterBackendService()
    }
}
