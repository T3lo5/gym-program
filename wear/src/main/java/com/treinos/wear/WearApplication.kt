package com.treinos.wear

import android.app.Application
import com.treinos.wear.data.WearStateStore
import com.treinos.wear.data.WearStandaloneStore
import com.treinos.wear.data.WearSyncService

class WearApplication : Application() {

    val stateStore: WearStateStore by lazy { WearStateStore(this) }

    /** H4-16: Room do relógio — snapshot da sessão e séries offline. */
    val standaloneStore: WearStandaloneStore by lazy { WearStandaloneStore.from(this) }

    override fun onCreate() {
        super.onCreate()
        WearSyncService.setStateStore(stateStore)
        WearSyncService.setStandaloneStore(standaloneStore)
    }
}
