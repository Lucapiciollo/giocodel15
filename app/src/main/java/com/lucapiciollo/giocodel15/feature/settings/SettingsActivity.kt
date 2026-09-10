package com.lucapiciollo.giocodel15.feature.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lucapiciollo.giocodel15.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        binding.musicSwitch.isChecked = prefs.getBoolean(KEY_MUSIC, true)
        binding.effectsSwitch.isChecked = prefs.getBoolean(KEY_EFFECTS, true)

        binding.musicSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_MUSIC, checked).apply()
        }
        binding.effectsSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_EFFECTS, checked).apply()
        }
        binding.backButton.setOnClickListener { finish() }
        binding.homeButton.setOnClickListener { finish() }
    }

    companion object {
        private const val PREFS_NAME = "game_ui_preferences"
        private const val KEY_MUSIC = "music_enabled"
        private const val KEY_EFFECTS = "effects_enabled"
    }
}
