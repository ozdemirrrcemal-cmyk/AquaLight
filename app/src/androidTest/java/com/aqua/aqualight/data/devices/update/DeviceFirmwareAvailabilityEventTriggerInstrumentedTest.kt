package com.aqua.aqualight.data.devices.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceFirmwareAvailabilityEventTriggerInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun validatedSnapshotReconcilesUntilRuntimeBecomesUnavailable() = runBlocking {
        val trust = FakeTrust()
        val reconciledOwners = mutableListOf<String>()
        val trigger = trigger(trust, reconciledOwners)
        val snapshot = liveSnapshot()

        trigger.acceptSnapshot(snapshot)
        trigger.acceptSnapshot(snapshot)
        trigger.acceptUnavailable(snapshot.deviceUid)
        trigger.acceptSnapshot(snapshot)

        assertEquals(
            listOf(OWNER_UID, OWNER_UID, OWNER_UID),
            reconciledOwners
        )
        assertEquals(listOf(DEVICE_UID.value), trust.clearedDeviceUids)
        trigger.close()
    }

    @Test
    fun unvalidatedSnapshotPreservesProcessTrustAndDoesNotReconcile() = runBlocking {
        val trust = FakeTrust()
        val reconciledOwners = mutableListOf<String>()
        val trigger = trigger(trust, reconciledOwners)

        trigger.acceptSnapshot(
            liveSnapshot().copy(runtimeMetadataGeneration = 0L)
        )

        assertEquals(emptyList<String>(), reconciledOwners)
        assertEquals(emptyList<String>(), trust.clearedDeviceUids)
        trigger.close()
    }

    private fun trigger(
        trust: FakeTrust,
        reconciledOwners: MutableList<String>
    ): DeviceFirmwareAvailabilityEventTrigger {
        return DeviceFirmwareAvailabilityEventTrigger(
            context = context,
            ownerUid = OWNER_UID,
            lifecycleEvents = null,
            snapshots = null,
            dependencies = DeviceFirmwareAvailabilityEventTriggerDependencies(
                trust = trust,
                policy = DeviceFirmwareAvailabilityTrustPolicy(
                    nowMillis = { NOW_MILLIS }
                ),
                dispatcher = Dispatchers.Unconfined,
                reconcileOwner = { ownerUid -> reconciledOwners += ownerUid }
            )
        )
    }

    private fun liveSnapshot(): DeviceSnapshot {
        return DeviceSnapshot(
            identity = DeviceIdentity(uid = DEVICE_UID),
            product = DeviceProduct(productKey = "LIGHT_AQUA_LIGHT"),
            firmwareVersion = "1.0.0",
            capabilities = DeviceCapabilities(ota = true),
            runtimeMetadataGeneration = 4L,
            connectionState = DeviceConnectionState(
                onlineState = DeviceOnlineState.AUTHENTICATED,
                lastRuntimeMessageAtMillis = NOW_MILLIS
            )
        )
    }

    private class FakeTrust : DeviceFirmwareAvailabilityTrust {
        val clearedDeviceUids = mutableListOf<String>()

        override suspend fun recordValidated(
            ownerUid: String,
            snapshot: DeviceSnapshot
        ): Boolean {
            return snapshot.hasValidatedRuntimeMetadata &&
                snapshot.connectionState.onlineState == DeviceOnlineState.AUTHENTICATED
        }

        override suspend fun isFresh(
            ownerUid: String,
            snapshot: DeviceSnapshot
        ): Boolean = false

        override suspend fun trackedDeviceUids(ownerUid: String): Set<String> = emptySet()

        override suspend fun clearDevice(ownerUid: String, deviceUid: String) {
            clearedDeviceUids += deviceUid
        }

        override suspend fun clearOwner(ownerUid: String) = Unit
    }

    private companion object {
        const val OWNER_UID = "owner-event"
        val DEVICE_UID = DeviceUid("device-event")
        const val NOW_MILLIS = 4_000_000L
    }
}
