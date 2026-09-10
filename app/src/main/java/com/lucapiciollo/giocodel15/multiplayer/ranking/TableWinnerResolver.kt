package com.lucapiciollo.giocodel15.multiplayer.ranking

import com.lucapiciollo.giocodel15.multiplayer.model.TableScore

/**
 * Orders table standings and resolves whether a table has an overall winner, given a
 * `targetWins` configured on table creation. Pure Kotlin so it can be unit tested.
 */
object TableWinnerResolver {

    /** Standings ordered by wins (desc), then player name (asc) for stable, readable ties. */
    fun rankedScores(scores: Collection<TableScore>): List<TableScore> = scores.sortedWith(
        compareByDescending<TableScore> { it.wins }.thenBy { it.playerName.lowercase() }
    )

    /**
     * Returns the first player whose wins reached [targetWins], or null if nobody has yet.
     * When multiple players reach it in the same update, the one with the most wins wins the
     * table (ties broken alphabetically, matching [rankedScores]).
     */
    fun resolve(scores: Collection<TableScore>, targetWins: Int): TableScore? {
        if (targetWins <= 0) return null
        return rankedScores(scores).firstOrNull { it.wins >= targetWins }
    }
}
