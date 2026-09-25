package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.core.net.toUri
import androidx.navigation.fragment.findNavController
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import com.aqua.aqualight.databinding.FragmentTankDetailPlantsBinding
import com.aqua.aqualight.databinding.ItemTankPlantPhotoBinding
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.tabs.aquarium.navigation.TankDetailTabArgs
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom

class TankDetailPlantsFragment : TankPlantPhotoFragment() {
    private var _binding: FragmentTankDetailPlantsBinding? = null
    private val binding get() = _binding!!

    private var currentPlants: List<AquariumPlantTag> = emptyList()
    protected override val hasPhotoView: Boolean get() = _binding != null
    private var isOpeningPlantTagScreen = false
    private var isOpeningPlantHealth = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTankDetailPlantsBinding.bind(view)

        binding.plantHealthEntry.ivHealthEntryIcon.setImageResource(
            R.drawable.ic_health_plant_24
        )
        binding.plantHealthEntry.tvHealthEntryTitle.setText(
            R.string.plant_health_entry_title
        )
        binding.plantHealthEntry.tvHealthEntrySummary.setText(
            R.string.plant_health_entry_summary
        )
        binding.plantHealthEntry.tvHealthEntryLastCheck.setText(
            R.string.plant_health_entry_last_check
        )
        binding.plantHealthEntry.root.setOnClickListener {
            openPlantHealth()
        }

        binding.btnAddPlant.setOnClickListener { openPlantTagScreen() }
        setupPhotoSourceResultListener()

        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            val tank = tanks.firstOrNull { it.id == tankId } ?: return@observe
            currentPlants = tank.plants
            renderPlants(currentPlants)
        }
    }

    override fun onResume() {
        super.onResume()
        isOpeningPlantTagScreen = false
        isOpeningPlantHealth = false
    }

    private fun openPlantHealth() {
        if (isOpeningPlantHealth || photoTarget.isInProgress) return

        val navController = findNavController()
        navController.currentBackStackEntry
            ?.savedStateHandle
            ?.set(
                TankDetailFragment.KEY_SELECTED_TAB,
                TankDetailTabArgs.PLANTS
            )

        val didNavigate = navController.navigateSafelyFrom(
            sourceDestinationId = R.id.tankDetailFragment,
            directions = TankDetailFragmentDirections
                .actionTankDetailFragmentToPlantHealthFragment(tankId)
        )

        isOpeningPlantHealth = didNavigate
    }

    private fun openPlantTagScreen() {
        if (isOpeningPlantTagScreen || photoTarget.isInProgress) return
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.tankDetailFragment) return
        isOpeningPlantTagScreen = true
        navController.navigate(
            TankDetailFragmentDirections.actionTankDetailFragmentToTankDetailPlantTagFragment(
                tankId = tankId
            )
        )
    }

    private fun renderPlants(plants: List<AquariumPlantTag>) {
        if (_binding == null) return
        binding.plantListContainer.removeAllViews()
        plants.forEachIndexed { index, plant ->
            binding.plantListContainer.addView(createPlantCard(index, plant))
        }
    }

    private fun createPlantCard(index: Int, plant: AquariumPlantTag): View {
        val item = ItemTankPlantPhotoBinding.inflate(
            layoutInflater,
            binding.plantListContainer,
            false
        )
        item.tvPlantOrderBadge.text = LocaleFormatter.formatInteger(requireContext(), index + 1)
        item.tvPlantCategory.text = plant.category
        item.tvPlantName.text = plant.plantName
        item.plantCard.contentDescription = getString(
            R.string.aquarium_plant_photo_action_description,
            plant.plantName
        )
        bindPlantPhoto(item.imgPlantPhoto, plant.photoUri)
        item.plantCard.setOnClickListener {
            showPlantPhotoSource(plant)
        }
        return item.root
    }

    private fun bindPlantPhoto(imageView: ImageView, photoUri: String?) {
        if (photoUri.isNullOrBlank()) {
            imageView.scaleType = ImageView.ScaleType.CENTER
            imageView.setImageResource(R.drawable.ic_camera_24)
            return
        }

        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.load(photoUri.toUri()) {
            error(R.drawable.ic_camera_24)
            crossfade(true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        internal const val ARG_TANK_ID = "tankId"

        fun newInstance(tankId: Long): TankDetailPlantsFragment {
            return TankDetailPlantsFragment().apply {
                arguments = Bundle().apply { putLong(ARG_TANK_ID, tankId) }
            }
        }
    }
}
