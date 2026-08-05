package com.aqua.aqualight.data.devices.store

import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceSnapshotMergerNameResetTest {

    @Test
    fun `authenticated snapshot treats an empty custom name as an explicit reset`() {
        val merged = DeviceSnapshotMerger.merge(
            previous = snapshot(customName = "Bebeğimmm", generation = 7L),
            incoming = snapshot(customName = "", generation = 7L)
        )

        assertEquals("", merged.identity.customName)
        assertEquals(PRODUCT_NAME, merged.title)
    }

    @Test
    fun `stale discovery cannot restore an old custom name after authenticated reset`() {
        val merged = DeviceSnapshotMerger.merge(
            previous = snapshot(customName = "", generation = 7L),
            incoming = snapshot(customName = "Bebeğimmm", generation = 0L)
        )

        assertEquals("", merged.identity.customName)
        assertEquals(PRODUCT_NAME, merged.title)
    }

    @Test
    fun `untrusted blank value still preserves an existing custom name`() {
        val merged = DeviceSnapshotMerger.merge(
            previous = snapshot(customName = "Bebeğimmm", generation = 0L),
            incoming = snapshot(customName = "", generation = 0L)
        )

        assertEquals("Bebeğimmm", merged.identity.customName)
    }

    @Test
    fun `newer authenticated generation can publish a new custom name`() {
        val merged = DeviceSnapshotMerger.merge(
            previous = snapshot(customName = "", generation = 7L),
            incoming = snapshot(customName = "Living room", generation = 8L)
        )

        assertEquals("Living room", merged.identity.customName)
    }

    private fun snapshot(customName: String, generation: Long): DeviceSnapshot = DeviceSnapshot(
        identity = DeviceIdentity(
            uid = DEVICE_UID,
            displayName = PRODUCT_NAME,
            customName = customName
        ),
        product = DeviceProduct(displayName = PRODUCT_NAME),
        runtimeMetadataGeneration = generation
    )

    private companion object {
        val DEVICE_UID = DeviceUid("device-name-reset")
        const val PRODUCT_NAME = "WRGB Pro Elite 120"
    }
}
