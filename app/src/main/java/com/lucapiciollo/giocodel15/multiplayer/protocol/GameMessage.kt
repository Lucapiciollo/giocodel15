package com.lucapiciollo.giocodel15.multiplayer.protocol

data class GameMessage(
    val version: Int = CURRENT_VERSION,
    val type: GameMessageType,
    val tableId: String,
    val roundId: String? = null,
    val payload: String = "{}"
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
