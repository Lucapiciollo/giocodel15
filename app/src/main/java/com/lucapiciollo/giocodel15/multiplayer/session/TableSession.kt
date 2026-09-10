package com.lucapiciollo.giocodel15.multiplayer.session

import com.lucapiciollo.giocodel15.multiplayer.model.PlayerResult
import com.lucapiciollo.giocodel15.multiplayer.model.RoundEndMode
import com.lucapiciollo.giocodel15.multiplayer.model.TableMode
import com.lucapiciollo.giocodel15.multiplayer.model.TableScore
import com.lucapiciollo.giocodel15.multiplayer.ranking.TableWinnerResolver

object TableSession {
    var tableId: String = "local-table"
    var isHost: Boolean = false
    var gridSize: Int = 4
    var expectedPlayers: Int = 1
    var maxPlayers: Int = 4
    var targetWins: Int = 3
    var tableMode: TableMode = TableMode.TABLE
    var roundEndMode: RoundEndMode = RoundEndMode.FULL_RANKING
    var roundState: RoundState = RoundState.WAITING

    private val scores = linkedMapOf<String, TableScore>()
    private val lastRanking = mutableListOf<PlayerResult>()

    /** Players currently part of the lobby/table, keyed by playerId (stable device identity). */
    private val activePlayers = linkedMapOf<String, String>()

    /** Nearby endpointId -> playerId, so connection callbacks (which only carry endpointId) can
     * be resolved back to the stable playerId used everywhere else (results, ranking, roster). */
    private val endpointPlayers = linkedMapOf<String, String>()

    fun registerPlayer(playerId: String, playerName: String) {
        activePlayers[playerId] = playerName
        scores.putIfAbsent(playerId, TableScore(playerId, playerName, 0))
    }

    fun removePlayer(playerId: String) {
        activePlayers.remove(playerId)
    }

    fun activeRoster(): List<Pair<String, String>> = activePlayers.map { it.key to it.value }

    fun bindEndpoint(endpointId: String, playerId: String) {
        endpointPlayers[endpointId] = playerId
    }

    fun playerIdForEndpoint(endpointId: String): String? = endpointPlayers[endpointId]

    fun unbindEndpoint(endpointId: String): String? = endpointPlayers.remove(endpointId)

    fun applyRound(ranking: List<PlayerResult>) {
        lastRanking.clear()
        lastRanking.addAll(ranking)
        ranking.forEach { registerPlayer(it.playerId, it.playerName) }
        ranking.firstOrNull()?.let { winner ->
            val current = scores[winner.playerId] ?: TableScore(winner.playerId, winner.playerName)
            scores[winner.playerId] = current.copy(wins = current.wins + 1)
        }
    }

    fun ranking(): List<TableScore> = TableWinnerResolver.rankedScores(scores.values)

    fun lastRound(): List<PlayerResult> = lastRanking.toList()

    fun winnerReachedTarget(): TableScore? = TableWinnerResolver.resolve(scores.values, targetWins)

    fun clear() {
        tableId = "local-table"
        isHost = false
        gridSize = 4
        expectedPlayers = 1
        maxPlayers = 4
        targetWins = 3
        tableMode = TableMode.TABLE
        roundEndMode = RoundEndMode.FULL_RANKING
        roundState = RoundState.WAITING
        scores.clear()
        lastRanking.clear()
        activePlayers.clear()
        endpointPlayers.clear()
    }
}

