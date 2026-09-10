package com.lucapiciollo.giocodel15.multiplayer.ranking

import com.lucapiciollo.giocodel15.multiplayer.model.PlayerResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RankingCalculatorTest {

    private fun result(id: String, elapsedMs: Long, finishedAt: Long = 0L, moves: Int = 10) = PlayerResult(
        playerId = id,
        playerName = id,
        roundId = "r",
        elapsedMs = elapsedMs,
        moves = moves,
        finishedAt = finishedAt,
        boardHash = "hash"
    )

    @Test
    fun `orders by elapsed time ascending`() {
        val ranked = RankingCalculator.rank(
            listOf(result("slow", 5000L), result("fast", 1000L), result("mid", 3000L))
        )
        assertEquals(listOf("fast", "mid", "slow"), ranked.map { it.playerId })
    }

    @Test
    fun `ties on elapsed time are broken by finishedAt`() {
        val ranked = RankingCalculator.rank(
            listOf(
                result("later", 1000L, finishedAt = 200L),
                result("earlier", 1000L, finishedAt = 100L)
            )
        )
        assertEquals(listOf("earlier", "later"), ranked.map { it.playerId })
    }

    @Test
    fun `ties on elapsed and finishedAt are broken by moves`() {
        val ranked = RankingCalculator.rank(
            listOf(
                result("moreMoves", 1000L, finishedAt = 100L, moves = 50),
                result("fewerMoves", 1000L, finishedAt = 100L, moves = 20)
            )
        )
        assertEquals(listOf("fewerMoves", "moreMoves"), ranked.map { it.playerId })
    }

    @Test
    fun `positionOf returns the 1-based rank`() {
        val results = listOf(result("slow", 5000L), result("fast", 1000L))
        assertEquals(1, RankingCalculator.positionOf(results, "fast"))
        assertEquals(2, RankingCalculator.positionOf(results, "slow"))
    }

    @Test
    fun `positionOf returns null for an unknown player`() {
        val results = listOf(result("only", 1000L))
        assertNull(RankingCalculator.positionOf(results, "missing"))
    }
}
