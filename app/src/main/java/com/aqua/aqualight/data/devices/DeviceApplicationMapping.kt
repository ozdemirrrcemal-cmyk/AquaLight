package com.aqua.aqualight.data.devices

import com.aqua.aqualight.application.devices.DeviceRootCapability
import com.aqua.aqualight.application.devices.DeviceRootMenuFeature
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.devices.OwnerDeviceFamily
import com.aqua.aqualight.application.devices.OwnerDeviceListItem
import com.aqua.aqualight.application.devices.OwnerDeviceStatusSnapshot
import com.aqua.aqualight.application.devices.TankDeviceListItem
import com.aqua.aqualight.data.devices.model.DeviceFamily
import com.aqua.aqualight.data.devices.model.DeviceOnlineState
import com.aqua.aqualight.data.devices.model.DeviceSnapshot

internal fun DeviceSnapshot.toOwnerDeviceListItem(
    assignedTankName: String = ""
): OwnerDeviceListItem {
    return OwnerDeviceListItem(
        deviceUid = deviceUid.value,
        displayName = title.ifBlank { deviceUid.value },
        serialText = serialText(),
        family = product.family.toOwnerDeviceFamily(),
        availability = connectionState.onlineState.toOwnerDeviceAvailability(),
        assignedTankName = assignedTankName.trim()
    )
}

internal fun DeviceSnapshot.toOwnerDeviceStatusSnapshot(): OwnerDeviceStatusSnapshot {
    return OwnerDeviceStatusSnapshot(
        deviceUid = deviceUid.value,
        displayName = title.ifBlank { deviceUid.value },
        serialText = serialText(),
        family = product.family.toOwnerDeviceFamily(),
        availability = connectionState.onlineState.toOwnerDeviceAvailability(),
        ipAddress = endpoint.ip.trim(),
        lastSeenAtMillis = latestSeenAtMillis()
    )
}

internal fun DeviceSnapshot.toTankDeviceListItem(): TankDeviceListItem {
    return TankDeviceListItem(
        deviceUid = deviceUid.value,
        displayName = title.ifBlank { deviceUid.value },
        serialText = serialText(),
        family = product.family.toOwnerDeviceFamily(),
        availability = connectionState.onlineState.toOwnerDeviceAvailability()
    )
}

internal fun DeviceSnapshot.toDeviceRootSnapshot(): DeviceRootSnapshot {
    val rootCapabilities = buildSet {
        if (capabilities.manualLight) add(DeviceRootCapability.MANUAL_LIGHT)
        if (capabilities.lightProgram) add(DeviceRootCapability.LIGHT_PROGRAM)
        if (capabilities.lightPresets) add(DeviceRootCapability.LIGHT_PRESETS)
        if (capabilities.lightSimulation) add(DeviceRootCapability.LIGHT_SIMULATION)
        if (capabilities.dosing) add(DeviceRootCapability.DOSING)
        if (capabilities.standaloneTimer) add(DeviceRootCapability.STANDALONE_TIMER)
        if (capabilities.cooling) add(DeviceRootCapability.COOLING)
        if (capabilities.fan) add(DeviceRootCapability.FAN)
        if (capabilities.temperature) add(DeviceRootCapability.TEMPERATURE)
        if (capabilities.timeSync) add(DeviceRootCapability.TIME_SYNC)
        if (capabilities.ota) add(DeviceRootCapability.OTA)
    }

    return DeviceRootSnapshot(
        deviceUid = deviceUid.value,
        title = product.displayName.ifBlank { product.model },
        availability = connectionState.onlineState.toOwnerDeviceAvailability(),
        ipAddress = endpoint.ip.trim(),
        firmwareLabel = listOf(
            firmwareVersion.ifBlank { null },
            firmwareBuild.ifBlank { null }
        ).filterNotNull().joinToString(separator = " / "),
        modelLabel = listOf(
            product.model.ifBlank { null },
            product.hardwareRevision.ifBlank { null }
        ).filterNotNull().joinToString(separator = " / "),
        lightChannelCount = limits.lightChannelCount,
        timerChannelCount = limits.timerChannelCount,
        dosingChannelCount = limits.dosingChannelCount,
        fanOutputCount = limits.fanOutputCount,
        capabilities = rootCapabilities,
        supportedFeatures = supportedFeatures.filter(String::isNotBlank),
        supportedScreens = supportedScreens.filter(String::isNotBlank),
        menuFeatures = rootMenuFeatures()
    )
}

