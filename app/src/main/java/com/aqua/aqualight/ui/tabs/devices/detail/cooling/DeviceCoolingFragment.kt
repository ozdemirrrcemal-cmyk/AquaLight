package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentDeviceCoolingBinding
import com.aqua.aqualight.ui.tabs.devices.detail.DeviceVisualSpecs
import kotlinx.coroutines.launch

class DeviceCoolingFragment : Fragment(R.layout.fragment_device_cooling) {

    private var _binding: FragmentDeviceCoolingBinding? = null
    private val binding get() = _binding!!

    private val repository = CoolingDeviceRepository()

    private var temperatureChartRenderer: TemperatureChartRenderer? = null
    private var coolingManagementRenderer: CoolingManagementRenderer? = null

    private var latestCoolingDashboardData: CoolingDeviceRepository.CoolingDashboardData? = null

    private val deviceIp: String
        get() = requireArguments().getString(ARG_DEVICE_IP).orEmpty()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentDeviceCoolingBinding.bind(view)

        applyCoolingVisualStyle()

        temperatureChartRenderer = TemperatureChartRenderer(
            chart = binding.temperatureChartView,
            visualSpec = DeviceVisualSpecs.Cooling
        ).also { renderer ->
            renderer.setup()
        }

        coolingManagementRenderer = CoolingManagementRenderer(
            container = binding.fanCardsContainer,
            visualSpec = DeviceVisualSpecs.Cooling,
            onFanCardClick = { fan, rule ->
                showCoolingFanSettingsBottomSheet(
                    fan = fan,
                    rule = rule
                )
            }
        )

        binding.btnRefreshTemperature.setOnClickListener {
            loadCoolingDashboard()
        }

        loadCoolingDashboard()
    }

    private fun applyCoolingVisualStyle() {
        val visualSpec = DeviceVisualSpecs.Cooling

        binding.cardTemperatureGraph.setCardBackgroundColor(
            visualSpec.cardBackgroundColor
        )

        binding.cardTemperatureGraph.strokeColor =
            visualSpec.cardStrokeColor

        binding.cardCoolingManagement.setCardBackgroundColor(
            visualSpec.cardBackgroundColor
        )

        binding.cardCoolingManagement.strokeColor =
            visualSpec.cardStrokeColor

        binding.viewGraphAccent.setBackgroundColor(
            visualSpec.accentColor
        )

        binding.viewCoolingAccent.setBackgroundColor(
            visualSpec.accentColor
        )

        binding.btnRefreshTemperature.backgroundTintList =
            ColorStateList.valueOf(
                visualSpec.buttonColor
            )

        binding.btnRefreshTemperature.setTextColor(
            visualSpec.buttonTextColor
        )
    }

    private fun loadCoolingDashboard() {
        val currentBinding = _binding ?: return

        if (deviceIp.isBlank()) {
            latestCoolingDashboardData = null
            temperatureChartRenderer?.clear()
            coolingManagementRenderer?.clear()
            return
        }

        currentBinding.btnRefreshTemperature.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                repository.fetchCoolingDashboardData(
                    ipAddress = deviceIp
                )
            }

            if (_binding == null) {
                return@launch
            }

            result.onSuccess { data ->
                latestCoolingDashboardData = data

                temperatureChartRenderer?.render(
                    sensors = data.sensors
                )

                coolingManagementRenderer?.render(
                    data = data
                )
            }.onFailure {
                latestCoolingDashboardData = null

                temperatureChartRenderer?.clear()
                coolingManagementRenderer?.clear()
            }

            _binding?.btnRefreshTemperature?.isEnabled = true
        }
    }

    private fun showCoolingFanSettingsBottomSheet(
        fan: CoolingDeviceRepository.FanChannelData,
        rule: CoolingDeviceRepository.CoolRuleData?
    ) {
        val dashboardData = latestCoolingDashboardData ?: return

        CoolingFanSettingsBottomSheet(
            fragment = this,
            visualSpec = DeviceVisualSpecs.Cooling,
            fan = fan,
            rule = rule,
            sensors = dashboardData.sensors,
            onSave = { draft ->
                handleCoolingFanSettingsDraft(
                    draft = draft
                )
            }
        ).show()
    }

    private fun handleCoolingFanSettingsDraft(
        draft: CoolingFanSettingsBottomSheet.CoolingFanSettingsDraft
    ) {
        // Sonraki adımda ESP32 /set bağlantısı burada yapılacak.
        // Şimdilik bottom sheet doğru fanı, rule'u ve sensörleri alıyor mu diye test edeceğiz.

        // draft.fanIndex
        // draft.ruleIndex
        // draft.fanMode
        // draft.startCooling
        // draft.fullPower
        // draft.minimumPowerPercent
        // draft.maximumPowerPercent
        // draft.selectedSensorIndexes
    }

    override fun onDestroyView() {
        temperatureChartRenderer = null
        coolingManagementRenderer = null
        latestCoolingDashboardData = null
        _binding = null

        super.onDestroyView()
    }

    companion object {
        private const val ARG_DEVICE_ID = "deviceId"
        private const val ARG_DEVICE_IP = "deviceIp"

        fun newInstance(
            deviceId: Long,
            deviceIp: String
        ): DeviceCoolingFragment {
            return DeviceCoolingFragment().apply {
                arguments = Bundle().apply {
                    putLong(
                        ARG_DEVICE_ID,
                        deviceId
                    )

                    putString(
                        ARG_DEVICE_IP,
                        deviceIp
                    )
                }
            }
        }
    }
}