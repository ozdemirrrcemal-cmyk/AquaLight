package com.aqua.aqualight.data.auth

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.aqua.aqualight.application.notifications.AppProcessForegroundState

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
        AppProcessForegroundState.update(true)
        controller.enterForeground()
    }

    override fun onStop(owner: LifecycleOwner) {
        controller.leaveForeground()
        AppProcessForegroundState.update(false)
    }
}

internal interface AppForegroundLifecycleController {
    fun enterForeground()
    fun leaveForeground()
}
