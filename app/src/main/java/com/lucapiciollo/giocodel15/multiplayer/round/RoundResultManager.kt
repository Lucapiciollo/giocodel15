package com.lucapiciollo.giocodel15.multiplayer.round

import com.lucapiciollo.giocodel15.multiplayer.model.PlayerResult
import com.lucapiciollo.giocodel15.multiplayer.model.RoundEndMode
import com.lucapiciollo.giocodel15.multiplayer.model.RoundParticipantStatus
import com.lucapiciollo.giocodel15.multiplayer.model.RoundRankingEntry
import com.lucapiciollo.giocodel15.multiplayer.ranking.RankingCalculator
import com.lucapiciollo.giocodel15.multiplayer.validation.PlayerResultValidator
import com.lucapiciollo.giocodel15.multiplayer.validation.RoundValidationContext
import com.lucapiciollo.giocodel15.multiplayer.validation.ValidationResult

/**
 * Host-only authority that owns a round's lifecycle: accepting/validating results exactly once
 * per player, tracking disconnects, and deciding when the round is over according to the
 * table's [RoundEndMode] (plus a safety [timeoutMs] so a round can never last forever).
 *
 * Kept independent from any Activity so `GameActivity` stays a thin coordinator.
 */
class RoundResultManager(
    private val roundId: String,
    private val gridSize: Int,
    private val seed: Long,
    private val startAtMs: Long,
    private val endMode: RoundEndMode,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    players: List<Pair<String, String>>,
    private val validator: PlayerResultValidator = PlayerResultValidator()
) {
    private val playerNames: LinkedHashMap<String, String> = LinkedHashMap<String, String>().apply {
        players.forEach { (id, name) -> put(id, name) }
    }
    private val results = linkedMapOf<String, PlayerResult>()
    private val disconnected = linkedSetOf<String>()

    val totalPlayers: Int get() = playerNames.size
    val finishedCount: Int get() = results.size

    /** Read-only snapshot of results accepted so far (for live position/ranking feedback). */
    fun acceptedResults(): List<PlayerResult> = results.values.toList()

    /** Attempts to accept a remote result, validating it first. Never throws. */
    fun tryAccept(result: PlayerResult): ValidationResult {
        val context = RoundValidationContext(
            roundId = roundId,
            gridSize = gridSize,
            seed = seed,
            startAtMs = startAtMs,
            knownPlayerIds = playerNames.keys,
            alreadyAcceptedPlayerIds = results.keys
        )
        val outcome = validator.validate(result, context)
        if (outcome is ValidationResult.Valid) {
            results[result.playerId] = result
            disconnected -= result.playerId
        }
        return outcome
    }

    /** Marks a still-unfinished player as disconnected. No-op if they already finished. */
    fun markDisconnected(playerId: String) {
        if (playerId in results) return
        if (playerId !in playerNames) return
        disconnected += playerId
    }

    fun isComplete(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (nowMs - startAtMs >= timeoutMs) return true
        val stillMissing = totalPlayers - results.size - disconnected.size
        return when (endMode) {
            RoundEndMode.SPRINT -> results.isNotEmpty()
            RoundEndMode.PODIUM -> results.size >= minOf(3, totalPlayers) || stillMissing <= 0
            RoundEndMode.FULL_RANKING -> stillMissing <= 0
        }
    }

    /** Final ranking: finished players first (ordered), then DNF/DISCONNECTED players. */
    fun buildFinalRanking(): List<RoundRankingEntry> {
        val finished = RankingCalculator.rank(results.values).map {
            RoundRankingEntry(it.playerId, it.playerName, RoundParticipantStatus.FINISHED, it)
        }
        val unfinished = playerNames.keys - results.keys
        val notFinished = unfinished.map { playerId ->
            val status = if (playerId in disconnected) {
                RoundParticipantStatus.DISCONNECTED
            } else {
                RoundParticipantStatus.DNF
            }
            RoundRankingEntry(playerId, playerNames[playerId] ?: playerId, status)
        }
        return finished + notFinished
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
