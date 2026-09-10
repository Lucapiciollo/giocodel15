package com.lucapiciollo.giocodel15.game.engine

import com.lucapiciollo.giocodel15.game.model.PuzzleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleEngineTest {

    @Test
    fun `move updates only when target tile is adjacent to empty slot`() {
        val state = PuzzleState(size = 3, tiles = listOf(1, 2, 3, 4, 5, 6, 7, 0, 8))
        // empty at index 7 (row 2, col 1); index 4 (row1,col1) is adjacent
        assertTrue(PuzzleEngine.canMove(state, 4))
        // index 0 (row0,col0) is not adjacent
        assertFalse(PuzzleEngine.canMove(state, 0))
    }

    @Test
    fun `invalid move returns the same state unchanged`() {
        val state = PuzzleState(size = 3, tiles = listOf(1, 2, 3, 4, 5, 6, 7, 0, 8))
        val result = PuzzleEngine.move(state, 0)
        assertEquals(state, result)
    }

    @Test
    fun `valid move swaps tile with empty slot and increments moves`() {
        val state = PuzzleState(size = 3, tiles = listOf(1, 2, 3, 4, 5, 6, 7, 0, 8))
        val result = PuzzleEngine.move(state, 4)
        assertEquals(0, result.tiles[4])
        assertEquals(5, result.tiles[7])
        assertEquals(1, result.moves)
    }

    @Test
    fun `boardHash is stable for identical boards and differs for different ones`() {
        val a = PuzzleState(size = 3, tiles = listOf(1, 2, 3, 4, 5, 6, 7, 0, 8))
        val b = PuzzleState(size = 3, tiles = listOf(1, 2, 3, 4, 5, 6, 7, 0, 8))
        val c = PuzzleState(size = 3, tiles = listOf(1, 2, 3, 4, 5, 6, 0, 7, 8))

        assertEquals(PuzzleEngine.boardHash(a), PuzzleEngine.boardHash(b))
        assertTrue(PuzzleEngine.boardHash(a) != PuzzleEngine.boardHash(c))
    }

    @Test
    fun `replay reaching the solved state marks it solved`() {
        val size = 3
        val seed = 5L
        val shuffled = PuzzleGenerator.generate(size, seed)

        val moves = bfsSolve(shuffled)
        val replayed = PuzzleEngine.replay(size, seed, moves)
        assertTrue(replayed.isSolved)
    }

    @Test
    fun `replay ignores illegal move indices instead of throwing`() {
        val size = 3
        val seed = 3L
        val replayed = PuzzleEngine.replay(size, seed, listOf(-1, 999, 0))
        // No exception thrown, and the board remains a valid permutation.
        assertEquals(size * size, replayed.tiles.size)
    }

    /** Breadth-first search over legal moves; small enough boards (8-puzzle) make this instant. */
    private fun bfsSolve(start: PuzzleState): List<Int> {
        if (start.isSolved) return emptyList()
        val visited = hashSetOf(start.tiles)
        val queue = ArrayDeque<Pair<PuzzleState, List<Int>>>()
        queue.add(start to emptyList())
        while (queue.isNotEmpty()) {
            val (state, path) = queue.removeFirst()
            for (candidate in PuzzleEngine.movableTileIndices(state)) {
                val next = PuzzleEngine.move(state, candidate)
                if (next.tiles in visited) continue
                val nextPath = path + candidate
                if (next.isSolved) return nextPath
                visited += next.tiles
                queue.add(next to nextPath)
            }
        }
        error("no solution found")
    }
}
