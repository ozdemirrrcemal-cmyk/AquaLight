package com.aqua.aqualight.ui.tabs.aquarium.navigation

import androidx.navigation.NavController
import androidx.navigation.NavDirections

object AquariumTabArgs {
    const val BASIC = "basic"
    const val DETAILS = "details"
    const val OTHERS = "others"
}

object TankDetailTabArgs {
    const val DEVICES = "devices"
    const val ACTIVITY = "activity"
    const val TANK = "tank"
    const val PLANTS = "plants"
    const val TANK_LIFE = "tank_life"
}

fun NavController.navigateSafelyFrom(
    sourceDestinationId: Int,
    directions: NavDirections
): Boolean {
    if (currentDestination?.id != sourceDestinationId) {
        return false
    }

    navigate(directions)
    return true
}
