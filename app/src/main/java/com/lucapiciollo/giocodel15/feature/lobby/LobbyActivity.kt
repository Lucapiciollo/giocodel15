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
import androidx.core.view.isVisible
import com.lucapiciollo.giocodel15.R
import com.lucapiciollo.giocodel15.databinding.ActivityLobbyBinding
import com.lucapiciollo.giocodel15.databinding.ItemPlayerBinding
import com.lucapiciollo.giocodel15.feature.game.GameActivity
import com.lucapiciollo.giocodel15.multiplayer.model.TableMode
import com.lucapiciollo.giocodel15.multiplayer.nearby.DeviceIdentity
import com.lucapiciollo.giocodel15.multiplayer.nearby.NearbyConnectionManager
import com.lucapiciollo.giocodel15.multiplayer.nearby.NearbyPermissions
import com.lucapiciollo.giocodel15.multiplayer.nearby.NearbySession
import com.lucapiciollo.giocodel15.multiplayer.nearby.NearbyTableAdvertisement
import com.lucapiciollo.giocodel15.multiplayer.protocol.GameMessage
import com.lucapiciollo.giocodel15.multiplayer.protocol.GameMessageType
import com.lucapiciollo.giocodel15.multiplayer.session.TableSession
import org.json.JSONObject

class LobbyActivity : AppCompatActivity(), NearbyConnectionManager.Listener {

    private lateinit var binding: ActivityLobbyBinding
    private lateinit var nearby: NearbyConnectionManager
    private val endpointNames = linkedMapOf<String, String>()
    private val playerRows = linkedMapOf<String, ItemPlayerBinding>()
    private val handler = Handler(Looper.getMainLooper())
    private var leavingForGame = false

    private val isHost by lazy { intent.getBooleanExtra(EXTRA_IS_HOST, false) }
    private val gridSize by lazy { intent.getIntExtra(EXTRA_GRID_SIZE, DEFAULT_GRID_SIZE) }
    private val initialTableId by lazy { intent.getStringExtra(EXTRA_TABLE_ID) ?: DEFAULT_TABLE_ID }
    private val hostEndpointId by lazy { intent.getStringExtra(EXTRA_HOST_ENDPOINT_ID) }
    private val tableMode by lazy {
        runCatching {
            TableMode.valueOf(intent.getStringExtra(EXTRA_TABLE_MODE) ?: TableMode.TABLE.name)
        }.getOrDefault(TableMode.TABLE)
    }
    private val maxPlayers by lazy {
        if (tableMode == TableMode.ONE_VS_ONE) 2
        else intent.getIntExtra(EXTRA_MAX_PLAYERS, DEFAULT_MAX_PLAYERS).coerceIn(2, MAX_SUPPORTED_PLAYERS)
    }
    private val targetWins by lazy {
        intent.getIntExtra(EXTRA_TARGET_WINS, DEFAULT_TARGET_WINS).let { if (it in VALID_TARGET_WINS) it else DEFAULT_TARGET_WINS }
    }

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

        if (isHost) NearbySession.reset()
        nearby = NearbySession.manager(this)
        nearby.setListener(this)

        if (isHost) {
            TableSession.tableId = initialTableId
            TableSession.isHost = true
            TableSession.gridSize = gridSize
            TableSession.tableMode = tableMode
            TableSession.maxPlayers = maxPlayers
            TableSession.targetWins = targetWins
        } else {
            TableSession.tableId = initialTableId
            TableSession.isHost = false
        }

        binding.startGameButton.isVisible = isHost
        binding.startGameButton.setOnClickListener { startRoundAsHost() }

        val selfName = DeviceIdentity.displayName(this)
        TableSession.registerPlayer(selfName, selfName)

