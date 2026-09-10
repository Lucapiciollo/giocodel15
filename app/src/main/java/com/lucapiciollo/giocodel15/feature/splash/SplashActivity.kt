package com.lucapiciollo.giocodel15.feature.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.lucapiciollo.giocodel15.R
import com.lucapiciollo.giocodel15.databinding.ActivitySplashBinding
import com.lucapiciollo.giocodel15.feature.home.HomeActivity

/** First screen shown at cold start: animates the puzzle tiles flying in and assembling,
 * then reveals the app title/tagline and hands off to [HomeActivity]. Purely decorative,
 * no game/Nearby logic involved. */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val tiles = collectTiles(binding.tileGrid)
        val flyDistance = resources.getDimension(R.dimen.splash_fly_distance)

        tiles.forEachIndexed { index, tile ->
            val row = index / COLUMNS
            val col = index % COLUMNS
            tile.alpha = 0f
            tile.scaleX = TILE_START_SCALE
            tile.scaleY = TILE_START_SCALE
            tile.translationX = (col - CENTER_COL) * flyDistance
            tile.translationY = (row - CENTER_ROW) * flyDistance

            tile.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationX(0f)
                .translationY(0f)
                .setStartDelay(index * TILE_STAGGER_MS)
                .setDuration(TILE_ANIM_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        val titleDelay = tiles.size * TILE_STAGGER_MS + TILE_ANIM_MS
        binding.appTitle.animate().alpha(1f).setStartDelay(titleDelay).setDuration(TITLE_ANIM_MS).start()
        binding.appTagline.animate()
            .alpha(1f)
            .setStartDelay(titleDelay + TAGLINE_EXTRA_DELAY_MS)
            .setDuration(TITLE_ANIM_MS)
            .start()

        handler.postDelayed({ goToHome() }, titleDelay + TAGLINE_EXTRA_DELAY_MS + HOLD_MS)
    }

    private fun collectTiles(container: ViewGroup): List<View> = buildList {
        for (rowIndex in 0 until container.childCount) {
            val row = container.getChildAt(rowIndex) as? ViewGroup ?: continue
            for (colIndex in 0 until row.childCount) {
                val child = row.getChildAt(colIndex)
                if (child.tag == TILE_TAG) add(child)
            }
        }
    }

    private fun goToHome() {
        if (isFinishing) return
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val TILE_TAG = "tile"
        private const val COLUMNS = 4
        private const val CENTER_COL = 1.5f
        private const val CENTER_ROW = 1f
        private const val TILE_START_SCALE = 0.4f
        private const val TILE_STAGGER_MS = 60L
        private const val TILE_ANIM_MS = 420L
        private const val TITLE_ANIM_MS = 350L
        private const val TAGLINE_EXTRA_DELAY_MS = 120L
        private const val HOLD_MS = 450L
    }
}
