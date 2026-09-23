package com.aqua.aqualight.ui.tabs.aquarium.detail

internal enum class TankDetailCareProfileTarget {
    PLANTS,
    LIVESTOCK
}

internal object TankDetailCareProfileActionHandler {

    fun resolve(action: String): TankDetailCareProfileTarget? {
        return when (action) {
            TankDetailFragment.CARE_PROFILE_ACTION_PLANTS ->
                TankDetailCareProfileTarget.PLANTS
            TankDetailFragment.CARE_PROFILE_ACTION_LIVESTOCK ->
                TankDetailCareProfileTarget.LIVESTOCK
            else -> null
        }
    }
}
