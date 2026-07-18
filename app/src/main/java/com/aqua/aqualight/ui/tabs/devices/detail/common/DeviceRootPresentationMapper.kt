package com.aqua.aqualight.ui.tabs.devices.detail.common

import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceRootCapability
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.application.text.AppTextResolver

object DeviceRootPresentationMapper {

    fun availabilityLabel(
        snapshot: DeviceRootSnapshot,
        textResolver: AppTextResolver
    ): String = textResolver.get(
        if (snapshot.availability == OwnerDeviceAvailability.REACHABLE) {
            R.string.device_runtime_online
        } else {
            R.string.device_runtime_offline
        }
    )

    fun primaryCount(snapshot: DeviceRootSnapshot, kind: DeviceRootKind): Int = when (kind) {
        DeviceRootKind.DOSING -> snapshot.dosingChannelCount
        DeviceRootKind.TIMER -> snapshot.timerChannelCount
        DeviceRootKind.COOLING -> snapshot.fanOutputCount
    }

    fun overviewFeatureLabel(
        snapshot: DeviceRootSnapshot,
        kind: DeviceRootKind,
        textResolver: AppTextResolver
    ): String {
        val labels = buildList {
            when (kind) {
                DeviceRootKind.DOSING -> if (DeviceRootCapability.DOSING in snapshot.capabilities) {
                    add(textResolver.get(R.string.device_feature_dosing))
                }
                DeviceRootKind.TIMER -> if (DeviceRootCapability.STANDALONE_TIMER in snapshot.capabilities) {
                    add(textResolver.get(R.string.device_feature_timer))
                }
                DeviceRootKind.COOLING -> {
                    if (DeviceRootCapability.COOLING in snapshot.capabilities) {
                        add(textResolver.get(R.string.device_feature_cooling))
                    }
                    if (DeviceRootCapability.FAN in snapshot.capabilities) {
                        add(textResolver.get(R.string.device_feature_fan))
                    }
                    if (DeviceRootCapability.TEMPERATURE in snapshot.capabilities) {
                        add(textResolver.get(R.string.device_feature_temperature))
                    }
                }
            }
            if (DeviceRootCapability.TIME_SYNC in snapshot.capabilities) {
                add(textResolver.get(R.string.device_feature_time_sync))
            }
            if (DeviceRootCapability.OTA in snapshot.capabilities) {
                add(textResolver.get(R.string.device_feature_ota))
            }
            addAll(snapshot.supportedFeatures.filter(String::isNotBlank))
            addAll(snapshot.supportedScreens.filter(String::isNotBlank))
        }
        return labels.distinct().joinToString(separator = ", ").ifBlank {
            textResolver.get(R.string.device_runtime_unknown)
        }
    }

    fun lightFeatureLabel(
        snapshot: DeviceRootSnapshot,
        textResolver: AppTextResolver
    ): String {
        val labels = buildList {
            if (DeviceRootCapability.MANUAL_LIGHT in snapshot.capabilities) {
                add(textResolver.get(R.string.device_feature_manual_light))
            }
            if (DeviceRootCapability.LIGHT_PROGRAM in snapshot.capabilities) {
                add(textResolver.get(R.string.device_feature_program))
            }
            if (DeviceRootCapability.LIGHT_PRESETS in snapshot.capabilities) {
                add(textResolver.get(R.string.device_feature_presets))
            }
            if (DeviceRootCapability.LIGHT_SIMULATION in snapshot.capabilities) {
                add(textResolver.get(R.string.device_feature_simulation))
            }
            if (DeviceRootCapability.TEMPERATURE in snapshot.capabilities) {
                add(textResolver.get(R.string.device_feature_temperature))
            }
            if (DeviceRootCapability.OTA in snapshot.capabilities) {
                add(textResolver.get(R.string.device_feature_ota))
            }
            addAll(snapshot.supportedFeatures.filter(String::isNotBlank))
            addAll(snapshot.supportedScreens.filter(String::isNotBlank))
        }
        return labels.distinct().joinToString(separator = ", ").ifBlank {
            textResolver.get(R.string.device_runtime_unknown)
        }
    }
}
