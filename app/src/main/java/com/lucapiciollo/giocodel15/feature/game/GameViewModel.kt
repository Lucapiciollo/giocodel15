package com.lucapiciollo.giocodel15.feature.game

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import com.lucapiciollo.giocodel15.game.engine.PuzzleEngine
import com.lucapiciollo.giocodel15.game.engine.PuzzleGenerator
import com.lucapiciollo.giocodel15.game.model.PuzzleState

class GameViewModel : ViewModel() {

    var puzzleState: PuzzleState? = null
        private set

    var seed: Long? = null
        private set

    /** Hash of the freshly generated (pre-shuffle-replay) board, sent to the host so it can
     * confirm this device played the exact board defined by the round's gridSize/seed. */
    var initialBoardHash: String? = null
        private set

    var startedAtElapsedMs: Long? = null
        private set

    var finishedElapsedMs: Long? = null
        private set

    private val _moveSequence = mutableListOf<Int>()
    val moveSequence: List<Int> get() = _moveSequence.toList()

    fun initialize(gridSize: Int, requestedSeed: Long) {
        if (puzzleState != null) return

        seed = requestedSeed
        val initialState = PuzzleGenerator.generate(size = gridSize, seed = requestedSeed)
        initialBoardHash = PuzzleEngine.boardHash(initialState)
        puzzleState = initialState
        startedAtElapsedMs = SystemClock.elapsedRealtime()
    }

    fun updateState(newState: PuzzleState) {
        puzzleState = newState
    }

    fun recordMove(tileIndex: Int) {
        _moveSequence += tileIndex
    }

    fun elapsedMs(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Long {
        finishedElapsedMs?.let { return it }
        val started = startedAtElapsedMs ?: return 0L
        return (nowElapsedMs - started).coerceAtLeast(0L)
    }

    fun finish(): Long {
        finishedElapsedMs?.let { return it }
        val elapsed = elapsedMs()
        finishedElapsedMs = elapsed
        return elapsed
    }
}

