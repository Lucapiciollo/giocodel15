package com.lucapiciollo.giocodel15.game.engine

import com.lucapiciollo.giocodel15.game.model.PuzzleState
import kotlin.math.abs

object PuzzleEngine {

    fun canMove(state: PuzzleState, tileIndex: Int): Boolean {
        if (tileIndex !in state.tiles.indices) return false
        if (state.tiles[tileIndex] == PuzzleState.EMPTY_TILE) return false

        val emptyIndex = state.emptyIndex
        val tileRow = tileIndex / state.size
        val tileCol = tileIndex % state.size
        val emptyRow = emptyIndex / state.size
        val emptyCol = emptyIndex % state.size

        return abs(tileRow - emptyRow) + abs(tileCol - emptyCol) == 1
    }

    fun move(state: PuzzleState, tileIndex: Int): PuzzleState {
        if (!canMove(state, tileIndex)) return state

        val updatedTiles = state.tiles.toMutableList()
        val emptyIndex = state.emptyIndex
        updatedTiles[emptyIndex] = updatedTiles[tileIndex]
        updatedTiles[tileIndex] = PuzzleState.EMPTY_TILE

        return state.copy(
            tiles = updatedTiles,
            moves = state.moves + 1
        )
    }

    fun movableTileIndices(state: PuzzleState): List<Int> {
        val emptyIndex = state.emptyIndex
        val row = emptyIndex / state.size
        val col = emptyIndex % state.size
        val result = ArrayList<Int>(4)

        if (row > 0) result += emptyIndex - state.size
        if (row < state.size - 1) result += emptyIndex + state.size
        if (col > 0) result += emptyIndex - 1
        if (col < state.size - 1) result += emptyIndex + 1

        return result
    }
}
