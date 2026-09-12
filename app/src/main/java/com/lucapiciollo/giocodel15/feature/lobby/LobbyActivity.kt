package com.lucapiciollo.giocodel15.feature.lobby

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.lucapiciollo.giocodel15.R
import com.lucapiciollo.giocodel15.core.ui.applyNavigationBarBottomInset
import com.lucapiciollo.giocodel15.core.ui.applyStatusBarTopInset
import com.lucapiciollo.giocodel15.core.ui.applyPressScaleAnimation
import com.lucapiciollo.giocodel15.core.ui.confirmAction
import com.lucapiciollo.giocodel15.core.ui.goHome
import com.lucapiciollo.giocodel15.core.ui.playEntranceAnimation
import com.lucapiciollo.giocodel15.databinding.ActivityLobbyBinding
import com.lucapiciollo.giocodel15.databinding.ItemPlayerBinding
import com.lucapiciollo.giocodel15.feature.game.GameActivity
import com.lucapiciollo.giocodel15.multiplayer.model.RoundEndMode
import com.lucapiciollo.giocodel15.multiplayer.model.TableMode
import com.lucapiciollo.giocodel15.multiplayer.nearby.DeviceIdentity
import com.lucapiciollo.giocodel15.multiplayer.nearby.HostDisconnectDialog
import com.lucapiciollo.giocodel15.multiplayer.nearby.NearbyConnectionManager
import com.lucapiciollo.giocodel15.multiplayer.nearby.NearbyPermissions
import com.lucapiciollo.giocodel15.multiplayer.nearby.NearbySession
import com.lucapiciollo.giocodel15.multiplayer.protocol.GameMessage
import com.lucapiciollo.giocodel15.multiplayer.protocol.GameMessageType
import com.lucapiciollo.giocodel15.multiplayer.protocol.LobbyPlayerInfo
import com.lucapiciollo.giocodel15.multiplayer.protocol.LobbyRosterCodec
import com.lucapiciollo.giocodel15.multiplayer.session.RoundStartScheduler
import com.lucapiciollo.giocodel15.multiplayer.session.RoundState
import com.lucapiciollo.giocodel15.multiplayer.session.TableSession
import org.json.JSONObject
import java.util.UUID

class LobbyActivity : AppCompatActivity(), NearbyConnectionManager.Listener {

    private lateinit var binding: ActivityLobbyBinding
    private lateinit var nearby: NearbyConnectionManager
    private val playerRows = linkedMapOf<String, ItemPlayerBinding>()
    private val handler = Handler(Looper.getMainLooper())
    private val startScheduler = RoundStartScheduler(handler)
    private var hostClosedHandled = false

