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

    val topLevelGraphIds: Set<Int> = setOf(
        R.id.nav_app,
        R.id.nav_aquarium,
        R.id.nav_maintenance,
        R.id.nav_devices,
        R.id.nav_settings
    )

    fun isTopLevelDestination(
        destinationId: Int
    ): Boolean {
        return destinationId in topLevelDestinationIds ||
            destinationId in topLevelGraphIds
    }

    fun isInsideAppGraph(
        destination: NavDestination?
    ): Boolean {
        val hierarchy =
            destination?.hierarchy?.toList() ?: return false

        return hierarchy.any { node ->
            node.id in topLevelGraphIds
        }
    }

    fun shouldShowBottomBar(
        destination: NavDestination?
    ): Boolean {
        val destinationId =
            destination?.id ?: return false

        if (isTopLevelDestination(destinationId)) {
            return true
        }

        if (!isInsideAppGraph(destination)) {
            return false
        }

        return false
    }
}
