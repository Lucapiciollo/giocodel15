package com.lucapiciollo.giocodel15.core.ui

import android.graphics.LinearGradient
import android.graphics.Shader
import android.widget.TextView
import androidx.core.view.doOnLayout

/** Applies a horizontal linear-gradient shader to this [TextView]'s paint, used for themed
 * titles in the 'Minimal Glow' visual direction (e.g. a green-to-blue "Gioco del 15").
 * The shader needs a known width, so it is applied once the view has been laid out. */
fun TextView.applyHorizontalGradient(startColor: Int, endColor: Int) {
    doOnLayout { view ->
        if (view.width <= 0) return@doOnLayout
        paint.shader = LinearGradient(
            0f, 0f, view.width.toFloat(), 0f,
            startColor, endColor,
            Shader.TileMode.CLAMP
        )
        invalidate()
    }
}
