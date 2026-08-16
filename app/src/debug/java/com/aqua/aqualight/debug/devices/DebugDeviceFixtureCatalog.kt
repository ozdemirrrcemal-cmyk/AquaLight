package com.aqua.aqualight.debug.devices

import com.aqua.aqualight.application.devices.DeviceRootCatalogState
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceListItem
import com.aqua.aqualight.data.devices.catalog.AqlCommercialCatalogProduct
import com.aqua.aqualight.data.devices.catalog.AqlCommercialDeviceCatalog
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceFamily
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
 * Installable-Debug-only fixtures for non-Dosing products.
 *
 * Dosing fixtures are deliberately excluded. Dosing acceptance uses physical devices and the same
 * production runtime/state path in every build type.
 */
internal class DebugDeviceFixtureCatalog {

    val snapshots: List<DeviceSnapshot> = AqlCommercialDeviceCatalog.products
        .filterNot { product -> product.family == DeviceFamily.DOSING }
        .mapIndexed { index, product -> product.toFixtureSnapshot(index) }

    private val snapshotsByUid = snapshots.associateBy { snapshot -> snapshot.deviceUid.value }

    init {
        check(snapshots.none { snapshot -> snapshot.product.family == DeviceFamily.DOSING }) {
            "Dosing debug fixtures are forbidden; Dosing must use the production runtime."
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
        val deviceUid = DeviceUid("DEBUG-FIXTURE-${productKey.value}")
        val now = System.currentTimeMillis()
        return DeviceSnapshot(
            identity = toFixtureIdentity(deviceUid, index),
            product = toFixtureProduct(),
            firmwareVersion = "1.0.0-debug",
            firmwareBuild = "fixture",
            apiVersion = "1",
            protocolVersion = "1",
            endpoint = fixtureEndpoint(index),
            capabilities = toFixtureCapabilities(),
            limits = toFixtureLimits(),
            supportedFeatures = profile.supportedFeatures.map { feature -> feature.wireValue },
            supportedScreens = profile.supportedScreens.map { screen -> screen.wireValue },
            runtimeMetadataGeneration = 1L,
            connectionState = fixtureConnectionState(now),
            lastSeenAtMillis = now
        )
    }

    private fun AqlCommercialCatalogProduct.toFixtureIdentity(
        deviceUid: DeviceUid,
        index: Int
    ): DeviceIdentity = DeviceIdentity(
        uid = deviceUid,
        shortId = "DBG-${index + 1}",
        serialNumber = "DEBUG-${productKey.value}",
        displayName = displayName,
        customName = "Test $displayName"
    )

    private fun AqlCommercialCatalogProduct.toFixtureProduct(): DeviceProduct = DeviceProduct(
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
    )

    private fun AqlCommercialCatalogProduct.toFixtureCapabilities(): DeviceCapabilities {
        val values = profile.capabilities
        return DeviceCapabilities(
            light = values.light,
            manualLight = values.manualLight,
            lightProgram = values.lightProgram,
            lightPresets = values.lightPresets,
            lightSimulation = values.lightSimulation,
            fan = values.fan,
            cooling = values.cooling,
            temperature = values.temperature,
            standaloneTimer = values.standaloneTimer,
            dosing = values.dosing,
            timeSync = values.timeSync,
            ota = values.ota
        )
    }

    private fun AqlCommercialCatalogProduct.toFixtureLimits(): DeviceLimits = DeviceLimits(
        lightChannelCount = limits.lightChannelCount,
        fanOutputCount = limits.fanOutputCount,
        temperatureSensorCount = limits.temperatureSensorCount,
        timerChannelCount = limits.timerChannelCount,
        dosingChannelCount = limits.dosingChannelCount
    )
}

private fun fixtureEndpoint(index: Int): DeviceRuntimeEndpoint = DeviceRuntimeEndpoint(
    ip = "192.168.254.${100 + index}",
    wifiMode = "STA",
    wifiConnected = true,
    wsPort = 81
)

private fun fixtureConnectionState(now: Long): DeviceConnectionState = DeviceConnectionState(
    onlineState = DeviceOnlineState.AUTHENTICATED,
    lastUdpSeenAtMillis = now,
    lastWsConnectedAtMillis = now,
    lastAuthenticatedAtMillis = now,
    lastRuntimeMessageAtMillis = now,
    lastControlProofAtMillis = now
)
