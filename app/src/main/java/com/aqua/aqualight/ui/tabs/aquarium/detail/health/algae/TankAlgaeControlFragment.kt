package com.aqua.aqualight.ui.tabs.aquarium.detail.health.algae

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeDensity
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeObservationLocation
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeTrend
import com.aqua.aqualight.application.aquarium.health.algae.AlgaeTypeId
import com.aqua.aqualight.databinding.FragmentTankAlgaeControlBinding
import com.aqua.aqualight.ui.tabs.aquarium.detail.health.TankHealthFragmentDirections
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import com.google.android.material.button.MaterialButton
import java.util.Locale

class TankAlgaeControlFragment : Fragment(R.layout.fragment_tank_algae_control) {

    private var _binding: FragmentTankAlgaeControlBinding? = null
    private val binding get() = _binding!!

    private var tankId: Long = 0L
    private var formVisible: Boolean = false
    private var selectedType: AlgaeTypeId? = null
    private val selectedLocations = linkedSetOf<AlgaeObservationLocation>()
    private var selectedDensity: AlgaeDensity? = null
    private var selectedTrend: AlgaeTrend? = null

    private lateinit var commonAdapter: AlgaeCatalogAdapter
    private lateinit var typeAdapter: AlgaeCatalogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tankId = requireArguments().getLong(ARG_TANK_ID)
        require(tankId > 0L) {
            "TankAlgaeControlFragment requires a positive tankId."
        }

        restoreState(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankAlgaeControlBinding.bind(view)

        setupCatalogs()
        setupSearch()
        setupLocationButtons()
        setupDensityButtons()
        setupTrendButtons()
        setupActions()
        restoreSelections()
        renderMode()
        updateAnalyzeEnabled()
    }

    fun openNewObservation() {
        showObservationForm(preselectedType = null)
    }

    private fun setupCatalogs() {
        commonAdapter = AlgaeCatalogAdapter { type ->
            showObservationForm(type)
        }
        binding.commonAlgaeRecycler.apply {
            layoutManager = GridLayoutManager(requireContext(), COMMON_GRID_COLUMNS)
            adapter = commonAdapter
            itemAnimator = null
        }
        commonAdapter.submitItems(
            AlgaeUiCatalog.definitions.take(COMMON_TYPE_COUNT)
        )

        typeAdapter = AlgaeCatalogAdapter { type ->
            selectedType = type
            typeAdapter.setSelected(type)
            updateAnalyzeEnabled()
        }
        binding.algaeTypeRecycler.apply {
            layoutManager = GridLayoutManager(requireContext(), TYPE_GRID_COLUMNS)
            adapter = typeAdapter
            itemAnimator = null
        }
        typeAdapter.submitItems(
            definitions = AlgaeUiCatalog.definitions,
            selected = selectedType
        )
    }

    private fun setupSearch() {
        binding.etAlgaeSearch.doAfterTextChanged { editable ->
            val query = editable?.toString().orEmpty().trim()
            val normalized = query.lowercase(Locale.getDefault())
            val definitions = if (normalized.isBlank()) {
                AlgaeUiCatalog.definitions
            } else {
                AlgaeUiCatalog.definitions.filter { definition ->
                    getString(definition.nameRes)
                        .lowercase(Locale.getDefault())
                        .contains(normalized)
                }
            }
            typeAdapter.submitItems(
                definitions = definitions,
                selected = selectedType
            )
        }
    }

    private fun setupLocationButtons() {
        locationButtons().forEach { (button, location) ->
            button.setOnClickListener {
                if (location in selectedLocations) {
                    selectedLocations -= location
                } else {
                    selectedLocations += location
                }
                renderChoiceButton(
                    button = button,
                    selected = location in selectedLocations
                )
                updateAnalyzeEnabled()
            }
        }
    }

    private fun setupDensityButtons() {
        densityButtons().forEach { (button, density) ->
            button.setOnClickListener {
                selectedDensity = density
                densityButtons().forEach { (candidate, value) ->
                    renderChoiceButton(
                        button = candidate,
                        selected = value == selectedDensity
                    )
                }
                updateAnalyzeEnabled()
            }
        }
    }

    private fun setupTrendButtons() {
        trendButtons().forEach { (button, trend) ->
            button.setOnClickListener {
                selectedTrend = trend
                trendButtons().forEach { (candidate, value) ->
                    renderChoiceButton(
                        button = candidate,
                        selected = value == selectedTrend
                    )
                }
                updateAnalyzeEnabled()
            }
        }
    }

    private fun setupActions() {
        binding.btnNewObservation.setOnClickListener {
            showObservationForm(preselectedType = null)
        }
        binding.btnBackToOverview.setOnClickListener {
            showOverview()
        }
        binding.btnAnalyze.setOnClickListener {
            openAnalysis()
        }
    }

    private fun showObservationForm(preselectedType: AlgaeTypeId?) {
        formVisible = true
        if (preselectedType != null) {
            selectedType = preselectedType
            typeAdapter.setSelected(preselectedType)
        }
        renderMode()
        updateAnalyzeEnabled()
        binding.algaeObservationScroll.scrollTo(0, 0)
    }

    private fun showOverview() {
        formVisible = false
        renderMode()
        binding.algaeOverviewScroll.scrollTo(0, 0)
    }

    private fun renderMode() {
        binding.algaeOverviewScroll.isVisible = !formVisible
        binding.algaeObservationScroll.isVisible = formVisible
    }

