package com.lucapiciollo.giocodel15.feature.game

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.lucapiciollo.giocodel15.R
import com.lucapiciollo.giocodel15.core.ui.applyNavigationBarBottomInset
import com.lucapiciollo.giocodel15.core.ui.applyStatusBarTopInset
import com.lucapiciollo.giocodel15.core.ui.confirmAction
import com.lucapiciollo.giocodel15.core.ui.goHome
import com.lucapiciollo.giocodel15.core.ui.playEntranceAnimation
import com.lucapiciollo.giocodel15.core.ui.PuzzleBoardConfig
import com.lucapiciollo.giocodel15.databinding.ActivityGameBinding
import com.lucapiciollo.giocodel15.feature.result.ResultActivity
import com.lucapiciollo.giocodel15.multiplayer.model.PlayerResult
import com.lucapiciollo.giocodel15.multiplayer.nearby.DeviceIdentity
import com.lucapiciollo.giocodel15.multiplayer.nearby.HostDisconnectDialog
import com.lucapiciollo.giocodel15.multiplayer.nearby.NearbyConnectionManager
import com.lucapiciollo.giocodel15.multiplayer.nearby.NearbySession
import com.lucapiciollo.giocodel15.multiplayer.protocol.GameMessage
import com.lucapiciollo.giocodel15.multiplayer.protocol.GameMessageType
import com.lucapiciollo.giocodel15.multiplayer.protocol.PlayerResultCodec
import com.lucapiciollo.giocodel15.multiplayer.protocol.RoundRankingCodec
import com.lucapiciollo.giocodel15.multiplayer.ranking.RankingCalculator
import com.lucapiciollo.giocodel15.multiplayer.round.RoundResultManager
import com.lucapiciollo.giocodel15.multiplayer.session.RoundState
import com.lucapiciollo.giocodel15.multiplayer.session.TableSession
import com.lucapiciollo.giocodel15.multiplayer.validation.ValidationResult
import org.json.JSONObject
import java.util.Locale

class GameActivity : AppCompatActivity(), NearbyConnectionManager.Listener {

    private lateinit var binding: ActivityGameBinding
    private val viewModel: GameViewModel by viewModels()
    private lateinit var nearby: NearbyConnectionManager
    private val handler = Handler(Looper.getMainLooper())

    private val isHost by lazy { intent.getBooleanExtra(EXTRA_IS_HOST, false) }
    private val tableId by lazy { intent.getStringExtra(EXTRA_TABLE_ID) ?: "local-table" }
    private val roundId by lazy { intent.getStringExtra(EXTRA_ROUND_ID) ?: "local-round" }
    private val startAtMs by lazy { intent.getLongExtra(EXTRA_START_AT, System.currentTimeMillis()) }
    private val expectedPlayers by lazy { intent.getIntExtra(EXTRA_EXPECTED_PLAYERS, 1).coerceAtLeast(1) }

    /** Host-only authority deciding validation/ranking/round-end. Null on client devices. */
    private var roundResultManager: RoundResultManager? = null
    private var roundFinalized = false
    private var hostClosedHandled = false

    private val timeoutChecker = object : Runnable {
        override fun run() {
            val manager = roundResultManager ?: return
            if (roundFinalized) return
            if (manager.isComplete()) {
                finalizeRound(manager)
            } else {
                handler.postDelayed(this, TIMEOUT_CHECK_INTERVAL_MS)
            }
        }
    }

