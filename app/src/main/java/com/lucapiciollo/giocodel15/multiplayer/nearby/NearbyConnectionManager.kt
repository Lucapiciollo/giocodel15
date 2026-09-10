package com.lucapiciollo.giocodel15.multiplayer.nearby

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.lucapiciollo.giocodel15.multiplayer.protocol.GameMessage
import com.lucapiciollo.giocodel15.multiplayer.protocol.GameMessageCodec

class NearbyConnectionManager(context: Context) {

    interface Listener {
        fun onEndpointFound(endpointId: String, endpointName: String) = Unit
        fun onEndpointLost(endpointId: String) = Unit
        fun onConnectionInitiated(endpointId: String, endpointName: String) = Unit
        fun onConnected(endpointId: String) = Unit
        fun onDisconnected(endpointId: String) = Unit
        fun onMessageReceived(endpointId: String, message: GameMessage) = Unit
        fun onError(message: String) = Unit
    }

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context.applicationContext)
    private val connectedEndpoints = linkedSetOf<String>()
    var listener: Listener? = null

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            runCatching { GameMessageCodec.decode(bytes) }
                .onSuccess { listener?.onMessageReceived(endpointId, it) }
                .onFailure { listener?.onError(it.message ?: "Messaggio Nearby non valido") }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            listener?.onConnectionInitiated(endpointId, info.endpointName)
            client.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { listener?.onError(it.message ?: "Connessione rifiutata") }
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (resolution.status.isSuccess) {
                connectedEndpoints += endpointId
                listener?.onConnected(endpointId)
            } else {
                listener?.onError("Connessione Nearby non riuscita: ${resolution.status.statusCode}")
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpoints -= endpointId
            listener?.onDisconnected(endpointId)
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            listener?.onEndpointFound(endpointId, info.endpointName)
        }

        override fun onEndpointLost(endpointId: String) {
            listener?.onEndpointLost(endpointId)
        }
    }

    fun startAdvertising(displayName: String) {
        // Defensive: on Activity recreation (e.g. screen rotation) this can be called again while
        // the underlying client is still advertising from before; stopping first makes it
        // idempotent instead of failing with STATUS_ALREADY_ADVERTISING.
        client.stopAdvertising()
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        client.startAdvertising(displayName, SERVICE_ID, connectionCallback, options)
            .addOnFailureListener { listener?.onError(it.message ?: "Impossibile creare il tavolo") }
    }

    fun startDiscovery() {
        // Same idempotency guard as startAdvertising(), for STATUS_ALREADY_DISCOVERING.
        client.stopDiscovery()
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        client.startDiscovery(SERVICE_ID, discoveryCallback, options)
            .addOnFailureListener { listener?.onError(it.message ?: "Impossibile cercare tavoli") }
    }

    fun requestConnection(displayName: String, endpointId: String) {
        client.requestConnection(displayName, endpointId, connectionCallback)
            .addOnFailureListener { listener?.onError(it.message ?: "Impossibile connettersi al tavolo") }
    }

    fun send(endpointId: String, message: GameMessage) {
        client.sendPayload(endpointId, Payload.fromBytes(GameMessageCodec.encode(message)))
            .addOnFailureListener { listener?.onError(it.message ?: "Invio messaggio fallito") }
    }

    fun broadcast(message: GameMessage) {
        if (connectedEndpoints.isEmpty()) return
        client.sendPayload(connectedEndpoints.toList(), Payload.fromBytes(GameMessageCodec.encode(message)))
            .addOnFailureListener { listener?.onError(it.message ?: "Broadcast fallito") }
    }

    fun disconnect(endpointId: String) {
        client.disconnectFromEndpoint(endpointId)
        connectedEndpoints -= endpointId
    }

    fun stopAdvertising() = client.stopAdvertising()
    fun stopDiscovery() = client.stopDiscovery()

    fun disconnectAll() {
        connectedEndpoints.toList().forEach(client::disconnectFromEndpoint)
        connectedEndpoints.clear()
    }

    companion object {
        const val SERVICE_ID = "com.lucapiciollo.giocodel15.nearby"
        private val STRATEGY = Strategy.P2P_CLUSTER
    }
}
