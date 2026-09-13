package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.menu

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
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import com.aqua.aqualight.ui.common.dialog.UnsavedChangesExitGuard
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom.DeviceLightCustomCurveActions
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom.DeviceLightCustomCurveEffect
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom.DeviceLightCustomCurveScreen
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom.DeviceLightCustomCurveViewModel
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom.DeviceLightCustomDraft
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom.MILLIS_PER_MINUTE
import com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.custom.MINUTES_PER_DAY
import kotlinx.coroutines.launch

class DeviceLightCustomCurveFragment : Fragment(R.layout.fragment_device_light_custom_curve) {

    private val args: DeviceLightCustomCurveFragmentArgs by navArgs()
    private val viewModel: DeviceLightCustomCurveViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
    private var _binding: FragmentDeviceLightCustomCurveBinding? = null
    private val binding get() = _binding!!
    private lateinit var unsavedGuard: UnsavedChangesExitGuard
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
        registerResults()
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
            onEveryDayClick = viewModel::selectEveryDay,
            onWeekdayClick = viewModel::toggleWeekday,
            onGraphTimeClick = viewModel::selectOrAddGraphTime,
            onAddPointClick = viewModel::requestAddPoint,
            onEditTimeClick = viewModel::requestEditSelectedTime,
            onDuplicatePointClick = viewModel::duplicateSelectedPoint,
            onDeletePointClick = viewModel::deleteSelectedPoint,
            onChannelChanged = viewModel::updateSelectedChannel,
            onChannelStep = viewModel::stepSelectedChannel,
            onPreviewTimeChanged = viewModel::updatePreviewTime,
            onPreviewClick = viewModel::preview,
            onLoadClick = { unsavedGuard.requestAction(::openLibrary) },
            onSaveAsClick = viewModel::requestSaveAs,
            onResetClick = { unsavedGuard.requestAction(viewModel::resetToDevice) }
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
                launch { viewModel.effects.collect(::handleEffect) }
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

    private fun handleEffect(effect: DeviceLightCustomCurveEffect) {
        when (effect) {
            is DeviceLightCustomCurveEffect.OpenTimePicker -> showTimePicker(effect.originalTimeMs)
            is DeviceLightCustomCurveEffect.OpenSaveAs -> showSaveAs(effect.usedCustomNames)
            DeviceLightCustomCurveEffect.OpenLibrary -> openLibrary()
            is DeviceLightCustomCurveEffect.ShowSuccess -> showMessage(effect.messageRes, true)
            is DeviceLightCustomCurveEffect.ShowError -> showMessage(effect.messageRes, false)
        }
    }

    private fun registerResults() {
        childFragmentManager.setFragmentResultListener(TIME_REQUEST_KEY, viewLifecycleOwner) { _, result ->
            if (result.getString(AquaTimePickerBottomSheet.RESULT_KEY) !=
                AquaTimePickerBottomSheet.RESULT_SELECTED
            ) return@setFragmentResultListener
            val original = result.getString(AquaTimePickerBottomSheet.RESULT_PAYLOAD_ID)
                ?.takeUnless { value -> value == NEW_POINT_PAYLOAD }
                ?.toLongOrNull()
            val minutes = result.getInt(AquaTimePickerBottomSheet.RESULT_MINUTES_OF_DAY)
            viewModel.addOrMovePoint(original, minutes * MILLIS_PER_MINUTE)
        }
        childFragmentManager.setFragmentResultListener(SAVE_AS_REQUEST_KEY, viewLifecycleOwner) { _, result ->
            if (result.getString(TextInputBottomSheet.RESULT_KEY) == TextInputBottomSheet.RESULT_SAVED) {
                viewModel.saveAs(result.getString(TextInputBottomSheet.RESULT_VALUE).orEmpty())
            }
        }
    }

    private fun showTimePicker(originalTimeMs: Long?) {
        val occupied = viewModel.currentState.draft.points.mapTo(mutableSetOf()) { point ->
            (point.timeMs / MILLIS_PER_MINUTE).toInt()
        }
        originalTimeMs?.let { occupied.remove((it / MILLIS_PER_MINUTE).toInt()) }
        val selectable = (0 until MINUTES_PER_DAY).filterNot(occupied::contains)
        if (selectable.isEmpty()) return
        val preferred = originalTimeMs?.div(MILLIS_PER_MINUTE)?.toInt() ?: NOON_MINUTES
        val initial = selectable.minByOrNull { minute -> kotlin.math.abs(minute - preferred) }
            ?: return
        AquaTimePickerBottomSheet.show(
            childFragmentManager,
            AquaTimePickerBottomSheet.Request(
                title = getString(R.string.device_light_custom_point_time_title),
                message = getString(R.string.device_light_custom_point_time_message),
                initialHour = initial / MINUTES_PER_HOUR,
                initialMinute = initial % MINUTES_PER_HOUR,
                selectableMinutesOfDay = selectable,
                confirmText = getString(R.string.device_light_custom_point_time_confirm),
                cancelText = getString(R.string.cancel),
                resultTarget = AquaTimePickerBottomSheet.ResultTarget(
                    requestKey = TIME_REQUEST_KEY,
                    payloadId = originalTimeMs?.toString() ?: NEW_POINT_PAYLOAD
                )
            )
        )
    }

    private fun showSaveAs(usedNames: List<String>) {
        TextInputBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.device_light_library_save_as_title),
            label = getString(R.string.device_light_library_name_label),
            hint = getString(R.string.device_light_library_name_hint),
            initialValue = "",
            supportingText = getString(R.string.device_light_library_name_supporting_text),
            saveText = getString(R.string.device_light_library_save),
            cancelText = getString(R.string.cancel),
            required = true,
            requiredMessage = getString(R.string.device_light_library_name_required_error),
            requestKey = SAVE_AS_REQUEST_KEY,
            maxLength = DeviceLightLibraryNamePolicy.MAX_LENGTH,
            requestFocus = true,
            disallowedValues = usedNames,
            disallowedMessage = getString(R.string.device_light_library_name_duplicate_error)
        )
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

    private fun showMessage(messageRes: Int, success: Boolean) {
        setFragmentGlobalLoading(false)
        (activity as? BaseActivity)?.showSnackBar(
            message = getString(messageRes),
            type = if (success) BaseActivity.SnackType.SUCCESS else BaseActivity.SnackType.ERROR
        )
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
        const val TIME_REQUEST_KEY = "device_light_custom_point_time"
        const val SAVE_AS_REQUEST_KEY = "device_light_custom_save_as"
        const val NEW_POINT_PAYLOAD = "new"
        const val INITIAL_TAB_CUSTOM = "CUSTOM"
        const val MINUTES_PER_HOUR = 60
        const val NOON_MINUTES = 12 * MINUTES_PER_HOUR
        const val STATE_DRAFT_DIRTY = "device_light_custom_draft_dirty"
    }
}
