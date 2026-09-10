package com.lucapiciollo.giocodel15.core.ui

import android.content.Context
import android.view.View
import androidx.core.view.updatePadding

/** Pads this view's top by the system status bar height, on top of whatever top padding it
 * already declares in XML. The app draws edge-to-edge (enforced by the platform for apps
 * targeting recent SDKs), so without this, titles would start underneath the status bar
 * clock/icons. Uses the classic `status_bar_height` framework resource directly instead of
 * WindowInsets dispatch, which proved unreliable to time correctly across devices/OEM skins. */
fun View.applyStatusBarTopInset() {
    val initialPaddingTop = paddingTop
    updatePadding(top = initialPaddingTop + statusBarHeightPx(context))
}

/** Pads this view's bottom by the system navigation bar height, on top of whatever bottom
 * padding it already declares in XML. Needed for the same edge-to-edge reason as
 * [applyStatusBarTopInset]: bottom-pinned buttons would otherwise end up partly underneath the
 * gesture/3-button navigation bar. */
fun View.applyNavigationBarBottomInset() {
    val initialPaddingBottom = paddingBottom
    updatePadding(bottom = initialPaddingBottom + navigationBarHeightPx(context))
}

private fun statusBarHeightPx(context: Context): Int {
    val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
    return if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
}

private fun navigationBarHeightPx(context: Context): Int {
    val resId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
    return if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
}
