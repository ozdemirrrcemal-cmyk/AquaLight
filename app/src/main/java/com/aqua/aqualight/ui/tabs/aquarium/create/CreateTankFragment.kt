package com.aqua.aqualight.ui.tabs.aquarium.create

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentCreateTankBinding
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.MaterialPickerFragment
import com.aqua.aqualight.ui.tabs.aquarium.create.plants.PlantPickerFragment
import com.aqua.aqualight.ui.tabs.aquarium.create.plants.PlantTagFragment
import com.aqua.aqualight.ui.tabs.aquarium.create.steps.TankDescriptionFragment
import com.aqua.aqualight.ui.tabs.aquarium.create.steps.TankInfoFragment
import com.aqua.aqualight.ui.tabs.aquarium.create.steps.TankMaterialFragment
import com.aqua.aqualight.ui.tabs.aquarium.create.steps.TankNameFragment
import com.aqua.aqualight.ui.tabs.aquarium.create.steps.TankPhotoFragment
import com.aqua.aqualight.ui.tabs.aquarium.create.steps.TankStepFragment
import kotlinx.coroutines.launch

class CreateTankFragment : Fragment(R.layout.fragment_create_tank),
    MaterialPickerFragment.MaterialPickerHost,
    PlantPickerFragment.PlantPickerHost {

    private var _binding: FragmentCreateTankBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CreateTankViewModel by viewModels()

    private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private var currentStepIndex: Int = 0

    private var isCompletingTank: Boolean = false

    private val steps: List<() -> Fragment> =
        listOf(
            { TankNameFragment() },
            { TankDescriptionFragment() },
            { TankPhotoFragment() },
            { TankMaterialFragment() },
            { TankInfoFragment() }
        )

    private val totalSteps: Int
        get() = steps.size

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        _binding =
            FragmentCreateTankBinding.bind(view)

        currentStepIndex =
            restoreCurrentStepIndex(savedInstanceState)

        setupHeader()
        setupSystemBackButton()
        setupNextButton()

        restoreCurrentStepIfNeeded()
        restoreOverlayContainersIfNeeded()
    }

    private fun setupHeader() {
        binding.appHeader.setupAquaHeader(
            fragment = this,
            config = AquaHeaderConfig(
                titleOverride = "Step ${currentStepIndex + 1}",
                onBackClick = {
                    handleBackNavigation()
                }
            )
        )
    }

    private fun restoreCurrentStepIndex(
        savedInstanceState: Bundle?
    ): Int {
        val savedIndex =
            savedInstanceState?.getInt(
                KEY_CURRENT_STEP,
                0
            ) ?: 0

        return savedIndex.coerceIn(
            0,
            steps.lastIndex
        )
    }

    private fun restoreCurrentStepIfNeeded() {
        val currentChild =
            childFragmentManager.findFragmentById(
                R.id.stepFragmentContainer
            )

        if (currentChild == null) {
            showStep(
                index = currentStepIndex,
                animate = false
            )
        } else {
            updateHeader()
            updateProgress()
            updateButton()
        }
    }

    private fun restoreOverlayContainersIfNeeded() {
        binding.plantFlowContainer.isVisible =
            childFragmentManager.findFragmentById(
                R.id.plantFlowContainer
            ) != null

        binding.materialFlowContainer.isVisible =
            childFragmentManager.findFragmentById(
                R.id.materialFlowContainer
            ) != null
    }

    private fun setupSystemBackButton() {
        requireActivity()
            .onBackPressedDispatcher
            .addCallback(
                viewLifecycleOwner,
                object : OnBackPressedCallback(true) {

                    override fun handleOnBackPressed() {
                        handleBackNavigation()
                    }
                }
            )
    }

    private fun handleBackNavigation() {
        when {
            handleMaterialFlowBack() -> Unit
            handlePlantFlowBack() -> Unit
            else -> goBack()
        }
    }

    private fun setupNextButton() {
        binding.btnNext.setOnClickListener {
            handleNextClick()
        }
    }

    private fun handleNextClick() {
        if (isCompletingTank) {
            return
        }

        val currentFragment =
            childFragmentManager.findFragmentById(
                R.id.stepFragmentContainer
            )

        if (currentFragment is TankStepFragment) {
            val isValid =
                currentFragment.validateAndSave()

            if (!isValid) {
                return
            }
        }

        if (isLastStep()) {
            completeTank()
        } else {
            showStep(
                index = currentStepIndex + 1
            )
        }
    }

    private fun isLastStep(): Boolean {
        return currentStepIndex == totalSteps - 1
    }

    private fun showStep(
        index: Int,
        animate: Boolean = true
    ) {
        val targetIndex =
            index.coerceIn(
                0,
                steps.lastIndex
            )

        val isForward =
            targetIndex > currentStepIndex

        childFragmentManager.commit {
            setReorderingAllowed(true)

            if (animate) {
                if (isForward) {
                    setCustomAnimations(
                        R.anim.nav_slide_in_right,
                        R.anim.nav_slide_out_left
                    )
                } else {
                    setCustomAnimations(
                        R.anim.nav_slide_in_left,
                        R.anim.nav_slide_out_right
                    )
                }
            }

            replace(
                R.id.stepFragmentContainer,
                steps[targetIndex].invoke(),
                stepTag(targetIndex)
            )
        }

        currentStepIndex =
            targetIndex

        updateHeader()
        updateProgress()
        updateButton()
    }

    private fun updateHeader() {
        setupHeader()
    }

    private fun updateProgress() {
        val progress =
            ((currentStepIndex + 1) * 100) / totalSteps

        binding.progressBar.progress =
            progress
    }

    private fun updateButton() {
        binding.btnNext.text =
            if (isLastStep()) {
                "Complete"
            } else {
                "Next"
            }

        binding.btnNext.isEnabled =
            !isCompletingTank
    }

    private fun goBack() {
        if (currentStepIndex > 0) {
            showStep(
                index = currentStepIndex - 1
            )
        } else {
            findNavController().navigateUp()
        }
    }

    private fun completeTank() {
        if (isCompletingTank) {
            return
        }

        isCompletingTank =
            true

        binding.btnNext.isEnabled =
            false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                aquariumTankViewModel.addTankFromDraft(
                    viewModel.tankDraft
                )

                findNavController().popBackStack(
                    R.id.aquariumFragment,
                    false
                )
            } catch (exception: Exception) {
                exception.printStackTrace()

                isCompletingTank =
                    false

                _binding?.btnNext?.isEnabled =
                    true

                Toast.makeText(
                    requireContext(),
                    "Tank could not be saved.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun openPlantTagFlow() {
        binding.plantFlowContainer.isVisible =
            true

        childFragmentManager.commit {
            setReorderingAllowed(true)

            setCustomAnimations(
                R.anim.nav_slide_in_right,
                R.anim.nav_slide_out_left
            )

            replace(
                R.id.plantFlowContainer,
                PlantTagFragment(),
                TAG_PLANT_TAG_FRAGMENT
            )
        }
    }

    fun openPlantPickerFlow() {
        if (!binding.plantFlowContainer.isVisible) {
            return
        }

        val existingPicker =
            childFragmentManager.findFragmentByTag(
                TAG_PLANT_PICKER_FRAGMENT
            )

        if (existingPicker != null) {
            return
        }

        childFragmentManager.commit {
            setReorderingAllowed(true)

            setCustomAnimations(
                R.anim.nav_slide_in_right,
                R.anim.nav_slide_out_left
            )

            add(
                R.id.plantFlowContainer,
                PlantPickerFragment.newCreateInstance(),
                TAG_PLANT_PICKER_FRAGMENT
            )
        }
    }

    fun closePlantTagFlow() {
        val plantPickerFragment =
            childFragmentManager.findFragmentByTag(
                TAG_PLANT_PICKER_FRAGMENT
            )

        val plantTagFragment =
            childFragmentManager.findFragmentByTag(
                TAG_PLANT_TAG_FRAGMENT
            )

        childFragmentManager.commit {
            setReorderingAllowed(true)

            plantPickerFragment?.let { fragment ->
                remove(fragment)
            }

            plantTagFragment?.let { fragment ->
                remove(fragment)
            }
        }

        binding.plantFlowContainer.isVisible =
            false
    }

    override fun closePlantPickerFlow() {
        val plantPickerFragment =
            childFragmentManager.findFragmentByTag(
                TAG_PLANT_PICKER_FRAGMENT
            ) ?: return

        childFragmentManager.commit {
            setReorderingAllowed(true)

            setCustomAnimations(
                R.anim.nav_slide_in_left,
                R.anim.nav_slide_out_right
            )

            remove(
                plantPickerFragment
            )
        }
    }

    private fun handlePlantFlowBack(): Boolean {
        if (!binding.plantFlowContainer.isVisible) {
            return false
        }

        val plantPickerFragment =
            childFragmentManager.findFragmentByTag(
                TAG_PLANT_PICKER_FRAGMENT
            )

        if (plantPickerFragment != null) {
            closePlantPickerFlow()
        } else {
            closePlantTagFlow()
        }

        return true
    }

    fun openMaterialPickerFlow(
        categoryKey: String,
        categoryTitle: String
    ) {
        if (binding.materialFlowContainer.isVisible) {
            return
        }

        binding.materialFlowContainer.isVisible =
            true

        childFragmentManager.commit {
            setReorderingAllowed(true)

            setCustomAnimations(
                R.anim.nav_slide_in_right,
                R.anim.nav_slide_out_left
            )

            replace(
                R.id.materialFlowContainer,
                MaterialPickerFragment.newCreateInstance(
                    categoryKey = categoryKey,
                    categoryTitle = categoryTitle
                ),
                TAG_MATERIAL_PICKER_FRAGMENT
            )
        }
    }

    override fun closeMaterialPickerFlow() {
        val currentMaterialFragment =
            childFragmentManager.findFragmentByTag(
                TAG_MATERIAL_PICKER_FRAGMENT
            )

        if (currentMaterialFragment != null) {
            childFragmentManager.commit {
                setReorderingAllowed(true)

                setCustomAnimations(
                    R.anim.nav_slide_in_left,
                    R.anim.nav_slide_out_right
                )

                remove(
                    currentMaterialFragment
                )
            }
        }

        binding.materialFlowContainer.isVisible =
            false
    }

    private fun handleMaterialFlowBack(): Boolean {
        if (!binding.materialFlowContainer.isVisible) {
            return false
        }

        closeMaterialPickerFlow()

        return true
    }

    private fun stepTag(
        index: Int
    ): String {
        return "$TAG_STEP_PREFIX$index"
    }

    override fun onSaveInstanceState(
        outState: Bundle
    ) {
        outState.putInt(
            KEY_CURRENT_STEP,
            currentStepIndex
        )

        super.onSaveInstanceState(
            outState
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()

        _binding =
            null
    }

    companion object {

        private const val KEY_CURRENT_STEP =
            "key_current_step"

        private const val TAG_STEP_PREFIX =
            "CreateTankStep_"

        private const val TAG_PLANT_TAG_FRAGMENT =
            "PlantTagFragment"

        private const val TAG_PLANT_PICKER_FRAGMENT =
            "PlantPickerFragment"

        private const val TAG_MATERIAL_PICKER_FRAGMENT =
            "MaterialPickerFragment"
    }
}