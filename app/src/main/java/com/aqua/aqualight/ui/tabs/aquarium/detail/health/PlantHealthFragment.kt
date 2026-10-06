package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import com.aqua.aqualight.databinding.FragmentPlantHealthBinding
import com.aqua.aqualight.databinding.ItemPlantHealthPlantBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom

class PlantHealthFragment : Fragment(R.layout.fragment_plant_health) {

    private val args: PlantHealthFragmentArgs by navArgs()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var _binding: FragmentPlantHealthBinding? = null
    private val binding get() = _binding!!

    private var tankPlants: List<AquariumPlantTag> = emptyList()
    private var isNavigating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L) {
            "PlantHealthFragment requires a positive tankId."
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPlantHealthBinding.bind(view)

        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.screen_title_plant_health),
                onBackClick = { findNavController().navigateUp() }
            )
        )

        binding.etSearch.doAfterTextChanged { text ->
            renderPlants(text?.toString().orEmpty())
        }

        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            tankPlants = tanks.firstOrNull { tank -> tank.id == args.tankId }
                ?.plants
                .orEmpty()
            renderPlants(binding.etSearch.text?.toString().orEmpty())
        }
    }

    override fun onResume() {
        super.onResume()
        isNavigating = false
    }

    private fun renderPlants(query: String) {
        if (_binding == null) return
        val normalized = query.trim()
        val visiblePlants = if (normalized.isBlank()) {
            tankPlants
        } else {
            tankPlants.filter { plant ->
                plant.plantName.contains(normalized, ignoreCase = true) ||
                    plant.category.contains(normalized, ignoreCase = true)
            }
        }

        binding.plantListContainer.removeAllViews()
        binding.cardEmpty.isVisible = visiblePlants.isEmpty()
        binding.plantListContainer.isVisible = visiblePlants.isNotEmpty()

        visiblePlants.forEach { plant ->
            val item = ItemPlantHealthPlantBinding.inflate(
                LayoutInflater.from(requireContext()),
                binding.plantListContainer,
                false
            )
            bindPlant(item, plant)
            item.root.setOnClickListener { openPlantDetail(plant.id) }
            binding.plantListContainer.addView(item.root)
        }
    }

    private fun bindPlant(
        item: ItemPlantHealthPlantBinding,
        plant: AquariumPlantTag
    ) {
        val catalog = PlantHealthCatalogUi.record(requireContext(), plant.catalogId)
        val growth = PlantHealthCatalogUi.growth(requireContext(), catalog?.care)
        item.tvPlantName.text = plant.plantName
        item.tvPlantMeta.text = listOf(plant.category, growth)
            .filter(String::isNotBlank)
            .joinToString(" • ")
        item.tvStatus.setText(R.string.plant_health_status_no_observation)
        bindPlantPhoto(item.imgPlant, plant.photoUri)
    }

    private fun bindPlantPhoto(imageView: ImageView, photoUri: String?) {
        if (photoUri.isNullOrBlank()) {
            imageView.scaleType = ImageView.ScaleType.CENTER
            imageView.setImageResource(R.drawable.ic_health_plant_24)
            return
        }
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.load(photoUri.toUri()) {
            crossfade(true)
            error(R.drawable.ic_health_plant_24)
        }
    }

    private fun openPlantDetail(plantId: Long) {
        if (isNavigating) return
        isNavigating = findNavController().navigateSafelyFrom(
            sourceDestinationId = R.id.plantHealthFragment,
            directions = PlantHealthFragmentDirections
                .actionPlantHealthFragmentToPlantHealthDetailFragment(
                    tankId = args.tankId,
                    plantId = plantId
                )
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
