package com.lucapiciollo.giocodel15.multiplayer.protocol

import com.lucapiciollo.giocodel15.multiplayer.model.PlayerResult
import org.json.JSONArray
import org.json.JSONObject

/** JSON (de)serialization for [PlayerResult], shared between host validation and clients. */
object PlayerResultCodec {

    fun toJson(result: PlayerResult): JSONObject = JSONObject()
        .put("playerId", result.playerId)
        .put("playerName", result.playerName)
        .put("roundId", result.roundId)
        .put("elapsedMs", result.elapsedMs)
        .put("moves", result.moves)
        .put("finishedAt", result.finishedAt)
        .put("boardHash", result.boardHash)
        .put("moveSequence", JSONArray(result.moveSequence))

    fun fromJson(json: JSONObject, fallbackId: String = ""): PlayerResult {
        val moveSequenceJson = json.optJSONArray("moveSequence")
        val moveSequence = buildList {
            if (moveSequenceJson != null) {
                for (index in 0 until moveSequenceJson.length()) add(moveSequenceJson.getInt(index))
            }
        }
        return PlayerResult(
            playerId = json.optString("playerId", fallbackId),
            playerName = json.optString("playerName", fallbackId),
            roundId = json.optString("roundId"),
            elapsedMs = json.getLong("elapsedMs"),
            moves = json.getInt("moves"),
            finishedAt = json.optLong("finishedAt", System.currentTimeMillis()),
            boardHash = json.optString("boardHash"),
            moveSequence = moveSequence
        )
    }
}
