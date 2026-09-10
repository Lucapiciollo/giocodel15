package com.lucapiciollo.giocodel15.multiplayer.model

data class GameTable(
    val id: String,
    val name: String,
    val hostId: String,
    val config: GameConfig,
    val players: List<Player> = emptyList()
)
