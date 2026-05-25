package com.aqua.aqualight.ui.tabs.devices.detail.cooling

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentDeviceCoolingBinding
import com.aqua.aqualight.ui.tabs.devices.detail.DeviceVisualSpecs
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class DeviceCoolingFragment : Fragment(R.layout.fragment_device_cooling) {

    private var _binding: FragmentDeviceCoolingBinding? = null
    private val binding get() = _binding!!

    private val repository = CoolingDeviceRepository()

    private var temperatureChartRenderer: TemperatureChartRenderer? = null
    private var coolingManagementRenderer: CoolingManagementRenderer? = null

    private var latestCoolingDashboardData: CoolingDeviceRepository.CoolingDashboardData? = null
    private var isSavingCoolingSettings: Boolean = false

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

            showUserErrorMessage(
                message = "Device address is missing. Please reconnect the device."
            )

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
            }.onFailure { error ->
                latestCoolingDashboardData = null

                temperatureChartRenderer?.clear()
                coolingManagementRenderer?.clear()

                showUserErrorMessage(
                    message = createDeviceConnectionMessage(
                        action = DeviceAction.LOAD,
                        error = error
                    )
                )
            }

            _binding?.btnRefreshTemperature?.isEnabled = true
        }
    }

    private fun showCoolingFanSettingsBottomSheet(
        fan: CoolingDeviceRepository.FanChannelData,
        rule: CoolingDeviceRepository.CoolRuleData?
    ) {
        val dashboardData = latestCoolingDashboardData ?: return

        if (isSavingCoolingSettings) {
            return
        }

        CoolingFanSettingsBottomSheet(
            fragment = this,
            visualSpec = DeviceVisualSpecs.Cooling,
            fan = fan,
            rule = rule,
            sensors = dashboardData.sensors,
            onSave = { draft, sheet ->
                saveCoolingFanSettings(
                    draft = draft,
                    sheet = sheet
                )
            }
        ).show()
    }

    private fun saveCoolingFanSettings(
        draft: CoolingFanSettingsBottomSheet.CoolingFanSettingsDraft,
        sheet: CoolingFanSettingsBottomSheet
    ) {
        val dashboardData = latestCoolingDashboardData

        if (dashboardData == null) {
            sheet.showSaveError(
                message = "Device data is not ready yet. Please refresh and try again."
            )
            return
        }

        if (deviceIp.isBlank()) {
            sheet.showSaveError(
                message = "Device address is missing. Please reconnect the device."
            )
            return
        }

        if (isSavingCoolingSettings) {
            return
        }

        isSavingCoolingSettings = true

        showGlobalLoading(
            show = true
        )

        _binding?.btnRefreshTemperature?.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            val saveResult = runCatching {
                repository.saveCoolingFanSettings(
                    ipAddress = deviceIp,
                    currentData = dashboardData,
                    fanIndex = draft.fanIndex,
                    ruleIndex = draft.ruleIndex,
                    fanName = draft.fanName,
                    fanMode = draft.fanMode,
                    startCooling = draft.startCooling,
                    fullPower = draft.fullPower,
                    minimumPowerPercent = draft.minimumPowerPercent,
                    maximumPowerPercent = draft.maximumPowerPercent,
                    selectedSensorIndexes = draft.selectedSensorIndexes
                )
            }

            if (_binding == null) {
                isSavingCoolingSettings = false

                showGlobalLoading(
                    show = false
                )

                return@launch
            }

            saveResult.onFailure { error ->
                sheet.showSaveError(
                    message = createDeviceConnectionMessage(
                        action = DeviceAction.SAVE,
                        error = error
                    )
                )

                _binding?.btnRefreshTemperature?.isEnabled = true

                isSavingCoolingSettings = false

                showGlobalLoading(
                    show = false
                )

                return@launch
            }

            sheet.closeAfterSave()

            val refreshResult = runCatching {
                repository.fetchCoolingDashboardData(
                    ipAddress = deviceIp
                )
            }

            if (_binding == null) {
                isSavingCoolingSettings = false

                showGlobalLoading(
                    show = false
                )

                return@launch
            }

            refreshResult.onSuccess { data ->
                latestCoolingDashboardData = data

                temperatureChartRenderer?.render(
                    sensors = data.sensors
                )

                coolingManagementRenderer?.render(
                    data = data
                )
            }.onFailure {
                showUserErrorMessage(
                    message = "Settings were saved, but the latest device data could not be refreshed."
                )
            }

            _binding?.btnRefreshTemperature?.isEnabled = true

            isSavingCoolingSettings = false

            showGlobalLoading(
                show = false
            )
        }
    }

    private enum class DeviceAction {
        LOAD,
        SAVE
    }

    private fun createDeviceConnectionMessage(
        action: DeviceAction,
        error: Throwable
    ): String {
        val rootError = findRootCause(
            error = error
        )

        return when (rootError) {
            is SocketTimeoutException,
            is ConnectException,
            is NoRouteToHostException -> {
                when (action) {
                    DeviceAction.LOAD -> {
                        "Device is not responding. Make sure your phone and the device are on the same Wi-Fi network."
                    }

                    DeviceAction.SAVE -> {
                        "Settings could not be saved. Make sure the device is online and connected to the same Wi-Fi network."
                    }
                }
            }

            is UnknownHostException -> {
                "Device address could not be found. Please scan or reconnect the device."
            }

            is SocketException,
            is IOException -> {
                when (action) {
                    DeviceAction.LOAD -> {
                        "Could not connect to the device. Check your Wi-Fi connection and try again."
                    }

                    DeviceAction.SAVE -> {
                        "Settings could not be saved. Check your Wi-Fi connection and try again."
                    }
                }
            }

            is IllegalStateException -> {
                when (action) {
                    DeviceAction.LOAD -> {
                        "The device did not return valid cooling data. Please try again."
                    }

                    DeviceAction.SAVE -> {
                        "The device did not accept the settings. Please try again."
                    }
                }
            }

            else -> {
                when (action) {
                    DeviceAction.LOAD -> {
                        "Cooling data could not be loaded. Please try again."
                    }

                    DeviceAction.SAVE -> {
                        "Settings could not be saved. Please try again."
                    }
                }
            }
        }
    }

    private fun findRootCause(
        error: Throwable
    ): Throwable {
        var current: Throwable = error

        while (current.cause != null && current.cause !== current) {
            current = current.cause ?: break
        }

        return current
    }

    private fun showUserErrorMessage(
        message: String
    ) {
        val baseActivity = activity as? BaseActivity

        if (baseActivity != null) {
            baseActivity.showSnackBar(
                message = message,
                type = BaseActivity.SnackType.ERROR
            )
        } else {
            Toast.makeText(
                requireContext(),
                message,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showGlobalLoading(
        show: Boolean
    ) {
        (activity as? BaseActivity)?.showLoading(
            show
        )
    }

    override fun onDestroyView() {
        temperatureChartRenderer = null
        coolingManagementRenderer = null
        latestCoolingDashboardData = null
        isSavingCoolingSettings = false
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