package com.aqua.aqualight.ui.tabs.devices.detail.light.presentation.quicksetup

import android.os.Bundle
import android.text.InputType
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
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentDeviceLightQuickSetupBinding
import com.aqua.aqualight.ui.common.bottomsheet.TextInputBottomSheet
import com.aqua.aqualight.ui.common.dialog.AppTimePickerDialogFragment
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.common.loading.setFragmentGlobalLoading
import java.util.Calendar
import kotlinx.coroutines.launch

class DeviceLightQuickSetupFragment : Fragment(R.layout.fragment_device_light_quick_setup) {

    private val args: DeviceLightQuickSetupFragmentArgs by navArgs()
    private val viewModel: DeviceLightQuickSetupViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
    private var _binding: FragmentDeviceLightQuickSetupBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDeviceLightQuickSetupBinding.bind(view)
        registerInputResults()
        setupContent()
        viewModel.bind(args.deviceUid)
        renderState(viewModel.uiState.value)
        observeViewModel()
    }

    private fun setupContent() {
        val actions = DeviceLightQuickSetupActions(
            onWaterDepthRequested = { showMeasurement(MEASUREMENT_WATER_DEPTH) },
            onFixtureHeightRequested = { showMeasurement(MEASUREMENT_FIXTURE_HEIGHT) },
            onProgramEndRequested = { showTime(TIME_PROGRAM_END) },
            onDaylightStartRequested = { showTime(TIME_DAYLIGHT_START) },
            onDaylightEndRequested = { showTime(TIME_DAYLIGHT_END) },
            onPlantDemandSelected = viewModel::selectPlantDemand,
            onPlantCoverageSelected = viewModel::selectPlantCoverage,
            onCo2ReadinessSelected = viewModel::selectCo2Readiness,
            onDaylightSelected = viewModel::selectDaylight,
            onSurfaceGrowthSelected = viewModel::selectSurfaceGrowth,
            onShelterSelected = viewModel::selectShelter,
            onToggleDetails = viewModel::toggleDetails,
            onEditInstalledPlan = { viewModel.setInstalledPlanEditing(true) },
            onCancelEdit = { viewModel.setInstalledPlanEditing(false) },
            onApply = viewModel::apply
        )
        binding.quickSetupCompose.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DeviceLightQuickSetupScreen(state, actions)
            }
        }
    }

    private fun registerInputResults() {
        childFragmentManager.setFragmentResultListener(
            MEASUREMENT_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(TextInputBottomSheet.RESULT_KEY) !=
                TextInputBottomSheet.RESULT_SAVED
            ) return@setFragmentResultListener
            val value = result.getString(TextInputBottomSheet.RESULT_VALUE)
                ?.trim()
                ?.toIntOrNull()
                ?: return@setFragmentResultListener
            val payload = result.getString(TextInputBottomSheet.RESULT_PAYLOAD_ID)
            val state = viewModel.uiState.value
            val accepted = when (payload) {
                MEASUREMENT_WATER_DEPTH -> {
                    val tankHeight = state.tank?.tankHeightCm ?: return@setFragmentResultListener
                    if (value !in QUICK_SETUP_WATER_DEPTH_MIN_CM..tankHeight) false else {
                        viewModel.setWaterDepthCm(value)
                        true
                    }
                }
                MEASUREMENT_FIXTURE_HEIGHT -> {
                    if (value !in QUICK_SETUP_FIXTURE_HEIGHT_MIN_CM..
                        QUICK_SETUP_FIXTURE_HEIGHT_MAX_CM
                    ) {
                        false
                    } else {
                        viewModel.setFixtureHeightAboveWaterCm(value)
                        true
                    }
                }
                else -> false
            }
            if (!accepted) {
                showMessage(R.string.device_light_quick_setup_measurement_out_of_range, false)
            }
        }
        childFragmentManager.setFragmentResultListener(
            TIME_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (result.getString(AppTimePickerDialogFragment.RESULT_KEY) !=
                AppTimePickerDialogFragment.RESULT_SELECTED
            ) return@setFragmentResultListener
            val selectedMinute = Calendar.getInstance().run {
                timeInMillis = result.getLong(AppTimePickerDialogFragment.RESULT_MILLIS)
                get(Calendar.HOUR_OF_DAY) * MINUTES_PER_HOUR + get(Calendar.MINUTE)
            }
            when (result.getString(AppTimePickerDialogFragment.RESULT_PAYLOAD_ID)) {
                TIME_PROGRAM_END -> {
                    if (selectedMinute < QUICK_SETUP_MINIMUM_END_MINUTE) {
                        showMessage(R.string.device_light_quick_setup_end_time_invalid, false)
                    } else {
                        viewModel.setProgramEndMinute(selectedMinute)
                    }
                }
                TIME_DAYLIGHT_START -> {
                    val end = viewModel.uiState.value.daylightEndMinute
                    if (end != null && selectedMinute >= end) {
                        showMessage(R.string.device_light_quick_setup_daylight_window_invalid, false)
                    } else {
                        viewModel.setDaylightStartMinute(selectedMinute)
                    }
                }
                TIME_DAYLIGHT_END -> {
                    val start = viewModel.uiState.value.daylightStartMinute
                    if (start != null && selectedMinute <= start) {
                        showMessage(R.string.device_light_quick_setup_daylight_window_invalid, false)
                    } else {
                        viewModel.setDaylightEndMinute(selectedMinute)
                    }
                }
            }
        }
    }

    private fun showMeasurement(payload: String) {
        val state = viewModel.uiState.value
        val water = payload == MEASUREMENT_WATER_DEPTH
        val maximum = if (water) {
            state.tank?.tankHeightCm ?: return
        } else {
            QUICK_SETUP_FIXTURE_HEIGHT_MAX_CM
        }
        val minimum = if (water) {
            QUICK_SETUP_WATER_DEPTH_MIN_CM
        } else {
            QUICK_SETUP_FIXTURE_HEIGHT_MIN_CM
        }
        val currentValue = if (water) state.waterDepthCm else state.fixtureHeightAboveWaterCm
        TextInputBottomSheet.show(
            fragmentManager = childFragmentManager,
            title = getString(R.string.device_light_quick_setup_measurement_sheet_title),
            label = getString(
                if (water) {
                    R.string.device_light_quick_setup_water_depth
                } else {
                    R.string.device_light_quick_setup_fixture_height
                }
            ),
            hint = getString(R.string.device_light_quick_setup_measurement_sheet_hint),
            initialValue = currentValue?.toString().orEmpty(),
            supportingText = getString(
                if (water) {
                    R.string.device_light_quick_setup_water_depth_help
                } else {
                    R.string.device_light_quick_setup_fixture_height_help
                },
                minimum,
                maximum
            ),
            suffixText = getString(R.string.device_light_quick_setup_cm_suffix),
            saveText = getString(R.string.device_light_quick_setup_measurement_save),
            cancelText = getString(R.string.device_light_quick_setup_measurement_cancel),
            required = true,
            requiredMessage = getString(
                R.string.device_light_quick_setup_measurement_required
            ),
            requestKey = MEASUREMENT_REQUEST_KEY,
            payloadId = payload,
            maxLength = 3,
            inputType = InputType.TYPE_CLASS_NUMBER,
            minimumNumericValueExclusive = minimum.toDouble() - 1.0,
            requestFocus = true
        )
    }

    private fun showTime(payload: String) {
        val state = viewModel.uiState.value
        val existingMinute = when (payload) {
            TIME_PROGRAM_END -> state.programEndMinute
            TIME_DAYLIGHT_START -> state.daylightStartMinute
            TIME_DAYLIGHT_END -> state.daylightEndMinute
            else -> null
        }
        val initialMinute = existingMinute ?: currentValidMinute(payload)
        AppTimePickerDialogFragment.show(
            fragmentManager = childFragmentManager,
            requestKey = TIME_REQUEST_KEY,
            initialMillis = minuteOfDayToCurrentMillis(initialMinute),
            payloadId = payload
        )
    }

    private fun currentValidMinute(payload: String): Int {
        val now = Calendar.getInstance()
        val currentMinute = now.get(Calendar.HOUR_OF_DAY) * MINUTES_PER_HOUR +
            now.get(Calendar.MINUTE)
        return when (payload) {
            TIME_PROGRAM_END -> currentMinute.coerceAtLeast(QUICK_SETUP_MINIMUM_END_MINUTE)
            TIME_DAYLIGHT_START -> DEFAULT_DAYLIGHT_START_MINUTE
            TIME_DAYLIGHT_END -> DEFAULT_DAYLIGHT_END_MINUTE
            else -> currentMinute
        }
    }

    private fun minuteOfDayToCurrentMillis(minute: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minute / MINUTES_PER_HOUR)
        set(Calendar.MINUTE, minute % MINUTES_PER_HOUR)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::renderState) }
                launch { viewModel.effects.collect(::renderEffect) }
            }
        }
    }

    private fun renderState(state: DeviceLightQuickSetupUiState) {
        if (_binding == null) return
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.device_menu_quick_setup_title),
                onBackClick = { findNavController().navigateUp() }
            )
        )
        setFragmentGlobalLoading(state.initialLoading || state.applying)
    }

    private fun renderEffect(effect: DeviceLightQuickSetupEffect) {
        if (_binding == null) return
        when (effect) {
            DeviceLightQuickSetupEffect.Applied -> {
                showMessage(R.string.device_light_quick_setup_applied, true)
            }
            is DeviceLightQuickSetupEffect.CloseUnavailable -> {
                showMessage(effect.messageRes, false)
                findNavController().navigateUp()
            }
            is DeviceLightQuickSetupEffect.ShowMessage -> showMessage(effect.messageRes, false)
        }
    }

    private fun showMessage(messageRes: Int, success: Boolean) {
        (activity as? BaseActivity)?.showSnackBar(
            message = getString(messageRes),
            type = if (success) BaseActivity.SnackType.SUCCESS else BaseActivity.SnackType.ERROR
        )
    }

    override fun onDestroyView() {
        setFragmentGlobalLoading(false)
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val MEASUREMENT_REQUEST_KEY = "quick_setup_measurement"
        const val MEASUREMENT_WATER_DEPTH = "water_depth"
        const val MEASUREMENT_FIXTURE_HEIGHT = "fixture_height"
        const val TIME_REQUEST_KEY = "quick_setup_time"
        const val TIME_PROGRAM_END = "program_end"
        const val TIME_DAYLIGHT_START = "daylight_start"
        const val TIME_DAYLIGHT_END = "daylight_end"
        const val MINUTES_PER_HOUR = 60
        const val DEFAULT_DAYLIGHT_START_MINUTE = 8 * MINUTES_PER_HOUR
        const val DEFAULT_DAYLIGHT_END_MINUTE = 17 * MINUTES_PER_HOUR
    }
}
