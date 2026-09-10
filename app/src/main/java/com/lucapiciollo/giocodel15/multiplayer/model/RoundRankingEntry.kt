package com.lucapiciollo.giocodel15.multiplayer.model

/**
 * A single row of a round's final ranking, covering players who finished as well as
 * players who did not (DNF) or lost connection (DISCONNECTED).
 */
data class RoundRankingEntry(
    val playerId: String,
    val playerName: String,
    val status: RoundParticipantStatus,
    val result: PlayerResult? = null
)
