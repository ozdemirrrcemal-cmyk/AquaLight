package com.aqua.aqualight.data.devices.add

import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.catalog.AquaSetupSsid

object DeviceSetupSsidParser {

    /**
     * Commercial setup SSID format:
     * AQL-<setupCode>-<shortId>
     *
     * Eski AquaLight-WRGBPro2-1221 / AquaCool-CoolPro-7872 formatları artık
     * kabul edilmez. Uygulama yayınlanmadığı için eski destek taşınmıyor.
     */
    fun parse(
        ssid: String
    ): DeviceAddCandidate? {
        val setupSsid = AquaSetupSsid.parse(
            ssid = ssid
        ) ?: return null

        val definition = AquaDeviceCatalog.findBySetupCode(
            setupCode = setupSsid.setupCode
        ) ?: return null

        return DeviceAddCandidate(
            key = "setup:${setupSsid.rawSsid}",
            source = DeviceAddSource.SETUP_AP,
            displayName = definition.displayName,
            familyName = definition.productFamily,
            productId = definition.productId,
            productKey = definition.productKey,
            category = definition.category,
            setupCode = definition.setupCode,
            deviceType = definition.type,
            stateText = "Setup mode",
            actionText = "Set up",
            setupSsid = setupSsid.rawSsid,
            setupShortId = setupSsid.shortId
        )
    }

    fun isPossibleAquaSetupSsid(
        ssid: String
    ): Boolean {
        return AquaSetupSsid.isValid(
            ssid = ssid
        )
    }
}
