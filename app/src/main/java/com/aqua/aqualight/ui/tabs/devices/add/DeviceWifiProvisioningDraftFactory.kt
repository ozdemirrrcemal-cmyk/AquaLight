package com.aqua.aqualight.ui.tabs.devices.add

import com.aqua.aqualight.data.devices.provisioning.model.AqlProvisioningDraft
import com.aqua.aqualight.data.devices.provisioning.model.AqlWifiCredentials
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningBleAddressCache
import com.aqua.aqualight.data.devices.provisioning.store.AqlProvisioningDraftStore
import java.util.TimeZone

object DeviceWifiProvisioningDraftFactory {

    fun create(
        args: DeviceWifiProvisioningFragmentArgs,
        ssid: String,
        networkKey: String
    ): Result<AqlProvisioningDraft> {
        return runCatching {
            val deviceTimeZone = TimeZone.getDefault()
            val utcOffsetMinutes = deviceTimeZone.getOffset(System.currentTimeMillis()) / MILLIS_PER_MINUTE
            val timeZoneId = deviceTimeZone.id.orEmpty()
            val credentials = AqlWifiCredentials(
                ssid = ssid,
                password = networkKey,
                timezone = "$timeZoneId$TIMEZONE_OFFSET_SEPARATOR$utcOffsetMinutes",
                utcOffsetMinutes = utcOffsetMinutes
            )

            AqlProvisioningDraftStore.create(
                candidateId = args.candidateId,
                bleAddress = args.bleAddress.ifBlank { AqlProvisioningBleAddressCache.get(args.bleName) },
                bleName = args.bleName,
                claimCode = args.claimCode,
                rawQrPayload = args.rawQrPayload,
                deviceTitle = args.deviceTitle,
                deviceSerial = args.deviceSerial,
                deviceModel = args.deviceModel,
                wifiCredentials = credentials
            )
        }
    }

    private const val MILLIS_PER_MINUTE = 60_000
    private const val TIMEZONE_OFFSET_SEPARATOR = "|"
}
