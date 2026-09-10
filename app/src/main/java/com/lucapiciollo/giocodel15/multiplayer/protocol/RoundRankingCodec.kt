package com.lucapiciollo.giocodel15.multiplayer.protocol

import com.lucapiciollo.giocodel15.multiplayer.model.RoundParticipantStatus
import com.lucapiciollo.giocodel15.multiplayer.model.RoundRankingEntry
import org.json.JSONArray
import org.json.JSONObject

/** JSON (de)serialization for a round's final ranking, including DNF/DISCONNECTED entries. */
object RoundRankingCodec {

    fun toJson(entries: List<RoundRankingEntry>): String = JSONArray().apply {
        entries.forEach { entry ->
            val json = JSONObject()
                .put("playerId", entry.playerId)
                .put("playerName", entry.playerName)
                .put("status", entry.status.name)
            entry.result?.let { json.put("result", PlayerResultCodec.toJson(it)) }
            put(json)
        }
    }.toString()

    fun fromJson(json: String): List<RoundRankingEntry> {
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val status = runCatching {
                    RoundParticipantStatus.valueOf(item.getString("status"))
                }.getOrDefault(RoundParticipantStatus.DNF)
                val result = item.optJSONObject("result")?.let { PlayerResultCodec.fromJson(it) }
                add(
                    RoundRankingEntry(
                        playerId = item.getString("playerId"),
                        playerName = item.getString("playerName"),
                        status = status,
                        result = result
                    )
                )
            }
        }
    }
}
