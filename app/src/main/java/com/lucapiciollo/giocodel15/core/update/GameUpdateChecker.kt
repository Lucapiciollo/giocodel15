package com.lucapiciollo.giocodel15.core.update

import android.app.Activity
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.lucapiciollo.giocodel15.R

/**
 * Checks the Play Store for a newer app version and prompts the user to update. Relies entirely
 * on Play Store infrastructure: no custom backend, no network call of our own. Silently does
 * nothing if the app wasn't installed from Play Store (e.g. sideloaded debug builds) or the
 * device is offline.
 *
 * Tries an IMMEDIATE (full-screen, blocking) update first; falls back to a FLEXIBLE (background
 * download + "restart to apply" prompt) update if IMMEDIATE isn't allowed for the current
 * rollout, so users still get prompted instead of nothing happening at all.
 */
object GameUpdateChecker {

    private const val TAG = "GiocoDel15"
    const val REQ_UPDATE = 950

    private var appUpdateManager: AppUpdateManager? = null
    private var installListener: InstallStateUpdatedListener? = null

    /** Call from onCreate(): checks for an update and starts an update flow if one is available. */
    fun checkForUpdate(activity: Activity) {
        val manager = manager(activity)
        manager.appUpdateInfo
            .addOnSuccessListener { info -> startIfAvailable(manager, info, activity) }
            .addOnFailureListener { e ->
                Log.i(TAG, "Update check skipped (offline or not installed via Play Store): ${e.message}")
            }
    }

    /** Call from onResume(): resumes an update that was interrupted, or prompts to restart if a
     * flexible update already finished downloading while the app was in background. */
    fun resumeUpdateIfInProgress(activity: Activity) {
        val manager = manager(activity)
        manager.appUpdateInfo.addOnSuccessListener { info ->
            when {
                info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS ->
                    startIfAvailable(manager, info, activity)
                info.installStatus() == InstallStatus.DOWNLOADED ->
                    promptCompleteUpdate(manager, activity)
            }
        }
    }

    /** Call from onDestroy(): drops the install-state listener so it doesn't leak the Activity. */
    fun unregister() {
        val manager = appUpdateManager
        val listener = installListener
        if (manager != null && listener != null) {
            manager.unregisterListener(listener)
        }
        installListener = null
    }

    private fun manager(activity: Activity): AppUpdateManager =
        appUpdateManager ?: AppUpdateManagerFactory.create(activity.applicationContext).also {
            appUpdateManager = it
        }

    private fun startIfAvailable(manager: AppUpdateManager, info: AppUpdateInfo, activity: Activity) {
        val updateAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
            info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
        if (!updateAvailable) return

        when {
            info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> runCatching {
                manager.startUpdateFlowForResult(info, AppUpdateType.IMMEDIATE, activity, REQ_UPDATE)
            }.onFailure { e -> Log.w(TAG, "Failed to start immediate update flow", e) }

            info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> startFlexibleUpdate(manager, info, activity)

            else -> Log.i(TAG, "Update available but neither immediate nor flexible flow is allowed right now")
        }
    }

    private fun startFlexibleUpdate(manager: AppUpdateManager, info: AppUpdateInfo, activity: Activity) {
        installListener?.let { manager.unregisterListener(it) }
        val listener = InstallStateUpdatedListener { state ->
            if (state.installStatus() == InstallStatus.DOWNLOADED) promptCompleteUpdate(manager, activity)
        }
        installListener = listener
        manager.registerListener(listener)
        runCatching {
            manager.startUpdateFlowForResult(info, AppUpdateType.FLEXIBLE, activity, REQ_UPDATE)
        }.onFailure { e -> Log.w(TAG, "Failed to start flexible update flow", e) }
    }

    private fun promptCompleteUpdate(manager: AppUpdateManager, activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setTitle(R.string.update_ready_title)
            .setMessage(R.string.update_ready_message)
            .setPositiveButton(R.string.update_ready_restart) { _, _ -> manager.completeUpdate() }
            .setNegativeButton(R.string.update_ready_later, null)
            .setCancelable(true)
            .show()
    }
}
