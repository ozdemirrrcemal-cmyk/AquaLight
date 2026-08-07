package com.aqua.aqualight.data.devices.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceFirmwareAvailabilityTrustStoreInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val policy = DeviceFirmwareAvailabilityTrustPolicy(
        nowMillis = { NOW_MILLIS },
        maxAgeMillis = MAX_AGE_MILLIS,
        maxFutureSkewMillis = 0L
    )

    @Test
    fun recreatedStoreAcceptsMatchingDurableSnapshotAfterProcessDeath() = runBlocking {
        val ownerUid = "trust-owner-${UUID.randomUUID()}"
        val live = liveSnapshot()
        val initial = store()
        initial.clearOwner(ownerUid)

        assertTrue(initial.recordValidated(ownerUid, live))

        val recreated = store()
        assertTrue(recreated.isFresh(ownerUid, durableCopy(live)))
        recreated.clearOwner(ownerUid)
    }

    @Test
    fun deviceDeletionRemovesPersistedTrustWithoutAffectingOtherDevice() = runBlocking {
        val ownerUid = "trust-delete-owner-${UUID.randomUUID()}"
        val first = liveSnapshot("device-first")
        val second = liveSnapshot("device-second")
        val store = store()
        store.clearOwner(ownerUid)
        store.recordValidated(ownerUid, first)
        store.recordValidated(ownerUid, second)

        store.clearDevice(ownerUid, first.deviceUid.value)

        assertFalse(store.isFresh(ownerUid, durableCopy(first)))
        assertTrue(store.isFresh(ownerUid, durableCopy(second)))
        store.clearOwner(ownerUid)
    }

    private fun store(): DeviceFirmwareAvailabilityTrustStore {
        return DeviceFirmwareAvailabilityTrustStore.createForTests(context, policy)
    }

    private fun liveSnapshot(
        deviceUid: String = "device-process-death"
    ): DeviceSnapshot {
        return DeviceSnapshot(
            identity = DeviceIdentity(uid = DeviceUid(deviceUid)),
            product = DeviceProduct(
                brand = "AquaLight",
                productId = "com.aqualight.light.aqua_light",
                productKey = "LIGHT_AQUA_LIGHT",
                family = DeviceFamily.LIGHT,
                familyRaw = "light",
                line = "aqua",
                model = "aqua_light",
                displayName = "Aqua Light",
                skuCode = "AQL-L-AQL-GLB-BLK",
                hardwareRevision = "1.0"
            ),
            firmwareVersion = "1.0.0",
            capabilities = DeviceCapabilities(light = true, ota = true),
            runtimeMetadataGeneration = 3L,
            connectionState = DeviceConnectionState(
                onlineState = DeviceOnlineState.AUTHENTICATED,
                lastRuntimeMessageAtMillis = NOW_MILLIS
            )
        )
    }

    private fun durableCopy(snapshot: DeviceSnapshot): DeviceSnapshot {
        return snapshot.copy(
            runtimeMetadataGeneration = 0L,
            connectionState = DeviceConnectionState()
        )
    }

    private companion object {
        const val NOW_MILLIS = 2_000_000L
        const val MAX_AGE_MILLIS = 15L * 60L * 1_000L
    }
}
