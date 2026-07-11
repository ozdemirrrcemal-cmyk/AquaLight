package com.aqua.aqualight.data.devices.store

import com.aqua.aqualight.data.devices.contract.AqlWsContract
import com.aqua.aqualight.data.devices.model.DeviceCapabilities
import com.aqua.aqualight.data.devices.model.DeviceConnectionState
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceIdentity
import com.aqua.aqualight.data.devices.model.DeviceLimits
import com.aqua.aqualight.data.devices.model.DeviceProduct
import com.aqua.aqualight.data.devices.model.DeviceRuntimeEndpoint
import com.aqua.aqualight.data.devices.model.DeviceSnapshot
import com.aqua.aqualight.data.devices.model.DeviceUid

internal object KnownDeviceProtoMapper {

    fun toStored(
        ownerUid: String,
        snapshot: DeviceSnapshot
    ): StoredKnownDevice {
        return StoredKnownDevice.newBuilder()
            .setOwnerUid(ownerUid.trim())
            .setIdentity(snapshot.identity.toStored())
            .setProduct(snapshot.product.toStored())
            .setFirmwareVersion(snapshot.firmwareVersion.trim())
            .setFirmwareBuild(snapshot.firmwareBuild.trim())
            .setApiVersion(snapshot.apiVersion.trim())
            .setProtocolVersion(snapshot.protocolVersion.trim())
            .setEndpoint(snapshot.endpoint.toStored())
            .setCapabilities(snapshot.capabilities.toStored())
            .setLimits(snapshot.limits.toStored())
            .addAllSupportedFeatures(snapshot.supportedFeatures.normalizedValues())
            .addAllSupportedScreens(snapshot.supportedScreens.normalizedValues())
            .addAllModules(snapshot.modules.normalizedValues())
            .setLastSeenAtMillis(snapshot.lastSeenAtMillis.coerceAtLeast(0L))
            .build()
    }

