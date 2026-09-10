package com.lucapiciollo.giocodel15.multiplayer.session

import android.os.Handler

/**
 * Ticks a countdown towards an absolute `startAt` instant (epoch millis), shared by
 * `LobbyActivity` (first round) and `ResultActivity` (new round) so the "wait for the exact
 * synchronized instant" logic isn't duplicated. The remaining time is recomputed from the wall
 * clock on every tick instead of relying on a fixed delay counted from when the start message
 * was received, so it stays correct even if a tick fires late.
 */
class RoundStartScheduler(private val handler: Handler) {

    private var pendingTick: Runnable? = null

    fun schedule(startAtMs: Long, onTick: (secondsLeft: Long) -> Unit, onStart: () -> Unit) {
        cancel()
        val runnable = object : Runnable {
            override fun run() {
                val remaining = startAtMs - System.currentTimeMillis()
                if (remaining <= 0) {
                    pendingTick = null
                    onStart()
                    return
                }
                onTick((remaining / 1000L) + 1)
                handler.postDelayed(this, TICK_INTERVAL_MS)
            }
        }
        pendingTick = runnable
        handler.post(runnable)
    }

    fun cancel() {
        pendingTick?.let(handler::removeCallbacks)
        pendingTick = null
    }

    companion object {
        private const val TICK_INTERVAL_MS = 150L
    }
}
