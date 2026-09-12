package com.lucapiciollo.giocodel15.multiplayer.nearby

data class NearbyTableAdvertisement(
    val tableId: String,
    val hostName: String
) {
    companion object {
        private const val PREFIX = "G15"
        private const val SEPARATOR = "|"

        fun encode(tableId: String, hostName: String): String =
            listOf(PREFIX, tableId, hostName).joinToString(SEPARATOR)

        fun decode(endpointName: String): NearbyTableAdvertisement? {
            val parts = endpointName.split(SEPARATOR, limit = 3)
            if (parts.size != 3 || parts[0] != PREFIX) return null
            val tableId = parts[1].trim()
            val hostName = parts[2].trim()
            if (tableId.isBlank() || hostName.isBlank()) return null
            return NearbyTableAdvertisement(tableId, hostName)
        }
    }
}
