package com.aqua.aqualight.ui.tabs.aquarium.detail

import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil3.load
import coil3.request.crossfade
import com.aqua.aqualight.R
import com.aqua.aqualight.application.aquarium.AquariumPlantTag
import com.aqua.aqualight.base.BaseActivity
import com.aqua.aqualight.databinding.FragmentPlantTagBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderCardIconAction
import com.aqua.aqualight.ui.common.header.AquaHeaderCardIconTone
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.create.plants.PlantPickerFragment
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom
import com.aqua.aqualight.ui.tabs.aquarium.plants.PlantTagUiRenderer
import kotlinx.coroutines.launch

class TankDetailPlantTagFragment : Fragment(R.layout.fragment_plant_tag) {

    private val args: TankDetailPlantTagFragmentArgs by navArgs()
    private var _binding: FragmentPlantTagBinding? = null
    private val binding get() = _binding!!
    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var tankId: Long = 0L
    private var photoUri: String? = null
    private val selectedPlants = mutableListOf<AquariumPlantTag>()
    private var pendingMarkerX: Float = 0.5f
    private var pendingMarkerY: Float = 0.5f
    private var hasLoadedTankData: Boolean = false
    private var isSaving: Boolean = false
    private var isOpeningPlantPicker: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tankId = args.tankId
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPlantTagBinding.bind(view)
        setupHeader()
        setupTankData()
        setupResultListener()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        isOpeningPlantPicker = false
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                title = getString(R.string.aquarium_tag_plants_title),
                onBackClick = { findNavController().navigateUp() },
                cardIconAction = AquaHeaderCardIconAction(
                    iconRes = R.drawable.ic_check_24,
                    contentDescription = getString(R.string.aquarium_confirm),
                    tone = AquaHeaderCardIconTone.SUCCESS,
                    enabled = !isSaving,
                    onClick = ::savePlantsAndClose
                )
            )
        )
    }

    private fun setupTankData() {
        aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
            if (hasLoadedTankData) return@observe
            val tank = tanks.firstOrNull { it.id == tankId }
            if (tank == null) {
                findNavController().navigateUp()
                return@observe
            }

            photoUri = tank.photoUri
            selectedPlants.clear()
            selectedPlants.addAll(tank.plants)
            hasLoadedTankData = true
            setupImage()
            renderPlants()
            renderMarkers()
        }
    }

    private fun setupImage() {
        val imageUri = photoUri
        if (!imageUri.isNullOrBlank()) {
            binding.imgAquariumPhoto.load(Uri.parse(imageUri)) { crossfade(true) }
        } else {
            binding.imgAquariumPhoto.setImageResource(R.drawable.nature_aquarium)
        }
    }

    private fun setupResultListener() {
        val savedStateHandle = findNavController()
            .currentBackStackEntry
            ?.savedStateHandle
            ?: return

        savedStateHandle.getLiveData<Bundle>(
            PlantPickerFragment.RESULT_BUNDLE_KEY
        ).observe(viewLifecycleOwner) { bundle ->
            savedStateHandle.remove<Bundle>(PlantPickerFragment.RESULT_BUNDLE_KEY)
            val plantName = bundle.getString(
                PlantPickerFragment.RESULT_PLANT_NAME
            ) ?: return@observe
            val category = bundle.getString(
                PlantPickerFragment.RESULT_PLANT_CATEGORY
            ) ?: return@observe

            selectedPlants.add(
                AquariumPlantTag(
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
        binding.imageTouchArea.setOnTouchListener { touchedView, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                pendingMarkerX = event.x / touchedView.width.toFloat()
                pendingMarkerY = event.y / touchedView.height.toFloat()
                openPlantPickerScreen()
                touchedView.performClick()
            }
            true
        }
    }

    private fun openPlantPickerScreen() {
        if (isOpeningPlantPicker) return
        isOpeningPlantPicker = findNavController().navigateSafelyFrom(
            sourceDestinationId = R.id.tankDetailPlantTagFragment,
            directions = TankDetailPlantTagFragmentDirections
                .actionTankDetailPlantTagFragmentToPlantPickerFragment(
                    useNavResult = true
                )
        )
    }

    private fun savePlantsAndClose() {
        if (isSaving) return
        isSaving = true
        setupHeader()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                aquariumTankViewModel.updateTankPlants(
                    tankId = tankId,
                    plants = selectedPlants
                )
                findNavController().navigateUp()
            } catch (exception: Exception) {
                exception.printStackTrace()
                isSaving = false
                setupHeader()
                (activity as? BaseActivity)?.showSnackBar(
                    message = getString(R.string.aquarium_error_plants_save_failed),
                    type = BaseActivity.SnackType.ERROR
                )
            }
        }
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
        private const val ARG_TANK_ID = "tankId"

        fun newInstance(tankId: Long): TankDetailPlantTagFragment {
            return TankDetailPlantTagFragment().apply {
                arguments = bundleOf(ARG_TANK_ID to tankId)
            }
        }
    }
}
