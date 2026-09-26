package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
import android.widget.Space
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumTankTaxonomy
import com.aqua.aqualight.databinding.FragmentTankHealthAnalysisAddBinding
import com.aqua.aqualight.databinding.ItemTankHealthAnalysisParameterInputBinding
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.dialog.AppDatePickerDialogFragment
import com.aqua.aqualight.ui.common.dialog.AppTimePickerDialogFragment
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.common.AquariumTankTaxonomyText
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import java.util.Calendar

class TankHealthAnalysisAddFragment :
    Fragment(R.layout.fragment_tank_health_analysis_add) {

    private val args: TankHealthAnalysisAddFragmentArgs by navArgs()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var _binding: FragmentTankHealthAnalysisAddBinding? = null
    private val binding get() = _binding!!

    private val selectedCalendar: Calendar = Calendar.getInstance()
    private var temperatureSource: TemperatureSource = TemperatureSource.MANUAL
    private var sensorUiState: TemperatureSensorUiState = TemperatureSensorUiState.Unavailable
    private var manualTemperatureValue: String = ""
    private var tankProfile: String? = null

    private val parameterValues = linkedMapOf<WaterTestParameterId, String>()
    private val additionalParameters = linkedSetOf<WaterTestParameterId>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L) {
            "TankHealthAnalysisAddFragment requires a positive tankId."
        }
        restoreUiState(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankHealthAnalysisAddBinding.bind(view)

        setupHeader()
        setupPickerResultListeners()
        setupWaterTestPickerResultListener()
        setupMeasurementTime()
        setupTemperatureSource()
        setupTemperatureInput()
        setupWaterParameterActions()
        setupNavigation()
        observeTankProfile()

        renderMeasurementTime()
        renderSensorUiState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (_binding != null && temperatureSource == TemperatureSource.MANUAL) {
            manualTemperatureValue = binding.sensorSection.inputTemperature.text
                ?.toString()
                .orEmpty()
        }

        outState.putLong(STATE_MEASUREMENT_TIME_MILLIS, selectedCalendar.timeInMillis)
        outState.putString(STATE_TEMPERATURE_SOURCE, temperatureSource.name)
        outState.putString(STATE_MANUAL_TEMPERATURE, manualTemperatureValue)
        outState.putStringArrayList(
            STATE_ADDITIONAL_PARAMETER_IDS,
            ArrayList(additionalParameters.map { it.name })
        )
        outState.putStringArrayList(
            STATE_PARAMETER_VALUE_IDS,
            ArrayList(parameterValues.keys.map { it.name })
        )
        outState.putStringArrayList(
            STATE_PARAMETER_VALUES,
            ArrayList(parameterValues.values)
        )
        super.onSaveInstanceState(outState)
    }

    private fun restoreUiState(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) return

        val savedMeasurementTime = savedInstanceState.getLong(
            STATE_MEASUREMENT_TIME_MILLIS,
            NO_SAVED_TIME
        )
        if (savedMeasurementTime != NO_SAVED_TIME) {
            selectedCalendar.timeInMillis = savedMeasurementTime
        }

        temperatureSource = savedInstanceState.getString(STATE_TEMPERATURE_SOURCE)
            ?.let { runCatching { TemperatureSource.valueOf(it) }.getOrNull() }
            ?: TemperatureSource.MANUAL
        manualTemperatureValue = savedInstanceState.getString(STATE_MANUAL_TEMPERATURE).orEmpty()

        savedInstanceState.getStringArrayList(STATE_ADDITIONAL_PARAMETER_IDS)
            .orEmpty()
            .mapNotNull { rawId ->
                runCatching { WaterTestParameterId.valueOf(rawId) }.getOrNull()
            }
            .forEach(additionalParameters::add)

        val valueIds = savedInstanceState
            .getStringArrayList(STATE_PARAMETER_VALUE_IDS)
            .orEmpty()
        val values = savedInstanceState
            .getStringArrayList(STATE_PARAMETER_VALUES)
            .orEmpty()
        valueIds.zip(values).forEach { (rawId, value) ->
            runCatching { WaterTestParameterId.valueOf(rawId) }
                .getOrNull()
                ?.let { parameterValues[it] = value }
        }
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.screen_title_tank_health_analysis_add),
                onBackClick = {
                    findNavController().navigateUp()
                }
            )
        )
    }

    private fun setupPickerResultListeners() {
        childFragmentManager.setFragmentResultListener(
            DATE_PICKER_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (
                result.getString(AppDatePickerDialogFragment.RESULT_KEY) !=
                AppDatePickerDialogFragment.RESULT_SELECTED
            ) {
                return@setFragmentResultListener
            }

            selectedCalendar.timeInMillis = result.getLong(
                AppDatePickerDialogFragment.RESULT_MILLIS
            )
            renderMeasurementTime()
        }

        childFragmentManager.setFragmentResultListener(
            TIME_PICKER_REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            if (
                result.getString(AppTimePickerDialogFragment.RESULT_KEY) !=
                AppTimePickerDialogFragment.RESULT_SELECTED
            ) {
                return@setFragmentResultListener
            }

            selectedCalendar.timeInMillis = result.getLong(
                AppTimePickerDialogFragment.RESULT_MILLIS
            )
            selectedCalendar.set(Calendar.SECOND, 0)
            selectedCalendar.set(Calendar.MILLISECOND, 0)
            renderMeasurementTime()
        }
    }

    private fun setupWaterTestPickerResultListener() {
        childFragmentManager.setFragmentResultListener(
            WaterTestPickerBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            val parameterId = result.getString(WaterTestPickerBottomSheet.RESULT_PARAMETER_ID)
                ?.let { rawId ->
                    runCatching { WaterTestParameterId.valueOf(rawId) }.getOrNull()
                }
                ?: return@setFragmentResultListener
            val profile = tankProfile ?: return@setFragmentResultListener
            if (parameterId !in WaterTestProfileUiCatalog.additionalIds(profile)) {
                return@setFragmentResultListener
            }

            additionalParameters.add(parameterId)
            renderWaterParameters()
        }
    }

    private fun setupMeasurementTime() {
        binding.measurementTimeSection.cardDate.setOnClickListener {
            AppDatePickerDialogFragment.show(
                fragmentManager = childFragmentManager,
                requestKey = DATE_PICKER_REQUEST_KEY,
                initialMillis = selectedCalendar.timeInMillis
            )
        }

        binding.measurementTimeSection.cardTime.setOnClickListener {
            AppTimePickerDialogFragment.show(
                fragmentManager = childFragmentManager,
                requestKey = TIME_PICKER_REQUEST_KEY,
                initialMillis = selectedCalendar.timeInMillis
            )
        }
    }

    private fun setupTemperatureSource() {
        binding.sensorSection.cardSensorSource.setOnClickListener {
            if (sensorUiState !is TemperatureSensorUiState.Reading) {
                return@setOnClickListener
            }
            if (temperatureSource == TemperatureSource.MANUAL) {
                manualTemperatureValue = binding.sensorSection.inputTemperature.text
                    ?.toString()
                    .orEmpty()
            }
            temperatureSource = TemperatureSource.SENSOR
            renderTemperatureSource()
        }
        binding.sensorSection.cardManualSource.setOnClickListener {
            temperatureSource = TemperatureSource.MANUAL
            renderTemperatureSource()
        }
    }

    private fun setupTemperatureInput() {
        binding.sensorSection.inputTemperature.setText(manualTemperatureValue)
        binding.sensorSection.inputTemperature.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    if (temperatureSource == TemperatureSource.MANUAL) {
                        manualTemperatureValue = s?.toString().orEmpty()
                    }
                }

                override fun afterTextChanged(s: Editable?) = Unit
            }
        )
    }

    private fun setupWaterParameterActions() {
        binding.waterParametersSection.btnAddTest.setOnClickListener {
            val profile = tankProfile ?: return@setOnClickListener
            val available = WaterTestProfileUiCatalog.additionalIds(profile)
                .filterNot(additionalParameters::contains)
            WaterTestPickerBottomSheet.show(
                fragmentManager = childFragmentManager,
                tankProfile = profile,
                parameterIds = available
            )
        }
    }

    private fun setupNavigation() {
        binding.btnHistory.setOnClickListener {
            findNavController().navigateSafelyFrom(
                sourceDestinationId = R.id.tankHealthAnalysisAddFragment,
                directions = TankHealthAnalysisAddFragmentDirections
                    .actionTankHealthAnalysisAddFragmentToTankHealthAnalysisHistoryFragment(
                        args.tankId
                    )
            )
        }

        // Persistence is intentionally deferred to the data-integration stage.
        binding.btnSaveAnalysis.setOnClickListener { }
    }

    private fun observeTankProfile() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            val nextProfile = tanks
                .firstOrNull { tank -> tank.id == args.tankId }
                ?.tankType
                ?.takeIf(AquariumTankTaxonomy::isSupportedTankType)

            if (nextProfile == tankProfile) return@observe

            tankProfile = nextProfile
            val allowedAdditional = nextProfile
                ?.let(WaterTestProfileUiCatalog::additionalIds)
                .orEmpty()
                .toSet()
            additionalParameters.retainAll(allowedAdditional)
            renderWaterParameters()
        }
    }

    private fun renderMeasurementTime() {
        binding.measurementTimeSection.tvDateValue.text = LocaleFormatter.formatDate(
            requireContext(),
            selectedCalendar.timeInMillis
        )
        binding.measurementTimeSection.tvTimeValue.text = LocaleFormatter.formatTime(
            requireContext(),
            selectedCalendar.timeInMillis
        )
    }

    private fun renderSensorUiState() {
        binding.sensorSection.tvSensorStatusValue.text = when (val state = sensorUiState) {
            TemperatureSensorUiState.Unavailable ->
                getString(R.string.tank_health_analysis_sensor_unavailable)

            TemperatureSensorUiState.Loading ->
                getString(R.string.tank_health_analysis_sensor_loading)

            is TemperatureSensorUiState.Available ->
                getString(R.string.tank_health_analysis_sensor_available, state.deviceName)

            is TemperatureSensorUiState.Reading ->
                getString(
                    R.string.tank_health_analysis_sensor_reading,
                    state.deviceName,
                    state.temperatureText
                )

            is TemperatureSensorUiState.Stale ->
                getString(R.string.tank_health_analysis_sensor_stale)

            TemperatureSensorUiState.Error ->
                getString(R.string.tank_health_analysis_sensor_error)
        }

        val sensorSelectable = sensorUiState is TemperatureSensorUiState.Reading
        binding.sensorSection.cardSensorSource.isEnabled = sensorSelectable
        binding.sensorSection.cardSensorSource.isClickable = sensorSelectable
        binding.sensorSection.cardSensorSource.alpha = if (sensorSelectable) {
            ENABLED_ALPHA
        } else {
            DISABLED_ALPHA
        }

        if (!sensorSelectable && temperatureSource == TemperatureSource.SENSOR) {
            temperatureSource = TemperatureSource.MANUAL
        }
        renderTemperatureSource()
    }

    private fun renderTemperatureSource() {
        val context = requireContext()
        val primary = ContextCompat.getColor(context, R.color.aqua_accent_primary)
        val transparent = ContextCompat.getColor(context, R.color.aqua_color_transparent)
        val outline = ContextCompat.getColor(context, R.color.aqua_card_metric_outline)
        val selectedText = ContextCompat.getColor(context, R.color.aqua_content_on_dark)
        val unselectedText = ContextCompat.getColor(context, R.color.aqua_card_text_secondary)

        val sensorReading = sensorUiState as? TemperatureSensorUiState.Reading
        val sensorSelected = temperatureSource == TemperatureSource.SENSOR &&
            sensorReading != null

        binding.sensorSection.cardSensorSource.setCardBackgroundColor(
            if (sensorSelected) primary else transparent
        )
        binding.sensorSection.cardSensorSource.strokeColor =
            if (sensorSelected) primary else outline
        binding.sensorSection.tvSensorSource.setTextColor(
            if (sensorSelected) selectedText else unselectedText
        )
        binding.sensorSection.tvSensorSource.setTypeface(
            null,
            if (sensorSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
        )

        binding.sensorSection.cardManualSource.setCardBackgroundColor(
            if (sensorSelected) transparent else primary
        )
        binding.sensorSection.cardManualSource.strokeColor =
            if (sensorSelected) outline else primary
        binding.sensorSection.tvManualSource.setTextColor(
            if (sensorSelected) unselectedText else selectedText
        )
        binding.sensorSection.tvManualSource.setTypeface(
            null,
            if (sensorSelected) android.graphics.Typeface.NORMAL else android.graphics.Typeface.BOLD
        )

        binding.sensorSection.cardSensorReadingBadge.isVisible = sensorSelected

        val temperatureInput = binding.sensorSection.inputTemperature
        val targetValue = if (sensorSelected) {
            requireNotNull(sensorReading).temperatureText
        } else {
            manualTemperatureValue
        }
        if (temperatureInput.text?.toString() != targetValue) {
            temperatureInput.setText(targetValue)
        }

        temperatureInput.isFocusable = !sensorSelected
        temperatureInput.isFocusableInTouchMode = !sensorSelected
        temperatureInput.isClickable = !sensorSelected
        temperatureInput.isCursorVisible = !sensorSelected
    }

    private fun renderWaterParameters() {
        val section = binding.waterParametersSection
        val profile = tankProfile

        if (profile == null) {
            section.tvProfileContext.setText(R.string.tank_health_analysis_profile_missing)
            section.tvRecommendedTitle.isVisible = false
            section.recommendedParametersContainer.removeAllViews()
            section.tvAdditionalTitle.isVisible = false
            section.additionalParametersContainer.isVisible = false
            section.additionalParametersContainer.removeAllViews()
            section.btnAddTest.isEnabled = false
            return
        }

        section.tvProfileContext.text = getString(
            R.string.tank_health_analysis_profile_context,
            AquariumTankTaxonomyText.tankTypeLabel(requireContext(), profile)
        )
        section.tvRecommendedTitle.isVisible = true

        val recommendedModels = WaterTestProfileUiCatalog.recommendedIds(profile).map { id ->
            WaterTestProfileUiCatalog.model(
                tankProfile = profile,
                id = id,
                importance = WaterTestImportance.RECOMMENDED,
                value = parameterValues[id].orEmpty()
            )
        }
        renderParameterContainer(
            container = section.recommendedParametersContainer,
            models = recommendedModels
        )

        val additionalOrder = WaterTestProfileUiCatalog.additionalIds(profile)
        val additionalModels = additionalOrder
            .filter(additionalParameters::contains)
            .map { id ->
                WaterTestProfileUiCatalog.model(
                    tankProfile = profile,
                    id = id,
                    importance = WaterTestImportance.ADDITIONAL,
                    value = parameterValues[id].orEmpty()
                )
            }

        val hasAdditional = additionalModels.isNotEmpty()
        section.tvAdditionalTitle.isVisible = hasAdditional
        section.additionalParametersContainer.isVisible = hasAdditional
        renderParameterContainer(
            container = section.additionalParametersContainer,
            models = additionalModels
        )

        val availableAdditional = additionalOrder.filterNot(additionalParameters::contains)
        section.btnAddTest.isEnabled = availableAdditional.isNotEmpty()
        section.btnAddTest.setText(
            if (availableAdditional.isEmpty()) {
                R.string.tank_health_analysis_all_additional_tests_added
            } else {
                R.string.tank_health_analysis_add_test
            }
        )
    }

    private fun renderParameterContainer(
        container: LinearLayout,
        models: List<WaterTestParameterUiModel>
    ) {
        container.removeAllViews()
        val spacing = resources.getDimensionPixelSize(R.dimen.aqua_size_4)
        val rowSpacing = resources.getDimensionPixelSize(R.dimen.aqua_size_8)

        models.chunked(PARAMETERS_PER_ROW).forEachIndexed { rowIndex, rowModels ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                baselineAligned = false
            }
            val rowParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (rowIndex > 0) topMargin = rowSpacing
            }

            rowModels.forEachIndexed { column, model ->
                val itemBinding = ItemTankHealthAnalysisParameterInputBinding.inflate(
                    layoutInflater,
                    row,
                    false
                )
                bindParameterInput(itemBinding, model)
                row.addView(
                    itemBinding.root,
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        marginEnd = if (column == 0) spacing else 0
                        marginStart = if (column == 0) 0 else spacing
                    }
                )
            }

            if (rowModels.size < PARAMETERS_PER_ROW) {
                row.addView(
                    Space(requireContext()),
                    LinearLayout.LayoutParams(
                        0,
                        1,
                        1f
                    ).apply {
                        marginStart = spacing
                    }
                )
            }

            container.addView(row, rowParams)
        }
    }

    private fun bindParameterInput(
        itemBinding: ItemTankHealthAnalysisParameterInputBinding,
        model: WaterTestParameterUiModel
    ) {
        itemBinding.root.id = View.generateViewId()
        itemBinding.inputLayout.id = View.generateViewId()
        itemBinding.inputValue.id = View.generateViewId()
        itemBinding.inputValue.isSaveEnabled = false

        itemBinding.tvParameterName.setText(model.nameRes)
        itemBinding.tvParameterSymbol.isVisible = model.symbolRes != null
        model.symbolRes?.let { symbolRes -> itemBinding.tvParameterSymbol.setText(symbolRes) }
        itemBinding.inputLayout.suffixText = model.unitRes?.let { unitRes -> getString(unitRes) }
        itemBinding.inputValue.setText(model.value)

        val accessibleLabel = buildString {
            append(getString(model.nameRes))
            model.symbolRes?.let { symbolRes ->
                append(", ")
                append(getString(symbolRes))
            }
        }
        itemBinding.inputValue.contentDescription = accessibleLabel

        val removable = model.importance == WaterTestImportance.ADDITIONAL
        itemBinding.btnRemoveParameter.isVisible = removable
        itemBinding.btnRemoveParameter.setOnClickListener(
            if (removable) {
                View.OnClickListener {
                    additionalParameters.remove(model.id)
                    parameterValues.remove(model.id)
                    renderWaterParameters()
                }
            } else {
                null
            }
        )

        itemBinding.inputValue.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    parameterValues[model.id] = s?.toString().orEmpty()
                }

                override fun afterTextChanged(s: Editable?) = Unit
            }
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private enum class TemperatureSource {
        SENSOR,
        MANUAL
    }

    private companion object {
        const val DATE_PICKER_REQUEST_KEY = "tank_health_analysis_date_picker"
        const val TIME_PICKER_REQUEST_KEY = "tank_health_analysis_time_picker"

        const val STATE_MEASUREMENT_TIME_MILLIS = "measurement_time_millis"
        const val STATE_TEMPERATURE_SOURCE = "temperature_source"
        const val STATE_MANUAL_TEMPERATURE = "manual_temperature"
        const val STATE_ADDITIONAL_PARAMETER_IDS = "additional_parameter_ids"
        const val STATE_PARAMETER_VALUE_IDS = "parameter_value_ids"
        const val STATE_PARAMETER_VALUES = "parameter_values"

        const val NO_SAVED_TIME = -1L
        const val PARAMETERS_PER_ROW = 2
        const val ENABLED_ALPHA = 1f
        const val DISABLED_ALPHA = 0.5f
    }
}
