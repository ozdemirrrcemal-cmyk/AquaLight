package com.aqua.aqualight.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.fragment.app.Fragment
import com.aqua.aqualight.ui.main.MainActivity

object RootNavigator {

    fun openAppGraph(
        fragment: Fragment
    ) {
        restartForCurrentSession(fragment)
    }

    fun openAuthGraph(
        fragment: Fragment,
        clearSessionNavigationState: Boolean = true
    ) {
        if (clearSessionNavigationState) {
            (fragment.activity as? MainActivity)
                ?.clearSessionNavigationState()
        }

        restartForCurrentSession(fragment)
    }

    /**
     * Owner-scoped repositories are captured by screen ViewModels. Reusing the root navigation
     * back stack across sign-in/sign-out can resurrect a ViewModel owned by the previous user.
     * A fresh task destroys all stale ViewModels before MainActivity resolves the current owner.
     */
    private fun restartForCurrentSession(
        fragment: Fragment
    ) {
        val activity = fragment.requireActivity()
        val intent = OwnerSessionRestartIntentFactory.create(activity)

        activity.startActivity(intent)
        activity.finish()
        activity.overridePendingTransition(0, 0)
    }
}

internal object OwnerSessionRestartIntentFactory {

    fun create(context: Context): Intent {
        return Intent(context, MainActivity::class.java).apply {
            addFlags(RESTART_FLAGS)
        }
    }

    val RESTART_FLAGS: Int =
        Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP
}
