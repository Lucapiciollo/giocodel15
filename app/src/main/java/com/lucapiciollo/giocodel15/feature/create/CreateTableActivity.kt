package com.lucapiciollo.giocodel15.feature.create

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lucapiciollo.giocodel15.R
import com.lucapiciollo.giocodel15.databinding.ActivityCreateTableBinding
import com.lucapiciollo.giocodel15.feature.game.GameActivity

class CreateTableActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateTableBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateTableBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.continueButton.setOnClickListener {
            startActivity(
                Intent(this, GameActivity::class.java).apply {
                    putExtra(GameActivity.EXTRA_GRID_SIZE, selectedGridSize())
                    putExtra(GameActivity.EXTRA_SEED, System.currentTimeMillis())
                }
            )
        }
    }

    private fun selectedGridSize(): Int = when (binding.gridSizeGroup.checkedButtonId) {
        R.id.grid3Button -> 3
        R.id.grid5Button -> 5
        R.id.grid6Button -> 6
        else -> 4
    }
}
