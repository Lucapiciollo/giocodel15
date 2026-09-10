package com.lucapiciollo.giocodel15.multiplayer.ranking

import com.lucapiciollo.giocodel15.multiplayer.model.TableScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TableWinnerResolverTest {

    @Test
    fun `ranks by wins descending`() {
        val scores = listOf(
            TableScore("a", "Alice", wins = 1),
            TableScore("b", "Bob", wins = 3),
            TableScore("c", "Carol", wins = 2)
        )
        val ranked = TableWinnerResolver.rankedScores(scores)
        assertEquals(listOf("b", "c", "a"), ranked.map { it.playerId })
    }

    @Test
    fun `ties on wins are broken alphabetically by name`() {
        val scores = listOf(
            TableScore("z", "Zoe", wins = 2),
            TableScore("a", "Amy", wins = 2)
        )
        val ranked = TableWinnerResolver.rankedScores(scores)
        assertEquals(listOf("a", "z"), ranked.map { it.playerId })
    }

    @Test
    fun `resolve returns null when nobody reached targetWins`() {
        val scores = listOf(TableScore("a", "Alice", wins = 2))
        assertNull(TableWinnerResolver.resolve(scores, targetWins = 3))
    }

    @Test
    fun `resolve returns the player who reached targetWins`() {
        val scores = listOf(
            TableScore("a", "Alice", wins = 2),
            TableScore("b", "Bob", wins = 3)
        )
        assertEquals("b", TableWinnerResolver.resolve(scores, targetWins = 3)?.playerId)
    }

    @Test
    fun `resolve breaks ties among multiple winners by most wins then name`() {
        val scores = listOf(
            TableScore("b", "Bob", wins = 3),
            TableScore("a", "Alice", wins = 4)
        )
        assertEquals("a", TableWinnerResolver.resolve(scores, targetWins = 3)?.playerId)
    }
}
