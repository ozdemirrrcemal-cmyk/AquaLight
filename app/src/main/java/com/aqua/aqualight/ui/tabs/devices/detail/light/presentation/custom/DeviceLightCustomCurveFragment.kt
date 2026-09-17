package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.devices.light.library.DeviceLightLibraryNamePolicy
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentDeviceLightCustomCurveBinding
import com.aqua.aqualight.ui.common.bottomsheet.AquaTimePickerBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.BottomSheetAction
import com.aqua.aqualight.ui.common.bottomsheet.BottomSheetActionStyle
import com.aqua.aqualight.ui.common.bottomsheet.GlobalActionBottomSheet
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import com.aqua.aqualight.ui.common.dialog.UnsavedChangesExitGuard
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import kotlinx.coroutines.launch

class DeviceLightCustomCurveFragment : Fragment(R.layout.fragment_device_light_custom_curve) {

    private val args: DeviceLightCustomCurveFragmentArgs by navArgs()
    private val viewModel: DeviceLightCustomCurveViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
    private var _binding: FragmentDeviceLightCustomCurveBinding? = null
    private val binding get() = _binding!!
    private lateinit var unsavedGuard: UnsavedChangesExitGuard
    private lateinit var effectHandler: DeviceLightCustomCurveEffectHandler
    private var refreshAfterLibrary = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceLightCustomCurveBinding.bind(view)
        viewModel.bind(
            deviceUidText = args.deviceUid,
            restoredDraft = savedInstanceState?.let(DeviceLightCustomDraft::restore),
            restoredDirty = savedInstanceState?.getBoolean(STATE_DRAFT_DIRTY, false) == true
        )
        attachUnsavedGuard()
        effectHandler = DeviceLightCustomCurveEffectHandler(
            fragment = this,
            viewModel = viewModel
        )
        effectHandler.registerResults()
        setupContent()
        renderState()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        if (refreshAfterLibrary) {
            refreshAfterLibrary = false
            viewModel.refreshIfClean()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        viewModel.currentState.draft.writeTo(outState)
        outState.putBoolean(STATE_DRAFT_DIRTY, viewModel.currentState.hasUnsavedChanges)
        super.onSaveInstanceState(outState)
    }

    private fun attachUnsavedGuard() {
        unsavedGuard = UnsavedChangesExitGuard.attach(
            fragment = this,
            configuration = UnsavedChangesExitGuard.Configuration(
                requestKey = UNSAVED_REQUEST_KEY,
                actionId = ACTION_DISCARD_DRAFT,
                hasUnsavedChanges = { viewModel.currentState.hasUnsavedChanges },
                isExitBlocked = { viewModel.currentState.operationInProgress },
                beforeConfirmation = viewModel::clearPreview,
                exit = ::exitScreen
            )
        )
    }

