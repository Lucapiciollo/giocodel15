package com.lucapiciollo.giocodel15.game.engine

import com.lucapiciollo.giocodel15.game.model.PuzzleState
import kotlin.random.Random

object PuzzleGenerator {

    private const val DEFAULT_SHUFFLE_MULTIPLIER = 24

    fun generate(
        size: Int,
        seed: Long,
        shuffleMoves: Int = defaultShuffleMoves(size)
    ): PuzzleState {
        require(size >= PuzzleState.MIN_SIZE)
        require(shuffleMoves > 0)

        val random = Random(seed)
        var state = PuzzleState.solved(size)
        var previousEmptyIndex: Int? = null

        repeat(shuffleMoves) {
            val candidates = PuzzleEngine.movableTileIndices(state)
                .filterNot { it == previousEmptyIndex }
                .ifEmpty { PuzzleEngine.movableTileIndices(state) }

            val tileIndex = candidates[random.nextInt(candidates.size)]
            previousEmptyIndex = state.emptyIndex
            state = PuzzleEngine.move(state, tileIndex)
        }

        if (state.isSolved) {
            val tileIndex = PuzzleEngine.movableTileIndices(state).first()
            state = PuzzleEngine.move(state, tileIndex)
        }

        return state.copy(moves = 0)
    }

    fun defaultShuffleMoves(size: Int): Int = size * size * DEFAULT_SHUFFLE_MULTIPLIER
}
