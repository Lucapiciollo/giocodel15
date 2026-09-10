package com.lucapiciollo.giocodel15.multiplayer.nearby

import android.Manifest
import android.os.Build

object NearbyPermissions {

    fun requiredRuntimePermissions(): Array<String> = buildList {
        when {
            Build.VERSION.SDK_INT >= 37 -> {
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
                add("android.permission.ACCESS_LOCAL_NETWORK")
            }

            Build.VERSION.SDK_INT >= 32 -> {
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }

            Build.VERSION.SDK_INT >= 31 -> {
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }

            Build.VERSION.SDK_INT >= 29 -> add(Manifest.permission.ACCESS_FINE_LOCATION)
            else -> add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }.toTypedArray()
}
