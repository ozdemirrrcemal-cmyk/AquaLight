package com.aqua.aqualight.data.devices.update

import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DeviceFirmwareAvailabilitySnapshotSourceTest {

    @Test
    fun activeRepositoryNotReadyNeverFallsBackToDurableState() = runTest {
        var durableLoads = 0
        val source = source(
            activeState = ActiveOwnerDeviceSnapshotState(
                ready = false,
                snapshots = listOf(snapshot("active-device"))
            ),
            durableLoader = {
                durableLoads += 1
                listOf(snapshot("durable-device"))
            }
        )

        val result = source.load(OWNER_UID)

        assertSame(DeviceFirmwareAvailabilitySnapshotResult.Retryable, result)
        assertEquals(0, durableLoads)
    }

    @Test
    fun activeRepositoryUsesOnlyLiveValidatedSnapshots() = runTest {
        val trust = FakeTrust(validatedDeviceUids = setOf("trusted"))
        val source = source(
            trust = trust,
            activeState = ActiveOwnerDeviceSnapshotState(
                ready = true,
                snapshots = listOf(snapshot("trusted"), snapshot("untrusted"))
            )
        )

        val result = source.load(OWNER_UID)
            as DeviceFirmwareAvailabilitySnapshotResult.Ready

        assertEquals(setOf("trusted", "untrusted"), result.currentDeviceUids)
        assertEquals(listOf("trusted"), result.eligibleSnapshots.map { it.deviceUid.value })
        assertEquals(listOf("untrusted"), trust.clearedDeviceUids)
    }

    @Test
    fun processDeathUsesOnlyMatchingFreshDurableTrust() = runTest {
        val trust = FakeTrust(freshDeviceUids = setOf("fresh"))
        val source = source(
            trust = trust,
            activeState = null,
            durableLoader = {
                listOf(snapshot("fresh"), snapshot("stale"))
            }
        )

        val result = source.load(OWNER_UID)
            as DeviceFirmwareAvailabilitySnapshotResult.Ready

        assertEquals(setOf("fresh", "stale"), result.currentDeviceUids)
        assertEquals(listOf("fresh"), result.eligibleSnapshots.map { it.deviceUid.value })
    }

    private fun source(
        trust: FakeTrust = FakeTrust(),
        activeState: ActiveOwnerDeviceSnapshotState? = null,
        durableLoader: suspend (String) -> List<DeviceSnapshot> = { emptyList() }
    ): DeviceFirmwareAvailabilitySnapshotSource {
        return DeviceFirmwareAvailabilitySnapshotSource(
            trust = trust,
            activeStateProvider = { activeState },
            durableSnapshotLoader = durableLoader
        )
    }

    private fun snapshot(deviceUid: String): DeviceSnapshot {
        return DeviceSnapshot(
            identity = DeviceIdentity(uid = DeviceUid(deviceUid)),
            product = DeviceProduct(),
            firmwareVersion = "1.0.0",
            capabilities = DeviceCapabilities(ota = true)
        )
    }

    private class FakeTrust(
        private val validatedDeviceUids: Set<String> = emptySet(),
        private val freshDeviceUids: Set<String> = emptySet()
    ) : DeviceFirmwareAvailabilityTrust {
        val clearedDeviceUids = mutableListOf<String>()

        override suspend fun recordValidated(
            ownerUid: String,
            snapshot: DeviceSnapshot
        ): Boolean = snapshot.deviceUid.value in validatedDeviceUids

        override suspend fun isFresh(
            ownerUid: String,
            snapshot: DeviceSnapshot
        ): Boolean = snapshot.deviceUid.value in freshDeviceUids

        override suspend fun trackedDeviceUids(ownerUid: String): Set<String> = emptySet()

        override suspend fun clearDevice(ownerUid: String, deviceUid: String) {
            clearedDeviceUids += deviceUid
        }

        override suspend fun clearOwner(ownerUid: String) = Unit
    }

    private companion object {
        const val OWNER_UID = "owner-a"
    }
}
