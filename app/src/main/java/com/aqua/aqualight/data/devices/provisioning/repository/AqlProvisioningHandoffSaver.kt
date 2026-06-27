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
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningDraft
import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningRuntimeHandoff
import com.aqua.aqualight.data.devices.repository.DevicesRepositoryProvider

class AqlProvisioningHandoffSaver(
    context: Context
) {

    private val appContext = context.applicationContext

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
                    displayName = resolvedTitle(
                        handoff = handoff,
                        draft = draft
                    )
                ),
                product = DeviceProduct(
                    brand = "AquaLight",
                    family = DeviceFamily.fromWire(handoff.productFamily),
                    familyRaw = handoff.productFamily,
                    model = handoff.productModel.ifBlank { draft.deviceModel },
                    displayName = resolvedTitle(
                        handoff = handoff,
                        draft = draft
                    )
                ),
                firmwareVersion = handoff.firmwareVersion,
                firmwareBuild = handoff.firmwareBuild,
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

            repository.connectRuntime(handoff.deviceUid)

            registered
        }
    }

    private fun resolvedTitle(
        handoff: AqlProvisioningRuntimeHandoff,
        draft: AqlProvisioningDraft
    ): String {
        return handoff.productName
            .ifBlank { draft.deviceTitle }
            .ifBlank { handoff.productModel }
            .ifBlank { draft.deviceModel }
            .ifBlank { handoff.deviceUid.value }
    }
}
