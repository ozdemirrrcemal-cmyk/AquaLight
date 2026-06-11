package com.aqua.aqualight.ui.navigation

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import com.aqua.aqualight.R

object AppDestinationContract {

    val topLevelDestinationIds: Set<Int> = setOf(
        R.id.aquariumFragment,
        R.id.aquariumMaintenanceFragment,
        R.id.devicesFragment,
        R.id.settingsFragment
    )

    val topLevelGraphIds: Set<Int> = emptySet()

    fun isTopLevelDestination(
        destinationId: Int
    ): Boolean {
        return destinationId in topLevelDestinationIds
    }

    fun isInsideAppGraph(
        destination: NavDestination?
    ): Boolean {
        return destination
            ?.hierarchy
            ?.any { node -> node.id == R.id.nav_app }
            ?: false
    }
}
