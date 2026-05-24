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

        binding.btnRefreshTemperature.setOnClickListener {
            loadTemperatureGraph()
        }

        loadTemperatureGraph()
    }

    private fun applyCoolingVisualStyle() {
        val visualSpec = DeviceVisualSpecs.Cooling

        binding.cardTemperatureGraph.setCardBackgroundColor(
            visualSpec.cardBackgroundColor
        )

        binding.cardTemperatureGraph.strokeColor =
            visualSpec.cardStrokeColor

        binding.viewGraphAccent.setBackgroundColor(
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

    private fun loadTemperatureGraph() {
        val currentBinding = _binding ?: return

        if (deviceIp.isBlank()) {
            temperatureChartRenderer?.clear()
            return
        }

        currentBinding.btnRefreshTemperature.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                repository.fetchTemperatureData(
                    ipAddress = deviceIp
                )
            }

            if (_binding == null) {
                return@launch
            }

            result.onSuccess { data ->
                temperatureChartRenderer?.render(
                    sensors = data.sensors
                )
            }.onFailure {
                temperatureChartRenderer?.clear()
            }

            _binding?.btnRefreshTemperature?.isEnabled = true
        }
    }

    override fun onDestroyView() {
        temperatureChartRenderer = null
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