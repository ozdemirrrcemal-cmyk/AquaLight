package com.aqua.aqualight.data.devices.runtime.modules.firmware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFirmwareManifestContractValidatorTest {

    @Test
    fun `firmware golden catalog satisfies device-bound invariants`() {
        val manifest = goldenManifest()

        assertEquals(
            manifest,
            DeviceFirmwareManifestContractValidator.validate(manifest)
        )
    }

    @Test
    fun `duplicate product environment fails closed`() {
        val manifest = goldenManifest()
        val artifact = manifest.artifacts.single()
        val duplicateEnvironment = manifest.copy(
            artifacts = listOf(artifact, artifact.copy())
        )

        val failure = runCatching {
            DeviceFirmwareManifestContractValidator.validate(duplicateEnvironment)
        }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("duplicate product environments"))
    }

    @Test
    fun `artifact without OTA capability fails closed`() {
        val manifest = goldenManifest()
        val artifact = manifest.artifacts.single()
        val otaDisabled = manifest.copy(
            artifacts = listOf(
                artifact.copy(
                    product = artifact.product.copy(
                        capabilities = artifact.product.capabilities.copy(ota = false)
                    )
                )
            )
        )

        val failure = runCatching {
            DeviceFirmwareManifestContractValidator.validate(otaDisabled)
        }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("does not authorize OTA"))
    }

    @Test
    fun `firmware URL beyond device limit fails closed`() {
        val manifest = goldenManifest()
        val artifact = manifest.artifacts.single()
        val oversizedUrl = artifact.firmware.url + "x".repeat(
            DeviceFirmwareRuntimeContract.Limit.MAX_URL_LENGTH
        )
        val invalid = manifest.copy(
            artifacts = listOf(
                artifact.copy(
                    firmware = artifact.firmware.copy(url = oversizedUrl)
                )
            )
        )

        val failure = runCatching {
            DeviceFirmwareManifestContractValidator.validate(invalid)
        }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("exceeds the device limit"))
    }

    private fun goldenManifest(): DeviceFirmwareManifest {
        val raw = checkNotNull(
            javaClass.getResource("/ota/firmware-channel-manifest-v1.json")
        ).readText()
        return DeviceFirmwareManifestParser.parse(raw).getOrThrow()
    }
}
