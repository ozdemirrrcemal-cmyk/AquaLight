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

        binding.tvFanOutputValue.text = formatFanOutput(
            fan?.vNow
        )

        binding.tvCoolingActiveValue.text = if (rule.enabled) {
            "On"
        } else {
            "Off"
        }

        binding.tvFanModeValue.text = fan?.regime?.displayName ?: "--"

        binding.tvStartCoolingValue.text = formatTemperature(
            rule.tMin
        )

        binding.tvFullPowerValue.text = formatTemperature(
            rule.tMax
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
        binding.tvFanOutputValue.text = "--"
        binding.tvCoolingActiveValue.text = "--"
        binding.tvFanModeValue.text = "--"
        binding.tvStartCoolingValue.text = "--"
        binding.tvFullPowerValue.text = "--"
        binding.tvUsedSensorsValue.text = "--"
    }

    private fun formatFanOutput(
        value: Float?
    ): String {
        if (value == null || value < 0f) {
            return "--"
        }

        val percent = (value.coerceIn(
            0f,
            1f
        ) * 100f).roundToInt()

        return "$percent%"
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