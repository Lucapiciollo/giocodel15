package com.lucapiciollo.giocodel15.feature.game

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.lucapiciollo.giocodel15.R
import com.lucapiciollo.giocodel15.core.ui.PuzzleBoardConfig
import com.lucapiciollo.giocodel15.databinding.ActivityGameBinding
import com.lucapiciollo.giocodel15.feature.result.ResultActivity
import com.lucapiciollo.giocodel15.multiplayer.model.PlayerResult
import com.lucapiciollo.giocodel15.multiplayer.nearby.DeviceIdentity
import com.lucapiciollo.giocodel15.multiplayer.nearby.NearbyConnectionManager
import com.lucapiciollo.giocodel15.multiplayer.nearby.NearbySession
import com.lucapiciollo.giocodel15.multiplayer.protocol.GameMessage
import com.lucapiciollo.giocodel15.multiplayer.protocol.GameMessageType
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class GameActivity : AppCompatActivity(), NearbyConnectionManager.Listener {

    private lateinit var binding: ActivityGameBinding
    private val viewModel: GameViewModel by viewModels()
    private lateinit var nearby: NearbyConnectionManager
    private val handler = Handler(Looper.getMainLooper())
    private val results = linkedMapOf<String, PlayerResult>()

    private val isHost by lazy { intent.getBooleanExtra(EXTRA_IS_HOST, false) }
    private val tableId by lazy { intent.getStringExtra(EXTRA_TABLE_ID) ?: "local-table" }
    private val roundId by lazy { intent.getStringExtra(EXTRA_ROUND_ID) ?: "local-round" }
    private val expectedPlayers by lazy { intent.getIntExtra(EXTRA_EXPECTED_PLAYERS, 1).coerceAtLeast(1) }

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

        nearby = NearbySession.manager(this)
        nearby.listener = this

        val gridSize = intent.getIntExtra(EXTRA_GRID_SIZE, DEFAULT_GRID_SIZE)
        val seed = intent.getLongExtra(EXTRA_SEED, System.currentTimeMillis())
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
        handler.post(timerTick)

        binding.puzzleBoard.setOnStateChangedListener { state ->
            viewModel.updateState(state)
            renderStats()
        }

        binding.puzzleBoard.setOnSolvedListener { onPuzzleSolved() }
    }

    override fun onResume() {
        super.onResume()
        nearby.listener = this
    }

    private fun onPuzzleSolved() {
        if (viewModel.finishedElapsedMs != null) return
        val elapsed = viewModel.finish()
        val state = viewModel.puzzleState ?: return
        binding.puzzleBoard.configure(PuzzleBoardConfig(interactionEnabled = false, showNumbers = true, hapticFeedback = true))
        binding.gameStatus.setText(R.string.game_status_completed)
        binding.timerValue.text = formatElapsed(elapsed)

        val result = PlayerResult(
            playerId = DeviceIdentity.displayName(this),
            playerName = DeviceIdentity.displayName(this),
            elapsedMs = elapsed,
            moves = state.moves
        )

        if (isHost) {
            acceptResult(result)
        } else {
            nearby.broadcast(
                GameMessage(
                    type = GameMessageType.PLAYER_FINISHED,
                    tableId = tableId,
                    roundId = roundId,
                    payload = result.toJson().toString()
                )
            )
            binding.gameStatus.setText(R.string.game_waiting_results)
        }
    }

    override fun onMessageReceived(endpointId: String, message: GameMessage) {
        if (message.tableId != tableId || message.roundId != roundId) return
        when (message.type) {
            GameMessageType.PLAYER_FINISHED -> if (isHost) {
                runCatching { PlayerResult.fromJson(JSONObject(message.payload), endpointId) }
                    .onSuccess(::acceptResult)
            }
            GameMessageType.ROUND_RESULT -> if (!isHost) openResults(message.payload)
            else -> Unit
        }
    }

    private fun acceptResult(result: PlayerResult) {
        if (results.containsKey(result.playerId)) return
        if (result.elapsedMs <= 0L || result.moves <= 0) return
        results[result.playerId] = result
        binding.gameStatus.text = getString(R.string.game_finished_count, results.size, expectedPlayers)

        if (results.size >= expectedPlayers) {
            val ranking = results.values.sortedWith(compareBy<PlayerResult> { it.elapsedMs }.thenBy { it.moves })
            val json = JSONArray().apply { ranking.forEach { put(it.toJson()) } }.toString()
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
    }

    private fun openResults(rankingJson: String) {
        startActivity(Intent(this, ResultActivity::class.java).apply {
            putExtra(ResultActivity.EXTRA_RANKING_JSON, rankingJson)
        })
        finish()
    }

    override fun onDisconnected(endpointId: String) = Unit
    override fun onError(message: String) { binding.gameStatus.text = message }

    private fun renderStats() {
        val state = viewModel.puzzleState ?: return
        binding.movesValue.text = state.moves.toString()
        binding.gridValue.text = getString(R.string.game_grid_value, state.size, state.size)
        binding.gameStatus.setText(if (state.isSolved) R.string.game_status_completed else R.string.game_status_playing)
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

    private fun PlayerResult.toJson() = JSONObject()
        .put("playerId", playerId)
        .put("playerName", playerName)
        .put("elapsedMs", elapsedMs)
        .put("moves", moves)

    private fun PlayerResult.Companion.fromJson(json: JSONObject, fallbackId: String) = PlayerResult(
        playerId = json.optString("playerId", fallbackId),
        playerName = json.optString("playerName", fallbackId),
        elapsedMs = json.getLong("elapsedMs"),
        moves = json.getInt("moves")
    )

    companion object {
        const val EXTRA_GRID_SIZE = "extra_grid_size"
        const val EXTRA_SEED = "extra_seed"
        const val EXTRA_IS_HOST = "extra_is_host"
        const val EXTRA_TABLE_ID = "extra_table_id"
        const val EXTRA_ROUND_ID = "extra_round_id"
        const val EXTRA_EXPECTED_PLAYERS = "extra_expected_players"
        private const val DEFAULT_GRID_SIZE = 4
        private const val TIMER_REFRESH_MS = 50L
    }
}

private companion object PlayerResultCompanion