    private fun setupContent() {
        val actions = DeviceLightCustomCurveActions(
            onWeekdayClick = viewModel.dayEditor::toggleWeekday,
            onGraphPointClick = viewModel.pointEditor::selectGraphPoint,
            onGraphPointLongClick = viewModel.pointEditor::requestPointActions,
            onPlayheadChanged = viewModel.pointEditor::updatePlayhead,
            onPlayheadChangeFinished = {
                viewModel.pointEditor.requestPlayheadTime(editSelected = false)
            },
            onPlayheadTimeClick = {
                viewModel.pointEditor.requestPlayheadTime(editSelected = true)
            },
            onChannelChanged = viewModel.pointEditor::updateSelectedChannel,
            onPreviewClick = viewModel::preview,
            onLoadClick = { unsavedGuard.requestAction(::openLibrary) },
            onSaveAsClick = viewModel::requestSaveAs
        )
        binding.customCurveCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceLightCustomCurveScreen(state, actions)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { renderState() } }
                launch { viewModel.effects.collect(effectHandler::handle) }
            }
        }
    }

    private fun renderState() {
        if (_binding == null) return
        val state = viewModel.currentState
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.device_light_custom_curve_title),
                onBackClick = unsavedGuard::requestExit,
                statusIcon = state.connectionVisualState?.toWifiHeaderStatusIcon(requireContext())
            )
        )
        setFragmentGlobalLoading(state.initialLoading)
    }

    private fun openLibrary() {
        viewModel.clearPreview()
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.deviceLightCustomCurveFragment) return
        refreshAfterLibrary = true
        navController.navigate(
            DeviceLightCustomCurveFragmentDirections
                .actionDeviceLightCustomCurveFragmentToDeviceLightLibraryFragment(
                    deviceUid = args.deviceUid,
                    initialTab = INITIAL_TAB_CUSTOM
                )
        )
    }

    private fun exitScreen() {
        viewModel.clearPreview()
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.deviceLightCustomCurveFragment) {
            navController.navigateUp()
        }
    }

    override fun onDestroyView() {
        viewModel.clearPreview()
        setFragmentGlobalLoading(false)
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val UNSAVED_REQUEST_KEY = "device_light_custom_unsaved"
        const val ACTION_DISCARD_DRAFT = "discard_custom_draft"
        const val INITIAL_TAB_CUSTOM = "CUSTOM"
        const val STATE_DRAFT_DIRTY = "device_light_custom_draft_dirty"
    }
}