    private val isHost by lazy { intent.getBooleanExtra(EXTRA_IS_HOST, false) }
    private val gridSize by lazy { intent.getIntExtra(EXTRA_GRID_SIZE, DEFAULT_GRID_SIZE) }
    private val initialTableId by lazy { intent.getStringExtra(EXTRA_TABLE_ID) ?: DEFAULT_TABLE_ID }
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
    private val roundEndMode by lazy {
        runCatching {
            RoundEndMode.valueOf(intent.getStringExtra(EXTRA_ROUND_END_MODE) ?: RoundEndMode.FULL_RANKING.name)
        }.getOrDefault(RoundEndMode.FULL_RANKING)
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
        binding.root.applyStatusBarTopInset()
        binding.root.applyNavigationBarBottomInset()
        binding.root.playEntranceAnimation()

        nearby = NearbySession.manager(this)
        nearby.listener = this

        if (isHost) {
            TableSession.tableId = initialTableId
            TableSession.isHost = true
            TableSession.gridSize = gridSize
            TableSession.tableMode = tableMode
            TableSession.maxPlayers = maxPlayers
            TableSession.targetWins = targetWins
            TableSession.roundEndMode = roundEndMode
        } else {
            TableSession.isHost = false
        }
        TableSession.roundState = RoundState.WAITING

        binding.startGameButton.isVisible = isHost
        binding.startGameButton.setOnClickListener { startRoundAsHost() }

        if (isHost) {
            binding.tableCodeCard.isVisible = true
            binding.tableCodeValue.text = shortTableCode(initialTableId)
            binding.tableCodeCopyButton.applyPressScaleAnimation()
            binding.tableCodeCopyButton.setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("table_code", binding.tableCodeValue.text))
                Toast.makeText(this, R.string.lobby_table_code_label, Toast.LENGTH_SHORT).show()
            }
        }

        val selfName = DeviceIdentity.displayName(this)
        TableSession.registerPlayer(selfName, selfName)

        if (isHost) {
            addPlayer(selfName, selfName, true)
            requestPermissionsAndAdvertise()
        } else {
            binding.lobbySubtitle.text = getString(R.string.lobby_waiting)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                confirmAction(
                    R.string.leave_table_title,
                    R.string.leave_table_message,
                    R.string.leave_match_confirm,
                    R.string.leave_match_cancel
                ) {
                    if (isHost) closeTableAsHost() else leaveAsGuest()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        nearby.listener = this
    }

    override fun onConnectionInitiated(endpointId: String, endpointName: String) {
        TableSession.bindEndpoint(endpointId, endpointName)
    }

    override fun onConnected(endpointId: String) {
        if (!isHost) return
        if (TableSession.activeRoster().size >= TableSession.maxPlayers) {
            nearby.disconnect(endpointId)
            Toast.makeText(this, R.string.lobby_table_full, Toast.LENGTH_SHORT).show()
            return
        }

        val playerId = TableSession.playerIdForEndpoint(endpointId) ?: endpointId
        TableSession.registerPlayer(playerId, playerId)
        addPlayer(playerId, playerId, false)
        broadcastLobbyState()
    }

    override fun onDisconnected(endpointId: String) {
        if (isHost) {
            val playerId = TableSession.unbindEndpoint(endpointId)
            if (playerId != null) {
                TableSession.removePlayer(playerId)
                playerRows.remove(playerId)?.let { binding.playersContainer.removeView(it.root) }
            }
            broadcastLobbyState()
        } else {
            showHostClosedDialog()
        }
    }

    override fun onMessageReceived(endpointId: String, message: GameMessage) {
        if (message.version != GameMessage.CURRENT_VERSION) return

        when (message.type) {
            GameMessageType.HOST_CLOSED -> if (!isHost) showHostClosedDialog()

            GameMessageType.LOBBY_STATE -> if (!isHost) renderRoster(message.payload)

            GameMessageType.START_GAME -> if (!isHost) handleStartGame(message)

            else -> Unit
        }
    }

    private fun handleStartGame(message: GameMessage) {
        runCatching {
            val payload = JSONObject(message.payload)
            val size = payload.getInt("gridSize")
            val seed = payload.getLong("seed")
            val startAt = payload.getLong("startAt")
            val expectedPlayers = payload.optInt("expectedPlayers", 2).coerceAtLeast(1)
            val receivedMaxPlayers = payload.optInt("maxPlayers", expectedPlayers).coerceIn(2, MAX_SUPPORTED_PLAYERS)
            val receivedTargetWins = payload.optInt("targetWins", DEFAULT_TARGET_WINS).let {
                if (it in VALID_TARGET_WINS) it else DEFAULT_TARGET_WINS
            }
            val receivedMode = runCatching {
                TableMode.valueOf(payload.optString("tableMode", TableMode.TABLE.name))
            }.getOrDefault(TableMode.TABLE)
            val receivedRoundEndMode = runCatching {
                RoundEndMode.valueOf(payload.optString("roundEndMode", RoundEndMode.FULL_RANKING.name))
            }.getOrDefault(RoundEndMode.FULL_RANKING)

            TableSession.tableId = message.tableId
            TableSession.gridSize = size
            TableSession.expectedPlayers = expectedPlayers
            TableSession.maxPlayers = receivedMaxPlayers
            TableSession.targetWins = receivedTargetWins
            TableSession.tableMode = receivedMode
            TableSession.roundEndMode = receivedRoundEndMode

            val roundId = message.roundId ?: seed.toString()
            scheduleGameStart(size, seed, startAt, roundId, expectedPlayers, message.tableId)
        }.onFailure {
            onError(it.message ?: getString(R.string.lobby_invalid_start_game))
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
        binding.lobbySubtitle.text = getString(R.string.lobby_grid_waiting, gridSize, gridSize, getString(R.string.lobby_waiting))
    }

    private fun startRoundAsHost() {
        val seed = System.currentTimeMillis()
        val roundId = UUID.randomUUID().toString()
        val startAt = System.currentTimeMillis() + START_DELAY_MS
        val expectedPlayers = TableSession.activeRoster().size.coerceAtLeast(1)
        TableSession.expectedPlayers = expectedPlayers

        val payload = JSONObject()
            .put("gridSize", gridSize)
            .put("seed", seed)
            .put("startAt", startAt)
            .put("expectedPlayers", expectedPlayers)
            .put("maxPlayers", TableSession.maxPlayers)
            .put("targetWins", TableSession.targetWins)
            .put("tableMode", TableSession.tableMode.name)
            .put("roundEndMode", TableSession.roundEndMode.name)
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
        scheduleGameStart(gridSize, seed, startAt, roundId, expectedPlayers, TableSession.tableId)
    }

    private fun scheduleGameStart(
        size: Int,
        seed: Long,
        startAt: Long,
        roundId: String,
        expectedPlayers: Int,
        resolvedTableId: String
    ) {
        TableSession.gridSize = size
        TableSession.expectedPlayers = expectedPlayers
        TableSession.roundState = RoundState.COUNTDOWN

        startScheduler.schedule(
            startAtMs = startAt,
            onTick = { secondsLeft -> binding.lobbySubtitle.text = getString(R.string.countdown_seconds, secondsLeft) },
            onStart = {
                binding.lobbySubtitle.text = getString(R.string.countdown_go)
                startActivity(
                    Intent(this, GameActivity::class.java).apply {
                        putExtra(GameActivity.EXTRA_GRID_SIZE, size)
                        putExtra(GameActivity.EXTRA_SEED, seed)
                        putExtra(GameActivity.EXTRA_IS_HOST, isHost)
                        putExtra(GameActivity.EXTRA_TABLE_ID, resolvedTableId)
                        putExtra(GameActivity.EXTRA_ROUND_ID, roundId)
                        putExtra(GameActivity.EXTRA_START_AT, startAt)
                        putExtra(GameActivity.EXTRA_EXPECTED_PLAYERS, expectedPlayers)
                    }
                )
                finish()
            }
        )
    }

    private fun broadcastLobbyState() {
        if (!isHost) return
        val roster = TableSession.activeRoster().map { (id, name) ->
            LobbyPlayerInfo(id, name, id == DeviceIdentity.displayName(this))
        }
        TableSession.expectedPlayers = roster.size
        nearby.broadcast(
            GameMessage(
                type = GameMessageType.LOBBY_STATE,
                tableId = TableSession.tableId,
                payload = LobbyRosterCodec.toJson(roster)
            )
        )
        binding.lobbySubtitle.text = getString(R.string.lobby_players_count, roster.size, TableSession.maxPlayers)
    }

    private fun renderRoster(payload: String) {
        val roster = runCatching { LobbyRosterCodec.fromJson(payload) }.getOrNull() ?: return
        playerRows.values.forEach { binding.playersContainer.removeView(it.root) }
        playerRows.clear()
        roster.forEach { player ->
            TableSession.registerPlayer(player.playerId, player.playerName)
            addPlayer(player.playerId, player.playerName, player.isHost)
        }
        TableSession.expectedPlayers = roster.size
        binding.lobbySubtitle.text = getString(R.string.lobby_players_count, roster.size, TableSession.maxPlayers)
    }

    /** Derives a short, shareable 6-character code from the real table id, purely for the host
     * to show/copy as a friendly session reference (joining itself still happens via Nearby
     * discovery, this is not a manual join-code entry mechanism). */
    private fun shortTableCode(tableId: String): String =
        tableId.replace("-", "").take(TABLE_CODE_LENGTH).uppercase().padEnd(TABLE_CODE_LENGTH, '0')

    private fun addPlayer(id: String, name: String, host: Boolean) {
        if (playerRows.containsKey(id)) return
        val row = ItemPlayerBinding.inflate(layoutInflater, binding.playersContainer, false)
        row.playerName.text = name
        row.playerStatus.text = getString(if (host) R.string.lobby_status_host else R.string.lobby_status_connected)
        row.playerStatus.setBackgroundResource(if (host) R.drawable.bg_badge_host else R.drawable.bg_badge_connected)
        playerRows[id] = row
        binding.playersContainer.addView(row.root)
    }

    private fun showHostClosedDialog() {
        if (hostClosedHandled) return
        hostClosedHandled = true
        HostDisconnectDialog.show(this, nearby) { goHome() }
    }

    private fun closeTableAsHost() {
        nearby.broadcast(GameMessage(type = GameMessageType.HOST_CLOSED, tableId = TableSession.tableId))
        nearby.disconnectAll()
        TableSession.clear()
        goHome()
    }

    /** Non-host leaving the lobby before the match starts: no HOST_CLOSED broadcast (host-only),
     * but still disconnect and clear local session state before returning to Home, instead of a
     * bare `finish()` that used to just pop back to the Nearby discovery/table-setup screen still
     * sitting underneath in the back stack instead of Home. */
    private fun leaveAsGuest() {
        nearby.disconnectAll()
        TableSession.clear()
        goHome()
    }

    override fun onDestroy() {
        startScheduler.cancel()
        handler.removeCallbacksAndMessages(null)
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
        const val EXTRA_ROUND_END_MODE = "extra_round_end_mode"
        private const val DEFAULT_GRID_SIZE = 4
        private const val DEFAULT_TABLE_ID = "local-table"
        private const val DEFAULT_MAX_PLAYERS = 4
        private const val DEFAULT_TARGET_WINS = 3
        private const val MAX_SUPPORTED_PLAYERS = 8
        private val VALID_TARGET_WINS = setOf(1, 3, 5)
        private const val START_DELAY_MS = 3000L
        private const val TABLE_CODE_LENGTH = 6
    }
}