internal fun DeviceFamily.toOwnerDeviceFamily(): OwnerDeviceFamily {
    return when (this) {
        DeviceFamily.LIGHT -> OwnerDeviceFamily.LIGHT
        DeviceFamily.TIMER -> OwnerDeviceFamily.TIMER
        DeviceFamily.DOSING -> OwnerDeviceFamily.DOSING
        DeviceFamily.COOLING -> OwnerDeviceFamily.COOLING
        DeviceFamily.UNKNOWN -> OwnerDeviceFamily.UNKNOWN
    }
}

internal fun DeviceOnlineState.toOwnerDeviceAvailability(): OwnerDeviceAvailability {
    return when (this) {
        DeviceOnlineState.AUTHENTICATED,
        DeviceOnlineState.PROVISIONING,
        DeviceOnlineState.OTA_UPDATING -> OwnerDeviceAvailability.REACHABLE

        DeviceOnlineState.ONLINE_LAN,
        DeviceOnlineState.CONNECTING_WS,
        DeviceOnlineState.UNKNOWN,
        DeviceOnlineState.DISCOVERING,
        DeviceOnlineState.STALE,
        DeviceOnlineState.OFFLINE,
        DeviceOnlineState.LOCAL_NETWORK_OFFLINE,
        DeviceOnlineState.AUTH_REQUIRED,
        DeviceOnlineState.ERROR -> OwnerDeviceAvailability.UNREACHABLE
    }
}

private fun DeviceSnapshot.rootMenuFeatures(): Set<DeviceRootMenuFeature> = buildSet {
    if (capabilities.manualLight || hasAnyScreen("light.manual", "manual", "manualLight")) {
        add(DeviceRootMenuFeature.LIGHT_MANUAL)
    }
    if (hasAnyScreen("light.quickSetup", "quickSetup", "quick_setup")) {
        add(DeviceRootMenuFeature.LIGHT_QUICK_SETUP)
    }
    if (capabilities.lightProgram || hasAnyScreen("light.programs", "programs", "programList", "program_list")) {
        add(DeviceRootMenuFeature.LIGHT_PROGRAMS)
    }
    if (capabilities.lightPresets || hasAnyScreen("light.presets", "presets")) {
        add(DeviceRootMenuFeature.LIGHT_PRESETS)
    }
    if (capabilities.lightSimulation || hasAnyScreen("light.simulation", "simulation")) {
        add(DeviceRootMenuFeature.LIGHT_SIMULATION)
    }
    if (capabilities.dosing || hasAnyScreen("dosing.channels", "channels", "dosing")) {
        add(DeviceRootMenuFeature.DOSING_CHANNELS)
    }
    if (hasAnyScreen("dosing.calibration", "calibration")) {
        add(DeviceRootMenuFeature.DOSING_CALIBRATION)
    }
    if (hasAnyScreen("dosing.schedules", "schedules", "singleDose", "hourly24", "customPeriods", "timerMode")) {
        add(DeviceRootMenuFeature.DOSING_SCHEDULES)
    }
    if (capabilities.standaloneTimer || hasAnyScreen("timer.channels", "channels", "timer")) {
        add(DeviceRootMenuFeature.TIMER_CHANNELS)
    }
    if (hasAnyScreen("timer.schedules", "schedules")) {
        add(DeviceRootMenuFeature.TIMER_SCHEDULES)
    }
    if (capabilities.cooling || capabilities.fan || hasAnyScreen("cooling.fans", "fan", "fans", "cooling")) {
        add(DeviceRootMenuFeature.COOLING_FANS)
    }
    if (capabilities.temperature || hasAnyScreen("cooling.temperature", "temperature")) {
        add(DeviceRootMenuFeature.COOLING_TEMPERATURE)
    }
    if (capabilities.ota || hasAnyScreen("device.settings", "settings")) {
        add(DeviceRootMenuFeature.DEVICE_SETTINGS)
    }
}

private fun DeviceSnapshot.hasAnyScreen(vararg names: String): Boolean {
    val normalizedSupported = (supportedScreens + supportedFeatures)
        .map { value -> value.trim().lowercase() }
        .filter(String::isNotBlank)
        .toSet()
    return names.any { name -> normalizedSupported.contains(name.trim().lowercase()) }
}

private fun DeviceSnapshot.serialText(): String {
    return identity.serialNumber
        .ifBlank { identity.firmwareSerial }
        .ifBlank { identity.shortId }
        .ifBlank { deviceUid.value }
}

private fun DeviceSnapshot.latestSeenAtMillis(): Long {
    return listOfNotNull(
        connectionState.lastControlProofAtMillis,
        connectionState.lastRuntimeMessageAtMillis,
        connectionState.lastAuthenticatedAtMillis,
        connectionState.lastWsConnectedAtMillis,
        connectionState.lastUdpSeenAtMillis,
        lastSeenAtMillis.takeIf { value -> value > 0L }
    ).maxOrNull() ?: 0L
}
