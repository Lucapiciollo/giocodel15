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
import com.lucapiciollo.giocodel15.game.engine.PuzzleEngine
import com.lucapiciollo.giocodel15.game.model.PuzzleState
import kotlin.math.min

class PuzzleBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.game_bg_secondary)
        style = Paint.Style.FILL
    }
    private val boardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.game_outline_strong)
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tileStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.game_tile_stroke)
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val tileShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.game_tile_shadow)
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.game_empty_tile)
        style = Paint.Style.FILL
    }
    private val emptyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.game_cyan)
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
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

    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
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
        val outerRadius = 22f * density
        val boardRect = RectF(0f, 0f, boardSize, boardSize)
        canvas.drawRoundRect(boardRect, outerRadius, outerRadius, boardPaint)
        canvas.drawRoundRect(boardRect, outerRadius, outerRadius, boardStrokePaint)

        val padding = 12f * density
        val contentSize = boardSize - (padding * 2f)
        val gap = config.tileGapDp.coerceAtLeast(6f) * density
        val totalGap = gap * (state.size - 1)
        val tileSize = (contentSize - totalGap) / state.size
        val cornerRadius = config.cornerRadiusDp.coerceAtLeast(14f) * density
        val shadowOffset = 5f * density

        state.tiles.forEachIndexed { index, value ->
            val row = index / state.size
            val col = index % state.size
            val left = padding + col * (tileSize + gap)
            val top = padding + row * (tileSize + gap)
            val rect = RectF(left, top, left + tileSize, top + tileSize)

            if (value == PuzzleState.EMPTY_TILE) {
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, emptyPaint)
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, emptyStrokePaint)
                return@forEachIndexed
            }

            val shadowRect = RectF(rect.left, rect.top + shadowOffset, rect.right, rect.bottom + shadowOffset)
            canvas.drawRoundRect(shadowRect, cornerRadius, cornerRadius, tileShadowPaint)

            tilePaint.shader = LinearGradient(
                rect.left,
                rect.top,
                rect.left,
                rect.bottom,
                ContextCompat.getColor(context, R.color.game_tile_top),
                ContextCompat.getColor(context, R.color.game_tile_bottom),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, tilePaint)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, tileStrokePaint)
            tilePaint.shader = null

            if (config.showNumbers) {
                textPaint.textSize = tileSize * textSizeRatio(state.size)
                val baseline = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
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
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            onStateChanged?.invoke(state)
            invalidate()
            if (state.isSolved) onSolved?.invoke(state)
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
        val padding = 12f * density
        if (x !in padding..(boardSize - padding) || y !in padding..(boardSize - padding)) return null

        val contentSize = boardSize - (padding * 2f)
        val gap = config.tileGapDp.coerceAtLeast(6f) * density
        val tileSize = (contentSize - gap * (state.size - 1)) / state.size
        val step = tileSize + gap
        val localBoardX = x - padding
        val localBoardY = y - padding
        val col = (localBoardX / step).toInt().coerceIn(0, state.size - 1)
        val row = (localBoardY / step).toInt().coerceIn(0, state.size - 1)
        val localX = localBoardX - col * step
        val localY = localBoardY - row * step
        if (localX > tileSize || localY > tileSize) return null
        return row * state.size + col
    }

    private fun textSizeRatio(size: Int): Float = when (size) {
        3 -> 0.40f
        4 -> 0.36f
        5 -> 0.31f
        else -> 0.27f
    }

    companion object {
        private const val DEFAULT_GRID_SIZE = 4
    }
}
