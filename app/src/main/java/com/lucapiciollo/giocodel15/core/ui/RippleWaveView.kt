package com.lucapiciollo.giocodel15.core.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.lucapiciollo.giocodel15.R
import kotlin.math.min

/** Concentric rings expanding outward and fading, like a stone dropped in water — used as a
 * "searching…" indicator (e.g. Nearby discovery). Purely decorative, no touch handling. Starts
 * animating automatically when attached to a window and stops when detached, so it costs
 * nothing while off-screen. */
class RippleWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH_DP * density
        color = ContextCompat.getColor(context, R.color.game_secondary)
    }

    private var animator: ValueAnimator? = null
    private var progress = 0f

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        start()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) start() else stop()
    }

    private fun start() {
        if (animator != null || visibility != VISIBLE) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = CYCLE_DURATION_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stop() {
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = min(width, height) / 2f - paint.strokeWidth

        for (ring in 0 until RING_COUNT) {
            val ringProgress = (progress + ring.toFloat() / RING_COUNT) % 1f
            paint.alpha = ((1f - ringProgress) * MAX_ALPHA).toInt()
            canvas.drawCircle(cx, cy, maxRadius * ringProgress, paint)
        }
    }

    companion object {
        private const val RING_COUNT = 3
        private const val CYCLE_DURATION_MS = 2200L
        private const val STROKE_WIDTH_DP = 2.5f
        private const val MAX_ALPHA = 200
    }
}
