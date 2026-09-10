package com.lucapiciollo.giocodel15.multiplayer.model

data class PlayerResult(
    val playerId: String,
    val playerName: String,
    val elapsedMs: Long,
    val moves: Int
)