    private fun restoreSelections() {
        typeAdapter.setSelected(selectedType)
        locationButtons().forEach { (button, location) ->
            renderChoiceButton(
                button = button,
                selected = location in selectedLocations
            )
        }
        densityButtons().forEach { (button, density) ->
            renderChoiceButton(
                button = button,
                selected = density == selectedDensity
            )
        }
        trendButtons().forEach { (button, trend) ->
            renderChoiceButton(
                button = button,
                selected = trend == selectedTrend
            )
        }
    }

    private fun renderChoiceButton(
        button: MaterialButton,
        selected: Boolean
    ) {
        val context = requireContext()
        button.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                context,
                if (selected) {
                    R.color.aqua_surface_action
                } else {
                    R.color.aqua_button_secondary_container_tint
                }
            )
        )
        button.strokeColor = ColorStateList.valueOf(
            ContextCompat.getColor(
                context,
                if (selected) {
                    R.color.aqua_accent_primary
                } else {
                    R.color.aqua_card_metric_outline
                }
            )
        )
        button.strokeWidth = resources.getDimensionPixelSize(
            if (selected) {
                R.dimen.aqua_size_2
            } else {
                R.dimen.aqua_size_1
            }
        )
    }

    private fun updateAnalyzeEnabled() {
        binding.btnAnalyze.isEnabled =
            selectedType != null &&
                selectedLocations.isNotEmpty() &&
                selectedDensity != null &&
                selectedTrend != null
    }

    private fun openAnalysis() {
        val algaeType = selectedType ?: return
        val density = selectedDensity ?: return
        val trend = selectedTrend ?: return
        if (selectedLocations.isEmpty()) {
            return
        }

        findNavController().navigateSafelyFrom(
            sourceDestinationId = R.id.tankHealthFragment,
            directions = TankHealthFragmentDirections
                .actionTankHealthFragmentToTankAlgaeAnalysisFragment(
                    tankId = tankId,
                    algaeType = algaeType.name,
                    locations = selectedLocations.joinToString(separator = ",") { location ->
                        location.name
                    },
                    density = density.name,
                    trend = trend.name,
                    note = binding.etObservationNote.text?.toString().orEmpty().trim()
                )
        )
    }

    private fun locationButtons(): List<Pair<MaterialButton, AlgaeObservationLocation>> =
        listOf(
            binding.btnLocationFront to AlgaeObservationLocation.FRONT_GLASS,
            binding.btnLocationBack to AlgaeObservationLocation.BACK_GLASS,
            binding.btnLocationSide to AlgaeObservationLocation.SIDE_GLASS,
            binding.btnLocationPlants to AlgaeObservationLocation.PLANTS,
            binding.btnLocationWood to AlgaeObservationLocation.ROOT_WOOD,
            binding.btnLocationRocks to AlgaeObservationLocation.ROCKS,
            binding.btnLocationSubstrate to AlgaeObservationLocation.SUBSTRATE,
            binding.btnLocationEquipment to AlgaeObservationLocation.EQUIPMENT,
            binding.btnLocationOther to AlgaeObservationLocation.OTHER
        )

    private fun densityButtons(): List<Pair<MaterialButton, AlgaeDensity>> =
        listOf(
            binding.btnDensityLow to AlgaeDensity.LOW,
            binding.btnDensityMedium to AlgaeDensity.MEDIUM,
            binding.btnDensityHigh to AlgaeDensity.HIGH
        )

    private fun trendButtons(): List<Pair<MaterialButton, AlgaeTrend>> =
        listOf(
            binding.btnTrendIncreasing to AlgaeTrend.INCREASING,
            binding.btnTrendStable to AlgaeTrend.STABLE,
            binding.btnTrendDecreasing to AlgaeTrend.DECREASING
        )

    private fun restoreState(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            return
        }

        formVisible = savedInstanceState.getBoolean(KEY_FORM_VISIBLE)
        selectedType = savedInstanceState
            .getString(KEY_SELECTED_TYPE)
            ?.let(::enumValueOrNull)
        selectedDensity = savedInstanceState
            .getString(KEY_SELECTED_DENSITY)
            ?.let(::enumValueOrNull)
        selectedTrend = savedInstanceState
            .getString(KEY_SELECTED_TREND)
            ?.let(::enumValueOrNull)

        savedInstanceState
            .getStringArrayList(KEY_SELECTED_LOCATIONS)
            .orEmpty()
            .mapNotNull(::enumValueOrNull<AlgaeObservationLocation>)
            .forEach(selectedLocations::add)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_FORM_VISIBLE, formVisible)
        outState.putString(KEY_SELECTED_TYPE, selectedType?.name)
        outState.putString(KEY_SELECTED_DENSITY, selectedDensity?.name)
        outState.putString(KEY_SELECTED_TREND, selectedTrend?.name)
        outState.putStringArrayList(
            KEY_SELECTED_LOCATIONS,
            ArrayList(selectedLocations.map(AlgaeObservationLocation::name))
        )
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_TANK_ID = "tankId"
        private const val COMMON_GRID_COLUMNS = 4
        private const val TYPE_GRID_COLUMNS = 3
        private const val COMMON_TYPE_COUNT = 4

        private const val KEY_FORM_VISIBLE = "formVisible"
        private const val KEY_SELECTED_TYPE = "selectedType"
        private const val KEY_SELECTED_LOCATIONS = "selectedLocations"
        private const val KEY_SELECTED_DENSITY = "selectedDensity"
        private const val KEY_SELECTED_TREND = "selectedTrend"

        fun newInstance(tankId: Long): TankAlgaeControlFragment =
            TankAlgaeControlFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TANK_ID, tankId)
                }
            }

        private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
            enumValues<T>().firstOrNull { entry -> entry.name == value }
    }
}
