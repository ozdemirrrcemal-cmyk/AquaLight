package com.aqua.aqualight.data.devices.provisioning.repository

import android.content.Context
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceLimits
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningDraft
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningRuntimeHandoff
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider

class AqlProvisioningHandoffSaver(
    context: Context
) {

    private val appContext = context.applicationContext
    private val metadataResolver = AqlProvisioningRuntimeMetadataResolver()

    suspend fun saveAndConnect(
        draft: AqlProvisioningDraft,
        handoff: AqlProvisioningRuntimeHandoff
    ): Result<DeviceSnapshot> {
        return runCatching {
            require(handoff.isUsable) {
                "Runtime handoff is missing device uid, WebSocket endpoint or token."
            }

            val repository = DevicesRepositoryProvider.get(appContext)

            repository.saveRuntimeToken(
                deviceUid = handoff.deviceUid,
                token = handoff.webSocketToken
            )

            val snapshot = DeviceSnapshot(
                identity = DeviceIdentity(
                    uid = handoff.deviceUid,
                    macAddress = draft.bleAddress,
                    serialNumber = draft.deviceSerial,
                    displayName = resolvedTitle(draft)
                ),
                product = DeviceProduct(
                    brand = "AquaLight",
                    family = DeviceFamily.UNKNOWN,
                    familyRaw = "",
                    model = draft.deviceModel,
                    displayName = resolvedTitle(draft)
                ),
                firmwareVersion = "",
                firmwareBuild = "",
                endpoint = handoff.endpoint,
                capabilities = DeviceCapabilities(),
                limits = DeviceLimits(),
                connectionState = DeviceConnectionState(
                    onlineState = DeviceOnlineState.ONLINE_LAN,
                    lastUdpSeenAtMillis = System.currentTimeMillis(),
                    lastErrorMessage = null
                ),
                lastSeenAtMillis = System.currentTimeMillis()
            )

            val registered = repository.registerSnapshot(snapshot)

            val resolved = metadataResolver.resolveAndConnect(
                repository = repository,
                provisionalSnapshot = registered
            ).getOrThrow()

            require(resolved.product.family != DeviceFamily.UNKNOWN) {
                "Runtime device identity did not include a supported product family."
            }

            repository.registerSnapshot(resolved)
        }
    }

    suspend fun rollbackProvisioningRegistration(deviceUid: DeviceUid): Result<Unit> {
        return runCatching {
            val repository = DevicesRepositoryProvider.get(appContext)
            repository.removeProvisioningRegistration(deviceUid)
            Unit
        }
    }

    private fun resolvedTitle(
        draft: AqlProvisioningDraft
    ): String {
        return draft.deviceTitle
            .ifBlank { draft.deviceModel }
            .ifBlank { draft.candidateId }
    }
}
