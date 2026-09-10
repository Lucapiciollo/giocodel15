package com.lucapiciollo.giocodel15.multiplayer.protocol

enum class GameMessageType {
    HELLO,
    PLAYER_JOINED,
    PLAYER_LEFT,
    LOBBY_STATE,
    GAME_CONFIG,
    READY,
    START_GAME,
    PLAYER_FINISHED,
    PLAYER_RESULT,
    ROUND_RESULT,
    NEW_ROUND,
    TABLE_FINISHED,
    HOST_CLOSED
}
