package com.aqua.aqualight.ui.tabs.devices.detail.timer

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.devices.DevicesDataStoreManager
import com.aqua.aqualight.databinding.FragmentDeviceTimerBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil

class DeviceTimerFragment : Fragment(R.layout.fragment_device_timer) {

    private var _binding: FragmentDeviceTimerBinding? = null
    private val binding get() = _binding!!

    private val repository = TimerDeviceRepository()

    private lateinit var devicesStore: DevicesDataStoreManager

    private var renderer: TimerDashboardRenderer? = null
    private var latestDashboardData: TimerDeviceRepository.TimerDashboardData? = null

    private var currentUserDeviceName: String = ""
    private var currentDisplayedTitle: String = ""

    private var isQuickActionRunning: Boolean = false
    private var isDeviceWriteRunning: Boolean = false

    private val deviceId: Long
        get() = requireArguments().getLong(ARG_DEVICE_ID)

    private val deviceIp: String
        get() = requireArguments().getString(ARG_DEVICE_IP).orEmpty()

    private val deviceTitle: String
        get() = requireArguments().getString(ARG_DEVICE_TITLE).orEmpty()

    private val canEditDeviceName: Boolean
        get() = requireArguments().getBoolean(
            ARG_CAN_EDIT_DEVICE_NAME,
            false
        )

    private val userDeviceName: String
        get() = requireArguments().getString(ARG_USER_DEVICE_NAME).orEmpty()

    private val defaultDeviceTitle: String
        get() = requireArguments().getString(ARG_DEFAULT_DEVICE_TITLE).orEmpty()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentDeviceTimerBinding.bind(view)

        devicesStore = DevicesDataStoreManager.create(
            requireContext()
        )

        renderer = TimerDashboardRenderer(
            binding = binding
        )

        bindStaticScreen()

        renderer?.clear()

        binding.tvTimerOnlineStatus.text = "Connecting"

        setOutletCardsEnabled(
            enabled = false
        )

        bindClicks()

