package com.aqua.aqualight.ui.tabs.devices.detail.common

import com.aqua.aqualight.application.devices.DeviceRootCapability
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability

object DeviceRootPresentationMapper {

    fun availabilityLabel(snapshot: DeviceRootSnapshot): String =
        if (snapshot.availability == OwnerDeviceAvailability.REACHABLE) "Online" else "Offline"

    fun primaryCount(snapshot: DeviceRootSnapshot, kind: DeviceRootKind): Int = when (kind) {
        DeviceRootKind.DOSING -> snapshot.dosingChannelCount
        DeviceRootKind.TIMER -> snapshot.timerChannelCount
        DeviceRootKind.COOLING -> snapshot.fanOutputCount
    }

    fun overviewFeatureLabel(snapshot: DeviceRootSnapshot, kind: DeviceRootKind): String {
        val labels = buildList {
            when (kind) {
                DeviceRootKind.DOSING -> if (DeviceRootCapability.DOSING in snapshot.capabilities) add("Dosing")
                DeviceRootKind.TIMER -> if (DeviceRootCapability.STANDALONE_TIMER in snapshot.capabilities) add("Timer")
                DeviceRootKind.COOLING -> {
                    if (DeviceRootCapability.COOLING in snapshot.capabilities) add("Cooling")
                    if (DeviceRootCapability.FAN in snapshot.capabilities) add("Fan")
                    if (DeviceRootCapability.TEMPERATURE in snapshot.capabilities) add("Temperature")
                }
            }
            if (DeviceRootCapability.TIME_SYNC in snapshot.capabilities) add("Time sync")
            if (DeviceRootCapability.OTA in snapshot.capabilities) add("OTA")
            addAll(snapshot.supportedFeatures.filter(String::isNotBlank))
            addAll(snapshot.supportedScreens.filter(String::isNotBlank))
        }
        return labels.distinct().joinToString(separator = ", ").ifBlank { "Unknown" }
    }

    fun lightFeatureLabel(snapshot: DeviceRootSnapshot): String {
        val labels = buildList {
            if (DeviceRootCapability.MANUAL_LIGHT in snapshot.capabilities) add("Manual light")
            if (DeviceRootCapability.LIGHT_PROGRAM in snapshot.capabilities) add("Program")
            if (DeviceRootCapability.LIGHT_PRESETS in snapshot.capabilities) add("Presets")
            if (DeviceRootCapability.LIGHT_SIMULATION in snapshot.capabilities) add("Simulation")
            if (DeviceRootCapability.TEMPERATURE in snapshot.capabilities) add("Temperature")
            if (DeviceRootCapability.OTA in snapshot.capabilities) add("OTA")
            addAll(snapshot.supportedFeatures.filter(String::isNotBlank))
            addAll(snapshot.supportedScreens.filter(String::isNotBlank))
        }
        return labels.distinct().joinToString(separator = ", ").ifBlank { "Unknown" }
    }
}
