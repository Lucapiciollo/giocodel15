package com.lucapiciollo.giocodel15.multiplayer.model

data class TableScore(
    val playerId: String,
    val playerName: String,
    val wins: Int = 0
)
