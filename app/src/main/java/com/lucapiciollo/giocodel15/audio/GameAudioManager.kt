package com.lucapiciollo.giocodel15.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.lucapiciollo.giocodel15.R

/** Central place for the app's sound effects and background music. Initialized once from
 * [com.lucapiciollo.giocodel15.GameApplication]; every screen shares the same instances.
 * All playback respects [SoundSettings] so the user's on/off choice always wins. */
object GameAudioManager {

    private var soundPool: SoundPool? = null
    private var tileMoveSoundId = 0
    private var musicPlayer: MediaPlayer? = null
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(MAX_SFX_STREAMS)
            .setAudioAttributes(attributes)
            .build()
        tileMoveSoundId = soundPool?.load(context.applicationContext, R.raw.sfx_tile_move, 1) ?: 0

        runCatching {
            MediaPlayer().apply {
                val afd = context.applicationContext.resources.openRawResourceFd(R.raw.bg_music)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                isLooping = true
                setVolume(MUSIC_VOLUME, MUSIC_VOLUME)
                prepare()
            }
        }.onSuccess { musicPlayer = it }
    }

    /** Plays the short tile-move click, if sound effects are enabled. */
    fun playTileMove(context: Context) {
        if (!SoundSettings.isSfxEnabled(context)) return
        soundPool?.play(tileMoveSoundId, SFX_VOLUME, SFX_VOLUME, 1, 0, 1f)
    }

    /** Resumes the background loop if music is enabled and it isn't already playing. */
    fun resumeMusicIfEnabled(context: Context) {
        if (!SoundSettings.isMusicEnabled(context)) return
        val player = musicPlayer ?: return
        if (!player.isPlaying) player.start()
    }

    fun pauseMusic() {
        val player = musicPlayer ?: return
        if (player.isPlaying) player.pause()
    }

    /** Called from the settings screen when the user flips the music switch. */
    fun onMusicSettingChanged(context: Context, enabled: Boolean) {
        SoundSettings.setMusicEnabled(context, enabled)
        if (enabled) resumeMusicIfEnabled(context) else pauseMusic()
    }

    /** Called from the settings screen when the user flips the sound-effects switch. */
    fun onSfxSettingChanged(context: Context, enabled: Boolean) {
        SoundSettings.setSfxEnabled(context, enabled)
    }

    private const val MAX_SFX_STREAMS = 4
    private const val MUSIC_VOLUME = 0.35f
    private const val SFX_VOLUME = 0.9f
}
