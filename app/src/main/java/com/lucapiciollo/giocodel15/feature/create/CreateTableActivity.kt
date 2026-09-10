package com.lucapiciollo.giocodel15.feature.create

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lucapiciollo.giocodel15.R
import com.lucapiciollo.giocodel15.databinding.ActivityCreateTableBinding
import com.lucapiciollo.giocodel15.feature.lobby.LobbyActivity
import com.lucapiciollo.giocodel15.multiplayer.model.TableMode
import java.util.UUID

class CreateTableActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateTableBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateTableBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.modeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val oneVsOne = checkedId == R.id.modeOneVsOneButton
            binding.maxPlayersGroup.isEnabled = !oneVsOne
            binding.maxPlayersLabel.isEnabled = !oneVsOne
            if (oneVsOne) binding.maxPlayersGroup.check(R.id.max2Button)
        }

        binding.continueButton.setOnClickListener {
            val mode = selectedMode()
            val maxPlayers = if (mode == TableMode.ONE_VS_ONE) 2 else selectedMaxPlayers()
            startActivity(
                Intent(this, LobbyActivity::class.java).apply {
                    putExtra(LobbyActivity.EXTRA_IS_HOST, true)
                    putExtra(LobbyActivity.EXTRA_GRID_SIZE, selectedGridSize())
                    putExtra(LobbyActivity.EXTRA_TABLE_ID, UUID.randomUUID().toString())
                    putExtra(LobbyActivity.EXTRA_TABLE_MODE, mode.name)
                    putExtra(LobbyActivity.EXTRA_MAX_PLAYERS, maxPlayers)
                    putExtra(LobbyActivity.EXTRA_TARGET_WINS, selectedTargetWins())
                }
            )
        }
    }

    private fun selectedMode(): TableMode = if (
        binding.modeGroup.checkedButtonId == R.id.modeOneVsOneButton
    ) TableMode.ONE_VS_ONE else TableMode.TABLE

    private fun selectedGridSize(): Int = when (binding.gridSizeGroup.checkedButtonId) {
        R.id.grid3Button -> 3
        R.id.grid5Button -> 5
        R.id.grid6Button -> 6
        else -> 4
    }

    private fun selectedMaxPlayers(): Int = when (binding.maxPlayersGroup.checkedButtonId) {
        R.id.max2Button -> 2
        R.id.max6Button -> 6
        R.id.max8Button -> 8
        else -> 4
    }

    private fun selectedTargetWins(): Int = when (binding.targetWinsGroup.checkedButtonId) {
        R.id.wins1Button -> 1
        R.id.wins5Button -> 5
        else -> 3
    }
}
