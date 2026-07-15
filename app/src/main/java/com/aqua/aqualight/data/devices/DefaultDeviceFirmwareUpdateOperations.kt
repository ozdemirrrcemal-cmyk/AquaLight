package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceFirmwareCommandResult
import com.aqua.aqualight.application.devices.DeviceFirmwareUpdateOperations
import com.aqua.aqualight.application.devices.PreparedDeviceFirmwareUpdate
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.repository.DevicesRepository
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareAsset
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareOtaStartPayload
import com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareUpdatePlan

internal class DefaultDeviceFirmwareUpdateOperations(
    private val devicesRepository: DevicesRepository
) : DeviceFirmwareUpdateOperations {

    override suspend fun prepareUpdate(
        deviceUid: String,
        manifestUrl: String,
        applyNow: Boolean
    ): Result<PreparedDeviceFirmwareUpdate> = runCatching {
        val uid = requireDeviceUid(deviceUid)
        val snapshot = devicesRepository.currentDevice(uid)
            ?: error("Device snapshot is not available yet.")
        val updater = devicesRepository.runtimeModules()?.firmwareUpdate
            ?: error("Firmware update runtime is not configured.")

        devicesRepository.connectRuntime(uid).getOrThrow()
        updater.fetchAndPlanUpdate(
            snapshot = snapshot,
            manifestUrl = manifestUrl,
            applyNow = applyNow
        ).getOrThrow().toApplicationPlan()
    }

    override fun startUpdate(
        plan: PreparedDeviceFirmwareUpdate
    ): DeviceFirmwareCommandResult {
        val updater = devicesRepository.runtimeModules()?.firmwareUpdate
            ?: return DeviceFirmwareCommandResult(
                sent = false,
                errorMessage = "Firmware update runtime is not configured."
            )
        return runCatching {
            devicesRepository.connectRuntime(requireDeviceUid(plan.deviceUid)).getOrThrow()
            updater.startUpdate(plan.toDataPlan()).toApplicationResult()
        }.getOrElse { error ->
            DeviceFirmwareCommandResult(
                sent = false,
                errorMessage = error.message ?: error::class.java.simpleName
            )
        }
    }

    override fun requestStatus(deviceUid: String): DeviceFirmwareCommandResult {
        val updater = devicesRepository.runtimeModules()?.firmwareUpdate
            ?: return DeviceFirmwareCommandResult(
                sent = false,
                errorMessage = "Firmware update runtime is not configured."
            )
        return runCatching {
            val uid = requireDeviceUid(deviceUid)
            devicesRepository.connectRuntime(uid).getOrThrow()
            updater.requestOtaStatus(uid).toApplicationResult()
        }.getOrElse { error ->
            DeviceFirmwareCommandResult(
                sent = false,
                errorMessage = error.message ?: error::class.java.simpleName
            )
        }
    }

    override fun clearStatus(deviceUid: String): DeviceFirmwareCommandResult {
        val updater = devicesRepository.runtimeModules()?.firmwareUpdate
            ?: return DeviceFirmwareCommandResult(
                sent = false,
                errorMessage = "Firmware update runtime is not configured."
            )
        return runCatching {
            val uid = requireDeviceUid(deviceUid)
            devicesRepository.connectRuntime(uid).getOrThrow()
            updater.clearOtaStatus(uid).toApplicationResult()
        }.getOrElse { error ->
            DeviceFirmwareCommandResult(
                sent = false,
                errorMessage = error.message ?: error::class.java.simpleName
            )
        }
    }

    private fun requireDeviceUid(value: String): DeviceUid {
        val normalized = value.trim()
        require(normalized.isNotBlank()) { "Device uid is missing." }
        return DeviceUid(normalized)
    }
}

private fun DeviceFirmwareUpdatePlan.toApplicationPlan(): PreparedDeviceFirmwareUpdate {
    return PreparedDeviceFirmwareUpdate(
        deviceUid = deviceUid.value,
        currentVersion = currentVersion,
        targetVersion = targetVersion,
        channel = channel,
        environment = env,
        productKey = productKey,
        productId = productId,
        model = model,
        hardwareRevision = hardwareRevision,
        displayName = displayName,
        filename = firmware.filename,
        downloadUrl = firmware.url,
        sha256 = firmware.sha256,
        sizeBytes = firmware.size,
        applyNow = payload.applyNow
    )
}

private fun PreparedDeviceFirmwareUpdate.toDataPlan(): DeviceFirmwareUpdatePlan {
    val uid = DeviceUid(deviceUid.trim())
    val firmware = DeviceFirmwareAsset(
        filename = filename,
        url = downloadUrl,
        sha256 = sha256,
        size = sizeBytes,
        otaSlotCompatible = true
    )
    val payload = DeviceFirmwareOtaStartPayload(
        url = downloadUrl,
        version = targetVersion,
        sha256 = sha256,
        expectedSize = sizeBytes,
        productKey = productKey,
        productId = productId,
        hardwareRevision = hardwareRevision,
        applyNow = applyNow,
        allowInsecureHttp = false
    )
    return DeviceFirmwareUpdatePlan(
        deviceUid = uid,
        currentVersion = currentVersion,
        targetVersion = targetVersion,
        channel = channel,
        env = environment,
        productKey = productKey,
        productId = productId,
        model = model,
        hardwareRevision = hardwareRevision,
        displayName = displayName,
        firmware = firmware,
        payload = payload
    )
}

private fun com.aqua.aqualight.data.devices.runtime.modules.firmware.DeviceFirmwareCommandResult.toApplicationResult(): DeviceFirmwareCommandResult {
    return DeviceFirmwareCommandResult(
        sent = sent,
        messageId = messageId,
        errorMessage = errorMessage
    )
}
