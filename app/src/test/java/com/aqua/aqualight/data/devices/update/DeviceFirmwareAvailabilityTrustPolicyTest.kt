package com.aqua.aqualight.data.devices.update

import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceLimits
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFirmwareAvailabilityTrustPolicyTest {

    @Test
    fun validatedAuthenticatedSnapshotProducesFreshTrust() {
        val policy = policy()
        val snapshot = liveSnapshot()

        val record = policy.recordFor(snapshot)

        assertNotNull(record)
        assertTrue(policy.matches(durableCopy(snapshot), requireNotNull(record)))
    }

    @Test
    fun processDeathCopyRetainsOnlyFingerprintBasedTrust() {
        val policy = policy()
        val live = liveSnapshot()
        val record = requireNotNull(policy.recordFor(live))
        val durable = durableCopy(live)

        assertFalse(durable.hasValidatedRuntimeMetadata)
        assertTrue(policy.matches(durable, record))
    }

    @Test
    fun staleOrFutureRuntimeProofFailsClosed() {
        val policy = policy()
        val stale = liveSnapshot(proofAtMillis = NOW_MILLIS - MAX_AGE_MILLIS - 1L)
        val future = liveSnapshot(proofAtMillis = NOW_MILLIS + FUTURE_SKEW_MILLIS + 1L)

        assertNull(policy.recordFor(stale))
        assertNull(policy.recordFor(future))
    }

    @Test
    fun invalidatedRuntimeMetadataCannotRefreshTrust() {
        val policy = policy()
        val invalidated = liveSnapshot().copy(runtimeMetadataGeneration = 0L)

        assertNull(policy.recordFor(invalidated))
    }

    @Test
    fun firmwareOrCapabilityDriftInvalidatesPersistedFingerprint() {
        val policy = policy()
        val snapshot = liveSnapshot()
        val original = policy.fingerprint(snapshot)

        assertNotEquals(
            original,
            policy.fingerprint(snapshot.copy(firmwareVersion = "2.0.0"))
        )
        assertNotEquals(
            original,
            policy.fingerprint(
                snapshot.copy(capabilities = snapshot.capabilities.copy(ota = false))
            )
        )
    }

    private fun policy(): DeviceFirmwareAvailabilityTrustPolicy {
        return DeviceFirmwareAvailabilityTrustPolicy(
            nowMillis = { NOW_MILLIS },
            maxAgeMillis = MAX_AGE_MILLIS,
            maxFutureSkewMillis = FUTURE_SKEW_MILLIS
        )
    }

    private fun liveSnapshot(
        proofAtMillis: Long = NOW_MILLIS
    ): DeviceSnapshot {
        return DeviceSnapshot(
            identity = DeviceIdentity(uid = DEVICE_UID),
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
            firmwareBuild = "100",
            apiVersion = "1",
            protocolVersion = "1",
            capabilities = DeviceCapabilities(light = true, ota = true),
            limits = DeviceLimits(lightChannelCount = 4),
            modules = listOf("light", "ota"),
            runtimeMetadataGeneration = 7L,
            connectionState = DeviceConnectionState(
                onlineState = DeviceOnlineState.AUTHENTICATED,
                lastRuntimeMessageAtMillis = proofAtMillis
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
        val DEVICE_UID = DeviceUid("device-trust")
        const val NOW_MILLIS = 1_000_000L
        const val MAX_AGE_MILLIS = 15L * 60L * 1_000L
        const val FUTURE_SKEW_MILLIS = 60L * 1_000L
    }
}
