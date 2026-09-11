package com.lucapiciollo.giocodel15.feature.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lucapiciollo.giocodel15.audio.GameAudioManager
import com.lucapiciollo.giocodel15.audio.SoundSettings
import com.lucapiciollo.giocodel15.core.ui.applyNavigationBarBottomInset
import com.lucapiciollo.giocodel15.core.ui.applyStatusBarTopInset
import com.lucapiciollo.giocodel15.databinding.ActivitySettingsBinding

/** Lets the player turn background music and sound effects on/off. Changes apply immediately
 * (no "save" step) and are persisted via [SoundSettings]. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyStatusBarTopInset()
        binding.root.applyNavigationBarBottomInset()

        binding.musicSwitch.isChecked = SoundSettings.isMusicEnabled(this)
        binding.sfxSwitch.isChecked = SoundSettings.isSfxEnabled(this)

        binding.musicSwitch.setOnCheckedChangeListener { _, isChecked ->
            GameAudioManager.onMusicSettingChanged(this, isChecked)
        }
        binding.sfxSwitch.setOnCheckedChangeListener { _, isChecked ->
            GameAudioManager.onSfxSettingChanged(this, isChecked)
        }

        binding.backButton.setOnClickListener { finish() }
        binding.backIconButton.setOnClickListener { finish() }
    }
}
