package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import com.aqua.aqualight.databinding.FragmentDeviceCoolingBinding
import java.util.Locale
import kotlin.math.roundToInt

class CoolingManagementRenderer(
    private val binding: FragmentDeviceCoolingBinding
) {

    fun render(
        data: CoolingDeviceRepository.CoolingDashboardData
    ) {
        val rule = data.primaryCoolRule()
        val fan = data.attachedFanFor(rule)
        val usedSensors = data.usedSensorsFor(rule)

        if (rule == null) {
            clear()
            return
        }

        binding.tvControlledFanValue.text = fan?.name ?: "No fan"

        binding.tvFanOutputValue.text = formatFanOutput(
            value = fan?.vNow
        )

        binding.tvFanModeValue.text = fan?.regime?.displayName ?: "--"

        binding.tvAutomationStatusValue.text = resolveAutomationStatus(
            rule = rule,
            fan = fan,
            usedSensorCount = usedSensors.size
        )

        binding.tvStartCoolingValue.text = formatTemperature(
            value = rule.tMin
        )

        binding.tvFullPowerValue.text = formatTemperature(
            value = rule.tMax
        )

        binding.tvFanPowerRangeValue.text = if (fan == null) {
            "--"
        } else {
            "${formatPercent(fan.vMin)} - ${formatPercent(fan.vMax)}"
        }

        binding.tvFanChannelsValue.text = formatFanChannels(
            fans = data.fanChannels
        )

        binding.tvUsedSensorsValue.text = if (usedSensors.isEmpty()) {
            "No sensor selected"
        } else {
            usedSensors.joinToString(
                separator = ", "
            ) { sensor ->
                sensor.name
            }
        }
    }

    fun clear() {
        binding.tvControlledFanValue.text = "--"
        binding.tvFanOutputValue.text = "--"
        binding.tvFanModeValue.text = "--"
        binding.tvAutomationStatusValue.text = "--"
        binding.tvStartCoolingValue.text = "--"
        binding.tvFullPowerValue.text = "--"
        binding.tvFanPowerRangeValue.text = "--"
        binding.tvFanChannelsValue.text = "--"
        binding.tvUsedSensorsValue.text = "--"
    }

    private fun resolveAutomationStatus(
        rule: CoolingDeviceRepository.CoolRuleData,
        fan: CoolingDeviceRepository.FanChannelData?,
        usedSensorCount: Int
    ): String {
        if (fan == null) {
            return "No fan"
        }

        return when (fan.regime) {
            CoolingDeviceRepository.FanRegime.OFF -> {
                "Off"
            }

            CoolingDeviceRepository.FanRegime.ON -> {
                "Manual On"
            }

            CoolingDeviceRepository.FanRegime.AUTO -> {
                when {
                    !rule.enabled -> "Disabled"
                    usedSensorCount <= 0 -> "No sensor"
                    else -> "Active"
                }
            }
        }
    }

    private fun formatFanChannels(
        fans: List<CoolingDeviceRepository.FanChannelData>
    ): String {
        if (fans.isEmpty()) {
            return "No fan channel"
        }

        return fans.joinToString(
            separator = "\n"
        ) { fan ->
            "${fan.name}: ${fan.regime.displayName} • ${formatFanOutput(fan.vNow)}"
        }
    }

    private fun formatFanOutput(
        value: Float?
    ): String {
        if (value == null || value < 0f) {
            return "--"
        }

        return "${normalizePercent(value).roundToInt()}%"
    }

    private fun formatPercent(
        value: Float
    ): String {
        return "${normalizePercent(value).roundToInt()}%"
    }

    private fun normalizePercent(
        value: Float
    ): Float {
        return if (value <= 1f) {
            value.coerceIn(
                0f,
                1f
            ) * 100f
        } else {
            value.coerceIn(
                0f,
                100f
            )
        }
    }

    private fun formatTemperature(
        value: Float
    ): String {
        return String.format(
            Locale.US,
            "%.1f °C",
            value
        )
    }
}