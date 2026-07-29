package com.aqua.aqualight.data.devices.store

import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceLimits
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceSnapshot

/** Central merge rules for one logical device. */
object DeviceSnapshotMerger {

    fun merge(previous: DeviceSnapshot?, incoming: DeviceSnapshot): DeviceSnapshot {
        if (previous == null) return incoming

        return incoming.copy(
            identity = mergeIdentity(previous.identity, incoming.identity),
            product = mergeProduct(previous.product, incoming.product),
            firmwareVersion = incoming.firmwareVersion.ifBlank { previous.firmwareVersion },
            firmwareBuild = incoming.firmwareBuild.ifBlank { previous.firmwareBuild },
            apiVersion = incoming.apiVersion.ifBlank { previous.apiVersion },
            protocolVersion = incoming.protocolVersion.ifBlank { previous.protocolVersion },
            endpoint = mergeEndpoint(previous.endpoint, incoming.endpoint),
            capabilities = mergeCapabilities(previous.capabilities, incoming.capabilities),
            limits = mergeLimits(previous.limits, incoming.limits),
            supportedFeatures = incoming.supportedFeatures.ifEmpty { previous.supportedFeatures },
            supportedScreens = incoming.supportedScreens.ifEmpty { previous.supportedScreens },
            modules = incoming.modules.ifEmpty { previous.modules },
            runtimeMetadataGeneration = maxOf(
                previous.runtimeMetadataGeneration,
                incoming.runtimeMetadataGeneration
            ),
            connectionState = mergeConnectionState(previous.connectionState, incoming.connectionState),
            lastSeenAtMillis = maxOf(previous.lastSeenAtMillis, incoming.lastSeenAtMillis)
        )
    }

    private fun mergeIdentity(previous: DeviceIdentity, incoming: DeviceIdentity): DeviceIdentity =
        incoming.copy(
            shortId = incoming.shortId.ifBlank { previous.shortId },
            chipId = incoming.chipId.ifBlank { previous.chipId },
            espChipId = incoming.espChipId.ifBlank { previous.espChipId },
            efuseMac = incoming.efuseMac.ifBlank { previous.efuseMac },
            macAddress = incoming.macAddress.ifBlank { previous.macAddress },
            serialNumber = incoming.serialNumber.ifBlank { previous.serialNumber },
            firmwareSerial = incoming.firmwareSerial.ifBlank { previous.firmwareSerial },
            displayName = incoming.displayName.ifBlank { previous.displayName },
            customName = incoming.customName.ifBlank { previous.customName },
            setupCode = incoming.setupCode.ifBlank { previous.setupCode },
            setupSsid = incoming.setupSsid.ifBlank { previous.setupSsid }
        )

    private fun mergeProduct(previous: DeviceProduct, incoming: DeviceProduct): DeviceProduct {
        val familyRaw = incoming.familyRaw.ifBlank { previous.familyRaw }
        val family = when {
            incoming.family != DeviceFamily.UNKNOWN -> incoming.family
            previous.family != DeviceFamily.UNKNOWN -> previous.family
            familyRaw.isNotBlank() -> DeviceFamily.fromWire(familyRaw)
            else -> DeviceFamily.UNKNOWN
        }
        return incoming.copy(
            brand = incoming.brand.ifBlank { previous.brand },
            productId = incoming.productId.ifBlank { previous.productId },
            productKey = incoming.productKey.ifBlank { previous.productKey },
            family = family,
            familyRaw = familyRaw,
            line = incoming.line.ifBlank { previous.line },
            model = incoming.model.ifBlank { previous.model },
            displayName = incoming.displayName.ifBlank { previous.displayName },
            skuId = incoming.skuId.ifBlank { previous.skuId },
            skuCode = incoming.skuCode.ifBlank { previous.skuCode },
            setupCode = incoming.setupCode.ifBlank { previous.setupCode },
            hardwareRevision = incoming.hardwareRevision.ifBlank { previous.hardwareRevision }
        )
    }

