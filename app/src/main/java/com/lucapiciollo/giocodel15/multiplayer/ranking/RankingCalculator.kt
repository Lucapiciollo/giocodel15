package com.lucapiciollo.giocodel15.multiplayer.ranking

import com.lucapiciollo.giocodel15.multiplayer.model.PlayerResult

/**
 * Orders validated round results. Independent from Android/Nearby so it can be unit tested and
 * reused by the host (to decide the live ranking) and by clients (to render it).
 */
object RankingCalculator {

    /**
     * Sorts by [PlayerResult.elapsedMs] (lower is better). [PlayerResult.finishedAt] is the
     * tie-break for equal times, followed by [PlayerResult.moves] for full determinism.
     */
    fun rank(results: Collection<PlayerResult>): List<PlayerResult> = results.sortedWith(
        compareBy<PlayerResult> { it.elapsedMs }
            .thenBy { it.finishedAt }
            .thenBy { it.moves }
    )

    /** 1-based position of [playerId] in the ranking, or null if not present. */
    fun positionOf(results: Collection<PlayerResult>, playerId: String): Int? {
        val index = rank(results).indexOfFirst { it.playerId == playerId }
        return if (index >= 0) index + 1 else null
    }
}
