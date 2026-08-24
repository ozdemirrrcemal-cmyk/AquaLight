package com.aqua.aqualight.ui.tabs.aquarium.detail.devices

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DosingSpotlightRotationControllerTest {

    @Test
    fun `visibility owns rotation lifetime and every resume restarts from first channel`() = runTest {
        val controller = DosingSpotlightRotationController(
            scope = this,
            intervalMillis = ROTATION_INTERVAL_MILLIS
        )
        controller.updateChannelCounts(mapOf(DEVICE_UID to CHANNEL_COUNT))

        controller.setVisible(true)
        assertSelectedChannel(controller, expectedIndex = 0)

        advanceTimeBy(ROTATION_INTERVAL_MILLIS - 1L)
        runCurrent()
        assertSelectedChannel(controller, expectedIndex = 0)

        advanceTimeBy(1L)
        runCurrent()
        assertSelectedChannel(controller, expectedIndex = 1)

        controller.setVisible(false)
        assertSelectedChannel(controller, expectedIndex = 0)

        advanceTimeBy(ROTATION_INTERVAL_MILLIS * 2L)
        runCurrent()
        assertSelectedChannel(controller, expectedIndex = 0)

        controller.setVisible(true)
        assertSelectedChannel(controller, expectedIndex = 0)

        advanceTimeBy(ROTATION_INTERVAL_MILLIS)
        runCurrent()
        assertSelectedChannel(controller, expectedIndex = 1)

        controller.setVisible(false)
    }

    private fun assertSelectedChannel(
        controller: DosingSpotlightRotationController,
        expectedIndex: Int
    ) {
        assertEquals(expectedIndex, controller.indices.value[DEVICE_UID])
    }

    private companion object {
        const val DEVICE_UID = "dose-pro-4"
        const val CHANNEL_COUNT = 4
        const val ROTATION_INTERVAL_MILLIS = 10_000L
    }
}
