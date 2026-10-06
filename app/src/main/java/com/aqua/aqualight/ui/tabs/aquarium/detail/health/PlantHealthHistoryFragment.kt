package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import com.aqua.aqualight.databinding.FragmentPlantHealthHistoryBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel

class PlantHealthHistoryFragment : Fragment(R.layout.fragment_plant_health_history) {

    private val args: PlantHealthHistoryFragmentArgs by navArgs()
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var _binding: FragmentPlantHealthHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        require(args.tankId > 0L && args.plantId > 0L) {
            "PlantHealthHistoryFragment requires positive tankId and plantId."
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPlantHealthHistoryBinding.bind(view)

        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.plant_health_history_title),
                onBackClick = { findNavController().navigateUp() }
            )
        )

        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            val plant = tanks.firstOrNull { tank -> tank.id == args.tankId }
                ?.plants
                ?.firstOrNull { item -> item.id == args.plantId }
            if (plant == null) {
                findNavController().navigateUp()
            } else {
                renderPlant(plant)
            }
        }
    }

    private fun renderPlant(plant: AquariumPlantTag) {
        binding.tvPlantName.text = plant.plantName
        binding.tvPlantCategory.text = plant.category
        if (plant.photoUri.isNullOrBlank()) {
            binding.imgPlant.scaleType = ImageView.ScaleType.CENTER
            binding.imgPlant.setImageResource(R.drawable.ic_health_plant_24)
        } else {
            binding.imgPlant.scaleType = ImageView.ScaleType.CENTER_CROP
            binding.imgPlant.load(plant.photoUri.toUri()) {
                crossfade(true)
                error(R.drawable.ic_health_plant_24)
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