        startDashboardAutoRefresh()
    }

    private fun bindStaticScreen() {
        currentUserDeviceName = userDeviceName

        currentDisplayedTitle = deviceTitle.ifBlank {
            defaultDeviceTitle.ifBlank {
                "Timer Controller"
            }
        }

        binding.tvTimerTitle.text = currentDisplayedTitle
        binding.tvTimerSubtitle.text = "4 channel smart timer"

        binding.cardTimerDeviceSummary.isClickable =
            canEditDeviceName

        binding.cardTimerDeviceSummary.isFocusable =
            canEditDeviceName
    }

    private fun bindClicks() {
        binding.cardTimerDeviceSummary.setOnClickListener {
            if (canEditDeviceName) {
                showDeviceNameBottomSheet()
            }
        }

        binding.cardOutlet1.setOnClickListener {
            showOutletSettings(
                outletPosition = 0
            )
        }

        binding.cardOutlet2.setOnClickListener {
            showOutletSettings(
                outletPosition = 1
            )
        }

        binding.cardOutlet3.setOnClickListener {
            showOutletSettings(
                outletPosition = 2
            )
        }

        binding.cardOutlet4.setOnClickListener {
            showOutletSettings(
                outletPosition = 3
            )
        }

        binding.cardOutlet1Power.setOnClickListener {
            quickToggleOutlet(
                outletPosition = 0
            )
        }

        binding.cardOutlet2Power.setOnClickListener {
            quickToggleOutlet(
                outletPosition = 1
            )
        }

        binding.cardOutlet3Power.setOnClickListener {
            quickToggleOutlet(
                outletPosition = 2
            )
        }

        binding.cardOutlet4Power.setOnClickListener {
            quickToggleOutlet(
                outletPosition = 3
            )
        }
    }

    private fun startDashboardAutoRefresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                refreshTimerDashboard(
                    clearWhenFailed = true
                )

                while (true) {
                    delay(
                        DASHBOARD_REFRESH_INTERVAL_MS
                    )

                    refreshTimerDashboard(
                        clearWhenFailed = false
                    )
                }
            }
        }
    }

    private suspend fun refreshTimerDashboard(
        clearWhenFailed: Boolean
    ) {
        if (isDeviceWriteRunning) {
            return
        }

        if (!isDeviceReachable()) {
            latestDashboardData = null

            if (_binding == null) {
                return
            }

            binding.tvTimerOnlineStatus.text = "Offline"

            if (clearWhenFailed) {
                renderer?.clear()

                setOutletCardsEnabled(
                    enabled = false
                )
            }

            return
        }

        val result = runCatching {
            repository.fetchTimerDashboardData(
                ipAddress = deviceIp
            )
        }

        if (_binding == null) {
            return
        }

        result.onSuccess { data ->
            latestDashboardData = data

            binding.tvTimerOnlineStatus.text = "Online"

            renderer?.render(
                data = data
            )

            setOutletCardsEnabled(
                enabled = true
            )
        }.onFailure {
            binding.tvTimerOnlineStatus.text = "Offline"

            if (
                clearWhenFailed ||
                latestDashboardData == null
            ) {
                latestDashboardData = null

                renderer?.clear()

                setOutletCardsEnabled(
                    enabled = false
                )
            }
        }
    }

    private fun showDeviceNameBottomSheet() {
        TimerDeviceNameBottomSheet(
            fragment = this,
            currentName = currentUserDeviceName.ifBlank {
                currentDisplayedTitle
            },
            fallbackName = defaultDeviceTitle.ifBlank {
                "Timer Controller"
            },
            onSave = { newName, sheet ->
                saveDeviceName(
                    newName = newName,
                    sheet = sheet
                )
            }
        ).show()
    }

    private fun saveDeviceName(
        newName: String,
        sheet: TimerDeviceNameBottomSheet
    ) {
        showGlobalLoading()

        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                devicesStore.updateDevice(
                    id = deviceId,
                    name = newName
                )
            }

            if (_binding == null) {
                hideGlobalLoading()
                return@launch
            }

            result.onSuccess {
                hideGlobalLoading()

                currentUserDeviceName = newName
                currentDisplayedTitle = newName

                binding.tvTimerTitle.text = newName

                sheet.closeAfterSave()
            }.onFailure {
                hideGlobalLoading()

                sheet.showSaveError(
                    message = "Device name could not be saved."
                )

                showErrorSnack(
                    message = "Device name could not be saved."
                )
            }
        }
    }

    private fun showOutletSettings(
        outletPosition: Int
    ) {
        val data = latestDashboardData ?: run {
            showErrorSnack(
                message = "Timer data is not available."
            )
            return
        }

        val outlet = data.outlets.getOrNull(
            outletPosition
        ) ?: run {
            showErrorSnack(
                message = "Outlet data is not available."
            )
            return
        }

        val rule = data.ruleForOutlet(
            outlet = outlet
        )

        val state = TimerOutletEditorState(
            outletIndex = outlet.index,
            timerRuleIndex = rule?.index ?: outlet.index,
            gpioPwm = outlet.gpioPwm,
            outletName = outlet.name,
            regime = outlet.regime,
            timerEnabled = rule?.enabled ?: false,
            startTime = rule?.timeStart?.ifBlank {
                "00:00"
            } ?: "00:00",
            runDurationMinutes = durationToMinutes(
                value = rule?.intervalOn
            ).coerceAtLeast(
                1
            ),
            offDurationMinutes = durationToMinutes(
                value = rule?.intervalOff
            ).coerceAtLeast(
                0
            ),
            repeatCount = rule?.count?.coerceAtLeast(
                1
            ) ?: 1,
            weekDays = normalizeWeekDays(
                source = rule?.weekDays
            )
        )

        TimerOutletSettingsBottomSheet(
            fragment = this,
            initialState = state,
            onSave = { updatedState, sheet ->
                saveOutletSettings(
                    state = updatedState,
                    sheet = sheet
                )
            }
        ).show()
    }

    private fun saveOutletSettings(
        state: TimerOutletEditorState,
        sheet: TimerOutletSettingsBottomSheet
    ) {
        if (!isDeviceReachable()) {
            sheet.showSaveError(
                message = "Device is not reachable."
            )

            binding.tvTimerOnlineStatus.text = "Offline"

            showErrorSnack(
                message = "Device is not reachable."
            )

            return
        }

        isDeviceWriteRunning = true

        showGlobalLoading()

        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                repository.updateOutletSettings(
                    ipAddress = deviceIp,
                    state = state
                )

                repository.fetchTimerDashboardData(
                    ipAddress = deviceIp
                )
            }

            if (_binding == null) {
                isDeviceWriteRunning = false
                hideGlobalLoading()
                return@launch
            }

            result.onSuccess { data ->
                isDeviceWriteRunning = false

                hideGlobalLoading()

                latestDashboardData = data

                binding.tvTimerOnlineStatus.text = "Online"

                renderer?.render(
                    data = data
                )

                setOutletCardsEnabled(
                    enabled = true
                )

                sheet.closeAfterSave()
            }.onFailure {
                isDeviceWriteRunning = false

                hideGlobalLoading()

                binding.tvTimerOnlineStatus.text = "Offline"

                sheet.showSaveError(
                    message = "Outlet settings could not be saved."
                )

                showErrorSnack(
                    message = "Outlet settings could not be saved."
                )
            }
        }
    }

    private fun quickToggleOutlet(
        outletPosition: Int
    ) {
        if (isQuickActionRunning) {
            return
        }

        val data = latestDashboardData ?: run {
            showErrorSnack(
                message = "Timer data is not available."
            )
            return
        }

        val outlet = data.outlets.getOrNull(
            outletPosition
        ) ?: run {
            showErrorSnack(
                message = "Outlet data is not available."
            )
            return
        }

        if (!isDeviceReachable()) {
            binding.tvTimerOnlineStatus.text = "Offline"

            showErrorSnack(
                message = "Device is not reachable."
            )

            return
        }

        val nextRegime = if (outlet.isCurrentlyOn()) {
            TimerDeviceRepository.OutletRegime.OFF
        } else {
            TimerDeviceRepository.OutletRegime.ON
        }

        isQuickActionRunning = true
        isDeviceWriteRunning = true

        setOutletCardsEnabled(
            enabled = false
        )

        showGlobalLoading()

        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                repository.updateOutletRegime(
                    ipAddress = deviceIp,
                    outletIndex = outlet.index,
                    regime = nextRegime
                )

                repository.fetchTimerDashboardData(
                    ipAddress = deviceIp
                )
            }

            if (_binding == null) {
                isDeviceWriteRunning = false
                isQuickActionRunning = false

                hideGlobalLoading()
                return@launch
            }

            result.onSuccess { refreshedData ->
                isDeviceWriteRunning = false
                isQuickActionRunning = false

                hideGlobalLoading()

                setOutletCardsEnabled(
                    enabled = true
                )

                latestDashboardData = refreshedData

                binding.tvTimerOnlineStatus.text = "Online"

                renderer?.render(
                    data = refreshedData
                )
            }.onFailure {
                isDeviceWriteRunning = false
                isQuickActionRunning = false

                hideGlobalLoading()

                setOutletCardsEnabled(
                    enabled = true
                )

                binding.tvTimerOnlineStatus.text = "Offline"

                showErrorSnack(
                    message = "Outlet command could not be sent."
                )
            }
        }
    }

    private fun setOutletCardsEnabled(
        enabled: Boolean
    ) {
        if (_binding == null) {
            return
        }

        binding.cardOutlet1.isEnabled = enabled
        binding.cardOutlet2.isEnabled = enabled
        binding.cardOutlet3.isEnabled = enabled
        binding.cardOutlet4.isEnabled = enabled

        binding.cardOutlet1Power.isEnabled = enabled
        binding.cardOutlet2Power.isEnabled = enabled
        binding.cardOutlet3Power.isEnabled = enabled
        binding.cardOutlet4Power.isEnabled = enabled
    }

    private fun durationToMinutes(
        value: String?
    ): Int {
        if (value.isNullOrBlank()) {
            return 0
        }

        val parts = value.trim()
            .split(":")
            .mapNotNull { part ->
                part.toIntOrNull()
            }

        if (parts.isEmpty()) {
            return 0
        }

        val totalSeconds = when (parts.size) {
            3 -> {
                parts[0] * 3600 +
                    parts[1] * 60 +
                    parts[2]
            }

            2 -> {
                parts[0] * 60 +
                    parts[1]
            }

            else -> {
                parts[0]
            }
        }

        if (totalSeconds <= 0) {
            return 0
        }

        return ceil(
            totalSeconds / 60.0
        ).toInt()
    }

    private fun normalizeWeekDays(
        source: List<Boolean>?
    ): List<Boolean> {
        val days = source
            ?.take(7)
            ?.toMutableList()
            ?: mutableListOf()

        while (days.size < 7) {
            days.add(
                true
            )
        }

        if (days.none { enabled ->
                enabled
            }
        ) {
            return List(
                size = 7
            ) {
                true
            }
        }

        return days
    }

    private fun isDeviceReachable(): Boolean {
        return deviceIp.isNotBlank() &&
            deviceIp != "0.0.0.0"
    }

    private fun showGlobalLoading() {
        (activity as? BaseActivity)?.showLoading(
            show = true
        )
    }

    private fun hideGlobalLoading() {
        (activity as? BaseActivity)?.showLoading(
            show = false
        )
    }

    private fun showErrorSnack(
        message: String
    ) {
        (activity as? BaseActivity)?.showSnackBar(
            message = message,
            type = BaseActivity.SnackType.ERROR
        )
    }

    override fun onDestroyView() {
        isDeviceWriteRunning = false
        isQuickActionRunning = false

        hideGlobalLoading()

        renderer = null
        latestDashboardData = null
        _binding = null

        super.onDestroyView()
    }

    companion object {
        private const val ARG_DEVICE_ID = "deviceId"
        private const val ARG_DEVICE_IP = "deviceIp"
        private const val ARG_DEVICE_TITLE = "deviceTitle"
        private const val ARG_CAN_EDIT_DEVICE_NAME = "canEditDeviceName"
        private const val ARG_USER_DEVICE_NAME = "userDeviceName"
        private const val ARG_DEFAULT_DEVICE_TITLE = "defaultDeviceTitle"

        private const val DASHBOARD_REFRESH_INTERVAL_MS = 1000L

        fun newInstance(
            deviceId: Long,
            deviceIp: String,
            deviceTitle: String,
            canEditDeviceName: Boolean,
            userDeviceName: String,
            defaultDeviceTitle: String
        ): DeviceTimerFragment {
            return DeviceTimerFragment().apply {
                arguments = Bundle().apply {
                    putLong(
                        ARG_DEVICE_ID,
                        deviceId
                    )

                    putString(
                        ARG_DEVICE_IP,
                        deviceIp
                    )

                    putString(
                        ARG_DEVICE_TITLE,
                        deviceTitle
                    )

                    putBoolean(
                        ARG_CAN_EDIT_DEVICE_NAME,
                        canEditDeviceName
                    )

                    putString(
                        ARG_USER_DEVICE_NAME,
                        userDeviceName
                    )

                    putString(
                        ARG_DEFAULT_DEVICE_TITLE,
                        defaultDeviceTitle
                    )
                }
            }
        }

        fun newInstance(
            deviceId: Long
        ): DeviceTimerFragment {
            return newInstance(
                deviceId = deviceId,
                deviceIp = "",
                deviceTitle = "",
                canEditDeviceName = false,
                userDeviceName = "",
                defaultDeviceTitle = ""
            )
        }
    }
}