        if (isHost) {
            addPlayer("host", selfName, true)
            requestPermissionsAndAdvertise()
        } else {
            binding.lobbySubtitle.text = getString(R.string.lobby_waiting)
            requestGameConfigFromHost()
        }
    }

    override fun onResume() {
        super.onResume()
        nearby.setListener(this)
    }

    private fun requestGameConfigFromHost() {
        val endpointId = hostEndpointId ?: return
        nearby.send(
            endpointId,
            GameMessage(
                type = GameMessageType.HELLO,
                tableId = initialTableId
            )
        )
    }

    override fun onConnectionInitiated(endpointId: String, endpointName: String) {
        if (isFinishing || isDestroyed) return
        endpointNames[endpointId] = endpointName
    }

    override fun onConnected(endpointId: String) {
        if (isFinishing || isDestroyed || !isHost) return
        if (playerRows.containsKey(endpointId)) return

        if (playerRows.size >= TableSession.maxPlayers) {
            nearby.disconnect(endpointId)
            Toast.makeText(this, R.string.lobby_table_full, Toast.LENGTH_SHORT).show()
            return
        }

        val name = endpointNames[endpointId] ?: endpointId
        addPlayer(endpointId, name, false)
        TableSession.registerPlayer(endpointId, name)
        sendGameConfig(endpointId)
    }

    private fun sendGameConfig(endpointId: String) {
        val payload = JSONObject()
            .put("gridSize", TableSession.gridSize)
            .put("maxPlayers", TableSession.maxPlayers)
            .put("targetWins", TableSession.targetWins)
            .put("tableMode", TableSession.tableMode.name)
            .toString()

        nearby.send(
            endpointId,
            GameMessage(
                type = GameMessageType.GAME_CONFIG,
                tableId = TableSession.tableId,
                payload = payload
            )
        )
    }

    override fun onDisconnected(endpointId: String) {
        if (isFinishing || isDestroyed) return
        playerRows.remove(endpointId)?.let { binding.playersContainer.removeView(it.root) }
        endpointNames.remove(endpointId)
    }

    override fun onMessageReceived(endpointId: String, message: GameMessage) {
        if (isFinishing || isDestroyed || message.version != GameMessage.CURRENT_VERSION) return

        if (isHost && message.type == GameMessageType.HELLO) {
            sendGameConfig(endpointId)
            return
        }

        if (!isHost && message.type == GameMessageType.GAME_CONFIG) {
            applyGameConfig(message)
            return
        }

        if (message.type != GameMessageType.START_GAME || isHost) return

        runCatching {
            val payload = JSONObject(message.payload)
            val size = payload.getInt("gridSize")
            val seed = payload.getLong("seed")
            val delayMs = payload.optLong("startDelayMs", START_DELAY_MS)
            val expectedPlayers = payload.optInt("expectedPlayers", 2).coerceAtLeast(1)
            val receivedMaxPlayers = payload.optInt("maxPlayers", expectedPlayers).coerceIn(2, MAX_SUPPORTED_PLAYERS)
            val receivedTargetWins = payload.optInt("targetWins", DEFAULT_TARGET_WINS).let {
                if (it in VALID_TARGET_WINS) it else DEFAULT_TARGET_WINS
            }
            val receivedMode = runCatching {
                TableMode.valueOf(payload.optString("tableMode", TableMode.TABLE.name))
            }.getOrDefault(TableMode.TABLE)

            TableSession.tableId = message.tableId
            TableSession.gridSize = size
            TableSession.expectedPlayers = expectedPlayers
            TableSession.maxPlayers = receivedMaxPlayers
            TableSession.targetWins = receivedTargetWins
            TableSession.tableMode = receivedMode

            scheduleGameStart(size, seed, delayMs, message.roundId, expectedPlayers, message.tableId)
        }.onFailure {
            onError(it.message ?: "Configurazione partita non valida")
        }
    }

    private fun applyGameConfig(message: GameMessage) {
        runCatching {
            val payload = JSONObject(message.payload)
            TableSession.tableId = message.tableId
            TableSession.gridSize = payload.optInt("gridSize", DEFAULT_GRID_SIZE)
            TableSession.maxPlayers = payload.optInt("maxPlayers", DEFAULT_MAX_PLAYERS)
                .coerceIn(2, MAX_SUPPORTED_PLAYERS)
            TableSession.targetWins = payload.optInt("targetWins", DEFAULT_TARGET_WINS).let {
                if (it in VALID_TARGET_WINS) it else DEFAULT_TARGET_WINS
            }
            TableSession.tableMode = runCatching {
                TableMode.valueOf(payload.optString("tableMode", TableMode.TABLE.name))
            }.getOrDefault(TableMode.TABLE)

            binding.lobbySubtitle.text = "${TableSession.gridSize}×${TableSession.gridSize} · ${getString(R.string.lobby_waiting)}"
        }.onFailure {
            onError(it.message ?: "Configurazione tavolo non valida")
        }
    }

    override fun onError(message: String) {
        if (isFinishing || isDestroyed) return
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
        val endpointName = NearbyTableAdvertisement.encode(
            TableSession.tableId,
            DeviceIdentity.displayName(this)
        )
        nearby.startAdvertising(endpointName)
        binding.lobbySubtitle.text = "${gridSize}×${gridSize} · ${getString(R.string.lobby_waiting)}"
    }

    private fun startRoundAsHost() {
        if (!binding.startGameButton.isEnabled) return

        val seed = System.currentTimeMillis()
        val roundId = seed.toString()
        val expectedPlayers = playerRows.size.coerceAtLeast(1)
        TableSession.expectedPlayers = expectedPlayers

        val payload = JSONObject()
            .put("gridSize", gridSize)
            .put("seed", seed)
            .put("startDelayMs", START_DELAY_MS)
            .put("expectedPlayers", expectedPlayers)
            .put("maxPlayers", TableSession.maxPlayers)
            .put("targetWins", TableSession.targetWins)
            .put("tableMode", TableSession.tableMode.name)
            .toString()

        nearby.broadcast(
            GameMessage(
                type = GameMessageType.START_GAME,
                tableId = TableSession.tableId,
                roundId = roundId,
                payload = payload
            )
        )
        nearby.stopAdvertising()
        binding.startGameButton.isEnabled = false
        scheduleGameStart(gridSize, seed, START_DELAY_MS, roundId, expectedPlayers, TableSession.tableId)
    }

    private fun scheduleGameStart(
        size: Int,
        seed: Long,
        delayMs: Long,
        roundId: String?,
        expectedPlayers: Int,
        resolvedTableId: String
    ) {
        val resolvedRoundId = roundId ?: return
        TableSession.gridSize = size
        TableSession.expectedPlayers = expectedPlayers
        binding.lobbySubtitle.text = "3 · 2 · 1 · VIA"
        handler.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            leavingForGame = true
            startActivity(
                Intent(this, GameActivity::class.java).apply {
                    putExtra(GameActivity.EXTRA_GRID_SIZE, size)
                    putExtra(GameActivity.EXTRA_SEED, seed)
                    putExtra(GameActivity.EXTRA_IS_HOST, isHost)
                    putExtra(GameActivity.EXTRA_TABLE_ID, resolvedTableId)
                    putExtra(GameActivity.EXTRA_ROUND_ID, resolvedRoundId)
                    putExtra(GameActivity.EXTRA_EXPECTED_PLAYERS, expectedPlayers)
                }
            )
            finish()
        }, delayMs.coerceAtLeast(0L))
    }

    private fun addPlayer(id: String, name: String, host: Boolean) {
        if (playerRows.containsKey(id) || isFinishing || isDestroyed) return
        val row = ItemPlayerBinding.inflate(layoutInflater, binding.playersContainer, false)
        row.playerName.text = name
        row.playerStatus.text = if (host) "HOST" else "CONNESSO"
        playerRows[id] = row
        binding.playersContainer.addView(row.root)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        nearby.clearListener(this)
        if (isFinishing && !isChangingConfigurations && !leavingForGame) {
            nearby.resetTransport()
            TableSession.clear()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_IS_HOST = "extra_is_host"
        const val EXTRA_GRID_SIZE = "extra_grid_size"
        const val EXTRA_TABLE_ID = "extra_table_id"
        const val EXTRA_HOST_ENDPOINT_ID = "extra_host_endpoint_id"
        const val EXTRA_TABLE_MODE = "extra_table_mode"
        const val EXTRA_MAX_PLAYERS = "extra_max_players"
        const val EXTRA_TARGET_WINS = "extra_target_wins"
        private const val DEFAULT_GRID_SIZE = 4
        private const val DEFAULT_TABLE_ID = "local-table"
        private const val DEFAULT_MAX_PLAYERS = 4
        private const val DEFAULT_TARGET_WINS = 3
        private const val MAX_SUPPORTED_PLAYERS = 8
        private val VALID_TARGET_WINS = setOf(1, 3, 5)
        private const val START_DELAY_MS = 3000L
    }
}
