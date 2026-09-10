package com.lucapiciollo.giocodel15.core.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.lucapiciollo.giocodel15.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class NearbyRadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = ContextCompat.getColor(context, R.color.game_cyan)
        alpha = 90
    }
    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = ContextCompat.getColor(context, R.color.game_green_light)
        strokeCap = Paint.Cap.ROUND
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.game_green_light)
    }
    private val endpointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.game_cyan_light)
    }
    private val endpointStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = ContextCompat.getColor(context, R.color.game_green_light)
    }

    private var sweepDegrees = 0f
    private var endpointCount = 0
    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 2600L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            sweepDegrees = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        contentDescription = context.getString(R.string.nearby_radar_description)
    }

    fun setEndpointCount(count: Int) {
        endpointCount = count.coerceIn(0, MAX_ENDPOINT_MARKERS)
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val side = min(width, height)
        setMeasuredDimension(resolveSize(side, widthMeasureSpec), resolveSize(side, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = side * 0.44f

        for (index in 1..4) {
            canvas.drawCircle(cx, cy, maxRadius * index / 4f, ringPaint)
        }
        canvas.drawLine(cx - maxRadius, cy, cx + maxRadius, cy, ringPaint)
        canvas.drawLine(cx, cy - maxRadius, cx, cy + maxRadius, ringPaint)

        val rad = Math.toRadians(sweepDegrees.toDouble())
        val ex = cx + cos(rad).toFloat() * maxRadius
        val ey = cy + sin(rad).toFloat() * maxRadius
        canvas.drawLine(cx, cy, ex, ey, sweepPaint)
        canvas.drawCircle(cx, cy, 7f * density, centerPaint)

        repeat(endpointCount) { index ->
            val angle = Math.toRadians((ENDPOINT_ANGLES[index]).toDouble())
            val radialFactor = ENDPOINT_RADII[index]
            val px = cx + cos(angle).toFloat() * maxRadius * radialFactor
            val py = cy + sin(angle).toFloat() * maxRadius * radialFactor
            canvas.drawCircle(px, py, 11f * density, endpointPaint)
            canvas.drawCircle(px, py, 15f * density, endpointStroke)
        }
    }

    companion object {
        private const val MAX_ENDPOINT_MARKERS = 6
        private val ENDPOINT_ANGLES = floatArrayOf(205f, 318f, 142f, 32f, 260f, 78f)
        private val ENDPOINT_RADII = floatArrayOf(0.72f, 0.64f, 0.58f, 0.78f, 0.50f, 0.84f)
    }
}
