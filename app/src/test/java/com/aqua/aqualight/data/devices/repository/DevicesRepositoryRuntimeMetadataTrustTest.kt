package com.aqua.aqualight.data.devices.repository

import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.store.DeviceRegistryStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicesRepositoryRuntimeMetadataTrustTest {

    @Test
    fun `discovery merge preserves current generation but registration withdraws it`() = runTest {
        val registry = DeviceRegistryStore()
        val validated = snapshot(generation = 7L)
        registry.upsert(validated)

        registry.updateExistingAll(
            listOf(
                snapshot(generation = 0L).copy(
                    connectionState = validated.connectionState.copy(
                        onlineState = DeviceOnlineState.ONLINE_LAN
                    )
                )
            )
        )

        assertEquals(7L, registry.currentDevice(DEVICE_UID)?.runtimeMetadataGeneration)
        assertTrue(registry.currentDevice(DEVICE_UID)?.hasValidatedRuntimeMetadata == true)

        val repository = DevicesRepository(registryStore = registry)
        val staged = repository.stageProvisioningSnapshot(snapshot(generation = 0L))

        assertEquals(0L, staged.runtimeMetadataGeneration)
        assertFalse(staged.hasValidatedRuntimeMetadata)
        assertEquals(0L, registry.currentDevice(DEVICE_UID)?.runtimeMetadataGeneration)
    }

    private fun snapshot(generation: Long): DeviceSnapshot = DeviceSnapshot(
        identity = DeviceIdentity(uid = DEVICE_UID, customName = "My Device"),
        product = DeviceProduct(
            brand = "AquaLight",
            productKey = "DOSING_DOSE_PRO_2",
            productId = "com.aqualight.dosing.dose_pro_2",
            displayName = "Dose Pro 2",
            model = "dose_pro_2",
            hardwareRevision = "2.0"
        ),
        runtimeMetadataGeneration = generation
    )

    private companion object {
        val DEVICE_UID = DeviceUid("AQL-DP2-TRUST")
    }
}
