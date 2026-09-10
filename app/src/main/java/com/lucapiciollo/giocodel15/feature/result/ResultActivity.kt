package com.lucapiciollo.giocodel15.feature.result

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lucapiciollo.giocodel15.databinding.ActivityResultBinding
import com.lucapiciollo.giocodel15.databinding.ItemRankingBinding
import org.json.JSONArray
import java.util.Locale

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val rankingJson = intent.getStringExtra(EXTRA_RANKING_JSON).orEmpty()
        renderRanking(rankingJson)
        binding.closeButton.setOnClickListener { finish() }
    }

    private fun renderRanking(json: String) {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: JSONArray()
        if (array.length() == 0) {
            binding.winnerTitle.text = getString(com.lucapiciollo.giocodel15.R.string.result_no_results)
            return
        }

        val winner = array.getJSONObject(0)
        binding.winnerTitle.text = getString(
            com.lucapiciollo.giocodel15.R.string.result_winner,
            winner.getString("playerName")
        )
        binding.winnerTime.text = formatElapsed(winner.getLong("elapsedMs"))

        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            val row = ItemRankingBinding.inflate(layoutInflater, binding.rankingContainer, false)
            row.position.text = (index + 1).toString()
            row.playerName.text = item.getString("playerName")
            row.playerTime.text = formatElapsed(item.getLong("elapsedMs"))
            row.playerMoves.text = item.getInt("moves").toString()
            binding.rankingContainer.addView(row.root)
        }
    }

    private fun formatElapsed(elapsedMs: Long): String {
        val totalSeconds = elapsedMs / 1000.0
        val minutes = (elapsedMs / 60_000)
        val seconds = totalSeconds - minutes * 60
        return String.format(Locale.getDefault(), "%02d:%06.3f", minutes, seconds)
    }

    companion object {
        const val EXTRA_RANKING_JSON = "extra_ranking_json"
    }
}
