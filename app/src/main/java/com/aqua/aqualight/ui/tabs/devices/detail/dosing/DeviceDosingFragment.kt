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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

class DeviceDosingFragment :
    Fragment(R.layout.fragment_device_dosing) {

    private var _binding: FragmentDeviceDosingBinding? = null
    private val binding get() = _binding!!

    private lateinit var channelSettingsDataStoreManager: DosingChannelSettingsDataStoreManager
    private lateinit var dosingEspRepository: DosingEspRepository

    private val latestLocalSettingsByChannel: MutableMap<Int, DosingChannelSettingsUi> =
        mutableMapOf()

    private val latestEspStateByChannel: MutableMap<Int, DosingEspState> =
        mutableMapOf()

    private val runningPumpIndexes: MutableSet<Int> =
        mutableSetOf()

    private var navigationInProgress: Boolean =
        false

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
        startDosingStatePolling()
        startPumpRunningPolling()
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

        cardBinding.cardChannelState.visibility =
            View.VISIBLE

        cardBinding.tvChannelState.text =
            "Not Active"

        cardBinding.tvChannelHint.text =
            "Tap to configure this channel"

        cardBinding.tvChannelHint.visibility =
            View.VISIBLE

        cardBinding.tvChannelDose.text =
            "0 ml"

        cardBinding.tvChannelSchedule.text =
            "Every day"

        cardBinding.tvChannelStatus.text =
            "Mode"

        cardBinding.tvChannelModeProgress.text =
            "0/0"

        cardBinding.tvChannelReservoir.text =
            ""

        cardBinding.channelMetricsContainer.visibility =
            View.GONE

        cardBinding.channelModeRow.visibility =
            View.GONE

        cardBinding.channelProgressSection.visibility =
            View.GONE

        cardBinding.channelTimelineHeaderRow.visibility =
            View.GONE

        cardBinding.dosingTimelineView.renderEmpty()

        cardBinding.dosingTimelineView.visibility =
            View.GONE

        cardBinding.tvDosingTimelineCaption.text =
            ""

        cardBinding.tvDosingTimelineCaption.visibility =
            View.GONE

        cardBinding.cardManualDoseChip.visibility =
            View.GONE

        cardBinding.tvManualDoseChip.text =
            ""

        cardBinding.channelReservoirHeaderRow.visibility =
            View.GONE

        cardBinding.channelProgressBarRow.visibility =
            View.GONE

        cardBinding.tvChannelProgressTitle.text =
            "Reservoir"

        cardBinding.tvChannelProgressValue.text =
            ""

        cardBinding.tvChannelProgressValue.visibility =
            View.GONE

        cardBinding.progressChannelDose.max =
            RESERVOIR_PROGRESS_MAX

        cardBinding.progressChannelDose.progress =
            0

        cardBinding.btnChannelQuickDose.visibility =
            View.GONE

        cardBinding.btnChannelQuickDose.setOnClickListener(
            null
        )
    }

    private fun observeChannelCards() {
        val channelCards =
            getChannelCards()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                channelCards.forEachIndexed { channelIndex, _ ->
                    launch {
                        channelSettingsDataStoreManager.observeChannelSettings(
                            deviceId = deviceId,
                            channelIndex = channelIndex
                        ).collect { settings ->
                            latestLocalSettingsByChannel[channelIndex] =
                                settings

                            renderChannelCard(
                                channelIndex = channelIndex
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startDosingStatePolling() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                while (true) {
                    refreshDosingStatesFromEsp(
                        showWarning = false
                    )

                    delay(
                        timeMillis = DOSING_STATE_REFRESH_INTERVAL_MS
                    )
                }
            }
        }
    }

    private fun startPumpRunningPolling() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(
                Lifecycle.State.STARTED
            ) {
                while (true) {
                    refreshPumpRunningStates()

                    delay(
                        timeMillis = PUMP_RUNNING_REFRESH_INTERVAL_MS
                    )
                }
            }
        }
    }

    private suspend fun refreshPumpRunningStates() {
        if (deviceIp.isBlank()) {
            return
        }

        val result =
            runCatching {
                dosingEspRepository.fetchDosingRuntimeChannels(
                    deviceIp = deviceIp
                )
            }

        if (_binding == null) {
            return
        }

        result.onSuccess { channels ->
            val previousRunningPumpIndexes =
                runningPumpIndexes.toSet()

            runningPumpIndexes.clear()

            channels.forEachIndexed { channelIndex, channel ->
                val vNow =
                    channel.vNow ?: 0f

                if (vNow > PUMP_RUNNING_VNOW_THRESHOLD) {
                    runningPumpIndexes.add(
                        channelIndex
                    )
                }
            }

            if (previousRunningPumpIndexes != runningPumpIndexes) {
                renderPumpRunningIndicators()

                getChannelCards().indices.forEach { channelIndex ->
                    renderChannelCard(
                        channelIndex = channelIndex
                    )
                }
            }
        }
    }

    private suspend fun refreshDosingStatesFromEsp(
        showWarning: Boolean
    ) {
        if (deviceIp.isBlank()) {
            return
        }

        val result =
            runCatching {
                dosingEspRepository.fetchDosingScreenStates(
                    deviceIp = deviceIp
                )
            }

        if (_binding == null) {
            return
        }

        result.onSuccess { states ->
            states.forEachIndexed { channelIndex, state ->
                latestEspStateByChannel[channelIndex] =
                    state

                renderChannelCard(
                    channelIndex = channelIndex
                )
            }
        }.onFailure {
            if (showWarning) {
                showSnackBar(
                    message = "Device data could not be refreshed.",
                    type = BaseActivity.SnackType.WARNING
                )
            }
        }
    }

    private fun getChannelCards(): List<ItemDosingChannelCardBinding> {
        return listOf(
            binding.channelCard1,
            binding.channelCard2,
            binding.channelCard3,
            binding.channelCard4
        )
    }

    private fun renderChannelCard(
        channelIndex: Int
    ) {
        if (_binding == null) {
            return
        }

        val cardBinding =
            getChannelCards().getOrNull(
                index = channelIndex
            ) ?: return

        val settings =
            latestLocalSettingsByChannel[channelIndex]

        val espState =
            latestEspStateByChannel[channelIndex]

        val channelNumber =
            channelIndex + 1

        val channelName =
            espState
                ?.channel
                ?.name
                ?.trim()
                ?.takeIf { name ->
                    name.isNotBlank() && name != "-"
                } ?: "Channel $channelNumber"

        val dailyDoseMl =
            espState?.configuredDailyDoseMl ?: 0f

        val hasSchedule =
            dailyDoseMl > 0f

        val hasReservoir =
            settings?.reservoirTrackingEnabled == true &&
                settings.hasReservoirCapacity

        val hasVisibleDetails =
            hasSchedule || hasReservoir

        val isScheduleActive =
            hasSchedule &&
                espState?.scheduleEnabled == true

        cardBinding.tvChannelNumber.text =
            channelNumber.toString()

        cardBinding.tvChannelName.text =
            channelName

        cardBinding.cardChannelState.visibility =
            View.VISIBLE

        cardBinding.tvChannelState.text =
            if (isScheduleActive) {
                "Active"
            } else {
                "Not Active"
            }

        cardBinding.tvChannelDose.text =
            formatMl(
                value = dailyDoseMl
            )

        cardBinding.tvChannelSchedule.text =
            espState?.let { state ->
                formatWeekDays(
                    weekDays = getScheduleWeekDays(
                        state = state
                    )
                )
            } ?: "Every day"

        cardBinding.tvChannelStatus.text =
            if (
                espState != null &&
                hasSchedule
            ) {
                formatModeLabel(
                    state = espState
                )
            } else {
                "Mode"
            }

        cardBinding.tvChannelModeProgress.text =
            if (
                espState != null &&
                hasSchedule
            ) {
                formatModeProgressOnly(
                    state = espState
                )
            } else {
                "0/0"
            }

        cardBinding.tvChannelHint.visibility =
            if (hasVisibleDetails) {
                View.GONE
            } else {
                View.VISIBLE
            }

        cardBinding.channelMetricsContainer.visibility =
            if (hasSchedule) {
                View.VISIBLE
            } else {
                View.GONE
            }

        cardBinding.channelModeRow.visibility =
            if (hasSchedule) {
                View.VISIBLE
            } else {
                View.GONE
            }

        val showTimeline =
            espState != null &&
                dailyDoseMl > 0f

        cardBinding.channelProgressSection.visibility =
            if (
                showTimeline ||
                hasReservoir
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }

        renderDosingTimelineSection(
            cardBinding = cardBinding,
            channelIndex = channelIndex,
            espState = espState,
            dailyDoseMl = dailyDoseMl
        )

        renderReservoirSection(
            cardBinding = cardBinding,
            channelIndex = channelIndex,
            settings = settings,
            espState = espState,
            dailyDoseMl = dailyDoseMl
        )
    }

    private fun renderDosingTimelineSection(
        cardBinding: ItemDosingChannelCardBinding,
        channelIndex: Int,
        espState: DosingEspState?,
        dailyDoseMl: Float
    ) {
        if (
            espState == null ||
            dailyDoseMl <= 0f
        ) {
            cardBinding.channelTimelineHeaderRow.visibility =
                View.GONE

            cardBinding.dosingTimelineView.renderEmpty()

            cardBinding.dosingTimelineView.visibility =
                View.GONE

            cardBinding.tvDosingTimelineCaption.text =
                ""

            cardBinding.tvDosingTimelineCaption.visibility =
                View.GONE

            cardBinding.cardManualDoseChip.visibility =
                View.GONE

            return
        }

        val totalRuns =
            calculateTotalRuns(
                state = espState
            ).coerceAtLeast(
                minimumValue = 1
            )

        val currentRun =
            extractCurrentRun(
                state = espState,
                totalRuns = totalRuns
            )

        val isRunning =
            runningPumpIndexes.contains(
                element = channelIndex
            )

        cardBinding.channelTimelineHeaderRow.visibility =
            View.VISIBLE

        cardBinding.dosingTimelineView.visibility =
            View.VISIBLE

        cardBinding.tvDosingTimelineCaption.visibility =
            View.VISIBLE

        cardBinding.cardManualDoseChip.visibility =
            View.GONE

        when (espState.activeMode) {
            DosingScheduleMode.SINGLE -> {
                cardBinding.dosingTimelineView.renderSingle(
                    completed = currentRun >= 1,
                    running = isRunning
                )

                cardBinding.tvDosingTimelineCaption.text =
                    "${formatMl(dailyDoseMl)} scheduled · $currentRun/1"
            }

            DosingScheduleMode.HOURLY_24 -> {
                cardBinding.dosingTimelineView.renderHourly24(
                    completedRuns = currentRun,
                    running = isRunning
                )

                cardBinding.tvDosingTimelineCaption.text =
                    "${formatMl(dailyDoseMl / 24f)} each · $currentRun/24"
            }

            DosingScheduleMode.CUSTOM_PERIODS -> {
                val customPeriodCount =
                    findCustomPeriodTimers(
                        state = espState
                    ).size.coerceAtLeast(
                        minimumValue = 1
                    )

                cardBinding.dosingTimelineView.renderCustomPeriods(
                    completedPeriods = currentRun.coerceAtMost(
                        maximumValue = customPeriodCount
                    ),
                    totalPeriods = customPeriodCount,
                    running = isRunning
                )

                cardBinding.tvDosingTimelineCaption.text =
                    "$customPeriodCount periods · $totalRuns doses total"
            }

            DosingScheduleMode.TIMER -> {
                cardBinding.dosingTimelineView.renderTimer(
                    completedDoses = currentRun,
                    totalDoses = totalRuns,
                    running = isRunning
                )

                cardBinding.tvDosingTimelineCaption.text =
                    "$totalRuns timer doses · $currentRun/$totalRuns"
            }
        }
    }

    private fun findCustomPeriodTimers(
        state: DosingEspState
    ): List<DosingEspTimerState> {
        return state.channelTimers
            .filter { timer ->
                timer.name.contains(
                    other = "CUSTOM_PERIODS",
                    ignoreCase = true
                ) ||
                    timer.name.contains(
                        other = "CUSTOM_TIME",
                        ignoreCase = true
                    )
            }
            .filter { timer ->
                timer.dosePerRunMl > 0f &&
                    timer.count > 0
            }
            .sortedBy { timer ->
                timer.index
            }
    }

    private fun renderReservoirSection(
        cardBinding: ItemDosingChannelCardBinding,
        channelIndex: Int,
        settings: DosingChannelSettingsUi?,
        espState: DosingEspState?,
        dailyDoseMl: Float
    ) {
        if (settings == null) {
            hideReservoirProgress(
                cardBinding = cardBinding
            )

            return
        }

        if (!settings.reservoirTrackingEnabled) {
            hideReservoirProgress(
                cardBinding = cardBinding
            )

            return
        }

        val capacityMl =
            settings.containerVolumeMl
                ?.takeIf { value ->
                    value > 0f
                }

        if (capacityMl == null) {
            hideReservoirProgress(
                cardBinding = cardBinding
            )

            return
        }

        val remainingMl =
            espState
                ?.channel
                ?.restMl
                ?.coerceAtLeast(
                    minimumValue = 0f
                )
                ?.coerceAtMost(
                    maximumValue = capacityMl
                ) ?: capacityMl

        if (remainingMl <= 0f) {
            renderRefillReservoirState(
                cardBinding = cardBinding,
                channelIndex = channelIndex,
                capacityMl = capacityMl
            )

            return
        }

        val remainingDays =
            calculateRemainingDays(
                remainingMl = remainingMl,
                dailyDoseMl = dailyDoseMl
            )

        cardBinding.tvChannelReservoir.text =
            if (remainingDays == null) {
                "${formatMlAmount(remainingMl)}/${formatMlAmount(capacityMl)} ml"
            } else {
                "$remainingDays days ${formatMlAmount(remainingMl)}/${formatMlAmount(capacityMl)} ml"
            }

        cardBinding.channelReservoirHeaderRow.visibility =
            View.VISIBLE

        cardBinding.channelProgressBarRow.visibility =
            View.VISIBLE

        cardBinding.tvChannelProgressTitle.text =
            "Reservoir"

        cardBinding.tvChannelProgressValue.text =
            ""

        cardBinding.tvChannelProgressValue.visibility =
            View.GONE

        cardBinding.progressChannelDose.max =
            RESERVOIR_PROGRESS_MAX

        cardBinding.progressChannelDose.progress =
            calculateReservoirProgressPercent(
                remainingMl = remainingMl,
                capacityMl = capacityMl
            )

        cardBinding.btnChannelQuickDose.visibility =
            View.GONE

        cardBinding.btnChannelQuickDose.setOnClickListener(
            null
        )
    }

    private fun hideReservoirProgress(
        cardBinding: ItemDosingChannelCardBinding
    ) {
        cardBinding.tvChannelReservoir.text =
            ""

        cardBinding.channelReservoirHeaderRow.visibility =
            View.GONE

        cardBinding.channelProgressBarRow.visibility =
            View.GONE

        cardBinding.tvChannelProgressValue.text =
            ""

        cardBinding.tvChannelProgressValue.visibility =
            View.GONE

        cardBinding.progressChannelDose.progress =
            0

        cardBinding.btnChannelQuickDose.visibility =
            View.GONE

        cardBinding.btnChannelQuickDose.setOnClickListener(
            null
        )
    }

    private fun renderRefillReservoirState(
        cardBinding: ItemDosingChannelCardBinding,
        channelIndex: Int,
        capacityMl: Float
    ) {
        cardBinding.tvChannelReservoir.text =
            "Refill required 0/${formatMlAmount(capacityMl)} ml"

        cardBinding.channelReservoirHeaderRow.visibility =
            View.VISIBLE

        cardBinding.channelProgressBarRow.visibility =
            View.GONE

        cardBinding.tvChannelProgressTitle.text =
            "Reservoir"

        cardBinding.tvChannelProgressValue.text =
            ""

        cardBinding.tvChannelProgressValue.visibility =
            View.GONE

        cardBinding.progressChannelDose.progress =
            0

        cardBinding.btnChannelQuickDose.visibility =
            View.VISIBLE

        cardBinding.btnChannelQuickDose.text =
            "Refill"

        cardBinding.btnChannelQuickDose.setOnClickListener {
            refillReservoirOnDevice(
                channelIndex = channelIndex,
                capacityMl = capacityMl
            )
        }
    }

    private fun refillReservoirOnDevice(
        channelIndex: Int,
        capacityMl: Float
    ) {
        if (deviceIp.isBlank()) {
            showSnackBar(
                message = "Device IP address is missing.",
                type = BaseActivity.SnackType.WARNING
            )

            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val result =
                runCatching {
                    dosingEspRepository.refillChannelReservoir(
                        deviceIp = deviceIp,
                        channelIndex = channelIndex,
                        capacityMl = capacityMl
                    )
                }

            if (_binding == null) {
                return@launch
            }

            result.onSuccess { state ->
                latestEspStateByChannel[channelIndex] =
                    state

                renderChannelCard(
                    channelIndex = channelIndex
                )

                showSnackBar(
                    message = "Reservoir refilled.",
                    type = BaseActivity.SnackType.NORMAL
                )
            }.onFailure {
                showSnackBar(
                    message = "Reservoir could not be refilled.",
                    type = BaseActivity.SnackType.WARNING
                )
            }
        }
    }

    private fun calculateRemainingDays(
        remainingMl: Float,
        dailyDoseMl: Float
    ): String? {
        if (dailyDoseMl <= 0f) {
            return null
        }

        val days =
            remainingMl / dailyDoseMl

        return when {
            days < 1f -> {
                "<1"
            }

            else -> {
                days.toInt().toString()
            }
        }
    }

    private fun calculateReservoirProgressPercent(
        remainingMl: Float,
        capacityMl: Float
    ): Int {
        if (capacityMl <= 0f) {
            return 0
        }

        return (
            remainingMl.coerceIn(
                minimumValue = 0f,
                maximumValue = capacityMl
            ) / capacityMl * RESERVOIR_PROGRESS_MAX
            ).roundToInt()
            .coerceIn(
                minimumValue = 0,
                maximumValue = RESERVOIR_PROGRESS_MAX
            )
    }

    private fun getScheduleWeekDays(
        state: DosingEspState
    ): List<Boolean> {
        val timer =
            findPrimaryScheduleTimer(
                state = state
            ) ?: state.timer

        return if (timer.weekDays.size == 7) {
            timer.weekDays
        } else {
            List(
                size = 7
            ) {
                true
            }
        }
    }

    private fun formatWeekDays(
        weekDays: List<Boolean>
    ): String {
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

        if (safeWeekDays.all { selected ->
                selected
            }
        ) {
            return "Every day"
        }

        val labels =
            listOf(
                "Mon",
                "Tue",
                "Wed",
                "Thu",
                "Fri",
                "Sat",
                "Sun"
            )

        return safeWeekDays
            .mapIndexedNotNull { index, selected ->
                if (selected) {
                    labels[index]
                } else {
                    null
                }
            }
            .joinToString(
                separator = ", "
            )
    }

    private fun formatModeLabel(
        state: DosingEspState
    ): String {
        return "Mode ${formatModeNameTitle(state.activeMode)}"
    }

    private fun formatModeProgressOnly(
        state: DosingEspState
    ): String {
        val totalRuns =
            calculateTotalRuns(
                state = state
            ).coerceAtLeast(
                minimumValue = 1
            )

        val currentRun =
            extractCurrentRun(
                state = state,
                totalRuns = totalRuns
            )

        return "$currentRun/$totalRuns"
    }

    private fun formatModeNameTitle(
        mode: DosingScheduleMode
    ): String {
        return when (mode) {
            DosingScheduleMode.SINGLE -> {
                "Single"
            }

            DosingScheduleMode.HOURLY_24 -> {
                "24 Hourly"
            }

            DosingScheduleMode.CUSTOM_PERIODS -> {
                "Custom Periods"
            }

            DosingScheduleMode.TIMER -> {
                "Timer"
            }
        }
    }

    private fun calculateTotalRuns(
        state: DosingEspState
    ): Int {
        return when (state.activeMode) {
            DosingScheduleMode.SINGLE -> {
                1
            }

            DosingScheduleMode.HOURLY_24 -> {
                24
            }

            DosingScheduleMode.CUSTOM_PERIODS -> {
                val customTimers =
                    state.channelTimers.filter { timer ->
                        timer.name.contains(
                            other = "CUSTOM_PERIODS",
                            ignoreCase = true
                        ) ||
                            timer.name.contains(
                                other = "CUSTOM_TIME",
                                ignoreCase = true
                            )
                    }

                customTimers
                    .sumOf { timer ->
                        timer.count.coerceAtLeast(
                            minimumValue = 0
                        )
                    }
                    .takeIf { count ->
                        count > 0
                    } ?: 1
            }

            DosingScheduleMode.TIMER -> {
                val timerModeTimers =
                    state.channelTimers.filter { timer ->
                        timer.name.contains(
                            other = "TIMER",
                            ignoreCase = true
                        )
                    }

                timerModeTimers
                    .sumOf { timer ->
                        timer.count.coerceAtLeast(
                            minimumValue = 0
                        )
                    }
                    .takeIf { count ->
                        count > 0
                    } ?: state.timer.count.coerceAtLeast(
                    minimumValue = 1
                )
            }
        }
    }

    private fun extractCurrentRun(
        state: DosingEspState,
        totalRuns: Int
    ): Int {
        val statusText =
            state.channelTimers
                .mapNotNull { timer ->
                    timer.status
                }
                .firstOrNull { status ->
                    status.isNotBlank()
                } ?: state.timer.status.orEmpty()

        val match =
            Regex(
                pattern = """(\d+)\s*/\s*(\d+)"""
            ).find(
                input = statusText
            )

        val current =
            match
                ?.groupValues
                ?.getOrNull(
                    index = 1
                )
                ?.toIntOrNull()

        return current
            ?.coerceIn(
                minimumValue = 1,
                maximumValue = totalRuns
            ) ?: 1
    }

    private fun findPrimaryScheduleTimer(
        state: DosingEspState
    ): DosingEspTimerState? {
        return state.channelTimers.firstOrNull { timer ->
            timer.enabled &&
                timer.dosePerRunMl > 0f &&
                timer.count > 0
        } ?: state.channelTimers.firstOrNull { timer ->
            timer.dosePerRunMl > 0f &&
                timer.count > 0
        } ?: state.timer.takeIf { timer ->
            timer.dosePerRunMl > 0f &&
                timer.count > 0
        }
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

    private fun formatMlAmount(
        value: Float
    ): String {
        val safeValue =
            value.coerceAtLeast(
                minimumValue = 0f
            )

        return if (safeValue % 1f == 0f) {
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
        private const val ARG_CHANNEL_INDEX = "channelIndex"

        private const val RESERVOIR_PROGRESS_MAX = 100
        private const val DOSING_STATE_REFRESH_INTERVAL_MS = 4000L
        private const val PUMP_RUNNING_REFRESH_INTERVAL_MS = 1000L
        private const val PUMP_RUNNING_VNOW_THRESHOLD = 0.01f

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
                        "canEditDeviceName",
                        canEditDeviceName
                    )

                    putString(
                        "userDeviceName",
                        userDeviceName
                    )

                    putString(
                        "defaultDeviceTitle",
                        defaultDeviceTitle
                    )
                }
            }
        }
    }
}