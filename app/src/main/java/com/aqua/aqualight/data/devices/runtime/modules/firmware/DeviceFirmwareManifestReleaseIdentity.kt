package com.aqua.aqualight.data.devices.runtime.modules.firmware

internal fun DeviceFirmwareManifest.hasExpectedReleaseTag(): Boolean {
    val environment = artifacts.singleOrNull()?.env ?: return false
    return tag == "$environment-v$version"
}

internal fun DeviceFirmwareManifest.expectedFirmwareFilename(
    artifact: DeviceFirmwareManifestArtifact
): String = "AquaLight-${artifact.env}-v$version-ota.bin"

internal fun DeviceFirmwareManifest.expectedFactoryFilename(
    artifact: DeviceFirmwareManifestArtifact
): String = "AquaLight-${artifact.env}-v$version-factory.zip"
