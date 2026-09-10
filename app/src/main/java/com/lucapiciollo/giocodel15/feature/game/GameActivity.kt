package com.lucapiciollo.giocodel15.feature.game

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.lucapiciollo.giocodel15.core.ui.PuzzleBoardConfig
import com.lucapiciollo.giocodel15.databinding.ActivityGameBinding

class GameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameBinding
    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val gridSize = intent.getIntExtra(EXTRA_GRID_SIZE, DEFAULT_GRID_SIZE)
        val seed = intent.getLongExtra(EXTRA_SEED, System.currentTimeMillis())

        viewModel.initialize(gridSize, seed)

        binding.puzzleBoard.configure(
            PuzzleBoardConfig(
                interactionEnabled = true,
                showNumbers = true,
                hapticFeedback = true
            )
        )

        viewModel.puzzleState?.let(binding.puzzleBoard::setPuzzleState)
        renderStats()

        binding.puzzleBoard.setOnStateChangedListener { state ->
            viewModel.updateState(state)
            renderStats()
        }

        binding.puzzleBoard.setOnSolvedListener {
            binding.gameStatus.text = "Completato!"
        }
    }

    private fun renderStats() {
        val state = viewModel.puzzleState ?: return
        binding.movesValue.text = state.moves.toString()
        binding.gridValue.text = "${state.size}×${state.size}"
        binding.gameStatus.text = if (state.isSolved) "Completato!" else "In gioco"
    }

    companion object {
        const val EXTRA_GRID_SIZE = "extra_grid_size"
        const val EXTRA_SEED = "extra_seed"
        private const val DEFAULT_GRID_SIZE = 4
    }
}
