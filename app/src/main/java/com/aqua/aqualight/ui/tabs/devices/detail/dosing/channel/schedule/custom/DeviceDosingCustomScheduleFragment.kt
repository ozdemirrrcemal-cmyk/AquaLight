package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.custom

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.common.DeviceDosingChannelDestinationFragment

/** Draft editor for a daily dose distributed across explicit, non-overlapping periods. */
class DeviceDosingCustomScheduleFragment :
    DeviceDosingChannelDestinationFragment(R.layout.fragment_device_dosing_channel_detail) {

    private val args: DeviceDosingCustomScheduleFragmentArgs by navArgs()
    private var periods by mutableStateOf<List<DeviceDosingCustomPeriod>>(emptyList())
    private var validationMessageRes by mutableStateOf<Int?>(null)
    private lateinit var editor: DeviceDosingCustomScheduleEditor

    override val destinationTitle: String
        get() = getString(R.string.device_dosing_detail_schedule_custom)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val validLimits = args.maxEventsPerChannel > 0 &&
            args.maxCustomPeriodsPerChannel > 0
        val argumentPeriods = if (validLimits) {
            DeviceDosingCustomScheduleContract.decodeDraft(
                encoded = args.periodsDraft,
                maxEventsPerChannel = args.maxEventsPerChannel,
                maxPeriodsPerChannel = args.maxCustomPeriodsPerChannel
            )
        } else {
            null
        }
        if (args.dailyDoseMicroliters < 0L || argumentPeriods == null) {
            findNavController().navigateUp()
            return
        }

        periods = restoreCustomPeriods(
            savedInstanceState = savedInstanceState,
            argumentPeriods = argumentPeriods,
            maxEventsPerChannel = args.maxEventsPerChannel,
            maxPeriodsPerChannel = args.maxCustomPeriodsPerChannel
        )
        validationMessageRes = savedInstanceState
            ?.getInt(STATE_VALIDATION_MESSAGE_RES, NO_MESSAGE_RES)
            ?.takeUnless { messageRes -> messageRes == NO_MESSAGE_RES }
        editor = DeviceDosingCustomScheduleEditor(
            host = DeviceDosingCustomScheduleEditorHost(
                fragment = this,
                slotId = args.slotId,
                maxEventsPerChannel = args.maxEventsPerChannel,
                maxPeriodsPerChannel = args.maxCustomPeriodsPerChannel,
                periods = { periods },
                updatePeriods = { updated -> periods = updated },
                updateValidation = { messageRes -> validationMessageRes = messageRes }
            ),
            savedInstanceState = savedInstanceState
        ).also { scheduleEditor -> scheduleEditor.bindResults(viewLifecycleOwner) }

        setupSelectedPump(
            view = view,
            deviceUid = args.deviceUid,
            slotId = args.slotId,
            pumpCount = args.pumpCount,
            channelNumber = args.channelNumber
        )
        setupContent(view)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(
            STATE_PERIODS_DRAFT,
            DeviceDosingCustomScheduleContract.encodeDraft(
                periods = periods,
                maxEventsPerChannel = args.maxEventsPerChannel,
                maxPeriodsPerChannel = args.maxCustomPeriodsPerChannel
            )
        )
        outState.putInt(STATE_VALIDATION_MESSAGE_RES, validationMessageRes ?: NO_MESSAGE_RES)
        if (::editor.isInitialized) editor.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    private fun setupContent(view: View) {
        view.findViewById<ComposeView>(R.id.channelDetailContent).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DeviceDosingCustomScheduleScreen(
                    state = DeviceDosingCustomScheduleUiState(
                        dailyDoseMicroliters = args.dailyDoseMicroliters,
                        periods = periods,
                        maxEventsPerChannel = args.maxEventsPerChannel,
                        maxPeriodsPerChannel = args.maxCustomPeriodsPerChannel,
                        validationMessage = validationMessageRes?.let(::getString)
                    ),
                    onAction = ::handleScheduleAction
                )
            }
        }
    }

    private fun handleScheduleAction(action: DeviceDosingCustomScheduleAction) {
        when (action) {
            DeviceDosingCustomScheduleAction.Add -> editor.beginAdd()
            is DeviceDosingCustomScheduleAction.Edit -> editor.beginEdit(action.index)
            is DeviceDosingCustomScheduleAction.Remove -> removePeriod(action.index)
            DeviceDosingCustomScheduleAction.Save -> saveDraft()
        }
    }

    private fun removePeriod(index: Int) {
        if (index !in periods.indices) return
        periods = periods.filterIndexed { candidateIndex, _ -> candidateIndex != index }
        validationMessageRes = null
    }

    private fun saveDraft() {
        val navController = findNavController()
        val canSave =
            navController.currentDestination?.id == R.id.deviceDosingCustomScheduleFragment &&
                args.dailyDoseMicroliters > 0L && periods.isNotEmpty() &&
                DeviceDosingCustomScheduleContract.validate(
                    periods = periods,
                    maxEventsPerChannel = args.maxEventsPerChannel,
                    maxPeriodsPerChannel = args.maxCustomPeriodsPerChannel
                ) == null
        if (!canSave) return

        parentFragmentManager.setFragmentResult(
            DeviceDosingCustomScheduleContract.RESULT_REQUEST_KEY,
            bundleOf(
                DeviceDosingCustomScheduleContract.RESULT_KEY to
                    DeviceDosingCustomScheduleContract.RESULT_SAVED,
                DeviceDosingCustomScheduleContract.RESULT_SLOT_ID to args.slotId,
                DeviceDosingCustomScheduleContract.RESULT_PERIODS_DRAFT to
                    DeviceDosingCustomScheduleContract.encodeDraft(
                        periods = periods,
                        maxEventsPerChannel = args.maxEventsPerChannel,
                        maxPeriodsPerChannel = args.maxCustomPeriodsPerChannel
                    )
            )
        )
        navController.navigateUp()
    }

    private companion object {
        const val STATE_PERIODS_DRAFT = "custom_schedule_periods_draft"
        const val STATE_VALIDATION_MESSAGE_RES = "custom_schedule_validation_message_res"
        const val NO_MESSAGE_RES = 0
    }
}

private fun restoreCustomPeriods(
    savedInstanceState: Bundle?,
    argumentPeriods: List<DeviceDosingCustomPeriod>,
    maxEventsPerChannel: Int,
    maxPeriodsPerChannel: Int
): List<DeviceDosingCustomPeriod> = savedInstanceState
    ?.getString("custom_schedule_periods_draft")
    ?.let { encoded ->
        DeviceDosingCustomScheduleContract.decodeDraft(
            encoded = encoded,
            maxEventsPerChannel = maxEventsPerChannel,
            maxPeriodsPerChannel = maxPeriodsPerChannel
        )
    }
    ?: argumentPeriods
