package com.aqua.aqualight.data.auth

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Converts Android's process lifecycle into the one foreground boundary used by device presence.
 *
 * ProcessLifecycleOwner delays its background signal across Activity recreation, so configuration
 * changes do not stop and immediately restart authenticated device verification.
 */
internal class AppProcessLifecycleObserver(
    private val controller: AppForegroundLifecycleController
) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) {
        controller.enterForeground()
    }

    override fun onStop(owner: LifecycleOwner) {
        controller.leaveForeground()
    }
}

internal interface AppForegroundLifecycleController {
    fun enterForeground()
    fun leaveForeground()
}
