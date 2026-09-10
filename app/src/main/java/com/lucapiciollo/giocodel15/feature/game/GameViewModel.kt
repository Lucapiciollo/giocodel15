package com.lucapiciollo.giocodel15.feature.game

import androidx.lifecycle.ViewModel
import com.lucapiciollo.giocodel15.game.engine.PuzzleGenerator
import com.lucapiciollo.giocodel15.game.model.PuzzleState

class GameViewModel : ViewModel() {

    var puzzleState: PuzzleState? = null
        private set

    var seed: Long? = null
        private set

    fun initialize(gridSize: Int, requestedSeed: Long) {
        if (puzzleState != null) return

        seed = requestedSeed
        puzzleState = PuzzleGenerator.generate(
            size = gridSize,
            seed = requestedSeed
        )
    }

    fun updateState(newState: PuzzleState) {
        puzzleState = newState
    }
}
