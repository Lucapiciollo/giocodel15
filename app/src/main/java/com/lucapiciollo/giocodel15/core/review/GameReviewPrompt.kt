package com.lucapiciollo.giocodel15.core.review

import android.app.Activity
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Triggers Google Play's native in-app review popup (stars + comment, shown without leaving the
 * app) after a positive moment such as winning a round/table or solving a solo puzzle. No custom
 * backend involved: Play Core itself decides internally whether to actually display the dialog
 * (it's quota-limited by Google), so it's safe/expected to call this every time and let Google
 * throttle it rather than tracking our own "already asked" flag.
 */
object GameReviewPrompt {

    private const val TAG = "GiocoDel15"

    fun maybeRequestReview(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (!request.isSuccessful) {
                Log.i(TAG, "In-app review not available (offline or not installed via Play Store)")
                return@addOnCompleteListener
            }
            val reviewInfo = request.result
            manager.launchReviewFlow(activity, reviewInfo)
        }
    }
}
