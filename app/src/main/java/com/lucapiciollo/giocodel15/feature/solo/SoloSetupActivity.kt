package com.lucapiciollo.giocodel15.feature.solo

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lucapiciollo.giocodel15.R
import com.lucapiciollo.giocodel15.core.ui.applyNavigationBarBottomInset
import com.lucapiciollo.giocodel15.core.ui.applyStatusBarTopInset
import com.lucapiciollo.giocodel15.core.ui.playEntranceAnimation
import com.lucapiciollo.giocodel15.databinding.ActivitySoloSetupBinding

/** Entry screen for the offline single-player practice mode: pick a grid size and start
 * solving alone, with no Nearby/TableSession involvement whatsoever. */
class SoloSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySoloSetupBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySoloSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyStatusBarTopInset()
        binding.root.applyNavigationBarBottomInset()
        binding.root.playEntranceAnimation()

        binding.continueButton.setOnClickListener {
            startActivity(
                Intent(this, SoloGameActivity::class.java).apply {
                    putExtra(SoloGameActivity.EXTRA_GRID_SIZE, selectedGridSize())
                    putExtra(SoloGameActivity.EXTRA_SEED, System.currentTimeMillis())
                }
            )
        }
    }

    private fun selectedGridSize(): Int = when (binding.gridSizeGroup.checkedButtonId) {
        R.id.grid3Button -> 3
        R.id.grid5Button -> 5
        R.id.grid6Button -> 6
        else -> DEFAULT_GRID_SIZE
    }

    companion object {
        private const val DEFAULT_GRID_SIZE = 4
    }
}