private class DeviceLightCustomCurveEffectHandler(
    private val fragment: DeviceLightCustomCurveFragment,
    private val viewModel: DeviceLightCustomCurveViewModel
) {

    fun registerResults() {
        fragment.childFragmentManager.setFragmentResultListener(
            TIME_REQUEST_KEY,
            fragment.viewLifecycleOwner
        ) { _, result ->
            val purpose = result.getString(AquaTimePickerBottomSheet.RESULT_PAYLOAD_ID)
                ?.toTimePickerPurpose() ?: return@setFragmentResultListener
            if (result.getString(AquaTimePickerBottomSheet.RESULT_KEY) ==
                AquaTimePickerBottomSheet.RESULT_SELECTED
            ) {
                val minutes = result.getInt(AquaTimePickerBottomSheet.RESULT_MINUTES_OF_DAY)
                val targetTimeMs = minutes * MILLIS_PER_MINUTE
                when (purpose) {
                    is DeviceLightCustomTimePickerPurpose.Add ->
                        viewModel.pointEditor.addOrMovePoint(null, targetTimeMs)
                    is DeviceLightCustomTimePickerPurpose.Move ->
                        viewModel.pointEditor.addOrMovePoint(purpose.originalTimeMs, targetTimeMs)
                }
            } else {
                viewModel.pointEditor.cancelTimeSelection(purpose)
            }
        }
        fragment.childFragmentManager.setFragmentResultListener(
            POINT_ACTIONS_REQUEST_KEY,
            fragment.viewLifecycleOwner
        ) { _, result ->
            if (result.getString(GlobalActionBottomSheet.RESULT_KEY) !=
                GlobalActionBottomSheet.RESULT_ACTION
            ) {
                return@setFragmentResultListener
            }
            val timeMs = result.getString(GlobalActionBottomSheet.RESULT_PAYLOAD_ID)
                ?.toLongOrNull() ?: return@setFragmentResultListener
            viewModel.pointEditor.selectGraphPoint(timeMs)
            when (result.getString(GlobalActionBottomSheet.RESULT_ACTION_ID)) {
                ACTION_EDIT_TIME -> viewModel.pointEditor.requestEditSelectedTime()
                ACTION_DELETE_POINT -> viewModel.pointEditor.deletePoint(timeMs)
            }
        }
        fragment.childFragmentManager.setFragmentResultListener(
            SAVE_AS_REQUEST_KEY,
            fragment.viewLifecycleOwner
        ) { _, result ->
            val saved = result.getString(TextInputBottomSheet.RESULT_KEY) ==
                TextInputBottomSheet.RESULT_SAVED
            if (saved) {
                viewModel.saveAs(result.getString(TextInputBottomSheet.RESULT_VALUE).orEmpty())
            }
        }
    }

    fun handle(effect: DeviceLightCustomCurveEffect) {
        when (effect) {
            is DeviceLightCustomCurveEffect.OpenTimePicker -> showTimePicker(effect.purpose)
            is DeviceLightCustomCurveEffect.OpenPointActions -> showPointActions(effect.timeMs)
            is DeviceLightCustomCurveEffect.OpenSaveAs -> showSaveAs(effect.usedCustomNames)
            is DeviceLightCustomCurveEffect.ShowSuccess -> showMessage(
                message = fragment.getString(effect.messageRes),
                type = BaseActivity.SnackType.SUCCESS
            )
            is DeviceLightCustomCurveEffect.ShowError -> showMessage(
                message = fragment.getString(effect.messageRes),
                type = BaseActivity.SnackType.ERROR
            )
            is DeviceLightCustomCurveEffect.ShowPointLimit -> showMessage(
                message = fragment.resources.getQuantityString(
                    R.plurals.device_light_custom_point_limit_warning,
                    effect.maxPoints,
                    effect.maxPoints
                ),
                type = BaseActivity.SnackType.WARNING
            )
        }
    }

    private fun showTimePicker(purpose: DeviceLightCustomTimePickerPurpose) {
        val occupied = viewModel.currentState.draft.points.mapTo(mutableSetOf()) { point ->
            (point.timeMs / MILLIS_PER_MINUTE).toInt()
        }
        val originalTimeMs = (purpose as? DeviceLightCustomTimePickerPurpose.Move)?.originalTimeMs
        originalTimeMs?.let { occupied.remove((it / MILLIS_PER_MINUTE).toInt()) }
        val selectable = (0 until MINUTES_PER_DAY).filterNot(occupied::contains)
        val preferred = when (purpose) {
            is DeviceLightCustomTimePickerPurpose.Add ->
                (purpose.preferredTimeMs / MILLIS_PER_MINUTE).toInt()
            is DeviceLightCustomTimePickerPurpose.Move ->
                (purpose.originalTimeMs / MILLIS_PER_MINUTE).toInt()
        }
        val initial = selectable.minByOrNull { minute -> kotlin.math.abs(minute - preferred) }
        if (initial != null) {
            val addingPoint = purpose is DeviceLightCustomTimePickerPurpose.Add
            AquaTimePickerBottomSheet.show(
                fragment.childFragmentManager,
                AquaTimePickerBottomSheet.Request(
                    title = fragment.getString(
                        if (addingPoint) {
                            R.string.device_light_custom_add_time_title
                        } else {
                            R.string.device_light_custom_move_time_title
                        }
                    ),
                    message = fragment.getString(
                        if (addingPoint) {
                            R.string.device_light_custom_add_time_message
                        } else {
                            R.string.device_light_custom_move_time_message
                        }
                    ),
                    initialHour = initial / MINUTES_PER_HOUR,
                    initialMinute = initial % MINUTES_PER_HOUR,
                    selectableMinutesOfDay = selectable,
                    confirmText = fragment.getString(R.string.device_light_custom_time_done),
                    cancelText = fragment.getString(R.string.device_light_custom_time_cancel),
                    showSelectionPreview = false,
                    showColumnLabels = false,
                    showFormatHint = false,
                    splitSelectionHighlight = true,
                    helperText = fragment.getString(
                        if (addingPoint) {
                            R.string.device_light_custom_add_time_helper
                        } else {
                            R.string.device_light_custom_move_time_helper
                        }
                    ),
                    cancelAsTextAction = true,
                    resultTarget = AquaTimePickerBottomSheet.ResultTarget(
                        requestKey = TIME_REQUEST_KEY,
                        payloadId = purpose.toPayload()
                    )
                )
            )
        }
    }

    private fun showPointActions(timeMs: Long) {
        val canDelete = viewModel.currentState.canDeleteSelectedPoint &&
            viewModel.currentState.selectedTimeMs == timeMs
        val actions = buildList {
            add(
                BottomSheetAction(
                    id = ACTION_EDIT_TIME,
                    text = fragment.getString(R.string.device_light_custom_edit_time),
                    style = BottomSheetActionStyle.NEUTRAL
                )
            )
            if (canDelete) {
                add(
                    BottomSheetAction(
                        id = ACTION_DELETE_POINT,
                        text = fragment.getString(R.string.device_light_custom_delete_point_action),
                        style = BottomSheetActionStyle.DANGER
                    )
                )
            }
        }
        GlobalActionBottomSheet.show(
            fragmentManager = fragment.childFragmentManager,
            title = fragment.getString(
                R.string.device_light_custom_point_actions_title,
                formatTime(timeMs)
            ),
            message = fragment.getString(R.string.device_light_custom_point_actions_message),
            actions = actions,
            requestKey = POINT_ACTIONS_REQUEST_KEY,
            payloadId = timeMs.toString()
        )
    }

    private fun showSaveAs(usedNames: List<String>) {
        TextInputBottomSheet.show(
            fragmentManager = fragment.childFragmentManager,
            title = fragment.getString(R.string.device_light_library_save_as_title),
            label = fragment.getString(R.string.device_light_library_name_label),
            hint = fragment.getString(R.string.device_light_library_name_hint),
            initialValue = "",
            supportingText = fragment.getString(
                R.string.device_light_library_name_supporting_text
            ),
            saveText = fragment.getString(R.string.device_light_library_save),
            cancelText = fragment.getString(R.string.cancel),
            required = true,
            requiredMessage = fragment.getString(
                R.string.device_light_library_name_required_error
            ),
            requestKey = SAVE_AS_REQUEST_KEY,
            maxLength = DeviceLightLibraryNamePolicy.MAX_LENGTH,
            requestFocus = true,
            disallowedValues = usedNames,
            disallowedMessage = fragment.getString(
                R.string.device_light_library_name_duplicate_error
            )
        )
    }

    private fun showMessage(message: String, type: BaseActivity.SnackType) {
        fragment.setFragmentGlobalLoading(false)
        (fragment.activity as? BaseActivity)?.showSnackBar(message, type)
    }
}

