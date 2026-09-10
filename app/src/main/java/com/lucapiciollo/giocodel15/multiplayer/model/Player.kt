package com.lucapiciollo.giocodel15.multiplayer.model

data class Player(
    val id: String,
    val nickname: String,
    val isHost: Boolean = false,
    val isReady: Boolean = false,
    val isConnected: Boolean = true
)
