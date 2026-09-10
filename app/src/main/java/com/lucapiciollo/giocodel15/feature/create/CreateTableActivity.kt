package com.lucapiciollo.giocodel15.feature.create

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lucapiciollo.giocodel15.R
import com.lucapiciollo.giocodel15.databinding.ActivityCreateTableBinding
import com.lucapiciollo.giocodel15.feature.lobby.LobbyActivity
import java.util.UUID

class CreateTableActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateTableBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateTableBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.continueButton.setOnClickListener {
            startActivity(
                Intent(this, LobbyActivity::class.java).apply {
                    putExtra(LobbyActivity.EXTRA_IS_HOST, true)
                    putExtra(LobbyActivity.EXTRA_GRID_SIZE, selectedGridSize())
                    putExtra(LobbyActivity.EXTRA_TABLE_ID, UUID.randomUUID().toString())
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
