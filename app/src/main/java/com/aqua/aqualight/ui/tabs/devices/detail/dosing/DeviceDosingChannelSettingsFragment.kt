package com.aqua.aqualight.ui.tabs.devices.detail.dosing

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.data.devices.dosing.DosingCalibrationDataStoreManager
import com.aqua.aqualight.data.devices.dosing.DosingChannelSettingsDataStoreManager
import com.aqua.aqualight.databinding.FragmentDeviceDosingChannelSettingsBinding
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.bottomsheet.DosingBottomSheets
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import com.aqua.aqualight.data.devices.dosing.esp.DosingEspRepository
import com.aqua.aqualight.data.devices.dosing.esp.DosingEspState
import com.aqua.aqualight.data.devices.dosing.esp.DosingScheduleMode

class DeviceDosingChannelSettingsFragment :
Fragment(R.layout.fragment_device_dosing_channel_settings) {

    private var _binding: FragmentDeviceDosingChannelSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var calibrationDataStoreManager: DosingCalibrationDataStoreManager

    private lateinit var channelSettingsDataStoreManager: DosingChannelSettingsDataStoreManager

    private lateinit var dosingEspRepository: DosingEspRepository

    private var espDosingState: DosingEspState? =
    null

    private var selectedMode: DosingMode =
    DosingMode.SINGLE

    private var scheduleEnabled: Boolean =
    true

    private var savedScheduleEnabled: Boolean =
    true

    private var dailyDoseMl: Float? =
    0f

    private var savedDailyDoseMl: Float? =
    0f

    private var selectedWeekDays: List<Boolean> =
    List(
        size = 7
    ) {
        true
    }

    private var savedWeekDays: List<Boolean> =
    List(
        size = 7
    ) {
        true
    }

    private var reservoirTrackingEnabled: Boolean =
    true

    private var savedReservoirTrackingEnabled: Boolean =
    true

    private var missedDoseCompensationEnabled: Boolean =
    true

    private var savedMissedDoseCompensationEnabled: Boolean =
    true

    private var containerVolumeMl: Float? =
    null

    private var savedContainerVolumeMl: Float? =
    null

    private var suppressScheduleCallback: Boolean =
    false

    private var suppressReservoirTrackingCallback: Boolean =
    false

    private var suppressMissedDoseCompensationCallback: Boolean =
    false

    private var saveSettingsInProgress: Boolean =
    false

    private val hasUnsavedDataStoreSettings: Boolean
    get() =
    reservoirTrackingEnabled != savedReservoirTrackingEnabled ||
    missedDoseCompensationEnabled != savedMissedDoseCompensationEnabled ||
    !areFloatValuesSame(
        currentValue = containerVolumeMl,
        savedValue = savedContainerVolumeMl
    )

    private val hasUnsavedEspTimerSettings: Boolean
    get() =
    scheduleEnabled != savedScheduleEnabled ||
    !areFloatValuesSame(
        currentValue = dailyDoseMl,
        savedValue = savedDailyDoseMl
    ) ||
    selectedWeekDays != savedWeekDays

    private val hasUnsavedChannelSettings: Boolean
    get() =
    hasUnsavedDataStoreSettings || hasUnsavedEspTimerSettings

    private val channelIndex: Int
    get() = requireArguments().getInt(
        ARG_CHANNEL_INDEX,
        0
    ).coerceIn(
        minimumValue = 0,
        maximumValue = 3
    )

    private val channelNumber: Int
    get() = channelIndex + 1

    private val deviceId: Long
    get() = requireArguments().getLong(
        ARG_DEVICE_ID
    )

    private val deviceIp: String
    get() = requireArguments().getString(
        ARG_DEVICE_IP
    ).orEmpty()

    private val deviceTitle: String
    get() = requireArguments().getString(
        ARG_DEVICE_TITLE
    ).orEmpty()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
        FragmentDeviceDosingChannelSettingsBinding.bind(
            view
        )

        calibrationDataStoreManager =
        DosingCalibrationDataStoreManager(
            context = requireContext()
        )

        channelSettingsDataStoreManager =
        DosingChannelSettingsDataStoreManager(
            context = requireContext()
        )

        dosingEspRepository =
        DosingEspRepository()

        bindHeaderActions()
        bindStaticPreview()
        bindCalibrationState()
        bindSelectedPumpIndicator()
        syncInitialChannelSettingsFromUi()
        bindLocalChannelSettings()
        bindClicks()

        selectDosingMode(
            mode = selectedMode
        )

        renderWeekDays(
            weekDays = selectedWeekDays
        )

        updateScheduleEnabledState(
            enabled = scheduleEnabled
        )

        renderTopBarSaveState()
        fetchDosingStateFromEsp()
    }

    private fun bindHeaderActions() {
        renderChannelTitle()

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnSaveSettings.setOnClickListener {
            saveChannelSettingsIfNeeded()
        }

        renderTopBarSaveState()
    }

    private fun bindStaticPreview() {
        binding.tvDailyDoseValue.text =
        "0 ml"

        binding.tvLastCalibrated.text =
        "Last calibrated: Not calibrated"

        binding.tvContainerVolumeValue.text =
        "Not set"

        makeDailyDoseCardDisplayOnly()
    }

    private fun syncInitialChannelSettingsFromUi() {
        scheduleEnabled =
        binding.switchScheduleEnabled.isChecked

        savedScheduleEnabled =
        scheduleEnabled

        missedDoseCompensationEnabled =
        binding.switchMissedDoseCompensation.isChecked

        savedMissedDoseCompensationEnabled =
        missedDoseCompensationEnabled

        dailyDoseMl =
        readDailyDoseFromText()

        savedDailyDoseMl =
        dailyDoseMl

        selectedWeekDays =
        List(
            size = 7
        ) {
            true
        }

        savedWeekDays =
        selectedWeekDays
    }

    private fun makeDailyDoseCardDisplayOnly() {
        binding.cardDailyDose.setOnClickListener(
            null
        )

        binding.cardDailyDose.isClickable =
        false

        binding.cardDailyDose.isFocusable =
        false
    }

    private fun fetchDosingStateFromEsp() {
        if (deviceIp.isBlank()) {
            showSnackBar(
                message = "Device IP address is missing.",
                type = BaseActivity.SnackType.WARNING
            )

            return
        }

        val baseActivity =
        activity as? BaseActivity

        baseActivity?.showLoading(
            true
        )

        viewLifecycleOwner.lifecycleScope.launch {
            val result =
            runCatching {
                dosingEspRepository.fetchDosingState(
                    deviceIp = deviceIp,
                    channelIndex = channelIndex
                )
            }

            baseActivity?.showLoading(
                false
            )

            if (_binding == null) {
                return@launch
            }

            result.onSuccess {
                state ->
                applyEspDosingState(
                    state = state
                )
            }.onFailure {
                DialogManager.showConfirmDialog(
                    context = requireContext(),
                    type = DialogType.ERROR,
                    title = "Device Data Failed",
                    message = "Dosing data could not be loaded from the device. Please check the device connection and try again.",
                    onConfirm = {
                        fetchDosingStateFromEsp()
                    }
                )
            }
        }
    }

    private fun applyEspDosingState(
        state: DosingEspState
    ) {
        espDosingState =
        state

        renderChannelTitle(
            name = state.channel.name
        )

        scheduleEnabled =
        state.timer.enabled

        savedScheduleEnabled =
        state.timer.enabled

        dailyDoseMl =
        state.configuredDailyDoseMl

        savedDailyDoseMl =
        state.configuredDailyDoseMl

        selectedWeekDays =
        state.timer.weekDays

        savedWeekDays =
        state.timer.weekDays

        suppressScheduleCallback =
        true

        binding.switchScheduleEnabled.isChecked =
        state.timer.enabled

        suppressScheduleCallback =
        false

        renderDailyDoseValue(
            value = state.configuredDailyDoseMl
        )

        renderWeekDays(
            weekDays = state.timer.weekDays
        )

        selectDosingMode(
            mode = mapEspModeToUiMode(
                mode = state.activeMode
            )
        )

        updateScheduleEnabledState(
            enabled = state.timer.enabled
        )

        renderTopBarSaveState()
    }

    private fun mapEspModeToUiMode(
        mode: DosingScheduleMode
    ): DosingMode {
        return when (mode) {
            DosingScheduleMode.SINGLE -> {
                DosingMode.SINGLE
            }

            DosingScheduleMode.HOURLY_24 -> {
                DosingMode.HOURLY_24
            }

            DosingScheduleMode.CUSTOM_PERIODS -> {
                DosingMode.CUSTOM_PERIODS
            }

            DosingScheduleMode.TIMER -> {
                DosingMode.TIMER
            }
        }
    }

    private fun renderDailyDoseValue(
        value: Float?
    ) {
        binding.tvDailyDoseValue.text =
        value?.let {
            dose ->
            "${formatDoseMl(dose)} ml"
        } ?: "0 ml"
    }

    private fun formatDoseMl(
        value: Float
    ): String {
        return if (value % 1f == 0f) {
            value.toInt().toString()
        } else {
            String.format(
                Locale.US,
                "%.2f",
                value
            ).trimEnd(
                '0'
            ).trimEnd(
                '.'
            )
        }
    }

    private fun bindLocalChannelSettings() {
        binding.switchReservoirTracking.setOnCheckedChangeListener {
            _, isChecked ->
            if (suppressReservoirTrackingCallback) {
                return@setOnCheckedChangeListener
            }

            reservoirTrackingEnabled =
            isChecked

            renderReservoirTrackingState(
                enabled = isChecked
            )

            renderTopBarSaveState()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                channelSettingsDataStoreManager.observeChannelSettings(
                    deviceId = deviceId,
                    channelIndex = channelIndex
                ).collect {
                    settings ->
                    val hadUnsavedChanges =
                    hasUnsavedChannelSettings || saveSettingsInProgress

                    savedReservoirTrackingEnabled =
                    settings.reservoirTrackingEnabled

                    savedContainerVolumeMl =
                    settings.containerVolumeMl

                    savedMissedDoseCompensationEnabled =
                    settings.missedDoseCompensationEnabled

                    if (!hadUnsavedChanges) {
                        reservoirTrackingEnabled =
                        settings.reservoirTrackingEnabled

                        containerVolumeMl =
                        settings.containerVolumeMl

                        missedDoseCompensationEnabled =
                        settings.missedDoseCompensationEnabled

                        suppressReservoirTrackingCallback =
                        true

                        binding.switchReservoirTracking.isChecked =
                        settings.reservoirTrackingEnabled

                        suppressReservoirTrackingCallback =
                        false

                        suppressMissedDoseCompensationCallback =
                        true

                        binding.switchMissedDoseCompensation.isChecked =
                        settings.missedDoseCompensationEnabled

                        suppressMissedDoseCompensationCallback =
                        false

                        renderReservoirTrackingState(
                            enabled = settings.reservoirTrackingEnabled
                        )
                    }

                    renderContainerVolumeValue(
                        value = if (hadUnsavedChanges) {
                            containerVolumeMl
                        } else {
                            settings.containerVolumeMl
                        }
                    )

                    renderTopBarSaveState()
                }
            }
        }
    }

    private fun saveChannelSettingsIfNeeded() {
        if (
            saveSettingsInProgress ||
            !hasUnsavedChannelSettings
        ) {
            return
        }

        val hadDataStoreChanges =
        hasUnsavedDataStoreSettings

        val hadEspTimerChanges =
        hasUnsavedEspTimerSettings

        val baseActivity =
        activity as? BaseActivity

        saveSettingsInProgress =
        true

        renderTopBarSaveState()

        baseActivity?.showLoading(
            true
        )

        viewLifecycleOwner.lifecycleScope.launch {
            val result =
            runCatching {
                if (hadDataStoreChanges) {
                    channelSettingsDataStoreManager.saveLocalChannelSettings(
                        deviceId = deviceId,
                        channelIndex = channelIndex,
                        reservoirTrackingEnabled = reservoirTrackingEnabled,
                        containerVolumeMl = containerVolumeMl,
                        missedDoseCompensationEnabled = missedDoseCompensationEnabled
                    )
                }

                if (hadEspTimerChanges) {
                    espDosingState ?: throw IllegalStateException(
                        "Device schedule data is not loaded yet."
                    )

                    dosingEspRepository.updateTimerEnabledAndWeekDays(
                        deviceIp = deviceIp,
                        channelIndex = channelIndex,
                        enabled = scheduleEnabled,
                        weekDays = selectedWeekDays
                    )
                }
            }

            baseActivity?.showLoading(
                false
            )

            if (_binding == null) {
                return@launch
            }

            saveSettingsInProgress =
            false

            result.onSuccess {
                if (hadDataStoreChanges) {
                    savedReservoirTrackingEnabled =
                    reservoirTrackingEnabled

                    savedContainerVolumeMl =
                    containerVolumeMl

                    savedMissedDoseCompensationEnabled =
                    missedDoseCompensationEnabled
                }

                if (hadEspTimerChanges) {
                    savedScheduleEnabled =
                    scheduleEnabled

                    savedDailyDoseMl =
                    dailyDoseMl

                    savedWeekDays =
                    selectedWeekDays

                    espDosingState =
                    espDosingState?.copy(
                        timer = espDosingState!!.timer.copy(
                            enabled = scheduleEnabled,
                            weekDays = selectedWeekDays
                        )
                    )
                }

            }.onFailure {
                DialogManager.showConfirmDialog(
                    context = requireContext(),
                    type = DialogType.ERROR,
                    title = "Save Failed",
                    message = "Channel settings could not be saved. Please check the device connection and try again.",
                    onConfirm = {
                        saveChannelSettingsIfNeeded()
                    }
                )
            }

            renderTopBarSaveState()
        }
    }

    private fun renderTopBarSaveState() {
        if (_binding == null) {
            return
        }

        val showSave =
        hasUnsavedChannelSettings || saveSettingsInProgress

        binding.btnSaveSettings.visibility =
        if (showSave) {
            View.VISIBLE
        } else {
            View.INVISIBLE
        }

        binding.btnSaveSettings.isEnabled =
        hasUnsavedChannelSettings && !saveSettingsInProgress

        binding.btnSaveSettings.alpha =
        if (saveSettingsInProgress) {
            0.45f
        } else {
            1f
        }
    }

    private fun readDailyDoseFromText(): Float? {
        return binding.tvDailyDoseValue.text
        ?.toString()
        ?.replace(
            oldValue = "ml",
            newValue = ""
        )
        ?.trim()
        ?.replace(
            oldValue = ",",
            newValue = "."
        )
        ?.toFloatOrNull()
    }

    private fun areFloatValuesSame(
        currentValue: Float?,
        savedValue: Float?
    ): Boolean {
        if (
            currentValue == null ||
            savedValue == null
        ) {
            return currentValue == savedValue
        }

        return abs(
            currentValue - savedValue
        ) < 0.001f
    }

    private fun renderContainerVolumeValue(
        value: Float?
    ) {
        binding.tvContainerVolumeValue.text =
        value?.let {
            volume ->
            formatContainerVolume(
                value = volume
            )
        } ?: "Not set"
    }

    private fun formatContainerVolume(
        value: Float
    ): String {
        val text =
        if (value % 1f == 0f) {
            value.toInt().toString()
        } else {
            String.format(
                Locale.US,
                "%.1f",
                value
            ).trimEnd(
                '0'
            ).trimEnd(
                '.'
            )
        }

        return "$text ml"
    }

    private fun renderReservoirTrackingState(
        enabled: Boolean
    ) {
        binding.rowContainerVolume.visibility =
        if (enabled) {
            View.VISIBLE
        } else {
            View.GONE
        }

        binding.rowContainerVolume.isEnabled =
        enabled

        binding.tvContainerVolumeValue.alpha =
        if (enabled) {
            1f
        } else {
            0.45f
        }
    }

    private fun bindCalibrationState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                calibrationDataStoreManager.observeCalibration(
                    deviceId = deviceId,
                    channelIndex = channelIndex
                ).collect {
                    calibration ->
                    binding.tvLastCalibrated.text =
                    if (calibration == null) {
                        "Last calibrated: Not calibrated"
                    } else {
                        "Last calibrated: ${
                                formatCalibrationDate(
                                    millis = calibration.lastCalibratedAtMillis
                                )
                            }"
                    }
                }
            }
        }
    }

    private fun formatCalibrationDate(
        millis: Long
    ): String {
        return SimpleDateFormat(
            "dd MMM yyyy, HH:mm",
            Locale.getDefault()
        ).format(
            Date(millis)
        )
    }

    private fun bindSelectedPumpIndicator() {
        binding.selectedIndicatorPump1.visibility =
        if (channelIndex == 0) {
            View.VISIBLE
        } else {
            View.GONE
        }

        binding.selectedIndicatorPump2.visibility =
        if (channelIndex == 1) {
            View.VISIBLE
        } else {
            View.GONE
        }

        binding.selectedIndicatorPump3.visibility =
        if (channelIndex == 2) {
            View.VISIBLE
        } else {
            View.GONE
        }

        binding.selectedIndicatorPump4.visibility =
        if (channelIndex == 3) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun bindClicks() {
        binding.switchScheduleEnabled.setOnCheckedChangeListener {
            _, isChecked ->
            if (suppressScheduleCallback) {
                return@setOnCheckedChangeListener
            }

            scheduleEnabled =
            isChecked

            updateScheduleEnabledState(
                enabled = isChecked
            )

            renderTopBarSaveState()
        }

        binding.switchMissedDoseCompensation.setOnCheckedChangeListener {
            _, isChecked ->
            if (suppressMissedDoseCompensationCallback) {
                return@setOnCheckedChangeListener
            }

            missedDoseCompensationEnabled =
            isChecked

            renderTopBarSaveState()
        }

        binding.rowModeSingle.setOnClickListener {
            selectDosingMode(
                mode = DosingMode.SINGLE
            )

            openSingleModeSettings()
        }

        binding.radioModeSingle.setOnClickListener {
            selectDosingMode(
                mode = DosingMode.SINGLE
            )

            openSingleModeSettings()
        }

        binding.rowModeHourly.setOnClickListener {
            selectDosingMode(
                mode = DosingMode.HOURLY_24
            )

            openHourly24ModeSettings()
        }

        binding.radioModeHourly.setOnClickListener {
            selectDosingMode(
                mode = DosingMode.HOURLY_24
            )

            openHourly24ModeSettings()
        }

        binding.rowModeCustomPeriods.setOnClickListener {
            selectDosingMode(
                mode = DosingMode.CUSTOM_PERIODS
            )

            openCustomPeriodsSettings()
        }

        binding.radioModeCustomPeriods.setOnClickListener {
            selectDosingMode(
                mode = DosingMode.CUSTOM_PERIODS
            )

            openCustomPeriodsSettings()
        }

        binding.rowModeTimer.setOnClickListener {
            selectDosingMode(
                mode = DosingMode.TIMER
            )

            openTimerModeSettings()
        }

        binding.radioModeTimer.setOnClickListener {
            selectDosingMode(
                mode = DosingMode.TIMER
            )

            openTimerModeSettings()
        }

        binding.rowEveryDay.setOnClickListener {
            selectEveryDay()
        }

        binding.radioEveryDay.setOnClickListener {
            selectEveryDay()
        }

        binding.rowContainerVolume.setOnClickListener {
            if (!reservoirTrackingEnabled) {
                return@setOnClickListener
            }

            DosingBottomSheets.showMlValueEditor(
                context = requireContext(),
                title = "Container Volume",
                description = "Set the total liquid volume available in this reservoir.",
                hint = "500",
                initialValue = containerVolumeMl,
                allowClear = true,
                onValidationError = {
                    message ->
                    showSnackBar(
                        message = message,
                        type = BaseActivity.SnackType.WARNING
                    )
                },
                onClear = {
                    containerVolumeMl =
                    null

                    renderContainerVolumeValue(
                        value = containerVolumeMl
                    )

                    renderTopBarSaveState()
                },
                onDone = {
                    value ->
                    containerVolumeMl =
                    value

                    renderContainerVolumeValue(
                        value = containerVolumeMl
                    )

                    renderTopBarSaveState()
                }
            )
        }

        binding.btnCalibrate.setOnClickListener {
            openCalibrationWizard()
        }

        binding.btnManualDosing.setOnClickListener {
            openManualDosingBottomSheet()
        }

        binding.btnResetChannel.setOnClickListener {
            showSnackBar(
                message = "Reset confirmation will open for Channel $channelNumber.",
                type = BaseActivity.SnackType.NORMAL
            )
        }

        bindWeekDayClicks()
    }

    private fun selectEveryDay() {
        if (!binding.rowEveryDay.isEnabled) {
            return
        }

        selectedWeekDays =
        List(
            size = 7
        ) {
            true
        }

        renderWeekDays(
            weekDays = selectedWeekDays
        )

        renderTopBarSaveState()
    }

    private fun bindWeekDayClicks() {
        val chips =
        listOf(
            binding.chipDayMon,
            binding.chipDayTue,
            binding.chipDayWed,
            binding.chipDayThu,
            binding.chipDayFri,
            binding.chipDaySat,
            binding.chipDaySun
        )

        chips.forEachIndexed {
            index, chip ->
            chip.isClickable =
            true

            chip.isFocusable =
            true

            chip.setOnClickListener {
                toggleWeekDay(
                    index = index
                )
            }
        }
    }

    private fun toggleWeekDay(
        index: Int
    ) {
        if (!binding.rowEveryDay.isEnabled) {
            return
        }

        val mutableDays =
        selectedWeekDays.toMutableList()

        mutableDays[index] =
        !mutableDays[index]

        if (mutableDays.none {
            selected -> selected
        }) {
            showSnackBar(
                message = "Please select at least one day.",
                type = BaseActivity.SnackType.WARNING
            )

            return
        }

        selectedWeekDays =
        mutableDays

        renderWeekDays(
            weekDays = selectedWeekDays
        )

        renderTopBarSaveState()
    }

    private fun renderWeekDays(
        weekDays: List<Boolean>
    ) {
        val safeWeekDays =
        if (weekDays.size == 7) {
            weekDays
        } else {
            List(
                size = 7
            ) {
                true
            }
        }

        val chips =
        listOf(
            binding.chipDayMon,
            binding.chipDayTue,
            binding.chipDayWed,
            binding.chipDayThu,
            binding.chipDayFri,
            binding.chipDaySat,
            binding.chipDaySun
        )

        chips.forEachIndexed {
            index, chip ->
            val selected =
            safeWeekDays[index]

            chip.alpha =
            if (selected) {
                1f
            } else {
                0.35f
            }

            chip.setBackgroundColor(
                Color.parseColor(
                    if (selected) {
                        "#702536"
                    } else {
                        "#24314F"
                    }
                )
            )
        }

        binding.radioEveryDay.isChecked =
        safeWeekDays.all {
            selected ->
            selected
        }
    }

    private fun openSingleModeSettings() {
        findNavController().navigate(
            R.id.action_deviceDosingChannelSettingsFragment_to_deviceDosingSingleModeSettingsFragment,
            createChannelBundle()
        )
    }

    private fun openHourly24ModeSettings() {
        findNavController().navigate(
            R.id.action_deviceDosingChannelSettingsFragment_to_deviceDosingHourly24ModeSettingsFragment,
            createChannelBundle()
        )
    }

    private fun openCustomPeriodsSettings() {
        findNavController().navigate(
            R.id.action_deviceDosingChannelSettingsFragment_to_deviceDosingCustomPeriodsSettingsFragment,
            createChannelBundle()
        )
    }

    private fun openTimerModeSettings() {
        findNavController().navigate(
            R.id.action_deviceDosingChannelSettingsFragment_to_deviceDosingTimerModeSettingsFragment,
            createChannelBundle()
        )
    }

    private fun openCalibrationWizard() {
        findNavController().navigate(
            R.id.action_deviceDosingChannelSettingsFragment_to_deviceDosingCalibrationFragment,
            createChannelBundle()
        )
    }

    private fun createChannelBundle(): Bundle {
        return Bundle().apply {
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

            putInt(
                ARG_CHANNEL_INDEX,
                channelIndex
            )
        }
    }

    private fun selectDosingMode(
        mode: DosingMode
    ) {
        selectedMode =
        mode

        binding.radioModeSingle.isChecked =
        mode == DosingMode.SINGLE

        binding.radioModeHourly.isChecked =
        mode == DosingMode.HOURLY_24

        binding.radioModeCustomPeriods.isChecked =
        mode == DosingMode.CUSTOM_PERIODS

        binding.radioModeTimer.isChecked =
        mode == DosingMode.TIMER
    }

    private fun updateScheduleEnabledState(
        enabled: Boolean
    ) {
        val contentAlpha =
        if (enabled) {
            1f
        } else {
            0.45f
        }

        binding.cardDailyDose.alpha =
        contentAlpha

        binding.cardDosingSchedule.alpha =
        contentAlpha

        binding.cardRecurrence.alpha =
        contentAlpha

        binding.cardMissedDoseCompensation.alpha =
        contentAlpha

        binding.cardDailyDose.isEnabled =
        true

        makeDailyDoseCardDisplayOnly()

        binding.rowModeSingle.isEnabled =
        enabled

        binding.rowModeHourly.isEnabled =
        enabled

        binding.rowModeCustomPeriods.isEnabled =
        enabled

        binding.rowModeTimer.isEnabled =
        enabled

        binding.rowEveryDay.isEnabled =
        enabled

        binding.switchMissedDoseCompensation.isEnabled =
        enabled
    }

    private fun openManualDosingBottomSheet() {
        DosingBottomSheets.showMlValueEditor(
            context = requireContext(),
            title = "Manual Dosing",
            description = "Enter the dose amount you want to add now for Channel $channelNumber. This action will run the pump immediately after confirmation.",
            hint = "5",
            initialValue = null,
            allowClear = false,
            confirmButtonText = "Confirm Dose",
            onValidationError = {
                message ->
                showSnackBar(
                    message = message,
                    type = BaseActivity.SnackType.WARNING
                )
            },
            onClear = {
                // Manual dosing does not use clear action.
            },
            onDone = {
                value ->
                startManualDosing(
                    doseMl = value
                )
            }
        )
    }

    private fun startManualDosing(
        doseMl: Float
    ) {
        if (doseMl <= 0f) {
            showSnackBar(
                message = "Please enter a valid dose amount.",
                type = BaseActivity.SnackType.WARNING
            )

            return
        }

        val currentState =
        espDosingState

        if (currentState == null) {
            DialogManager.showConfirmDialog(
                context = requireContext(),
                type = DialogType.WARNING,
                title = "Device Data Not Loaded",
                message = "Dosing data has not been loaded from the device yet. Load device data and try again.",
                onConfirm = {
                    fetchDosingStateFromEsp()
                }
            )

            return
        }

        if (!currentState.channel.isCalibrated) {
            DialogManager.showInfoDialog(
                context = requireContext(),
                type = DialogType.WARNING,
                title = "Calibration Required",
                message = "Please calibrate this dosing channel before using manual dosing."
            )

            return
        }

        val baseActivity =
        activity as? BaseActivity

        baseActivity?.showLoading(
            true
        )

        viewLifecycleOwner.lifecycleScope.launch {
            val result =
            runCatching {
                dosingEspRepository.sendManualDose(
                    deviceIp = deviceIp,
                    channelIndex = channelIndex,
                    doseMl = doseMl,
                    calibrationMsPerMl = currentState.channel.calibrationMsPerMl
                )
            }

            baseActivity?.showLoading(
                false
            )

            if (_binding == null) {
                return@launch
            }

            result.onFailure {
                DialogManager.showConfirmDialog(
                    context = requireContext(),
                    type = DialogType.ERROR,
                    title = "Manual Dosing Failed",
                    message = "The manual dose could not be started. Please check the device connection and try again.",
                    confirmTextResId = R.string.confirm,
                    cancelTextResId = R.string.cancel,
                    onConfirm = {
                        startManualDosing(
                            doseMl = doseMl
                        )
                    }
                )
            }
        }
    }

    private fun renderChannelTitle(
        name: String? = espDosingState?.channel?.name
    ) {
        val cleanName =
        name
        ?.trim()
        .orEmpty()

        binding.tvChannelSettingsTitle.text =
        if (
            cleanName.isNotBlank() &&
            cleanName != "-"
        ) {
            cleanName
        } else {
            "Channel $channelNumber"
        }
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
        _binding =
        null

        super.onDestroyView()
    }

    private enum class DosingMode {
        SINGLE,
        HOURLY_24,
        CUSTOM_PERIODS,
        TIMER
    }

    companion object {
        private const val ARG_DEVICE_ID = "deviceId"
        private const val ARG_DEVICE_IP = "deviceIp"
        private const val ARG_DEVICE_TITLE = "deviceTitle"
        private const val ARG_CHANNEL_INDEX = "channelIndex"

        fun newInstance(
            deviceId: Long,
            deviceIp: String,
            deviceTitle: String,
            channelIndex: Int
        ): DeviceDosingChannelSettingsFragment {
            return DeviceDosingChannelSettingsFragment().apply {
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

                    putInt(
                        ARG_CHANNEL_INDEX,
                        channelIndex.coerceIn(
                            minimumValue = 0,
                            maximumValue = 3
                        )
                    )
                }
            }
        }
    }
}