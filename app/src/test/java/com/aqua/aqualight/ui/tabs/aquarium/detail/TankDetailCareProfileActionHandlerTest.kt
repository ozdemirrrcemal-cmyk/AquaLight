package com.aqua.aqualight.ui.tabs.aquarium.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TankDetailCareProfileActionHandlerTest {

    @Test
    fun plantsActionResolvesToPlantsTarget() {
        assertEquals(
            TankDetailCareProfileTarget.PLANTS,
            TankDetailCareProfileActionHandler.resolve(
                TankDetailFragment.CARE_PROFILE_ACTION_PLANTS
            )
        )
    }

    @Test
    fun livestockActionResolvesToLivestockTarget() {
        assertEquals(
            TankDetailCareProfileTarget.LIVESTOCK,
            TankDetailCareProfileActionHandler.resolve(
                TankDetailFragment.CARE_PROFILE_ACTION_LIVESTOCK
            )
        )
    }

    @Test
    fun unknownActionDoesNotProduceANavigationTarget() {
        assertNull(
            TankDetailCareProfileActionHandler.resolve("unknown")
        )
    }
}
