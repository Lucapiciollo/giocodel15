package com.lucapiciollo.giocodel15.core.ui

import android.animation.ValueAnimator
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
import android.view.animation.AnimationUtils
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
    private val emptyCellFillColor = ContextCompat.getColor(context, R.color.game_empty_cell_bg)
    private val emptyCellStrokeColor = ContextCompat.getColor(context, R.color.game_cyan)

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

    /** Empty-cell fill: dark, recessed panel look. */
    private val emptyCellFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = emptyCellFillColor
    }

    /** Empty-cell stroke: cyan outline with a subtle glow, marking the free slot. */
    private val emptyCellStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = EMPTY_CELL_STROKE_WIDTH_DP * density
        color = emptyCellStrokeColor
        setShadowLayer(EMPTY_CELL_GLOW_RADIUS_DP * density, 0f, 0f, withAlpha(emptyCellStrokeColor, EMPTY_CELL_GLOW_ALPHA))
    }

    private var state: PuzzleState = PuzzleState.solved(DEFAULT_GRID_SIZE)
    private var config: PuzzleBoardConfig = PuzzleBoardConfig()
    private var onStateChanged: ((PuzzleState) -> Unit)? = null
    private var onSolved: ((PuzzleState) -> Unit)? = null
    private var onTileMoved: ((Int) -> Unit)? = null

    /** Slide animation for the tile that just moved: drawn separately, interpolated between
     * its old and new grid position, while the rest of the board renders from the already-
     * updated [state] (so the vacated cell correctly shows as empty underneath the sliding
     * tile). Purely visual — move validation, listeners and haptics still fire synchronously
     * with the underlying state change, only the on-screen slide is deferred/animated. */
    private var animatingTileValue: Int? = null
    private var animatingFromIndex: Int = -1
    private var animatingToIndex: Int = -1
    private var animatingProgress: Float = 1f
    private val slideInterpolator = AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_slow_in)
    private var slideAnimator: ValueAnimator? = null

    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(R.string.puzzle_board_content_description)
        // Canvas shadow layers on non-text shapes are only honored on a software-rendered
        // layer; the board is small and redrawn only on discrete moves, so the cost is negligible.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setPuzzleState(newState: PuzzleState) {
        slideAnimator?.cancel()
        animatingTileValue = null
        state = newState
        invalidate()
    }

    fun getPuzzleState(): PuzzleState = state

    override fun onDetachedFromWindow() {
        slideAnimator?.cancel()
        slideAnimator = null
        super.onDetachedFromWindow()
    }

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

        val movableIndices = if (config.interactionEnabled) {
            PuzzleEngine.movableTileIndices(state)
        } else {
            emptyList()
        }

        val skipIndex = if (animatingTileValue != null) animatingToIndex else -1

        state.tiles.forEachIndexed { index, value ->
            if (index == skipIndex) return@forEachIndexed

            val rect = rectForIndex(index, tileSize, gap)

            if (value == PuzzleState.EMPTY_TILE) {
                drawEmptyCell(canvas, rect, cornerRadius)
                return@forEachIndexed
            }

            if (index in movableIndices) {
                val glowRect = RectF(
                    rect.left + glowInset,
                    rect.top + glowInset,
                    rect.right - glowInset,
                    rect.bottom - glowInset
                )
                canvas.drawRoundRect(glowRect, cornerRadius, cornerRadius, glowPaint)
            }

            drawTile(canvas, rect, value, cornerRadius, tileSize)
        }

        // Draw the moving tile last, interpolated between its old and new cell, so it slides
        // smoothly on top of the (already updated) static grid underneath.
        val movingValue = animatingTileValue
        if (movingValue != null) {
            val fromRect = rectForIndex(animatingFromIndex, tileSize, gap)
            val toRect = rectForIndex(animatingToIndex, tileSize, gap)
            val t = slideInterpolator?.getInterpolation(animatingProgress) ?: animatingProgress
            val rect = RectF(
                lerp(fromRect.left, toRect.left, t),
                lerp(fromRect.top, toRect.top, t),
                lerp(fromRect.right, toRect.right, t),
                lerp(fromRect.bottom, toRect.bottom, t)
            )
            drawTile(canvas, rect, movingValue, cornerRadius, tileSize)
        }
    }

    private fun rectForIndex(index: Int, tileSize: Float, gap: Float): RectF {
        val row = index / state.size
        val col = index % state.size
        val left = col * (tileSize + gap)
        val top = row * (tileSize + gap)
        return RectF(left, top, left + tileSize, top + tileSize)
    }

    private fun drawEmptyCell(canvas: Canvas, rect: RectF, cornerRadius: Float) {
        val strokeInset = (EMPTY_CELL_STROKE_WIDTH_DP * density) / 2f
        val emptyRect = RectF(
            rect.left + strokeInset,
            rect.top + strokeInset,
            rect.right - strokeInset,
            rect.bottom - strokeInset
        )
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, emptyCellFillPaint)
        canvas.drawRoundRect(emptyRect, cornerRadius, cornerRadius, emptyCellStrokePaint)
    }

    private fun drawTile(canvas: Canvas, rect: RectF, value: Int, cornerRadius: Float, tileSize: Float) {
        val bevelDepth = BEVEL_DEPTH_DP * density

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

    private fun lerp(start: Float, end: Float, t: Float): Float = start + (end - start) * t

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!config.interactionEnabled || !isEnabled) return false
        if (event.action != MotionEvent.ACTION_UP) return true

        val index = tileIndexAt(event.x, event.y) ?: return true
        val movingValue = state.tiles[index]
        val moved = PuzzleEngine.move(state, index)

        if (moved !== state) {
            val emptyIndexBeforeMove = state.emptyIndex
            state = moved
            if (config.hapticFeedback) {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            GameAudioManager.playTileMove(context)
            startSlideAnimation(fromIndex = index, toIndex = emptyIndexBeforeMove, value = movingValue)
            onTileMoved?.invoke(index)
            onStateChanged?.invoke(state)

            if (state.isSolved) {
                onSolved?.invoke(state)
            }
        }

        performClick()
        return true
    }

    private fun startSlideAnimation(fromIndex: Int, toIndex: Int, value: Int) {
        slideAnimator?.cancel()
        animatingTileValue = value
        animatingFromIndex = fromIndex
        animatingToIndex = toIndex
        animatingProgress = 0f
        slideAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SLIDE_DURATION_MS
            addUpdateListener {
                animatingProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    animatingTileValue = null
                    slideAnimator = null
                    invalidate()
                }
            })
            start()
        }
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
        private const val EMPTY_CELL_STROKE_WIDTH_DP = 2f
        private const val EMPTY_CELL_GLOW_RADIUS_DP = 8f
        private const val EMPTY_CELL_GLOW_ALPHA = 0x80
        private const val SLIDE_DURATION_MS = 110L

        private fun withAlpha(color: Int, alpha: Int): Int =
            (color and 0x00FFFFFF) or (alpha shl 24)
    }
}
