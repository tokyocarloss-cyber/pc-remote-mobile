package com.pcremote.mobile

class NexusApplication: android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        SecureConnection.init(this)
        NexusEffects.prepare(this)
        Api.host = SecureConnection.lastHost()
    }
}
