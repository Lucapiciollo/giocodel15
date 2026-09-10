package com.lucapiciollo.giocodel15.multiplayer.model

/**
 * Outcome reported by a player at the end of a round.
 *
 * [boardHash] is the hash of the *initial* shuffled board the player played on (see
 * `PuzzleEngine.boardHash`), used by the host to confirm the player actually played the
 * board generated from the round's `gridSize`/`seed`. [moveSequence] is optional: when present
 * the host can replay it with `PuzzleEngine.replay` to confirm it truly leads to the solution.
 */
data class PlayerResult(
    val playerId: String,
    val playerName: String,
    val roundId: String,
    val elapsedMs: Long,
    val moves: Int,
    val finishedAt: Long,
    val boardHash: String,
    val moveSequence: List<Int> = emptyList()
)
