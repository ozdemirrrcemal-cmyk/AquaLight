package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.devices.dosing.DosingChannelSettingsDataStoreManager
import com.aqua.aqualight.data.devices.dosing.DosingChannelSettingsUi
import com.aqua.aqualight.data.devices.dosing.EspDosingCalibrationStateClient
import com.aqua.aqualight.data.devices.dosing.esp.DosingEspRepository
import com.aqua.aqualight.data.devices.dosing.esp.DosingEspState
import com.aqua.aqualight.data.devices.dosing.esp.DosingEspTimerState
import com.aqua.aqualight.data.devices.dosing.esp.DosingScheduleMode
import com.aqua.aqualight.databinding.FragmentDeviceDosingBinding
import com.aqua.aqualight.databinding.ItemDosingChannelCardBinding
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

class DeviceDosingFragment :
    Fragment(R.layout.fragment_device_dosing) {

    private var _binding: FragmentDeviceDosingBinding? = null
    private val binding get() = _binding!!

    private lateinit var channelSettingsDataStoreManager: DosingChannelSettingsDataStoreManager
    private lateinit var dosingEspRepository: DosingEspRepository

    private var navigationInProgress: Boolean = false

    private val runningPumpIndexes: MutableSet<Int> =
        mutableSetOf()

    private val deviceId: Long
        get() = requireArguments().getLong(ARG_DEVICE_ID)

    private val deviceIp: String
        get() = requireArguments().getString(ARG_DEVICE_IP).orEmpty()

    private val deviceTitle: String
        get() = requireArguments().getString(ARG_DEVICE_TITLE).orEmpty()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentDeviceDosingBinding.bind(
                view
            )

        channelSettingsDataStoreManager =
            DosingChannelSettingsDataStoreManager(
                context = requireContext()
            )

        dosingEspRepository =
            DosingEspRepository()

        bindDefaultChannelCards()
        observeChannelCards()
        bindClicks()
        renderPumpRunningIndicators()
    }

    private fun bindDefaultChannelCards() {
        bindEmptyChannelCard(
            cardBinding = binding.channelCard1,
            channelNumber = 1,
            channelName = "Channel 1"
        )

        bindEmptyChannelCard(
            cardBinding = binding.channelCard2,
            channelNumber = 2,
            channelName = "Channel 2"
        )

        bindEmptyChannelCard(
            cardBinding = binding.channelCard3,
            channelNumber = 3,
            channelName = "Channel 3"
        )

        bindEmptyChannelCard(
            cardBinding = binding.channelCard4,
            channelNumber = 4,
            channelName = "Channel 4"
        )
    }

    private fun bindEmptyChannelCard(
        cardBinding: ItemDosingChannelCardBinding,
        channelNumber: Int,
        channelName: String
    ) {
        cardBinding.tvChannelNumber.text =
            channelNumber.toString()

        cardBinding.tvChannelName.text =
            channelName

        cardBinding.tvChannelState.text =
            "Set up"

        cardBinding.tvChannelHint.text =
            "Tap to configure this channel"

        cardBinding.tvChannelHint.visibility =
            View.VISIBLE

        cardBinding.tvChannelDose.text =
            "0 ml"

        cardBinding.tvChannelSchedule.text =
            "Every day"

        cardBinding.tvChannelStatus.text =
            "Not set up"

        cardBinding.tvChannelReservoir.text =
            "Reservoir not set"

        cardBinding.tvChannelReservoir.setOnClickListener(
            null
        )

        cardBinding.tvChannelReservoir.isClickable =
            false

        cardBinding.tvChannelReservoir.isFocusable =
            false

        cardBinding.tvChannelProgressTitle.text =
            "Today"

        cardBinding.tvChannelProgressValue.text =
            "0 / 0 ml"

        cardBinding.progressChannelDose.max =
            TODAY_PROGRESS_MAX

        cardBinding.progressChannelDose.progress =
            0

        cardBinding.progressChannelDose.visibility =
            View.VISIBLE

        cardBinding.channelMetricsContainer.visibility =
            View.GONE

        cardBinding.channelProgressSection.visibility =
            View.GONE

        cardBinding.channelProgressBarRow.visibility =
            View.GONE

        cardBinding.btnChannelQuickDose.visibility =
            View.GONE

        cardBinding.btnChannelQuickDose.setOnClickListener(
            null
        )
    }

    private fun observeChannelCards() {
        val channelCards =
            listOf(
                binding.channelCard1,
                binding.channelCard2,
                binding.channelCard3,
                binding.channelCard4
            )

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                channelCards.forEachIndexed { channelIndex, cardBinding ->
                    launch {
                        channelSettingsDataStoreManager.observeChannelSettings(
                            deviceId = deviceId,
                            channelIndex = channelIndex
                        ).collect { settings ->
                            renderChannelFromSources(
                                cardBinding = cardBinding,
                                settings = settings
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun renderChannelFromSources(
        cardBinding: ItemDosingChannelCardBinding,
        settings: DosingChannelSettingsUi
    ) {
        if (deviceIp.isBlank()) {
            renderDeviceDataUnavailable(
                cardBinding = cardBinding,
                settings = settings
            )

            return
        }

        val stateResult =
            runCatching {
                dosingEspRepository.fetchDosingState(
                    deviceIp = deviceIp,
                    channelIndex = settings.channelIndex
                )
            }

        if (_binding == null) {
            return
        }

        stateResult.onSuccess { state ->
            renderConfiguredChannelCard(
                cardBinding = cardBinding,
                settings = settings,
                state = state
            )
        }.onFailure {
            renderDeviceDataUnavailable(
                cardBinding = cardBinding,
                settings = settings
            )
        }
    }

    private fun renderConfiguredChannelCard(
        cardBinding: ItemDosingChannelCardBinding,
        settings: DosingChannelSettingsUi,
        state: DosingEspState
    ) {
        val channelName =
            state.channel.name
                .trim()
                .takeIf { name ->
                    name.isNotBlank() &&
                        name != "-"
                } ?: "Channel ${settings.channelIndex + 1}"

        val dailyDoseMl =
            state.configuredDailyDoseMl
                ?.coerceAtLeast(
                    minimumValue = 0f
                ) ?: 0f

        val hasSchedule =
            dailyDoseMl > 0f

        val hasAnyVisibleData =
            state.channel.isCalibrated ||
                hasSchedule ||
                settings.hasReservoirCapacity

        cardBinding.tvChannelName.text =
            channelName

        cardBinding.tvChannelState.text =
            when {
                !state.channel.isCalibrated -> {
                    "Calibrate"
                }

                hasSchedule && state.scheduleEnabled -> {
                    "Active"
                }

                hasSchedule && !state.scheduleEnabled -> {
                    "Paused"
                }

                else -> {
                    "Set up"
                }
            }

        cardBinding.tvChannelHint.visibility =
            if (hasAnyVisibleData) {
                View.GONE
            } else {
                View.VISIBLE
            }

        cardBinding.channelMetricsContainer.visibility =
            if (hasAnyVisibleData) {
                View.VISIBLE
            } else {
                View.GONE
            }

        cardBinding.tvChannelDose.text =
            formatMl(
                value = dailyDoseMl
            )

        cardBinding.tvChannelSchedule.text =
            formatScheduleText(
                state = state
            )

        cardBinding.tvChannelStatus.text =
            formatModeStatusText(
                state = state
            )

        renderReservoirText(
            cardBinding = cardBinding,
            settings = settings,
            state = state,
            dailyDoseMl = dailyDoseMl
        )

        renderTodayProgress(
            cardBinding = cardBinding,
            dailyDoseMl = dailyDoseMl,
            manualTodayMl = 0f,
            autoTodayMl = 0f
        )
    }

    private fun renderReservoirText(
        cardBinding: ItemDosingChannelCardBinding,
        settings: DosingChannelSettingsUi,
        state: DosingEspState,
        dailyDoseMl: Float
    ) {
        clearReservoirClick(
            cardBinding = cardBinding
        )

        when {
            !settings.reservoirTrackingEnabled -> {
                cardBinding.tvChannelReservoir.text =
                    "Tracking off"
            }

            !settings.hasReservoirCapacity -> {
                cardBinding.tvChannelReservoir.text =
                    "Reservoir not set"
            }

            state.channel.restMl == null -> {
                cardBinding.tvChannelReservoir.text =
                    "Rest unavailable"
            }

            state.channel.restMl <= 0f -> {
                renderReservoirRefillAction(
                    cardBinding = cardBinding,
                    settings = settings
                )
            }

            else -> {
                cardBinding.tvChannelReservoir.text =
                    formatReservoirSummary(
                        restMl = state.channel.restMl,
                        dailyDoseMl = dailyDoseMl
                    )
            }
        }
    }

    private fun renderReservoirRefillAction(
        cardBinding: ItemDosingChannelCardBinding,
        settings: DosingChannelSettingsUi
    ) {
        cardBinding.tvChannelReservoir.text =
            "Refill"

        cardBinding.tvChannelReservoir.isClickable =
            true

        cardBinding.tvChannelReservoir.isFocusable =
            true

        cardBinding.tvChannelReservoir.setOnClickListener {
            refillReservoir(
                cardBinding = cardBinding,
                settings = settings
            )
        }
    }

    private fun clearReservoirClick(
        cardBinding: ItemDosingChannelCardBinding
    ) {
        cardBinding.tvChannelReservoir.setOnClickListener(
            null
        )

        cardBinding.tvChannelReservoir.isClickable =
            false

        cardBinding.tvChannelReservoir.isFocusable =
            false
    }

    private fun renderTodayProgress(
        cardBinding: ItemDosingChannelCardBinding,
        dailyDoseMl: Float,
        manualTodayMl: Float,
        autoTodayMl: Float
    ) {
        if (dailyDoseMl <= 0f) {
            cardBinding.channelProgressSection.visibility =
                View.GONE

            cardBinding.channelProgressBarRow.visibility =
                View.GONE

            cardBinding.btnChannelQuickDose.visibility =
                View.GONE

            cardBinding.btnChannelQuickDose.setOnClickListener(
                null
            )

            return
        }

        val givenTodayMl =
            (manualTodayMl + autoTodayMl).coerceAtLeast(
                minimumValue = 0f
            )

        val progressPercent =
            calculateTodayProgressPercent(
                givenTodayMl = givenTodayMl,
                dailyDoseMl = dailyDoseMl
            )

        cardBinding.channelProgressSection.visibility =
            View.VISIBLE

        cardBinding.channelProgressBarRow.visibility =
            View.VISIBLE

        cardBinding.progressChannelDose.visibility =
            View.VISIBLE

        cardBinding.tvChannelProgressTitle.text =
            "Today"

        cardBinding.tvChannelProgressValue.text =
            "${formatMl(givenTodayMl)} / ${formatMl(dailyDoseMl)}"

        cardBinding.progressChannelDose.max =
            TODAY_PROGRESS_MAX

        cardBinding.progressChannelDose.progress =
            progressPercent

        cardBinding.btnChannelQuickDose.visibility =
            View.GONE

        cardBinding.btnChannelQuickDose.setOnClickListener(
            null
        )
    }

    private fun renderDeviceDataUnavailable(
        cardBinding: ItemDosingChannelCardBinding,
        settings: DosingChannelSettingsUi
    ) {
        cardBinding.tvChannelState.text =
            "Offline"

        cardBinding.tvChannelHint.text =
            "Device data could not be loaded"

        cardBinding.tvChannelHint.visibility =
            View.VISIBLE

        cardBinding.channelMetricsContainer.visibility =
            if (settings.hasReservoirCapacity) {
                View.VISIBLE
            } else {
                View.GONE
            }

        cardBinding.tvChannelReservoir.text =
            if (settings.hasReservoirCapacity) {
                "Rest unavailable"
            } else {
                "Reservoir not set"
            }

        clearReservoirClick(
            cardBinding = cardBinding
        )

        cardBinding.channelProgressSection.visibility =
            View.GONE

        cardBinding.channelProgressBarRow.visibility =
            View.GONE

        cardBinding.btnChannelQuickDose.visibility =
            View.GONE

        cardBinding.btnChannelQuickDose.setOnClickListener(
            null
        )
    }

    private fun refillReservoir(
        cardBinding: ItemDosingChannelCardBinding,
        settings: DosingChannelSettingsUi
    ) {
        val capacityMl =
            settings.containerVolumeMl
                ?.takeIf { value ->
                    value > 0f
                }

        if (capacityMl == null) {
            showSnackBar(
                message = "Container volume is not set.",
                type = BaseActivity.SnackType.WARNING
            )

            return
        }

        if (deviceIp.isBlank()) {
            showSnackBar(
                message = "Device IP address is missing.",
                type = BaseActivity.SnackType.WARNING
            )

            return
        }

        cardBinding.tvChannelReservoir.text =
            "Refilling..."

        clearReservoirClick(
            cardBinding = cardBinding
        )

        viewLifecycleOwner.lifecycleScope.launch {
            val result =
                runCatching {
                    dosingEspRepository.refillChannelReservoir(
                        deviceIp = deviceIp,
                        channelIndex = settings.channelIndex,
                        capacityMl = capacityMl
                    )
                }

            if (_binding == null) {
                return@launch
            }

            result.onSuccess {
                showSnackBar(
                    message = "Reservoir refilled.",
                    type = BaseActivity.SnackType.NORMAL
                )

                renderChannelFromSources(
                    cardBinding = cardBinding,
                    settings = settings
                )
            }.onFailure {
                cardBinding.tvChannelReservoir.text =
                    "Refill"

                renderReservoirRefillAction(
                    cardBinding = cardBinding,
                    settings = settings
                )

                showSnackBar(
                    message = "Reservoir could not be refilled.",
                    type = BaseActivity.SnackType.WARNING
                )
            }
        }
    }

    private fun formatScheduleText(
        state: DosingEspState
    ): String {
        val timer =
            findPrimaryDisplayTimer(
                state = state
            )

        val weekDays =
            timer?.weekDays
                ?.takeIf { days ->
                    days.size == 7
                } ?: List(
                size = 7
            ) {
                true
            }

        if (weekDays.all { selected -> selected }) {
            return "Every day"
        }

        val dayNames =
            listOf(
                "Mon",
                "Tue",
                "Wed",
                "Thu",
                "Fri",
                "Sat",
                "Sun"
            )

        val selectedDays =
            weekDays.mapIndexedNotNull { index, selected ->
                if (selected) {
                    dayNames[index]
                } else {
                    null
                }
            }

        return if (selectedDays.isEmpty()) {
            "No days"
        } else {
            selectedDays.joinToString(
                separator = ", "
            )
        }
    }

    private fun formatModeStatusText(
        state: DosingEspState
    ): String {
        val timers =
            findDoseTimers(
                state = state
            )

        if (timers.isEmpty()) {
            return "Not set up"
        }

        val primaryTimer =
            findPrimaryDisplayTimer(
                state = state
            )

        val totalDoseCount =
            timers.sumOf { timer ->
                timer.count.coerceAtLeast(
                    minimumValue = 0
                )
            }.coerceAtLeast(
                minimumValue = 1
            )

        val progressText =
            primaryTimer
                ?.status
                ?.trim()
                ?.takeIf { status ->
                    status.isNotBlank() &&
                        status != "-"
                } ?: "${totalDoseCount}x"

        return "$progressText ${formatModeLabel(state.activeMode)}"
    }

    private fun formatModeLabel(
        mode: DosingScheduleMode
    ): String {
        return when (mode) {
            DosingScheduleMode.SINGLE -> {
                "Single"
            }

            DosingScheduleMode.HOURLY_24 -> {
                "/24 hourly"
            }

            DosingScheduleMode.CUSTOM_PERIODS -> {
                "Custom"
            }

            DosingScheduleMode.TIMER -> {
                "Timer"
            }
        }
    }

    private fun findPrimaryDisplayTimer(
        state: DosingEspState
    ): DosingEspTimerState? {
        return findDoseTimers(
            state = state
        ).firstOrNull()
            ?: state.timer.takeIf { timer ->
                timer.count > 0 ||
                    timer.dosePerRunMl > 0f
            }
    }

    private fun findDoseTimers(
        state: DosingEspState
    ): List<DosingEspTimerState> {
        return state.channelTimers
            .filter { timer ->
                timer.dosePerRunMl > 0f &&
                    timer.count > 0
            }
            .sortedBy { timer ->
                timer.index
            }
    }

    private fun formatReservoirSummary(
        restMl: Float,
        dailyDoseMl: Float
    ): String {
        if (dailyDoseMl <= 0f) {
            return "${formatMl(restMl)} left"
        }

        val daysLeft =
            floor(
                restMl / dailyDoseMl
            ).toInt()
                .coerceAtLeast(
                    minimumValue = 0
                )

        return "$daysLeft days   ${formatMl(restMl)}"
    }

    private fun calculateTodayProgressPercent(
        givenTodayMl: Float,
        dailyDoseMl: Float
    ): Int {
        if (dailyDoseMl <= 0f) {
            return 0
        }

        return (
            givenTodayMl.coerceIn(
                minimumValue = 0f,
                maximumValue = dailyDoseMl
            ) / dailyDoseMl * TODAY_PROGRESS_MAX
            ).roundToInt()
            .coerceIn(
                minimumValue = 0,
                maximumValue = TODAY_PROGRESS_MAX
            )
    }

    private fun formatMl(
        value: Float
    ): String {
        val safeValue =
            value.coerceAtLeast(
                minimumValue = 0f
            )

        val amount =
            if (safeValue % 1f == 0f) {
                safeValue.toInt().toString()
            } else {
                String.format(
                    Locale.US,
                    "%.2f",
                    safeValue
                ).trimEnd(
                    '0'
                ).trimEnd(
                    '.'
                )
            }

        return "$amount ml"
    }

    private fun bindClicks() {
        binding.hotspotPump1.setOnClickListener {
            handlePumpClick(
                pumpIndex = 0
            )
        }

        binding.hotspotPump2.setOnClickListener {
            handlePumpClick(
                pumpIndex = 1
            )
        }

        binding.hotspotPump3.setOnClickListener {
            handlePumpClick(
                pumpIndex = 2
            )
        }

        binding.hotspotPump4.setOnClickListener {
            handlePumpClick(
                pumpIndex = 3
            )
        }

        binding.channelCard1.cardChannelRoot.setOnClickListener {
            handlePumpClick(
                pumpIndex = 0
            )
        }

        binding.channelCard2.cardChannelRoot.setOnClickListener {
            handlePumpClick(
                pumpIndex = 1
            )
        }

        binding.channelCard3.cardChannelRoot.setOnClickListener {
            handlePumpClick(
                pumpIndex = 2
            )
        }

        binding.channelCard4.cardChannelRoot.setOnClickListener {
            handlePumpClick(
                pumpIndex = 3
            )
        }
    }

    private fun handlePumpClick(
        pumpIndex: Int
    ) {
        if (navigationInProgress) {
            return
        }

        val safePumpIndex =
            pumpIndex.coerceIn(
                minimumValue = 0,
                maximumValue = 3
            )

        routeChannelByCalibrationState(
            channelIndex = safePumpIndex
        )
    }

    private fun routeChannelByCalibrationState(
        channelIndex: Int
    ) {
        navigationInProgress =
            true

        viewLifecycleOwner.lifecycleScope.launch {
            val calibrationState =
                runCatching {
                    EspDosingCalibrationStateClient.readChannelCalibrationState(
                        deviceIp = deviceIp,
                        channelIndex = channelIndex
                    )
                }.getOrNull()

            navigationInProgress =
                false

            if (!isAdded || _binding == null) {
                return@launch
            }

            when {
                calibrationState == null -> {
                    showSnackBar(
                        message = "Calibration state could not be read. Opening settings.",
                        type = BaseActivity.SnackType.WARNING
                    )

                    openSelectedPumpSettings(
                        channelIndex = channelIndex
                    )
                }

                calibrationState.calibratedOnDevice -> {
                    openSelectedPumpSettings(
                        channelIndex = channelIndex
                    )
                }

                else -> {
                    openSelectedPumpCalibration(
                        channelIndex = channelIndex
                    )
                }
            }
        }
    }

    private fun renderPumpRunningIndicators() {
        binding.indicatorPump1.visibility =
            if (runningPumpIndexes.contains(0)) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.indicatorPump2.visibility =
            if (runningPumpIndexes.contains(1)) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.indicatorPump3.visibility =
            if (runningPumpIndexes.contains(2)) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.indicatorPump4.visibility =
            if (runningPumpIndexes.contains(3)) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun openSelectedPumpSettings(
        channelIndex: Int
    ) {
        findNavController().navigate(
            R.id.action_deviceMenuFragment_to_deviceDosingChannelSettingsFragment,
            createChannelBundle(
                channelIndex = channelIndex
            )
        )
    }

    private fun openSelectedPumpCalibration(
        channelIndex: Int
    ) {
        findNavController().navigate(
            R.id.action_deviceMenuFragment_to_deviceDosingCalibrationFragment,
            createChannelBundle(
                channelIndex = channelIndex
            )
        )
    }

    private fun createChannelBundle(
        channelIndex: Int
    ): Bundle {
        return bundleOf(
            ARG_DEVICE_ID to deviceId,
            ARG_DEVICE_IP to deviceIp,
            ARG_DEVICE_TITLE to deviceTitle,
            ARG_CHANNEL_INDEX to channelIndex
        )
    }

    private fun showSnackBar(
        message: String,
        type: BaseActivity.SnackType = BaseActivity.SnackType.NORMAL
    ) {
        (activity as? BaseActivity)?.showSnackBar(
            message = message,
            type = type
        )
    }

    override fun onDestroyView() {
        navigationInProgress =
            false

        _binding =
            null

        super.onDestroyView()
    }

    companion object {
        private const val ARG_DEVICE_ID = "deviceId"
        private const val ARG_DEVICE_IP = "deviceIp"
        private const val ARG_DEVICE_TITLE = "deviceTitle"
        private const val ARG_CAN_EDIT_DEVICE_NAME = "canEditDeviceName"
        private const val ARG_USER_DEVICE_NAME = "userDeviceName"
        private const val ARG_DEFAULT_DEVICE_TITLE = "defaultDeviceTitle"
        private const val ARG_CHANNEL_INDEX = "channelIndex"

        private const val TODAY_PROGRESS_MAX = 100

        fun newInstance(
            deviceId: Long,
            deviceIp: String,
            deviceTitle: String,
            canEditDeviceName: Boolean,
            userDeviceName: String,
            defaultDeviceTitle: String
        ): DeviceDosingFragment {
            return DeviceDosingFragment().apply {
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
    }
}