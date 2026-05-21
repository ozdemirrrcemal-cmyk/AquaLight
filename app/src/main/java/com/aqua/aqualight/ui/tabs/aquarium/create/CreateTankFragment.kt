package com.aqua.aqualight.ui.tabs.aquarium.create

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentCreateTankBinding
import com.aqua.aqualight.ui.tabs.aquarium.create.materials.MaterialPickerFragment
import com.aqua.aqualight.ui.tabs.aquarium.create.plants.PlantPickerFragment
import com.aqua.aqualight.ui.tabs.aquarium.create.plants.PlantTagFragment
import com.aqua.aqualight.ui.tabs.aquarium.create.steps.TankDescriptionFragment
import com.aqua.aqualight.ui.tabs.aquarium.create.steps.TankInfoFragment
import com.aqua.aqualight.ui.tabs.aquarium.create.steps.TankMaterialFragment
import com.aqua.aqualight.ui.tabs.aquarium.create.steps.TankNameFragment
import com.aqua.aqualight.ui.tabs.aquarium.create.steps.TankPhotoFragment
import com.aqua.aqualight.ui.tabs.aquarium.create.steps.TankStepFragment
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import kotlinx.coroutines.launch

class CreateTankFragment : Fragment(R.layout.fragment_create_tank) {

    private var _binding: FragmentCreateTankBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CreateTankViewModel by viewModels()
	private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

    private val totalSteps = 5
    private var currentStepIndex = 0

    private val steps: List<() -> Fragment> = listOf(
        { TankNameFragment() },
        { TankDescriptionFragment() },
        { TankPhotoFragment() },
        { TankMaterialFragment() },
        { TankInfoFragment() }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentCreateTankBinding.bind(view)

        currentStepIndex = savedInstanceState?.getInt(KEY_CURRENT_STEP) ?: 0

        setupBackButton()
        setupSystemBackButton()
        setupNextButton()

        val currentChild = childFragmentManager.findFragmentById(
            R.id.stepFragmentContainer
        )

        if (currentChild == null) {
            showStep(currentStepIndex)
        } else {
            updateHeader()
            updateProgress()
            updateButton()
        }
    }

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener {
            if (handleMaterialFlowBack()) {
                return@setOnClickListener
            }

            if (handlePlantFlowBack()) {
                return@setOnClickListener
            }

            goBack()
        }
    }

    private fun setupSystemBackButton() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (handleMaterialFlowBack()) {
                        return
                    }

                    if (handlePlantFlowBack()) {
                        return
                    }

                    goBack()
                }
            }
        )
    }

    private fun setupNextButton() {
        binding.btnNext.setOnClickListener {
            val currentFragment = childFragmentManager.findFragmentById(
                R.id.stepFragmentContainer
            )

            if (currentFragment is TankStepFragment) {
                val isValid = currentFragment.validateAndSave()

                if (!isValid) {
                    return@setOnClickListener
                }
            }

            if (currentStepIndex == totalSteps - 1) {
                completeTank()
            } else {
                showStep(currentStepIndex + 1)
            }
        }
    }

    private fun showStep(index: Int) {
        currentStepIndex = index

        childFragmentManager.commit {
            replace(
                R.id.stepFragmentContainer,
                steps[index].invoke(),
                "STEP_$index"
            )
        }

        updateHeader()
        updateProgress()
        updateButton()
    }

    private fun updateHeader() {
        binding.tvTitle.text = "Step ${currentStepIndex + 1}"
    }

    private fun updateProgress() {
        val progress = ((currentStepIndex + 1) * 100) / totalSteps
        binding.progressBar.progress = progress
    }

    private fun updateButton() {
        binding.btnNext.text = if (currentStepIndex == totalSteps - 1) {
            "Complete"
        } else {
            "Next"
        }
    }

    private fun goBack() {
        if (currentStepIndex > 0) {
            showStep(currentStepIndex - 1)
        } else {
            findNavController().navigateUp()
        }
    }

    private fun completeTank() {
    binding.btnNext.isEnabled = false

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

            binding.btnNext.isEnabled = true

            Toast.makeText(
                requireContext(),
                "Tank could not be saved.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

    fun openPlantTagFlow() {
        binding.plantFlowContainer.isVisible = true

        childFragmentManager.commit {
            replace(
                R.id.plantFlowContainer,
                PlantTagFragment(),
                "PLANT_TAG_FRAGMENT"
            )
        }
    }

    fun openPlantPickerFlow() {
        childFragmentManager.commit {
            setReorderingAllowed(true)
            add(
                R.id.plantFlowContainer,
                PlantPickerFragment(),
                "PLANT_PICKER_FRAGMENT"
            )
            addToBackStack("PLANT_PICKER_FRAGMENT")
        }
    }

    fun closePlantTagFlow() {
        childFragmentManager.popBackStack(
            null,
            FragmentManager.POP_BACK_STACK_INCLUSIVE
        )

        val currentPlantFragment = childFragmentManager.findFragmentById(
            R.id.plantFlowContainer
        )

        if (currentPlantFragment != null) {
            childFragmentManager.commit {
                remove(currentPlantFragment)
            }
        }

        binding.plantFlowContainer.isVisible = false
    }

    private fun handlePlantFlowBack(): Boolean {
        if (!binding.plantFlowContainer.isVisible) {
            return false
        }

        if (childFragmentManager.backStackEntryCount > 0) {
            childFragmentManager.popBackStack()
        } else {
            closePlantTagFlow()
        }

        return true
    }

    fun openMaterialPickerFlow(
        categoryKey: String,
        categoryTitle: String
    ) {
        binding.materialFlowContainer.isVisible = true

        childFragmentManager.commit {
            replace(
                R.id.materialFlowContainer,
                MaterialPickerFragment.newInstance(
                    categoryKey = categoryKey,
                    categoryTitle = categoryTitle
                ),
                "MATERIAL_PICKER_FRAGMENT"
            )
        }
    }

    fun closeMaterialPickerFlow() {
        val currentMaterialFragment = childFragmentManager.findFragmentById(
            R.id.materialFlowContainer
        )

        if (currentMaterialFragment != null) {
            childFragmentManager.commit {
                remove(currentMaterialFragment)
            }
        }

        binding.materialFlowContainer.isVisible = false
    }

    private fun handleMaterialFlowBack(): Boolean {
        if (!binding.materialFlowContainer.isVisible) {
            return false
        }

        closeMaterialPickerFlow()
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_CURRENT_STEP, currentStepIndex)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_CURRENT_STEP = "key_current_step"
    }
}