package com.lucapiciollo.giocodel15.multiplayer.session

import com.lucapiciollo.giocodel15.multiplayer.model.PlayerResult
import com.lucapiciollo.giocodel15.multiplayer.model.TableScore

object TableSession {
    var tableId: String = "local-table"
    var isHost: Boolean = false
    var gridSize: Int = 4
    var expectedPlayers: Int = 1
    var targetWins: Int = 3

    private val scores = linkedMapOf<String, TableScore>()
    private val lastRanking = mutableListOf<PlayerResult>()

    fun registerPlayer(playerId: String, playerName: String) {
        scores.putIfAbsent(playerId, TableScore(playerId, playerName, 0))
    }

    fun applyRound(ranking: List<PlayerResult>) {
        lastRanking.clear()
        lastRanking.addAll(ranking)
        ranking.forEach { registerPlayer(it.playerId, it.playerName) }
        ranking.firstOrNull()?.let { winner ->
            val current = scores[winner.playerId] ?: TableScore(winner.playerId, winner.playerName)
            scores[winner.playerId] = current.copy(wins = current.wins + 1)
        }
    }

    fun ranking(): List<TableScore> = scores.values.sortedWith(
        compareByDescending<TableScore> { it.wins }.thenBy { it.playerName.lowercase() }
    )

    fun lastRound(): List<PlayerResult> = lastRanking.toList()

    fun winnerReachedTarget(): TableScore? = ranking().firstOrNull { it.wins >= targetWins }

    fun clear() {
        tableId = "local-table"
        isHost = false
        gridSize = 4
        expectedPlayers = 1
        targetWins = 3
        scores.clear()
        lastRanking.clear()
    }
}
