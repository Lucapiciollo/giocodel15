package com.lucapiciollo.giocodel15.feature.result

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.lucapiciollo.giocodel15.R
import com.lucapiciollo.giocodel15.core.ui.applyNavigationBarBottomInset
import com.lucapiciollo.giocodel15.core.ui.applyStatusBarTopInset
import com.lucapiciollo.giocodel15.core.ui.playEntranceAnimation
import com.lucapiciollo.giocodel15.databinding.ActivityResultBinding
import com.lucapiciollo.giocodel15.databinding.ItemRankingBinding
import com.lucapiciollo.giocodel15.feature.create.CreateTableActivity
import com.lucapiciollo.giocodel15.feature.game.GameActivity
import com.lucapiciollo.giocodel15.feature.home.HomeActivity
import com.lucapiciollo.giocodel15.feature.nearby.NearbyTablesActivity
import com.lucapiciollo.giocodel15.multiplayer.model.RoundEndMode
import com.lucapiciollo.giocodel15.multiplayer.model.RoundParticipantStatus
import com.lucapiciollo.giocodel15.multiplayer.model.RoundRankingEntry
import com.lucapiciollo.giocodel15.multiplayer.model.TableMode
import com.lucapiciollo.giocodel15.multiplayer.nearby.HostDisconnectDialog
import com.lucapiciollo.giocodel15.multiplayer.nearby.NearbyConnectionManager
import com.lucapiciollo.giocodel15.multiplayer.nearby.NearbySession
import com.lucapiciollo.giocodel15.multiplayer.protocol.GameMessage
import com.lucapiciollo.giocodel15.multiplayer.protocol.GameMessageType
import com.lucapiciollo.giocodel15.multiplayer.protocol.RoundRankingCodec
import com.lucapiciollo.giocodel15.multiplayer.session.RoundStartScheduler
import com.lucapiciollo.giocodel15.multiplayer.session.RoundState
import com.lucapiciollo.giocodel15.multiplayer.session.TableSession
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

class ResultActivity : AppCompatActivity(), NearbyConnectionManager.Listener {

    private lateinit var binding: ActivityResultBinding
    private lateinit var nearby: NearbyConnectionManager
    private val handler = Handler(Looper.getMainLooper())
    private val startScheduler = RoundStartScheduler(handler)
    private val isHost by lazy { intent.getBooleanExtra(EXTRA_IS_HOST, false) }
    private var hostClosedHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyStatusBarTopInset()
        binding.root.applyNavigationBarBottomInset()
        binding.root.playEntranceAnimation()

        nearby = NearbySession.manager(this)
        nearby.listener = this
        TableSession.roundState = RoundState.RESULTS

        val rankingJson = intent.getStringExtra(EXTRA_RANKING_JSON).orEmpty()
        renderRanking(rankingJson)
        renderTableRanking()

        val tableWinner = TableSession.winnerReachedTarget()
        binding.newRoundButton.isVisible = isHost && tableWinner == null
        binding.newTableButton.isVisible = tableWinner != null
        binding.tableStatus.text = when {
            tableWinner != null -> getString(R.string.result_table_winner, tableWinner.playerName, tableWinner.wins)
            isHost -> ""
            else -> getString(R.string.result_wait_host)
        }

