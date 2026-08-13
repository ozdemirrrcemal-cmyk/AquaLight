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

/** Draft editor bounded by the current firmware-published custom-program capacities. */
class DeviceDosingCustomScheduleFragment :
    DeviceDosingChannelDestinationFragment(R.layout.fragment_device_dosing_channel_detail) {

    private val args: DeviceDosingCustomScheduleFragmentArgs by navArgs()
    private var periods by mutableStateOf<List<DeviceDosingCustomPeriod>>(emptyList())
    private var validationMessageRes by mutableStateOf<Int?>(null)
    private lateinit var editorPayload: DeviceDosingCustomEditorPayload
    private lateinit var editor: DeviceDosingCustomScheduleEditor

    override val destinationTitle: String
        get() = getString(R.string.device_dosing_detail_schedule_custom)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val argumentPayload = DeviceDosingCustomScheduleContract.decodeEditorPayload(args.periodsDraft)
        if (args.dailyDoseMicroliters < 0L || argumentPayload == null) {
            findNavController().navigateUp()
            return
        }
        editorPayload = argumentPayload

        periods = restoreCustomPeriods(savedInstanceState, argumentPayload.periods)
        if (
            DeviceDosingCustomScheduleContract.validate(
                periods,
                argumentPayload.maxPeriods,
                argumentPayload.maxDoseCount
            ) != null
        ) {
            findNavController().navigateUp()
            return
        }
        validationMessageRes = savedInstanceState
            ?.getInt(STATE_VALIDATION_MESSAGE_RES, NO_MESSAGE_RES)
            ?.takeUnless { messageRes -> messageRes == NO_MESSAGE_RES }
        editor = DeviceDosingCustomScheduleEditor(
            host = DeviceDosingCustomScheduleEditorHost(
                fragment = this,
                slotId = args.slotId,
                maxPeriods = argumentPayload.maxPeriods,
                maxDoseCount = argumentPayload.maxDoseCount,
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
            DeviceDosingCustomScheduleContract.encodeDraft(periods)
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
                        maxPeriods = editorPayload.maxPeriods,
                        maxDoseCount = editorPayload.maxDoseCount,
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
        val validation = if (::editorPayload.isInitialized) {
            DeviceDosingCustomScheduleContract.validate(
                periods,
                editorPayload.maxPeriods,
                editorPayload.maxDoseCount
            )
        } else {
            DeviceDosingCustomScheduleContract.ValidationError.INVALID_PERIOD
        }
        val canSave =
            navController.currentDestination?.id == R.id.deviceDosingCustomScheduleFragment &&
                args.dailyDoseMicroliters > 0L &&
                periods.isNotEmpty() &&
                validation == null
        if (!canSave) {
            validationMessageRes = validation?.toMessageRes()
            return
        }

        parentFragmentManager.setFragmentResult(
            DeviceDosingCustomScheduleContract.RESULT_REQUEST_KEY,
            bundleOf(
                DeviceDosingCustomScheduleContract.RESULT_KEY to
                    DeviceDosingCustomScheduleContract.RESULT_SAVED,
                DeviceDosingCustomScheduleContract.RESULT_SLOT_ID to args.slotId,
                DeviceDosingCustomScheduleContract.RESULT_PERIODS_DRAFT to
                    DeviceDosingCustomScheduleContract.encodeDraft(periods)
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
    argumentPeriods: List<DeviceDosingCustomPeriod>
): List<DeviceDosingCustomPeriod> = savedInstanceState
    ?.getString("custom_schedule_periods_draft")
    ?.let(DeviceDosingCustomScheduleContract::decodeDraft)
    ?: argumentPeriods

private fun DeviceDosingCustomScheduleContract.ValidationError.toMessageRes(): Int = when (this) {
    DeviceDosingCustomScheduleContract.ValidationError.INVALID_PERIOD ->
        R.string.device_dosing_custom_error_invalid_period
    DeviceDosingCustomScheduleContract.ValidationError.TOO_MANY_DOSES ->
        R.string.device_dosing_custom_error_too_many
    DeviceDosingCustomScheduleContract.ValidationError.OVERLAPPING_PERIODS ->
        R.string.device_dosing_custom_error_overlap
}
