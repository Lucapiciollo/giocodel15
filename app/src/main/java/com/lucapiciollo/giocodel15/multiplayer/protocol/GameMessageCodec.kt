package com.lucapiciollo.giocodel15.multiplayer.protocol

import org.json.JSONObject

object GameMessageCodec {

    fun encode(message: GameMessage): ByteArray {
        val json = JSONObject()
            .put("version", message.version)
            .put("type", message.type.name)
            .put("tableId", message.tableId)
            .put("roundId", message.roundId)
            .put("payload", JSONObject(message.payload))

        return json.toString().encodeToByteArray()
    }

    fun decode(bytes: ByteArray): GameMessage {
        val json = JSONObject(bytes.decodeToString())
        val payload = json.optJSONObject("payload")?.toString() ?: "{}"

        return GameMessage(
            version = json.getInt("version"),
            type = GameMessageType.valueOf(json.getString("type")),
            tableId = json.getString("tableId"),
            roundId = json.optString("roundId").takeIf { it.isNotBlank() && it != "null" },
            payload = payload
        )
    }
}
