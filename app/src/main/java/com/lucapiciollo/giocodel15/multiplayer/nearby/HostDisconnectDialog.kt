package com.lucapiciollo.giocodel15.multiplayer.nearby

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.lucapiciollo.giocodel15.R
import com.lucapiciollo.giocodel15.multiplayer.session.TableSession

/**
 * Single place to react to a host disconnection (explicit `HOST_CLOSED` message, or simply
 * losing the only Nearby connection a client has, which is always the host in this app's star
 * topology). Reused by every non-host Activity instead of duplicating the dialog + cleanup.
 */
object HostDisconnectDialog {

    fun show(activity: Activity, nearby: NearbyConnectionManager, onReturnHome: () -> Unit) {
        if (activity.isFinishing) return
        AlertDialog.Builder(activity)
            .setTitle(R.string.host_closed_title)
            .setMessage(R.string.host_closed_message)
            .setCancelable(false)
            .setPositiveButton(R.string.host_closed_return_home) { dialog, _ ->
                dialog.dismiss()
                nearby.disconnectAll()
                TableSession.clear()
                onReturnHome()
            }
            .show()
    }
}
