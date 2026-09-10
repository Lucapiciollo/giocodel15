package com.lucapiciollo.giocodel15.multiplayer.nearby

import android.content.Context

object NearbySession {
    @Volatile
    private var manager: NearbyConnectionManager? = null

    fun manager(context: Context): NearbyConnectionManager =
        manager ?: synchronized(this) {
            manager ?: NearbyConnectionManager(context.applicationContext).also { manager = it }
        }

    fun reset() {
        manager?.apply {
            stopAdvertising()
            stopDiscovery()
            disconnectAll()
            listener = null
        }
        manager = null
    }
}