    fun toSnapshot(
        stored: StoredKnownDevice
    ): DeviceSnapshot {
        val identity = stored.identity
        val product = stored.product
        val endpoint = stored.endpoint
        val capabilities = stored.capabilities
        val limits = stored.limits

        return DeviceSnapshot(
            identity = DeviceIdentity(
                uid = DeviceUid(identity.deviceUid),
                shortId = identity.shortId,
                chipId = identity.chipId,
                espChipId = identity.espChipId,
                efuseMac = identity.efuseMac,
                macAddress = identity.macAddress,
                serialNumber = identity.serialNumber,
                firmwareSerial = identity.firmwareSerial,
                displayName = identity.displayName,
                customName = identity.customName,
                setupCode = identity.setupCode,
                setupSsid = identity.setupSsid
            ),
            product = DeviceProduct(
                brand = product.brand,
                productId = product.productId,
                productKey = product.productKey,
                family = DeviceFamily.fromWire(
                    product.familyRaw.ifBlank { product.family }
                ),
                familyRaw = product.familyRaw.ifBlank { product.family },
                line = product.line,
                model = product.model,
                displayName = product.displayName,
                skuId = product.skuId,
                skuCode = product.skuCode,
                setupCode = product.setupCode,
                hardwareRevision = product.hardwareRevision
            ),
            firmwareVersion = stored.firmwareVersion,
            firmwareBuild = stored.firmwareBuild,
            apiVersion = stored.apiVersion,
            protocolVersion = stored.protocolVersion,
            endpoint = DeviceRuntimeEndpoint(
                ip = endpoint.ip,
                wifiMode = endpoint.wifiMode,
                wifiConnected = endpoint.wifiConnected,
                setupApActive = endpoint.setupApActive,
                runtimeTransport = endpoint.runtimeTransport,
                wsPort = endpoint.wsPort,
                wsPath = endpoint.wsPath.ifBlank { AqlWsContract.DEFAULT_PATH },
                wsProtocol = endpoint.wsProtocol.ifBlank { AqlWsContract.DEFAULT_PROTOCOL },
                wsProtocolVersion = endpoint.wsProtocolVersion,
                discoveryPort = endpoint.discoveryPort
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
            supportedFeatures = stored.getSupportedFeaturesList(),
            supportedScreens = stored.getSupportedScreensList(),
            modules = stored.getModulesList(),
            connectionState = DeviceConnectionState(),
            lastSeenAtMillis = stored.lastSeenAtMillis
        )
    }

    private fun DeviceIdentity.toStored(): StoredKnownDeviceIdentity {
        return StoredKnownDeviceIdentity.newBuilder()
            .setDeviceUid(uid.value.trim())
            .setShortId(shortId.trim())
            .setChipId(chipId.trim())
            .setEspChipId(espChipId.trim())
            .setEfuseMac(efuseMac.trim())
            .setMacAddress(macAddress.trim())
            .setSerialNumber(serialNumber.trim())
            .setFirmwareSerial(firmwareSerial.trim())
            .setDisplayName(displayName.trim())
            .setCustomName(customName.trim())
            .setSetupCode(setupCode.trim())
            .setSetupSsid(setupSsid.trim())
            .build()
    }

    private fun DeviceProduct.toStored(): StoredKnownDeviceProduct {
        return StoredKnownDeviceProduct.newBuilder()
            .setBrand(brand.trim())
            .setProductId(productId.trim())
            .setProductKey(productKey.trim())
            .setFamily(family.wireValue)
            .setFamilyRaw(familyRaw.trim().ifBlank { family.wireValue })
            .setLine(line.trim())
            .setModel(model.trim())
            .setDisplayName(displayName.trim())
            .setSkuId(skuId.trim())
            .setSkuCode(skuCode.trim())
            .setSetupCode(setupCode.trim())
            .setHardwareRevision(hardwareRevision.trim())
            .build()
    }

    private fun DeviceRuntimeEndpoint.toStored(): StoredKnownDeviceEndpoint {
        return StoredKnownDeviceEndpoint.newBuilder()
            .setIp(ip.trim())
            .setWifiMode(wifiMode.trim())
            .setWifiConnected(wifiConnected)
            .setSetupApActive(setupApActive)
            .setRuntimeTransport(runtimeTransport.trim())
            .setWsPort(wsPort.coerceIn(0, MAX_PORT))
            .setWsPath(wsPath.trim().ifBlank { AqlWsContract.DEFAULT_PATH })
            .setWsProtocol(wsProtocol.trim().ifBlank { AqlWsContract.DEFAULT_PROTOCOL })
            .setWsProtocolVersion(wsProtocolVersion.coerceAtLeast(0))
            .setDiscoveryPort(discoveryPort.coerceIn(0, MAX_PORT))
            .build()
    }

    private fun DeviceCapabilities.toStored(): StoredKnownDeviceCapabilities {
        return StoredKnownDeviceCapabilities.newBuilder()
            .setLight(light)
            .setManualLight(manualLight)
            .setLightProgram(lightProgram)
            .setLightPresets(lightPresets)
            .setLightSimulation(lightSimulation)
            .setFan(fan)
            .setCooling(cooling)
            .setTemperature(temperature)
            .setStandaloneTimer(standaloneTimer)
            .setDosing(dosing)
            .setTimeSync(timeSync)
            .setOta(ota)
            .build()
    }

    private fun DeviceLimits.toStored(): StoredKnownDeviceLimits {
        return StoredKnownDeviceLimits.newBuilder()
            .setLightChannelCount(lightChannelCount.coerceAtLeast(0))
            .setFanOutputCount(fanOutputCount.coerceAtLeast(0))
            .setTemperatureSensorCount(temperatureSensorCount.coerceAtLeast(0))
            .setTimerChannelCount(timerChannelCount.coerceAtLeast(0))
            .setDosingChannelCount(dosingChannelCount.coerceAtLeast(0))
            .build()
    }

    private fun List<String>.normalizedValues(): List<String> {
        return asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .sorted()
            .toList()
    }

    private const val MAX_PORT = 65_535
}
