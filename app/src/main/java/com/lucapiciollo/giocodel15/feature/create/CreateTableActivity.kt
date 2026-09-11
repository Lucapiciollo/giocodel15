package com.lucapiciollo.giocodel15.feature.create

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.lucapiciollo.giocodel15.R
import com.lucapiciollo.giocodel15.core.ui.applyNavigationBarBottomInset
import com.lucapiciollo.giocodel15.core.ui.applyStatusBarTopInset
import com.lucapiciollo.giocodel15.core.ui.playEntranceAnimation
import com.lucapiciollo.giocodel15.databinding.ActivityCreateTableBinding
import com.lucapiciollo.giocodel15.feature.lobby.LobbyActivity
import com.lucapiciollo.giocodel15.multiplayer.model.RoundEndMode
import com.lucapiciollo.giocodel15.multiplayer.model.TableMode
import java.util.UUID

class CreateTableActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateTableBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateTableBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyStatusBarTopInset()
        binding.root.applyNavigationBarBottomInset()
        binding.root.playEntranceAnimation()

        binding.modeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val oneVsOne = checkedId == R.id.modeOneVsOneButton
            if (oneVsOne) binding.maxPlayersGroup.check(R.id.max2Button)
            setGroupEnabled(binding.maxPlayersGroup, !oneVsOne)
            binding.maxPlayersLabel.isEnabled = !oneVsOne
            refreshSummary()
        }

        binding.gridSizeGroup.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) refreshSummary()
        }
        binding.maxPlayersGroup.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) refreshSummary()
        }
        binding.targetWinsGroup.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) refreshSummary()
        }
        binding.roundEndModeGroup.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) refreshSummary()
        }
        refreshSummary()

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
                    putExtra(LobbyActivity.EXTRA_ROUND_END_MODE, selectedRoundEndMode().name)
                }
            )
        }
    }

    private fun setGroupEnabled(group: ViewGroup, enabled: Boolean) {
        group.isEnabled = enabled
        group.alpha = if (enabled) 1f else DISABLED_ALPHA
        for (index in 0 until group.childCount) {
            group.getChildAt(index).isEnabled = enabled
        }
    }

    /** Updates the read-only configuration summary card to reflect the currently selected
     * mode/grid/players/wins, so the player sees a plain-language recap before confirming. */
    private fun refreshSummary() {
        val mode = selectedMode()
        val modeLabel = getString(
            if (mode == TableMode.ONE_VS_ONE) R.string.mode_one_vs_one else R.string.mode_table
        )
        val maxPlayers = if (mode == TableMode.ONE_VS_ONE) 2 else selectedMaxPlayers()
        val gridSize = selectedGridSize()
        binding.configSummaryText.text = getString(
            R.string.create_table_summary_body,
            modeLabel,
            gridSize,
            gridSize,
            maxPlayers,
            selectedTargetWins()
        )
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

    private fun selectedRoundEndMode(): RoundEndMode = when (binding.roundEndModeGroup.checkedButtonId) {
        R.id.roundEndSprintButton -> RoundEndMode.SPRINT
        R.id.roundEndPodiumButton -> RoundEndMode.PODIUM
        else -> RoundEndMode.FULL_RANKING
    }

    companion object {
        private const val DISABLED_ALPHA = 0.45f
    }
}
