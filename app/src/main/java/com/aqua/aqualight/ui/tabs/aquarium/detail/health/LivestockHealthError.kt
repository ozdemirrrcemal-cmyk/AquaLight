package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.util.Log
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.aqua.aqualight.base.BaseActivity
import kotlinx.coroutines.CancellationException

private const val HEALTH_LOG_TAG = "LivestockHealth"

internal fun Fragment.reportHealthError(error: Throwable, @StringRes message: Int) {
    if (error is CancellationException) throw error
    Log.e(HEALTH_LOG_TAG, "Health operation failed", error)
    (activity as? BaseActivity)?.showSnackBar(getString(message), BaseActivity.SnackType.ERROR)
}
