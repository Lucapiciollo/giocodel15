package com.lucapiciollo.giocodel15.feature.lobby

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.lucapiciollo.giocodel15.R
import com.lucapiciollo.giocodel15.databinding.ActivityLobbyBinding
import com.lucapiciollo.giocodel15.databinding.ItemPlayerBinding
import com.lucapiciollo.giocodel15.feature.game.GameActivity
import com.lucapiciollo.giocodel15.multiplayer.nearby.DeviceIdentity
import com.lucapiciollo.giocodel15.multiplayer.nearby.NearbyConnectionManager
import com.lucapiciollo.giocodel15.multiplayer.nearby.NearbyPermissions
import com.lucapiciollo.giocodel15.multiplayer.nearby.NearbySession
import com.lucapiciollo.giocodel15.multiplayer.protocol.GameMessage
import com.lucapiciollo.giocodel15.multiplayer.protocol.GameMessageType
import org.json.JSONObject

class LobbyActivity : AppCompatActivity(), NearbyConnectionManager.Listener {

    private lateinit var binding: ActivityLobbyBinding
    private lateinit var nearby: NearbyConnectionManager
    private val endpointNames = linkedMapOf<String, String>()
    private val playerRows = linkedMapOf<String, ItemPlayerBinding>()
    private val handler = Handler(Looper.getMainLooper())

    private val isHost by lazy { intent.getBooleanExtra(EXTRA_IS_HOST, false) }
    private val gridSize by lazy { intent.getIntExtra(EXTRA_GRID_SIZE, DEFAULT_GRID_SIZE) }
    private val tableId by lazy { intent.getStringExtra(EXTRA_TABLE_ID) ?: DEFAULT_TABLE_ID }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) startHostAdvertising()
        else binding.lobbySubtitle.setText(R.string.nearby_permission_denied)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLobbyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        nearby = NearbySession.manager(this)
        nearby.listener = this

        binding.startGameButton.isVisible = isHost
        binding.startGameButton.setOnClickListener { startRoundAsHost() }

        if (isHost) {
            addPlayer("host", DeviceIdentity.displayName(this), true)
            requestPermissionsAndAdvertise()
        } else {
            binding.lobbySubtitle.text = getString(R.string.lobby_waiting)
        }
    }

    override fun onResume() {
        super.onResume()
        nearby.listener = this
    }

    override fun onConnectionInitiated(endpointId: String, endpointName: String) {
        endpointNames[endpointId] = endpointName
    }

    override fun onConnected(endpointId: String) {
        if (isHost) {
            addPlayer(endpointId, endpointNames[endpointId] ?: endpointId, false)
        }
    }

    override fun onDisconnected(endpointId: String) {
        playerRows.remove(endpointId)?.let { binding.playersContainer.removeView(it.root) }
    }

    override fun onMessageReceived(endpointId: String, message: GameMessage) {
        if (message.version != GameMessage.CURRENT_VERSION) return
        if (message.type != GameMessageType.START_GAME || isHost) return

        runCatching {
            val payload = JSONObject(message.payload)
            val size = payload.getInt("gridSize")
            val seed = payload.getLong("seed")
            val delayMs = payload.optLong("startDelayMs", START_DELAY_MS)
            scheduleGameStart(size, seed, delayMs)
        }.onFailure {
            onError(it.message ?: "Configurazione partita non valida")
        }
    }

    override fun onError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun requestPermissionsAndAdvertise() {
        val required = NearbyPermissions.requiredRuntimePermissions()
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startHostAdvertising() else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun startHostAdvertising() {
        nearby.startAdvertising(DeviceIdentity.displayName(this))
        binding.lobbySubtitle.text = "${gridSize}×${gridSize} · ${getString(R.string.lobby_waiting)}"
    }

    private fun startRoundAsHost() {
        val seed = System.currentTimeMillis()
        val payload = JSONObject()
            .put("gridSize", gridSize)
            .put("seed", seed)
            .put("startDelayMs", START_DELAY_MS)
            .toString()

        nearby.broadcast(
            GameMessage(
                type = GameMessageType.START_GAME,
                tableId = tableId,
                roundId = seed.toString(),
                payload = payload
            )
        )
        nearby.stopAdvertising()
        binding.startGameButton.isEnabled = false
        scheduleGameStart(gridSize, seed, START_DELAY_MS)
    }

    private fun scheduleGameStart(size: Int, seed: Long, delayMs: Long) {
        binding.lobbySubtitle.text = "3 · 2 · 1 · VIA"
        handler.postDelayed({
            startActivity(
                Intent(this, GameActivity::class.java).apply {
                    putExtra(GameActivity.EXTRA_GRID_SIZE, size)
                    putExtra(GameActivity.EXTRA_SEED, seed)
                }
            )
        }, delayMs.coerceAtLeast(0L))
    }

    private fun addPlayer(id: String, name: String, host: Boolean) {
        if (playerRows.containsKey(id)) return
        val row = ItemPlayerBinding.inflate(layoutInflater, binding.playersContainer, false)
        row.playerName.text = name
        row.playerStatus.text = if (host) "HOST" else "CONNESSO"
        playerRows[id] = row
        binding.playersContainer.addView(row.root)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_IS_HOST = "extra_is_host"
        const val EXTRA_GRID_SIZE = "extra_grid_size"
        const val EXTRA_TABLE_ID = "extra_table_id"
        const val EXTRA_HOST_ENDPOINT_ID = "extra_host_endpoint_id"
        private const val DEFAULT_GRID_SIZE = 4
        private const val DEFAULT_TABLE_ID = "local-table"
        private const val START_DELAY_MS = 3000L
    }
}
