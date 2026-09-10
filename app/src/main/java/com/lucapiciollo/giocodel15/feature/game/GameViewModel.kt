package com.lucapiciollo.giocodel15.feature.game

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import com.lucapiciollo.giocodel15.game.engine.PuzzleGenerator
import com.lucapiciollo.giocodel15.game.model.PuzzleState

class GameViewModel : ViewModel() {

    var puzzleState: PuzzleState? = null
        private set

    var seed: Long? = null
        private set

    var startedAtElapsedMs: Long? = null
        private set

    var finishedElapsedMs: Long? = null
        private set

    fun initialize(gridSize: Int, requestedSeed: Long) {
        if (puzzleState != null) return

        seed = requestedSeed
        puzzleState = PuzzleGenerator.generate(
            size = gridSize,
            seed = requestedSeed
        )
        startedAtElapsedMs = SystemClock.elapsedRealtime()
    }

    fun updateState(newState: PuzzleState) {
        puzzleState = newState
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