    private fun mergeEndpoint(
        previous: DeviceRuntimeEndpoint,
        incoming: DeviceRuntimeEndpoint
    ): DeviceRuntimeEndpoint = incoming.copy(
        ip = incoming.ip.ifBlank { previous.ip },
        wifiMode = incoming.wifiMode.ifBlank { previous.wifiMode },
        runtimeTransport = incoming.runtimeTransport.ifBlank { previous.runtimeTransport },
        wsPort = incoming.wsPort.takeIf { it > 0 } ?: previous.wsPort,
        wsPath = incoming.wsPath.ifBlank { previous.wsPath },
        wsProtocol = incoming.wsProtocol.ifBlank { previous.wsProtocol },
        wsProtocolVersion = incoming.wsProtocolVersion.takeIf { it > 0 }
            ?: previous.wsProtocolVersion,
        discoveryPort = incoming.discoveryPort.takeIf { it > 0 } ?: previous.discoveryPort
    )

    private fun mergeCapabilities(
        previous: DeviceCapabilities,
        incoming: DeviceCapabilities
    ): DeviceCapabilities = if (incoming == DeviceCapabilities()) previous else incoming

    private fun mergeLimits(previous: DeviceLimits, incoming: DeviceLimits): DeviceLimits =
        if (incoming == DeviceLimits()) previous else incoming

    private fun mergeConnectionState(
        previous: DeviceConnectionState,
        incoming: DeviceConnectionState
    ): DeviceConnectionState {
        val resolvedOnlineState = when {
            incoming.onlineState == DeviceOnlineState.UNKNOWN &&
                previous.onlineState != DeviceOnlineState.UNKNOWN -> previous.onlineState
            shouldPreserveRuntimeState(previous, incoming) -> previous.onlineState
            else -> incoming.onlineState
        }
        return incoming.copy(
            onlineState = resolvedOnlineState,
            lastUdpSeenAtMillis = maxNullable(previous.lastUdpSeenAtMillis, incoming.lastUdpSeenAtMillis),
            lastWsConnectedAtMillis = maxNullable(previous.lastWsConnectedAtMillis, incoming.lastWsConnectedAtMillis),
            lastAuthenticatedAtMillis = maxNullable(previous.lastAuthenticatedAtMillis, incoming.lastAuthenticatedAtMillis),
            lastRuntimeMessageAtMillis = maxNullable(previous.lastRuntimeMessageAtMillis, incoming.lastRuntimeMessageAtMillis),
            lastControlProofAtMillis = maxNullable(previous.lastControlProofAtMillis, incoming.lastControlProofAtMillis),
            lastUdpSeenElapsedMillis = maxNullable(previous.lastUdpSeenElapsedMillis, incoming.lastUdpSeenElapsedMillis),
            lastWsConnectedElapsedMillis = maxNullable(previous.lastWsConnectedElapsedMillis, incoming.lastWsConnectedElapsedMillis),
            lastAuthenticatedElapsedMillis = maxNullable(previous.lastAuthenticatedElapsedMillis, incoming.lastAuthenticatedElapsedMillis),
            lastRuntimeMessageElapsedMillis = maxNullable(previous.lastRuntimeMessageElapsedMillis, incoming.lastRuntimeMessageElapsedMillis),
            lastControlProofElapsedMillis = maxNullable(previous.lastControlProofElapsedMillis, incoming.lastControlProofElapsedMillis),
            lastErrorMessage = incoming.lastErrorMessage ?: previous.lastErrorMessage
        )
    }

    private fun shouldPreserveRuntimeState(
        previous: DeviceConnectionState,
        incoming: DeviceConnectionState
    ): Boolean {
        if (!incoming.onlineState.isLanPresenceOnly) return false
        return previous.onlineState == DeviceOnlineState.AUTHENTICATED ||
            previous.onlineState == DeviceOnlineState.PROVISIONING ||
            previous.onlineState == DeviceOnlineState.OTA_UPDATING
    }

    private val DeviceOnlineState.isLanPresenceOnly: Boolean
        get() = this == DeviceOnlineState.ONLINE_LAN || this == DeviceOnlineState.STALE

    private fun maxNullable(left: Long?, right: Long?): Long? = when {
        left == null -> right
        right == null -> left
        else -> maxOf(left, right)
    }
}
