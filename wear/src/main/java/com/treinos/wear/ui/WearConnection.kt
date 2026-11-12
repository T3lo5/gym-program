package com.treinos.wear.ui

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WearConnectionWatcher(
    context: Context,
    private val scope: CoroutineScope,
) {

    private val capabilityClient = Wearable.getCapabilityClient(context)

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val listener = CapabilityClient.OnCapabilityChangedListener { info ->
        _connected.value = info.nodes.isNotEmpty()
    }

    fun start() {
        runCatching { capabilityClient.addListener(listener, CAPABILITY) }
        refresh()
    }

    fun stop() {
        runCatching { capabilityClient.removeListener(listener) }
    }

    private fun refresh() {
        scope.launch {
            val info = withContext(Dispatchers.IO) {
                runCatching {
                    Tasks.await(
                        capabilityClient.getCapability(
                            CAPABILITY,
                            CapabilityClient.FILTER_REACHABLE,
                        ),
                    )
                }.getOrNull()
            }
            _connected.value = !info?.nodes.isNullOrEmpty()
        }
    }

    companion object {
        const val CAPABILITY = "treinos_phone"
    }
}