package com.aqua.aqualight.ui.tabs.devices.detail.common

import androidx.annotation.StringRes
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.DeviceRootCapability
import com.aqua.aqualight.application.devices.DeviceRootSnapshot
import com.aqua.aqualight.application.devices.OwnerDeviceAvailability
import com.aqua.aqualight.ui.common.text.AquaUiText

object DeviceRootPresentationMapper {

    @StringRes
    fun availabilityLabelRes(snapshot: DeviceRootSnapshot): Int =
        if (snapshot.availability == OwnerDeviceAvailability.REACHABLE) {
            R.string.device_online
        } else {
            R.string.device_offline
        }

    fun primaryCount(snapshot: DeviceRootSnapshot, kind: DeviceRootKind): Int = when (kind) {
        DeviceRootKind.DOSING -> snapshot.dosingChannelCount
        DeviceRootKind.TIMER -> snapshot.timerChannelCount
        DeviceRootKind.COOLING -> snapshot.fanOutputCount
    }

    fun overviewFeatureText(snapshot: DeviceRootSnapshot, kind: DeviceRootKind): AquaUiText {
        val labels = buildList<AquaUiText> {
            when (kind) {
                DeviceRootKind.DOSING -> if (DeviceRootCapability.DOSING in snapshot.capabilities) {
                    addResource(R.string.device_feature_dosing)
                }
                DeviceRootKind.TIMER -> if (DeviceRootCapability.STANDALONE_TIMER in snapshot.capabilities) {
                    addResource(R.string.device_feature_timer)
                }
                DeviceRootKind.COOLING -> {
                    if (DeviceRootCapability.COOLING in snapshot.capabilities) {
                        addResource(R.string.device_feature_cooling)
                    }
                    if (DeviceRootCapability.FAN in snapshot.capabilities) {
                        addResource(R.string.device_feature_fan)
                    }
                    if (DeviceRootCapability.TEMPERATURE in snapshot.capabilities) {
                        addResource(R.string.device_feature_temperature)
                    }
                }
            }
            if (DeviceRootCapability.TIME_SYNC in snapshot.capabilities) {
                addResource(R.string.device_feature_time_sync)
            }
            if (DeviceRootCapability.OTA in snapshot.capabilities) {
                addResource(R.string.device_feature_ota)
            }
            addDynamic(snapshot.supportedFeatures)
            addDynamic(snapshot.supportedScreens)
        }
        return labels.asUiText()
    }

    fun lightFeatureText(snapshot: DeviceRootSnapshot): AquaUiText {
        val labels = buildList<AquaUiText> {
            if (DeviceRootCapability.MANUAL_LIGHT in snapshot.capabilities) {
                addResource(R.string.device_feature_manual_light)
            }
            if (DeviceRootCapability.LIGHT_PROGRAM in snapshot.capabilities) {
                addResource(R.string.device_feature_program)
            }
            if (DeviceRootCapability.LIGHT_PRESETS in snapshot.capabilities) {
                addResource(R.string.device_feature_presets)
            }
            if (DeviceRootCapability.LIGHT_SIMULATION in snapshot.capabilities) {
                addResource(R.string.device_feature_simulation)
            }
            if (DeviceRootCapability.TEMPERATURE in snapshot.capabilities) {
                addResource(R.string.device_feature_temperature)
            }
            if (DeviceRootCapability.OTA in snapshot.capabilities) {
                addResource(R.string.device_feature_ota)
            }
            addDynamic(snapshot.supportedFeatures)
            addDynamic(snapshot.supportedScreens)
        }
        return labels.asUiText()
    }

    private fun MutableList<AquaUiText>.addResource(@StringRes resId: Int) {
        add(AquaUiText.Resource(resId))
    }

    private fun MutableList<AquaUiText>.addDynamic(values: Iterable<String>) {
        values.filter(String::isNotBlank).mapTo(this) { AquaUiText.Dynamic(it) }
    }

    private fun List<AquaUiText>.asUiText(): AquaUiText {
        val distinctLabels = distinct()
        return if (distinctLabels.isEmpty()) {
            AquaUiText.Resource(R.string.device_unknown)
        } else {
            AquaUiText.Joined(
                parts = distinctLabels,
                separatorRes = R.string.common_list_separator
            )
        }
    }
}
