package com.lucapiciollo.giocodel15.multiplayer.protocol

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON (de)serialization for the lobby roster the host broadcasts (`LOBBY_STATE`) so every
 * client can render the same player list and recompute `expectedPlayers` without needing a
 * direct Nearby connection to every other peer.
 */
object LobbyRosterCodec {

    fun toJson(players: List<LobbyPlayerInfo>): String = JSONArray().apply {
        players.forEach {
            put(
                JSONObject()
                    .put("playerId", it.playerId)
                    .put("playerName", it.playerName)
                    .put("isHost", it.isHost)
            )
        }
    }.toString()

    fun fromJson(json: String): List<LobbyPlayerInfo> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    LobbyPlayerInfo(
                        playerId = item.getString("playerId"),
                        playerName = item.getString("playerName"),
                        isHost = item.optBoolean("isHost", false)
                    )
                )
            }
        }
    }
}

data class LobbyPlayerInfo(
    val playerId: String,
    val playerName: String,
    val isHost: Boolean
)
