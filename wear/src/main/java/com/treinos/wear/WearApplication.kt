package com.treinos.wear

import android.app.Application
import com.treinos.wear.data.WearStateStore
import com.treinos.wear.data.WearSyncService

class WearApplication : Application() {

    val stateStore: WearStateStore by lazy { WearStateStore(this) }

    override fun onCreate() {
        super.onCreate()
        WearSyncService.setStateStore(stateStore)
    }
}
