package com.lucapiciollo.giocodel15.multiplayer.model

data class GameConfig(
    val gridSize: Int,
    val seed: Long,
    val tableMode: TableMode = TableMode.TABLE,
    val maxPlayers: Int = 4,
    val targetWins: Int = 3
)
