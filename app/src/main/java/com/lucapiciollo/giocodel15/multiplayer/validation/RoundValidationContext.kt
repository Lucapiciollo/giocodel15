package com.lucapiciollo.giocodel15.multiplayer.validation

/**
 * Everything the host knows about the round in progress, needed to validate an incoming
 * [com.lucapiciollo.giocodel15.multiplayer.model.PlayerResult] without trusting the client.
 */
data class RoundValidationContext(
    val roundId: String,
    val gridSize: Int,
    val seed: Long,
    val startAtMs: Long,
    val knownPlayerIds: Set<String>,
    val alreadyAcceptedPlayerIds: Set<String>
)
