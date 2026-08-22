package com.aqua.aqualight.ui.tabs.devices.route

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceMenuAccessResult
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceMenuPresentationStateHolderTest {

    private val holder = DeviceMenuPresentationStateHolder(DeviceRouteResolver())

    @Test
    fun `prepared result remains ready until matching UI acknowledgement`() {
        val request = requireNotNull(holder.begin("device-1"))

        holder.complete(
            request = request,
            result = DeviceMenuAccessResult.Available(
                deviceUid = "device-1",
                title = "Dose Pro 4",
                family = OwnerDeviceFamily.DOSING,
                presentationPrepared = true
            )
        )

        val ready = holder.state.value as DeviceMenuPresentationState.Ready
        assertEquals(request.requestId, ready.requestId)
        assertEquals("device-1", ready.route.deviceUid)
        assertEquals("Dose Pro 4", ready.route.title)
        assertEquals(DeviceRouteTarget.DOSING_ROOT, ready.route.target)
        assertTrue(ready.route.presentationPrepared)

        assertFalse(holder.acknowledge(request.requestId + 1L))
        assertEquals(ready, holder.state.value)
        assertTrue(holder.acknowledge(request.requestId))
        assertEquals(DeviceMenuPresentationState.Idle, holder.state.value)
    }

    @Test
    fun `unprepared result fails closed without producing a route`() {
        val request = requireNotNull(holder.begin("device-1"))

        holder.complete(
            request = request,
            result = DeviceMenuAccessResult.Available(
                deviceUid = "device-1",
                title = "Dose Pro 4",
                family = OwnerDeviceFamily.DOSING,
                presentationPrepared = false
            )
        )

        val failure = holder.state.value as DeviceMenuPresentationState.Failure
        assertEquals(request.requestId, failure.requestId)
        assertEquals("device-1", failure.deviceUid)
        assertEquals("Dose Pro 4", failure.title)
        assertEquals(R.string.device_menu_current_data_not_ready_message, failure.messageRes)
    }

    @Test
    fun `active request owns the state until its terminal result`() {
        val request = requireNotNull(holder.begin("device-1"))

        assertNull(holder.begin("device-2"))
        assertEquals(request, holder.state.value)

        holder.complete(
            request = DeviceMenuPresentationState.Preparing(
                requestId = request.requestId + 1L,
                deviceUid = "device-2"
            ),
            result = DeviceMenuAccessResult.Available(
                deviceUid = "device-2",
                title = "Other device",
                family = OwnerDeviceFamily.LIGHT,
                presentationPrepared = true
            )
        )

        assertEquals(request, holder.state.value)
    }
}
