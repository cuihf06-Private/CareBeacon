package com.carebeacon.app

import android.app.Application
import com.carebeacon.app.data.AppDatabase
import com.carebeacon.app.data.RoleStore
import com.carebeacon.app.service.WardForegroundService

class CareBeaconApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val roleStore: RoleStore by lazy { RoleStore(this) }

    fun startWardService() {
        WardForegroundService.start(this)
    }

    fun stopWardService() {
        WardForegroundService.stop(this)
    }
}