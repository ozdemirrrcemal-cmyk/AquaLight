package com.aqua.aqualight.ui.tabs.devices.detail.dosing.channel.schedule.timer

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

/** Draft editor bounded by the current firmware-published Timer-mode event capacity. */
class DeviceDosingTimerScheduleFragment :
    DeviceDosingChannelDestinationFragment(R.layout.fragment_device_dosing_channel_detail) {

    private val args: DeviceDosingTimerScheduleFragmentArgs by navArgs()
    private var doses by mutableStateOf<List<DeviceDosingTimerDose>>(emptyList())
    private var validationMessageRes by mutableStateOf<Int?>(null)
    private lateinit var editorPayload: DeviceDosingTimerEditorPayload
    private lateinit var editor: DeviceDosingTimerScheduleEditor

    override val destinationTitle: String
        get() = getString(R.string.device_dosing_detail_schedule_timer)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val argumentPayload = DeviceDosingTimerScheduleContract.decodeEditorPayload(args.dosesDraft)
        if (argumentPayload == null) {
            findNavController().navigateUp()
            return
        }
        editorPayload = argumentPayload

        doses = restoreTimerDoses(savedInstanceState, argumentPayload.doses)
        if (DeviceDosingTimerScheduleContract.validate(doses, argumentPayload.maxDoseCount) != null) {
            findNavController().navigateUp()
            return
        }
        validationMessageRes = savedInstanceState
            ?.getInt(STATE_VALIDATION_MESSAGE_RES, NO_MESSAGE_RES)
            ?.takeUnless { messageRes -> messageRes == NO_MESSAGE_RES }
        editor = DeviceDosingTimerScheduleEditor(
            host = DeviceDosingTimerScheduleEditorHost(
                fragment = this,
                slotId = args.slotId,
                maxDoseCount = argumentPayload.maxDoseCount,
                doses = { doses },
                updateDoses = { updated -> doses = updated },
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
            STATE_DOSES_DRAFT,
            DeviceDosingTimerScheduleContract.encodeDraft(doses)
        )
        outState.putInt(STATE_VALIDATION_MESSAGE_RES, validationMessageRes ?: NO_MESSAGE_RES)
        if (::editor.isInitialized) editor.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    private fun setupContent(view: View) {
        view.findViewById<ComposeView>(R.id.channelDetailContent).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DeviceDosingTimerScheduleScreen(
                    state = DeviceDosingTimerScheduleUiState(
                        doses = doses,
                        validationMessage = validationMessageRes?.let(::getString)
                    ),
                    onAction = ::handleScheduleAction
                )
            }
        }
    }

    private fun handleScheduleAction(action: DeviceDosingTimerScheduleAction) {
        when (action) {
            DeviceDosingTimerScheduleAction.Add -> editor.beginAdd()
            is DeviceDosingTimerScheduleAction.Edit -> editor.beginEdit(action.index)
            is DeviceDosingTimerScheduleAction.Remove -> removeDose(action.index)
            DeviceDosingTimerScheduleAction.Save -> saveDraft()
        }
    }

    private fun removeDose(index: Int) {
        if (index !in doses.indices) return
        doses = doses.filterIndexed { candidateIndex, _ -> candidateIndex != index }
        validationMessageRes = null
    }

    private fun saveDraft() {
        val navController = findNavController()
        val validation = if (::editorPayload.isInitialized) {
            DeviceDosingTimerScheduleContract.validate(doses, editorPayload.maxDoseCount)
        } else {
            DeviceDosingTimerScheduleContract.ValidationError.INVALID_DOSE
        }
        val canSave =
            navController.currentDestination?.id == R.id.deviceDosingTimerScheduleFragment &&
                doses.isNotEmpty() &&
                validation == null
        if (!canSave) {
            validationMessageRes = validation?.toMessageRes()
            return
        }

        parentFragmentManager.setFragmentResult(
            DeviceDosingTimerScheduleContract.RESULT_REQUEST_KEY,
            bundleOf(
                DeviceDosingTimerScheduleContract.RESULT_KEY to
                    DeviceDosingTimerScheduleContract.RESULT_SAVED,
                DeviceDosingTimerScheduleContract.RESULT_SLOT_ID to args.slotId,
                DeviceDosingTimerScheduleContract.RESULT_DOSES_DRAFT to
                    DeviceDosingTimerScheduleContract.encodeDraft(doses)
            )
        )
        navController.navigateUp()
    }

    private companion object {
        const val STATE_DOSES_DRAFT = "timer_schedule_doses_draft"
        const val STATE_VALIDATION_MESSAGE_RES = "timer_schedule_validation_message_res"
        const val NO_MESSAGE_RES = 0
    }
}

private fun restoreTimerDoses(
    savedInstanceState: Bundle?,
    argumentDoses: List<DeviceDosingTimerDose>
): List<DeviceDosingTimerDose> = savedInstanceState
    ?.getString("timer_schedule_doses_draft")
    ?.let(DeviceDosingTimerScheduleContract::decodeDraft)
    ?: argumentDoses

private fun DeviceDosingTimerScheduleContract.ValidationError.toMessageRes(): Int = when (this) {
    DeviceDosingTimerScheduleContract.ValidationError.INVALID_DOSE ->
        R.string.device_dosing_timer_error_invalid_amount
    DeviceDosingTimerScheduleContract.ValidationError.TOO_MANY_DOSES ->
        R.string.device_dosing_timer_error_too_many
    DeviceDosingTimerScheduleContract.ValidationError.DUPLICATE_TIME ->
        R.string.device_dosing_timer_error_duplicate_time
    DeviceDosingTimerScheduleContract.ValidationError.TOTAL_OVERFLOW ->
        R.string.device_dosing_timer_error_total
}
