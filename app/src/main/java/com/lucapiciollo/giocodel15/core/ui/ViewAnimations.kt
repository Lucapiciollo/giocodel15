package com.lucapiciollo.giocodel15.core.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils

/**
 * Adds a subtle "press" scale-down feedback (matching the Premium Gamer spec's tactile card
 * interactions) to a clickable view, without interfering with its existing OnClickListener:
 * the touch listener always returns false so the click event still dispatches normally.
 */
fun View.applyPressScaleAnimation(pressedScale: Float = 0.96f, duration: Long = 100L) {
    setOnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.animate().cancel()
                view.animate().scaleX(pressedScale).scaleY(pressedScale).setDuration(duration).start()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.animate().cancel()
                view.animate().scaleX(1f).scaleY(1f).setDuration(duration).start()
            }
        }
        false
    }
}

/**
 * Plays a one-shot screen-entrance animation (fade in + slide up) on this view, intended to be
 * called on the root layout right after setContentView in an Activity's onCreate. Purely
 * cosmetic — does not delay or gate any other initialization logic.
 */
fun View.playEntranceAnimation(translationYDp: Float = 16f, duration: Long = 320L) {
    val distancePx = translationYDp * resources.displayMetrics.density
    alpha = 0f
    translationY = distancePx
    animate()
        .alpha(1f)
        .translationY(0f)
        .setDuration(duration)
        .setInterpolator(AnimationUtils.loadInterpolator(context, android.R.interpolator.decelerate_cubic))
        .setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // Reset in case the view is measured/laid out again later (e.g. config change
                // handled without recreation) so it doesn't get stuck invisible/offset.
                alpha = 1f
                translationY = 0f
            }
        })
        .start()
}
