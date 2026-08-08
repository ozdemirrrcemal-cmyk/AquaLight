package com.aqua.aqualight.data.devices.runtime.modules.firmware

import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommand
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandGateway
import com.aqua.aqualight.data.devices.runtime.core.DeviceRuntimeCommandOutcome
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFirmwareUpdateRepositoryAvailabilityTest {

    @Test
    fun `unpublished latest manifest resolves to installed version as no update`() = runTest {
        val repository = repositoryWith(
            DeviceFirmwareManifestNotPublishedException(statusCode = 404)
        )

        val availability = repository.fetchAndEvaluateUpdate(
            snapshot = dosePro4Snapshot(),
            manifestUrl = MANIFEST_URL
        ).getOrThrow() as DeviceFirmwareAvailability.UpToDate

        assertEquals("1.0.0", availability.currentVersion)
        assertEquals("1.0.0", availability.latestVersion)
        assertTrue(!availability.releaseContent.isPresent)
    }

    @Test
    fun `real transport failure remains a failed availability check`() = runTest {
        val repository = repositoryWith(IOException("offline"))

        val result = repository.fetchAndEvaluateUpdate(
            snapshot = dosePro4Snapshot(),
            manifestUrl = MANIFEST_URL
        )

        assertTrue(result.isFailure)
        assertEquals("offline", result.exceptionOrNull()?.message)
    }

    private fun repositoryWith(failure: Throwable): DeviceFirmwareUpdateRepository {
        val runtime = DeviceFirmwareRuntimeRepository(
            object : DeviceRuntimeCommandGateway {
                override suspend fun <T> execute(
                    deviceUid: DeviceUid,
                    command: DeviceRuntimeCommand<T>,
                    timeoutMillis: Long
                ): DeviceRuntimeCommandOutcome<T> {
                    kotlin.error("Runtime must not be used during availability evaluation.")
                }
            }
        )
        val source = object : DeviceFirmwareManifestHttpSource() {
            override suspend fun load(url: String): Result<DeviceFirmwareManifest> =
                Result.failure(failure)
        }
        return DeviceFirmwareUpdateRepository(
            runtime = runtime,
            manifestSource = source
        )
    }

    private fun dosePro4Snapshot() = DeviceSnapshot(
        identity = DeviceIdentity(uid = DeviceUid(DEVICE_UID)),
        product = DeviceProduct(
            brand = "AquaLight",
            productId = "com.aqualight.dosing.dose_pro_4",
            productKey = "DOSING_DOSE_PRO_4",
            family = DeviceFamily.DOSING,
            familyRaw = "dosing",
            line = "dose_pro",
            model = "dose_pro_4",
            displayName = "Dose Pro 4",
            skuCode = "AQL-D-DP4-GLB-BLK",
            hardwareRevision = "2.0"
        ),
        firmwareVersion = "1.0.0",
        capabilities = DeviceCapabilities(
            dosing = true,
            timeSync = true,
            ota = true
        ),
        runtimeMetadataGeneration = 1L
    )

    private companion object {
        const val DEVICE_UID = "AQL-DP4-NO-RELEASE"
        const val MANIFEST_URL =
            "https://github.com/ozdemirrrcemal-cmyk/AquaLight-OTA-Releases/" +
                "releases/latest/download/manifest-stable.json"
    }
}
