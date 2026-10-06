package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import com.aqua.aqualight.databinding.FragmentPlantHealthDetailBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom

class PlantHealthDetailFragment : Fragment(R.layout.fragment_plant_health_detail) {

    private val args: PlantHealthDetailFragmentArgs by navArgs()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var _binding: FragmentPlantHealthDetailBinding? = null
    private val binding get() = _binding!!

    private var currentPlant: AquariumPlantTag? = null
    private var selectedTab = PlantHealthDetailTab.OVERVIEW
    private var isNavigating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L && args.plantId > 0L) {
            "PlantHealthDetailFragment requires positive tankId and plantId."
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPlantHealthDetailBinding.bind(view)
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.plant_health_detail_title),
                onBackClick = { findNavController().navigateUp() }
            )
        )
        bindTabs()
        binding.btnNewObservation.setOnClickListener { openNewObservation() }
        binding.btnOpenHistory.setOnClickListener { openHistory() }
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            currentPlant = tanks.firstOrNull { tank -> tank.id == args.tankId }
                ?.plants
                ?.firstOrNull { plant -> plant.id == args.plantId }
            currentPlant?.let { plant ->
                binding.renderPlantHealthCatalog(this, plant)
            } ?: findNavController().navigateUp()
        }
        renderSelectedTab()
    }

    override fun onResume() {
        super.onResume()
        isNavigating = false
    }

    private fun bindTabs() {
        binding.tabOverview.setOnClickListener { selectTab(PlantHealthDetailTab.OVERVIEW) }
        binding.tabObservations.setOnClickListener {
            selectTab(PlantHealthDetailTab.OBSERVATIONS)
        }
        binding.tabCare.setOnClickListener { selectTab(PlantHealthDetailTab.CARE) }
        binding.tabNotes.setOnClickListener { selectTab(PlantHealthDetailTab.NOTES) }
    }

    private fun selectTab(tab: PlantHealthDetailTab) {
        selectedTab = tab
        renderSelectedTab()
    }

    private fun renderSelectedTab() {
        if (_binding == null) return
        binding.overviewContainer.isVisible = selectedTab == PlantHealthDetailTab.OVERVIEW
        binding.observationsContainer.isVisible = selectedTab == PlantHealthDetailTab.OBSERVATIONS
        binding.careContainer.isVisible = selectedTab == PlantHealthDetailTab.CARE
        binding.notesContainer.isVisible = selectedTab == PlantHealthDetailTab.NOTES
        setTabSelected(binding.tabOverview, selectedTab == PlantHealthDetailTab.OVERVIEW)
        setTabSelected(binding.tabObservations, selectedTab == PlantHealthDetailTab.OBSERVATIONS)
        setTabSelected(binding.tabCare, selectedTab == PlantHealthDetailTab.CARE)
        setTabSelected(binding.tabNotes, selectedTab == PlantHealthDetailTab.NOTES)
    }

    private fun setTabSelected(view: TextView, selected: Boolean) {
        view.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (selected) R.color.aqua_accent_primary else R.color.aqua_content_secondary
            )
        )
        view.setTypeface(
            view.typeface,
            if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
        )
    }

    private fun openNewObservation() {
        if (isNavigating || currentPlant == null) return
        isNavigating = findNavController().navigateSafelyFrom(
            sourceDestinationId = R.id.plantHealthDetailFragment,
            directions = PlantHealthDetailFragmentDirections
                .actionPlantHealthDetailFragmentToPlantHealthObservationFragment(
                    tankId = args.tankId,
                    plantId = args.plantId
                )
        )
    }

    private fun openHistory() {
        if (isNavigating || currentPlant == null) return
        isNavigating = findNavController().navigateSafelyFrom(
            sourceDestinationId = R.id.plantHealthDetailFragment,
            directions = PlantHealthDetailFragmentDirections
                .actionPlantHealthDetailFragmentToPlantHealthHistoryFragment(
                    tankId = args.tankId,
                    plantId = args.plantId
                )
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}

private enum class PlantHealthDetailTab {
    OVERVIEW,
    OBSERVATIONS,
    CARE,
    NOTES
}
