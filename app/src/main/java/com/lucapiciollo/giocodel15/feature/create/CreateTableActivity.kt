package com.lucapiciollo.giocodel15.feature.create

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lucapiciollo.giocodel15.R
import com.lucapiciollo.giocodel15.databinding.ActivityCreateTableBinding

class CreateTableActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateTableBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateTableBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.continueButton.setOnClickListener {
            val gridSize = when (binding.gridSizeGroup.checkedButtonId) {
                R.id.grid3Button -> 3
                R.id.grid5Button -> 5
                R.id.grid6Button -> 6
                else -> 4
            }

            // The selected configuration will be passed to LobbyActivity
            // when the Nearby/lobby milestone is introduced.
            binding.continueButton.contentDescription = "Griglia ${gridSize}x${gridSize} selezionata"
        }
    }
}
