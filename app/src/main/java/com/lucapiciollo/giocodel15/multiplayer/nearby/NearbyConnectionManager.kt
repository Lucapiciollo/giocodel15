package com.lucapiciollo.giocodel15.multiplayer.nearby

import android.content.Context
import android.os.Handler
import android.os.Looper
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

    enum class State {
        IDLE,
        ADVERTISING,
        DISCOVERING,
        CONNECTING,
        CONNECTED
    }

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val connectedEndpoints = linkedSetOf<String>()

    @Volatile
    var listener: Listener? = null
        private set

    @Volatile
    var state: State = State.IDLE
        private set

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    fun clearListener(listener: Listener) {
        if (this.listener === listener) this.listener = null
    }

    private fun dispatch(block: (Listener) -> Unit) {
        val current = listener ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block(current)
        } else {
            mainHandler.post {
                listener?.let(block)
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            runCatching { GameMessageCodec.decode(bytes) }
                .onSuccess { message -> dispatch { it.onMessageReceived(endpointId, message) } }
                .onFailure { error -> dispatch { it.onError(error.message ?: "Messaggio Nearby non valido") } }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            dispatch { it.onConnectionInitiated(endpointId, info.endpointName) }
            client.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { error ->
                    dispatch { it.onError(error.message ?: "Connessione rifiutata") }
                }
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (resolution.status.isSuccess) {
                val isNewConnection = connectedEndpoints.add(endpointId)
                state = State.CONNECTED
                if (isNewConnection) dispatch { it.onConnected(endpointId) }
            } else {
                if (connectedEndpoints.isEmpty()) state = State.IDLE
                dispatch { it.onError("Connessione Nearby non riuscita: ${resolution.status.statusCode}") }
            }
        }

        override fun onDisconnected(endpointId: String) {
            val wasConnected = connectedEndpoints.remove(endpointId)
            if (connectedEndpoints.isEmpty()) state = State.IDLE
            if (wasConnected) dispatch { it.onDisconnected(endpointId) }
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            dispatch { it.onEndpointFound(endpointId, info.endpointName) }
        }

        override fun onEndpointLost(endpointId: String) {
            dispatch { it.onEndpointLost(endpointId) }
        }
    }

    fun startAdvertising(displayName: String) {
        stopDiscovery()
        stopAdvertising()
        state = State.ADVERTISING
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        client.startAdvertising(displayName, SERVICE_ID, connectionCallback, options)
            .addOnFailureListener { error ->
                state = if (connectedEndpoints.isEmpty()) State.IDLE else State.CONNECTED
                dispatch { it.onError(error.message ?: "Impossibile creare il tavolo") }
            }
    }

    fun startDiscovery() {
        stopAdvertising()
        stopDiscovery()
        state = State.DISCOVERING
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        client.startDiscovery(SERVICE_ID, discoveryCallback, options)
            .addOnFailureListener { error ->
                state = if (connectedEndpoints.isEmpty()) State.IDLE else State.CONNECTED
                dispatch { it.onError(error.message ?: "Impossibile cercare tavoli") }
            }
    }

    fun requestConnection(displayName: String, endpointId: String) {
        if (connectedEndpoints.contains(endpointId)) {
            dispatch { it.onConnected(endpointId) }
            return
        }
        stopDiscovery()
        state = State.CONNECTING
        client.requestConnection(displayName, endpointId, connectionCallback)
            .addOnFailureListener { error ->
                state = if (connectedEndpoints.isEmpty()) State.IDLE else State.CONNECTED
                dispatch { it.onError(error.message ?: "Impossibile connettersi al tavolo") }
            }
    }

    fun send(endpointId: String, message: GameMessage) {
        if (!connectedEndpoints.contains(endpointId)) return
        client.sendPayload(endpointId, Payload.fromBytes(GameMessageCodec.encode(message)))
            .addOnFailureListener { error -> dispatch { it.onError(error.message ?: "Invio messaggio fallito") } }
    }

    fun broadcast(message: GameMessage) {
        if (connectedEndpoints.isEmpty()) return
        client.sendPayload(connectedEndpoints.toList(), Payload.fromBytes(GameMessageCodec.encode(message)))
            .addOnFailureListener { error -> dispatch { it.onError(error.message ?: "Broadcast fallito") } }
    }

    fun disconnect(endpointId: String) {
        client.disconnectFromEndpoint(endpointId)
        connectedEndpoints.remove(endpointId)
        if (connectedEndpoints.isEmpty()) state = State.IDLE
    }

    fun stopAdvertising() {
        client.stopAdvertising()
        if (state == State.ADVERTISING) state = if (connectedEndpoints.isEmpty()) State.IDLE else State.CONNECTED
    }

    fun stopDiscovery() {
        client.stopDiscovery()
        if (state == State.DISCOVERING) state = if (connectedEndpoints.isEmpty()) State.IDLE else State.CONNECTED
    }

    fun disconnectAll() {
        val endpoints = connectedEndpoints.toList()
        connectedEndpoints.clear()
        endpoints.forEach(client::disconnectFromEndpoint)
        state = State.IDLE
    }

    fun resetTransport() {
        stopAdvertising()
        stopDiscovery()
        disconnectAll()
    }

    companion object {
        const val SERVICE_ID = "com.lucapiciollo.giocodel15.nearby"
        private val STRATEGY = Strategy.P2P_CLUSTER
    }
}
