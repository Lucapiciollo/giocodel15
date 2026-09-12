package com.lucapiciollo.giocodel15.core.ui

import android.app.Activity
import android.content.Intent
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.lucapiciollo.giocodel15.feature.home.HomeActivity

/**
 * Returns to the Home screen, clearing every Activity currently on the task back stack (so a
 * subsequent system/gesture back press from Home exits the app instead of resurfacing a stale
 * setup/lobby/game screen underneath). Centralizes a snippet that used to be copy-pasted
 * identically across every Activity that can lead back to Home (GameActivity, LobbyActivity,
 * ResultActivity, SoloGameActivity).
 */
fun Activity.goHome() {
    startActivity(
        Intent(this, HomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )
    finish()
}

/**
 * Generic Sì/No confirmation dialog, reused by the hardware/gesture back-button handling across
 * Activities (see the various `onBackPressedDispatcher.addCallback` overrides) so each screen
 * doesn't need to hand-roll its own [AlertDialog] for "leave match?"/"exit app?" style prompts.
 * Mirrors the plain `AlertDialog.Builder` style already used by [com.lucapiciollo.giocodel15.multiplayer.nearby.HostDisconnectDialog].
 */
fun Activity.confirmAction(
    @StringRes title: Int,
    @StringRes message: Int,
    @StringRes confirmText: Int,
    @StringRes cancelText: Int,
    onConfirm: () -> Unit
) {
    if (isFinishing) return
    AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setCancelable(true)
        .setPositiveButton(confirmText) { dialog, _ -> dialog.dismiss(); onConfirm() }
        .setNegativeButton(cancelText, null)
        .show()
}
