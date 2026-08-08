package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceListItem
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceLimits
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid
import com.aqua.aqualight.data.devices.toDeviceRootSnapshot
import com.aqua.aqualight.data.devices.toOwnerDeviceListItem

/**
 * Installable-Debug-only device fixtures.
 *
 * There is exactly one fixture for every production commercial catalog product. Product identity,
 * capabilities, limits, features, screens and menu routes always originate from that catalog. The
 * fixture supplies only physical-device facts unavailable during UI work: identity, endpoint,
 * liveness and validated runtime generation.
 */
internal class DebugDeviceFixtureCatalog {

    val snapshots: List<DeviceSnapshot> = AqlCommercialDeviceCatalog.products
        .mapIndexed { index, product -> product.toFixtureSnapshot(index) }

    private val snapshotsByUid = snapshots.associateBy { snapshot -> snapshot.deviceUid.value }

    init {
        check(snapshots.size == AqlCommercialDeviceCatalog.products.size) {
            "Debug fixture catalog must cover every commercial product."
        }
        snapshots.forEach { snapshot ->
            check(snapshot.toDeviceRootSnapshot().catalogState == DeviceRootCatalogState.VALID) {
                "Debug fixture must remain valid against the production commercial catalog: " +
                    snapshot.deviceUid.value
            }
        }
    }

    fun contains(deviceUid: String): Boolean = deviceUid.trim() in snapshotsByUid

    fun snapshot(deviceUid: String): DeviceSnapshot? = snapshotsByUid[deviceUid.trim()]

    fun rootSnapshot(deviceUid: String): DeviceRootSnapshot? =
        snapshot(deviceUid)?.toDeviceRootSnapshot()

    fun listItems(): List<OwnerDeviceListItem> = snapshots.map { snapshot ->
        snapshot.toOwnerDeviceListItem().copy(
            displayName = "${snapshot.title} [TEST]"
        )
    }

    private fun AqlCommercialCatalogProduct.toFixtureSnapshot(index: Int): DeviceSnapshot {
        val capabilities = profile.capabilities
        val fixtureSuffix = productKey.value
        val deviceUid = DeviceUid("DEBUG-FIXTURE-$fixtureSuffix")
        val now = System.currentTimeMillis()

        return DeviceSnapshot(
            identity = DeviceIdentity(
                uid = deviceUid,
                shortId = "DBG-${index + 1}",
                serialNumber = "DEBUG-$fixtureSuffix",
                displayName = displayName,
                customName = "Test $displayName"
            ),
            product = DeviceProduct(
                brand = "AquaLight",
                productId = productId.value,
                productKey = productKey.value,
                family = family,
                familyRaw = family.wireValue,
                line = line.value,
                model = model.value,
                displayName = displayName,
                skuId = skuId.value,
                skuCode = skuCode.value,
                hardwareRevision = hardwareRevision.value
            ),
            firmwareVersion = "1.0.0-debug",
            firmwareBuild = "fixture",
            apiVersion = "1",
            protocolVersion = "1",
            endpoint = DeviceRuntimeEndpoint(
                ip = "192.168.254.${100 + index}",
                wifiMode = "STA",
                wifiConnected = true,
                wsPort = 81
            ),
            capabilities = DeviceCapabilities(
                light = capabilities.light,
                manualLight = capabilities.manualLight,
                lightProgram = capabilities.lightProgram,
                lightPresets = capabilities.lightPresets,
                lightSimulation = capabilities.lightSimulation,
                fan = capabilities.fan,
                cooling = capabilities.cooling,
                temperature = capabilities.temperature,
                standaloneTimer = capabilities.standaloneTimer,
                dosing = capabilities.dosing,
                timeSync = capabilities.timeSync,
                ota = capabilities.ota
            ),
            limits = DeviceLimits(
                lightChannelCount = limits.lightChannelCount,
                fanOutputCount = limits.fanOutputCount,
                temperatureSensorCount = limits.temperatureSensorCount,
                timerChannelCount = limits.timerChannelCount,
                dosingChannelCount = limits.dosingChannelCount
            ),
            supportedFeatures = profile.supportedFeatures.map { feature -> feature.wireValue },
            supportedScreens = profile.supportedScreens.map { screen -> screen.wireValue },
            runtimeMetadataGeneration = 1L,
            connectionState = DeviceConnectionState(
                onlineState = DeviceOnlineState.AUTHENTICATED,
                lastUdpSeenAtMillis = now,
                lastWsConnectedAtMillis = now,
                lastAuthenticatedAtMillis = now,
                lastRuntimeMessageAtMillis = now,
                lastControlProofAtMillis = now
            ),
            lastSeenAtMillis = now
        )
    }
}
