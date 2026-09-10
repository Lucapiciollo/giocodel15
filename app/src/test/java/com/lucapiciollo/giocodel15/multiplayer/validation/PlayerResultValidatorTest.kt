package com.lucapiciollo.giocodel15.multiplayer.validation

import com.lucapiciollo.giocodel15.game.engine.PuzzleEngine
import com.lucapiciollo.giocodel15.game.engine.PuzzleGenerator
import com.lucapiciollo.giocodel15.multiplayer.model.PlayerResult
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerResultValidatorTest {

    private val gridSize = 3
    private val seed = 123L
    private val roundId = "round-1"
    private val startAtMs = 1_000_000L
    private val validBoardHash = PuzzleEngine.boardHash(PuzzleGenerator.generate(gridSize, seed))

    private fun context(
        knownPlayerIds: Set<String> = setOf("p1", "p2"),
        alreadyAccepted: Set<String> = emptySet()
    ) = RoundValidationContext(
        roundId = roundId,
        gridSize = gridSize,
        seed = seed,
        startAtMs = startAtMs,
        knownPlayerIds = knownPlayerIds,
        alreadyAcceptedPlayerIds = alreadyAccepted
    )

    private fun validResult(
        playerId: String = "p1",
        elapsedMs: Long = 5_000L,
        moves: Int = 40,
        finishedAt: Long = startAtMs + elapsedMs,
        boardHash: String = validBoardHash,
        moveSequence: List<Int> = emptyList()
    ) = PlayerResult(
        playerId = playerId,
        playerName = playerId,
        roundId = roundId,
        elapsedMs = elapsedMs,
        moves = moves,
        finishedAt = finishedAt,
        boardHash = boardHash,
        moveSequence = moveSequence
    )

    @Test
    fun `accepts a plausible result`() {
        val outcome = PlayerResultValidator().validate(validResult(), context())
        assertTrue(outcome is ValidationResult.Valid)
    }

    @Test
    fun `rejects mismatched roundId`() {
        val result = validResult().copy(roundId = "other-round")
        val outcome = PlayerResultValidator().validate(result, context())
        assertTrue(outcome is ValidationResult.Invalid)
    }

    @Test
    fun `rejects unknown player`() {
        val result = validResult(playerId = "ghost")
        val outcome = PlayerResultValidator().validate(result, context())
        assertTrue(outcome is ValidationResult.Invalid)
    }

    @Test
    fun `rejects duplicate result for an already accepted player`() {
        val outcome = PlayerResultValidator().validate(
            validResult(playerId = "p1"),
            context(alreadyAccepted = setOf("p1"))
        )
        assertTrue(outcome is ValidationResult.Invalid)
    }

    @Test
    fun `rejects non-positive moves`() {
        val outcome = PlayerResultValidator().validate(validResult(moves = 0), context())
        assertTrue(outcome is ValidationResult.Invalid)
    }

    @Test
    fun `rejects implausibly fast elapsedMs`() {
        val outcome = PlayerResultValidator().validate(validResult(elapsedMs = 10L), context())
        assertTrue(outcome is ValidationResult.Invalid)
    }

    @Test
    fun `rejects implausibly slow elapsedMs`() {
        val outcome = PlayerResultValidator().validate(
            validResult(elapsedMs = 60 * 60 * 1000L),
            context()
        )
        assertTrue(outcome is ValidationResult.Invalid)
    }

    @Test
    fun `rejects finish timestamp before round start`() {
        val outcome = PlayerResultValidator().validate(
            validResult(finishedAt = startAtMs - 1),
            context()
        )
        assertTrue(outcome is ValidationResult.Invalid)
    }

    @Test
    fun `rejects a board hash that does not match gridSize and seed`() {
        val outcome = PlayerResultValidator().validate(
            validResult(boardHash = "bogus-hash"),
            context()
        )
        assertTrue(outcome is ValidationResult.Invalid)
    }

    @Test
    fun `rejects a move sequence that does not reach a solved board`() {
        val outcome = PlayerResultValidator().validate(
            validResult(moveSequence = listOf(0, 0, 0)),
            context()
        )
        assertTrue(outcome is ValidationResult.Invalid)
    }

    @Test
    fun `accepts a move sequence that genuinely solves the board`() {
        val shuffled = PuzzleGenerator.generate(gridSize, seed)
        val moves = bfsSolve(shuffled)

        val outcome = PlayerResultValidator().validate(
            validResult(moveSequence = moves),
            context()
        )
        assertTrue(outcome is ValidationResult.Valid)
    }

    /** Breadth-first search over legal moves; small enough boards (8-puzzle) make this instant. */
    private fun bfsSolve(start: com.lucapiciollo.giocodel15.game.model.PuzzleState): List<Int> {
        if (start.isSolved) return emptyList()
        val visited = hashSetOf(start.tiles)
        val queue = ArrayDeque<Pair<com.lucapiciollo.giocodel15.game.model.PuzzleState, List<Int>>>()
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