    private val timerTick = object : Runnable {
        override fun run() {
            binding.timerValue.text = formatElapsed(viewModel.elapsedMs())
            if (viewModel.finishedElapsedMs == null) handler.postDelayed(this, TIMER_REFRESH_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyStatusBarTopInset()
        binding.root.applyNavigationBarBottomInset()
        binding.root.playEntranceAnimation()

        nearby = NearbySession.manager(this)
        nearby.listener = this

        val gridSize = intent.getIntExtra(EXTRA_GRID_SIZE, DEFAULT_GRID_SIZE)
        val seed = intent.getLongExtra(EXTRA_SEED, System.currentTimeMillis())

        TableSession.tableId = tableId
        TableSession.isHost = isHost
        TableSession.gridSize = gridSize
        TableSession.expectedPlayers = expectedPlayers
        TableSession.roundState = RoundState.PLAYING

        if (isHost) {
            val roster = TableSession.activeRoster().ifEmpty {
                val self = DeviceIdentity.displayName(this)
                listOf(self to self)
            }
            roundResultManager = RoundResultManager(
                roundId = roundId,
                gridSize = gridSize,
                seed = seed,
                startAtMs = startAtMs,
                endMode = TableSession.roundEndMode,
                players = roster
            )
            handler.postDelayed(timeoutChecker, TIMEOUT_CHECK_INTERVAL_MS)
        }

        viewModel.initialize(gridSize, seed)

        binding.puzzleBoard.configure(
            PuzzleBoardConfig(
                interactionEnabled = viewModel.finishedElapsedMs == null,
                showNumbers = true,
                hapticFeedback = true
            )
        )

        viewModel.puzzleState?.let(binding.puzzleBoard::setPuzzleState)
        renderStats()
        binding.gameStatus.text = getString(
            R.string.game_players_racing,
            roundResultManager?.totalPlayers ?: expectedPlayers
        )
        handler.post(timerTick)

        binding.puzzleBoard.setOnStateChangedListener { state ->
            viewModel.updateState(state)
            renderStats()
        }
        binding.puzzleBoard.setOnTileMovedListener { tileIndex -> viewModel.recordMove(tileIndex) }
        binding.puzzleBoard.setOnSolvedListener { onPuzzleSolved() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                confirmAction(
                    R.string.leave_match_title,
                    R.string.leave_match_message,
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

    private fun onPuzzleSolved() {
        if (viewModel.finishedElapsedMs != null) return
        val elapsed = viewModel.finish()
        val state = viewModel.puzzleState ?: return
        binding.puzzleBoard.configure(
            PuzzleBoardConfig(
                interactionEnabled = false,
                showNumbers = true,
                hapticFeedback = true
            )
        )
        binding.gameStatus.setText(R.string.game_status_completed)
        binding.timerValue.text = formatElapsed(elapsed)

        val identity = DeviceIdentity.displayName(this)
        val result = PlayerResult(
            playerId = identity,
            playerName = identity,
            roundId = roundId,
            elapsedMs = elapsed,
            moves = state.moves,
            finishedAt = System.currentTimeMillis(),
            boardHash = viewModel.initialBoardHash.orEmpty(),
            moveSequence = viewModel.moveSequence
        )

        if (isHost) {
            handleIncomingResult(result)
        } else {
            nearby.broadcast(
                GameMessage(
                    type = GameMessageType.PLAYER_FINISHED,
                    tableId = tableId,
                    roundId = roundId,
                    payload = PlayerResultCodec.toJson(result).toString()
                )
            )
            binding.gameStatus.setText(R.string.game_waiting_results)
        }
    }

    override fun onMessageReceived(endpointId: String, message: GameMessage) {
        if (message.type == GameMessageType.HOST_CLOSED) {
            if (!isHost) showHostClosedDialog()
            return
        }
        if (message.tableId != tableId || message.roundId != roundId) return
        when (message.type) {
            GameMessageType.PLAYER_FINISHED -> if (isHost) {
                runCatching { PlayerResultCodec.fromJson(JSONObject(message.payload), endpointId) }
                    .onSuccess(::handleIncomingResult)
            }
            GameMessageType.PLAYER_RESULT -> if (!isHost) {
                runCatching {
                    val json = JSONObject(message.payload)
                    binding.gameStatus.text = getString(
                        R.string.game_opponent_finished,
                        json.getString("playerName"),
                        json.getInt("finished"),
                        json.getInt("total")
                    )
                }
            }
            GameMessageType.ROUND_RESULT -> if (!isHost) openResults(message.payload)
            else -> Unit
        }
    }

    /** Host-only: validates and (if accepted) folds a result into the round, broadcasting a
     * light "N/total finished" update without revealing anyone's time. */
    private fun handleIncomingResult(result: PlayerResult) {
        val manager = roundResultManager ?: return
        if (roundFinalized) return

        when (val outcome = manager.tryAccept(result)) {
            is ValidationResult.Invalid -> {
                onError(getString(R.string.game_status_playing) + ": " + outcome.reason)
                return
            }

            ValidationResult.Valid -> Unit
        }

        val position = RankingCalculator.positionOf(manager.acceptedResults(), result.playerId)
            ?: manager.finishedCount

        nearby.broadcast(
            GameMessage(
                type = GameMessageType.PLAYER_RESULT,
                tableId = tableId,
                roundId = roundId,
                payload = JSONObject()
                    .put("playerName", result.playerName)
                    .put("position", position)
                    .put("finished", manager.finishedCount)
                    .put("total", manager.totalPlayers)
                    .toString()
            )
        )

        binding.gameStatus.text = getString(
            R.string.game_finished_count,
            manager.finishedCount,
            manager.totalPlayers
        )

        if (manager.isComplete()) finalizeRound(manager)
    }

    private fun finalizeRound(manager: RoundResultManager) {
        if (roundFinalized) return
        roundFinalized = true
        val ranking = manager.buildFinalRanking()
        val json = RoundRankingCodec.toJson(ranking)

        nearby.broadcast(
            GameMessage(
                type = GameMessageType.ROUND_RESULT,
                tableId = tableId,
                roundId = roundId,
                payload = json
            )
        )
        openResults(json)
    }

    private fun openResults(rankingJson: String) {
        val entries = RoundRankingCodec.fromJson(rankingJson)
        TableSession.applyRound(entries.mapNotNull { it.result })
        TableSession.roundState = RoundState.RESULTS

        startActivity(Intent(this, ResultActivity::class.java).apply {
            putExtra(ResultActivity.EXTRA_RANKING_JSON, rankingJson)
            putExtra(ResultActivity.EXTRA_IS_HOST, isHost)
        })
        finish()
    }

    override fun onDisconnected(endpointId: String) {
        if (isHost) {
            val playerId = TableSession.unbindEndpoint(endpointId) ?: return
            val manager = roundResultManager ?: return
            manager.markDisconnected(playerId)
            if (manager.isComplete()) finalizeRound(manager)
        } else {
            showHostClosedDialog()
        }
    }

    override fun onError(message: String) {
        binding.gameStatus.text = message
    }

    private fun showHostClosedDialog() {
        if (hostClosedHandled) return
        hostClosedHandled = true
        HostDisconnectDialog.show(this, nearby) { goHome() }
    }

    private fun closeTableAsHost() {
        nearby.broadcast(GameMessage(type = GameMessageType.HOST_CLOSED, tableId = tableId, roundId = roundId))
        nearby.disconnectAll()
        TableSession.clear()
        goHome()
    }

    /** Non-host leaving an active match: no HOST_CLOSED broadcast (that's the host's job), but
     * still disconnect and clear local session state before returning to Home, same as the host
     * path, instead of a bare `finish()` that used to just pop back to whatever screen (Nearby
     * discovery/table setup) was underneath in the stack instead of Home. */
    private fun leaveAsGuest() {
        nearby.disconnectAll()
        TableSession.clear()
        goHome()
    }

    private fun renderStats() {
        val state = viewModel.puzzleState ?: return
        binding.movesValue.text = state.moves.toString()
        binding.gridValue.text = getString(R.string.game_grid_value, state.size, state.size)
    }

    private fun formatElapsed(elapsedMs: Long): String {
        val minutes = elapsedMs / 60_000
        val seconds = (elapsedMs % 60_000) / 1000
        val millis = elapsedMs % 1000
        return String.format(Locale.getDefault(), "%02d:%02d.%03d", minutes, seconds, millis)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_GRID_SIZE = "extra_grid_size"
        const val EXTRA_SEED = "extra_seed"
        const val EXTRA_IS_HOST = "extra_is_host"
        const val EXTRA_TABLE_ID = "extra_table_id"
        const val EXTRA_ROUND_ID = "extra_round_id"
        const val EXTRA_START_AT = "extra_start_at"
        const val EXTRA_EXPECTED_PLAYERS = "extra_expected_players"
        private const val DEFAULT_GRID_SIZE = 4
        private const val TIMER_REFRESH_MS = 50L
        private const val TIMEOUT_CHECK_INTERVAL_MS = 2_000L
    }
}
