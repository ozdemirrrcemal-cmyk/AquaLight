package com.aqua.aqualight.ui.navigation

import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.main.MainActivity

object RootNavigator {

    fun openAppGraph(
        fragment: Fragment
    ) {
        rootNavController(fragment).navigate(
            R.id.nav_aquarium,
            null,
            navOptions {
                popUpTo(R.id.authContainerFragment) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        )
    }

    fun openAuthGraph(
        fragment: Fragment,
        clearSessionNavigationState: Boolean = true
    ) {
        if (clearSessionNavigationState) {
            (fragment.activity as? MainActivity)
                ?.clearSessionNavigationState()
        }

        rootNavController(fragment).navigate(
            R.id.authContainerFragment,
            null,
            navOptions {
                popUpTo(R.id.nav_app) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        )
    }

    private fun rootNavController(
        fragment: Fragment
    ): NavController {
        val navHost = fragment.requireActivity()
            .supportFragmentManager
            .findFragmentById(R.id.nav_host) as NavHostFragment

        return navHost.navController
    }
}
