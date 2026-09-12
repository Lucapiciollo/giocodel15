package com.lucapiciollo.giocodel15.multiplayer.protocol

import org.json.JSONObject
import org.json.JSONArray

object GameMessageCodec {

    fun encode(message: GameMessage): ByteArray {
        val payloadText = message.payload.trim()
        val payload = if (payloadText.startsWith("[")) {
            JSONArray(payloadText)
        } else {
            JSONObject(payloadText)
        }
        val json = JSONObject()
            .put("version", message.version)
            .put("type", message.type.name)
            .put("tableId", message.tableId)
            .put("roundId", message.roundId)
            .put("payload", payload)

        return json.toString().encodeToByteArray()
    }

    fun decode(bytes: ByteArray): GameMessage {
        val json = JSONObject(bytes.decodeToString())
        val payload = json.optJSONObject("payload")?.toString()
            ?: json.optJSONArray("payload")?.toString()
            ?: "{}"

        return GameMessage(
            version = json.getInt("version"),
            type = GameMessageType.valueOf(json.getString("type")),
            tableId = json.getString("tableId"),
            roundId = json.optString("roundId").takeIf { it.isNotBlank() && it != "null" },
            payload = payload
        )
    }
}
