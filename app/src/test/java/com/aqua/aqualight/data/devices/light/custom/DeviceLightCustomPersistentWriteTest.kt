package com.aqua.aqualight.data.devices.light.custom

import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomChannel
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomFailure
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomMutationResult
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomReadResult
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomSnapshot
import com.aqua.aqualight.application.devices.light.custom.DeviceLightCustomWriteResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceLightCustomPersistentWriteTest {

    @Test
    fun `durable write clears preview then mutates then reads authoritative state`() = runTest {
        val calls = mutableListOf<String>()
        val snapshot = snapshot()

        val result = executeDeviceLightCustomPersistentWrite(
            clearPreview = {
                calls += PREVIEW_CLEAR
                DeviceLightCustomMutationResult.Success
            },
            mutate = {
                calls += DURABLE_MUTATION
                DeviceLightCustomMutationResult.Success
            },
            readAuthoritative = {
                calls += AUTHORITATIVE_READ
                DeviceLightCustomReadResult.Available(snapshot)
            }
        )

        assertEquals(
            listOf(PREVIEW_CLEAR, DURABLE_MUTATION, AUTHORITATIVE_READ),
            calls
        )
        assertEquals(DeviceLightCustomWriteResult.Success(snapshot), result)
    }

    @Test
    fun `preview clear failure blocks durable mutation and readback`() = runTest {
        val calls = mutableListOf<String>()

        val result = executeDeviceLightCustomPersistentWrite(
            clearPreview = {
                calls += PREVIEW_CLEAR
                DeviceLightCustomMutationResult.Failed(DeviceLightCustomFailure.UNAVAILABLE)
            },
            mutate = {
                calls += DURABLE_MUTATION
                DeviceLightCustomMutationResult.Success
            },
            readAuthoritative = {
                calls += AUTHORITATIVE_READ
                DeviceLightCustomReadResult.Available(snapshot())
            }
        )

        assertEquals(listOf(PREVIEW_CLEAR), calls)
        assertEquals(
            DeviceLightCustomWriteResult.Failed(DeviceLightCustomFailure.UNAVAILABLE),
            result
        )
    }

    @Test
    fun `mutation failure blocks authoritative readback`() = runTest {
        val calls = mutableListOf<String>()

        val result = executeDeviceLightCustomPersistentWrite(
            clearPreview = {
                calls += PREVIEW_CLEAR
                DeviceLightCustomMutationResult.Success
            },
            mutate = {
                calls += DURABLE_MUTATION
                DeviceLightCustomMutationResult.Failed(DeviceLightCustomFailure.REJECTED)
            },
            readAuthoritative = {
                calls += AUTHORITATIVE_READ
                DeviceLightCustomReadResult.Available(snapshot())
            }
        )

        assertEquals(listOf(PREVIEW_CLEAR, DURABLE_MUTATION), calls)
        assertEquals(
            DeviceLightCustomWriteResult.Failed(DeviceLightCustomFailure.REJECTED),
            result
        )
    }

    private fun snapshot() = DeviceLightCustomSnapshot(
        deviceUid = DEVICE_UID,
        productKey = PRODUCT_KEY,
        revision = REVISION,
        installed = false,
        weekdaysMask = EVERY_DAY_MASK,
        maxPoints = MAX_POINTS,
        timeStepMs = MINUTE_MILLIS,
        currentTimeMs = null,
        channels = listOf(
            DeviceLightCustomChannel.RED,
            DeviceLightCustomChannel.GREEN,
            DeviceLightCustomChannel.BLUE
        ),
        points = emptyList(),
        firmwareWriteAuthoritative = true
    )

    private companion object {
        const val PREVIEW_CLEAR = "preview.clear"
        const val DURABLE_MUTATION = "custom.mutate"
        const val AUTHORITATIVE_READ = "status/custom.read"
        const val DEVICE_UID = "light-custom-test"
        const val PRODUCT_KEY = "LIGHT_RGB_PRO_SLIM"
        const val REVISION = 4L
        const val EVERY_DAY_MASK = 127
        const val MAX_POINTS = 96
        const val MINUTE_MILLIS = 60_000L
    }
}
