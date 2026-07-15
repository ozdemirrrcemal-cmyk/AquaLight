package com.aqua.aqualight.ui.tabs.devices.add

import com.aqua.aqualight.application.devices.provisioning.ProvisioningDraftOperations
import com.aqua.aqualight.application.devices.provisioning.ProvisioningDraftRequest
import com.aqua.aqualight.application.devices.provisioning.ProvisioningDraftSession
import java.util.TimeZone

object DeviceWifiProvisioningDraftFactory {

    fun create(
        args: DeviceWifiProvisioningFragmentArgs,
        ssid: String,
        networkKey: String,
        operations: ProvisioningDraftOperations
    ): Result<ProvisioningDraftSession> {
        return runCatching {
            val deviceTimeZone = TimeZone.getDefault()
            val utcOffsetMinutes =
                deviceTimeZone.getOffset(System.currentTimeMillis()) / MILLIS_PER_MINUTE
            val timeZoneId = deviceTimeZone.id.orEmpty()
            val request = ProvisioningDraftRequest(
                candidateId = args.candidateId,
                bleAddress = args.bleAddress,
                bleName = args.bleName,
                claimCode = args.claimCode,
                rawQrPayload = args.rawQrPayload,
                deviceTitle = args.deviceTitle,
                deviceSerial = args.deviceSerial,
                deviceModel = args.deviceModel,
                wifiSsid = ssid,
                wifiPassword = networkKey,
                timezone = "$timeZoneId$TIMEZONE_OFFSET_SEPARATOR$utcOffsetMinutes",
                utcOffsetMinutes = utcOffsetMinutes
            )
            operations.createDraft(request).getOrThrow()
        }
    }

    private const val MILLIS_PER_MINUTE = 60_000
    private const val TIMEZONE_OFFSET_SEPARATOR = "|"
}
