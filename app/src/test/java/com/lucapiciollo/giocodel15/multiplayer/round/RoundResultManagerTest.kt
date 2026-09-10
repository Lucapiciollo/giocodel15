package com.lucapiciollo.giocodel15.multiplayer.round

import com.lucapiciollo.giocodel15.game.engine.PuzzleEngine
import com.lucapiciollo.giocodel15.game.engine.PuzzleGenerator
import com.lucapiciollo.giocodel15.multiplayer.model.PlayerResult
import com.lucapiciollo.giocodel15.multiplayer.model.RoundEndMode
import com.lucapiciollo.giocodel15.multiplayer.model.RoundParticipantStatus
import com.lucapiciollo.giocodel15.multiplayer.validation.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundResultManagerTest {

    private val gridSize = 3
    private val seed = 55L
    private val roundId = "round-x"
    private val startAtMs = System.currentTimeMillis() - 1_000L
    private val boardHash = PuzzleEngine.boardHash(PuzzleGenerator.generate(gridSize, seed))
    private val players = listOf("p1" to "Alice", "p2" to "Bob", "p3" to "Carol")

    private fun manager(endMode: RoundEndMode, timeoutMs: Long = RoundResultManager.DEFAULT_TIMEOUT_MS) =
        RoundResultManager(
            roundId = roundId,
            gridSize = gridSize,
            seed = seed,
            startAtMs = startAtMs,
            endMode = endMode,
            timeoutMs = timeoutMs,
            players = players
        )

    private fun result(playerId: String, elapsedMs: Long = 5_000L) = PlayerResult(
        playerId = playerId,
        playerName = playerId,
        roundId = roundId,
        elapsedMs = elapsedMs,
        moves = 30,
        finishedAt = startAtMs + elapsedMs,
        boardHash = boardHash
    )

    @Test
    fun `SPRINT mode completes as soon as one player finishes`() {
        val manager = manager(RoundEndMode.SPRINT)
        assertTrue(manager.tryAccept(result("p1")) is ValidationResult.Valid)
        assertTrue(manager.isComplete())
    }

    @Test
    fun `PODIUM mode completes once three players finish`() {
        val manager = manager(RoundEndMode.PODIUM)
        assertTrue(manager.tryAccept(result("p1")) is ValidationResult.Valid)
        assertFalse(manager.isComplete())
        assertTrue(manager.tryAccept(result("p2")) is ValidationResult.Valid)
        assertFalse(manager.isComplete())
        assertTrue(manager.tryAccept(result("p3")) is ValidationResult.Valid)
        assertTrue(manager.isComplete())
    }

    @Test
    fun `FULL_RANKING mode waits for every player`() {
        val manager = manager(RoundEndMode.FULL_RANKING)
        manager.tryAccept(result("p1"))
        manager.tryAccept(result("p2"))
        assertFalse(manager.isComplete())
        manager.tryAccept(result("p3"))
        assertTrue(manager.isComplete())
    }

    @Test
    fun `disconnected player before finishing is marked DISCONNECTED in final ranking`() {
        val manager = manager(RoundEndMode.FULL_RANKING)
        manager.tryAccept(result("p1"))
        manager.markDisconnected("p2")
        manager.markDisconnected("p3")
        assertTrue(manager.isComplete())

        val ranking = manager.buildFinalRanking()
        val p2Entry = ranking.first { it.playerId == "p2" }
        assertEquals(RoundParticipantStatus.DISCONNECTED, p2Entry.status)
    }

    @Test
    fun `a player who never finishes nor disconnects is marked DNF once round is otherwise complete`() {
        val manager = manager(RoundEndMode.SPRINT)
        manager.tryAccept(result("p1"))
        assertTrue(manager.isComplete())

        val ranking = manager.buildFinalRanking()
        val p2Entry = ranking.first { it.playerId == "p2" }
        assertEquals(RoundParticipantStatus.DNF, p2Entry.status)
    }

    @Test
    fun `finished players are ranked before DNF and DISCONNECTED entries`() {
        val manager = manager(RoundEndMode.FULL_RANKING)
        manager.tryAccept(result("p1"))
        manager.markDisconnected("p2")
        manager.tryAccept(result("p3"))

        val ranking = manager.buildFinalRanking()
        val statuses = ranking.map { it.status }
        assertEquals(
            listOf(RoundParticipantStatus.FINISHED, RoundParticipantStatus.FINISHED, RoundParticipantStatus.DISCONNECTED),
            statuses
        )
    }

    @Test
    fun `round is force-completed once the timeout elapses regardless of end mode`() {
        val manager = manager(RoundEndMode.FULL_RANKING, timeoutMs = 1_000L)
        manager.tryAccept(result("p1"))
        assertFalse(manager.isComplete(nowMs = startAtMs + 500L))
        assertTrue(manager.isComplete(nowMs = startAtMs + 2_000L))
    }

    @Test
    fun `a second result for the same player is rejected as duplicate`() {
        val manager = manager(RoundEndMode.FULL_RANKING)
        manager.tryAccept(result("p1"))
        val outcome = manager.tryAccept(result("p1"))
        assertTrue(outcome is ValidationResult.Invalid)
    }
}
