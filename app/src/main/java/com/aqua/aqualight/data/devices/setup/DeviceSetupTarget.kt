package com.aqua.aqualight.data.devices.setup

import com.aqua.aqualight.data.devices.DeviceSerialFormatter
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCatalog
import com.aqua.aqualight.data.devices.catalog.AquaDeviceCategory
import com.aqua.aqualight.data.devices.catalog.AquaProductKey
import com.aqua.aqualight.data.devices.catalog.AquaSetupSsid

/**
 * Device setup entry arguments converted into a validated setup target.
 *
 * Keeping this outside the Fragment makes the setup contract reusable and keeps
 * UI code away from product/category resolution rules.
 */
data class DeviceSetupEntryArgs(
    val setupSsid: String,
    val displayName: String,
    val familyName: String,
    val productId: String,
    val productKey: String,
    val category: String,
    val setupCode: String,
    val setupShortId: String
)

data class DeviceSetupTarget(
    val setupSsid: String,
    val displayName: String,
    val familyName: String,
    val expectedProductId: String,
    val expectedProductKey: AquaProductKey,
    val expectedCategory: AquaDeviceCategory,
    val expectedSetupCode: String,
    val setupShortId: String,
    val setupContractValid: Boolean,
    val serialText: String
)

object DeviceSetupTargetResolver {

    fun resolve(
        args: DeviceSetupEntryArgs
    ): DeviceSetupTarget {
        var displayName = args.displayName
        var familyName = args.familyName

        val rawSetupSsid = args.setupSsid.trim()

        val parsedSetupSsid = AquaSetupSsid.parse(
            ssid = rawSetupSsid
        )

        val setupSsid = parsedSetupSsid?.rawSsid ?: rawSetupSsid

        var expectedProductId = args.productId

        var expectedProductKey = AquaProductKey.fromStorageKey(
            value = args.productKey
        )

        var expectedCategory = AquaDeviceCategory.fromStorageKey(
            value = args.category
        )

        var expectedSetupCode = args.setupCode.ifBlank {
            parsedSetupSsid?.setupCode.orEmpty()
        }.trim()

        val setupShortId = args.setupShortId.ifBlank {
            parsedSetupSsid?.shortId.orEmpty()
        }.trim()

        val definition = AquaDeviceCatalog.findDefinition(
            productId = expectedProductId,
            productKey = expectedProductKey,
            category = expectedCategory
        ) ?: AquaDeviceCatalog.findBySetupCode(
            setupCode = expectedSetupCode
        )

        if (definition != null) {
            expectedProductId = definition.productId
            expectedProductKey = definition.productKey
            expectedCategory = definition.category
            expectedSetupCode = definition.setupCode

            if (displayName.isBlank() || displayName == DEFAULT_DEVICE_NAME) {
                displayName = definition.displayName
            }

            if (familyName.isBlank() || familyName == DEFAULT_FAMILY_NAME) {
                familyName = definition.productFamily
            }
        }

        val setupContractValid = parsedSetupSsid != null &&
            definition != null &&
            expectedSetupCode.equals(
                other = parsedSetupSsid.setupCode,
                ignoreCase = true
            ) &&
            setupShortId.equals(
                other = parsedSetupSsid.shortId,
                ignoreCase = true
            )

        return DeviceSetupTarget(
            setupSsid = setupSsid,
            displayName = displayName,
            familyName = familyName,
            expectedProductId = expectedProductId,
            expectedProductKey = expectedProductKey,
            expectedCategory = expectedCategory,
            expectedSetupCode = expectedSetupCode,
            setupShortId = setupShortId,
            setupContractValid = setupContractValid,
            serialText = DeviceSerialFormatter.buildCommercialIdentifier(
                setupCode = expectedSetupCode,
                shortId = setupShortId
            )
        )
    }

    private const val DEFAULT_DEVICE_NAME = "Device"
    private const val DEFAULT_FAMILY_NAME = "Aqua device"
}
