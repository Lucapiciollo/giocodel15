package com.lucapiciollo.giocodel15.feature.result

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.lucapiciollo.giocodel15.R
import com.lucapiciollo.giocodel15.databinding.ActivityResultBinding
import com.lucapiciollo.giocodel15.databinding.ItemRankingBinding
import com.lucapiciollo.giocodel15.feature.game.GameActivity
import com.lucapiciollo.giocodel15.multiplayer.model.TableMode
import com.lucapiciollo.giocodel15.multiplayer.nearby.NearbyConnectionManager
import com.lucapiciollo.giocodel15.multiplayer.nearby.NearbySession
import com.lucapiciollo.giocodel15.multiplayer.protocol.GameMessage
import com.lucapiciollo.giocodel15.multiplayer.protocol.GameMessageType
import com.lucapiciollo.giocodel15.multiplayer.session.TableSession
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class ResultActivity : AppCompatActivity(), NearbyConnectionManager.Listener {

    private lateinit var binding: ActivityResultBinding
    private lateinit var nearby: NearbyConnectionManager
    private val handler = Handler(Looper.getMainLooper())
    private val isHost by lazy { intent.getBooleanExtra(EXTRA_IS_HOST, false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        nearby = NearbySession.manager(this)
        nearby.listener = this

        val rankingJson = intent.getStringExtra(EXTRA_RANKING_JSON).orEmpty()
        renderRanking(rankingJson)
        renderTableRanking()

        val tableWinner = TableSession.winnerReachedTarget()
        binding.newRoundButton.isVisible = isHost && tableWinner == null
        binding.tableStatus.text = when {
            tableWinner != null -> getString(R.string.result_table_winner, tableWinner.playerName, tableWinner.wins)
            isHost -> ""
            else -> getString(R.string.result_wait_host)
        }

        binding.newRoundButton.setOnClickListener { startNewRoundAsHost() }
        binding.closeButton.setOnClickListener {
            nearby.disconnectAll()
            TableSession.clear()
            finishAffinity()
        }
    }

    override fun onResume() {
        super.onResume()
        nearby.listener = this
    }

    private fun renderRanking(json: String) {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: JSONArray()
        if (array.length() == 0) {
            binding.winnerTitle.text = getString(R.string.result_no_results)
            return
        }

        val winner = array.getJSONObject(0)
        binding.winnerTitle.text = getString(R.string.result_winner, winner.getString("playerName"))
        binding.winnerTime.text = formatElapsed(winner.getLong("elapsedMs"))

        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val row = ItemRankingBinding.inflate(layoutInflater, binding.rankingContainer, false)
            row.position.text = medalOrPosition(index)
            row.playerName.text = item.getString("playerName")
            row.playerTime.text = formatElapsed(item.getLong("elapsedMs"))
            row.playerMoves.text = item.getInt("moves").toString()
            binding.rankingContainer.addView(row.root)
        }
    }

    private fun renderTableRanking() {
        TableSession.ranking().forEachIndexed { index, score ->
            val row = ItemRankingBinding.inflate(layoutInflater, binding.tableRankingContainer, false)
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
        val roundId = seed.toString()
        val payload = JSONObject()
            .put("gridSize", TableSession.gridSize)
            .put("seed", seed)
            .put("startDelayMs", START_DELAY_MS)
            .put("expectedPlayers", TableSession.expectedPlayers)
            .put("maxPlayers", TableSession.maxPlayers)
            .put("targetWins", TableSession.targetWins)
            .put("tableMode", TableSession.tableMode.name)
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
        binding.tableStatus.text = "3 · 2 · 1 · VIA"
        scheduleRoundStart(TableSession.gridSize, seed, roundId, TableSession.expectedPlayers, START_DELAY_MS)
    }

    override fun onMessageReceived(endpointId: String, message: GameMessage) {
        if (isHost || message.type != GameMessageType.NEW_ROUND) return
        if (message.tableId != TableSession.tableId) return

        runCatching {
            val payload = JSONObject(message.payload)
            TableSession.maxPlayers = payload.optInt("maxPlayers", TableSession.maxPlayers).coerceAtLeast(2)
            TableSession.targetWins = payload.optInt("targetWins", TableSession.targetWins)
            TableSession.tableMode = runCatching {
                TableMode.valueOf(payload.optString("tableMode", TableSession.tableMode.name))
            }.getOrDefault(TableSession.tableMode)
            scheduleRoundStart(
                payload.getInt("gridSize"),
                payload.getLong("seed"),
                message.roundId,
                payload.optInt("expectedPlayers", TableSession.expectedPlayers).coerceAtLeast(1),
                payload.optLong("startDelayMs", START_DELAY_MS).coerceAtLeast(0L)
            )
        }.onFailure { onError(it.message ?: "Nuova manche non valida") }
    }

    private fun scheduleRoundStart(
        gridSize: Int,
        seed: Long,
        roundId: String,
        expectedPlayers: Int,
        delayMs: Long
    ) {
        TableSession.gridSize = gridSize
        TableSession.expectedPlayers = expectedPlayers
        binding.tableStatus.text = "3 · 2 · 1 · VIA"

        handler.postDelayed({
            startActivity(Intent(this, GameActivity::class.java).apply {
                putExtra(GameActivity.EXTRA_GRID_SIZE, gridSize)
                putExtra(GameActivity.EXTRA_SEED, seed)
                putExtra(GameActivity.EXTRA_IS_HOST, isHost)
                putExtra(GameActivity.EXTRA_TABLE_ID, TableSession.tableId)
                putExtra(GameActivity.EXTRA_ROUND_ID, roundId)
                putExtra(GameActivity.EXTRA_EXPECTED_PLAYERS, expectedPlayers)
            })
            finish()
        }, delayMs)
    }

    override fun onError(message: String) {
        binding.tableStatus.text = message
    }

    override fun onDisconnected(endpointId: String) = Unit

    private fun medalOrPosition(index: Int): String = when (index) {
        0 -> "🥇"
        1 -> "🥈"
        2 -> "🥉"
        else -> (index + 1).toString()
    }

    private fun formatElapsed(elapsedMs: Long): String {
        val totalSeconds = elapsedMs / 1000.0
        val minutes = elapsedMs / 60_000
        val seconds = totalSeconds - minutes * 60
        return String.format(Locale.getDefault(), "%02d:%06.3f", minutes, seconds)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RANKING_JSON = "extra_ranking_json"
        const val EXTRA_IS_HOST = "extra_is_host"
        private const val START_DELAY_MS = 3000L
    }
}
