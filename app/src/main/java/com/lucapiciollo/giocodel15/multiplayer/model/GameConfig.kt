package com.lucapiciollo.giocodel15.multiplayer.model

data class GameConfig(
    val gridSize: Int,
    val seed: Long,
    val maxPlayers: Int = 8,
    val targetWins: Int = 1
)
