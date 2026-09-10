package com.lucapiciollo.giocodel15.multiplayer.nearby

import android.content.Context
import android.os.Build
import android.provider.Settings

object DeviceIdentity {
    fun displayName(context: Context): String {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeLast(4)
            ?.uppercase()
            ?: "0000"
        val model = Build.MODEL.take(12).trim().ifBlank { "Giocatore" }
        return "$model-$id"
    }
}