private fun DeviceLightCustomTimePickerPurpose.toPayload(): String = when (this) {
    is DeviceLightCustomTimePickerPurpose.Add -> "$ADD_POINT_PAYLOAD_PREFIX$preferredTimeMs"
    is DeviceLightCustomTimePickerPurpose.Move -> "$MOVE_POINT_PAYLOAD_PREFIX$originalTimeMs"
}

private fun String.toTimePickerPurpose(): DeviceLightCustomTimePickerPurpose? = when {
    startsWith(ADD_POINT_PAYLOAD_PREFIX) -> removePrefix(ADD_POINT_PAYLOAD_PREFIX).toLongOrNull()
        ?.let(DeviceLightCustomTimePickerPurpose::Add)
    startsWith(MOVE_POINT_PAYLOAD_PREFIX) -> removePrefix(MOVE_POINT_PAYLOAD_PREFIX).toLongOrNull()
        ?.let(DeviceLightCustomTimePickerPurpose::Move)
    else -> null
}

private const val TIME_REQUEST_KEY = "device_light_custom_point_time"
private const val POINT_ACTIONS_REQUEST_KEY = "device_light_custom_point_actions"
private const val SAVE_AS_REQUEST_KEY = "device_light_custom_save_as"
private const val ACTION_EDIT_TIME = "edit_time"
private const val ACTION_DELETE_POINT = "delete_point"
private const val ADD_POINT_PAYLOAD_PREFIX = "add:"
private const val MOVE_POINT_PAYLOAD_PREFIX = "move:"
