package com.lucapiciollo.giocodel15.audio

import android.content.Context

/** Persists the user's sound preferences (SharedPreferences-backed). Both default to enabled. */
object SoundSettings {
    private const val PREFS_NAME = "sound_settings"
    private const val KEY_SFX_ENABLED = "sfx_enabled"
    private const val KEY_MUSIC_ENABLED = "music_enabled"

    fun isSfxEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SFX_ENABLED, true)

    fun isMusicEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MUSIC_ENABLED, true)

    fun setSfxEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SFX_ENABLED, enabled).apply()
    }

    fun setMusicEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MUSIC_ENABLED, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
