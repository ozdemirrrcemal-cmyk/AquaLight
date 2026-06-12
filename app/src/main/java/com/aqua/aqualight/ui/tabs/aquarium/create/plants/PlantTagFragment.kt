package com.aqua.aqualight.ui.tabs.aquarium.create.plants

import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import com.aqua.aqualight.ui.tabs.aquarium.plants.PlantTagUiRenderer
import coil3.load
import coil3.request.crossfade
import com.aqua.aqualight.R
import com.aqua.aqualight.data.aquarium.model.TankPlantTag
import com.aqua.aqualight.databinding.FragmentPlantTagBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderCardIconAction
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.create.CreateTankViewModel

class PlantTagFragment : Fragment(R.layout.fragment_plant_tag) {

    private var _binding: FragmentPlantTagBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CreateTankViewModel by navGraphViewModels(R.id.nav_create_tank)

    private val selectedPlants = mutableListOf<TankPlantTag>()

    private var pendingMarkerX: Float = 0.5f
    private var pendingMarkerY: Float = 0.5f

    private var hasInitializedSelectedPlants: Boolean = false
    private var isOpeningPlantPicker: Boolean = false

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding = FragmentPlantTagBinding.bind(view)

        initializeSelectedPlantsIfNeeded()

        setupHeader()
        setupImage()
        setupResultListener()
        setupClickListeners()
        renderPlants()
        renderMarkers()
    }

    override fun onResume() {
        super.onResume()
        isOpeningPlantPicker = false
    }

    private fun initializeSelectedPlantsIfNeeded() {
        if (hasInitializedSelectedPlants) {
            return
        }

        selectedPlants.clear()
        selectedPlants.addAll(viewModel.tankDraft.plants)
        hasInitializedSelectedPlants = true
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = getString(R.string.aquarium_tag_plants_title),
                onBackClick = {
                    closePlantTagFlow()
                },
                cardIconAction = AquaHeaderCardIconAction(
                    iconRes = R.drawable.ic_check_24,
                    contentDescription = getString(R.string.aquarium_confirm),
                    backgroundColor = Color.parseColor("#1F6F4A"),
                    strokeColor = Color.parseColor("#2A8A5E"),
                    iconTintColor = Color.WHITE,
                    onClick = {
                        confirmPlantTags()
                    }
                )
            )
        )
    }

    private fun setupImage() {
        val photoUri = viewModel.tankDraft.photoUri

        if (!photoUri.isNullOrBlank()) {
            binding.imgAquariumPhoto.load(photoUri) {
                crossfade(true)
            }
        } else {
            binding.imgAquariumPhoto.setImageResource(R.drawable.nature_aquarium)
        }
    }

    private fun setupResultListener() {
        val savedStateHandle = findNavController()
            .currentBackStackEntry
            ?.savedStateHandle
            ?: return

        savedStateHandle.getLiveData<Bundle?>(
            PlantPickerFragment.RESULT_BUNDLE_KEY
        ).observe(viewLifecycleOwner) { bundle ->
            if (bundle == null) {
                return@observe
            }

            savedStateHandle.set<Bundle?>(
                PlantPickerFragment.RESULT_BUNDLE_KEY,
                null
            )

            val plantName = bundle.getString(
                PlantPickerFragment.RESULT_PLANT_NAME
            ) ?: return@observe

            val category = bundle.getString(
                PlantPickerFragment.RESULT_PLANT_CATEGORY
            ) ?: return@observe

            selectedPlants.add(
                TankPlantTag(
                    plantName = plantName,
                    category = category,
                    markerX = pendingMarkerX,
                    markerY = pendingMarkerY
                )
            )

            renderPlants()
            renderMarkers()
        }
    }

    private fun setupClickListeners() {
        binding.imageTouchArea.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                pendingMarkerX = event.x / view.width.toFloat()
                pendingMarkerY = event.y / view.height.toFloat()

                if (!isOpeningPlantPicker) {
                    val didNavigate = findNavController().navigateSafelyFrom(
                        sourceDestinationId = R.id.createPlantTagFragment,
                        directions = PlantTagFragmentDirections
                            .actionCreatePlantTagFragmentToCreatePlantPickerFragment(
                                useNavResult = true
                            )
                    )

                    isOpeningPlantPicker = didNavigate
                }

                view.performClick()
                true
            } else {
                true
            }
        }
    }

    private fun confirmPlantTags() {
        viewModel.updateTankPlants(selectedPlants)

        findNavController()
            .previousBackStackEntry
            ?.savedStateHandle
            ?.set(
                RESULT_KEY,
                true
            )

        closePlantTagFlow()
    }

    private fun closePlantTagFlow() {
        findNavController().navigateUp()
    }

    private fun renderPlants() {
        PlantTagUiRenderer.renderSelectedPlantList(
            container = binding.plantListContainer,
            plants = selectedPlants,
            onRemoveAt = { index ->
                selectedPlants.removeAt(index)
                renderPlants()
                renderMarkers()
            }
        )
    }

    private fun renderMarkers() {
        PlantTagUiRenderer.renderMarkers(
            container = binding.markerContainer,
            plants = selectedPlants
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val RESULT_KEY = "plant_tag_result"
        const val RESULT_UPDATED = "plant_tag_updated"
    }
}