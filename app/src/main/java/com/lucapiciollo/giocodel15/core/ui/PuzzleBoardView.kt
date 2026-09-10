package com.lucapiciollo.giocodel15.core.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.lucapiciollo.giocodel15.R
import com.lucapiciollo.giocodel15.audio.GameAudioManager
import com.lucapiciollo.giocodel15.game.engine.PuzzleEngine
import com.lucapiciollo.giocodel15.game.model.PuzzleState
import kotlin.math.min

class PuzzleBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val tileStartColor = ContextCompat.getColor(context, R.color.game_tile)
    private val tileEndColor = ContextCompat.getColor(context, R.color.game_tile_dark)
    private val tileBaseColor = ContextCompat.getColor(context, R.color.game_tile_shadow)
    private val glowColor = withAlpha(ContextCompat.getColor(context, R.color.game_primary), GLOW_ALPHA)

    /** Solid "side" of the 3D bevel: a flat-color step peeking out from behind the tile face,
     * offset downward, simulating physical thickness (like a chiclet/arcade button). Also
     * carries the soft ambient contact shadow, since it sits at the lowest visual point. */
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tileBaseColor
        setShadowLayer(SHADOW_RADIUS_DP * density, 0f, SHADOW_DY_DP * density, SHADOW_COLOR)
    }

    /** Tile face: diagonal gradient, shader assigned per-tile in onDraw. */
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Glossy top-lit highlight overlaid on the face for a rounded/raised look. */
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = glowColor
        setShadowLayer(GLOW_RADIUS_DP * density, 0f, 0f, glowColor)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        color = ContextCompat.getColor(context, R.color.game_tile_text)
    }

    private var state: PuzzleState = PuzzleState.solved(DEFAULT_GRID_SIZE)
    private var config: PuzzleBoardConfig = PuzzleBoardConfig()
    private var onStateChanged: ((PuzzleState) -> Unit)? = null
    private var onSolved: ((PuzzleState) -> Unit)? = null
    private var onTileMoved: ((Int) -> Unit)? = null

    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(R.string.puzzle_board_content_description)
        // Canvas shadow layers on non-text shapes are only honored on a software-rendered
        // layer; the board is small and redrawn only on discrete moves, so the cost is negligible.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setPuzzleState(newState: PuzzleState) {
        state = newState
        invalidate()
    }

    fun getPuzzleState(): PuzzleState = state

    fun configure(newConfig: PuzzleBoardConfig) {
        config = newConfig
        isEnabled = newConfig.interactionEnabled
        invalidate()
    }

    fun setOnStateChangedListener(listener: ((PuzzleState) -> Unit)?) {
        onStateChanged = listener
    }

    fun setOnSolvedListener(listener: ((PuzzleState) -> Unit)?) {
        onSolved = listener
    }

    /** Notified with the tile index (click target) of every successful move, in order. */
    fun setOnTileMovedListener(listener: ((Int) -> Unit)?) {
        onTileMoved = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val requestedWidth = MeasureSpec.getSize(widthMeasureSpec)
        val requestedHeight = MeasureSpec.getSize(heightMeasureSpec)

        val width = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> suggestedMinimumWidth
            else -> requestedWidth
        }
        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> width
            else -> requestedHeight
        }

        val square = min(width, height)
        setMeasuredDimension(resolveSize(square, widthMeasureSpec), resolveSize(square, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val boardSize = min(width, height).toFloat()
        val gap = config.tileGapDp * density
        val totalGap = gap * (state.size - 1)
        val tileSize = (boardSize - totalGap) / state.size
        val cornerRadius = config.cornerRadiusDp * density
        val glowInset = GLOW_INSET_DP * density
        val bevelDepth = BEVEL_DEPTH_DP * density

        val movableIndices = if (config.interactionEnabled) {
            PuzzleEngine.movableTileIndices(state)
        } else {
            emptyList()
        }

        state.tiles.forEachIndexed { index, value ->
            if (value == PuzzleState.EMPTY_TILE) return@forEachIndexed

            val row = index / state.size
            val col = index % state.size
            val left = col * (tileSize + gap)
            val top = row * (tileSize + gap)
            val rect = RectF(left, top, left + tileSize, top + tileSize)

            if (index in movableIndices) {
                val glowRect = RectF(
                    rect.left + glowInset,
                    rect.top + glowInset,
                    rect.right - glowInset,
                    rect.bottom - glowInset
                )
                canvas.drawRoundRect(glowRect, cornerRadius, cornerRadius, glowPaint)
            }

            // 1) Solid base "side" of the tile, offset down: gives the tile physical thickness
            // and carries the soft ambient contact shadow (chiclet/3D-button look).
            val baseRect = RectF(rect.left, rect.top + bevelDepth, rect.right, rect.bottom + bevelDepth)
            canvas.drawRoundRect(baseRect, cornerRadius, cornerRadius, basePaint)

            // 2) Tile face: diagonal gradient, drawn at the un-shifted position so the base
            // peeks out from underneath as a flat-color edge.
            tilePaint.shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                tileStartColor, tileEndColor, Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, tilePaint)

            // 3) Glossy top-lit highlight for a rounded/raised look.
            highlightPaint.shader = LinearGradient(
                rect.left, rect.top, rect.left, rect.bottom,
                HIGHLIGHT_COLOR, TRANSPARENT, Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, highlightPaint)

            if (config.showNumbers) {
                textPaint.textSize = tileSize * TEXT_SIZE_RATIO
                val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
                canvas.drawText(value.toString(), rect.centerX(), baseline, textPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!config.interactionEnabled || !isEnabled) return false
        if (event.action != MotionEvent.ACTION_UP) return true

        val index = tileIndexAt(event.x, event.y) ?: return true
        val moved = PuzzleEngine.move(state, index)

        if (moved !== state) {
            state = moved
            if (config.hapticFeedback) {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            GameAudioManager.playTileMove(context)
            onTileMoved?.invoke(index)
            onStateChanged?.invoke(state)
            invalidate()

            if (state.isSolved) {
                onSolved?.invoke(state)
            }
        }

        performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun tileIndexAt(x: Float, y: Float): Int? {
        val boardSize = min(width, height).toFloat()
        if (x !in 0f..boardSize || y !in 0f..boardSize) return null

        val gap = config.tileGapDp * density
        val tileSize = (boardSize - gap * (state.size - 1)) / state.size
        val step = tileSize + gap
        val col = (x / step).toInt().coerceIn(0, state.size - 1)
        val row = (y / step).toInt().coerceIn(0, state.size - 1)

        val localX = x - col * step
        val localY = y - row * step
        if (localX > tileSize || localY > tileSize) return null

        return row * state.size + col
    }

    companion object {
        private const val DEFAULT_GRID_SIZE = 4
        private const val TEXT_SIZE_RATIO = 0.38f
        private const val SHADOW_RADIUS_DP = 6f
        private const val SHADOW_DY_DP = 3f
        private const val SHADOW_COLOR = 0x8A000000.toInt()
        private const val GLOW_RADIUS_DP = 14f
        private const val GLOW_INSET_DP = 2f
        private const val GLOW_ALPHA = 0xAA
        private const val BEVEL_DEPTH_DP = 5f
        private const val HIGHLIGHT_COLOR = 0x66FFFFFF
        private const val TRANSPARENT = 0x00FFFFFF

        private fun withAlpha(color: Int, alpha: Int): Int =
            (color and 0x00FFFFFF) or (alpha shl 24)
    }
}