        binding.newRoundButton.setOnClickListener { startNewRoundAsHost() }
        binding.newTableButton.setOnClickListener { startNewTable() }
        binding.closeButton.setOnClickListener {
            if (isHost) {
                nearby.broadcast(GameMessage(type = GameMessageType.HOST_CLOSED, tableId = TableSession.tableId))
            }
            nearby.disconnectAll()
            TableSession.clear()
            goHome()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isHost) {
                    nearby.broadcast(GameMessage(type = GameMessageType.HOST_CLOSED, tableId = TableSession.tableId))
                    nearby.disconnectAll()
                    TableSession.clear()
                }
                goHome()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        nearby.listener = this
    }

    private fun renderRanking(json: String) {
        val entries = runCatching { RoundRankingCodec.fromJson(json) }.getOrNull().orEmpty()
        if (entries.isEmpty()) {
            binding.winnerTitle.text = getString(R.string.result_no_results)
            binding.winnerTrophy.isVisible = false
            return
        }

        val winner = entries.firstOrNull { it.status == RoundParticipantStatus.FINISHED }
        if (winner?.result != null) {
            binding.winnerTitle.text = getString(R.string.result_winner, winner.playerName)
            binding.winnerTime.text = formatElapsed(winner.result.elapsedMs)
            binding.winnerTrophy.isVisible = true
        } else {
            binding.winnerTitle.text = getString(R.string.result_no_results)
            binding.winnerTrophy.isVisible = false
        }

        entries.forEachIndexed { index, entry -> addRankingRow(index, entry) }
    }

    private fun addRankingRow(index: Int, entry: RoundRankingEntry) {
        val row = ItemRankingBinding.inflate(layoutInflater, binding.rankingContainer, false)
        row.root.setBackgroundResource(rankCardBackground(index))
        row.playerName.text = entry.playerName
        val result = entry.result
        if (entry.status == RoundParticipantStatus.FINISHED && result != null) {
            row.position.text = medalOrPosition(index)
            row.playerTime.text = formatElapsed(result.elapsedMs)
            row.playerMoves.text = result.moves.toString()
        } else {
            row.position.text = getString(R.string.result_position_none)
            row.playerMoves.text = ""
            row.playerTime.text = getString(
                if (entry.status == RoundParticipantStatus.DISCONNECTED) {
                    R.string.result_status_disconnected
                } else {
                    R.string.result_status_dnf
                }
            )
        }
        binding.rankingContainer.addView(row.root)
    }

    private fun renderTableRanking() {
        TableSession.ranking().forEachIndexed { index, score ->
            val row = ItemRankingBinding.inflate(layoutInflater, binding.tableRankingContainer, false)
            row.root.setBackgroundResource(rankCardBackground(index))
            row.position.text = medalOrPosition(index)
            row.playerName.text = score.playerName
            row.playerTime.text = getString(R.string.result_wins, score.wins)
            row.playerMoves.text = ""
            binding.tableRankingContainer.addView(row.root)
        }
    }

    private fun startNewRoundAsHost() {
        if (!isHost || TableSession.winnerReachedTarget() != null) return

        val seed = System.currentTimeMillis()
        val roundId = UUID.randomUUID().toString()
        val startAt = System.currentTimeMillis() + START_DELAY_MS
        val expectedPlayers = TableSession.activeRoster().size.coerceAtLeast(1)
        TableSession.expectedPlayers = expectedPlayers

        val payload = JSONObject()
            .put("gridSize", TableSession.gridSize)
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
                type = GameMessageType.NEW_ROUND,
                tableId = TableSession.tableId,
                roundId = roundId,
                payload = payload
            )
        )

        binding.newRoundButton.isEnabled = false
        scheduleRoundStart(TableSession.gridSize, seed, startAt, roundId, expectedPlayers)
    }

    private fun startNewTable() {
        if (isHost) {
            nearby.broadcast(GameMessage(type = GameMessageType.HOST_CLOSED, tableId = TableSession.tableId))
        }
        nearby.disconnectAll()
        TableSession.clear()
        startActivity(
            Intent(this, if (isHost) CreateTableActivity::class.java else NearbyTablesActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    override fun onMessageReceived(endpointId: String, message: GameMessage) {
        if (message.type == GameMessageType.HOST_CLOSED) {
            if (!isHost) showHostClosedDialog()
            return
        }
        if (isHost || message.type != GameMessageType.NEW_ROUND) return
        if (message.tableId != TableSession.tableId) return

        runCatching {
            val payload = JSONObject(message.payload)
            TableSession.maxPlayers = payload.optInt("maxPlayers", TableSession.maxPlayers).coerceAtLeast(2)
            TableSession.targetWins = payload.optInt("targetWins", TableSession.targetWins)
            TableSession.tableMode = runCatching {
                TableMode.valueOf(payload.optString("tableMode", TableSession.tableMode.name))
            }.getOrDefault(TableSession.tableMode)
            TableSession.roundEndMode = runCatching {
                RoundEndMode.valueOf(payload.optString("roundEndMode", TableSession.roundEndMode.name))
            }.getOrDefault(TableSession.roundEndMode)
            val seed = payload.getLong("seed")
            val startAt = payload.getLong("startAt")
            scheduleRoundStart(
                payload.getInt("gridSize"),
                seed,
                startAt,
                message.roundId ?: seed.toString(),
                payload.optInt("expectedPlayers", TableSession.expectedPlayers).coerceAtLeast(1)
            )
        }.onFailure { onError(it.message ?: getString(R.string.result_invalid_new_round)) }
    }

    private fun scheduleRoundStart(
        gridSize: Int,
        seed: Long,
        startAt: Long,
        roundId: String,
        expectedPlayers: Int
    ) {
        TableSession.gridSize = gridSize
        TableSession.expectedPlayers = expectedPlayers
        TableSession.roundState = RoundState.COUNTDOWN

        startScheduler.schedule(
            startAtMs = startAt,
            onTick = { secondsLeft -> binding.tableStatus.text = getString(R.string.countdown_seconds, secondsLeft) },
            onStart = {
                binding.tableStatus.text = getString(R.string.countdown_go)
                startActivity(Intent(this, GameActivity::class.java).apply {
                    putExtra(GameActivity.EXTRA_GRID_SIZE, gridSize)
                    putExtra(GameActivity.EXTRA_SEED, seed)
                    putExtra(GameActivity.EXTRA_IS_HOST, isHost)
                    putExtra(GameActivity.EXTRA_TABLE_ID, TableSession.tableId)
                    putExtra(GameActivity.EXTRA_ROUND_ID, roundId)
                    putExtra(GameActivity.EXTRA_START_AT, startAt)
                    putExtra(GameActivity.EXTRA_EXPECTED_PLAYERS, expectedPlayers)
                })
                finish()
            }
        )
    }

    override fun onError(message: String) {
        binding.tableStatus.text = message
    }

    override fun onDisconnected(endpointId: String) {
        if (!isHost) showHostClosedDialog()
    }

    private fun showHostClosedDialog() {
        if (hostClosedHandled) return
        hostClosedHandled = true
        HostDisconnectDialog.show(this, nearby) { goHome() }
    }

    private fun goHome() {
        startActivity(
            Intent(this, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    /** No emoji medals per the Premium Gamer spec — rank is conveyed by the card's color
     * (gold/blue/neutral, see [rankCardBackground]) plus a plain numeric position. */
    private fun medalOrPosition(index: Int): String = (index + 1).toString()

    private fun rankCardBackground(index: Int): Int = when (index) {
        0 -> R.drawable.bg_card_gold
        1 -> R.drawable.bg_card_blue
        else -> R.drawable.bg_card_neutral
    }

    private fun formatElapsed(elapsedMs: Long): String {
        val totalSeconds = elapsedMs / 1000.0
        val minutes = elapsedMs / 60_000
        val seconds = totalSeconds - minutes * 60
        return String.format(Locale.getDefault(), "%02d:%06.3f", minutes, seconds)
    }

    override fun onDestroy() {
        startScheduler.cancel()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RANKING_JSON = "extra_ranking_json"
        const val EXTRA_IS_HOST = "extra_is_host"
        private const val START_DELAY_MS = 3000L
    }
}
