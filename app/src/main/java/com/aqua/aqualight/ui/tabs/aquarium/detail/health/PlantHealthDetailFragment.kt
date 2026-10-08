package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import com.aqua.aqualight.databinding.FragmentPlantHealthDetailBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom

class PlantHealthDetailFragment : Fragment(R.layout.fragment_plant_health_detail) {

    private val health: PlantHealthViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
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
        selectedTab = savedInstanceState?.getString(KEY_TAB)?.let(PlantHealthDetailTab::valueOf)
            ?: PlantHealthDetailTab.OVERVIEW
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_TAB, selectedTab.name)
        super.onSaveInstanceState(outState)
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
        binding.cardNoObservations.isVisible = false
        binding.cardNoNotes.isVisible = false
        binding.cardLatestObservation.isVisible = false
        binding.tvObservationStatus.isVisible = false
        binding.btnRetry.setOnClickListener { health.retry() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                health.observations(args.tankId, args.plantId).collect { state ->
                    binding.renderPlantRecords(state, ::openRecord)
                }
            }
        }
        binding.bindPlantHealthTabs(::selectTab)
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
        binding.tabOverview.setPlantHealthTabSelected(selectedTab == PlantHealthDetailTab.OVERVIEW)
        binding.tabObservations.setPlantHealthTabSelected(selectedTab == PlantHealthDetailTab.OBSERVATIONS)
        binding.tabCare.setPlantHealthTabSelected(selectedTab == PlantHealthDetailTab.CARE)
        binding.tabNotes.setPlantHealthTabSelected(selectedTab == PlantHealthDetailTab.NOTES)
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

    private fun openRecord(id: Long) {
        if (isNavigating || currentPlant == null) return
        isNavigating = findNavController().navigateSafelyFrom(
            R.id.plantHealthDetailFragment,
            PlantHealthDetailFragmentDirections.actionPlantHealthDetailFragmentToPlantObservationRecordFragment(
                args.tankId, args.plantId, id
            )
        )
    }

    private companion object { const val KEY_TAB = "plant_detail_tab" }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
