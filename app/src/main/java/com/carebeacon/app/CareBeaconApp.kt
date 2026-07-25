package com.carebeacon.app

import android.app.Application
import com.carebeacon.app.data.AccountRepository
import com.carebeacon.app.data.AppDatabase
import com.carebeacon.app.data.LocalAccountRepository
import com.carebeacon.app.data.LocalRelationshipRepository
import com.carebeacon.app.data.RelationshipRepository
import com.carebeacon.app.data.RoleStore
import com.carebeacon.app.data.SessionStore
import com.carebeacon.app.service.WardForegroundService

class CareBeaconApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val roleStore: RoleStore by lazy { RoleStore(this) }
    val sessionStore: SessionStore by lazy { SessionStore(this) }

    val accountRepository: AccountRepository by lazy {
        LocalAccountRepository(database.accountDao(), sessionStore)
    }

    val relationshipRepository: RelationshipRepository by lazy {
        LocalRelationshipRepository(database.relationshipDao(), database.accountDao())
    }

    fun startWardService() {
        WardForegroundService.start(this)
    }

    fun stopWardService() {
        WardForegroundService.stop(this)
    }
}