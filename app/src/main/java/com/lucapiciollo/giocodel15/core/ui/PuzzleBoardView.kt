package com.lucapiciollo.giocodel15.core.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.lucapiciollo.giocodel15.R
import com.lucapiciollo.giocodel15.game.engine.PuzzleEngine
import com.lucapiciollo.giocodel15.game.model.PuzzleState
import kotlin.math.min

class PuzzleBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private var state: PuzzleState = PuzzleState.solved(DEFAULT_GRID_SIZE)
    private var config: PuzzleBoardConfig = PuzzleBoardConfig()
    private var onStateChanged: ((PuzzleState) -> Unit)? = null
    private var onSolved: ((PuzzleState) -> Unit)? = null

    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        tilePaint.color = ContextCompat.getColor(context, R.color.game_tile)
        textPaint.color = ContextCompat.getColor(context, R.color.game_tile_text)
        contentDescription = context.getString(R.string.puzzle_board_content_description)
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

        state.tiles.forEachIndexed { index, value ->
            if (value == PuzzleState.EMPTY_TILE) return@forEachIndexed

            val row = index / state.size
            val col = index % state.size
            val left = col * (tileSize + gap)
            val top = row * (tileSize + gap)
            val rect = RectF(left, top, left + tileSize, top + tileSize)

            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, tilePaint)

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
    }
}
