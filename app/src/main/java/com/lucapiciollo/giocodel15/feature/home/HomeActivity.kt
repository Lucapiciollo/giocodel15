package com.lucapiciollo.giocodel15.feature.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.lucapiciollo.giocodel15.R
import com.lucapiciollo.giocodel15.core.ui.applyHorizontalGradient
import com.lucapiciollo.giocodel15.core.ui.applyNavigationBarBottomInset
import com.lucapiciollo.giocodel15.core.ui.applyPressScaleAnimation
import com.lucapiciollo.giocodel15.core.ui.applyStatusBarTopInset
import com.lucapiciollo.giocodel15.core.ui.confirmAction
import com.lucapiciollo.giocodel15.core.ui.playEntranceAnimation
import com.lucapiciollo.giocodel15.core.update.GameUpdateChecker
import com.lucapiciollo.giocodel15.databinding.ActivityHomeBinding
import com.lucapiciollo.giocodel15.feature.create.CreateTableActivity
import com.lucapiciollo.giocodel15.feature.nearby.NearbyTablesActivity
import com.lucapiciollo.giocodel15.feature.settings.SettingsActivity
import com.lucapiciollo.giocodel15.feature.solo.SoloSetupActivity

/** Home/menu screen: the "hub" the user starts on and always returns to after a game, a table,
 * or the settings screen. Also the natural place to centralize the Play Store update check,
 * since (unlike the splash screen) it's visited repeatedly and stays alive long enough for the
 * in-app update flow to complete. */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyStatusBarTopInset()
        binding.root.applyNavigationBarBottomInset()
        binding.root.playEntranceAnimation()

        binding.title.applyHorizontalGradient(
            ContextCompat.getColor(this, R.color.game_primary),
            ContextCompat.getColor(this, R.color.game_secondary)
        )

        binding.createTableButton.applyPressScaleAnimation()
        binding.joinTableButton.applyPressScaleAnimation()
        binding.soloPlayButton.applyPressScaleAnimation()
        binding.settingsEntry.applyPressScaleAnimation()

        binding.createTableButton.setOnClickListener {
            startActivity(Intent(this, CreateTableActivity::class.java))
        }

        binding.joinTableButton.setOnClickListener {
            startActivity(Intent(this, NearbyTablesActivity::class.java))
        }

        binding.soloPlayButton.setOnClickListener {
            startActivity(Intent(this, SoloSetupActivity::class.java))
        }

        binding.settingsEntry.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        GameUpdateChecker.checkForUpdate(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                confirmAction(
                    R.string.exit_app_title,
                    R.string.exit_app_message,
                    R.string.exit_app_confirm,
                    R.string.exit_app_cancel
                ) { finish() }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        GameUpdateChecker.resumeUpdateIfInProgress(this)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == GameUpdateChecker.REQ_UPDATE && resultCode != RESULT_OK) {
            // User cancelled or the flow failed; it will simply be retried next time
            // HomeActivity is created/resumed, no extra handling needed here.
        }
    }

    override fun onDestroy() {
        GameUpdateChecker.unregister()
        super.onDestroy()
    }
}
