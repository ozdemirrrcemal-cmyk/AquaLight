package com.aqua.aqualight.data.devices.add

import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.catalog.AquaSetupSsid

object DeviceSetupSsidParser {

    /**
     * Commercial setup SSID format:
     * AQL-<setupCode>-<shortId>
     *
     * Product-name based SSID aliases are intentionally not accepted.
     * The app is not released yet, so setup discovery only follows this final
     * commercial contract.
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
