package com.lucapiciollo.giocodel15.feature.solo

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.lucapiciollo.giocodel15.R
import com.lucapiciollo.giocodel15.core.ui.PuzzleBoardConfig
import com.lucapiciollo.giocodel15.core.ui.applyStatusBarTopInset
import com.lucapiciollo.giocodel15.databinding.ActivitySoloGameBinding
import com.lucapiciollo.giocodel15.feature.game.GameViewModel
import com.lucapiciollo.giocodel15.feature.home.HomeActivity
import java.util.Locale

/** Fully offline single-player practice mode: solve the puzzle alone, no Nearby transport,
 * no [com.lucapiciollo.giocodel15.multiplayer.session.TableSession] involvement. Reuses the
 * same pure puzzle engine/view-model as the multiplayer [com.lucapiciollo.giocodel15.feature.game.GameActivity]. */
class SoloGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySoloGameBinding
    private val viewModel: GameViewModel by viewModels()
    private val handler = Handler(Looper.getMainLooper())

    private val gridSize by lazy { intent.getIntExtra(EXTRA_GRID_SIZE, DEFAULT_GRID_SIZE) }

    private val timerTick = object : Runnable {
        override fun run() {
            binding.timerValue.text = formatElapsed(viewModel.elapsedMs())
            if (viewModel.finishedElapsedMs == null) handler.postDelayed(this, TIMER_REFRESH_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySoloGameBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyStatusBarTopInset()

        val seed = intent.getLongExtra(EXTRA_SEED, System.currentTimeMillis())
        viewModel.initialize(gridSize, seed)

        val alreadyFinished = viewModel.finishedElapsedMs != null
        binding.puzzleBoard.configure(
            PuzzleBoardConfig(
                interactionEnabled = !alreadyFinished,
                showNumbers = true,
                hapticFeedback = true
            )
        )
        viewModel.puzzleState?.let(binding.puzzleBoard::setPuzzleState)
        renderStats()

        if (alreadyFinished) {
            showSolvedState(viewModel.finishedElapsedMs ?: 0L)
        } else {
            binding.gameStatus.setText(R.string.game_status_playing)
            handler.post(timerTick)
        }

        binding.puzzleBoard.setOnStateChangedListener { state ->
            viewModel.updateState(state)
            renderStats()
        }
        binding.puzzleBoard.setOnTileMovedListener { tileIndex -> viewModel.recordMove(tileIndex) }
        binding.puzzleBoard.setOnSolvedListener { onPuzzleSolved() }

        binding.replayButton.setOnClickListener { replay() }
        binding.goHomeButton.setOnClickListener { goHome() }
    }

    private fun onPuzzleSolved() {
        if (viewModel.finishedElapsedMs != null) return
        val elapsed = viewModel.finish()
        binding.puzzleBoard.configure(
            PuzzleBoardConfig(interactionEnabled = false, showNumbers = true, hapticFeedback = true)
        )
        showSolvedState(elapsed)
    }

    private fun showSolvedState(elapsedMs: Long) {
        val moves = viewModel.puzzleState?.moves ?: 0
        binding.timerValue.text = formatElapsed(elapsedMs)
        binding.gameStatus.text = getString(R.string.solo_result_summary, formatElapsed(elapsedMs), moves)
        binding.replayButton.isVisible = true
        binding.goHomeButton.isVisible = true
    }

    private fun replay() {
        startActivity(
            Intent(this, SoloGameActivity::class.java).apply {
                putExtra(EXTRA_GRID_SIZE, gridSize)
                putExtra(EXTRA_SEED, System.currentTimeMillis())
            }
        )
        finish()
    }

    private fun goHome() {
        startActivity(
            Intent(this, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    private fun renderStats() {
        val state = viewModel.puzzleState ?: return
        binding.movesValue.text = state.moves.toString()
        binding.gridValue.text = getString(R.string.game_grid_value, state.size, state.size)
    }

    private fun formatElapsed(elapsedMs: Long): String {
        val minutes = elapsedMs / 60_000
        val seconds = (elapsedMs % 60_000) / 1000
        val millis = elapsedMs % 1000
        return String.format(Locale.getDefault(), "%02d:%02d.%03d", minutes, seconds, millis)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_GRID_SIZE = "extra_grid_size"
        const val EXTRA_SEED = "extra_seed"
        private const val DEFAULT_GRID_SIZE = 4
        private const val TIMER_REFRESH_MS = 50L
    }
}
