package com.lucapiciollo.giocodel15.core.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.lucapiciollo.giocodel15.R
import kotlin.math.min

/**
 * Radar-style searching indicator for the Nearby tables screen: three concentric rings, a
 * center dot and a soft cyan sweep wedge that rotates continuously (2600ms per revolution)
 * while the view is visible, replacing the old plain ripple-wave animation.
 */
class NearbyRadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val cyan = ContextCompat.getColor(context, R.color.game_cyan)

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = RING_STROKE_WIDTH_DP * density
        color = withAlpha(cyan, RING_ALPHA)
    }

    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = cyan
    }

    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = null
    }

    private var sweepAngle = 0f
    private var rotationAnimator: ValueAnimator? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startSweep()
    }

    override fun onDetachedFromWindow() {
        stopSweep()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) startSweep() else stopSweep()
    }

    private fun startSweep() {
        if (rotationAnimator != null || visibility != VISIBLE || !isAttachedToWindow) return
        rotationAnimator = ObjectAnimator.ofFloat(this, "sweepAngle", 0f, 360f).apply {
            duration = SWEEP_DURATION_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            start()
        }
    }

    private fun stopSweep() {
        rotationAnimator?.cancel()
        rotationAnimator = null
    }

    @Suppress("unused") // driven by ObjectAnimator via reflection on the "sweepAngle" property
    fun setSweepAngle(angle: Float) {
        sweepAngle = angle
        invalidate()
    }

    fun getSweepAngle(): Float = sweepAngle

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = min(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = min(width, height) / 2f - ringPaint.strokeWidth

        // Sweep wedge, drawn first so the rings/dot sit on top of it.
        val sweepRect = android.graphics.RectF(cx - maxRadius, cy - maxRadius, cx + maxRadius, cy + maxRadius)
        sweepPaint.shader = android.graphics.SweepGradient(
            cx, cy,
            intArrayOf(withAlpha(cyan, 0), withAlpha(cyan, SWEEP_HEAD_ALPHA), withAlpha(cyan, 0)),
            floatArrayOf(0f, SWEEP_WIDTH_FRACTION, SWEEP_WIDTH_FRACTION * 2)
        )
        canvas.save()
        canvas.rotate(sweepAngle, cx, cy)
        canvas.drawArc(sweepRect, 0f, 360f, true, sweepPaint)
        canvas.restore()

        // Concentric rings (outer to inner).
        canvas.drawCircle(cx, cy, maxRadius, ringPaint)
        canvas.drawCircle(cx, cy, maxRadius * 0.66f, ringPaint)
        canvas.drawCircle(cx, cy, maxRadius * 0.33f, ringPaint)

        // Center dot.
        canvas.drawCircle(cx, cy, CENTER_DOT_RADIUS_DP * density, centerDotPaint)
    }

    companion object {
        private const val SWEEP_DURATION_MS = 2600L
        private const val RING_STROKE_WIDTH_DP = 1.3f
        private const val RING_ALPHA = 0x55
        private const val CENTER_DOT_RADIUS_DP = 5f
        private const val SWEEP_HEAD_ALPHA = 0x70
        private const val SWEEP_WIDTH_FRACTION = 0.12f

        private fun withAlpha(color: Int, alpha: Int): Int =
            (color and 0x00FFFFFF) or (alpha shl 24)
    }
}
